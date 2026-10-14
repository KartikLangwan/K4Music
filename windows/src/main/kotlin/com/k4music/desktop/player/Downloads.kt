package com.k4music.desktop.player

import com.k4music.desktop.AppLog
import com.k4music.desktop.AppPaths
import com.k4music.desktop.data.Db
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Offline songs. A download resolves the stream exactly like playback (so it gets the same
 * fallbacks), writes to `<id>.part` and renames on success; a broken download never shows as done.
 */
object Downloads {
    private const val PARALLEL = 4
    private val dir get() = AppPaths.downloads
    private val indexFile get() = dir.resolve("downloads.properties")
    private val index by lazy { Properties().also { p -> runCatching { indexFile.inputStream().use(p::load) } } }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val permits = Semaphore(PARALLEL)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    lateinit var resolver: StreamResolver

    /** Ids of finished downloads. */
    private val _done = MutableStateFlow(loadDone())
    val done: StateFlow<Set<String>> = _done.asStateFlow()

    /** Download progress 0..1 per id while running. */
    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val progress: StateFlow<Map<String, Float>> = _progress.asStateFlow()

    @Synchronized private fun loadDone(): Set<String> =
        index.stringPropertyNames().filterTo(HashSet()) { id -> File(dir, index.getProperty(id)).isFile }

    @Synchronized fun file(id: String): File? = index.getProperty(id)?.let { File(dir, it) }?.takeIf { it.isFile }

    fun isDownloaded(id: String) = id in _done.value

    fun download(track: Track) {
        if (track.isLocal || isDownloaded(track.id) || jobs.containsKey(track.id)) return
        _progress.update { it + (track.id to 0f) }
        jobs[track.id] = scope.launch {
            try {
                permits.withPermit { fetch(track) }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) AppLog.w("Downloads", "Download failed for ${track.id}", e)
            } finally {
                jobs.remove(track.id)
                _progress.update { it - track.id }
            }
        }
    }

    fun cancel(id: String) { jobs.remove(id)?.cancel() }

    @Synchronized
    fun delete(id: String) {
        cancel(id)
        index.getProperty(id)?.let { File(dir, it).delete() }
        index.remove(id)
        save()
        _done.update { it - id }
    }

    fun deleteAll() = _done.value.forEach(::delete)

    private suspend fun fetch(track: Track) {
        Db.library.insert(track)
        val stream = resolver.resolve(track)
        val request = Request.Builder().url(stream.url).apply {
            stream.headers.forEach { (k, v) -> header(k, v) }
            // YouTube throttles a request without a Range header to ~30 KB/s; an open range comes at
            // full speed (measured: 31 KB/s vs 14.8 MB/s).
            if (stream.url.contains("googlevideo.com")) header("Range", "bytes=0-")
        }.build()
        http.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code}" }
            val body = response.body ?: error("Empty response")
            val extension = when {
                body.contentType()?.subtype?.contains("webm") == true -> "webm"
                stream.url.contains(".mp4") || body.contentType()?.subtype?.contains("mp4") == true -> "m4a"
                else -> "audio"
            }
            val name = "${safeName(track.id)}.$extension"
            val part = File(dir, "$name.part")
            val total = body.contentLength().takeIf { it > 0 }
            body.byteStream().use { input ->
                part.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var written = 0L
                    while (true) {
                        kotlin.coroutines.coroutineContext.ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        written += n
                        if (total != null) _progress.update { it + (track.id to (written.toFloat() / total).coerceIn(0f, 1f)) }
                    }
                }
            }
            val target = File(dir, name)
            check(part.renameTo(target)) { "Could not finish $name" }
            synchronized(this) {
                index.setProperty(track.id, name)
                save()
            }
            _done.update { it + track.id }
            AppLog.i("Downloads", "Downloaded ${track.id} (${target.length() / 1024} KB)")
        }
    }

    private fun safeName(id: String) = id.replace(Regex("[^A-Za-z0-9_-]"), "_").takeLast(80)

    private fun save() = runCatching { indexFile.outputStream().use { index.store(it, "K4 Music downloads") } }
}
