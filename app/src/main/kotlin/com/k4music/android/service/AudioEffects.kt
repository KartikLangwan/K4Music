package com.k4music.android.service

import android.media.AudioFormat as PlatformAudioFormat
import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.runtime.Immutable
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.k4music.android.preferences.PlayerPreferences
import com.k4music.android.preferences.PlayerPreferences.VirtualizerMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.tanh

private const val TAG = "AudioEffects"
private const val PRIORITY = 1

/** What this device's audio framework actually offers; the Audio screen only shows these. */
@Immutable
data class AudioCapabilities(
    val bandFrequenciesHz: List<Int>,
    val minLevel: Int,
    val maxLevel: Int,
    val bassBoost: Boolean,
    val virtualizer: Boolean,
    val threeD: Boolean,
    val surround: Boolean,
    val reverb: Boolean
) {
    val equalizer get() = bandFrequenciesHz.isNotEmpty()
}

/**
 * Owns the platform effects for the player's audio session. Effects exist only while enabled,
 * so a disabled effect costs nothing and never fights a system-wide equalizer. All calls happen
 * on the player's (main) thread; every platform call can fail on some devices and is guarded.
 */
@OptIn(UnstableApi::class)
class AudioEffects {
    val widener = StereoWidener()

    private val mutableCapabilities = MutableStateFlow<AudioCapabilities?>(null)
    val capabilities: StateFlow<AudioCapabilities?> = mutableCapabilities.asStateFlow()

    private var sessionId = C.AUDIO_SESSION_ID_UNSET
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var reverb: PresetReverb? = null

    fun attach(audioSessionId: Int) {
        if (audioSessionId == sessionId || audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
        release()
        sessionId = audioSessionId
        mutableCapabilities.value = probe()
        apply()
    }

    fun apply() {
        if (sessionId == C.AUDIO_SESSION_ID_UNSET) return
        applyEqualizer()
        applyBassBoost()
        applyVirtualizer()
        applyReverb()
        applyWidener()
    }

    fun release() {
        equalizer = equalizer.drop()
        bassBoost = bassBoost.drop()
        virtualizer = virtualizer.drop()
        reverb = reverb.drop()
        sessionId = C.AUDIO_SESSION_ID_UNSET
    }

    private fun <T : AudioEffect> T?.drop(): T? {
        this?.let { effect -> guard("release") { effect.enabled = false; effect.release() } }
        return null
    }

    private fun <T : AudioEffect> current(existing: T?, create: () -> T): T? =
        existing ?: guard("create") { create() }

    private fun applyEqualizer() {
        if (!PlayerPreferences.equalizerEnabled) {
            equalizer = equalizer.drop()
            return
        }
        val eq = current(equalizer) { Equalizer(PRIORITY, sessionId) }.also { equalizer = it } ?: return
        guard("equalizer") {
            val (min, max) = eq.bandLevelRange.let { it[0].toInt() to it[1].toInt() }
            val levels = PlayerPreferences.equalizerLevels
            for (band in 0 until eq.numberOfBands) {
                eq.setBandLevel(band.toShort(), levels.getOrElse(band) { 0 }.coerceIn(min, max).toShort())
            }
            eq.enabled = true
        }
    }

    private fun applyBassBoost() {
        if (!PlayerPreferences.bassBoost) {
            bassBoost = bassBoost.drop()
            return
        }
        val boost = current(bassBoost) { BassBoost(PRIORITY, sessionId) }.also { bassBoost = it } ?: return
        guard("bass boost") {
            if (boost.strengthSupported) boost.setStrength(PlayerPreferences.bassBoostLevel.coerceIn(0, 1000).toShort())
            boost.enabled = true
        }
    }

    private fun applyVirtualizer() {
        val mode = PlayerPreferences.virtualizerMode
        if (mode == VirtualizerMode.Off) {
            virtualizer = virtualizer.drop()
            return
        }
        val v = current(virtualizer) { Virtualizer(PRIORITY, sessionId) }.also { virtualizer = it } ?: return
        guard("virtualizer") {
            // Devices that cannot force a mode keep their automatic one; strength still applies.
            v.forceVirtualizationMode(mode.platformMode)
            if (v.strengthSupported) v.setStrength(PlayerPreferences.virtualizerStrength.coerceIn(0, 1000).toShort())
            v.enabled = true
        }
    }

    private fun applyReverb() {
        val preset = PlayerPreferences.reverb
        if (preset == PlayerPreferences.Reverb.None) {
            reverb = reverb.drop()
            return
        }
        // Created on the player's session this is an insert effect: no aux send is needed.
        val r = current(reverb) { PresetReverb(PRIORITY, sessionId) }.also { reverb = it } ?: return
        guard("reverb") {
            r.preset = preset.preset
            r.enabled = true
        }
    }

    private fun applyWidener() {
        widener.width = PlayerPreferences.stereoWidening.coerceIn(0, 100) / 100f
    }

    private fun probe(): AudioCapabilities {
        val types = guard("query") { AudioEffect.queryEffects().map { it.type }.toSet() }.orEmpty()

        val eq = guard("equalizer probe") {
            val eq = equalizer ?: Equalizer(0, sessionId)
            try {
                Triple(
                    List(eq.numberOfBands.toInt()) { eq.getCenterFreq(it.toShort()) / 1000 },
                    eq.bandLevelRange[0].toInt(),
                    eq.bandLevelRange[1].toInt()
                )
            } finally {
                if (eq !== equalizer) eq.release()
            }
        }

        val modes = guard("virtualizer probe") {
            val v = virtualizer ?: Virtualizer(0, sessionId)
            try {
                VirtualizerMode.entries.filter { it != VirtualizerMode.Off }.associateWith {
                    runCatching { v.canVirtualize(PlatformAudioFormat.CHANNEL_OUT_STEREO, it.platformMode) }.getOrDefault(false)
                }
            } finally {
                if (v !== virtualizer) v.release()
            }
        }
        // Some implementations answer "no" for every forced mode yet virtualize fine in auto mode.
        val anyMode = modes?.values?.any { it } == true

        return AudioCapabilities(
            bandFrequenciesHz = eq?.first.orEmpty(),
            minLevel = eq?.second ?: -1500,
            maxLevel = eq?.third ?: 1500,
            bassBoost = AudioEffect.EFFECT_TYPE_BASS_BOOST in types,
            virtualizer = modes != null,
            threeD = modes != null && (!anyMode || modes[VirtualizerMode.ThreeD] == true),
            surround = modes != null && (!anyMode || modes[VirtualizerMode.Surround] == true),
            reverb = AudioEffect.EFFECT_TYPE_PRESET_REVERB in types
        )
    }

    private inline fun <T> guard(what: String, block: () -> T): T? = try {
        block()
    } catch (e: Exception) {
        Log.w(TAG, "Audio effect unavailable: $what", e)
        null
    } catch (e: UnsatisfiedLinkError) {
        Log.w(TAG, "Audio effect library missing: $what", e)
        null
    }
}

private val VirtualizerMode.platformMode
    get() = if (this == VirtualizerMode.Surround) Virtualizer.VIRTUALIZATION_MODE_TRANSAURAL
    else Virtualizer.VIRTUALIZATION_MODE_BINAURAL

/**
 * "ATD" wide stage: mid/side stereo widening done in K4 Music's own audio pipeline, so it works
 * on every device and output. The side (difference) signal is raised while the mid is trimmed
 * to hold loudness, and a soft knee keeps boosted peaks from hard clipping. Mono input bypasses.
 */
@OptIn(UnstableApi::class)
class StereoWidener : BaseAudioProcessor() {
    /** 0 = untouched, 1 = widest. Read on the audio thread. */
    @Volatile
    var width = 0f

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat =
        if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT && inputAudioFormat.channelCount == 2) inputAudioFormat
        else AudioFormat.NOT_SET

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val output = replaceOutputBuffer(size)
        val w = width
        if (w <= 0f) {
            output.put(inputBuffer)
        } else {
            val sideGain = 1f + w
            val midGain = 1f - 0.3f * w
            while (inputBuffer.remaining() >= 4) {
                val left = inputBuffer.getShort().toFloat()
                val right = inputBuffer.getShort().toFloat()
                val mid = (left + right) * 0.5f * midGain
                val side = (left - right) * 0.5f * sideGain
                output.putShort(softClip(mid + side))
                output.putShort(softClip(mid - side))
            }
            output.put(inputBuffer)
        }
        output.flip()
    }
}

