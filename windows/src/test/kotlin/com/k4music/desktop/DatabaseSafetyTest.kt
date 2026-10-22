package com.k4music.desktop

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.k4music.desktop.data.K4Database
import com.k4music.desktop.data.Maintenance
import com.k4music.desktop.data.newDatabase
import com.k4music.desktop.player.Track
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DatabaseSafetyTest {
    private fun library(file: File) = Room.databaseBuilder<K4Database>(file.absolutePath) { newDatabase() }
        .setDriver(BundledSQLiteDriver()).build()

    @Test
    fun snapshotIncludesCommittedWalAndPreservesTheLiveLibrary() = runBlocking<Unit> {
        val dir = Files.createTempDirectory("k4-snapshot-test").toFile()
        try {
            val source = File(dir, "source.db")
            val room = library(source)
            try {
                room.library.insert(Track("song", "Before snapshot"))
                assertTrue(File(source.path + "-wal").length() > 0, "Test must include committed WAL pages")
                val backup = File(dir, "backup.db")
                Maintenance.snapshot(source, backup)
                Maintenance.validateBackup(backup)
                room.library.insert(Track("later", "After snapshot"))
                BundledSQLiteDriver().open(backup.path).use { db ->
                    db.prepare("SELECT COUNT(*) FROM Song").use { row ->
                        assertTrue(row.step())
                        assertEquals(1L, row.getLong(0))
                    }
                }
            } finally { room.close() }
        } finally { dir.deleteRecursively() }
    }

    @Test
    fun rejectsMissingTablesEvenWithTheOriginalRoomIdentity() = runBlocking<Unit> {
        val dir = Files.createTempDirectory("k4-schema-test").toFile()
        try {
            val file = File(dir, "invalid.db")
            val room = library(file)
            try { room.library.insert(Track("song", "Song")) } finally { room.close() }
            BundledSQLiteDriver().open(file.path).use { it.execSQL("DROP TABLE Lyrics") }
            assertFailsWith<IllegalStateException> { Maintenance.validateBackup(file) }
        } finally { dir.deleteRecursively() }
    }

    @Test
    fun failedAtomicReplacementPreservesTheStagedFile() {
        val dir = Files.createTempDirectory("k4-replace-test").toFile()
        try {
            val staged = File(dir, "staged").apply { writeText("validated snapshot") }
            val target = File(dir, "nonempty-directory").apply { mkdir() }
            File(target, "original").writeText("keep")
            assertFailsWith<Exception> { Maintenance.replaceAtomic(staged, target) }
            assertEquals("validated snapshot", staged.readText())
            assertEquals("keep", File(target, "original").readText())
        } finally { dir.deleteRecursively() }
    }
    @Test
    fun localMetadataRefreshPreservesLikesAndPlaylistMembership() = runBlocking<Unit> {
        val dir = Files.createTempDirectory("k4-local-test").toFile()
        val room = library(File(dir, "local.db"))
        try {
            val original = Track("local:C:/music/song.mp3", "Old title", "Old artist")
            room.library.insert(original, likedAt = 123L)
            val (playlist, _) = room.library.createPlaylist(com.k4music.android.models.Playlist(name = "Keep"), listOf(original))
            room.library.upsertLocal(original.copy(title = "New title", artists = "New artist", durationText = "3:00"))
            val liked = room.library.favorites().first().single()
            assertEquals("New title", liked.title)
            assertEquals("New artist", liked.artistsText)
            assertEquals(123L, liked.likedAt)
            assertEquals(1, room.library.playlistSongCount(playlist))
        } finally { room.close(); dir.deleteRecursively() }
    }

    @Test
    fun firstOpenFailureRollsBackWithoutOverwritingRecoveryCopies() = runBlocking<Unit> {
        val dir = Files.createTempDirectory("k4-rollback-test").toFile()
        val original = File(dir, "data.db")
        val candidate = File(dir, "pending.db")
        try {
            library(original).let { db -> try { db.library.insert(Track("original", "Keep me")) } finally { db.close() } }
            library(candidate).let { db -> try { db.library.insert(Track("replacement", "Reject me")) } finally { db.close() } }
            val failure = Maintenance.applyRestore(original, candidate) { file ->
                if (file == original) error("Injected first-open failure")
                Maintenance.validateBackup(file)
            }
            assertTrue(failure.orEmpty().contains("previous library was recovered"))
            library(original).let { db ->
                try { assertEquals("original", db.library.songs(com.k4music.core.data.enums.SongSortBy.DateAdded,
                    com.k4music.core.data.enums.SortOrder.Descending).first().single().id) }
                finally { db.close() }
            }
            assertTrue(dir.listFiles().orEmpty().any { it.name.startsWith("data-before-restore-") })
        } finally { dir.deleteRecursively() }
    }

}
