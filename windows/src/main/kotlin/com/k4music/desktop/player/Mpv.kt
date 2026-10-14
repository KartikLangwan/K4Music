package com.k4music.desktop.player

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.ptr.DoubleByReference
import kotlin.concurrent.thread

/** The small slice of libmpv's client API that K4 Music uses (mpv/client.h). */
@Suppress("FunctionName", "LocalVariableName")
internal interface LibMpv : Library {
    fun mpv_create(): Pointer?
    fun mpv_initialize(ctx: Pointer): Int
    fun mpv_terminate_destroy(ctx: Pointer)
    fun mpv_set_option_string(ctx: Pointer, name: String, data: String): Int
    fun mpv_set_property_string(ctx: Pointer, name: String, data: String): Int
    fun mpv_get_property(ctx: Pointer, name: String, format: Int, data: Pointer): Int
    fun mpv_get_property_string(ctx: Pointer, name: String): Pointer?
    fun mpv_free(data: Pointer)
    fun mpv_command(ctx: Pointer, args: Array<String?>): Int
    fun mpv_observe_property(ctx: Pointer, replyUserdata: Long, name: String, format: Int): Int
    fun mpv_wait_event(ctx: Pointer, timeout: Double): Pointer
    fun mpv_wakeup(ctx: Pointer)
    fun mpv_error_string(error: Int): String
    fun mpv_request_log_messages(ctx: Pointer, minLevel: String): Int

    companion object {
        const val FORMAT_FLAG = 3
        const val FORMAT_DOUBLE = 5

        const val EVENT_NONE = 0
        const val EVENT_SHUTDOWN = 1
        const val EVENT_LOG_MESSAGE = 2
        const val EVENT_END_FILE = 7
        const val EVENT_FILE_LOADED = 8
        const val EVENT_PLAYBACK_RESTART = 21
        const val EVENT_PROPERTY_CHANGE = 22

        const val END_EOF = 0
        const val END_ERROR = 4
    }
}

@Suppress("unused")
@Structure.FieldOrder("event_id", "error", "reply_userdata", "data")
internal class MpvEvent(p: Pointer) : Structure(p) {
    @JvmField var event_id = 0
    @JvmField var error = 0
    @JvmField var reply_userdata = 0L
    @JvmField var data: Pointer? = null
    init { read() }
}

@Suppress("unused")
@Structure.FieldOrder("prefix", "level", "text", "log_level")
internal class MpvLogMessage(p: Pointer) : Structure(p) {
    @JvmField var prefix: String? = null
    @JvmField var level: String? = null
    @JvmField var text: String? = null
    @JvmField var log_level = 0
    init { read() }
}

@Suppress("unused")
@Structure.FieldOrder("reason", "error")
internal class MpvEndFile(p: Pointer) : Structure(p) {
    @JvmField var reason = 0
    @JvmField var error = 0
    init { read() }
}

@Suppress("unused")
@Structure.FieldOrder("name", "format", "data")
internal class MpvProperty(p: Pointer) : Structure(p) {
    @JvmField var name: String? = null
    @JvmField var format = 0
    @JvmField var data: Pointer? = null
    init { read() }
}

/**
 * One libmpv audio player. Events arrive on a background thread through [Listener];
 * every call is safe from any thread (libmpv's client API is thread-safe).
 */
class Mpv(libraryDir: java.io.File) : AutoCloseable {
    interface Listener {
        fun onPosition(seconds: Double) {}
        fun onDuration(seconds: Double) {}
        fun onPaused(paused: Boolean) {}
        fun onBuffering(buffering: Boolean) {}
        fun onLoaded() {}
        /** The current file ended: [error] is null at a normal end, otherwise mpv's error text. */
        fun onEnded(error: String?) {}
    }

    private val lib: LibMpv
    private val ctx: Pointer
    @Volatile private var listener: Listener? = null
    @Volatile private var closed = false
    private val events: Thread

    init {
        System.setProperty("jna.library.path", libraryDir.absolutePath)
        // UTF-8, not the ANSI code page: local file paths and titles can be any language.
        lib = Native.load("libmpv-2", LibMpv::class.java, mapOf(Library.OPTION_STRING_ENCODING to "UTF-8"))
        ctx = lib.mpv_create() ?: error("mpv_create failed")
        mapOf(
            "vid" to "no", "video" to "no", "audio-display" to "no",
            "ytdl" to "no", "terminal" to "no", "input-default-bindings" to "no",
            "idle" to "yes", "keep-open" to "no",
            "cache" to "yes", "demuxer-max-bytes" to "64MiB", "demuxer-readahead-secs" to "30",
            "network-timeout" to "15", "audio-client-name" to "K4 Music",
            "volume-max" to "100", "gapless-audio" to "weak",
            // YouTube throttles one long download; ranged requests (as ExoPlayer makes) arrive at full speed.
            "curl-max-request-size" to "1MiB"
        ).forEach { (name, value) -> lib.mpv_set_option_string(ctx, name, value) }
        check(lib.mpv_initialize(ctx) >= 0) { "mpv_initialize failed" }

        // mpv's own warnings and errors go to the app log (why a stream failed, etc.).
        lib.mpv_request_log_messages(ctx, System.getProperty("k4.mpvLog") ?: "warn")
        lib.mpv_observe_property(ctx, 1, "time-pos", LibMpv.FORMAT_DOUBLE)
        lib.mpv_observe_property(ctx, 2, "duration", LibMpv.FORMAT_DOUBLE)
        lib.mpv_observe_property(ctx, 3, "pause", LibMpv.FORMAT_FLAG)
        lib.mpv_observe_property(ctx, 4, "paused-for-cache", LibMpv.FORMAT_FLAG)
        events = thread(isDaemon = true, name = "mpv-events") { eventLoop() }
    }

