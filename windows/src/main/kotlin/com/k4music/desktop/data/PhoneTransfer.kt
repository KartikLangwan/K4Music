package com.k4music.desktop.data

import com.k4music.android.models.Playlist
import com.k4music.android.models.Song
import com.k4music.desktop.AppLog
import com.k4music.providers.sync.LibraryStore
import com.k4music.providers.sync.LibraryTransfer
import com.k4music.providers.sync.LocalPlaylist
import com.k4music.providers.sync.SongMeta
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** The desktop library as the phone transfer sees it. */
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

    override suspend fun setLike(id: String, at: Long) { dao.like(id, at) }

    override suspend fun createPlaylist(name: String) = dao.insert(Playlist(name = name))

    override suspend fun updatePlaylist(id: Long, name: String, songIds: List<String>) = dao.replacePlaylist(id, name, songIds)
}

/**
 * Settings → Phone transfer: shows a QR code for a few minutes and takes in the phone's likes and playlists
 * over the local network. Nothing is listening before the user asks, or after one library arrived.
 */
object PhoneTransfer {
    const val WINDOW_MS = 5 * 60_000L

    sealed interface State {
        data object Idle : State
        data object NoNetwork : State
        data class Waiting(val invite: LibraryTransfer.Invite, val until: Long) : State
        data class Done(val imported: LibraryTransfer.Imported) : State
        data object Expired : State
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()
    @Volatile private var receiver: LibraryTransfer.Receiver? = null

    fun start() {
        stop()
        val hosts = LibraryTransfer.localAddresses()
        if (hosts.isEmpty()) { _state.value = State.NoNetwork; return }
        val r = runCatching { LibraryTransfer.Receiver() }.getOrElse { AppLog.w("Transfer", "Could not listen", it); _state.value = State.NoNetwork; return }
        receiver = r
        _state.value = State.Waiting(r.invite(hosts), System.currentTimeMillis() + WINDOW_MS)
        scope.launch {
            val doc = r.receive(WINDOW_MS) { library ->
                val imported = runBlocking { LibraryTransfer.import(library, DesktopLibraryStore) }
                AppLog.i("Transfer", "Received ${imported.likes} likes, ${imported.playlists} playlists, ${imported.songs} songs")
                _state.value = State.Done(imported)
                "OK ${imported.likes} ${imported.playlists} ${imported.songs}"
            }
            r.close()
            if (receiver === r) {
                receiver = null
                if (doc == null && _state.value is State.Waiting) _state.value = State.Expired
            }
        }
    }

    /** Stops listening (Cancel, or leaving the screen). */
    fun stop() {
        receiver?.close()
        receiver = null
        if (_state.value is State.Waiting) _state.value = State.Idle
    }
}
