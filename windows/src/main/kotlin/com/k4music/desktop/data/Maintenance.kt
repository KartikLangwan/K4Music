package com.k4music.desktop.data

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.k4music.desktop.AppLog
import com.k4music.desktop.AppPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit

/** Library backup / restore (compatible with the Android app's backups) and tool updates. */
object Maintenance {
    private const val SCHEMA_VERSION = 30L
    private val pending get() = AppPaths.database.resolveSibling("data.db.restore")

    /** A separate writable SQLite connection makes a consistent snapshot without Room's read pool. */
    internal fun snapshot(source: File, target: File) {
        check(source.canonicalFile != target.canonicalFile) { "Choose a different backup destination" }
        BundledSQLiteDriver().open(source.absolutePath).use { db ->
            db.execSQL("PRAGMA busy_timeout=5000")
            val escaped = target.absolutePath.replace("'", "''")
            db.execSQL("VACUUM INTO '$escaped'")
        }
        java.io.RandomAccessFile(target, "rw").use { it.fd.sync() }
    }

    /** Full schema validation runs even if the file claims to have a valid Room identity hash. */
    internal fun validateBackup(file: File) {
        check(file.isFile && file.length() > 0) { "Empty backup" }
        BundledSQLiteDriver().open(file.absolutePath).use { db ->
            db.execSQL("PRAGMA busy_timeout=5000")
            db.prepare("PRAGMA integrity_check").use {
                check(it.step() && it.getText(0) == "ok") { "The backup file is damaged" }
            }
            db.prepare("PRAGMA user_version").use {
                check(it.step() && it.getLong(0) == SCHEMA_VERSION) { "This backup is from an incompatible version" }
            }
            db.prepare("PRAGMA foreign_key_check").use { check(!it.step()) { "Backup has broken library relationships" } }
            checkpoint(db)
            // Reset only the private copy's identity metadata; Room must perform full schema validation.
            db.execSQL("DROP TABLE IF EXISTS room_master_table")
        }
        val staged = androidx.room.Room.databaseBuilder<K4Database>(file.absolutePath) { newDatabase() }
            .setDriver(BundledSQLiteDriver()).build()
        try {
            kotlinx.coroutines.runBlocking { staged.library.raw(androidx.room.RoomRawQuery("SELECT 1")) }
        } finally { staged.close() }
    }

    private fun checkpoint(db: androidx.sqlite.SQLiteConnection) {
        db.prepare("PRAGMA wal_checkpoint(TRUNCATE)").use {
            check(it.step() && it.getLong(0) == 0L) { "Library is busy; retry the operation" }
        }
    }

