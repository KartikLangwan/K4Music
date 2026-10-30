package com.k4music.desktop.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.k4music.android.Brand
import com.k4music.desktop.AppLog
import com.k4music.desktop.AppPaths
import com.k4music.desktop.data.Db
import com.k4music.desktop.data.LocalMusic
import com.k4music.desktop.data.Maintenance
import com.k4music.desktop.player.AudioEffects
import com.k4music.desktop.player.Downloads
import com.k4music.desktop.prefs.AudioQuality
import com.k4music.desktop.prefs.Reverb
import com.k4music.desktop.prefs.Settings
import com.k4music.desktop.prefs.ThemeMode
import com.k4music.desktop.prefs.NavPosition
import com.k4music.desktop.prefs.BackgroundStyle
import com.k4music.providers.github.requests.releases
import com.k4music.desktop.res.*
import com.k4music.desktop.ui.AppDialogs
import com.k4music.desktop.ui.K4Slider
import com.k4music.desktop.ui.LocalPlayer
import com.k4music.desktop.ui.parseHexColor
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.text.DateFormat
import java.util.Date

private val accents = listOf("#F5F5F5", "#9A9A9A", "#00D2FF", "#FF5E62", "#00E676", "#FFB300", "#FF6584", "#4472CF")

private enum class SettingsPage(val title: StringResource, val subtitle: String, val icon: DrawableResource) {
    Appearance(Res.string.appearance, "Themes & Scenery", Res.drawable.color_palette),
    System(Res.string.desktop_system_title, "Windows & Startup", Res.drawable.settings),
    Playback(Res.string.player, "Audio & Engine", Res.drawable.play),
    Effects(Res.string.audio_effects, "Equalizer & DSP", Res.drawable.equalizer),
    Layout(Res.string.desktop_layout, "Sidebar & View", Res.drawable.grid),
    Library(Res.string.settings_section_library, "Folders & Data", Res.drawable.library),
    Transfer(Res.string.desktop_transfer_title, "Phone → PC", Res.drawable.sync),
    Advanced(Res.string.desktop_advanced, "yt-dlp & Logs", Res.drawable.server),
    About(Res.string.about, "Kartik Langwan", Res.drawable.information)
}

/** Kept while the app runs, so Settings reopens on the page you were looking at. */
private var lastPage by mutableStateOf(SettingsPage.Appearance)

