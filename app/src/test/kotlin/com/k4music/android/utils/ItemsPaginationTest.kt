package com.k4music.android.utils

import com.k4music.providers.innertube.Innertube
import com.k4music.providers.innertube.models.NavigationEndpoint
import com.k4music.providers.innertube.utils.plus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ItemsPaginationTest {
    private fun song(id: String?) = Innertube.SongItem(
        info = id?.let { Innertube.Info(name = it, endpoint = NavigationEndpoint.Endpoint.Watch(videoId = it)) },
        authors = null, album = null, durationText = null, explicit = false, thumbnail = null
    )

    @Test fun `last page clears continuation instead of requesting it forever`() {
        val first = Innertube.ItemsPage(listOf(song("1")), "next")
        val merged = first + Innertube.ItemsPage(listOf(song("2")), null)
        assertNull(merged.continuation)
        assertEquals(listOf("1", "2"), merged.items?.map { it.key })
    }

    @Test fun `malformed rows and duplicate ids never reach lazy lists`() {
        val first = Innertube.ItemsPage(listOf(song("1"), song(null)), "next")
        val merged = first + Innertube.ItemsPage(listOf(song("1"), song(""), song("2")), null)
        assertEquals(listOf("1", "2"), merged.items?.map { it.key })
    }

    @Test fun `empty terminal response keeps loaded songs and finishes pagination`() {
        val first = Innertube.ItemsPage(listOf(song("1")), "next")
        val merged = first + Innertube.ItemsPage<Innertube.SongItem>(null, null)
        assertNull(merged.continuation)
        assertEquals(listOf("1"), merged.items?.map { it.key })
    }
}
