package com.k4music.android.service

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

class AudioEffectsTest {
    private val fiveBands = listOf(60, 230, 910, 3600, 14000)

    @Test fun `presets land exactly on the classic five bands`() {
        val levels = EqualizerPreset.levelsFor(EqualizerPreset.Rock.gainsDb, fiveBands, -1500, 1500)
        assertEquals(listOf(500, 300, -100, 300, 500), levels)
    }

    @Test fun `presets interpolate onto other band layouts and clamp to the device range`() {
        val tenBands = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
        val levels = EqualizerPreset.levelsFor(EqualizerPreset.BassBoost.gainsDb, tenBands, -600, 600)
        assertEquals(10, levels.size)
        assertEquals(600, levels.first()) // +8 dB clamped to the device's +6 dB
        assertTrue(levels.all { it in -600..600 })
        assertTrue("bass boost must fall towards the treble", levels.first() > levels[5])
    }

    @Test fun `flat is flat everywhere`() {
        val levels = EqualizerPreset.levelsFor(EqualizerPreset.Flat.gainsDb, listOf(20, 1000, 20000), -1500, 1500)
        assertEquals(listOf(0, 0, 0), levels)
    }

    @Test fun `soft clip is linear below the knee and never exceeds full scale`() {
        assertEquals(1000.toShort(), softClip(1000f))
        assertEquals((-1000).toShort(), softClip(-1000f))
        assertTrue(softClip(1_000_000f) <= Short.MAX_VALUE)
        assertTrue(softClip(-1_000_000f) >= Short.MIN_VALUE)
        assertTrue("a clipped peak stays louder than the knee", softClip(40_000f) > softClip(28_000f))
    }

    private fun widen(width: Float, vararg samples: Short): ShortArray {
        val processor = StereoWidener().apply { this.width = width }
        processor.configure(AudioFormat(44_100, 2, C.ENCODING_PCM_16BIT))
        processor.flush()
        val input = ByteBuffer.allocateDirect(samples.size * 2).order(ByteOrder.nativeOrder())
        samples.forEach { input.putShort(it) }
        input.flip()
        processor.queueInput(input)
        val output = processor.output
        return ShortArray(output.remaining() / 2) { output.getShort() }
    }

    @Test fun `zero width passes audio through untouched`() {
        val samples = shortArrayOf(1200, -300, 32767, -32768)
        assertTrue(samples.contentEquals(widen(0f, *samples)))
    }

    @Test fun `widening raises the stereo difference and keeps mono centred`() {
        val (left, right) = widen(1f, 8000, 2000).let { it[0] to it[1] }
        assertTrue("side signal must grow", abs(left - right) > 6000)

        val mono = widen(1f, 5000, 5000)
        assertEquals(mono[0], mono[1])
    }

    @Test fun `mono input bypasses the widener`() {
        val processor = StereoWidener()
        processor.configure(AudioFormat(44_100, 1, C.ENCODING_PCM_16BIT))
        assertFalse(processor.isActive)
    }
}