@Composable
fun SettingsScreen() {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        Text(stringResource(Res.string.settings), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            // Accessible sidebar categories with icons and subtitles
            Column(Modifier.width(230.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SettingsPage.entries.forEach { page ->
                    val selected = page == lastPage
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(if (selected) colors.primaryContainer.copy(alpha = 0.85f) else Color.Transparent)
                            .then(if (selected) Modifier.border(1.dp, colors.primary.copy(alpha = 0.45f), RoundedCornerShape(12.dp)) else Modifier)
                            .clickable { lastPage = page }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        androidx.compose.material3.Icon(
                            painterResource(page.icon), null, Modifier.size(20.dp),
                            tint = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(page.title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                                color = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant
                            )
                            Text(
                                page.subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) colors.onPrimaryContainer.copy(alpha = 0.75f) else colors.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
            androidx.compose.runtime.key(lastPage) {
                Column(
                    Modifier.weight(1f).fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp, end = 8.dp)
                ) {
                    Column(Modifier.widthIn(max = 860.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        when (lastPage) {
                            SettingsPage.Appearance -> AppearanceSection()
                            SettingsPage.System -> SystemSection()
                            SettingsPage.Playback -> PlaybackSection()
                            SettingsPage.Effects -> EffectsSection()
                            SettingsPage.Layout -> LayoutSection()
                            SettingsPage.Library -> LibrarySection()
                            SettingsPage.Transfer -> TransferSection()
                            SettingsPage.Advanced -> AdvancedSection()
                            SettingsPage.About -> AboutSection()
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun LayoutSection() = Section(Res.string.desktop_layout) {
    Label(stringResource(Res.string.desktop_nav_position))
    Choices(
        listOf(
            NavPosition.Side to stringResource(Res.string.desktop_nav_side),
            NavPosition.Bottom to stringResource(Res.string.desktop_nav_bottom)
        ),
        Settings.navPosition
    ) { Settings.navPosition = it }
    Label(stringResource(Res.string.desktop_visible_buttons))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val hidden = Settings.hiddenNav.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        com.k4music.desktop.ui.navDestinations.filter { it.key != "Home" }.forEach { d ->
            FilterChip(
                selected = d.key !in hidden,
                onClick = { Settings.hiddenNav = (if (d.key in hidden) hidden - d.key else hidden + d.key).joinToString(",") },
                label = { Text(stringResource(d.title)) },
                leadingIcon = { androidx.compose.material3.Icon(painterResource(d.icon), null, Modifier.size(16.dp)) }
            )
        }
    }
    SwitchRow(stringResource(Res.string.desktop_compact_sidebar), stringResource(Res.string.desktop_compact_sidebar_description), Settings.sidebarCollapsed) { Settings.sidebarCollapsed = it }
    SwitchRow(stringResource(Res.string.desktop_sidebar_playlists), stringResource(Res.string.desktop_sidebar_playlists_description), Settings.sidebarPlaylists) { Settings.sidebarPlaylists = it }
    Label(stringResource(Res.string.desktop_shortcuts))
    Shortcuts(stringResource(Res.string.desktop_shortcuts_list))
}

/** "Ctrl+F  Search" lines drawn as keycaps beside what they do. */
@Composable
private fun Shortcuts(list: String) = Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    val colors = MaterialTheme.colorScheme
    list.lines().filter { it.isNotBlank() }.forEach { line ->
        val keys = line.substringBefore("  ").trim()
        val action = line.substringAfter("  ", "").trim()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.width(190.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                keys.split(" / ").forEachIndexed { i, combo ->
                    if (i > 0) Text("/", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                    combo.split('+').forEach { key ->
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = colors.surfaceContainerHigh,
                            border = BorderStroke(1.dp, colors.outlineVariant)
                        ) {
                            Text(key, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
                        }
                    }
                }
            }
            Text(action, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsSlider(value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier, onCommit: (Float) -> Unit = {}) =
    K4Slider(value, onChange, modifier, onCommit = onCommit, knob = true)

@Composable
private fun SystemSection() = Section(Res.string.desktop_system_title) {
    val startupFailure = stringResource(Res.string.desktop_startup_failed)
    val scope = rememberCoroutineScope()
    var registering by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        Settings.launchAtStartup = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.k4music.desktop.StartupManager.isEnabled()
        }
    }
    SwitchRow(
        stringResource(Res.string.desktop_startup_title),
        stringResource(Res.string.desktop_startup_hint),
        Settings.launchAtStartup
    ) {
        if (!registering) {
            registering = true
            scope.launch {
                try {
                    val success = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        com.k4music.desktop.StartupManager.setEnabled(it)
                    }
                    if (success) Settings.launchAtStartup = it
                    else AppDialogs.show(com.k4music.desktop.StartupManager.lastError ?: startupFailure)
                } finally { registering = false }
            }
        }
    }
    SwitchRow(
        stringResource(Res.string.desktop_start_minimized),
        stringResource(Res.string.desktop_start_minimized_hint),
        Settings.startMinimizedOnStartup
    ) { Settings.startMinimizedOnStartup = it }
    SwitchRow(
        stringResource(Res.string.desktop_tray_title),
        stringResource(Res.string.desktop_minimize_to_tray),
        Settings.minimizeToTray
    ) { Settings.minimizeToTray = it }
    SwitchRow(
        stringResource(Res.string.desktop_resume_on_start),
        stringResource(Res.string.desktop_resume_on_start_hint),
        Settings.resumeOnStart
    ) { Settings.resumeOnStart = it }
    if (com.k4music.desktop.DiscordPresence.available) {
        SwitchRow(
            stringResource(Res.string.desktop_discord),
            stringResource(Res.string.desktop_discord_hint),
            Settings.discordPresence
        ) { Settings.discordPresence = it }
    }
}

@Composable
private fun Section(title: StringResource, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun SwitchRow(title: String, description: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (description != null) Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            // Off must stay visible on dark surfaces: light thumb, outlined track.
            colors = androidx.compose.material3.SwitchDefaults.colors(
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                uncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        )
    }
}

@Composable
private fun Label(text: String, value: String? = null) = Row(Modifier.fillMaxWidth()) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
    if (value != null) Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun <T> Choices(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val colors = MaterialTheme.colorScheme
        options.forEach { (value, label) ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(label) },
                colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                    selectedContainerColor = colors.primary.copy(alpha = 0.22f),
                    selectedLabelColor = colors.onSurface
                )
            )
        }
    }
}

@Composable
private fun AppearanceSection() = Section(Res.string.appearance) {
    SwitchRow(stringResource(Res.string.desktop_reduced_motion), stringResource(Res.string.desktop_reduced_motion_hint), Settings.reducedMotion) {
        Settings.reducedMotion = it
    }
    Label(stringResource(Res.string.desktop_theme))
    Choices(
        listOf(
            ThemeMode.System to stringResource(Res.string.desktop_theme_system),
            ThemeMode.Dark to stringResource(Res.string.color_mode_dark),
            ThemeMode.Light to stringResource(Res.string.color_mode_light)
        ),
        Settings.themeMode
    ) { Settings.themeMode = it }

    Label(stringResource(Res.string.desktop_background))
    Choices(
        listOf(
            BackgroundStyle.BlurredArtwork to stringResource(Res.string.desktop_bg_blurred_artwork),
            BackgroundStyle.LiquidCanvas to "Liquid Canvas",
            BackgroundStyle.GlowAura to stringResource(Res.string.desktop_bg_glow_aura),
            BackgroundStyle.MidnightNeon to "Midnight Neon",
            BackgroundStyle.Alpine to stringResource(Res.string.desktop_bg_alpine),
            BackgroundStyle.Aurora to stringResource(Res.string.desktop_bg_aurora),
            BackgroundStyle.PureBlack to stringResource(Res.string.desktop_bg_pure_black),
            BackgroundStyle.Accent to stringResource(Res.string.desktop_bg_accent),
            BackgroundStyle.Image to stringResource(Res.string.desktop_bg_image)
        ),
        Settings.backgroundStyle
    ) { style ->
        if (style == BackgroundStyle.Image && Settings.backgroundImage.isBlank()) pickPicture() else Settings.backgroundStyle = style
    }

    if (Settings.backgroundStyle == BackgroundStyle.BlurredArtwork) {
        Label(stringResource(Res.string.desktop_backdrop_blur), "${Settings.backdropBlur} dp")
        Choices(
            listOf(
                20 to "Subtle (20 dp)",
                45 to "Balanced (45 dp)",
                70 to "Deep (70 dp)",
                95 to "Ultra (95 dp)"
            ),
            Settings.backdropBlur
        ) { Settings.backdropBlur = it }

        Label(stringResource(Res.string.desktop_backdrop_dim), "${Settings.backdropDim}%")
        Choices(
            listOf(
                35 to "Light (35%)",
                55 to "Balanced (55%)",
                70 to "High Contrast (70%)",
                85 to "Night (85%)"
            ),
            Settings.backdropDim
        ) { Settings.backdropDim = it }
    } else if (Settings.backgroundStyle in listOf(BackgroundStyle.Alpine, BackgroundStyle.Aurora, BackgroundStyle.LiquidCanvas)) {
        Label(stringResource(Res.string.desktop_backdrop_dim), "${Settings.backdropDim}%")
        Choices(
            listOf(
                35 to "Light (35%)",
                55 to "Balanced (55%)",
                70 to "High Contrast (70%)",
                85 to "Night (85%)"
            ),
            Settings.backdropDim
        ) { Settings.backdropDim = it }
    } else if (Settings.backgroundStyle == BackgroundStyle.Image) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { pickPicture() }) { Text(stringResource(Res.string.desktop_choose_picture)) }
            Text(File(Settings.backgroundImage).name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Label(stringResource(Res.string.desktop_backdrop_dim), "${Settings.backdropDim}%")
        Choices(
            listOf(
                35 to "Light (35%)",
                55 to "Balanced (55%)",
                70 to "High Contrast (70%)",
                85 to "Night (85%)"
            ),
            Settings.backdropDim
        ) { Settings.backdropDim = it }
    }

    Label(stringResource(Res.string.desktop_panel_transparency), "${Settings.panelOpacity}%")
    SettingsSlider(
        value = (Settings.panelOpacity - 15) / 85f,
        onChange = { Settings.panelOpacity = (15 + it * 85).toInt() },
        modifier = Modifier.fillMaxWidth()
    )
    Choices(
        listOf(
            35 to "Glassy (35%)",
            60 to "Balanced (60%)",
            78 to "Frosted (78%)",
            100 to "Solid (100%)"
        ),
        Settings.panelOpacity
    ) { Settings.panelOpacity = it }

    SwitchRow(
        stringResource(Res.string.desktop_glassmorphism),
        stringResource(Res.string.desktop_glassmorphism_desc),
        Settings.glassmorphism
    ) { Settings.glassmorphism = it }

    SwitchRow(
        stringResource(Res.string.desktop_artist_glow),
        stringResource(Res.string.desktop_artist_glow_desc),
        Settings.artistGlow
    ) { Settings.artistGlow = it }

    SwitchRow(
        stringResource(Res.string.desktop_oled_black),
        stringResource(Res.string.desktop_oled_black_desc),
        Settings.oledBlack
    ) { Settings.oledBlack = it }

    Label(stringResource(Res.string.desktop_accent))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        accents.forEach { hex ->
            val selected = Settings.accentColorHex.equals(hex, ignoreCase = true)
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(parseHexColor(hex))
                    .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onBackground, CircleShape) else Modifier)
                    .clickable { Settings.accentColorHex = hex }
            )
        }
    }

    SwitchRow(
        "Sync Accent with Artwork",
        "Dynamically adjust theme accent color to match currently playing song album artwork",
        Settings.syncAccentWithArtwork
    ) { Settings.syncAccentWithArtwork = it }

    SwitchRow(
        "Kinetic Lyrics",
        "Enable active line scaling, smooth spring scrolling, and vocal highlight glow in lyrics panel",
        Settings.kineticLyrics
    ) { Settings.kineticLyrics = it }
}

