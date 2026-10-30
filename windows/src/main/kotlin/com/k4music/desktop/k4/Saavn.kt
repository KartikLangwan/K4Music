package com.k4music.desktop.k4

import com.k4music.android.k4.JioSaavnApi
import com.k4music.android.k4.K4Song
import com.k4music.android.k4.preserveLegacySaavnKeys
import com.k4music.android.playlistimport.Candidate
import com.k4music.android.playlistimport.pickBestMatch
import com.k4music.desktop.AppPaths
import kotlinx.coroutines.CancellationException
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap

// Ported from app/.../k4/JioSaavnBridge.kt; matching uses the shared playlistimport/PlaylistImport.kt.

const val SAAVN_KEY_PREFIX = "saavn:"

private val saavnApi by lazy { JioSaavnApi() }

fun isSaavnKey(key: String?) = key?.startsWith(SAAVN_KEY_PREFIX) == true

/** Best CDN mp4 for a Saavn key, or null when nothing serves. Cancellation propagates. */
suspend fun resolveSaavnStream(key: String, quality: String = "320"): String? {
    return saavnApi.resolveMediaKey(key, quality)
}

/** Saavn search results with unplayable (blank) entries dropped. Never throws. */
suspend fun searchSaavnSongs(query: String): List<K4Song> = try {
    val songs = saavnApi.searchSongs(query).filter { it.contentUri.isNotBlank() }
    if (songs.isEmpty()) songs else preserveLegacySaavnKeys(songs, com.k4music.desktop.data.Db.library.legacySaavnSongIds())
} catch (error: CancellationException) {
    throw error
} catch (_: Exception) {
    emptyList()
}

/** The same song on JioSaavn, as a verified CDN url, or null (title plus artist or duration must match). */
suspend fun findSaavnStream(title: String, artist: String, durationMs: Long?, quality: String): String? {
    if (title.isBlank()) return null
    val candidates = searchSaavnSongs("$title $artist".trim())
        .map { Candidate(it.title, it.artist, it.durationMs.takeIf { d -> d > 0 }, it.contentUri) }
    val match = pickBestMatch(title, artist, durationMs, candidates) ?: return null
    return try { saavnApi.bestVariant(match.payload, quality = quality) }
    catch (error: CancellationException) { throw error }
    catch (_: Exception) { null }
}

/**
 * YouTube ids whose audio comes from JioSaavn because YouTube failed them. Persisted, so later
 * plays go straight to the source that works.
 */
object StreamFallback {
    private const val LIMIT = 1000
    private const val PIN_TTL_MS = 24 * 60 * 60 * 1000L
    // v2: v1 also pinned songs while YouTube was blocking the whole network; those pins are dropped.
    private val file get() = AppPaths.data.resolve("stream-fallback-v2.properties")
    private val props by lazy {
        AppPaths.data.resolve("stream-fallback.properties").delete()
        Properties().also { p -> runCatching { file.inputStream().use(p::load) } }
    }
    private val forced = ConcurrentHashMap.newKeySet<String>()

    /** YouTube first, always: a pin older than a day is dropped so the song tries YouTube again. */
    @Synchronized fun url(id: String): String? {
        val url = props.getProperty(id) ?: return null
        val at = props.getProperty("t.$id")?.toLongOrNull() ?: 0L
        if (System.currentTimeMillis() - at > PIN_TTL_MS) { drop(id); return null }
        return url
    }
    @Synchronized fun drop(id: String) { if (props.remove(id) != null) { props.remove("t.$id"); save() } }

    /** Next resolve of [id] skips YouTube and looks the song up on JioSaavn afresh. */
    fun force(id: String) {
        drop(id)
        forced.add(id)
    }
    fun isForced(id: String) = id in forced

    @Synchronized
    fun resolved(id: String, url: String?) {
        forced.remove(id)
        if (url == null) return
        // ponytail: wholesale reset at LIMIT entries; a dropped song just re-resolves via YouTube.
        if (props.size >= LIMIT * 2) props.clear()
        props.setProperty(id, url)
        props.setProperty("t.$id", System.currentTimeMillis().toString())
        save()
    }

    private fun save() = runCatching { file.outputStream().use { props.store(it, "K4 Audio stream fallbacks") } }
}
