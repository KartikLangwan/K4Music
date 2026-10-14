package com.k4music.desktop.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.k4music.desktop.res.*
import com.k4music.desktop.ui.AppDialogs
import com.k4music.desktop.ui.K4Slider
import com.k4music.desktop.ui.LocalPlayer
import com.k4music.desktop.ui.parseHexColor
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.text.DateFormat
import java.util.Date

private val accents = listOf("#7B5CFF", "#4472CF", "#E0559B", "#21B07A", "#F08A24", "#E54B4B", "#19A7B5", "#B98A2E")

@Composable
fun SettingsScreen() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
        Text(stringResource(Res.string.settings), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
        Column(Modifier.widthIn(max = 920.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AppearanceSection()
            PlaybackSection()
            EffectsSection()
            LibrarySection()
            Section(Res.string.sync) {
                Text(stringResource(Res.string.sync_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PipedAccounts()
            }
            AdvancedSection()
            AboutSection()
        }
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
        options.forEach { (value, label) -> FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label) }) }
    }
}

@Composable
private fun AppearanceSection() = Section(Res.string.appearance) {
    Label(stringResource(Res.string.desktop_theme))
    Choices(
        listOf(
            ThemeMode.System to stringResource(Res.string.desktop_theme_system),
            ThemeMode.Dark to stringResource(Res.string.color_mode_dark),
            ThemeMode.Light to stringResource(Res.string.color_mode_light)
        ),
        Settings.themeMode
    ) { Settings.themeMode = it }
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
}

@Composable
private fun PlaybackSection() = Section(Res.string.player) {
    val player = LocalPlayer.current
    val state by player.state.collectAsState()
    SwitchRow(stringResource(Res.string.smart_fallback), stringResource(Res.string.smart_fallback_description), Settings.smartFallback) { Settings.smartFallback = it }
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
    SwitchRow(stringResource(Res.string.desktop_tray_title), stringResource(Res.string.desktop_minimize_to_tray), Settings.minimizeToTray) { Settings.minimizeToTray = it }

    Label(stringResource(Res.string.desktop_speed), "%.2fx".format(Settings.speed))
    K4Slider((Settings.speed - 0.5f) / 1.5f, onChange = { Settings.speed = (0.5f + it * 1.5f).let { v -> (v * 20).toInt() / 20f } }, onCommit = { player.applyEffects() }, modifier = Modifier.fillMaxWidth())
    Label(stringResource(Res.string.desktop_pitch), "%.2fx".format(Settings.pitch))
    K4Slider((Settings.pitch - 0.5f) / 1.5f, onChange = { Settings.pitch = (0.5f + it * 1.5f).let { v -> (v * 20).toInt() / 20f } }, onCommit = { player.applyEffects() }, modifier = Modifier.fillMaxWidth())

    val sleepAt = state.sleepAt
    Label(stringResource(Res.string.sleep_timer), sleepAt?.let { stringResource(Res.string.desktop_sleep_until, DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it))) })
    Choices(
        listOf<Int?>(null, 15, 30, 45, 60, 90).map { m ->
            m to (if (m == null) stringResource(Res.string.desktop_off) else stringResource(Res.string.desktop_minutes, m))
        },
        null
    ) { player.setSleepTimer(it) }
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
        AudioEffects.bands.forEachIndexed { i, hz ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (hz >= 1000) "${hz / 1000} kHz" else "$hz Hz", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(64.dp))
                K4Slider(
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
    K4Slider(Settings.bassBoost / 100f, onChange = { Settings.bassBoost = (it * 100).toInt() }, onCommit = { player.applyEffects() }, modifier = Modifier.fillMaxWidth())
    Label(stringResource(Res.string.atd_wide_stage), "${Settings.wideStage}%")
    K4Slider(Settings.wideStage / 100f, onChange = { Settings.wideStage = (it * 100).toInt() }, onCommit = { player.applyEffects() }, modifier = Modifier.fillMaxWidth())
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
    var pendingRestore by remember { mutableStateOf(Maintenance.hasPendingRestore()) }
    val deleteAllLabel = stringResource(Res.string.delete_all_downloads)
    Label(stringResource(Res.string.settings_backup_title))
    Text(stringResource(Res.string.settings_backup_summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = {
            saveFile("k4music-${System.currentTimeMillis() / 1000}.db") { target ->
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
    if (pendingRestore) Text(stringResource(Res.string.desktop_restore_pending), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)

    Label(stringResource(Res.string.offline), stringResource(Res.string.desktop_downloads_count, downloads.size))
    OutlinedButton(enabled = downloads.isNotEmpty(), onClick = { AppDialogs.confirm(deleteAllLabel) { Downloads.deleteAll() } }) {
        Text(stringResource(Res.string.delete_all_downloads))
    }

    Label(stringResource(Res.string.desktop_music_folders))
    LocalMusic.folders().forEach { Text(it.absolutePath, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { AppDialogs.pickFolder { LocalMusic.addFolder(it); scope.launch { LocalMusic.scan() } } }) { Text(stringResource(Res.string.desktop_add_folder)) }
        OutlinedButton(onClick = { scope.launch { LocalMusic.scan() } }) { Text(stringResource(Res.string.desktop_scan)) }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { scope.launch { Db.library.clearEvents(); AppDialogs.show(getString(Res.string.desktop_done)) } }) { Text(stringResource(Res.string.desktop_clear_history)) }
        OutlinedButton(onClick = { scope.launch { Db.library.clearQueries(); AppDialogs.show(getString(Res.string.desktop_done)) } }) { Text(stringResource(Res.string.clear_search_history)) }
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
                val result = Maintenance.updateYtDlp()
                version = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Maintenance.ytDlpVersion() }
                AppDialogs.show(result.ifBlank { version })
                updating = false
            }
        }) { Text(stringResource(Res.string.desktop_update)) }
        OutlinedButton(onClick = { open(AppPaths.logs) }) { Text(stringResource(Res.string.desktop_open_logs)) }
        OutlinedButton(onClick = { open(AppPaths.data) }) { Text(stringResource(Res.string.desktop_open_data)) }
    }
}

@Composable
private fun AboutSection() = Section(Res.string.about) {
    Text("${Brand.APP_NAME} ${System.getProperty("jpackage.app-version") ?: "2.12.14"}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text(Brand.CREATOR, color = MaterialTheme.colorScheme.primary)
    Text("@${Brand.INSTAGRAM_HANDLE}", color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = { browse("https://github.com/KartikLangwan/K4Music/releases") }) { Text(stringResource(Res.string.desktop_check_updates)) }
        OutlinedButton(onClick = { browse("https://kartiklangwan.vercel.app") }) { Text("kartiklangwan.vercel.app") }
    }
    Text("GPL-3.0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
