package com.k4music.desktop.data

import com.k4music.desktop.player.Track
import com.k4music.providers.innertube.Innertube
import com.k4music.providers.innertube.models.bodies.NextBody
import com.k4music.providers.innertube.requests.lyrics
import com.k4music.providers.kugou.KuGou
import com.k4music.providers.lrclib.LrcLib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

/**
 * Lyrics for a song, looked up in the same order as the Android player (Lyrics.kt):
 * plain from YouTube Music then LrcLib; synced from LrcLib, LrcLib with the title's
 * "(...)" tail removed, then KuGou.
 */
object LyricsRepository {
    data class Result(val fixed: String, val synced: String)

    private val memory = ConcurrentHashMap<String, Result>()
    /** Persistent store hook (the library database); null until the database is ready. */
    @Volatile var store: Store? = null

    interface Store {
        suspend fun load(songId: String): Result?
        suspend fun save(track: Track, result: Result)
    }

    suspend fun get(track: Track): Result = withContext(Dispatchers.IO) {
        memory[track.id] ?: store?.load(track.id)?.also { memory[track.id] = it } ?: fetch(track).also {
            memory[track.id] = it
            if (it.fixed.isNotBlank() || it.synced.isNotBlank()) store?.save(track, it)
        }
    }

    /** Replaces the lyrics of [track] (edit / refetch). */
    suspend fun put(track: Track, result: Result) {
        memory[track.id] = result
        store?.save(track, result)
    }

    fun forget(id: String) { memory.remove(id) }

    /** Looks the lyrics up again online, replacing the saved ones. */
    suspend fun refetch(track: Track): Result = withContext(Dispatchers.IO) { fetch(track).also { put(track, it) } }

    private suspend fun fetch(track: Track): Result {
        val artist = track.artists.orEmpty()
        val title = if (track.isLocal) track.title.substringBeforeLast('.').trim() else track.title
        val duration = (track.durationMs ?: 0L).milliseconds

        val fixed = (if (track.isLocal || track.isSaavn) null else Innertube.lyrics(NextBody(videoId = track.id))?.getOrNull())
            ?: LrcLib.bestLyrics(artist = artist, title = title, duration = duration, album = track.album, synced = false)
                ?.map { it?.text }?.getOrNull()

        val synced = LrcLib.bestLyrics(artist = artist, title = title, duration = duration, album = track.album)
            ?.map { it?.text }?.getOrNull()
            ?: LrcLib.bestLyrics(artist = artist, title = title.split("(")[0].trim(), duration = duration, album = track.album)
                ?.map { it?.text }?.getOrNull()
            ?: KuGou.lyrics(artist = artist, title = title, duration = duration.inWholeSeconds)?.map { it?.value }?.getOrNull()

        return Result(fixed.orEmpty(), synced.orEmpty())
    }
}
