package com.k4music.desktop

import com.k4music.android.models.Playlist
import com.k4music.desktop.data.Db
import com.k4music.desktop.player.Mpv
import com.k4music.desktop.player.PlayerController
import com.k4music.desktop.player.Track
import com.k4music.desktop.ui.Screen
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Actual Compose/Skia frames with local fixtures: no providers, playback or installed app data. */
class VisualQaTest {
    @Test fun renderLibrarySettingsAndAnUnavailableLocalRecording() = runBlocking<Unit> {
        val native = File("downloads/mpv")
        assumeTrue("Bundled Windows libmpv is unavailable", File(native, "libmpv-2.dll").isFile)
        val output = File("build/implementation-visual-qa").apply { mkdirs() }
        val missing = Track("local:${File(output, "missing-recording.mp3").absolutePath}", "Missing local recording", "A preserved playlist reference", durationText = "3:00")
        val online = Track("saavn:id:visualfixture", "Catalogue recording", "Offline visual fixture", durationText = "4:00")
        val (playlistId, _) = Db.library.createPlaylist(Playlist(name = "Visual QA · preserved recordings"), listOf(missing, online))
        Mpv(native).use { mpv ->
            val player = PlayerController(mpv)
            try {
                player.restore(listOf(missing, online), 0, 30.0).join()
                DevTools.screenshots(output, player, listOf("library" to Screen.Library, "settings" to Screen.Settings,
                    "unavailable-local" to Screen.LocalPlaylist(playlistId)), settleMs = 1_200)
                for (name in listOf("library", "settings", "unavailable-local")) {
                    assertTrue(File(output, "$name.png").length() > 1_000, "Missing rendered $name frame")
                    assertTrue(File(output, "$name-queue.png").length() > 1_000, "Missing rendered $name queue frame")
                }
                // Lyrics panel, full player, light theme and a narrow window, through real clicks.
                val flows = File(output, "flows")
                DevTools.uitest(flows, player, listOf("go favorites", "click 1124 851", "shot lyrics", "click 1393 851", "wait 1200", "shot player"))
                DevTools.uitest(flows, player, listOf("theme light", "go favorites", "shot light"))
                DevTools.uitest(flows, player, listOf("size 960 640", "go favorites", "shot narrow"))
            } finally {
                player.close()
                // Repeated runs must not pile up fixture playlists in the sidebar.
                Db.library.delete(Playlist(id = playlistId, name = "Visual QA · preserved recordings"))
            }
        }
    }
}