@Composable
private fun PlaybackSection() = Section(Res.string.player) {
    val player = LocalPlayer.current
    val state by player.state.collectAsState()
    SwitchRow(stringResource(Res.string.smart_fallback), stringResource(Res.string.smart_fallback_description), Settings.smartFallback) { Settings.smartFallback = it }
    Label("YouTube sign-in (fixes \"confirm you're not a bot\" downloads)")
    Text(
        "Uses your browser's YouTube sign-in only on this PC, through the built-in downloader. Nothing is sent anywhere else. Close that browser first if it fails.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Choices(
        com.k4music.desktop.prefs.YtCookies.entries.map { it to if (it.browser == null) "Off" else it.name },
        Settings.ytCookies
    ) { Settings.ytCookies = it }
    com.k4music.desktop.ui.AudioOutputSettings(player)
    Label(stringResource(Res.string.audio_quality))
    Choices(
        listOf(
            AudioQuality.DataSaver to stringResource(Res.string.audio_quality_data_saver),
            AudioQuality.Balanced to stringResource(Res.string.audio_quality_balanced),
            AudioQuality.High to stringResource(Res.string.audio_quality_high)
        ),
        Settings.audioQuality
    ) { Settings.audioQuality = it }
    SwitchRow(stringResource(Res.string.persistent_queue), stringResource(Res.string.persistent_queue_description), Settings.persistentQueue) { Settings.persistentQueue = it }
    SwitchRow(stringResource(Res.string.skip_silence), stringResource(Res.string.skip_silence_description), Settings.skipSilence) {
        Settings.skipSilence = it; player.applyEffects()
    }
    SwitchRow(stringResource(Res.string.desktop_normalization), stringResource(Res.string.desktop_normalization_description), Settings.volumeNormalization) {
        Settings.volumeNormalization = it; player.applyEffects()
    }
    SwitchRow(stringResource(Res.string.sponsor_block), stringResource(Res.string.sponsor_block_description), Settings.sponsorBlockEnabled) { Settings.sponsorBlockEnabled = it }

    Label(stringResource(Res.string.desktop_speed), "%.2fx".format(Settings.speed))
    SettingsSlider((Settings.speed - 0.5f) / 1.5f, onChange = { Settings.speed = (0.5f + it * 1.5f).let { v -> (v * 20).toInt() / 20f } }, onCommit = { player.applyEffects() }, modifier = Modifier.fillMaxWidth())
    Label(stringResource(Res.string.desktop_pitch), "%.2fx".format(Settings.pitch))
    SettingsSlider((Settings.pitch - 0.5f) / 1.5f, onChange = { Settings.pitch = (0.5f + it * 1.5f).let { v -> (v * 20).toInt() / 20f } }, onCommit = { player.applyEffects() }, modifier = Modifier.fillMaxWidth())

    val sleepAt = state.sleepAt
    // While a timer runs, mark the length picked here (none when it was set before this screen opened).
    var sleepPicked by remember { mutableStateOf<Int?>(null) }
    Label(stringResource(Res.string.sleep_timer), sleepAt?.let { stringResource(Res.string.desktop_sleep_until, DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it))) })
    Choices(
        listOf<Int?>(null, 15, 30, 45, 60, 90).map { m ->
            m to (if (m == null) stringResource(Res.string.desktop_off) else stringResource(Res.string.desktop_minutes, m))
        },
        if (sleepAt == null) null else sleepPicked ?: -1
    ) { sleepPicked = it; player.setSleepTimer(it) }
}

