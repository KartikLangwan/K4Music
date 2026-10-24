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

    @Test
    fun startupCommandQuotesPathsWithSpacesWithoutChangingTheRegistry() {
        assertEquals("\"C:\\Program Files\\K4 Music\\K4 Music.exe\" --startup",
            StartupManager.startupCommand("C:\\Program Files\\K4 Music\\K4 Music.exe"))
    }

    @Test
    fun backgroundStyleParsing() {
        assertEquals(com.k4music.desktop.prefs.BackgroundStyle.BlurredArtwork, com.k4music.desktop.prefs.BackgroundStyle.fromName("Artwork"))
        assertEquals(com.k4music.desktop.prefs.BackgroundStyle.BlurredArtwork, com.k4music.desktop.prefs.BackgroundStyle.fromName("BlurredArtwork"))
        assertEquals(com.k4music.desktop.prefs.BackgroundStyle.GlowAura, com.k4music.desktop.prefs.BackgroundStyle.fromName("GlowAura"))
        assertEquals(com.k4music.desktop.prefs.BackgroundStyle.PureBlack, com.k4music.desktop.prefs.BackgroundStyle.fromName("PureBlack"))
        assertEquals(com.k4music.desktop.prefs.BackgroundStyle.Accent, com.k4music.desktop.prefs.BackgroundStyle.fromName("Accent"))
        assertEquals(com.k4music.desktop.prefs.BackgroundStyle.Image, com.k4music.desktop.prefs.BackgroundStyle.fromName("Image"))
        assertEquals(com.k4music.desktop.prefs.BackgroundStyle.Alpine, com.k4music.desktop.prefs.BackgroundStyle.fromName("Alpine"))
        assertEquals(com.k4music.desktop.prefs.BackgroundStyle.Aurora, com.k4music.desktop.prefs.BackgroundStyle.fromName("Aurora"))
        assertEquals(com.k4music.desktop.prefs.BackgroundStyle.LiquidCanvas, com.k4music.desktop.prefs.BackgroundStyle.fromName("LiquidCanvas"))
        assertEquals(com.k4music.desktop.prefs.BackgroundStyle.MidnightNeon, com.k4music.desktop.prefs.BackgroundStyle.fromName("MidnightNeon"))
        assertEquals(com.k4music.desktop.prefs.BackgroundStyle.BlurredArtwork, com.k4music.desktop.prefs.BackgroundStyle.fromName("nonexistent_style"))
    }
}

class StartupRegistryTest {
    @Test
    fun registersAndRemovesTheRunValueDirectly() {
        if (!System.getProperty("os.name").startsWith("Windows")) return
        val oldKey = StartupManager.runKey
        StartupManager.runKey = "HKCU\\Software\\K4MusicTest-${System.nanoTime()}"
        try {
            assertFalse(StartupManager.isEnabled())
            assertTrue(StartupManager.setEnabled(true, "C:/Program Files/K4 Music/K4 Music.exe"), StartupManager.lastError)
            assertTrue(StartupManager.isEnabled())
            assertTrue(StartupManager.setEnabled(false))
            assertFalse(StartupManager.isEnabled())
            assertFalse(StartupManager.setEnabled(true, null)) // dev run: no installed exe to register
        } finally {
            ProcessBuilder("reg", "delete", StartupManager.runKey, "/f").start().waitFor()
            StartupManager.runKey = oldKey
        }
    }
}

class SleepCountdownTest {
    @Test
    fun countdownFormats() {
        assertEquals("0:00", com.k4music.desktop.ui.formatCountdown(0))
        assertEquals("14:59", com.k4music.desktop.ui.formatCountdown(14 * 60_000L + 59_000))
        assertEquals("1:00:00", com.k4music.desktop.ui.formatCountdown(3_600_000))
        assertEquals("0:01", com.k4music.desktop.ui.formatCountdown(1))
    }
}
