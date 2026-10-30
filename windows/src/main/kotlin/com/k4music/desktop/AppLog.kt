package com.k4music.desktop

import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Plain-text app log in the data folder, rotated at ~1 MB; also echoed to stderr. */
object AppLog {
    private const val MAX_BYTES = 1_000_000L
    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    val file get() = AppPaths.logs.resolve("k4audio.log")

    @Synchronized
    fun write(level: String, tag: String, message: String, error: Throwable?): Int {
        val line = buildString {
            append(LocalDateTime.now().format(stamp)).append(' ').append(level).append(' ')
            append(tag).append(": ").append(message)
            if (error != null) append('\n').append(StringWriter().also { error.printStackTrace(PrintWriter(it)) })
        }
        System.err.println(line)
        runCatching {
            val log = file
            if (log.length() > MAX_BYTES) log.renameTo(log.resolveSibling("k4audio.1.log").also { it.delete() })
            log.appendText(line + "\n")
        }
        return 0
    }

    fun i(tag: String, message: String) = write("I", tag, message, null)
    fun w(tag: String, message: String, error: Throwable? = null) = write("W", tag, message, error)
    fun e(tag: String, message: String, error: Throwable? = null) = write("E", tag, message, error)
}