@Composable
private fun EffectsSection() = Section(Res.string.audio_effects) {
    val player = LocalPlayer.current
    var gains by remember { mutableStateOf(AudioEffects.gains()) }
    SwitchRow(stringResource(Res.string.equalizer), stringResource(Res.string.equalizer_effect_description), Settings.equalizerEnabled) {
        Settings.equalizerEnabled = it; player.applyEffects()
    }
    if (Settings.equalizerEnabled) {
        Choices(AudioEffects.presets.keys.map { it to it }, Settings.equalizerPreset) { name ->
            gains = AudioEffects.presets.getValue(name)
            AudioEffects.setGains(gains, name)
            player.applyEffects()
        }
        EqualizerCurveView(gains, MaterialTheme.colorScheme.primary, Modifier.padding(vertical = 4.dp))
        AudioEffects.bands.forEachIndexed { i, hz ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (hz >= 1000) "${hz / 1000} kHz" else "$hz Hz", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(64.dp))
                SettingsSlider(
                    (gains[i] + AudioEffects.MAX_GAIN) / (2 * AudioEffects.MAX_GAIN),
                    onChange = { v -> gains = gains.toMutableList().also { it[i] = ((v * 2 - 1) * AudioEffects.MAX_GAIN * 2).toInt() / 2f } },
                    onCommit = { AudioEffects.setGains(gains, "Custom"); player.applyEffects() },
                    modifier = Modifier.weight(1f)
                )
                Text("%+.1f dB".format(gains[i]), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(64.dp))
            }
        }
    }
    Label(stringResource(Res.string.bass_boost), "${Settings.bassBoost}%")
    SettingsSlider(Settings.bassBoost / 100f, onChange = { Settings.bassBoost = (it * 100).toInt() }, onCommit = { player.applyEffects() }, modifier = Modifier.fillMaxWidth())
    Label(stringResource(Res.string.atd_wide_stage), "${Settings.wideStage}%")
    SettingsSlider(Settings.wideStage / 100f, onChange = { Settings.wideStage = (it * 100).toInt() }, onCommit = { player.applyEffects() }, modifier = Modifier.fillMaxWidth())
    Label(stringResource(Res.string.reverb))
    Choices(
        listOf(
            Reverb.None to stringResource(Res.string.desktop_reverb_none),
            Reverb.SmallRoom to stringResource(Res.string.desktop_reverb_small),
            Reverb.LargeHall to stringResource(Res.string.desktop_reverb_large)
        ),
        Settings.reverb
    ) { Settings.reverb = it; player.applyEffects() }
}

