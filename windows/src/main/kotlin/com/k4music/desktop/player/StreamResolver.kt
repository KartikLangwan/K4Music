package com.k4music.desktop.player

import com.k4music.desktop.AppLog
import com.k4music.desktop.AppPaths
import com.k4music.desktop.k4.StreamFallback
import com.k4music.desktop.k4.findSaavnStream
import com.k4music.desktop.k4.resolveSaavnStream
import com.k4music.desktop.prefs.Settings
import com.k4music.providers.innertube.Innertube
import com.k4music.providers.innertube.models.bodies.PlayerBody
import com.k4music.providers.innertube.requests.player
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
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Where a resolved stream came from; decides how a later failure is handled. */
enum class StreamSource { Local, Direct, YtDlp, Saavn }

/** [temporary]: a stand-in while YouTube was slow; not cached, so the next play tries YouTube again. */
data class Stream(val url: String, val source: StreamSource, val headers: Map<String, String> = emptyMap(), val temporary: Boolean = false)

/** Nothing could serve this song (not a connection problem). */
class UnplayableException(message: String) : Exception(message)

/**
 * Same chain as the Android player (PlayerService.createYouTubeDataSourceResolverFactory):
 * YouTube direct audio url -> yt-dlp (15 s deadline) -> the same song on JioSaavn
 * (Smart fallback) -> unplayable. Resolved urls are cached per song for 15 minutes.
 */
class StreamResolver {
    private data class Cached(val stream: Stream, val at: Long)
    private val cache = ConcurrentHashMap<String, Cached>()
    /** Songs whose direct Innertube url the CDN refused: go straight to yt-dlp. */
    private val rejectedDirect = ConcurrentHashMap.newKeySet<String>()
    /**
     * Refusals of direct urls in a row. YouTube currently refuses them all (they need a token),
     * so after two the dead step is skipped (remembered across restarts, retried once a day);
     * a direct url that plays resets it.
     */
    private var directRefusals by Settings::directRefusals

    fun directPlayed() { if (directRefusals != 0) directRefusals = 0 }

    fun forget(id: String) { cache.remove(id) }

    /** The current [Stream] of [track] was refused; the next resolve picks the next source. */
    fun reject(track: Track, stream: Stream) {
        forget(track.id)
        when (stream.source) {
            StreamSource.Direct -> {
                if (rejectedDirect.size >= 64) rejectedDirect.clear()
                rejectedDirect += track.id
                directRefusals = (directRefusals + 1).coerceAtMost(2)
                Settings.directRefusedAt = System.currentTimeMillis()
            }
            StreamSource.Saavn -> if (!track.isSaavn) StreamFallback.drop(track.id)
            else -> Unit
        }
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
            else -> (if (fallbackOn) youtubeOrQuickFallback(track) else youtube(track))
                ?: (if (fallbackOn) saavnFallback(track) else null)
                ?: throw UnplayableException("No playable stream")
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
        val yt = async { youtube(track) }
        val quick = kotlinx.coroutines.withTimeoutOrNull(SLOW_YOUTUBE_MS) { yt.await() }
        if (quick != null || yt.isCompleted) return@coroutineScope quick ?: yt.await()
        AppLog.w(TAG, "YouTube is slow for ${track.id}; trying JioSaavn meanwhile")
        val saavn = saavnFallback(track, remember = false)
        if (saavn != null && !yt.isCompleted) { yt.cancel(); return@coroutineScope saavn.copy(temporary = true) }
        yt.await() ?: saavn?.copy(temporary = true)
    }

    private suspend fun youtube(track: Track): Stream? {
        if (track.id !in rejectedDirect && (directRefusals < 2 || System.currentTimeMillis() - Settings.directRefusedAt > DAY_MS)) {
            val body = Innertube.player(PlayerBody(videoId = track.id))?.let { result ->
                result.exceptionOrNull()?.let { if (it is IOException) throw it }
                result.getOrNull()
            }
            // A direct url only plays with the User-Agent of the client that asked for it (iOS, TV…).
            val userAgent = body?.context?.client?.userAgent
            AppLog.i(TAG, "Innertube client ${body?.context?.client?.clientName} for ${track.id}: format ${body?.streamingData?.audioFormat(Settings.audioQuality.youtubeItags)?.itag}")
            body?.streamingData?.audioFormat(Settings.audioQuality.youtubeItags)?.url
                ?.takeIf { it.isNotBlank() }
                ?.let { return Stream(it, StreamSource.Direct, userAgent?.let { ua -> mapOf("User-Agent" to ua) }.orEmpty()) }
        }
        return ytDlp(track.id)
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
            "-f", "bestaudio/best", "--socket-timeout", "12", "--retries", "2",
            "--js-runtimes", "quickjs:${qjs.absolutePath}",
            "https://www.youtube.com/watch?v=$videoId"
        ).apply {
            // yt-dlp unpacks itself into TEMP; keep that inside K4 Music's own folder.
            environment()["TEMP"] = temp.absolutePath
            environment()["TMP"] = temp.absolutePath
            redirectError(ProcessBuilder.Redirect.DISCARD)
        }.start()
        try {
            val output = StringBuilder()
            val reader = Thread { output.append(process.inputStream.bufferedReader().readText()) }.apply { start() }
            if (!process.waitFor(YT_DLP_TIMEOUT_S, TimeUnit.SECONDS)) {
                AppLog.w(TAG, "yt-dlp timed out for $videoId")
                return@runInterruptible null
            }
            reader.join(2_000)
            val info = runCatching { json.decodeFromString(YtDlpInfo.serializer(), output.toString()) }
                .onFailure { AppLog.w(TAG, "yt-dlp gave no usable answer for $videoId (exit ${process.exitValue()})") }
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
        const val DAY_MS = 24 * 60 * 60 * 1000L
        const val SLOW_YOUTUBE_MS = 7_000L
        const val YT_DLP_TIMEOUT_S = 15L
        val json = Json { ignoreUnknownKeys = true; isLenient = true }
    }
}
