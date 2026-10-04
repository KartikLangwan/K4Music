package com.k4music.android.k4

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object K4AudioEffects {
    private const val TAG = "K4Audio"

    private var equalizer: Equalizer? = null
    private var reverb: PresetReverb? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var currentSessionId: Int = 0

    // State flows for UI
    private val _isReverbEnabled = MutableStateFlow(false)
    val isReverbEnabled: StateFlow<Boolean> = _isReverbEnabled.asStateFlow()

    private val _currentReverbPreset = MutableStateFlow("Off")
    val currentReverbPreset: StateFlow<String> = _currentReverbPreset.asStateFlow()

    private val _isEqEnabled = MutableStateFlow(true)
    val isEqEnabled: StateFlow<Boolean> = _isEqEnabled.asStateFlow()

    private val _currentEqPreset = MutableStateFlow("Balanced")
    val currentEqPreset: StateFlow<String> = _currentEqPreset.asStateFlow()

    private val _bassStrength = MutableStateFlow(300)
    val bassStrength: StateFlow<Int> = _bassStrength.asStateFlow()

    private val _virtualizerStrength = MutableStateFlow(250)
    val virtualizerStrength: StateFlow<Int> = _virtualizerStrength.asStateFlow()

    val reverbPresets = listOf(
        "Off",
        "Small Room",
        "Medium Room",
        "Large Room",
        "Medium Hall",
        "Large Hall",
        "Plate",
        "Studio Space"
    )

    val eqPresets = listOf(
        "Balanced",
        "Bass Heavy",
        "Rock",
        "Pop",
        "Jazz",
        "Electronic",
        "Hip Hop",
        "Vocal Focus",
        "Acoustic"
    )

    fun attach(sessionId: Int) {
        if (currentSessionId == sessionId && equalizer != null) return
        release()
        currentSessionId = sessionId
        try {
            // Priority 0, audioSession
            equalizer = Equalizer(0, sessionId).apply {
                enabled = _isEqEnabled.value
            }
        } catch (e: Exception) {
            Log.w(TAG, "Equalizer init error: ${e.message}")
        }

        try {
            reverb = PresetReverb(0, sessionId).apply {
                preset = mapReverbPresetToShort(_currentReverbPreset.value)
                enabled = _isReverbEnabled.value
            }
        } catch (e: Exception) {
            Log.w(TAG, "Reverb init error: ${e.message}")
        }

        try {
            bassBoost = BassBoost(0, sessionId).apply {
                enabled = true
                if (strengthSupported) setStrength(_bassStrength.value.toShort())
            }
        } catch (e: Exception) {
            Log.w(TAG, "BassBoost init error: ${e.message}")
        }

        try {
            virtualizer = Virtualizer(0, sessionId).apply {
                enabled = true
                if (strengthSupported) setStrength(_virtualizerStrength.value.toShort())
            }
        } catch (e: Exception) {
            Log.w(TAG, "Virtualizer init error: ${e.message}")
        }
    }

    fun setReverbEnabled(enabled: Boolean) {
        _isReverbEnabled.value = enabled
        try {
            reverb?.enabled = enabled
            if (enabled && _currentReverbPreset.value == "Off") {
                setReverbPreset("Medium Room")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Set reverb enabled failed: ${e.message}")
        }
    }

    fun setReverbPreset(name: String) {
        _currentReverbPreset.value = name
        val presetVal = mapReverbPresetToShort(name)
        val enabled = name != "Off"
        _isReverbEnabled.value = enabled
        try {
            reverb?.preset = presetVal
            reverb?.enabled = enabled
        } catch (e: Exception) {
            Log.w(TAG, "Set reverb preset failed: ${e.message}")
        }
    }

    private fun mapReverbPresetToShort(name: String): Short {
        return when (name.lowercase()) {
            "small room" -> PresetReverb.PRESET_SMALLROOM
            "medium room" -> PresetReverb.PRESET_MEDIUMROOM
            "large room" -> PresetReverb.PRESET_LARGEROOM
            "medium hall" -> PresetReverb.PRESET_MEDIUMHALL
            "large hall" -> PresetReverb.PRESET_LARGEHALL
            "plate" -> PresetReverb.PRESET_PLATE
            "studio space" -> PresetReverb.PRESET_MEDIUMROOM
            else -> PresetReverb.PRESET_NONE
        }
    }

    fun setEqEnabled(enabled: Boolean) {
        _isEqEnabled.value = enabled
        try {
            equalizer?.enabled = enabled
        } catch (e: Exception) {
            Log.w(TAG, "Set eq enabled failed: ${e.message}")
        }
    }

    fun setEqPreset(name: String) {
        _currentEqPreset.value = name
        // Apply preset curve to EQ bands if available
        try {
            val eq = equalizer ?: return
            val bands = eq.numberOfBands.toInt()
            val (min, max) = eq.bandLevelRange[0] to eq.bandLevelRange[1]
            val curve = when (name.lowercase()) {
                "bass heavy" -> floatArrayOf(0.7f, 0.4f, 0.0f, -0.2f, -0.3f)
                "rock" -> floatArrayOf(0.5f, 0.2f, -0.1f, 0.3f, 0.6f)
                "pop" -> floatArrayOf(-0.1f, 0.3f, 0.5f, 0.3f, -0.1f)
                "jazz" -> floatArrayOf(0.3f, 0.1f, 0.1f, 0.2f, 0.3f)
                "electronic" -> floatArrayOf(0.6f, 0.3f, 0.0f, 0.2f, 0.5f)
                "hip hop" -> floatArrayOf(0.8f, 0.5f, -0.1f, 0.1f, 0.3f)
                "vocal focus" -> floatArrayOf(-0.3f, 0.1f, 0.6f, 0.4f, -0.2f)
                "acoustic" -> floatArrayOf(0.3f, 0.2f, 0.1f, 0.2f, 0.4f)
                else -> floatArrayOf(0f, 0f, 0f, 0f, 0f)
            }
            for (i in 0 until minOf(bands, curve.size)) {
                val factor = curve[i]
                val level = if (factor >= 0) (factor * max).toInt() else (-factor * min).toInt()
                eq.setBandLevel(i.toShort(), level.coerceIn(min.toInt(), max.toInt()).toShort())
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed applying EQ preset: ${e.message}")
        }
    }

    fun setBassBoost(level: Int) {
        _bassStrength.value = level
        try {
            bassBoost?.setStrength(level.coerceIn(0, 1000).toShort())
        } catch (_: Exception) {}
    }

    fun setVirtualizer(level: Int) {
        _virtualizerStrength.value = level
        try {
            virtualizer?.setStrength(level.coerceIn(0, 1000).toShort())
        } catch (_: Exception) {}
    }

    fun release() {
        try { equalizer?.release() } catch (_: Exception) {}
        try { reverb?.release() } catch (_: Exception) {}
        try { bassBoost?.release() } catch (_: Exception) {}
        try { virtualizer?.release() } catch (_: Exception) {}
        equalizer = null
        reverb = null
        bassBoost = null
        virtualizer = null
    }
}
