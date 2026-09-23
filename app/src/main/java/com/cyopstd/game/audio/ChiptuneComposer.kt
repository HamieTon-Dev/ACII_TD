package com.cyopstd.game.audio

import java.io.OutputStream
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/**
 * Writes the game's background music: a three-and-a-half minute lo-fi chiptune,
 * composed and synthesized from scratch every time the cache is cold.
 *
 * CyOps TD ships no audio files, so "music" has to be *generated*. The obvious
 * cheap answer — a two-second drone on a SoundPool loop — is exactly the thing
 * that grates after ten minutes of play. So this composes an actual arrangement
 * instead: eight sections of eight bars, each with its own chord progression,
 * melodic density and drum intensity, so the track develops rather than
 * repeats. The loop point is over three minutes out, and both ends fade through
 * near-silence, so the seam is inaudible.
 *
 * The lo-fi character is deliberate and comes from four things: a 16 kHz sample
 * rate, a gentle two-pole low-pass that shaves the harsh square-wave harmonics,
 * a slow tape-wow pitch drift, and a very quiet noise floor. The 8-bit
 * character comes from the voices themselves — pulse leads, pulse pads and a
 * triangle bass, the classic NES trio.
 *
 * Everything here is pure Kotlin with no Android dependency and no randomness
 * beyond a seeded generator, so the track is byte-identical on every device and
 * can be rendered and inspected by a JVM unit test.
 */
object ChiptuneComposer {

    /** Deliberately low: it is half the cost of 32 kHz and sounds warmer. */
    const val SAMPLE_RATE = 16_000

    /** Bump this when the arrangement changes so cached renders are replaced. */
    const val TRACK_VERSION = 1

    private const val BPM = 72f
    private const val BEATS_PER_BAR = 4
    private const val BARS_PER_SECTION = 8

    private val SECONDS_PER_BEAT = 60f / BPM
    private val SECONDS_PER_BAR = SECONDS_PER_BEAT * BEATS_PER_BAR

    /** Rendered in chunks of one section so peak memory stays near 2 MB. */
    private val FRAMES_PER_SECTION = (SECONDS_PER_BAR * BARS_PER_SECTION * SAMPLE_RATE).toInt()

    private const val TWO_PI = (2.0 * PI).toFloat()

    // ----------------------------------------------------------- the music

    /** Which oscillator a note is played on. */
    private enum class Voice { LEAD, PAD, BASS, KICK, HAT, RIM }

    private class Note(
        val startFrame: Int,
        val frames: Int,
        val freq: Float,
        val endFreq: Float,
        val voice: Voice,
        val amp: Float,
        val attack: Float,
        val release: Float,
        val decay: Float,
        val vibrato: Float
    )

    /**
     * One chord: a bass root plus a four-note voicing, all in MIDI numbers.
     * Everything is diatonic to A minor, which is why the pentatonic melody can
     * wander freely without ever landing on a wrong note.
     */
    private class Chord(val bass: Int, val voicing: IntArray)

    private val CHORDS = arrayOf(
        Chord(45, intArrayOf(57, 60, 64, 67)),  // Am7
        Chord(50, intArrayOf(57, 62, 65, 69)),  // Dm7
        Chord(43, intArrayOf(55, 59, 62, 67)),  // G
        Chord(48, intArrayOf(55, 60, 64, 67)),  // Cmaj7
        Chord(41, intArrayOf(57, 60, 65, 69)),  // Fmaj7
        Chord(40, intArrayOf(55, 59, 62, 64))   // Em7
    )

    /** Four-chord progressions, two bars per chord. */
    private val PROGRESSIONS = arrayOf(
        intArrayOf(0, 4, 3, 2),   // i  - VI - III - VII
        intArrayOf(1, 2, 3, 0),   // iv - VII - III - i
        intArrayOf(0, 5, 4, 2),   // i  - v  - VI  - VII
        intArrayOf(4, 3, 1, 0)    // VI - III - iv - i
    )

    /** A minor pentatonic, the melody's whole vocabulary. */
    private val PENTATONIC = intArrayOf(69, 72, 74, 76, 79, 81)

