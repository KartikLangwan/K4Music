package com.k4music.android.ui.screens.settings

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.k4music.android.LocalPlayerServiceBinder
import com.k4music.app.R
import com.k4music.android.preferences.PlayerPreferences
import com.k4music.android.service.PlayerService
import com.k4music.android.ui.components.themed.SecondaryTextButton
import com.k4music.android.ui.screens.Route
import com.k4music.android.ui.screens.audioRoute
import com.k4music.core.ui.utils.isAtLeastAndroid6

@OptIn(UnstableApi::class)
@Route
@Composable
fun PlayerSettings() = with(PlayerPreferences) {
    var changed by rememberSaveable { mutableStateOf(false) }

    SettingsCategoryScreen(title = stringResource(R.string.player)) {
        SettingsGroup(title = stringResource(R.string.player)) {
            SwitchSettingsEntry(
                title = stringResource(R.string.smart_fallback),
                text = stringResource(R.string.smart_fallback_description),
                isChecked = smartFallback,
                onCheckedChange = { smartFallback = it }
            )

            SwitchSettingsEntry(
                title = stringResource(R.string.persistent_queue),
                text = stringResource(R.string.persistent_queue_description),
                isChecked = persistentQueue,
                onCheckedChange = { persistentQueue = it }
            )

            if (isAtLeastAndroid6) SwitchSettingsEntry(
                title = stringResource(R.string.resume_playback),
                text = stringResource(R.string.resume_playback_description),
                isChecked = resumePlaybackWhenDeviceConnected,
                onCheckedChange = {
                    resumePlaybackWhenDeviceConnected = it
                }
            )

            SwitchSettingsEntry(
                title = stringResource(R.string.stop_when_closed),
                text = stringResource(R.string.stop_when_closed_description),
                isChecked = stopWhenClosed,
                onCheckedChange = { stopWhenClosed = it }
            )

            SwitchSettingsEntry(
                title = stringResource(R.string.pause_minimum_volume),
                text = stringResource(R.string.pause_minimum_volume_description),
                isChecked = stopOnMinimumVolume,
                onCheckedChange = { stopOnMinimumVolume = it }
            )
        }
        SettingsGroup(title = stringResource(R.string.audio)) {
            EnumValueSelectorSettingsEntry(
                title = stringResource(R.string.audio_quality),
                selectedValue = audioQuality,
                onValueSelect = { audioQuality = it },
                valueText = { it.displayName() }
            )

            AnimatedVisibility(visible = changed) {
                RestartPlayerSettingsEntry(
                    onRestart = { changed = false }
                )
            }

            SwitchSettingsEntry(
                title = stringResource(R.string.skip_silence),
                text = stringResource(R.string.skip_silence_description),
                isChecked = skipSilence,
                onCheckedChange = {
                    skipSilence = it
                }
            )

            AnimatedVisibility(visible = skipSilence) {
                val initialValue by remember { derivedStateOf { minimumSilence.toFloat() / 1000L } }
                var newValue by remember(initialValue) { mutableFloatStateOf(initialValue) }

                Column {
                    SliderSettingsEntry(
                        title = stringResource(R.string.minimum_silence_length),
                        text = stringResource(R.string.minimum_silence_length_description),
                        state = newValue,
                        onSlide = { newValue = it },
                        onSlideComplete = {
                            minimumSilence = newValue.toLong() * 1000L
                            changed = true
                        },
                        toDisplay = { stringResource(R.string.format_ms, it.toLong()) },
                        range = 1f..2000f
                    )
                }
            }

            SwitchSettingsEntry(
                title = stringResource(R.string.loudness_normalization),
                text = stringResource(R.string.loudness_normalization_description),
                isChecked = volumeNormalization,
                onCheckedChange = { volumeNormalization = it }
            )

            AnimatedVisibility(visible = volumeNormalization) {
                var newValue by remember(volumeNormalizationBaseGain) {
                    mutableFloatStateOf(volumeNormalizationBaseGain)
                }

                SliderSettingsEntry(
                    title = stringResource(R.string.loudness_base_gain),
                    text = stringResource(R.string.loudness_base_gain_description),
                    state = newValue,
                    onSlide = { newValue = it },
                    onSlideComplete = { volumeNormalizationBaseGain = newValue },
                    toDisplay = { stringResource(R.string.format_db, "%.1f".format(it)) },
                    range = -20f..20f,
                    steps = 79,
                    showTicks = false
                )
            }

            SettingsEntry(
                title = stringResource(R.string.audio_effects),
                text = stringResource(R.string.audio_effects_settings_description),
                onClick = { audioRoute.global() }
            )

            SwitchSettingsEntry(
                title = stringResource(R.string.sponsor_block),
                text = stringResource(R.string.sponsor_block_description),
                isChecked = sponsorBlockEnabled,
                onCheckedChange = {
                    sponsorBlockEnabled = it
                }
            )

            SwitchSettingsEntry(
                title = stringResource(R.string.audio_focus),
                text = stringResource(R.string.audio_focus_description),
                isChecked = handleAudioFocus,
                onCheckedChange = {
                    handleAudioFocus = it
                    changed = true
                }
            )
        }
    }
}

@Composable
fun RestartPlayerSettingsEntry(
    onRestart: () -> Unit,
    modifier: Modifier = Modifier,
    binder: PlayerService.Binder? = LocalPlayerServiceBinder.current
) = Row(
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    modifier = modifier
) {
    SettingsDescription(
        text = stringResource(R.string.minimum_silence_length_warning),
        important = true,
        modifier = Modifier.weight(2f)
    )
    SecondaryTextButton(
        text = stringResource(R.string.restart_service),
        onClick = {
            binder?.restartForegroundOrStop()?.let { onRestart() }
        },
        modifier = Modifier
            .weight(1f)
            .padding(end = 24.dp)
    )
}