    fun setListener(listener: Listener) { this.listener = listener }

    /** Plays [url] from [startSeconds], with per-file HTTP headers (yt-dlp streams need them). */
    fun load(url: String, startSeconds: Double = 0.0, headers: Map<String, String> = emptyMap()) {
        command("change-list", "http-header-fields", "clr", "")
        set("user-agent", headers.entries.firstOrNull { it.key.equals("User-Agent", true) }?.value ?: DEFAULT_USER_AGENT)
        headers.filterKeys { !it.equals("User-Agent", true) }
            .forEach { (key, value) -> command("change-list", "http-header-fields", "append", "$key: $value") }
        val options = if (startSeconds > 0.5) "start=${"%.2f".format(java.util.Locale.ROOT, startSeconds)}" else ""
        command("loadfile", url, "replace", "-1", options)
    }

    fun stop() = command("stop")
    fun seek(seconds: Double) = command("seek", "%.3f".format(java.util.Locale.ROOT, seconds), "absolute")

    fun setPaused(paused: Boolean) = set("pause", if (paused) "yes" else "no")
    fun setVolume(percent: Int) = set("volume", percent.coerceIn(0, 100).toString())
    fun setMuted(muted: Boolean) = set("mute", if (muted) "yes" else "no")
    fun setSpeed(speed: Float) = set("speed", speed.toString())
    /** Audio filter chain (equalizer, effects, normalization, skip silence). Empty clears it. */
    fun setAudioFilters(chain: String) = set("af", chain)
    fun set(name: String, value: String) { if (!closed) lib.mpv_set_property_string(ctx, name, value) }

    fun position(): Double? = getDouble("time-pos")

    /** A property as text (e.g. "audio-codec-name"), or null when unavailable. */
    fun getString(name: String): String? {
        if (closed) return null
        val p = lib.mpv_get_property_string(ctx, name) ?: return null
        return try { p.getString(0, "UTF-8") } finally { lib.mpv_free(p) }
    }

    /** mpv's real pause flag (events only fire on change, so state must not rely on them alone). */
    fun isPaused(): Boolean {
        if (closed) return true
        val out = com.sun.jna.ptr.IntByReference()
        return lib.mpv_get_property(ctx, "pause", LibMpv.FORMAT_FLAG, out.pointer) < 0 || out.value != 0
    }

    private fun getDouble(name: String): Double? {
        if (closed) return null
        val out = DoubleByReference()
        return if (lib.mpv_get_property(ctx, name, LibMpv.FORMAT_DOUBLE, out.pointer) >= 0) out.value else null
    }

    private fun command(vararg args: String) {
        if (!closed) lib.mpv_command(ctx, arrayOf(*args, null))
    }

    private fun eventLoop() {
        while (!closed) {
            val event = MpvEvent(lib.mpv_wait_event(ctx, -1.0))
            val l = listener
            when (event.event_id) {
                LibMpv.EVENT_SHUTDOWN -> return
                LibMpv.EVENT_LOG_MESSAGE -> event.data?.let { MpvLogMessage(it) }?.let { m ->
                    com.k4music.desktop.AppLog.write(when (m.level) { "warn" -> "W"; "error", "fatal" -> "E"; else -> "I" }, "mpv/${m.prefix}", m.text.orEmpty().trimEnd(), null)
                }
                LibMpv.EVENT_FILE_LOADED -> l?.onLoaded()
                LibMpv.EVENT_PLAYBACK_RESTART -> l?.onBuffering(false)
                LibMpv.EVENT_END_FILE -> event.data?.let { MpvEndFile(it) }?.let { end ->
                    when (end.reason) {
                        LibMpv.END_EOF -> l?.onEnded(null)
                        LibMpv.END_ERROR -> l?.onEnded(lib.mpv_error_string(end.error))
                    }
                }
                LibMpv.EVENT_PROPERTY_CHANGE -> event.data?.let { MpvProperty(it) }?.let { property ->
                    val data = property.data ?: return@let
                    when (property.name) {
                        "time-pos" -> l?.onPosition(data.getDouble(0))
                        "duration" -> l?.onDuration(data.getDouble(0))
                        "pause" -> l?.onPaused(data.getInt(0) != 0)
                        "paused-for-cache" -> l?.onBuffering(data.getInt(0) != 0)
                    }
                }
            }
        }
    }

    /** Stops the event thread before freeing the handle it waits on. */
    private companion object {
        const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
    }

    override fun close() {
        if (closed) return
        closed = true
        lib.mpv_wakeup(ctx)
        events.join(2_000)
        lib.mpv_terminate_destroy(ctx)
    }
}
