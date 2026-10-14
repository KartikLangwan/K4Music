package com.k4music.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import coil3.compose.setSingletonImageLoaderFactory
import com.k4music.desktop.player.Mpv
import com.k4music.desktop.player.PlayerController
import com.k4music.desktop.player.QueueStore
import com.k4music.desktop.player.toTrack
import com.k4music.providers.innertube.requests.searchPage
import com.k4music.providers.innertube.utils.from
import com.k4music.desktop.prefs.Settings
import com.k4music.desktop.prefs.ThemeMode
import com.k4music.desktop.res.Res
import com.k4music.desktop.res.app_name
import com.k4music.desktop.res.desktop_output_lost
import com.k4music.desktop.res.desktop_quit
import com.k4music.desktop.res.desktop_show_window
import com.k4music.desktop.res.k4_logo
import com.k4music.desktop.res.next_track
import com.k4music.desktop.res.play
import com.k4music.desktop.res.pause
import com.k4music.desktop.res.previous_track
import com.k4music.desktop.ui.K4Theme
import com.k4music.desktop.ui.LocalNavigator
import com.k4music.desktop.ui.LocalPlayer
import com.k4music.desktop.ui.Navigator
import com.k4music.desktop.ui.Screen
import com.k4music.desktop.ui.Shell
import com.k4music.desktop.ui.SidePanel
import com.k4music.desktop.ui.k4ImageLoader
import com.k4music.desktop.ui.parseHexColor
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.system.exitProcess
import kotlinx.coroutines.launch

