package com.cyopstd.game

import com.cyopstd.game.audio.ChiptuneComposer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The music is generated code, so it gets tested like code.
 *
 * These are not "does it sound nice" tests — nothing can assert that. They are
 * the things that would actually ruin the track on a player's device: a clipped
 * or silent render, a click at the loop seam, a file the decoder rejects, a
 * track short enough to notice repeating, or a mix so hot it distorts.
 */
class ChiptuneComposerTest {

    private val rendered: ByteArray by lazy {
        ByteArrayOutputStream(44 + ChiptuneComposer.totalFrames * 2).also {
            ChiptuneComposer.writeWav(it)
        }.toByteArray()
    }

    private fun samples(): FloatArray {
        val frames = ChiptuneComposer.totalFrames
        val out = FloatArray(frames)
        for (i in 0 until frames) {
            val lo = rendered[44 + i * 2].toInt() and 0xFF
            val hi = rendered[44 + i * 2 + 1].toInt()
            out[i] = ((hi shl 8) or lo).toShort() / 32768f
        }
        return out
    }

    @Test
    fun `the track is long enough that nobody hears it as a loop`() {
        // The complaint that prompted this was a two-second loop. Three and a
        // half minutes of developing arrangement is a different category.
        assertTrue(
            "track is ${ChiptuneComposer.trackSeconds}s",
            ChiptuneComposer.trackSeconds > 180f
        )
    }

    @Test
    fun `the render is a valid 16-bit mono WAV of the declared length`() {
        assertEquals("RIFF", String(rendered, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(rendered, 8, 4, Charsets.US_ASCII))
        assertEquals("data", String(rendered, 36, 4, Charsets.US_ASCII))

        fun int32(at: Int): Int =
            (rendered[at].toInt() and 0xFF) or
                ((rendered[at + 1].toInt() and 0xFF) shl 8) or
                ((rendered[at + 2].toInt() and 0xFF) shl 16) or
                ((rendered[at + 3].toInt() and 0xFF) shl 24)

        fun int16(at: Int): Int =
            (rendered[at].toInt() and 0xFF) or ((rendered[at + 1].toInt() and 0xFF) shl 8)

        assertEquals(1, int16(20))                              // PCM
        assertEquals(1, int16(22))                              // mono
        assertEquals(ChiptuneComposer.SAMPLE_RATE, int32(24))
        assertEquals(16, int16(34))                             // bits per sample
        assertEquals(ChiptuneComposer.totalFrames * 2, int32(40))
        assertEquals(44 + ChiptuneComposer.totalFrames * 2, rendered.size)
    }

    @Test
    fun `the track plays at a sane level without clipping`() {
        val samples = samples()
        var peak = 0f
        var energy = 0.0
        for (sample in samples) {
            if (abs(sample) > peak) peak = abs(sample)
            energy += sample.toDouble() * sample
        }
        val rms = sqrt(energy / samples.size)

        // Loud enough to hear under the effects, quiet enough not to distort.
        assertTrue("peak $peak", peak in 0.35f..0.98f)
        assertTrue("rms $rms", rms in 0.04..0.25)

        // Hard clipping would park a long run of samples at the rail; the soft
        // saturation in the master chain must make that impossible.
        var railed = 0
        for (sample in samples) if (abs(sample) > 0.995f) railed++
        assertTrue("$railed samples at the rail", railed == 0)
    }

    @Test
    fun `both ends fade through silence so the loop seam cannot click`() {
        val samples = samples()
        // MediaPlayer loops back from the last sample to the first. If either
        // end carried a live waveform, that jump would be an audible tick.
        assertTrue(abs(samples.first()) < 0.01f)
        assertTrue(abs(samples.last()) < 0.01f)
        assertTrue(abs(samples.last() - samples.first()) < 0.02f)
    }

    @Test
    fun `the arrangement actually develops instead of repeating`() {
        val samples = samples()
        val section = samples.size / 8
        val levels = (0 until 8).map { index ->
            var energy = 0.0
            for (i in index * section until (index + 1) * section) {
                energy += samples[i].toDouble() * samples[i]
            }
            sqrt(energy / section)
        }

        // Every section makes sound...
        for ((index, level) in levels.withIndex()) {
            assertTrue("section $index is silent", level > 0.01)
        }
        // ...but the busy middle is clearly fuller than the intro and outro,
        // which is what stops the track reading as one bar on repeat.
        val intro = levels.first()
        val busiest = levels.max()
        assertTrue("intro $intro vs busiest $busiest", busiest > intro * 1.4)
    }

    @Test
    fun `no section boundary leaves a gap or a step`() {
        val samples = samples()
        val section = samples.size / 8
        // Rendering is blocked per section; a reset filter or a dropped note
        // tail would show up as a discontinuity exactly on the boundary.
        for (index in 1 until 8) {
            val at = index * section
            val step = abs(samples[at] - samples[at - 1])
            assertTrue("step of $step at section $index", step < 0.12f)
        }
    }

    @Test
    fun `the render is deterministic`() {
        val second = ByteArrayOutputStream().also { ChiptuneComposer.writeWav(it) }.toByteArray()
        assertTrue("two renders differ", rendered.contentEquals(second))
    }
}