@Composable
private fun LibrarySection() = Section(Res.string.settings_section_library) {
    val scope = rememberCoroutineScope()
    val downloads by Downloads.done.collectAsState()
    val activeDownloads by Downloads.progress.collectAsState()
    var pendingRestore by remember { mutableStateOf(Maintenance.hasPendingRestore()) }
    val deleteAllLabel = stringResource(Res.string.delete_all_downloads)
    Label(stringResource(Res.string.settings_backup_title))
    Text(stringResource(Res.string.settings_backup_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = {
            saveFile("k4audio-${System.currentTimeMillis() / 1000}.db") { target ->
                scope.launch {
                    runCatching { Maintenance.backup(target) }
                        .onSuccess { AppDialogs.show(getString(Res.string.backup_saved)) }
                        .onFailure { AppLog.w("Backup", "Backup failed", it); AppDialogs.show(getString(Res.string.backup_failed)) }
                }
            }
        }) { Text(stringResource(Res.string.backup)) }
        OutlinedButton(onClick = {
            openFile { source ->
                scope.launch {
                    runCatching { Maintenance.stageRestore(source) }
                        .onSuccess { pendingRestore = true }
                        .onFailure { AppLog.w("Backup", "Restore rejected", it); AppDialogs.show(getString(Res.string.restore_failed)) }
                }
            }
        }) { Text(stringResource(Res.string.restore)) }
    }
    if (pendingRestore) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            Text(stringResource(Res.string.desktop_restore_pending), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            FilledTonalButton(onClick = { Maintenance.restartApp() }) {
                Text(stringResource(Res.string.desktop_restart_now))
            }
            OutlinedButton(onClick = {
                scope.launch {
                    try { Maintenance.cancelPendingRestore(); pendingRestore = false }
                    catch (error: kotlinx.coroutines.CancellationException) { throw error }
                    catch (error: Exception) { AppDialogs.show(error.message ?: getString(Res.string.restore_failed)) }
                }
            }) { Text(stringResource(Res.string.desktop_cancel_restore)) }
            Maintenance.lastRestoreError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }

    Label(stringResource(Res.string.offline), pluralStringResource(Res.plurals.desktop_downloads_count, downloads.size, downloads.size))
    OutlinedButton(enabled = downloads.isNotEmpty() || activeDownloads.isNotEmpty(), onClick = { AppDialogs.confirm(deleteAllLabel) { Downloads.deleteAll() } }) {
        Text(stringResource(Res.string.delete_all_downloads))
    }

    DownloadFailures()

    Label(stringResource(Res.string.desktop_music_folders))
    LocalMusic.folders().forEach { Text(it.absolutePath, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    Text(
        "Copy songs into ${LocalMusic.appFolder.absolutePath} (or any folder you add) and press Scan: they appear under Library → Local. To bring music from your phone, copy it from the phone's Music folder over USB.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { LocalMusic.openAppFolder() }) { Text("Open music folder") }
        OutlinedButton(onClick = { AppDialogs.pickFolder { LocalMusic.addFolder(it); scope.launch { AppDialogs.scanLocalMusic() } } }) { Text(stringResource(Res.string.desktop_add_folder)) }
        OutlinedButton(onClick = { scope.launch { AppDialogs.scanLocalMusic() } }) { Text(stringResource(Res.string.desktop_scan)) }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { scope.launch { Db.library.clearEvents(); AppDialogs.show(getString(Res.string.desktop_done)) } }) { Text(stringResource(Res.string.desktop_clear_history)) }
        OutlinedButton(onClick = { scope.launch { Db.library.clearQueries(); AppDialogs.show(getString(Res.string.desktop_done)) } }) { Text(stringResource(Res.string.clear_search_history)) }
    }
}

/** Phone transfer: a QR code the phone scans to send its likes and playlists here, over the local network only. */
@Composable
private fun TransferSection() = Section(Res.string.desktop_transfer_title) {
    val state by com.k4music.desktop.data.PhoneTransfer.state.collectAsState()
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    // Nothing keeps listening once the user leaves this page.
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { com.k4music.desktop.data.PhoneTransfer.stop() } }
    Text(stringResource(Res.string.desktop_transfer_description), style = MaterialTheme.typography.bodySmall, color = secondary)
    when (val st = state) {
        is com.k4music.desktop.data.PhoneTransfer.State.Waiting -> {
            QrCode(st.invite.text(), Modifier.size(220.dp))
            Text(stringResource(Res.string.desktop_transfer_waiting), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { com.k4music.desktop.data.PhoneTransfer.stop() }) { Text(stringResource(Res.string.cancel)) }
        }
        else -> {
            when (st) {
                is com.k4music.desktop.data.PhoneTransfer.State.Done -> Text(
                    if (st.imported.likes == 0 && st.imported.playlists == 0) stringResource(Res.string.desktop_transfer_nothing_new)
                    else stringResource(Res.string.desktop_transfer_done, st.imported.likes, st.imported.playlists, st.imported.songs),
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold
                )
                com.k4music.desktop.data.PhoneTransfer.State.Expired -> Text(stringResource(Res.string.desktop_transfer_expired), style = MaterialTheme.typography.bodyMedium)
                com.k4music.desktop.data.PhoneTransfer.State.NoNetwork -> Text(stringResource(Res.string.desktop_transfer_no_network), color = MaterialTheme.colorScheme.error)
                else -> Unit
            }
            FilledTonalButton(onClick = { com.k4music.desktop.data.PhoneTransfer.start() }) { Text(stringResource(Res.string.desktop_transfer_start)) }
        }
    }
}

