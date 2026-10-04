package com.k4music.android.utils

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueueNavigationTest {
    private fun wrapping(count: Int, direction: Int) = { index: Int -> Math.floorMod(index + direction, count) }
    private fun linear(count: Int, direction: Int) = { index: Int ->
        (index + direction).takeIf { it in 0 until count } ?: C.INDEX_UNSET
    }

    @Test fun `empty queue never asks for a song`() {
        assertNull(neighborIndex(0, -1, wrapping(1, 1)) { error("No song exists") })
    }

    @Test fun `repeat all wraps at queue ends`() {
        assertEquals(0, neighborIndex(3, 2, wrapping(3, 1)) { true })
        assertEquals(2, neighborIndex(3, 0, wrapping(3, -1)) { true })
    }

    @Test fun `without repeat the queue ends instead of wrapping`() {
        assertNull(neighborIndex(3, 2, linear(3, 1)) { true })
        assertNull(neighborIndex(3, 0, linear(3, -1)) { true })
        assertEquals(1, neighborIndex(3, 0, linear(3, 1)) { true })
    }

    @Test fun `filtered queues stop after one pass`() {
        var checked = 0
        assertNull(neighborIndex(4, 2, wrapping(4, -1)) { checked++; false })
        assertEquals(4, checked)
    }

    @Test fun `skip filtered songs and keep a lone allowed song`() {
        assertEquals(2, neighborIndex(4, 0, wrapping(4, 1)) { it == 2 })
        assertEquals(0, neighborIndex(1, 0, wrapping(1, -1)) { true })
    }

    @Test fun `walk follows the given order such as a shuffle order`() {
        val shuffled = listOf(2, 0, 3, 1)
        val next = { index: Int -> shuffled.getOrNull(shuffled.indexOf(index) + 1) ?: C.INDEX_UNSET }
        assertEquals(3, neighborIndex(4, 0, next) { true })
        assertNull(neighborIndex(4, 1, next) { true })
    }
}
