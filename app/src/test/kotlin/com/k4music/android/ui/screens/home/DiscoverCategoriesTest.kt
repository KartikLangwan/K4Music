package com.k4music.android.ui.screens.home

import org.junit.Assert.*
import org.junit.Test

class DiscoverCategoriesTest {
    @Test fun `Indian genres lead while main genres remain ahead of other moods`() {
        assertEquals(0, discoverMoodPriority("Hindi & Bollywood"))
        assertEquals(0, discoverMoodPriority("Punjabi"))
        assertEquals(1, discoverMoodPriority("ROCK"))
        assertEquals(1, discoverMoodPriority("Pop"))
        assertEquals(2, discoverMoodPriority("Workout"))
    }
    @Test fun `quick browse destinations are distinct meaningful searches`() {
        val categories = indianCategories + globalCategories
        assertEquals(categories.size, categories.map { it.query }.distinct().size)
        assertTrue(categories.all { it.query.length > 5 })
        assertTrue(indianCategories.any { "Bollywood" in it.query })
        assertTrue(globalCategories.any { "Rock" in it.query })
    }
}
