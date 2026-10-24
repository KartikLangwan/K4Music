package com.k4music.providers.lrclib.models

import com.k4music.providers.lrclib.LrcParser
import com.k4music.providers.lrclib.toLrcFile
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.time.Duration

@Serializable
data class Track(
    val id: Int,
    val trackName: String,
    val artistName: String,
    /** Missing on some entries (null in the answer): one such entry must not fail the whole search. */
    val duration: Double? = null,
    val plainLyrics: String?,
    val syncedLyrics: String?
) {
    val lrc by lazy { syncedLyrics?.let { LrcParser.parse(it)?.toLrcFile() } }
}

/** Same length within 2 s (sources round differently), else the closest title. */
internal fun List<Track>.bestMatchingFor(title: String, duration: Duration) =
    filter { it.duration != null && abs(it.duration - duration.inWholeMilliseconds / 1000.0) <= 2.0 }
        .minByOrNull { abs(it.duration!! - duration.inWholeMilliseconds / 1000.0) }
        ?: minByOrNull { abs(it.trackName.length - title.length) }