private const val FULL_SCALE = 32767f
private const val KNEE = 0.85f * FULL_SCALE

/** Linear below the knee, then a tanh curve that approaches (never exceeds) full scale. */
internal fun softClip(sample: Float): Short {
    val magnitude = abs(sample)
    val shaped = if (magnitude <= KNEE) magnitude
    else KNEE + (FULL_SCALE - KNEE) * tanh((magnitude - KNEE) / (FULL_SCALE - KNEE))
    return (sign(sample) * shaped).roundToInt().coerceIn(-32768, 32767).toShort()
}

/** Built-in equalizer curves, in dB at the classic 60 Hz…14 kHz reference points. */
enum class EqualizerPreset(val gainsDb: FloatArray) {
    Flat(floatArrayOf(0f, 0f, 0f, 0f, 0f)),
    Vocal(floatArrayOf(-2f, -1f, 3f, 4f, 1f)),
    Bass(floatArrayOf(4f, 2.5f, 0f, 0f, 0f)),
    BassBoost(floatArrayOf(8f, 5f, 1f, 0f, -1f)),
    Treble(floatArrayOf(0f, 0f, 0f, 3f, 6f)),
    Rock(floatArrayOf(5f, 3f, -1f, 3f, 5f)),
    Pop(floatArrayOf(-1f, 2f, 4f, 2f, -1f)),
    Classical(floatArrayOf(4f, 2f, -1f, 2f, 4f)),
    Electronic(floatArrayOf(5f, 3f, 0f, 2f, 4f));

    companion object {
        private val referenceHz = floatArrayOf(60f, 230f, 910f, 3600f, 14000f)

        /**
         * Maps a curve onto this device's bands by interpolating on a log-frequency axis, so a
         * preset means the same thing on a 5-band and a 10-band equalizer. Returns millibels.
         */
        fun levelsFor(gainsDb: FloatArray, bandFrequenciesHz: List<Int>, minLevel: Int, maxLevel: Int): List<Int> =
            bandFrequenciesHz.map { hz ->
                val x = log10(hz.coerceAtLeast(1).toFloat())
                val xs = referenceHz.map { log10(it) }
                val db = when {
                    x <= xs.first() -> gainsDb.first()
                    x >= xs.last() -> gainsDb.last()
                    else -> {
                        val i = xs.indexOfLast { it <= x }
                        val t = (x - xs[i]) / (xs[i + 1] - xs[i])
                        gainsDb[i] + t * (gainsDb[i + 1] - gainsDb[i])
                    }
                }
                (db * 100).roundToInt().coerceIn(minLevel, maxLevel)
            }
    }
}
