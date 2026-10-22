package com.k4music.android.k4

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The JioSaavn fallback bridge. Locks the three properties the fallback
 * depends on: search results carry a playable saavn key, durations render
 * like the rest of the UI, and junk keys resolve to null instead of throwing.
 */
class JioSaavnBridgeTest {

    private fun song(
        title: String = "Tum Hi Ho",
        artist: String = "Arijit Singh",
        durationMs: Long = 262_000,
        contentUri: String = "https://saavncdn.com/abc/_320.mp4",
        artworkUri: String? = "https://img.example/cover.jpg"
    ) = K4Song(
        id = "saavn_x1",
        title = title,
        artist = artist,
        album = "Aashiqui 2",
        durationMs = durationMs,
        contentUri = contentUri,
        artworkUri = artworkUri
    )

    @Test
    fun `song item keeps title artist and artwork`() {
        val item = song().toSongItem()
        assertEquals("Tum Hi Ho", item.info?.name)
        assertEquals("Arijit Singh", item.authors?.single()?.name)
        assertEquals("https://img.example/cover.jpg", item.thumbnail?.url)
        assertEquals("4:22", item.durationText)
    }

    @Test
    fun `song item key is the saavn key the player resolver understands`() {
        val item = song().toSongItem()
        assertEquals("saavn:id:x1", item.key)
        assertTrue(isSaavnKey(item.key))
        assertFalse(isSaavnKey("dQw4w9WgXcQ"))
        assertFalse(isSaavnKey(null))
    }

    @Test
    fun `durations render like the rest of the ui`() {
        assertNull(formatDurationMs(0))
        assertNull(formatDurationMs(-5))
        assertEquals("1:05", formatDurationMs(65_000))
        assertEquals("4:22", formatDurationMs(262_000))
        assertEquals("1:02:03", formatDurationMs(3_723_000))
    }

    @Test
    fun `junk keys resolve to null instead of throwing`() = kotlinx.coroutines.runBlocking {
        assertNull(resolveSaavnStream("saavn:"))
        assertNull(resolveSaavnStream("saavn:not-a-saavn-url"))
    }
}
