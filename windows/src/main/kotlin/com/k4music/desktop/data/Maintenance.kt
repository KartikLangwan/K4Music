package com.k4music.desktop.data

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.k4music.desktop.AppLog
import com.k4music.desktop.AppPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/** Library backup / restore (compatible with the Android app's backups) and tool updates. */
object Maintenance {
    private const val SCHEMA_VERSION = 30L
    private val pending get() = AppPaths.database.resolveSibling("data.db.restore")

    /** Writes a consistent copy of the library to [target]. */
    suspend fun backup(target: File) = withContext(Dispatchers.IO) {
        Db.library.checkpoint()
        AppPaths.database.copyTo(target, overwrite = true)
        AppLog.i("Backup", "Library saved to $target")
    }

    /**
     * Checks [source] (integrity, a Song table, schema 30 — phone or PC backup) and stages it; the
     * swap happens at the next start, before the library opens, so a bad file never replaces it.
     */
    suspend fun stageRestore(source: File) = withContext(Dispatchers.IO) {
        val staged = File(pending.path + ".check")
        source.copyTo(staged, overwrite = true)
        try {
            BundledSQLiteDriver().open(staged.absolutePath).use { db ->
                db.prepare("PRAGMA integrity_check").use { check(it.step() && it.getText(0) == "ok") { "The backup file is damaged" } }
                db.prepare("PRAGMA user_version").use { check(it.step() && it.getLong(0) == SCHEMA_VERSION) { "This backup is from an incompatible version" } }
                db.prepare("SELECT id, title FROM Song LIMIT 1").use { it.step() }
                db.execSQL("PRAGMA wal_checkpoint(TRUNCATE)")
            }
            staged.copyTo(pending, overwrite = true)
        } finally {
            staged.delete()
        }
    }

    /** Called at start-up, before the library opens. */
    fun applyPendingRestore() {
        if (!pending.isFile) return
        runCatching {
            val db = AppPaths.database
            listOf("-wal", "-shm").forEach { File(db.path + it).delete() }
            if (db.isFile) db.copyTo(File(db.path + ".before-restore"), overwrite = true)
            pending.copyTo(db, overwrite = true)
            pending.delete()
            AppLog.i("Backup", "Library restored from backup")
        }.onFailure { AppLog.e("Backup", "Restore failed; keeping the current library", it) }
    }

    fun hasPendingRestore() = pending.isFile

    /** Runs `yt-dlp -U`; returns its last output line. */
    suspend fun updateYtDlp(): String = withContext(Dispatchers.IO) {
        val exe = AppPaths.tools.resolve("yt-dlp/yt-dlp.exe")
        val process = ProcessBuilder(exe.absolutePath, "-U").redirectErrorStream(true).apply {
            val temp = AppPaths.local.resolve("tmp").apply { mkdirs() }
            environment()["TEMP"] = temp.absolutePath
            environment()["TMP"] = temp.absolutePath
        }.start()
        val output = process.inputStream.bufferedReader().readText()
        if (!process.waitFor(120, TimeUnit.SECONDS)) process.destroyForcibly()
        output.lines().lastOrNull { it.isNotBlank() }.orEmpty().also { AppLog.i("Maintenance", "yt-dlp -U: $it") }
    }

    fun ytDlpVersion(): String = runCatching {
        val process = ProcessBuilder(AppPaths.tools.resolve("yt-dlp/yt-dlp.exe").absolutePath, "--version").redirectErrorStream(true).apply {
            environment()["TEMP"] = AppPaths.local.resolve("tmp").apply { mkdirs() }.absolutePath
        }.start()
        process.waitFor(30, TimeUnit.SECONDS)
        process.inputStream.bufferedReader().readText().trim()
    }.getOrDefault("?")
}
