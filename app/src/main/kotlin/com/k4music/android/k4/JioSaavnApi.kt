package com.k4music.android.k4

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import com.k4music.providers.utils.useCancellable
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * JioSaavn catalogue: search + stream-URL resolve + quality ladder, ported
 * from K4Audio. In the merged app this is the SECONDARY source: YouTube
 * (Innertube/Piped) answers first, this fills gaps and backs imports.
 *
 * Self-contained on [K4Song] so no Room/K4Audio-model changes are needed.
 */
class JioSaavnApi(client: OkHttpClient? = null) {
    private val knownStreams = object : LinkedHashMap<String, String>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > 128
    }

    private fun rememberStream(id: String, url: String) {
        if (url.isNotBlank()) synchronized(knownStreams) { knownStreams[id] = url }
    }

    private val http: OkHttpClient = client ?: OkHttpClient.Builder()
        .protocols(listOf(Protocol.HTTP_1_1))
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun searchSongs(query: String, limit: Int = 40, page: Int = 1): List<K4Song> =
        withContext(Dispatchers.IO) {
            fetch(query, limit, page)
        }

    suspend fun searchMore(query: String, page: Int, limit: Int = 40): List<K4Song> {
        if (query.isBlank() || page < 2) return emptyList()
        return searchSongs(query, limit, page)
    }

    /** Expand reader: both new catalogue keys and all existing URL/cache identities remain valid. */
    suspend fun resolveMediaKey(key: String, quality: String = "320"): String? = try {
        withTimeoutOrNull(12_000) {
            if (!key.startsWith("saavn:")) return@withTimeoutOrNull null
            if (!key.startsWith(SAAVN_CATALOG_PREFIX)) return@withTimeoutOrNull bestVariant(key.removePrefix("saavn:"), quality = quality)
            val id = saavnCatalogueId(key) ?: return@withTimeoutOrNull null
            val cached = synchronized(knownStreams) { knownStreams[id] }
            if (cached != null) bestVariant(cached, quality = quality)?.let { return@withTimeoutOrNull it }
            val url = "https://www.jiosaavn.com/api.php?__call=song.getDetails&_format=json&pids=${URLEncoder.encode(id, "UTF-8")}"
            val json = http.newCall(Request.Builder().url(url).header("User-Agent", UA).build()).useCancellable {
                if (it.isSuccessful) it.body.string() else null
            } ?: return@withTimeoutOrNull null
            val song = JSONObject(json).optJSONObject(id) ?: return@withTimeoutOrNull null
            if (song.optString("id") != id) return@withTimeoutOrNull null
            val encrypted = song.optString("encrypted_media_url").ifBlank { song.optString("encrypted_drm_media_url") }
            val stream = decryptSaavn(encrypted)?.takeIf { it.isNotBlank() } ?: return@withTimeoutOrNull null
            rememberStream(id, stream)
            bestVariant(stream, quality = quality)
        }
    } catch (error: CancellationException) { throw error }
    catch (_: Exception) { null }

    /** Best verified CDN file for a Saavn stream URL, or null when none serve. */
    suspend fun bestVariant(url: String, excluded: Set<String> = emptySet(), quality: String = "320"): String? {
        if (isPreviewUrl(url)) return null
        // Unknown naming scheme: the url itself is the only candidate.
        val base = saavnBase(url) ?: return url.takeIf { it.startsWith("http") && it !in excluded }
        val ladder = listOf("320", "160", "96")
        val i = ladder.indexOf(quality).coerceAtLeast(0)
        val order = ladder.drop(i) + ladder.take(i).reversed()
        for (q in order) {
            val cand = "${base}_$q.mp4"
            if (cand in excluded) continue
            if (reachable(cand)) return cand
        }
        return null
    }

    fun isSaavnStream(url: String): Boolean = url.contains("saavncdn.com")

    private suspend fun fetch(query: String, limit: Int, page: Int): List<K4Song> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()
        val enc = URLEncoder.encode(q, "UTF-8")
        val url =
            "https://www.jiosaavn.com/api.php?__call=search.getResults&_format=json&n=$limit&p=$page&_marker=0&ctx=android&q=$enc"
        val jsonStr = try {
            http.newCall(
                Request.Builder().url(url)
                    .header("User-Agent", UA)
                    .build()
            ).useCancellable { r -> if (r.isSuccessful) r.body.string() else null }
        } catch (error: CancellationException) { throw error }
        catch (_: Exception) {
            return emptyList()
        } ?: return emptyList()
        return try {
            val results = JSONObject(jsonStr).optJSONArray("results") ?: return emptyList()
            val out = ArrayList<K4Song>(results.length())
            for (i in 0 until results.length()) {
                val r = results.optJSONObject(i) ?: continue
                val id = r.optString("id")
                if (id.isBlank()) continue
                val title = unescape(r.optString("song").ifBlank { r.optString("title") })
                val artist = unescape(
                    r.optString("primary_artists").ifBlank { r.optString("singers") }
                ).ifBlank { "Unknown Artist" }
                val album = unescape(r.optString("album"))
                val durSec = r.optLong("duration", 0L)
                val image = r.optString("image").replace("150x150", "500x500")
                val encUrl = r.optString("encrypted_media_url")
                    .ifBlank { r.optString("encrypted_drm_media_url") }
                val stream = if (encUrl.isNotBlank()) decryptSaavn(encUrl).orEmpty() else ""
                rememberStream(id, stream)
                out += K4Song(
                    id = "saavn_$id",
                    title = title,
                    artist = artist,
                    album = album,
                    durationMs = durSec * 1000L,
                    contentUri = stream,
                    artworkUri = image.takeIf { it.isNotBlank() }
                )
            }
            rankOnlineResults(q, out)
        } catch (e: Exception) {
            Log.w(TAG, "parse error: ${e.message}")
            emptyList()
        }
    }

    // A CDN probe is a 2-byte read; a slow answer is as bad as none.
    private val probe by lazy { http.newBuilder().callTimeout(3, TimeUnit.SECONDS).build() }

    private suspend fun reachable(url: String): Boolean = try {
        probe.newCall(
            Request.Builder().url(url)
                .addHeader("User-Agent", UA)
                .addHeader("Range", "bytes=0-1")
                .get().build()
        ).useCancellable { r -> r.isSuccessful || r.code == 206 }
    } catch (error: CancellationException) { throw error }
    catch (_: Exception) {
        false
    }

    private fun decryptSaavn(encrypted: String): String? = try {
        val key = SecretKeySpec("38346591".toByteArray(Charsets.UTF_8), "DES")
        val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, key)
        val decoded = Base64.decode(encrypted.trim(), Base64.DEFAULT)
        String(cipher.doFinal(decoded), Charsets.UTF_8)
    } catch (_: Exception) {
        null
    }

    private fun unescape(t: String): String = t
        .replace("&amp;", "&").replace("&quot;", "\"")
        .replace("&#039;", "'").replace("&lt;", "<").replace("&gt;", ">")

    private fun saavnBase(url: String): String? =
        SAAVN_Q_RE.find(url)?.let { url.substring(0, it.range.first) }

    private fun isPreviewUrl(url: String): Boolean =
        url.contains("preview") || url.contains("_p.mp4") || url.contains("jiotunepreview")

    private companion object {
        const val TAG = "K4Saavn"
        const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
        val SAAVN_Q_RE = Regex("_(320|160|96)(?:_p)?\\.(?:mp4|m4a)", RegexOption.IGNORE_CASE)
        val SAAVN_QUALITIES = setOf("320", "160", "96")
    }
}
