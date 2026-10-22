package com.k4music.desktop.data

import com.k4music.android.models.Event
import com.k4music.android.models.Lyrics
import com.k4music.desktop.AppLog
import com.k4music.desktop.player.PlayerController
import com.k4music.desktop.player.Track
import com.k4music.desktop.player.toTrack
import com.k4music.providers.innertube.Innertube
import com.k4music.providers.innertube.models.bodies.ContinuationBody
import com.k4music.providers.innertube.models.bodies.NextBody
import com.k4music.desktop.player.RadioSeed
import com.k4music.providers.innertube.requests.nextPage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Connects playback to the library: songs played are saved, history and play time recorded. */
object LibraryHooks {
    private const val MIN_LISTEN_MS = 10_000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeJob = SupervisorJob()
    private val writes = CoroutineScope(writeJob + Dispatchers.IO)

    /** Called after playback has stopped emitting history and before Room closes. */
    suspend fun flush() {
        writeJob.children.toList().forEach { it.join() }
    }

    /** Ids of liked songs, kept current so menus and the player bar never wait on the database. */
    val liked: kotlinx.coroutines.flow.StateFlow<Set<String>> = Db.library.favorites()
        .map { songs -> songs.mapTo(HashSet()) { it.id } }
        .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptySet())

    fun toggleLike(track: Track) {
        val like = track.id !in liked.value
        writes.launch { safely { Db.library.toggleLike(track, like) } }
    }

    fun attach(player: PlayerController) {
        player.onTrackStarted = { track -> writes.launch { safely { Db.library.insert(track) } } }
        player.onPlayed = { track, ms ->
            if (ms >= MIN_LISTEN_MS) writes.launch {
                safely {
                    Db.library.insert(track)
                    Db.library.insert(Event(songId = track.id, timestamp = System.currentTimeMillis(), playTime = ms))
                    Db.library.incrementTotalPlayTimeMs(track.id, ms)
                }
            }
        }
        // Radio: when the queue ends with repeat off, continue with songs related to the last one.
        player.onQueueEnding = { last ->
            if (last.isLocal || last.isSaavn) emptyList() else Radio(RadioSeed(videoId = last.id)).next()
        }
        player.radioSource = { seed -> Radio(seed)::next }
        LyricsRepository.store = object : LyricsRepository.Store {
            override suspend fun load(songId: String) = runCatching {
                Db.library.lyrics(songId).first()?.let { LyricsRepository.Result(it.fixed.orEmpty(), it.synced.orEmpty()) }
            }.getOrNull()

            override suspend fun save(track: Track, result: LyricsRepository.Result) {
                safely {
                    Db.library.insert(track)
                    Db.library.upsert(Lyrics(songId = track.id, fixed = result.fixed, synced = result.synced))
                }
            }
        }
    }

    private suspend fun safely(block: suspend () -> Unit) {
        runCatching { block() }.onFailure { AppLog.w("Library", "Library update failed", it) }
    }
}

/**
 * YouTube Music's endless "radio" for a seed (same requests as Android's YouTubeRadio):
 * the first call returns the automix page (~25 songs), later calls its continuations.
 */
private class Radio(private val seed: RadioSeed) {
    private var playlistId = seed.playlistId
    private var params = seed.params
    private var playlistSetVideoId = seed.playlistSetVideoId
    private var continuation: String? = null
    private var started = false

    suspend fun next(): List<Track> {
        if (started && continuation == null) return emptyList()
        val page = if (!started) {
            Innertube.nextPage(
                NextBody(videoId = seed.videoId, playlistId = playlistId, params = params, playlistSetVideoId = playlistSetVideoId)
            )?.getOrNull()?.also { result ->
                playlistId = result.playlistId
                params = result.params
                playlistSetVideoId = result.playlistSetVideoId
            }?.itemsPage
        } else Innertube.nextPage(ContinuationBody(continuation = continuation!!))?.getOrNull()
        started = true
        continuation = page?.continuation?.takeUnless { it == continuation }
        return page?.items.orEmpty().mapNotNull { it.toTrack() }
    }
}
