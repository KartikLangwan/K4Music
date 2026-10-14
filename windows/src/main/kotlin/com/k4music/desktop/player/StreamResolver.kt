package com.k4music.desktop.player

import com.k4music.desktop.AppLog
import com.k4music.desktop.AppPaths
import com.k4music.desktop.k4.StreamFallback
import com.k4music.desktop.k4.findSaavnStream
import com.k4music.desktop.k4.resolveSaavnStream
import com.k4music.desktop.prefs.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Where a resolved stream came from; decides how a later failure is handled. */
enum class StreamSource { Local, YtDlp, Saavn }

/** [temporary]: a stand-in while YouTube was slow; not cached, so the next play tries YouTube again. */
data class Stream(val url: String, val source: StreamSource, val headers: Map<String, String> = emptyMap(), val temporary: Boolean = false)

/** Nothing could serve this song (not a connection problem). */
class UnplayableException(message: String) : Exception(message)

/**
 * yt-dlp (15 s deadline) -> the same song on JioSaavn (Smart fallback) -> unplayable.
 * Resolved urls are cached per song for 15 minutes.
 *
 * No direct Innertube url step (Android's first step): on PC those urls play the first
 * megabyte and are then refused (HTTP 403), which cut songs off half way.
 */
class StreamResolver {
    private data class Cached(val stream: Stream, val at: Long)
    private val cache = ConcurrentHashMap<String, Cached>()
    fun forget(id: String) { cache.remove(id) }

    /** The current [Stream] of [track] was refused; the next resolve picks the next source. */
    fun reject(track: Track, stream: Stream) {
        forget(track.id)
        if (stream.source == StreamSource.Saavn && !track.isSaavn) StreamFallback.drop(track.id)
    }

