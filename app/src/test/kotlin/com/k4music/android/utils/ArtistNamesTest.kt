package com.k4music.android.utils

import com.k4music.providers.innertube.Innertube
import com.k4music.providers.innertube.models.NavigationEndpoint
import org.junit.Assert.assertEquals
import org.junit.Test

class ArtistNamesTest {
    private fun info(name: String?) = Innertube.Info<NavigationEndpoint.Endpoint.Browse>(name, null)

    @Test
    fun dropsSeparatorRunsAndRejoins() {
        val runs = listOf("Shashwat Sachdev", ", ", "Arijit Singh", " & ", "Irshad Kamil").map(::info)
        assertEquals("Shashwat Sachdev, Arijit Singh, Irshad Kamil", runs.artistNames())
        assertEquals("Shashwat Sachdev · Arijit Singh · Irshad Kamil", runs.artistNames(" · "))
    }

    @Test
    fun handlesNullAndEmpty() {
        assertEquals("", null.artistNames())
        assertEquals("Akon", listOf(info(null), info("Akon")).artistNames())
    }
}
