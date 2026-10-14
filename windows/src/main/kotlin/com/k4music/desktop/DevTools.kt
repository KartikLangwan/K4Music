package com.k4music.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.Density
import androidx.compose.ui.use
import coil3.compose.setSingletonImageLoaderFactory
import com.k4music.desktop.player.PlayerController
import com.k4music.desktop.player.StreamResolver
import com.k4music.desktop.player.Track
import com.k4music.desktop.ui.K4Theme
import com.k4music.desktop.ui.LocalNavigator
import com.k4music.desktop.ui.LocalPlayer
import com.k4music.desktop.ui.Navigator
import com.k4music.desktop.ui.Screen
import com.k4music.desktop.ui.Shell
import com.k4music.desktop.ui.SidePanel
import com.k4music.desktop.ui.k4ImageLoader
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

/**
 * Developer checks that run without a visible window:
 *  `--screenshots <dir>`  renders each main screen to PNG (how the UI really looks)
 *  `--selftest <dir>`     resolves and plays real songs through mpv and writes a report
 */
object DevTools {
    fun screenshots(dir: File, player: PlayerController, screens: List<Pair<String, Screen>>, settleMs: Long = 6_000) {
        dir.mkdirs()
        for ((name, screen) in screens) for (panel in listOf<SidePanel?>(null, SidePanel.Queue)) {
            val navigator = Navigator().apply { root(screen) }
            ImageComposeScene(1440, 900, Density(1f)) {
                setSingletonImageLoaderFactory(::k4ImageLoader)
                K4Theme {
                    CompositionLocalProvider(LocalNavigator provides navigator, LocalPlayer provides player) {
                        Shell(mutableStateOf(panel), mutableStateOf(false), FocusRequester())
                    }
                }
            }.use { scene ->
                // Let network content and images arrive, rendering frames as real time passes.
                val start = System.nanoTime()
                while (System.nanoTime() - start < settleMs * 1_000_000) {
                    scene.render(System.nanoTime() - start)
                    Thread.sleep(100)
                }
                val image = scene.render(System.nanoTime() - start)
                val suffix = if (panel == null) "" else "-queue"
                File(dir, "$name$suffix.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
                AppLog.i("DevTools", "Rendered $name$suffix")
            }
        }
    }

    /**
     * Drives the real UI headlessly: [script] lines are `click x y`, `key space`, `wait ms`, `shot name`.
     * After every step the navigator screen and player state go to uitest.txt, so clicks can be checked.
     */
    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    fun uitest(dir: File, player: PlayerController, script: List<String>) {
        dir.mkdirs()
        val navigator = Navigator()
        val panel = mutableStateOf<SidePanel?>(null)
        val playerOpen = mutableStateOf(false)
        val report = StringBuilder()
        val start = System.nanoTime()
        fun now() = System.nanoTime() - start
        // Optional first lines: `size W H` (window size) and `theme light`.
        val size = script.firstOrNull { it.startsWith("size ") }?.split(' ')?.let { it[1].toInt() to it[2].toInt() } ?: (1440 to 900)
        val dark = script.none { it.trim() == "theme light" }
        ImageComposeScene(size.first, size.second, Density(1f)) {
            setSingletonImageLoaderFactory(::k4ImageLoader)
            K4Theme(dark = dark) {
                CompositionLocalProvider(LocalNavigator provides navigator, LocalPlayer provides player) {
                    Shell(panel, playerOpen, FocusRequester())
                }
            }
        }.use { scene ->
            fun pump(ms: Long) {
                val until = System.nanoTime() + ms * 1_000_000
                do { scene.render(now()); Thread.sleep(30) } while (System.nanoTime() < until)
            }
            pump(1_500)
            for (line in script.map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }) {
                val parts = line.split(Regex("\\s+"))
                when (parts[0]) {
                    "click" -> {
                        val at = androidx.compose.ui.geometry.Offset(parts[1].toFloat(), parts[2].toFloat())
                        scene.sendPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Move, at)
                        pump(60)
                        scene.sendPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Press, at)
                        pump(60)
                        scene.sendPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Release, at)
                        pump(400)
                    }
                    "wait" -> pump(parts[1].toLong())
                    "scroll" -> {
                        val at = androidx.compose.ui.geometry.Offset(parts[1].toFloat(), parts[2].toFloat())
                        scene.sendPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Move, at)
                        scene.sendPointerEvent(
                            androidx.compose.ui.input.pointer.PointerEventType.Scroll, at,
                            scrollDelta = androidx.compose.ui.geometry.Offset(0f, parts[3].toFloat())
                        )
                        pump(800)
                    }
                    "rightclick" -> {
                        val at = androidx.compose.ui.geometry.Offset(parts[1].toFloat(), parts[2].toFloat())
                        scene.sendPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Move, at)
                        scene.sendPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Press, at, buttons = androidx.compose.ui.input.pointer.PointerButtons(isSecondaryPressed = true), button = androidx.compose.ui.input.pointer.PointerButton.Secondary)
                        pump(60)
                        scene.sendPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Release, at, button = androidx.compose.ui.input.pointer.PointerButton.Secondary)
                        pump(500)
                    }
                    "go" -> {
                        navigator.go(
                            when (parts[1]) {
                                "album" -> Screen.Album(parts[2])
                                "artist" -> Screen.Artist(parts[2])
                                "playlist" -> Screen.Playlist(parts[2])
                                "favorites" -> Screen.BuiltIn(com.k4music.desktop.ui.BuiltInKind.Favorites)
                                "history" -> Screen.BuiltIn(com.k4music.desktop.ui.BuiltInKind.History)
                                else -> Screen.Home
                            }
                        )
                        pump(5_000)
                    }
                    "search" -> { navigator.go(Screen.Search(line.substringAfter(' '))); pump(4_000) }
                    "size", "theme" -> Unit
                    // A fresh playlist of the current queue, opened (for reorder tests).
                    "mkplaylist" -> {
                        val tracks = player.state.value.queue.take(6)
                        kotlinx.coroutines.runBlocking {
                            com.k4music.desktop.data.Db.library.addToPlaylist(com.k4music.android.models.Playlist(name = "Reorder test"), tracks)
                        }
                        val id = kotlinx.coroutines.runBlocking {
                            com.k4music.desktop.data.Db.library.playlistPreviews(
                                com.k4music.core.data.enums.PlaylistSortBy.DateAdded, com.k4music.core.data.enums.SortOrder.Descending
                            ).first().first().id
                        }
                        navigator.go(Screen.LocalPlaylist(id))
                        pump(2_000)
                    }
                    "drag" -> {
                        val from = androidx.compose.ui.geometry.Offset(parts[1].toFloat(), parts[2].toFloat())
                        val to = androidx.compose.ui.geometry.Offset(parts[3].toFloat(), parts[4].toFloat())
                        scene.sendPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Move, from)
                        scene.sendPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Press, from)
                        pump(50)
                        for (step in 1..12) {
                            scene.sendPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Move, from + (to - from) * (step / 12f))
                            pump(30)
                        }
                        scene.sendPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Release, to)
                        pump(1_200)
                    }
                    "set" -> {
                        when (parts[1]) {
                            "nav" -> com.k4music.desktop.prefs.Settings.navPosition = com.k4music.desktop.prefs.NavPosition.valueOf(parts[2])
                            "collapsed" -> com.k4music.desktop.prefs.Settings.sidebarCollapsed = parts[2].toBoolean()
                            "playlists" -> com.k4music.desktop.prefs.Settings.sidebarPlaylists = parts[2].toBoolean()
                            "hidden" -> com.k4music.desktop.prefs.Settings.hiddenNav = parts.getOrElse(2) { "" }
                        }
                        pump(600)
                    }
                    "shot" -> File(dir, "${parts[1]}.png").writeBytes(scene.render(now()).encodeToData(EncodedImageFormat.PNG)!!.bytes)
                }
                val s = player.state.value
                report.appendLine(
                    "$line -> screen=${navigator.current} panel=${panel.value} full=${playerOpen.value} " +
                        "playing=${s.playing} buffering=${s.buffering} track=${s.current?.title} pos=${"%.1f".format(player.position.value)} " +
                        "queue=${s.queue.size}@${s.index} next=${s.queue.drop(s.index + 1).take(3).joinToString("|") { it.title.take(18) }} " +
                        "repeat=${com.k4music.desktop.prefs.Settings.repeatMode} shuffle=${com.k4music.desktop.prefs.Settings.shuffleEnabled} " +
                        "muted=${com.k4music.desktop.prefs.Settings.muted} vol=${com.k4music.desktop.prefs.Settings.volume} error=${s.error}"
                )
            }
        }
        File(dir, "uitest.txt").writeText(report.toString())
    }

    /** Plays each [tracks] entry for a few seconds; passes when the position actually advances. */
    fun selftest(dir: File, player: PlayerController, tracks: List<Track>): Boolean = runBlocking {
        dir.mkdirs()
        val resolver = StreamResolver()
        val report = StringBuilder()
        var ok = true
        for (track in tracks) {
            val started = System.currentTimeMillis()
            val stream = runCatching { resolver.resolve(track) }
            val resolvedIn = System.currentTimeMillis() - started
            player.clearQueue().join()
            kotlinx.coroutines.delay(500)
            val clicked = System.currentTimeMillis()
            player.play(listOf(track)).join()
            var firstSoundMs = -1L
            val advanced = withTimeoutOrNull(25_000) {
                while (player.position.value < 2.0) {
                    if (firstSoundMs < 0 && player.position.value > 0.05) firstSoundMs = System.currentTimeMillis() - clicked
                    kotlinx.coroutines.delay(50)
                }
                true
            } ?: false
            // Deep in the song: later parts of the stream must load too (cut-off streams failed here).
            val duration = player.state.value.durationSeconds
            val deepAt = duration * 0.8
            if (advanced && duration > 30) player.seek(deepAt)
            val deep = advanced && duration > 30 && withTimeoutOrNull(25_000) {
                while (player.position.value < deepAt + 3 || player.state.value.current?.id != track.id) kotlinx.coroutines.delay(100)
                true
            } == true
            val line = "${track.id} '${track.title}': resolve=${stream.getOrNull()?.source ?: stream.exceptionOrNull()?.message} " +
                "in ${resolvedIn}ms, firstSound=${firstSoundMs}ms, plays=$advanced, deep=$deep (pos=${"%.1f".format(player.position.value)}s of ${duration.toInt()}s, " +
                "source=${player.state.value.source}, error=${player.state.value.error})"
            AppLog.i("SelfTest", line)
            report.appendLine(line)
            ok = ok && advanced && deep
        }
        // A song plays to its real end, then the next one starts (no early cut, no skip).
        if (tracks.size >= 2) {
            player.clearQueue().join()
            player.play(tracks.take(2)).join()
            withTimeoutOrNull(25_000) { while (player.position.value < 1.0) kotlinx.coroutines.delay(100) }
            val duration = player.state.value.durationSeconds
            player.seek(duration - 6)
            val next = withTimeoutOrNull(40_000) {
                while (player.state.value.index != 1 || player.position.value < 1.0) kotlinx.coroutines.delay(100)
                true
            } == true
            val line = "end-of-song: '${tracks[0].title}' finished and '${tracks[1].title}' started = $next"
            AppLog.i("SelfTest", line)
            report.appendLine(line)
            ok = ok && next
        }
        player.clearQueue().join()
        File(dir, "selftest.txt").writeText(report.toString() + if (ok) "PASS\n" else "FAIL\n")
        ok
    }
}
