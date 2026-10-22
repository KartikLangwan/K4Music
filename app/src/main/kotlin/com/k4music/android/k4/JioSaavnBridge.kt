package com.k4music.android.k4

import com.k4music.providers.innertube.Innertube
import com.k4music.providers.innertube.models.NavigationEndpoint
import com.k4music.providers.innertube.models.Thumbnail
import android.content.Context
import com.k4music.android.Dependencies
import com.k4music.android.playlistimport.Candidate
import com.k4music.android.playlistimport.pickBestMatch
import kotlinx.coroutines.CancellationException

/**
 * JioSaavn <-> merged-app bridge.
 *
 * The decision for the merged app is "YouTube Music first, JioSaavn second":
 * a JioSaavn song enters the existing pipeline as a normal [Innertube.SongItem]
 * whose media key is a stable catalogue id or a preserved legacy URL. The player's resolving data
 * source recognises the prefix and resolves the CDN url here instead of asking
 * YouTube. Nothing else in the app needs to know about the second source.
 */
const val SAAVN_KEY_PREFIX = "saavn:"

private val saavnApi by lazy { JioSaavnApi() }

fun isSaavnKey(key: String?) = key?.startsWith(SAAVN_KEY_PREFIX) == true

/** Best CDN mp4 for a Saavn key, or null when nothing serves. Cancellation propagates. */
suspend fun resolveSaavnStream(key: String, quality: String = "320"): String? {
    return saavnApi.resolveMediaKey(key, quality)
}

/**
 * The same song on JioSaavn, as a verified CDN url, or null. Used when YouTube
 * cannot serve a track; requires a real title match plus artist or duration.
 */
suspend fun findSaavnStream(title: String, artist: String, durationMs: Long?, quality: String = "320"): String? {
    if (title.isBlank()) return null
    val candidates = searchSaavnSongs("$title $artist".trim())
        .map { Candidate(it.title, it.artist, it.durationMs.takeIf { d -> d > 0 }, it.contentUri) }
    val match = pickBestMatch(title, artist, durationMs, candidates) ?: return null
    return try { saavnApi.bestVariant(match.payload, quality = quality) }
    catch (error: CancellationException) { throw error }
    catch (_: Exception) { null }
}

/**
 * YouTube ids whose audio comes from JioSaavn because YouTube failed them.
 * Persisted: the player cache is keyed by the YouTube id, and bytes from two
 * different sources must never be mixed under one key.
 */
object StreamFallback {
    private val prefs by lazy {
        Dependencies.application.getSharedPreferences("stream_fallback", Context.MODE_PRIVATE)
    }
    private val forced = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    fun url(id: String): String? = prefs.getString(id, null)

    /**
     * YouTube first, always: a song pinned to JioSaavn tries YouTube again a day later (pins
     * from before this have no time and count as old).
     */
    fun isStale(id: String) = System.currentTimeMillis() - prefs.getLong("t:$id", 0L) > PIN_TTL_MS

    /** Next resolve of [id] skips YouTube and looks the song up on JioSaavn afresh. */
    fun force(id: String) {
        drop(id)
        forced.add(id)
    }
    fun isForced(id: String) = id in forced

    fun drop(id: String) = prefs.edit().remove(id).remove("t:$id").apply()

    fun resolved(id: String, url: String?) {
        forced.remove(id)
        if (url == null) return
        // ponytail: wholesale reset at 1000 pins (2 keys each); a dropped song just re-resolves via YouTube.
        if (prefs.all.size >= 2000) prefs.edit().clear().apply()
        prefs.edit().putString(id, url).putLong("t:$id", System.currentTimeMillis()).apply()
    }

    private const val PIN_TTL_MS = 24 * 60 * 60 * 1000L
}

/** Saavn search results with unplayable (blank) entries dropped. Never throws. */
suspend fun searchSaavnSongs(query: String): List<K4Song> = try {
    val songs = saavnApi.searchSongs(query).filter { it.contentUri.isNotBlank() }
    if (songs.isEmpty()) songs else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        preserveLegacySaavnKeys(songs, com.k4music.android.Database.legacySaavnSongIds())
    }
} catch (error: CancellationException) {
    throw error
} catch (_: Exception) {
    emptyList()
}

/** K4Song -> the item shape the existing search list, menus and player expect. */
fun K4Song.toSongItem(): Innertube.SongItem = Innertube.SongItem(
    info = Innertube.Info(
        name = title,
        endpoint = NavigationEndpoint.Endpoint.Watch(videoId = saavnMediaKey())
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