fun main(args: Array<String>) {
    Thread.setDefaultUncaughtExceptionHandler { thread, error -> AppLog.e("Crash", "Uncaught on ${thread.name}", error) }
    // Before any window: Windows then names and groups the app as "K4 Music" everywhere
    // (taskbar, pinned shortcut, notifications), not as the Java runtime.
    TitleBar.setAppId()
    // A library restore staged in Settings is applied before anything opens the database.
    com.k4music.desktop.data.Maintenance.applyPendingRestore()
    val navigator = Navigator()
    var openLink: (String) -> Unit = {}
    var requestQuit: () -> Unit = {}
    // Links from a second launch arrive on a socket thread; handle them on the UI thread.
    // "--quit" comes from the installer: close properly (save queue and settings) before an update.
    val incoming = { lines: List<String> ->
        lines.forEach { line -> javax.swing.SwingUtilities.invokeLater { if (line == QUIT_ARG) requestQuit() else openLink(line) } }
    }
    val devCheck = args.any { it in setOf("--selftest", "--screenshots", "--uitest") }
    if (!devCheck && !SingleInstance.acquire(args) { incoming(it); Unit }) exitProcess(0)
    // Nothing was running to close: do not start the app either.
    if (QUIT_ARG in args) exitProcess(0)
    AppLog.i("App", "K4 Music ${System.getProperty("jpackage.app-version") ?: "dev"} starting")

    val resolver = com.k4music.desktop.player.StreamResolver()
    val player = PlayerController(Mpv(AppPaths.tools), resolver)
    com.k4music.desktop.player.Downloads.resolver = resolver
    com.k4music.desktop.data.LibraryHooks.attach(player)
    if (!devCheck) DiscordPresence.attach(player)
    player.onAudioDeviceLost = {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
            com.k4music.desktop.ui.AppDialogs.show(org.jetbrains.compose.resources.getString(com.k4music.desktop.res.Res.string.desktop_output_lost))
        }
    }
    devMode(args, player)
    QueueStore.attach(player)
    // Once a day, a little after start: offer a new version (each version is offered once).
    if (!devCheck) kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
        kotlinx.coroutines.delay(20_000)
        if (System.currentTimeMillis() - Settings.updateCheckedAt < 20 * 60 * 60 * 1000L) return@launch
        val update = runCatching { Updater.check() }.getOrNull()
        Settings.updateCheckedAt = System.currentTimeMillis()
        if (update != null && update.version != Settings.updateOfferedVersion && com.k4music.desktop.ui.AppDialogs.current == null) {
            Settings.updateOfferedVersion = update.version
            com.k4music.desktop.ui.AppDialogs.current = com.k4music.desktop.ui.AppDialogs.Dialog.Update(update)
        }
    }
    // Warm yt-dlp once (Windows scans a program on its first launch), so the first song starts faster.
    Thread({ Thread.sleep(5_000); com.k4music.desktop.data.Maintenance.ytDlpVersion() }, "k4-warmup").apply { isDaemon = true }.start()
    openLink = { Links.open(it, navigator, player) }
    args.forEach { openLink(it) }

    application {
        setSingletonImageLoaderFactory(::k4ImageLoader)
        val windowState = rememberWindowState(
            size = DpSize(Settings.windowWidth.dp, Settings.windowHeight.dp),
            placement = if (Settings.windowMaximized) WindowPlacement.Maximized else WindowPlacement.Floating
        )
        val visible = remember { mutableStateOf(true) }
        val panel = remember { mutableStateOf<SidePanel?>(null) }
        val playerOpen = remember { mutableStateOf(false) }
        val searchFocus = remember { FocusRequester() }
        // F11: borderless full screen (see Fullscreen), back to the window it was before.
        var composeWindow: androidx.compose.ui.awt.ComposeWindow? = null
        fun toggleFullscreen() { composeWindow?.let(Fullscreen::toggle) }

        fun quit() {
            // In full screen the window covers the monitor: keep the size it had before.
            if (!Fullscreen.active) {
                Settings.windowMaximized = windowState.placement == WindowPlacement.Maximized
                if (!Settings.windowMaximized) {
                    Settings.windowWidth = windowState.size.width.value.toInt()
                    Settings.windowHeight = windowState.size.height.value.toInt()
                }
            }
            DiscordPresence.close()
            player.close()
            Settings.flush()
            com.k4music.desktop.data.Db.close()
            exitApplication()
        }
        requestQuit = ::quit

        Tray(
            icon = painterResource(Res.drawable.k4_logo),
            tooltip = stringResource(Res.string.app_name),
            onAction = { visible.value = true },
            menu = {
                Item(stringResource(Res.string.desktop_show_window), onClick = { visible.value = true })
                Item(stringResource(Res.string.play), onClick = { player.togglePlay() })
                Item(stringResource(Res.string.next_track), onClick = { player.next() })
                Item(stringResource(Res.string.previous_track), onClick = { player.previous() })
                Separator()
                Item(stringResource(Res.string.desktop_quit), onClick = ::quit)
            }
        )

        Window(
            onCloseRequest = { if (Settings.minimizeToTray) visible.value = false else quit() },
            visible = visible.value,
            state = windowState,
            title = stringResource(Res.string.app_name),
            icon = painterResource(Res.drawable.k4_logo),
            onPreviewKeyEvent = { event ->
                if (event.type != KeyEventType.KeyDown) return@Window false
                when {
                    event.isCtrlPressed && event.key == Key.DirectionRight -> { player.next(); true }
                    event.isCtrlPressed && event.key == Key.DirectionLeft -> { player.previous(); true }
                    event.isCtrlPressed && event.key == Key.F -> { runCatching { searchFocus.requestFocus() }; true }
                    event.isCtrlPressed && event.key == Key.B -> { Settings.sidebarCollapsed = !Settings.sidebarCollapsed; true }
                    event.isAltPressed && event.key == Key.DirectionLeft -> { navigator.back(); true }
                    event.isAltPressed && event.key == Key.DirectionRight -> { navigator.forward(); true }
                    event.key == Key.F11 -> { toggleFullscreen(); true }
                    event.key == Key.Escape && Fullscreen.active && !playerOpen.value -> { toggleFullscreen(); true }
                    event.key == Key.MediaPlayPause -> { player.togglePlay(); true }
                    event.key == Key.MediaNext -> { player.next(); true }
                    event.key == Key.MediaPrevious -> { player.previous(); true }
                    event.key == Key.Escape && playerOpen.value -> { playerOpen.value = false; true }
                    event.key == Key.Escape && navigator.canGoBack -> { navigator.back(); true }
                    else -> false
                }
            },
            // Space reaches the window only when no text field took it, so typing a space still works.
            onKeyEvent = { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Spacebar) { player.togglePlay(); true } else false
            }
        ) {
            window.minimumSize = java.awt.Dimension(900, 600)
            composeWindow = window
            androidx.compose.runtime.LaunchedEffect(Unit) {
                val ms = System.currentTimeMillis() - java.lang.management.ManagementFactory.getRuntimeMXBean().startTime
                AppLog.i("App", "Window ready ${ms}ms after launch")
            }
            // Previous / Play-Pause / Next under the taskbar thumbnail (Windows-native controls).
            val taskbarLabels = Taskbar.Labels(
                stringResource(Res.string.previous_track), stringResource(Res.string.play),
                stringResource(Res.string.pause), stringResource(Res.string.next_track)
            )
            androidx.compose.runtime.LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(800) // the taskbar button exists once the window is shown
                Taskbar.attach(window, taskbarLabels) { button ->
                    when (button) {
                        1 -> player.previous()
                        2 -> player.togglePlay()
                        3 -> player.next()
                    }
                }
                player.state.collect { Taskbar.setPlaying(it.playing) }
            }
            val dark = when (Settings.themeMode) {
                ThemeMode.Dark -> true
                ThemeMode.Light -> false
                ThemeMode.System -> systemDark
            }
            K4Theme(accent = parseHexColor(Settings.accentColorHex), dark = dark) {
                // The Windows title bar follows the app theme instead of staying white.
                val background = androidx.compose.material3.MaterialTheme.colorScheme.background
                val onBackground = androidx.compose.material3.MaterialTheme.colorScheme.onBackground
                androidx.compose.runtime.LaunchedEffect(dark, background) { TitleBar.style(window, dark, background, onBackground) }
                CompositionLocalProvider(LocalNavigator provides navigator, LocalPlayer provides player) {
                    Shell(
                        panel, playerOpen, searchFocus,
                        fullscreen = Fullscreen.active,
                        onToggleFullscreen = ::toggleFullscreen
                    )
                }
            }
        }
    }
}

