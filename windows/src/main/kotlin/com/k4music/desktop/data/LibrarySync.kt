package com.k4music.desktop.data

import com.k4music.android.models.Playlist
import com.k4music.android.models.Song
import com.k4music.desktop.AppLog
import com.k4music.desktop.AppPaths
import com.k4music.providers.sync.LibraryStore
import com.k4music.providers.sync.LocalPlaylist
import com.k4music.providers.sync.SongMeta
import com.k4music.providers.sync.SyncCrypto
import com.k4music.providers.sync.SyncEngine
import com.k4music.providers.sync.SyncResult
import com.k4music.providers.sync.SyncState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/** The desktop library as the sync engine sees it. */
internal object DesktopLibraryStore : LibraryStore {
    private val dao get() = Db.library

    override suspend fun likes() = dao.likedRows().associate { it.id to it.likedAt }

    override suspend fun playlists() = dao.plainPlaylists().map { LocalPlaylist(it.id, it.name, dao.playlistSongIds(it.id)) }

    override suspend fun songMeta(ids: Collection<String>): Map<String, SongMeta> =
        ids.distinct().chunked(500).flatMap { dao.songsByIds(it) }.associate {
            it.id to SongMeta(it.title, it.artistsText, it.durationText, it.thumbnailUrl)
        }

    override suspend fun ensureSong(id: String, meta: SongMeta?) {
        dao.insert(Song(id = id, title = meta?.title ?: id, artistsText = meta?.artists, durationText = meta?.duration, thumbnailUrl = meta?.thumbnail))
    }

    override suspend fun setLike(id: String, at: Long?) { dao.like(id, at) }

    override suspend fun createPlaylist(name: String) = dao.insert(Playlist(name = name))

    override suspend fun updatePlaylist(id: Long, name: String, songIds: List<String>) = dao.replacePlaylist(id, name, songIds)

    override suspend fun deletePlaylist(id: Long) { dao.deletePlaylistById(id) }
}

/**
 * Keeps likes and playlists in step with the paired phone. Nothing runs on a timer: a sync happens at
 * start, when the window regains focus (at most every few minutes), shortly after a local change, and on demand.
 */
object LibrarySync {
    sealed interface Status {
        data object Idle : Status
        data object Syncing : Status
        data class Failed(val message: String) : Status
    }

    private const val CHANGE_DELAY_MS = 20_000L
    private const val FOCUS_GAP_MS = 5 * 60_000L
    private val file get() = AppPaths.data.resolve("sync.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val engine by lazy { SyncEngine(DesktopLibraryStore) }

    private val _state = MutableStateFlow(load())
    val state: StateFlow<SyncState> = _state.asStateFlow()
    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    private var watcher: Job? = null
    private var lastAttempt = 0L

    private fun load(): SyncState = runCatching { json.decodeFromString(SyncState.serializer(), file.readText()) }.getOrDefault(SyncState())

    private fun save(state: SyncState) {
        _state.value = state
        runCatching {
            val temp = file.resolveSibling("sync.json.tmp")
            temp.writeText(json.encodeToString(SyncState.serializer(), state))
            java.nio.file.Files.move(temp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }.onFailure { AppLog.w("Sync", "Could not save sync state", it) }
    }

    /** Called once at startup: resumes watching and does the first sync when already paired. */
    fun start() {
        if (_state.value.paired) { watch(); sync() }
    }

    /** Starts a new pairing and returns the code to type on the other device. */
    fun pairNew(): String {
        val code = SyncCrypto.create().code
        pair(code)
        return code
    }

    /** False when [code] is not a valid pairing code. */
    fun pairWith(code: String): Boolean {
        val crypto = SyncCrypto.fromCode(code) ?: return false
        pair(crypto.code)
        return true
    }

    private fun pair(code: String) {
        save(SyncState(code = code))
        watch()
        sync()
    }

    /** The library was replaced by a restore: keep the pairing, drop what was known about the old library. */
    fun databaseReplaced() {
        if (_state.value.paired) save(_state.value.afterRestore())
    }

    fun unpair() {
        watcher?.cancel(); watcher = null
        save(SyncState())
        _status.value = Status.Idle
    }

    /** Window regained focus: sync if the last attempt is a few minutes old. */
    fun onFocus() {
        if (_state.value.paired && System.currentTimeMillis() - lastAttempt > FOCUS_GAP_MS) sync()
    }

    fun sync() {
        if (!_state.value.paired) return
        scope.launch {
            mutex.withLock {
                if (!_state.value.paired) return@withLock
                lastAttempt = System.currentTimeMillis()
                _status.value = Status.Syncing
                val started = _state.value
                val result = engine.sync(started)
                // Unpaired, re-paired or restored while this round ran: its result belongs to a state that is gone.
                if (_state.value !== started) { _status.value = Status.Idle; return@withLock }
                when (result) {
                    is SyncResult.Synced -> { save(result.state); _status.value = Status.Idle }
                    is SyncResult.Unchanged -> { save(result.state); _status.value = Status.Idle }
                    is SyncResult.Failed -> {
                        AppLog.w("Sync", "Sync failed", result.error)
                        _status.value = Status.Failed(result.error.message ?: "Sync failed")
                    }
                }
            }
        }
    }

    /** Re-reads a tiny summary of likes and playlists whenever those tables change (no polling) and syncs after a quiet moment. */
    @OptIn(FlowPreview::class)
    private fun watch() {
        watcher?.cancel()
        watcher = scope.launch {
            Db.library.syncSignal().distinctUntilChanged().drop(1).collectLatest {
                // Each new change restarts the wait, so a burst of edits syncs once.
                delay(CHANGE_DELAY_MS)
                sync()
            }
        }
    }
}