    /**
     * How each eight-bar section is played. This is the arrangement, and it is
     * the difference between a track and a loop: the intro is pad and bass
     * alone, the melody arrives in section two, section five drops out to a
     * breakdown, and the outro thins back down to the intro's chords so the
     * track folds into its own beginning.
     */
    private class Section(
        val progression: Int,
        val melodyDensity: Float,
        /** 0 = none, 1 = hats only, 2 = full kit. */
        val drums: Int,
        val melodyOctave: Int,
        val padGain: Float,
        val bassGain: Float = 1f,
        /** The lazy second bass note. Dropping it is what thins a section out. */
        val bassPickup: Boolean = true
    )

    private val ARRANGEMENT = arrayOf(
        Section(0, melodyDensity = 0.00f, drums = 0, melodyOctave = 0, padGain = 0.62f,
            bassGain = 0.62f, bassPickup = false),
        Section(0, melodyDensity = 0.30f, drums = 1, melodyOctave = 0, padGain = 0.90f,
            bassGain = 0.88f),
        Section(1, melodyDensity = 0.42f, drums = 2, melodyOctave = 0, padGain = 1.00f),
        Section(2, melodyDensity = 0.34f, drums = 2, melodyOctave = 1, padGain = 1.00f),
        Section(3, melodyDensity = 0.00f, drums = 1, melodyOctave = 0, padGain = 0.76f,
            bassGain = 0.70f, bassPickup = false),
        Section(1, melodyDensity = 0.44f, drums = 2, melodyOctave = 1, padGain = 1.00f),
        Section(2, melodyDensity = 0.48f, drums = 2, melodyOctave = 0, padGain = 1.00f),
        Section(0, melodyDensity = 0.20f, drums = 1, melodyOctave = 0, padGain = 0.70f,
            bassGain = 0.72f, bassPickup = false)
    )

    /** Total length of the rendered track in seconds. */
    val trackSeconds: Float get() = ARRANGEMENT.size * BARS_PER_SECTION * SECONDS_PER_BAR

    val totalFrames: Int get() = ARRANGEMENT.size * FRAMES_PER_SECTION

    // --------------------------------------------------------------- output

    /**
     * Render the whole track as a 16-bit mono WAV onto [out].
     *
     * Written a section at a time so nothing bigger than a couple of megabytes
     * is ever live, which matters on the low-end devices this game targets.
     */
    fun writeWav(out: OutputStream) {
        val frames = totalFrames
        out.write(wavHeader(frames))

        // The noise generator is object state, so a second render would start
        // where the first left off and produce a different track. Reset it.
        noiseState = NOISE_SEED

        val notes = compose()
        val mix = FloatArray(FRAMES_PER_SECTION)
        val bytes = ByteArray(FRAMES_PER_SECTION * 2)
        val master = Master()

        for (section in ARRANGEMENT.indices) {
            java.util.Arrays.fill(mix, 0f)
            val blockStart = section * FRAMES_PER_SECTION
            val blockEnd = blockStart + FRAMES_PER_SECTION

            for (note in notes) {
                if (note.startFrame >= blockEnd) break
                if (note.startFrame + note.frames <= blockStart) continue
                renderNote(note, mix, blockStart)
            }

            master.finish(mix, blockStart, frames, bytes)
            out.write(bytes)
        }
        out.flush()
    }

    // ---------------------------------------------------------- composition

    private fun compose(): List<Note> {
        val notes = ArrayList<Note>(2048)
        val rng = Rng(0x5EED_1F0Fu)
        var scaleIndex = 2

        for (s in ARRANGEMENT.indices) {
            val section = ARRANGEMENT[s]
            val progression = PROGRESSIONS[section.progression]

            for (bar in 0 until BARS_PER_SECTION) {
                val chord = CHORDS[progression[(bar / 2) % progression.size]]
                val barTime = (s * BARS_PER_SECTION + bar) * SECONDS_PER_BAR

                if (bar % 2 == 0) addPad(notes, chord, barTime, section.padGain)
                addBass(notes, chord, barTime, bar, section)
                if (section.drums > 0) addDrums(notes, barTime, bar, section.drums, rng)
                if (section.melodyDensity > 0f) {
                    scaleIndex = addMelody(notes, chord, barTime, section, scaleIndex, rng)
                }
            }
        }

        notes.sortBy { it.startFrame }
        return notes
    }