/** Black modules on a white square (with a quiet border), as phone scanners expect in light or dark theme. */
@Composable
private fun QrCode(text: String, modifier: Modifier) {
    val matrix = remember(text) {
        com.google.zxing.qrcode.QRCodeWriter().encode(
            text, com.google.zxing.BarcodeFormat.QR_CODE, 0, 0, mapOf(com.google.zxing.EncodeHintType.MARGIN to 2)
        )
    }
    Canvas(modifier.background(androidx.compose.ui.graphics.Color.White)) {
        val cell = size.minDimension / matrix.width
        for (y in 0 until matrix.height) for (x in 0 until matrix.width) if (matrix.get(x, y)) {
            drawRect(
                androidx.compose.ui.graphics.Color.Black,
                topLeft = androidx.compose.ui.geometry.Offset(x * cell, y * cell),
                size = androidx.compose.ui.geometry.Size(cell + 0.5f, cell + 0.5f)
            )
        }
    }
}

@Composable
private fun AdvancedSection() = Section(Res.string.desktop_advanced) {
    val scope = rememberCoroutineScope()
    var version by remember { mutableStateOf("…") }
    var updating by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { version = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Maintenance.ytDlpVersion() } }
    Label(stringResource(Res.string.desktop_ytdlp), version)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(enabled = !updating, onClick = {
            updating = true
            scope.launch {
                try {
                    val result = Maintenance.updateYtDlp()
                    version = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Maintenance.ytDlpVersion() }
                    AppDialogs.show(result.ifBlank { version })
                } catch (error: kotlinx.coroutines.CancellationException) {
                    throw error
                } catch (error: Exception) {
                    AppDialogs.show(error.message ?: getString(Res.string.desktop_extractor_update_failed))
                } finally { updating = false }
            }
        }) { Text(stringResource(Res.string.desktop_update)) }
        OutlinedButton(onClick = { open(AppPaths.logs) }) { Text(stringResource(Res.string.desktop_open_logs)) }
        OutlinedButton(onClick = { open(AppPaths.data) }) { Text(stringResource(Res.string.desktop_open_data)) }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AboutSection() = Section(Res.string.about) {
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    // Creator Showcase Card
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.surfaceContainerHighest.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // Hero Banner Image with curved top corners (Alpine nature scene)
            Box(Modifier.fillMaxWidth().height(160.dp)) {
                androidx.compose.foundation.Image(
                    painter = painterResource(Res.drawable.scenery_mountain),
                    contentDescription = "K4 Audio Alpine Landscape",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                )
                // Bottom fade scrim to blend into the card surface
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))
                        )
                    )
                )
                // App Version Pill in top-right corner
                Surface(
                    color = Color.Black.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
                ) {
                    Text(
                        "${Brand.APP_NAME} v$APP_VERSION",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Creator Details with Avatar
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Holographic Monogram Avatar Badge
                    androidx.compose.foundation.Image(
                        painter = painterResource(Res.drawable.creator_avatar),
                        contentDescription = Brand.CREATOR,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .border(2.5.dp, colors.primary, CircleShape)
                    )

                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                Brand.CREATOR,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = colors.onSurface
                            )
                            // Verified Creator Badge with sparkles icon
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = colors.primary.copy(alpha = 0.18f),
                                border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.45f))
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        painterResource(Res.drawable.sparkles),
                                        contentDescription = null,
                                        modifier = Modifier.size(11.dp),
                                        tint = colors.primary
                                    )
                                    Text(
                                        "VERIFIED",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = colors.primary
                                    )
                                }
                            }
                        }
                        Text(
                            stringResource(Res.string.desktop_creator_title),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.primary
                        )
                        Text(
                            "@${Brand.INSTAGRAM_HANDLE}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                }

                // Bio
                Text(
                    stringResource(Res.string.desktop_creator_bio),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )

                // Tech Stack Badges
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Compose Multiplatform", "Kotlin Coroutines", "libmpv Engine", "Material 3 Expressive", "JioSaavn & YT", "GPL-3.0").forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = colors.surfaceContainer,
                            border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.3f))
                        ) {
                            Text(
                                tag,
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))

                // Action Buttons
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        enabled = !checking,
                        onClick = {
                            checking = true
                            scope.launch {
                                checkForUpdate()
                                checking = false
                            }
                        }
                    ) {
                        Icon(painterResource(Res.drawable.sync), contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(Res.string.desktop_check_updates))
                    }
                    Button(
                        onClick = { browse(WEBSITE) },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
                    ) {
                        Icon(painterResource(Res.drawable.globe), contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(Res.string.desktop_website))
                    }
                    OutlinedButton(onClick = { browse(CREATOR_SITE) }) {
                        Icon(painterResource(Res.drawable.person), contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(Brand.CREATOR)
                    }
                    OutlinedButton(onClick = { browse(Brand.INSTAGRAM_URL) }) {
                        Text("@${Brand.INSTAGRAM_HANDLE}")
                    }
                    OutlinedButton(onClick = { browse(GITHUB) }) {
                        Text("GitHub")
                    }
                }
            }
        }
    }
}