    /** Requires atomic replacement: failure must leave the existing destination intact. */
    internal fun replaceAtomic(source: File, target: File) {
        Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    suspend fun backup(target: File) = withContext(Dispatchers.IO) {
        check(target.canonicalFile != AppPaths.database.canonicalFile) { "Cannot overwrite the active library" }
        val temp = File.createTempFile("backup-", ".db", target.absoluteFile.parentFile)
        try {
            snapshot(AppPaths.database, temp)
            validateBackup(temp)
            replaceAtomic(temp, target)
            AppLog.i("Backup", "Library saved to $target")
        } finally { temp.delete() }
    }

    private val restoreMutex = kotlinx.coroutines.sync.Mutex()

    /** Validate a private snapshot before publishing it as a pending restore. */
    suspend fun stageRestore(source: File) = withContext(Dispatchers.IO) {
        restoreMutex.lock()
        try {
            val staged = File.createTempFile("restore-check-", ".db", pending.parentFile)
            try {
                // Snapshot also includes committed WAL contents if an open database was selected.
                snapshot(source, staged)
                validateBackup(staged)
                replaceAtomic(staged, pending)
            } finally { staged.delete() }
        } finally { restoreMutex.unlock() }
    }

    @Volatile var lastRestoreError: String? = null
        private set

    /** Only the owning process calls this, before any library connections/background writers start. */
    fun applyPendingRestore() {
        lastRestoreError = applyRestore(AppPaths.database, pending)
    }

    internal fun applyRestore(database: File, candidate: File, verify: (File) -> Unit = ::validateBackup): String? {
        if (!candidate.isFile) return null
        var rollback: File? = null
        var swapped = false
        try {
            verify(candidate)
            if (database.isFile) {
                rollback = File.createTempFile("data-before-restore-", ".db", database.parentFile)
                snapshot(database, rollback)
                verify(rollback)
                BundledSQLiteDriver().open(database.absolutePath).use { connection ->
                    connection.execSQL("PRAGMA busy_timeout=5000")
                    checkpoint(connection)
                }
            }
            replaceAtomic(candidate, database)
            swapped = true
            // Exercise the normal Room opening path against the installed file before global Db opens.
            verify(database)
            AppLog.i("Backup", "Library restored; recovery snapshots kept alongside data.db")
            return null
        } catch (error: Exception) {
            val message = if (swapped) {
                try {
                    if (rollback != null) {
                        val recovered = File.createTempFile("restore-recovery-", ".db", database.parentFile)
                        try {
                            snapshot(rollback, recovered)
                            validateBackup(recovered)
                            // All validation connections to the rejected replacement have closed.
                            // Its journals must not be replayed against the recovered original.
                            listOf("-wal", "-shm").forEach { Files.deleteIfExists(File(database.path + it).toPath()) }
                            replaceAtomic(recovered, database)
                        } finally { recovered.delete() }
                        "Restore failed; your previous library was recovered. ${error.message.orEmpty()}"
                    } else {
                        // First install: preserve the rejected file and let Room create a fresh empty library.
                        listOf("-wal", "-shm").forEach { Files.deleteIfExists(File(database.path + it).toPath()) }
                        replaceAtomic(database, candidate)
                        "Restore failed; the backup was not applied. ${error.message.orEmpty()}"
                    }
                } catch (recoveryError: Exception) {
                    AppLog.e("Backup", "Automatic rollback failed; recovery copy at $rollback", recoveryError)
                    "Restore failed. A recovery copy is available at $rollback. ${recoveryError.message.orEmpty()}"
                }
            } else "Restore failed; your original library and staged backup were kept. ${error.message.orEmpty()}"
            AppLog.e("Backup", message, error)
            return message
        }
    }

    suspend fun cancelPendingRestore() = withContext(Dispatchers.IO) {
        restoreMutex.lock()
        try { Files.deleteIfExists(pending.toPath()); lastRestoreError = null } finally { restoreMutex.unlock() }
    }

    fun hasPendingRestore() = pending.isFile

    /** Installed by the window so restart follows exactly the normal shutdown path. */
    var onRestart: () -> Unit = { error("Restart is unavailable before the window is ready") }

    fun restartApp() = onRestart()

    /** Drains output while waiting, validates exit status, and kills the process on cancellation. */
    internal suspend fun runTool(command: List<String>, timeoutSeconds: Long): String =
        kotlinx.coroutines.runInterruptible(Dispatchers.IO) {
            val process = ProcessBuilder(command).redirectErrorStream(true).apply {
                val temp = AppPaths.local.resolve("tmp").apply { mkdirs() }
                environment()["TEMP"] = temp.absolutePath
                environment()["TMP"] = temp.absolutePath
            }.start()
            val output = StringBuffer()
            val reader = Thread({
                runCatching { process.inputStream.bufferedReader().forEachLine { line ->
                    output.append(line).append('\n')
                    if (output.length > 16_000) output.delete(0, output.length - 16_000)
                } }
            }, "k4-tool-output").apply { isDaemon = true; start() }
            try {
                check(process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) { "Tool timed out" }
                reader.join(2_000)
                check(process.exitValue() == 0) { "Tool failed (${process.exitValue()}): ${output.toString().takeLast(500)}" }
                output.toString().trim()
            } finally {
                if (process.isAlive) process.destroyForcibly()
                runCatching { process.inputStream.close() }
                reader.join(1_000)
            }
        }

    suspend fun updateYtDlp(): String = runTool(
        listOf(AppPaths.tools.resolve("yt-dlp/yt-dlp.exe").absolutePath, "-U"), 120
    ).lines().lastOrNull { it.isNotBlank() }.orEmpty().also { AppLog.i("Maintenance", "yt-dlp -U: $it") }

    fun ytDlpVersion(): String = runCatching {
        kotlinx.coroutines.runBlocking {
            runTool(listOf(AppPaths.tools.resolve("yt-dlp/yt-dlp.exe").absolutePath, "--version"), 15)
        }
    }.getOrDefault("?")
}
