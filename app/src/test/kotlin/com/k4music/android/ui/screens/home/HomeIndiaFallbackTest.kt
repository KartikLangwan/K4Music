package com.k4music.android.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.k4music.providers.innertube.Innertube
import com.k4music.providers.innertube.models.NavigationEndpoint

/**
 * The fresh-install home must always show Indian songs. These lock the
 * rotation contract: one query per weekday, deterministic, never blank,
 * wrapping safely past day 365.
 */
class HomeIndiaFallbackTest {

    @Test
    fun `seven distinct non blank queries cover the week`() {
        assertEquals(7, indiaHomeQueries.size)
        assertEquals(7, indiaHomeQueries.toSet().size)
        assertTrue(indiaHomeQueries.all { it.isNotBlank() })
    }

    @Test
    fun `rotation is deterministic week after week`() {
        for (day in 0..800) {
            assertEquals(indiaHomeQueries[day % 7], indiaHomeQuery(day))
        }
    }

    @Test
    fun `keyless search items are dropped before reaching the grid`() {
        val valid = Innertube.SongItem(
            info = Innertube.Info(
                name = "Tum Hi Ho",
                endpoint = NavigationEndpoint.Endpoint.Watch(videoId = "v1")
            ),
            authors = null,
            album = null,
            durationText = null,
            explicit = false,
            thumbnail = null
        )
        val keyless = Innertube.SongItem(
            info = null,
            authors = null,
            album = null,
            durationText = null,
            explicit = false,
            thumbnail = null
        )
        assertEquals(listOf(valid), listOf(keyless, valid).withPlayableKeys())
    }
}
