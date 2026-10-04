package com.k4music.android.utils

import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Last line of defence for the whole app: wherever an exception escapes
 * uncaught, it is logged and written to `filesDir/crashes.log` so a crash is
 * never silent, then handed to the platform handler as usual.
 *
 * ponytail: log + delegate, no auto-restart. Relaunching a half-dead process
 * turns one crash into a crash loop; recording it keeps the failure visible
 * and recoverable instead.
 */
object CrashGuard {

    private const val TAG = "K4MusicCrash"
    private const val FILE_NAME = "crashes.log"
    private const val MAX_BYTES = 256 * 1024

    @Volatile
    private var directory: File? = null

    @Volatile
    private var previous: Thread.UncaughtExceptionHandler? = null

    private val handler = Thread.UncaughtExceptionHandler { thread, error ->
        runCatching { directory?.let { record(it, thread.name, error) } }
            .onFailure { it.printStackTrace() }

        val delegate = previous
        if (delegate != null) delegate.uncaughtException(thread, error)
        else {
            Log.e(TAG, "Uncaught crash on ${thread.name}", error)
            android.os.Process.killProcess(android.os.Process.myPid())
        }
    }

    /** Idempotent: calling it again only re-points where crashes are recorded. */
    fun install(filesDir: File) {
        directory = filesDir

        val current = Thread.getDefaultUncaughtExceptionHandler()
        if (current === handler) return

        previous = current
        Thread.setDefaultUncaughtExceptionHandler(handler)
    }

    /**
     * Recorded crashes for the in-app diagnostics screen (newest last).
     * Empty string means a healthy app — never null, never throws.
     */
    fun readLog(filesDir: File, maxChars: Int = 16_000): String {
        val file = File(filesDir, FILE_NAME)
        if (!file.exists() || file.length() == 0L) return ""
        return runCatching { file.readText() }
            .getOrNull()
            .orEmpty()
            .takeIf { it.isNotBlank() }
            .orEmpty()
            .takeLast(maxChars.coerceAtLeast(0))
    }

    private fun record(filesDir: File, threadName: String, error: Throwable) {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val entry = buildString {
            append("---- ").append(stamp).append(" on ").append(threadName).append(" ----\n")
            append(error.stackTraceToString().take(8_000)).append('\n')
        }

        val file = File(filesDir, FILE_NAME)

        runCatching {
            // Keep the log bounded: one huge stack dump is enough to fill storage.
            if (file.length() > MAX_BYTES) file.delete()
            file.appendText(entry)
        }
    }
}