const val WEBSITE = "https://k4music.vercel.app"
private const val GITHUB = "https://github.com/KartikLangwan/K4Music"
private const val CREATOR_SITE = "https://kartiklangwan.vercel.app"
private val APP_VERSION get() = System.getProperty("jpackage.app-version") ?: "2.14.26"

/**
 * Compares this version with the newest K4 Audio release; downloads always go through the
 * website. Offline or rate-limited: the website opens so the user can look there.
 */
private suspend fun checkForUpdate() {
    val result = runCatching { com.k4music.desktop.Updater.check() }
    val update = result.getOrNull()
    when {
        result.isFailure -> { AppDialogs.show(getString(Res.string.desktop_update_failed)); browse(WEBSITE) }
        update != null -> AppDialogs.current = AppDialogs.Dialog.Update(update)
        else -> AppDialogs.show(getString(Res.string.desktop_up_to_date, APP_VERSION))
    }
}

/** "2.12.14" vs "2.7.15": numeric, part by part. */
internal fun compareVersions(a: String, b: String): Int {
    val x = a.split('.', '-').map { it.toIntOrNull() ?: 0 }
    val y = b.split('.', '-').map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(x.size, y.size)) {
        val c = x.getOrElse(i) { 0 }.compareTo(y.getOrElse(i) { 0 })
        if (c != 0) return c
    }
    return 0
}

