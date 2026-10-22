package com.k4music.desktop

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.k4music.desktop.data.K4Database
import com.k4music.desktop.data.LocalMusic
import com.k4music.desktop.data.newDatabase
import com.k4music.desktop.player.Track
import com.k4music.android.models.Lyrics
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalScanSafetyTest {
    @Test fun onlySuccessfullyScannedRootsMayPruneUnreferencedFiles() = runBlocking<Unit> {
        val dir = Files.createTempDirectory("k4-scan-safety").toFile()
        val available = File(dir, "available").apply { mkdir() }
        val unavailable = File(dir, "disconnected")
        val room = Room.databaseBuilder<K4Database>(File(dir, "library.db").absolutePath) { newDatabase() }
            .setDriver(BundledSQLiteDriver()).build()
        try {
            val deleted = Track("local:${File(available, "deleted.mp3").absolutePath}", "Deleted")
            val disconnected = Track("local:${File(unavailable, "keep.mp3").absolutePath}", "Disconnected")
            val liked = Track("local:${File(available, "liked.mp3").absolutePath}", "Keep liked")
            val listened = Track("local:${File(available, "listened.mp3").absolutePath}", "Keep listening totals")
            val lyrics = Track("local:${File(available, "lyrics.mp3").absolutePath}", "Keep edited lyrics")
            room.library.insert(deleted)
            room.library.insert(disconnected)
            room.library.insert(liked, likedAt = 123L)
            room.library.insert(listened)
            room.library.incrementTotalPlayTimeMs(listened.id, 15_000)
            room.library.insert(lyrics)
            room.library.upsert(Lyrics(lyrics.id, "User transcription", null))
            val scan = LocalMusic.collectFiles(listOf(available, unavailable))
            assertEquals(listOf(available.absolutePath), scan.completeRoots)
            assertTrue(scan.files.isEmpty())
            room.library.pruneLocalSongs(emptySet(), scan.completeRoots)
            val ids = room.library.localSongsByTitleAsc().first().map { it.id }
            assertFalse(deleted.id in ids)
            assertTrue(disconnected.id in ids)
            assertTrue(liked.id in ids)
            assertTrue(listened.id in ids)
            assertEquals(15_000L, room.library.song(listened.id).first()?.totalPlayTimeMs)
            assertTrue(lyrics.id in ids)
            assertEquals("User transcription", room.library.lyrics(lyrics.id).first()?.fixed)
        } finally { room.close(); dir.deleteRecursively() }
    }
}
