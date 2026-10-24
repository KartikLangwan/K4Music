package com.k4music.desktop

import com.k4music.desktop.player.PlaybackRetryBudget
import com.k4music.desktop.player.Track
import com.k4music.desktop.player.AudioEffects
import com.k4music.desktop.prefs.Settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuditFixesTest {
    @Test
    fun aSecondLaunchWithoutArgumentsStillShowsTheWindow() {
        assertEquals(listOf(""), SingleInstance.launchRequests(emptyList()))
        assertEquals(emptyList(), SingleInstance.launchRequests(listOf("--startup", "--minimized")))
        assertEquals(listOf("--quit"), SingleInstance.launchRequests(listOf("--quit")))
        assertEquals(listOf("https://youtu.be/x"), SingleInstance.launchRequests(listOf("--startup", "https://youtu.be/x")))
    }

    @Test
    fun aUserRetryStartsWithAFreshBudget() {
        val budget = PlaybackRetryBudget()
        assertTrue(budget.tryAcquire("a")); assertTrue(budget.tryAcquire("a")); assertFalse(budget.tryAcquire("a"))
        budget.reset()
        assertTrue(budget.tryAcquire("a"))
    }

    @Test
    fun artistNamesWithCommasStayWholeForTheLibrary() {
        val track = Track("id", "Song", artists = "Simon & Garfunkel", artistIds = listOf("UC1"), artistNames = listOf("Simon & Garfunkel"))
        assertEquals(track.artistIds.size, track.artistNames.size)
    }

    @Test
    fun boostedSoundGetsALimiterAtTheEnd() {
        val before = Settings.bassBoost
        try {
            Settings.bassBoost = 50
            assertTrue(AudioEffects.chain().substringAfterLast("lavfi=[").startsWith("alimiter"), AudioEffects.chain())
            Settings.bassBoost = 0
            assertFalse("alimiter" in AudioEffects.chain())
        } finally { Settings.bassBoost = before }
    }
}
