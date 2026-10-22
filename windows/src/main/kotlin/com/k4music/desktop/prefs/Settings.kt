package com.k4music.desktop.prefs

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.k4music.desktop.AppLog
import com.k4music.desktop.AppPaths
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Observable settings saved to settings.json. Each property is Compose state, so the UI follows
 * changes; every write is persisted (debounced to one file write per burst).
 */
abstract class SettingsStore(private val file: () -> java.io.File) {
    private val values: MutableMap<String, JsonElement> by lazy { load() }
    private val writer = java.util.concurrent.Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "k4-settings").apply { isDaemon = true }
    }
    @Volatile private var pending: java.util.concurrent.ScheduledFuture<*>? = null

    private fun load(): MutableMap<String, JsonElement> = runCatching {
        val f = file()
        if (f.isFile) Json.parseToJsonElement(f.readText()).let { (it as JsonObject).toMutableMap() } else mutableMapOf()
    }.onFailure { AppLog.w("Settings", "Unreadable settings, using defaults", it) }.getOrDefault(mutableMapOf())

    @Synchronized
    private fun put(key: String, value: JsonElement) {
        values[key] = value
        pending?.cancel(false)
        pending = writer.schedule({ flush() }, 300, java.util.concurrent.TimeUnit.MILLISECONDS)
    }

    @Synchronized
    fun flush() {
        runCatching {
            val f = file()
            val tmp = f.resolveSibling(f.name + ".tmp")
            tmp.writeText(Json.encodeToString(JsonObject.serializer(), JsonObject(values)))
            runCatching {
                java.nio.file.Files.move(
                    tmp.toPath(), f.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE
                )
            }.recoverCatching {
                java.nio.file.Files.move(
                    tmp.toPath(), f.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                )
            }.getOrThrow()
        }.onFailure { AppLog.w("Settings", "Could not save settings", it) }
    }

    private inner class Pref<T>(
        private val default: T,
        private val read: (JsonPrimitive) -> T?,
        private val write: (T) -> JsonPrimitive
    ) : ReadWriteProperty<Any?, T> {
        private var state: androidx.compose.runtime.MutableState<T>? = null

        private fun state(key: String) = state ?: mutableStateOf(
            values[key]?.let { runCatching { read(it.jsonPrimitive) }.getOrNull() } ?: default
        ).also { state = it }

        override fun getValue(thisRef: Any?, property: KProperty<*>): T = state(property.name).value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
            var current by state(property.name)
            if (current == value) return
            current = value
            put(property.name, write(value))
        }
    }

    protected fun boolean(default: Boolean): ReadWriteProperty<Any?, Boolean> =
        Pref(default, { it.booleanOrNull }, ::JsonPrimitive)
    protected fun int(default: Int): ReadWriteProperty<Any?, Int> =
        Pref(default, { it.intOrNull }, ::JsonPrimitive)
    protected fun long(default: Long): ReadWriteProperty<Any?, Long> =
        Pref(default, { it.longOrNull }, ::JsonPrimitive)
    protected fun float(default: Float): ReadWriteProperty<Any?, Float> =
        Pref(default, { it.floatOrNull }, ::JsonPrimitive)
    protected fun string(default: String): ReadWriteProperty<Any?, String> =
        Pref(default, { it.content }, ::JsonPrimitive)
    protected inline fun <reified E : Enum<E>> enum(default: E): ReadWriteProperty<Any?, E> =
        enumPref(default) { name -> enumValues<E>().firstOrNull { it.name == name } }

    protected fun <E : Enum<E>> enumPref(default: E, parse: (String) -> E?): ReadWriteProperty<Any?, E> =
        Pref(default, { parse(it.content) }, { JsonPrimitive(it.name) })
}

enum class RepeatMode { Off, All, One;
    val next get() = entries[(ordinal + 1) % entries.size]
}

/** Same choices as Android's PlayerPreferences.AudioQuality. */
enum class AudioQuality(val saavn: String, val youtubeItags: List<Int>) {
    DataSaver("96", listOf(250, 249, 139)),
    Balanced("160", listOf(140, 251)),
    High("320", listOf(774, 251, 140)) // 774: 256 kbps Opus when YouTube offers it
}

enum class ThemeMode { System, Dark, Light }

/** What sits behind pages: full blurred artwork, glowing color aura, living liquid canvas, midnight neon, scenic nature landscapes, OLED black, accent, or user picture. */
enum class BackgroundStyle {
    BlurredArtwork,
    LiquidCanvas,
    GlowAura,
    MidnightNeon,
    Alpine,
    Aurora,
    PureBlack,
    Accent,
    Image;

