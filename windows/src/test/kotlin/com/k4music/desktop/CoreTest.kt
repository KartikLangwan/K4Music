package com.k4music.desktop

import com.k4music.desktop.player.Failure
import com.k4music.desktop.player.PlaybackRetryBudget
import com.k4music.desktop.player.Recovery
import com.k4music.desktop.player.chooseRecovery
import com.k4music.desktop.ui.Navigator
import com.k4music.desktop.ui.Screen
import com.k4music.desktop.ui.screens.compareVersions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoreTest {
    @Test
    fun recoveryRetriesThenFallsBackOnceAndNeverSkips() {
        val retries = PlaybackRetryBudget()
        val fallbacks = PlaybackRetryBudget(limit = 1)
        fun next(f: Failure) = chooseRecovery(f, "a", fallbackAllowed = true, retries = retries, fallbacks = fallbacks)
        assertEquals(Recovery.Retry, next(Failure.Network))
        assertEquals(Recovery.Fallback, next(Failure.Source))
        assertEquals(Recovery.Retry, next(Failure.Source))
        assertEquals(Recovery.None, next(Failure.Source)) // error shown; the song is not skipped
    }

    @Test
    fun versionsCompareNumerically() {
        assertTrue(compareVersions("2.12.14", "2.7.15") > 0)
        assertTrue(compareVersions("2.12.14", "2.12.15") < 0)
        assertEquals(0, compareVersions("2.12.14", "2.12.14"))
        assertTrue(compareVersions("3.0", "2.99.99") > 0)
    }

    @Test
    fun navigatorBackAndForward() {
        val nav = Navigator()
        nav.go(Screen.Discover)
        nav.go(Screen.Album("x"))
        nav.back()
        assertEquals(Screen.Discover, nav.current)
        assertTrue(nav.canGoForward)
        nav.forward()
        assertEquals(Screen.Album("x"), nav.current)
        nav.back()
        nav.go(Screen.Library) // new navigation clears forward history
        assertFalse(nav.canGoForward)
        nav.root(Screen.Home)
        assertFalse(nav.canGoBack)
    }
}
