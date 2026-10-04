package com.k4music.android.k4

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import java.security.MessageDigest

/** IDs may contain URL paths. Never let them select a file outside the download directory. */
internal fun offlineFileKey(id: String): String =
    if (id.matches(Regex("[A-Za-z0-9_-]{1,120}"))) id
    else MessageDigest.getInstance("SHA-256").digest(id.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

/** Download progress for a single song. [done] and [error] are never both set. */
data class K4DownloadState(
    val songId: String,
    val percent: Int,
    val error: String? = null,
    val done: Boolean = false
)

/**
 * Crash-safe offline copy of an already-resolved stream.
 *
 * Ported from K4Music `SongDownloader`, decoupled from Room/player: the caller
 * supplies [resolve] (song -> playable URL) and the [dir]; this class
 * only guarantees:
 *  - writes go to a `.part` sibling, renamed on success (a crash/full disk can
 *    never leave a half-written file that looks complete);
 *  - cancel/failure always cleans its `.part` up;
 *  - the on-disk extension matches what the server actually served.
 *
 * Wire-up: pass K4Music's stream URL (Innertube/Piped/JioSaavnApi.bestVariant)
 * as [resolve], `File(context.filesDir, "offline_tracks")` as [dir].
 */
class K4Download(
    private val dir: File,
    private val resolve: suspend (K4Song) -> String?,
    client: OkHttpClient? = null
) {
    private val http: OkHttpClient = client ?: OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val _state = MutableStateFlow<Map<String, K4DownloadState>>(emptyMap())
    val state: StateFlow<Map<String, K4DownloadState>> = _state.asStateFlow()
    private val downloadMutex = Mutex()

    /** Completed download, or null. A leftover `.part` never matches. */
    fun fileOf(song: K4Song): File? =
        dir.listFiles()?.firstOrNull {
            (it.nameWithoutExtension == offlineFileKey(song.id) || it.nameWithoutExtension == song.id) && !it.name.endsWith(".part") && it.length() > 0
        }

    fun isDownloaded(song: K4Song): Boolean = fileOf(song) != null

    fun localUriOf(song: K4Song): String? =
        fileOf(song)?.let { Uri.fromFile(it).toString() }

    suspend fun download(song: K4Song): Boolean = downloadMutex.withLock { downloadInternal(song) }

    private suspend fun downloadInternal(song: K4Song): Boolean {
        val ctx = currentCoroutineContext()
        return try {
            _state.update { it + (song.id to K4DownloadState(song.id, 0)) }
            if (isDownloaded(song)) {
                _state.update { it + (song.id to K4DownloadState(song.id, 100, done = true)) }
                true
            } else {
                val url = withContext(Dispatchers.IO) { resolve(song) }
                if (url == null) {
                    _state.update {
                        it + (song.id to K4DownloadState(song.id, 0, error = "No playable stream found"))
                    }
                    false
                } else {
                    fetch(ctx, song, url)
                }
            }
        } catch (ce: CancellationException) {
            withContext(NonCancellable) { cleanupPartial(song) }
            _state.update { it + (song.id to K4DownloadState(song.id, 0, error = "Cancelled")) }
            throw ce
        } catch (e: Exception) {
            Log.w(TAG, "download failed for ${song.title}: ${e.message}")
            cleanupPartial(song)
            _state.update {
                it + (song.id to K4DownloadState(song.id, 0, error = e.message ?: "Download failed"))
            }
            false
        }
    }

    private suspend fun fetch(
        ctx: kotlin.coroutines.CoroutineContext,
        song: K4Song,
        url: String
    ): Boolean = withContext(Dispatchers.IO) {
        val part = File(dir, "${offlineFileKey(song.id)}.part")
        part.delete()
        part.parentFile?.mkdirs()

        var servedType: String? = null
        var total = -1L
        var written = 0L

        http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) {
                _state.update {
                    it + (song.id to K4DownloadState(song.id, 0, error = "HTTP ${resp.code}"))
                }
                return@withContext false
            }
            val body = resp.body
            if (body == null) {
                _state.update {
                    it + (song.id to K4DownloadState(song.id, 0, error = "Empty response"))
                }
                return@withContext false
            }
            servedType = body.contentType()?.toString()
            total = body.contentLength()
            part.outputStream().use { out ->
                body.byteStream().use { input ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        ctx.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        written += n
                        if (total > 0) {
                            _state.update {
                                it + (
                                    song.id to K4DownloadState(
                                        song.id,
                                        ((written * 100) / total).toInt().coerceIn(0, 100)
                                    )
                                    )
                            }
                        }
                    }
                }
            }
        }

        if (written == 0L || (total > 0 && written != total)) {
            cleanupPartial(song)
            _state.update {
                it + (song.id to K4DownloadState(song.id, 0, error = "Connection ended early"))
            }
            return@withContext false
        }

        val target = File(dir, "${offlineFileKey(song.id)}${extensionFor(servedType, url)}")
        if (target.exists()) target.delete()
        check(part.renameTo(target)) { "Could not finish download safely" }
        _state.update { it + (song.id to K4DownloadState(song.id, 100, done = true)) }
        true
    }

    private fun cleanupPartial(song: K4Song) {
        dir.listFiles()
            ?.filter { it.nameWithoutExtension == offlineFileKey(song.id) && it.name.endsWith(".part") }
            ?.forEach { runCatching { it.delete() } }
    }

    private fun extensionFor(servedType: String?, url: String): String {
        val fromType = when {
            servedType == null -> ""
            servedType.contains("mpeg", true) -> ".mp3"
            servedType.contains("mp4", true) -> ".m4a"
            servedType.contains("flac", true) -> ".flac"
            servedType.contains("ogg", true) || servedType.contains("opus", true) -> ".ogg"
            else -> ""
        }
        if (fromType.isNotEmpty()) return fromType
        val path = url.substringBefore('?').substringAfterLast('/')
        val fromPath = if ('.' in path) ".${path.substringAfterLast('.').lowercase()}" else ""
        return if (fromPath in KNOWN_EXTENSIONS) fromPath else ".mp3"
    }

    fun dismiss(songId: String) {
        _state.update { it - songId }
    }

    /** Total bytes of finished offline tracks (`.part` files excluded). */
    suspend fun usedBytes(): Long = withContext(Dispatchers.IO) {
        dir.listFiles()
            ?.filterNot { it.name.endsWith(".part") }
            ?.sumOf { it.length() } ?: 0L
    }

    private companion object {
        val KNOWN_EXTENSIONS = setOf(".mp3", ".m4a", ".flac", ".wav", ".ogg", ".opus")
        const val TAG = "K4Download"
    }
}
