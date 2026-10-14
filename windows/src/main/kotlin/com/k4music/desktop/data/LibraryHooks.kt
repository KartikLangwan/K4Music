package com.k4music.desktop.data

import com.k4music.android.models.Event
import com.k4music.android.models.Lyrics
import com.k4music.desktop.AppLog
import com.k4music.desktop.player.PlayerController
import com.k4music.desktop.player.Track
import com.k4music.desktop.player.toTrack
import com.k4music.providers.innertube.Innertube
import com.k4music.providers.innertube.models.bodies.NextBody
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

    /** Ids of liked songs, kept current so menus and the player bar never wait on the database. */
    val liked: kotlinx.coroutines.flow.StateFlow<Set<String>> = Db.library.favorites()
        .map { songs -> songs.mapTo(HashSet()) { it.id } }
        .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptySet())

    fun toggleLike(track: Track) {
        val like = track.id !in liked.value
        scope.launch { safely { Db.library.toggleLike(track, like) } }
    }

    fun attach(player: PlayerController) {
        player.onTrackStarted = { track -> scope.launch { safely { Db.library.insert(track) } } }
        player.onPlayed = { track, ms ->
            if (ms >= MIN_LISTEN_MS) scope.launch {
                safely {
                    Db.library.insert(track)
                    Db.library.insert(Event(songId = track.id, timestamp = System.currentTimeMillis(), playTime = ms))
                    Db.library.incrementTotalPlayTimeMs(track.id, ms)
                }
            }
        }
        // Radio: when the queue ends with repeat off, continue with songs related to the last one.
        player.onQueueEnding = { last ->
            if (last.isLocal || last.isSaavn) emptyList()
            else Innertube.nextPage(NextBody(videoId = last.id))?.getOrNull()
                ?.itemsPage?.items.orEmpty().mapNotNull { it.toTrack() }.filter { it.id != last.id }.take(20)
        }
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