    /**
     * The pad: the chord's third, fifth and seventh on narrow pulses, with the
     * third doubled a few cents sharp. That detuning is the whole warmth of the
     * track — two pulses beating slowly against each other read as "chorus"
     * rather than as two separate square waves.
     */
    private fun addPad(notes: MutableList<Note>, chord: Chord, time: Float, gain: Float) {
        val duration = SECONDS_PER_BAR * 2f
        for (index in 1..3) {
            notes += note(
                time, duration, midi(chord.voicing[index].toFloat()), Voice.PAD,
                amp = 0.085f * gain, attack = 0.25f, release = 0.55f
            )
        }
        notes += note(
            time, duration, midi(chord.voicing[1] + 0.07f), Voice.PAD,
            amp = 0.06f * gain, attack = 0.35f, release = 0.55f
        )
    }

    /** Triangle bass, root only, with a lazy pickup into the back half of the bar. */
    private fun addBass(
        notes: MutableList<Note>,
        chord: Chord,
        time: Float,
        bar: Int,
        section: Section
    ) {
        // Written where a phone speaker can actually reproduce it (82-147 Hz).
        // An octave lower reads as a proper sub-bass on headphones and as
        // silence on the device most people will play this on.
        val root = midi(chord.bass.toFloat())
        notes += note(
            time, SECONDS_PER_BEAT * 1.6f, root, Voice.BASS,
            amp = 0.34f * section.bassGain, attack = 0.012f, release = 0.10f, decay = 0.55f
        )
        if (!section.bassPickup) return
        val offset = if (bar % 4 == 3) 2.0f else 2.5f
        notes += note(
            time + SECONDS_PER_BEAT * offset, SECONDS_PER_BEAT * 1.2f, root, Voice.BASS,
            amp = 0.26f * section.bassGain, attack = 0.012f, release = 0.10f, decay = 0.7f
        )
    }

    private fun addDrums(
        notes: MutableList<Note>,
        time: Float,
        bar: Int,
        intensity: Int,
        rng: Rng
    ) {
        if (intensity >= 2) {
            notes += kick(time)
            notes += kick(time + SECONDS_PER_BEAT * 2.5f, 0.82f)
            notes += note(
                time + SECONDS_PER_BEAT * 2f, 0.16f, 0f, Voice.RIM,
                amp = 0.17f, attack = 0.001f, release = 0.02f, decay = 26f
            )
            if (bar % 8 == 7) {
                notes += note(
                    time + SECONDS_PER_BEAT * 3.5f, 0.14f, 0f, Voice.RIM,
                    amp = 0.13f, attack = 0.001f, release = 0.02f, decay = 30f
                )
            }
        }

        val hatSlots = if (intensity >= 2) intArrayOf(1, 3, 5, 7) else intArrayOf(2, 6)
        for (slot in hatSlots) {
            if (intensity >= 2 && rng.float() < 0.12f) continue
            notes += note(
                time + SECONDS_PER_BEAT * 0.5f * slot, 0.06f, 0f, Voice.HAT,
                amp = if (intensity >= 2) 0.075f else 0.05f,
                attack = 0.001f, release = 0.01f, decay = 70f
            )
        }
    }

    private fun kick(time: Float, gain: Float = 1f): Note = Note(
        startFrame = frameOf(time),
        frames = frameOf(0.18f),
        freq = 118f,
        endFreq = 46f,
        voice = Voice.KICK,
        amp = 0.52f * gain,
        attack = 0.002f,
        release = 0.03f,
        decay = 17f,
        vibrato = 0f
    )

    /**
     * The melody walks the pentatonic scale rather than jumping around it:
     * strong beats snap to a chord tone, weak beats step at most two scale
     * degrees from the last note. That constraint is what keeps a generated
     * line sounding like a tune instead of like a random-note generator.
     */
    private fun addMelody(
        notes: MutableList<Note>,
        chord: Chord,
        barTime: Float,
        section: Section,
        startIndex: Int,
        rng: Rng
    ): Int {
        var index = startIndex
        var slot = 0
        while (slot < 8) {
            val strong = slot == 0 || slot == 4
            val chance = section.melodyDensity * if (strong) 1.7f else 1f
            if (rng.float() > chance) {
                slot++
                continue
            }

            index = if (strong) {
                nearestScaleDegree(chord.voicing[rng.int(3)] % 12, index)
            } else {
                (index + rng.int(5) - 2).coerceIn(0, PENTATONIC.size - 1)
            }

            val lengthSlots = when {
                strong && rng.float() < 0.45f -> 3
                rng.float() < 0.30f -> 2
                else -> 1
            }
            val duration = SECONDS_PER_BEAT * 0.5f * lengthSlots * 0.92f
            val pitch = PENTATONIC[index] + section.melodyOctave * 12

            notes += note(
                barTime + SECONDS_PER_BEAT * 0.5f * slot, duration, midi(pitch.toFloat()),
                Voice.LEAD,
                amp = (if (section.melodyOctave > 0) 0.10f else 0.135f) * if (strong) 1f else 0.82f,
                attack = 0.014f, release = 0.06f, decay = 1.1f, vibrato = 0.005f
            )
            slot += lengthSlots
        }
        return index
    }

