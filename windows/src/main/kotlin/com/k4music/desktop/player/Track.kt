package com.k4music.desktop.player

import androidx.compose.runtime.Immutable
import com.k4music.android.k4.K4Song
import com.k4music.android.k4.parseClockToMs
import com.k4music.android.models.Song
import com.k4music.desktop.k4.SAAVN_KEY_PREFIX
import com.k4music.desktop.k4.isSaavnKey
import com.k4music.providers.innertube.Innertube
import kotlinx.serialization.Serializable

const val LOCAL_KEY_PREFIX = "local:"

/**
 * A playable song in the queue. [id] uses the Android app's keys: a YouTube video id,
 * `saavn:<cdn url>` for JioSaavn songs, or `local:<file path>` for files on this PC.
 */
@Immutable
@Serializable
data class Track(
    val id: String,
    val title: String,
    val artists: String? = null,
    val album: String? = null,
    val durationText: String? = null,
    val thumbnailUrl: String? = null,
    val explicit: Boolean = false,
    val albumId: String? = null,
    val artistIds: List<String> = emptyList()
) {
    /** Same name as Android's MediaItem.mediaId (the shared import sources use it). */
    val mediaId get() = id
    val isLocal get() = id.startsWith(LOCAL_KEY_PREFIX)
    val isSaavn get() = isSaavnKey(id)
    val durationMs get() = durationText?.let(::parseClockToMs)?.takeIf { it > 0 }
    val localPath get() = id.removePrefix(LOCAL_KEY_PREFIX)

    fun toSong() = Song(
        id = id,
        title = title,
        artistsText = artists,
        durationText = durationText,
        thumbnailUrl = thumbnailUrl,
        explicit = explicit
    )
}

fun Innertube.SongItem.toTrack(): Track? {
    val videoId = info?.endpoint?.videoId?.takeIf { it.isNotBlank() } ?: return null
    return Track(
        id = videoId,
        title = info?.name.orEmpty(),
        artists = authors?.joinToString("") { it.name.orEmpty() }?.takeIf { it.isNotBlank() },
        album = album?.name,
        durationText = durationText,
        thumbnailUrl = thumbnail?.size(544),
        explicit = explicit,
        albumId = album?.endpoint?.browseId,
        artistIds = authors?.mapNotNull { it.endpoint?.browseId }.orEmpty()
    )
}

fun Innertube.VideoItem.toTrack(): Track? {
    val videoId = info?.endpoint?.videoId?.takeIf { it.isNotBlank() } ?: return null
    return Track(
        id = videoId,
        title = info?.name.orEmpty(),
        artists = authors?.joinToString("") { it.name.orEmpty() }?.takeIf { it.isNotBlank() },
        durationText = durationText,
        thumbnailUrl = thumbnail?.size(544)
    )
}

fun K4Song.toTrack() = Track(
    id = SAAVN_KEY_PREFIX + contentUri,
    title = title,
    artists = artist.takeIf { it.isNotBlank() },
    album = album.takeIf { it.isNotBlank() },
    durationText = durationMs.takeIf { it > 0 }?.let(::formatDuration),
    thumbnailUrl = artworkUri
)

fun Song.toTrack() = Track(
    id = id,
    title = title,
    artists = artistsText,
    durationText = durationText,
    thumbnailUrl = thumbnailUrl,
    explicit = explicit
)

/** 65_000 -> "1:05", 3_723_000 -> "1:02:03". */
fun formatDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}
