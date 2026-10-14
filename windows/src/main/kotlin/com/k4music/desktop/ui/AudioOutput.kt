package com.k4music.desktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.k4music.desktop.player.AudioDevice
import com.k4music.desktop.player.PlayerController
import com.k4music.desktop.prefs.Settings
import com.k4music.desktop.res.Res
import com.k4music.desktop.res.desktop_audio_output
import com.k4music.desktop.res.desktop_output_default
import com.k4music.desktop.res.desktop_output_default_hint
import com.k4music.desktop.res.desktop_output_hint
import com.k4music.desktop.res.desktop_refresh_devices
import com.k4music.desktop.res.speaker_output
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

/** Windows' sound outputs, read fresh (a Bluetooth headset may have just connected). */
private suspend fun outputs(player: PlayerController): List<AudioDevice> = withContext(Dispatchers.IO) {
    player.audioDevices().ifEmpty { listOf(AudioDevice("auto", "")) }
}

@Composable
private fun AudioDevice.label() = if (name == "auto") stringResource(Res.string.desktop_output_default) else description

/** Player bar: choose where the music plays (speakers, headphones, Bluetooth…). */
@Composable
fun AudioOutputButton(player: PlayerController) {
    var open by remember { mutableStateOf(false) }
    var devices by remember { mutableStateOf<List<AudioDevice>>(emptyList()) }
    LaunchedEffect(open) { if (open) devices = outputs(player) }
    val current = Settings.audioDevice
    val currentLabel = devices.firstOrNull { it.name == current }?.label() ?: stringResource(Res.string.desktop_output_default)
    Box {
        ToggleIcon(
            Res.drawable.speaker_output,
            "${stringResource(Res.string.desktop_audio_output)}: $currentLabel",
            active = current != "auto"
        ) { open = true }
        DropdownMenu(open, onDismissRequest = { open = false }) {
            Text(
                stringResource(Res.string.desktop_audio_output),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            devices.forEach { device ->
                DropdownMenuItem(
                    text = { Text(device.label(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { RadioButton(selected = device.name == current, onClick = null) },
                    onClick = { player.setAudioDevice(device.name); open = false }
                )
            }
        }
    }
}

/** Settings → Player: the same choice as a list. */
@Composable
fun AudioOutputSettings(player: PlayerController) {
    var devices by remember { mutableStateOf<List<AudioDevice>>(emptyList()) }
    var refresh by remember { mutableStateOf(0) }
    LaunchedEffect(refresh) { devices = outputs(player) }
    val current = Settings.audioDevice
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(Res.string.desktop_audio_output), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(stringResource(Res.string.desktop_output_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        devices.forEach { device ->
            Row(
                Modifier.fillMaxWidth()
                    .selectable(selected = device.name == current, role = Role.RadioButton) { player.setAudioDevice(device.name) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RadioButton(selected = device.name == current, onClick = null)
                Column {
                    Text(device.label(), style = MaterialTheme.typography.bodyMedium)
                    if (device.name == "auto") Text(
                        stringResource(Res.string.desktop_output_default_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        OutlinedButton(onClick = { refresh++ }, modifier = Modifier.padding(top = 4.dp)) { Text(stringResource(Res.string.desktop_refresh_devices)) }
    }
}
