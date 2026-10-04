package com.k4music.android.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueueNavigationTest {
    @Test fun `empty queue never asks for a song`() {
        assertNull(adjacentPlayableIndex(0, -1, 1) { error("No song exists") })
    }

    @Test fun `next and previous wrap at queue ends`() {
        assertEquals(0, adjacentPlayableIndex(3, 2, 1) { true })
        assertEquals(2, adjacentPlayableIndex(3, 0, -1) { true })
    }

    @Test fun `filtered queues stop after one pass`() {
        var checked = 0
        assertNull(adjacentPlayableIndex(4, 2, -1) { checked++; false })
        assertEquals(4, checked)
    }

    @Test fun `skip filtered songs and keep a lone allowed song`() {
        assertEquals(2, adjacentPlayableIndex(4, 0, 1) { it == 2 })
        assertEquals(0, adjacentPlayableIndex(1, 0, -1) { true })
    }

    @Test fun `stale index after queue removal remains bounded`() {
        assertEquals(0, adjacentPlayableIndex(2, 7, 1) { true })
        assertEquals(1, adjacentPlayableIndex(2, -1, -1) { true })
    }
}