    companion object {
        fun fromName(name: String): BackgroundStyle = when (name) {
            "Artwork" -> BlurredArtwork
            else -> entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: BlurredArtwork
        }
    }
}

/** Where the Home / Discover / Search / Library buttons sit. */
enum class NavPosition { Side, Bottom }

enum class Reverb { None, SmallRoom, LargeHall }

object Settings : SettingsStore({ AppPaths.settings }) {
    // Player (names mirror Android's PlayerPreferences)
    var smartFallback by boolean(true)
    var persistentQueue by boolean(true)
    var repeatMode by enum(RepeatMode.Off)
    var shuffleEnabled by boolean(false)
    var audioQuality by enum(AudioQuality.High)
    var volume by int(80)
    var muted by boolean(false)
    /** Sound output: "auto" follows the Windows default, else mpv's device id (speakers, Bluetooth…). */
    var audioDevice by string("auto")
    /** "Playing K4 Music" on Discord (see DiscordPresence). Off by default: it shares what you listen to. */
    var discordPresence by boolean(false)
    /** Daily update check: when it last ran, and the version already offered (asked once). */
    var updateCheckedAt by long(0L)
    var updateOfferedVersion by string("")
    var speed by float(1f)
    var pitch by float(1f)
    var skipSilence by boolean(false)
    var volumeNormalization by boolean(true)
    var sponsorBlockEnabled by boolean(false)
    var sponsorBlockCategories by string("sponsor,selfpromo,intro,outro,music_offtopic")
    var resumeOnStart by boolean(false)
    var launchAtStartup by boolean(false)
    var startMinimizedOnStartup by boolean(true)

    // Audio effects (mpv filter chain; same choices as Android's AudioScreen)
    var equalizerEnabled by boolean(false)
    /** Ten band gains in dB (31 Hz … 16 kHz), comma separated. */
    var equalizerGains by string("0,0,0,0,0,0,0,0,0,0")
    var equalizerPreset by string("Flat")
    var bassBoost by int(0)          // 0..100
    var wideStage by int(0)          // 0..100
    var reverb by enum(Reverb.None)

    var isShowingLyrics by boolean(false)
    var isShowingSynchronizedLyrics by boolean(true)
    /** Active line scaling and vocal glow with bouncy spring scrolling. */
    var kineticLyrics by boolean(true)
    var reducedMotion by boolean(false)

    // Library
    var localFolders by string("")

    // Appearance
    var themeMode by enum(ThemeMode.Dark)
    var accentColorHex by string("#7B5CFF")
    /** Dynamic theme accent extracted directly from currently playing album art. */
    var syncAccentWithArtwork by boolean(true)
    var minimizeToTray by boolean(false)
    var navPosition by enum(NavPosition.Side)
    /** Side panel shrunk to icons only (Ctrl+B). */
    var sidebarCollapsed by boolean(false)
    /** Comma-separated names of navigation buttons the user hid (Discover, Search, Library). */
    var hiddenNav by string("")
    /** Shows your playlists under the navigation buttons, like a music library. */
    var sidebarPlaylists by boolean(true)
    var backgroundStyle by enumPref(BackgroundStyle.BlurredArtwork) { BackgroundStyle.fromName(it) }
    /** Backdrop blur radius (dp) for BlurredArtwork: 20, 45, 70, 95. */
    var backdropBlur by int(45)
    /** Backdrop darkness scrim percentage (35%, 50%, 65%, 80%) for contrast. */
    var backdropDim by int(55)
    /** Frosted acrylic glassmorphic panels with subtle borders. */
    var glassmorphism by boolean(true)
    /** Panel transparency/opacity percentage: 15% to 100%. */
    var panelOpacity by int(70)
    /** Ambient glow and gradient rings around artist avatars. */
    var artistGlow by boolean(true)
    /** Pure black surfaces for OLED displays. */
    var oledBlack by boolean(false)
    /** Picture file for BackgroundStyle.Image. */
    var backgroundImage by string("")
    /** Side panel and queue/lyrics panel widths (dp), set by dragging their edges. */
    var sidebarWidth by int(280)
    var sidePanelWidth by int(360)

    // Window
    var windowWidth by int(1280)
    var windowHeight by int(800)
    var windowMaximized by boolean(false)
}