    /** Pull [pitchClass] into the pentatonic and pick the octave nearest [near]. */
    private fun nearestScaleDegree(pitchClass: Int, near: Int): Int {
        var best = near
        var bestDistance = Int.MAX_VALUE
        for (i in PENTATONIC.indices) {
            if (PENTATONIC[i] % 12 != pitchClass) continue
            val distance = kotlin.math.abs(i - near)
            if (distance < bestDistance) {
                bestDistance = distance
                best = i
            }
        }
        // Not a pentatonic tone (the 7th of a maj7, say): step gently instead.
        return if (bestDistance == Int.MAX_VALUE) {
            (near + if (near < 3) 1 else -1).coerceIn(0, PENTATONIC.size - 1)
        } else {
            best
        }
    }

    private fun note(
        time: Float,
        duration: Float,
        freq: Float,
        voice: Voice,
        amp: Float,
        attack: Float,
        release: Float,
        decay: Float = 0f,
        vibrato: Float = 0f
    ): Note = Note(
        startFrame = frameOf(time),
        frames = frameOf(duration),
        freq = freq,
        endFreq = freq,
        voice = voice,
        amp = amp,
        attack = attack,
        release = release,
        decay = decay,
        vibrato = vibrato
    )

    private fun frameOf(seconds: Float): Int = (seconds * SAMPLE_RATE).toInt().coerceAtLeast(1)

    private fun midi(note: Float): Float = 440f * 2f.pow((note - 69f) / 12f)

    // ------------------------------------------------------------ synthesis

    private const val NOISE_SEED: UInt = 0x2545F491u

    private var noiseState: UInt = NOISE_SEED

    private fun noise(): Float {
        noiseState = noiseState xor (noiseState shl 13)
        noiseState = noiseState xor (noiseState shr 17)
        noiseState = noiseState xor (noiseState shl 5)
        return ((noiseState.toInt() and 0xFFFF) / 32768f) - 1f
    }

    private fun renderNote(note: Note, mix: FloatArray, blockStart: Int) {
        val from = maxOf(note.startFrame, blockStart)
        val to = minOf(note.startFrame + note.frames, blockStart + mix.size)
        if (to <= from) return

        val duration = note.frames.toFloat() / SAMPLE_RATE
        // A note that began in an earlier block resumes with the right phase.
        val skipped = (from - note.startFrame).toFloat() / SAMPLE_RATE
        var phase = (note.freq * skipped) % 1f

        for (frame in from until to) {
            val t = (frame - note.startFrame).toFloat() / SAMPLE_RATE
            val absolute = frame.toFloat() / SAMPLE_RATE

            var freq = note.freq
            if (note.endFreq != note.freq) {
                val sweep = (t / duration).coerceIn(0f, 1f)
                freq = note.freq + (note.endFreq - note.freq) * sweep
            }
            if (note.vibrato > 0f && t > 0.12f) {
                freq *= 1f + note.vibrato * sin(TWO_PI * 5.2f * (t - 0.12f))
            }
            // Tape wow: two slow, mutually prime drifts. Barely a fifth of a
            // semitone, but it is what stops the pads sounding like a computer.
            if (note.voice != Voice.HAT && note.voice != Voice.RIM) {
                freq *= 1f +
                    0.0024f * sin(TWO_PI * 0.13f * absolute) +
                    0.0012f * sin(TWO_PI * 0.37f * absolute + 1.7f)
            }

            phase += freq / SAMPLE_RATE
            if (phase >= 1f) phase -= phase.toInt().toFloat()

            val raw = when (note.voice) {
                Voice.LEAD -> if (phase < 0.5f) 1f else -1f
                Voice.PAD -> if (phase < 0.25f) 1f else -1f
                Voice.BASS -> if (phase < 0.5f) 4f * phase - 1f else 3f - 4f * phase
                Voice.KICK -> sin(TWO_PI * phase)
                Voice.HAT -> noise()
                Voice.RIM -> noise() * 0.7f + sin(t * 1180f) * 0.3f
            }

            mix[frame - blockStart] += raw * note.amp * envelope(note, t, duration)
        }
    }

