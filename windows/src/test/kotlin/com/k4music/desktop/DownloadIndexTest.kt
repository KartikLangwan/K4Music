package com.k4music.desktop

import com.k4music.desktop.player.DownloadIndex
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DownloadIndexTest {
    @Test fun legacyIndexReconcilesMissingFilesAndKeepsFailedDeletesRetryable() {
        val dir = Files.createTempDirectory("k4-download-index").toFile()
        try {
            val keep = File(dir, "legacy.audio").apply { writeText("audio") }
            val remove = File(dir, "remove.audio").apply { writeText("audio") }
            Properties().apply {
                setProperty("keep", keep.name); setProperty("remove", remove.name); setProperty("missing", "missing.audio")
            }.also { entries -> File(dir, "downloads.properties").outputStream().use { entries.store(it, "legacy") } }
            val index = DownloadIndex(dir)
            assertEquals(setOf("keep", "remove"), index.ids())
            assertFailsWith<IOException> { index.remove(index.indexedIds()) { file -> if (file == keep.canonicalFile) false else file.delete() } }
            assertTrue(keep.isFile)
            assertEquals(setOf("keep"), DownloadIndex(dir).ids())
            assertNull(index.file("missing"))
            index.remove(setOf("keep"))
            assertTrue(DownloadIndex(dir).ids().isEmpty())
        } finally { dir.deleteRecursively() }
    }

    @Test fun failedPublishDoesNotAdvertiseAnUncommittedDownload() {
        val dir = Files.createTempDirectory("k4-download-publish").toFile()
        try {
            val index = DownloadIndex(dir)
            File(dir, "audio.bin").writeText("audio")
            val obstruction = File(dir, "downloads.properties").apply { mkdir() }
            File(obstruction, "preserved").writeText("keep")
            assertFailsWith<IOException> { index.publish("song", "audio.bin") }
            assertNull(index.file("song"))
            assertEquals("keep", File(obstruction, "preserved").readText())
        } finally { dir.deleteRecursively() }
    }
}