/** Lets the user pick a picture for the page background. */
private fun pickPicture() = javax.swing.SwingUtilities.invokeLater {
    val chooser = javax.swing.JFileChooser().apply {
        fileFilter = javax.swing.filechooser.FileNameExtensionFilter("Pictures", "jpg", "jpeg", "png", "webp", "bmp")
    }
    if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
        Settings.backgroundImage = chooser.selectedFile.absolutePath
        Settings.backgroundStyle = BackgroundStyle.Image
    }
}

private fun open(folder: File) = runCatching { Desktop.getDesktop().open(folder) }
private fun browse(url: String) = runCatching { Desktop.getDesktop().browse(URI(url)) }

private fun saveFile(name: String, onPicked: (File) -> Unit) = javax.swing.SwingUtilities.invokeLater {
    val chooser = javax.swing.JFileChooser().apply { selectedFile = File(System.getProperty("user.home"), name) }
    if (chooser.showSaveDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) onPicked(chooser.selectedFile)
}

private fun openFile(onPicked: (File) -> Unit) = javax.swing.SwingUtilities.invokeLater {
    val chooser = javax.swing.JFileChooser()
    if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) onPicked(chooser.selectedFile)
}

@Composable
private fun EqualizerCurveView(gains: List<Float>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(90.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainer)) {
        val w = size.width
        val h = size.height
        val midY = h / 2f

        drawLine(
            color = color.copy(alpha = 0.20f),
            start = Offset(0f, midY),
            end = Offset(w, midY),
            strokeWidth = 1.dp.toPx()
        )

        if (gains.isEmpty()) return@Canvas
        val step = w / (gains.size - 1).coerceAtLeast(1)
        val points = gains.mapIndexed { i, g ->
            val norm = (g / AudioEffects.MAX_GAIN).coerceIn(-1f, 1f)
            val y = midY - norm * (h * 0.38f)
            Offset(i * step, y)
        }

        val path = Path()
        val fillPath = Path()
        path.moveTo(points.first().x, points.first().y)
        fillPath.moveTo(points.first().x, midY)
        fillPath.lineTo(points.first().x, points.first().y)

        for (i in 0 until points.size - 1) {
            val p0 = points[i]
            val p1 = points[i + 1]
            val cx = (p0.x + p1.x) / 2f
            path.cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
            fillPath.cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
        }
        fillPath.lineTo(points.last().x, midY)
        fillPath.close()

        drawPath(
            fillPath,
            Brush.verticalGradient(
                listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0.05f)),
                startY = 0f,
                endY = h
            )
        )
        drawPath(
            path,
            color = color,
            style = Stroke(width = 2.5f.dp.toPx(), cap = StrokeCap.Round)
        )

        points.forEach { pt ->
            drawCircle(color = color, radius = 3.5f.dp.toPx(), center = pt)
            drawCircle(color = Color.White, radius = 1.5f.dp.toPx(), center = pt)
        }
    }
}

/** Failed downloads stay actionable after progress disappears. */
@Composable
private fun DownloadFailures() {
    val failed by Downloads.failed.collectAsState()
    val transfers by Downloads.transfers.collectAsState()
    if (transfers.isNotEmpty()) {
        Label(stringResource(Res.string.desktop_download_active), "${transfers.size}")
        transfers.values.forEach { transfer ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(transfer.track.title, style = MaterialTheme.typography.titleSmall)
                    val phase = when (transfer.phase) {
                        Downloads.Phase.Queued -> Res.string.desktop_download_queued
                        Downloads.Phase.Resolving -> Res.string.desktop_download_preparing
                        Downloads.Phase.Downloading -> Res.string.desktop_download_receiving
                    }
                    val bytes = java.text.NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 }
                    val detail = if (transfer.phase == Downloads.Phase.Downloading) {
                        " • ${bytes.format(transfer.bytes / 1048576.0)} MB" +
                            (transfer.total?.let { " / ${bytes.format(it / 1048576.0)} MB" } ?: "")
                    } else ""
                    Text(stringResource(phase) + detail, style = MaterialTheme.typography.bodySmall)
                    val total = transfer.total
                    if (total != null && total > 0) androidx.compose.material3.LinearProgressIndicator(
                        progress = { (transfer.bytes.toFloat() / total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth()
                    ) else androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                TextButton(onClick = { Downloads.cancel(transfer.track.id) }) { Text(stringResource(Res.string.cancel)) }
            }
        }
    }
    if (failed.isEmpty()) return
    Label(stringResource(Res.string.desktop_download_failures), "${failed.size}")
    failed.values.forEach { failure ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(failure.track.title, style = MaterialTheme.typography.titleSmall)
                Text(failure.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { Downloads.retry(failure.track.id) }) { Text(stringResource(Res.string.retry)) }
            TextButton(onClick = { Downloads.dismissFailure(failure.track.id) }) { Text(stringResource(Res.string.desktop_download_dismiss)) }
        }
    }
}
