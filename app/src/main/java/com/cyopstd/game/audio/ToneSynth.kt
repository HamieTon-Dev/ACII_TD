package com.cyopstd.game.audio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Generates the game's sound effects as raw PCM at build-free runtime cost.
 *
 * CyOps TD ships no audio files at all. Every effect is a short synthesized
 * blip built here and handed to a SoundPool once at startup. That keeps the APK
 * tiny, sidesteps every audio-licensing question, and gives the game a coherent
 * "terminal beep" character that sampled effects would not.
 */
object ToneSynth {

    const val SAMPLE_RATE = 22050

    /** Waveshape of a single voice. */
    enum class Wave { SINE, SQUARE, TRIANGLE, NOISE }

    /**
     * One synthesizer voice.
     *
     * @param startFreq frequency in Hz at the start of the sound
     * @param endFreq   frequency at the end (a sweep when it differs)
     * @param amplitude peak amplitude, 0..1
     * @param decay     exponential decay rate; higher is snappier
     * @param delay     seconds of silence before this voice starts; its sweep
     *                  and envelope run from that moment. Lets one effect be a
     *                  sequence (a blast, then a second blast, then a tail).
     */
    data class Voice(
        val wave: Wave,
        val startFreq: Float,
        val endFreq: Float = startFreq,
        val amplitude: Float = 0.6f,
        val decay: Float = 6f,
        val delay: Float = 0f
    )

    /**
     * Render [voices] mixed together over [durationSeconds] into a 16-bit mono
     * WAV byte array (SoundPool needs a container, not bare PCM).
     */
    fun renderWav(durationSeconds: Float, voices: List<Voice>): ByteArray {
        val frames = (SAMPLE_RATE * durationSeconds).toInt().coerceAtLeast(1)
        val pcm = ShortArray(frames)

        // Deterministic pseudo-noise so every build sounds identical.
        var noiseState = 0x2545F491u

        for (i in 0 until frames) {
            val t = i.toFloat() / SAMPLE_RATE
            val progress = i.toFloat() / frames
            var sample = 0f

            for (voice in voices) {
                // Time and sweep are the voice's own, counted from its delay.
                // A voice with no delay computes exactly what it always did, so
                // every existing sound renders byte-for-byte as before.
                val vt = t - voice.delay
                if (vt < 0f) continue
                val vProgress = if (voice.delay == 0f) progress
                else (vt / (durationSeconds - voice.delay)).coerceIn(0f, 1f)
                val vi = if (voice.delay == 0f) i else (vt * SAMPLE_RATE).toInt()
                val freq = voice.startFreq + (voice.endFreq - voice.startFreq) * vProgress
                val envelope = exp(-voice.decay * vt) * voice.amplitude
                // Short fade-in removes the click an instant attack would make.
                val attack = (vi / (SAMPLE_RATE * 0.004f)).coerceAtMost(1f)

                val raw = when (voice.wave) {
                    Wave.SINE -> sin(TWO_PI * freq * vt)
                    Wave.SQUARE -> if (sin(TWO_PI * freq * vt) >= 0f) 0.55f else -0.55f
                    Wave.TRIANGLE -> {
                        val phase = (freq * vt) % 1f
                        (if (phase < 0.5f) 4f * phase - 1f else 3f - 4f * phase)
                    }
                    Wave.NOISE -> {
                        noiseState = noiseState xor (noiseState shl 13)
                        noiseState = noiseState xor (noiseState shr 17)
                        noiseState = noiseState xor (noiseState shl 5)
                        ((noiseState.toInt() and 0xFFFF) / 32768f) - 1f
                    }
                }
                sample += raw * envelope * attack
            }

            val clipped = sample.coerceIn(-1f, 1f)
            pcm[i] = (clipped * Short.MAX_VALUE * 0.85f).toInt().toShort()
        }

        return wrapInWavContainer(pcm)
    }

    private fun wrapInWavContainer(pcm: ShortArray): ByteArray {
        val dataSize = pcm.size * 2
        val out = ByteArray(44 + dataSize)
        var p = 0

        fun ascii(value: String) {
            for (c in value) out[p++] = c.code.toByte()
        }

        fun int32(value: Int) {
            out[p++] = (value and 0xFF).toByte()
            out[p++] = ((value shr 8) and 0xFF).toByte()
            out[p++] = ((value shr 16) and 0xFF).toByte()
            out[p++] = ((value shr 24) and 0xFF).toByte()
        }

        fun int16(value: Int) {
            out[p++] = (value and 0xFF).toByte()
            out[p++] = ((value shr 8) and 0xFF).toByte()
        }

        ascii("RIFF"); int32(36 + dataSize); ascii("WAVE")
        ascii("fmt "); int32(16)
        int16(1)                       // PCM
        int16(1)                       // mono
        int32(SAMPLE_RATE)
        int32(SAMPLE_RATE * 2)         // byte rate
        int16(2)                       // block align
        int16(16)                      // bits per sample
        ascii("data"); int32(dataSize)

        for (sample in pcm) {
            out[p++] = (sample.toInt() and 0xFF).toByte()
            out[p++] = ((sample.toInt() shr 8) and 0xFF).toByte()
        }
        return out
    }

    private const val TWO_PI = (2.0 * PI).toFloat()
}