    private fun envelope(note: Note, t: Float, duration: Float): Float {
        val attack = if (note.attack <= 0f) 1f else (t / note.attack).coerceAtMost(1f)
        val release = if (note.release > 0f && t > duration - note.release) {
            ((duration - t) / note.release).coerceIn(0f, 1f)
        } else {
            1f
        }
        val decay = if (note.decay > 0f) exp(-note.decay * t) else 1f
        return attack * release * decay
    }

    /**
     * The master chain, and the other half of the "lo-fi" in lo-fi chiptune:
     * a two-pole low-pass that takes the edge off every square wave, a
     * high-pass that keeps the bass from turning to mud, a soft saturation
     * instead of hard clipping, and a noise floor quiet enough to be felt
     * rather than heard. Filter state persists across blocks so the section
     * boundaries are inaudible.
     */
    private class Master {
        private var lowA = 0f
        private var lowB = 0f
        private var highY = 0f
        private var highX = 0f

        fun finish(mix: FloatArray, blockStart: Int, totalFrames: Int, out: ByteArray) {
            for (i in mix.indices) {
                var sample = mix[i] * MASTER_GAIN + noise() * 0.0035f

                lowA += LOW_ALPHA * (sample - lowA)
                lowB += LOW_ALPHA * (lowA - lowB)
                sample = lowB

                val input = sample
                highY = HIGH_ALPHA * (highY + input - highX)
                highX = input
                sample = highY

                sample = sample * (27f + sample * sample) / (27f + 9f * sample * sample)

                val frame = blockStart + i
                sample *= fade(frame, totalFrames)

                val value = (sample.coerceIn(-1f, 1f) * 32000f).toInt()
                out[i * 2] = (value and 0xFF).toByte()
                out[i * 2 + 1] = ((value shr 8) and 0xFF).toByte()
            }
        }

        /** Silence at both ends, so the loop seam cannot click. */
        private fun fade(frame: Int, totalFrames: Int): Float {
            val inFrames = SAMPLE_RATE * 2
            val outFrames = SAMPLE_RATE * 3
            val rise = (frame.toFloat() / inFrames).coerceIn(0f, 1f)
            val fall = ((totalFrames - frame).toFloat() / outFrames).coerceIn(0f, 1f)
            return rise * fall
        }
    }

    // ------------------------------------------------------------ container

    private fun wavHeader(frames: Int): ByteArray {
        val dataSize = frames * 2
        val header = ByteArray(44)
        var p = 0

        fun ascii(value: String) {
            for (c in value) header[p++] = c.code.toByte()
        }

        fun int32(value: Int) {
            header[p++] = (value and 0xFF).toByte()
            header[p++] = ((value shr 8) and 0xFF).toByte()
            header[p++] = ((value shr 16) and 0xFF).toByte()
            header[p++] = ((value shr 24) and 0xFF).toByte()
        }

        fun int16(value: Int) {
            header[p++] = (value and 0xFF).toByte()
            header[p++] = ((value shr 8) and 0xFF).toByte()
        }

        ascii("RIFF"); int32(36 + dataSize); ascii("WAVE")
        ascii("fmt "); int32(16)
        int16(1)                     // PCM
        int16(1)                     // mono
        int32(SAMPLE_RATE)
        int32(SAMPLE_RATE * 2)       // byte rate
        int16(2)                     // block align
        int16(16)                    // bits per sample
        ascii("data"); int32(dataSize)
        return header
    }

    /** Seeded xorshift: the track must be identical on every device and build. */
    private class Rng(private var state: UInt) {
        fun next(): UInt {
            state = state xor (state shl 13)
            state = state xor (state shr 17)
            state = state xor (state shl 5)
            return state
        }

        fun float(): Float = (next() and 0xFFFFFFu).toFloat() / 0xFFFFFF.toFloat()

        fun int(bound: Int): Int = (next() % bound.toUInt()).toInt()
    }

    private const val MASTER_GAIN = 1.25f
    private val LOW_ALPHA = run {
        val dt = 1f / SAMPLE_RATE
        val rc = 1f / (TWO_PI * 2600f)
        dt / (rc + dt)
    }
    private val HIGH_ALPHA = run {
        val dt = 1f / SAMPLE_RATE
        val rc = 1f / (TWO_PI * 38f)
        rc / (rc + dt)
    }
}