    /** Resolves running right now, by song: a hover prefetch and the click share one yt-dlp run. */
    private val inFlight = ConcurrentHashMap<String, kotlinx.coroutines.Deferred<Stream>>()
    private val background = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.IO)
    /** At most two speculative yt-dlp runs at once, so hovering a long list stays cheap. */
    private val prefetchSlots = kotlinx.coroutines.sync.Semaphore(2)

    suspend fun resolve(track: Track): Stream {
        val job = inFlight.computeIfAbsent(track.id) { id ->
            background.async { resolveNow(track) }.also { d -> d.invokeOnCompletion { inFlight.remove(id, d) } }
        }
        return try { job.await() } finally { if (job.isCompleted) inFlight.remove(track.id, job) }
    }

    /** Resolves [track] ahead of a likely click (hover, next in queue); errors are ignored. */
    fun prefetch(track: Track) {
        if (track.isLocal || cache[track.id]?.let { System.currentTimeMillis() - it.at < TTL_MS } == true || inFlight.containsKey(track.id)) return
        background.launch { prefetchSlots.withPermit { runCatching { resolve(track) } } }
    }

    private suspend fun resolveNow(track: Track): Stream = withContext(Dispatchers.IO) {
        // A downloaded song always plays from disk: offline-safe and instant.
        Downloads.file(track.id)?.let { return@withContext Stream(it.absolutePath, StreamSource.Local) }
        if (track.isLocal) {
            val file = File(track.localPath)
            if (!file.isFile) throw UnplayableException("File not found: ${file.name}")
            return@withContext Stream(file.absolutePath, StreamSource.Local)
        }
        val fallbackOn = Settings.smartFallback
        if (!fallbackOn && StreamFallback.url(track.id) != null) {
            StreamFallback.drop(track.id) // fallback turned off: back to YouTube
            forget(track.id)
        }
        if (fallbackOn && StreamFallback.isForced(track.id)) {
            return@withContext saavnFallback(track) ?: throw UnplayableException("No JioSaavn match")
        }
        cache[track.id]?.takeIf { System.currentTimeMillis() - it.at < TTL_MS }?.let { return@withContext it.stream }

        val stream = when {
            track.isSaavn -> resolveSaavnStream(track.id, Settings.audioQuality.saavn)
                ?.let { Stream(it, StreamSource.Saavn) }
                ?: throw UnplayableException("JioSaavn stream unavailable")
            fallbackOn && StreamFallback.url(track.id) != null ->
                Stream(StreamFallback.url(track.id)!!, StreamSource.Saavn)
            else -> {
                val youtube = when {
                    youtubeBlocked() -> null // don't keep knocking while YouTube is blocking this network
                    fallbackOn -> youtubeOrQuickFallback(track)
                    else -> ytDlp(track.id)
                }
                // A blocked network is not this song's fault: play JioSaavn for now, YouTube again later.
                val blocked = youtubeBlocked()
                youtube
                    ?: (if (fallbackOn) saavnFallback(track, remember = !blocked)?.let { if (blocked) it.copy(temporary = true) else it } else null)
                    ?: throw UnplayableException(if (blocked) "YouTube is blocking this network for now" else "No playable stream")
            }
        }
        if (!stream.temporary) cache[track.id] = Cached(stream, System.currentTimeMillis())
        stream
    }

    /**
     * YouTube normally answers in 2-3 s. When it is slow (bad network, YouTube busy), the same song
     * is looked up on JioSaavn meanwhile and whichever is ready first plays, instead of waiting out
     * yt-dlp's full deadline.
     */
    private suspend fun youtubeOrQuickFallback(track: Track): Stream? = kotlinx.coroutines.coroutineScope {
        val yt = async { ytDlp(track.id) }
        val quick = kotlinx.coroutines.withTimeoutOrNull(SLOW_YOUTUBE_MS) { yt.await() }
        if (quick != null || yt.isCompleted) return@coroutineScope quick ?: yt.await()
        AppLog.w(TAG, "YouTube is slow for ${track.id}; trying JioSaavn meanwhile")
        val saavn = saavnFallback(track, remember = false)
        if (saavn != null && !yt.isCompleted) { yt.cancel(); return@coroutineScope saavn.copy(temporary = true) }
        yt.await() ?: saavn?.copy(temporary = true)
    }

    /** Runs the bundled yt-dlp.exe with QuickJS for YouTube's JS challenge. Null on failure/timeout. */
    private suspend fun ytDlp(videoId: String): Stream? = runInterruptible(Dispatchers.IO) {
        val exe = AppPaths.tools.resolve("yt-dlp/yt-dlp.exe")
        val qjs = AppPaths.tools.resolve("qjs.exe")
        if (!exe.isFile) return@runInterruptible null
        val temp = AppPaths.local.resolve("tmp").apply { mkdirs() }
        val process = ProcessBuilder(
            // The cache keeps YouTube's player script and solved challenges: later songs resolve much faster.
            exe.absolutePath, "-J", "--no-warnings", "--no-playlist",
            "--cache-dir", AppPaths.local.resolve("yt-dlp-cache").absolutePath,
            // Streaming quality (Settings) picks the format; any audio if those are missing.
            "-f", (Settings.audioQuality.youtubeItags.map { it.toString() } + "bestaudio" + "best").joinToString("/"), "--socket-timeout", "12", "--retries", "2",
            "--js-runtimes", "quickjs:${qjs.absolutePath}",
            "https://www.youtube.com/watch?v=$videoId"
        ).apply {
            // yt-dlp unpacks itself into TEMP; keep that inside K4 Music's own folder.
            environment()["TEMP"] = temp.absolutePath
            environment()["TMP"] = temp.absolutePath
        }.start()
        try {
            val output = StringBuilder()
            val errors = StringBuilder()
            val reader = Thread { output.append(process.inputStream.bufferedReader().readText()) }.apply { start() }
            val errorReader = Thread { errors.append(process.errorStream.bufferedReader().readText().takeLast(2_000)) }.apply { start() }
            if (!process.waitFor(YT_DLP_TIMEOUT_S, TimeUnit.SECONDS)) {
                AppLog.w(TAG, "yt-dlp timed out for $videoId")
                return@runInterruptible null
            }
            reader.join(2_000)
            errorReader.join(1_000)
            val info = runCatching { json.decodeFromString(YtDlpInfo.serializer(), output.toString()) }
                .onFailure {
                    val error = errors.lines().lastOrNull { it.startsWith("ERROR") }.orEmpty().take(200)
                    AppLog.w(TAG, "yt-dlp gave no usable answer for $videoId (exit ${process.exitValue()}): $error")
                    if (BLOCKED_SIGNS.any { it in error }) onYoutubeBlocked()
                }
                .getOrNull() ?: return@runInterruptible null
            if (info.id != videoId) return@runInterruptible null
            val url = info.url?.takeIf { it.isNotBlank() }
                ?: info.formats?.firstOrNull { it.formatId == info.formatId }?.url?.takeIf { it.isNotBlank() }
                ?: return@runInterruptible null
            Stream(url, StreamSource.YtDlp, info.httpHeaders)
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }

    /**
     * YouTube sometimes refuses every request from a network for a while ("Sign in to confirm you're
     * not a bot", HTTP 429). Then yt-dlp rests for [BLOCK_PAUSE_MS] and songs play from JioSaavn
     * without being pinned to it.
     */
    @Volatile private var blockedUntil = 0L
    /** Called (from a background thread) when YouTube starts blocking this network. */
    var onBlocked: () -> Unit = {}
    private fun youtubeBlocked() = System.currentTimeMillis() < blockedUntil
    private fun onYoutubeBlocked() {
        val wasBlocked = youtubeBlocked()
        blockedUntil = System.currentTimeMillis() + BLOCK_PAUSE_MS
        if (!wasBlocked) {
            AppLog.w(TAG, "YouTube is blocking this network; JioSaavn for the next ${BLOCK_PAUSE_MS / 60_000} minutes")
            onBlocked()
        }
    }

    /** [remember]: save the match so later plays go straight to JioSaavn (YouTube refused the song). */
    private suspend fun saavnFallback(track: Track, remember: Boolean = true): Stream? {
        val url = runCatching {
            findSaavnStream(track.title, track.artists.orEmpty(), track.durationMs, Settings.audioQuality.saavn)
        }.getOrNull()
        if (remember) StreamFallback.resolved(track.id, url)
        if (url == null) {
            AppLog.w(TAG, "No JioSaavn fallback for ${track.id} (${track.title})")
            return null
        }
        AppLog.i(TAG, "Playing ${track.id} from the JioSaavn fallback")
        return Stream(url, StreamSource.Saavn)
    }

    @Serializable
    private data class YtDlpInfo(
        val id: String,
        @SerialName("format_id") val formatId: String? = null,
        val url: String? = null,
        val formats: List<Format>? = null,
        @SerialName("http_headers") val httpHeaders: Map<String, String> = emptyMap()
    ) {
        @Serializable
        data class Format(@SerialName("format_id") val formatId: String, val url: String? = null)
    }

    private companion object {
        const val TAG = "StreamResolver"
        const val TTL_MS = 15 * 60 * 1000L
        const val SLOW_YOUTUBE_MS = 10_000L
        const val YT_DLP_TIMEOUT_S = 15L
        const val BLOCK_PAUSE_MS = 10 * 60 * 1000L
        /** yt-dlp errors that mean "YouTube is refusing this network", not "this song is unavailable". */
        val BLOCKED_SIGNS = listOf("not a bot", "HTTP Error 429", "LOGIN_REQUIRED", "page needs to be reloaded")
        val json = Json { ignoreUnknownKeys = true; isLenient = true }
    }
}
