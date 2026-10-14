package com.k4music.desktop.k4

import com.k4music.android.k4.JioSaavnApi
import com.k4music.android.k4.K4Song
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

/** The same song on JioSaavn, as a verified CDN url, or null (title plus artist or duration must match). */
suspend fun findSaavnStream(title: String, artist: String, durationMs: Long?, quality: String): String? {
    if (title.isBlank()) return null
    val candidates = searchSaavnSongs("$title $artist".trim())
        .map { Candidate(it.title, it.artist, it.durationMs.takeIf { d -> d > 0 }, it.contentUri) }
    val match = pickBestMatch(title, artist, durationMs, candidates) ?: return null
    saavnApi.preferredQuality = quality
    return runCatching { saavnApi.bestVariant(match.payload) }.getOrNull()
}

/**
 * YouTube ids whose audio comes from JioSaavn because YouTube failed them. Persisted, so later
 * plays go straight to the source that works.
 */
object StreamFallback {
    private const val LIMIT = 1000
    // v2: v1 also pinned songs while YouTube was blocking the whole network; those pins are dropped.
    private val file get() = AppPaths.data.resolve("stream-fallback-v2.properties")
    private val props by lazy {
        AppPaths.data.resolve("stream-fallback.properties").delete()
        Properties().also { p -> runCatching { file.inputStream().use(p::load) } }
    }
    private val forced = ConcurrentHashMap.newKeySet<String>()

    @Synchronized fun url(id: String): String? = props.getProperty(id)
    @Synchronized fun drop(id: String) { if (props.remove(id) != null) save() }

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
        if (props.size >= LIMIT) props.clear()
        props.setProperty(id, url)
        save()
    }

    private fun save() = runCatching { file.outputStream().use { props.store(it, "K4 Music stream fallbacks") } }
}
