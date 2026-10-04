package com.k4music.android.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackProgressTest {
    @Test fun `unknown zero and negative durations draw finite progress`() {
        listOf(0L, -1L, -9223372036854775807L).forEach {
            assertEquals(0f, playbackFraction(1000, it), 0f)
        }
    }

    @Test fun `positions are clamped while seeking and switching tracks`() {
        assertEquals(0f, playbackFraction(-100, 1000), 0f)
        assertEquals(0.5f, playbackFraction(500, 1000), 0f)
        assertEquals(1f, playbackFraction(2000, 1000), 0f)
        assertEquals(1f, playbackFraction(Long.MAX_VALUE, Long.MAX_VALUE), 0f)
    }
}
