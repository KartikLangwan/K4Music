package com.k4music.desktop.player

import com.k4music.desktop.prefs.Reverb
import com.k4music.desktop.prefs.Settings
import java.util.Locale

/**
 * The Android audio effects as an mpv filter chain (FFmpeg filters through `lavfi`), applied live:
 * 10-band equalizer with presets, bass boost, wide stage, reverb, loudness normalization,
 * skip silence and pitch. Nothing is in the chain unless it is switched on.
 */
object AudioEffects {
    val bands = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
    const val MAX_GAIN = 12f

    /** Same preset names as the Android equalizer, shaped for ten bands. */
    val presets: Map<String, List<Float>> = linkedMapOf(
        "Flat" to listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
        "Vocal" to listOf(-2f, -2f, -1f, 1f, 3f, 4f, 3f, 1f, 0f, -1f),
        "Bass" to listOf(5f, 4f, 3f, 1f, 0f, 0f, 0f, 0f, 0f, 0f),
        "Bass Boost" to listOf(8f, 7f, 5f, 2f, 0f, -1f, -1f, 0f, 0f, 0f),
        "Treble" to listOf(0f, 0f, 0f, 0f, 0f, 1f, 3f, 5f, 6f, 6f),
        "Rock" to listOf(4f, 3f, 2f, 0f, -1f, -1f, 1f, 3f, 4f, 4f),
        "Pop" to listOf(-1f, 0f, 2f, 3f, 4f, 3f, 1f, 0f, -1f, -1f),
        "Classical" to listOf(3f, 2f, 1f, 0f, 0f, 0f, -1f, 0f, 2f, 3f),
        "Electronic" to listOf(5f, 4f, 1f, 0f, -2f, 1f, 0f, 2f, 4f, 5f)
    )

    fun gains(): List<Float> = Settings.equalizerGains.split(',').mapNotNull { it.trim().toFloatOrNull() }
        .let { if (it.size == bands.size) it else presets.getValue("Flat") }

    fun setGains(gains: List<Float>, preset: String) {
        Settings.equalizerGains = gains.joinToString(",") { "%.1f".format(Locale.ROOT, it.coerceIn(-MAX_GAIN, MAX_GAIN)) }
        Settings.equalizerPreset = preset
    }

    /** The mpv `af` value for the current settings ("" = no processing). */
    fun chain(): String {
        val lavfi = mutableListOf<String>()
        if (Settings.equalizerEnabled) gains().forEachIndexed { i, g ->
            if (g != 0f) lavfi += "equalizer=f=${bands[i]}:width_type=o:width=1:g=${fmt(g)}"
        }
        if (Settings.bassBoost > 0) lavfi += "bass=g=${fmt(Settings.bassBoost * 0.12f)}:f=100:width_type=s:width=0.6"
        if (Settings.wideStage > 0) lavfi += "extrastereo=m=${fmt(1f + Settings.wideStage / 100f * 1.5f)}"
        when (Settings.reverb) {
            Reverb.None -> Unit
            Reverb.SmallRoom -> lavfi += "aecho=0.8:0.7:40:0.25"
            Reverb.LargeHall -> lavfi += "aecho=0.8:0.85:70|130:0.3|0.2"
        }
        if (Settings.volumeNormalization) lavfi += "loudnorm=I=-16:TP=-1.5:LRA=11"
        if (Settings.skipSilence) lavfi += "silenceremove=start_periods=1:start_threshold=-50dB:stop_periods=-1:stop_duration=1.5:stop_threshold=-50dB"
        val filters = lavfi.map { "lavfi=[$it]" }.toMutableList()
        if (Settings.pitch != 1f) filters += "rubberband=pitch-scale=${fmt(Settings.pitch)}"
        return filters.joinToString(",")
    }

    private fun fmt(value: Float) = "%.2f".format(Locale.ROOT, value)
}
