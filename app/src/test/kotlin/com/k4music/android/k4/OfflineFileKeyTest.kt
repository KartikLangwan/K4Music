package com.k4music.android.k4

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineFileKeyTest {
    @Test fun `existing video filenames are preserved`() {
        assertEquals("dQw4w9WgXcQ", offlineFileKey("dQw4w9WgXcQ"))
    }

    @Test fun `paths and URLs cannot escape the offline folder or collide after sanitizing`() {
        val unsafe = listOf("../../outside", "https://example.com/song?id=1", "", "a".repeat(300))
        unsafe.forEach { id ->
            val key = offlineFileKey(id)
            assertTrue(key.matches(Regex("[a-f0-9]{64}")))
            assertEquals(key, offlineFileKey(id))
        }
        assertNotEquals(offlineFileKey("a/b"), offlineFileKey("a?b"))
    }
}
