package com.k4music.android.k4

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.util.Log
import com.k4music.android.Database
import com.k4music.android.models.Playlist
import com.k4music.android.models.Song
import com.k4music.android.models.SongPlaylistMap
import com.k4music.android.transaction
import com.k4music.providers.sync.LibraryStore
import com.k4music.providers.sync.LocalPlaylist
import com.k4music.providers.sync.SongMeta
import com.k4music.providers.sync.SyncCrypto
import com.k4music.providers.sync.SyncEngine
import com.k4music.providers.sync.SyncResult
import com.k4music.providers.sync.SyncState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/** The phone's library as the sync engine sees it. */
internal object AndroidLibraryStore : LibraryStore {
    override suspend fun likes() = withContext(Dispatchers.IO) { Database.likedRows().associate { it.id to it.likedAt } }

    override suspend fun playlists() = withContext(Dispatchers.IO) {
        Database.plainPlaylists().map { LocalPlaylist(it.id, it.name, Database.playlistSongIds(it.id)) }
    }

    override suspend fun songMeta(ids: Collection<String>): Map<String, SongMeta> = withContext(Dispatchers.IO) {
        ids.distinct().chunked(500).flatMap { Database.songsByIds(it) }.associate {
            it.id to SongMeta(it.title, it.artistsText, it.durationText, it.thumbnailUrl)
        }
    }

    override suspend fun ensureSong(id: String, meta: SongMeta?) = withContext(Dispatchers.IO) {
        Database.insert(Song(id = id, title = meta?.title ?: id, artistsText = meta?.artists, durationText = meta?.duration, thumbnailUrl = meta?.thumbnail))
        Unit
    }

    override suspend fun setLike(id: String, at: Long?) = withContext(Dispatchers.IO) { Database.like(id, at); Unit }

    override suspend fun createPlaylist(name: String) = withContext(Dispatchers.IO) { Database.insert(Playlist(name = name)) }

    override suspend fun updatePlaylist(id: Long, name: String, songIds: List<String>) = withContext(Dispatchers.IO) {
        transaction {
            Database.renamePlaylist(id, name)
            Database.clearPlaylist(id)
            songIds.distinct().forEachIndexed { index, song -> Database.insert(SongPlaylistMap(song, id, index)) }
        }
    }

    override suspend fun deletePlaylist(id: Long) = withContext(Dispatchers.IO) { Database.deletePlaylistById(id); Unit }
}

/**
 * Keeps likes and playlists in step with the paired PC. Nothing runs on a timer: a sync happens at
 * start, when the network comes back, shortly after a local change, and on demand. The network
 * callback exists only while this app process is alive and a code is set.
 */
object LibrarySync {
    sealed interface Status {
        data object Idle : Status
        data object Syncing : Status
        data class Failed(val message: String) : Status
    }

    private const val TAG = "LibrarySync"
    private const val CHANGE_DELAY_MS = 20_000L
    private const val NETWORK_GAP_MS = 60_000L
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val engine by lazy { SyncEngine(AndroidLibraryStore) }
    private lateinit var file: File
    private var connectivity: ConnectivityManager? = null

    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state.asStateFlow()
    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    private var watcher: Job? = null
    private var lastAttempt = 0L
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (System.currentTimeMillis() - lastAttempt > NETWORK_GAP_MS) sync()
        }
    }

    fun init(context: Context) {
        file = File(context.applicationContext.filesDir, "sync.json")
        connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)
        _state.value = runCatching { json.decodeFromString(SyncState.serializer(), file.readText()) }.getOrDefault(SyncState())
        // Resume a little after launch so start-up stays light.
        if (_state.value.paired) scope.launch { delay(8_000); engage() }
    }

    private fun save(state: SyncState) {
        _state.value = state
        runCatching {
            val temp = File(file.parentFile, "sync.json.tmp")
            temp.writeText(json.encodeToString(SyncState.serializer(), state))
            if (!temp.renameTo(file)) { file.delete(); temp.renameTo(file) }
        }.onFailure { Log.w(TAG, "Could not save sync state", it) }
    }

    /** Starts a new pairing and returns the code to type on the other device. */
    fun pairNew(): String = SyncCrypto.create().code.also(::pair)

    /** False when [code] is not a valid pairing code. */
    fun pairWith(code: String): Boolean {
        val crypto = SyncCrypto.fromCode(code) ?: return false
        pair(crypto.code)
        return true
    }

    private fun pair(code: String) {
        save(SyncState(code = code))
        engage()
    }

    /** The library was replaced by a restore: keep the pairing, drop what was known about the old library. */
    fun databaseReplaced() {
        if (_state.value.paired) save(_state.value.afterRestore())
    }

    fun unpair() {
        watcher?.cancel(); watcher = null
        runCatching { connectivity?.unregisterNetworkCallback(networkCallback) }
        save(SyncState())
        _status.value = Status.Idle
    }

    /** Begins watching for local changes and for the network (the network callback fires once now, which is the first sync). */
    private fun engage() {
        watch()
        runCatching { connectivity?.unregisterNetworkCallback(networkCallback) }
        val registered = runCatching { connectivity?.registerDefaultNetworkCallback(networkCallback) }.isSuccess
        if (!registered) sync()
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
                        Log.w(TAG, "Sync failed", result.error)
                        _status.value = Status.Failed(result.error.message ?: "Sync failed")
                    }
                }
            }
        }
    }

    /** Room re-reads a tiny summary only when likes, playlists or their songs change (no polling). */
    private fun watch() {
        watcher?.cancel()
        watcher = scope.launch {
            Database.syncSignal().distinctUntilChanged().drop(1).collectLatest {
                delay(CHANGE_DELAY_MS)
                sync()
            }
        }
    }
}
