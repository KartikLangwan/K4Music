package com.k4music.android.preferences

import android.media.audiofx.PresetReverb
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.k4music.android.GlobalPreferencesHolder
import com.k4music.app.R

object PlayerPreferences : GlobalPreferencesHolder() {
    val isInvincibilityEnabledProperty = boolean(false)
    var isInvincibilityEnabled by isInvincibilityEnabledProperty
    val trackLoopEnabledProperty = boolean(false)
    var trackLoopEnabled by trackLoopEnabledProperty
    val queueLoopEnabledProperty = boolean(true)
    var queueLoopEnabled by queueLoopEnabledProperty

    /** One view over the two stored loop flags, so every control shows the same mode. */
    var repeatMode: RepeatMode
        get() = when {
            trackLoopEnabled -> RepeatMode.One
            queueLoopEnabled -> RepeatMode.All
            else -> RepeatMode.Off
        }
        set(value) {
            queueLoopEnabled = value == RepeatMode.All
            trackLoopEnabled = value == RepeatMode.One
        }
    val shuffleEnabledProperty = boolean(false)
    var shuffleEnabled by shuffleEnabledProperty
    val skipSilenceProperty = boolean(false)
    var skipSilence by skipSilenceProperty
    val volumeNormalizationProperty = boolean(false)
    var volumeNormalization by volumeNormalizationProperty
    val volumeNormalizationBaseGainProperty = float(5.00f)
    var volumeNormalizationBaseGain by volumeNormalizationBaseGainProperty
    val bassBoostProperty = boolean(false)
    var bassBoost by bassBoostProperty
    val bassBoostLevelProperty = int(500)
    var bassBoostLevel by bassBoostLevelProperty
    val reverbProperty = enum(Reverb.None)
    var reverb by reverbProperty
    val resumePlaybackWhenDeviceConnectedProperty = boolean(false)
    var resumePlaybackWhenDeviceConnected by resumePlaybackWhenDeviceConnectedProperty
    val speedProperty = float(1f)
    var speed by speedProperty
    val pitchProperty = float(1f)
    var pitch by pitchProperty
    var minimumSilence by long(2_000_000L)
    var persistentQueue by boolean(true)
    var smartFallback by boolean(true)
    var stopWhenClosed by boolean(false)
    var stopOnMinimumVolume by boolean(true)

    var isShowingLyrics by boolean(false)
    var isShowingSynchronizedLyrics by boolean(false)

    var isShowingPrevButtonCollapsed by boolean(false)
    var horizontalSwipeToClose by boolean(false)
    var horizontalSwipeToRemoveItem by boolean(false)

    var seekBarStyle by enum(SeekBarStyle.Static)
    var wavySeekBarQuality by enum(WavySeekBarQuality.Great)
    var showLike by boolean(true)
    var showRemaining by boolean(false)
    var lyricsKeepScreenAwake by boolean(false)
    var lyricsShowSystemBars by boolean(true)

    var audioQuality by enum(AudioQuality.High)
    var handleAudioFocus by boolean(true)

    var pauseCache by boolean(false)

    val sponsorBlockEnabledProperty = boolean(false)
    var sponsorBlockEnabled by sponsorBlockEnabledProperty

    // Audio effects. Band levels are millibels for this device's equalizer bands.
    val equalizerEnabledProperty = boolean(false)
    var equalizerEnabled by equalizerEnabledProperty
    val equalizerLevelsProperty = json(emptyList<Int>())
    var equalizerLevels by equalizerLevelsProperty
    var equalizerPreset by string("Flat")
    var equalizerUserPresets by json(emptyMap<String, List<Int>>())
    val virtualizerModeProperty = enum(VirtualizerMode.Off)
    var virtualizerMode by virtualizerModeProperty
    val virtualizerStrengthProperty = int(700)
    var virtualizerStrength by virtualizerStrengthProperty
    val stereoWideningProperty = int(0)
    var stereoWidening by stereoWideningProperty

    enum class RepeatMode(@param:DrawableRes val icon: Int) {
        Off(R.drawable.repeat),
        All(R.drawable.repeat_on),
        One(R.drawable.repeat_one);

        val next get() = entries[(ordinal + 1) % entries.size]
    }

    enum class VirtualizerMode { Off, ThreeD, Surround }

    enum class AudioQuality(
        val saavn: String,
        val youtubeItags: List<Int>,
        val displayName: @Composable () -> String
    ) {
        DataSaver("96", listOf(250, 249, 139), { stringResource(R.string.audio_quality_data_saver) }),
        Balanced("160", listOf(140, 251), { stringResource(R.string.audio_quality_balanced) }),
        // 774: 256 kbps Opus when YouTube offers it, then 160 kbps Opus, then 128 kbps AAC.
        High("320", listOf(774, 251, 140), { stringResource(R.string.audio_quality_high) })
    }

    enum class SeekBarStyle(val displayName: @Composable () -> String) {
        Static(displayName = { stringResource(R.string.static_seek_bar_name) }),
        Wavy(displayName = { stringResource(R.string.wavy_seek_bar_name) })
    }

    @Suppress("unused")
    enum class WavySeekBarQuality(
        val quality: Float,
        val displayName: @Composable () -> String
    ) {
        Poor(quality = 50f, displayName = { stringResource(R.string.seek_bar_quality_poor) }),
        Low(quality = 25f, displayName = { stringResource(R.string.seek_bar_quality_low) }),
        Medium(quality = 15f, displayName = { stringResource(R.string.seek_bar_quality_medium) }),
        High(quality = 5f, displayName = { stringResource(R.string.seek_bar_quality_high) }),
        Great(quality = 1f, displayName = { stringResource(R.string.seek_bar_quality_great) }),
        Subpixel(
            quality = 0.5f,
            displayName = { stringResource(R.string.seek_bar_quality_subpixel) }
        )
    }

    @Suppress("unused")
    enum class Reverb(
        val preset: Short,
        val displayName: @Composable () -> String
    ) {
        None(
            preset = PresetReverb.PRESET_NONE,
            displayName = { stringResource(R.string.none) }
        ),
        SmallRoom(
            preset = PresetReverb.PRESET_SMALLROOM,
            displayName = { stringResource(R.string.reverb_small_room) }
        ),
        MediumRoom(
            preset = PresetReverb.PRESET_MEDIUMROOM,
            displayName = { stringResource(R.string.reverb_medium_room) }
        ),
        LargeRoom(
            preset = PresetReverb.PRESET_LARGEROOM,
            displayName = { stringResource(R.string.reverb_large_room) }
        ),
        MediumHall(
            preset = PresetReverb.PRESET_MEDIUMHALL,
            displayName = { stringResource(R.string.reverb_medium_hall) }
        ),
        LargeHall(
            preset = PresetReverb.PRESET_LARGEHALL,
            displayName = { stringResource(R.string.reverb_large_hall) }
        ),
        Plate(
            preset = PresetReverb.PRESET_PLATE,
            displayName = { stringResource(R.string.reverb_plate) }
        )
    }
}