/** Sent by the installer to a running copy (see SingleInstance) so it closes before an update. */
private const val QUIT_ARG = "--quit"

/** Windows "Choose your app mode" (AppsUseLightTheme = 0 means dark), read once at start. */
private val systemDark: Boolean by lazy { runCatching {
    val process = ProcessBuilder(
        "reg", "query", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize", "/v", "AppsUseLightTheme"
    ).redirectErrorStream(true).start()
    val out = process.inputStream.bufferedReader().readText()
    process.waitFor()
    out.contains("0x0")
}.getOrDefault(true) }

/** `--selftest <dir>` and `--screenshots <dir>`: run the developer checks and exit (see DevTools). */
private fun devMode(args: Array<String>, player: PlayerController) {
    fun arg(name: String) = args.indexOf(name).takeIf { it >= 0 }?.let { args.getOrNull(it + 1) }?.let { java.io.File(it) }
    val selftest = arg("--selftest")
    val shots = arg("--screenshots")
    val uitest = arg("--uitest")
    if (selftest == null && shots == null && uitest == null) return
    val sample = kotlinx.coroutines.runBlocking {
        val youtube = com.k4music.providers.innertube.Innertube.searchPage(
            com.k4music.providers.innertube.models.bodies.SearchBody(
                query = "Shape of You Ed Sheeran", params = com.k4music.providers.innertube.Innertube.SearchFilter.Song.value
            ),
            com.k4music.providers.innertube.Innertube.SongItem::from
        )?.getOrNull()?.items?.firstNotNullOfOrNull { it.toTrack() }
        val second = com.k4music.providers.innertube.Innertube.searchPage(
            com.k4music.providers.innertube.models.bodies.SearchBody(
                query = "Tum Hi Ho Arijit Singh", params = com.k4music.providers.innertube.Innertube.SearchFilter.Song.value
            ),
            com.k4music.providers.innertube.Innertube.SongItem::from
        )?.getOrNull()?.items?.firstNotNullOfOrNull { it.toTrack() }
        val saavn = com.k4music.desktop.k4.searchSaavnSongs("Kesariya Arijit Singh").firstOrNull()?.toTrack()
        listOfNotNull(youtube, second, saavn)
    }
    var ok = true
    if (selftest != null) ok = DevTools.selftest(selftest, player, sample)
    if (shots != null) {
        player.restore(sample, 0, 42.0)
        DevTools.screenshots(
            shots, player,
            listOf(
                "home" to Screen.Home, "discover" to Screen.Discover, "search" to Screen.Search("arijit singh"),
                "library" to Screen.Library, "settings" to Screen.Settings
            )
        )
    }
    if (uitest != null) {
        kotlinx.coroutines.runBlocking { player.play(sample).join() }
        DevTools.uitest(uitest, player, uitest.resolve("script.txt").readLines())
    }
    player.close()
    Settings.flush()
    exitProcess(if (ok) 0 else 1)
}
