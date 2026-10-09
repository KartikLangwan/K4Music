package com.k4music.android.k4

import com.k4music.providers.innertube.Innertube
import com.k4music.providers.innertube.models.NavigationEndpoint
import com.k4music.providers.innertube.models.Thumbnail
import kotlinx.coroutines.CancellationException

/**
 * JioSaavn <-> merged-app bridge.
 *
 * The decision for the merged app is "YouTube Music first, JioSaavn second":
 * a JioSaavn song enters the existing pipeline as a normal [Innertube.SongItem]
 * whose media key is "saavn:<direct stream url>". The player's resolving data
 * source recognises the prefix and resolves the CDN url here instead of asking
 * YouTube. Nothing else in the app needs to know about the second source.
 */
const val SAAVN_KEY_PREFIX = "saavn:"

private val saavnApi by lazy { JioSaavnApi() }

fun isSaavnKey(key: String?) = key?.startsWith(SAAVN_KEY_PREFIX) == true

/** Best CDN mp4 for a saavn key, or null when nothing serves. Never throws. */
fun resolveSaavnStream(key: String, quality: String = "320"): String? {
    val base = key.removePrefix(SAAVN_KEY_PREFIX)
    if (base.isBlank()) return null
    saavnApi.preferredQuality = quality
    return runCatching { saavnApi.bestVariant(base) }.getOrNull()
}

/** Saavn search results with unplayable (blank) entries dropped. Never throws. */
suspend fun searchSaavnSongs(query: String): List<K4Song> = try {
    saavnApi.searchSongs(query).filter { it.contentUri.isNotBlank() }
} catch (error: CancellationException) {
    throw error
} catch (_: Exception) {
    emptyList()
}

/** K4Song -> the item shape the existing search list, menus and player expect. */
fun K4Song.toSongItem(): Innertube.SongItem = Innertube.SongItem(
    info = Innertube.Info(
        name = title,
        endpoint = NavigationEndpoint.Endpoint.Watch(videoId = SAAVN_KEY_PREFIX + contentUri)
    ),
    authors = if (artist.isBlank()) null else listOf(
        Innertube.Info(name = artist, endpoint = null)
    ),
    album = null,
    durationText = formatDurationMs(durationMs),
    explicit = false,
    thumbnail = artworkUri?.let { Thumbnail(url = it, height = null, width = null) }
)

/** 65_000 -> "1:05", 3_723_000 -> "1:02:03", unknown -> null. */
fun formatDurationMs(ms: Long): String? {
    if (ms <= 0L) return null
    val total = ms / 1000
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}
