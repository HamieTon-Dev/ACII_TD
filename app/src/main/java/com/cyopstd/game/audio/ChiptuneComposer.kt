package com.cyopstd.game.audio

import java.io.OutputStream
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

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

    /**
     * The rate a track is rendered at, and a hard ceiling on how bright it can
     * possibly be.
     *
     * 16 kHz puts everything above 8 kHz out of reach, which is most of the
     * reason the original two tracks sound like tape: there is no air in them
     * because there is nowhere for air to live. It is the right call for those
     * two — half the cost, and warmth is what they are for — and the wrong one
     * for a track that is supposed to sound like a machine, so it is a track
     * property rather than a constant.
     */
    private var sampleRate = 16_000

    /** Bump this when an arrangement changes so cached renders are replaced. */
    const val TRACK_VERSION = 4

    private const val BEATS_PER_BAR = 4
    private const val BARS_PER_SECTION = 8

    /**
     * The two pieces of music in the game.
     *
     * They are deliberately opposite. [GAME] is slow lo-fi that has to sit
     * under an hour of play without ever asking for attention. [MENU] is the
     * one place the game is allowed to be loud: nothing is happening, nobody
     * is concentrating, and a classic chiptune is what a menu is for. So it is
     * faster, brighter — a far higher filter cutoff, which is most of the
     * difference between "lo-fi" and "8-bit" — and short, because a menu is
     * not somewhere anyone sits for three minutes.
     */
    enum class Track(
        val bpm: Float,
        /** Low-pass cutoff in Hz. Low is lo-fi; high is bright 8-bit. */
        val cutoffHz: Float,
        val masterGain: Float,
        /** Tape-wow depth. Zero is a clean machine. */
        val wow: Float,
        /**
         * Render rate, and a hard ceiling on brightness: nothing above half of
         * it can exist in the file at all.
         */
        val sampleRate: Int = 16_000,
        /** Noise floor. Audible hiss is a tape artefact; a machine has none. */
        val hiss: Float = 0.0035f,
        /**
         * Pulse width of the lead, 0.5 being a square.
         *
         * A square is mellow — odd harmonics only. Narrowing it brings in the
         * even harmonics too and the lead goes from warm to biting, which is
         * most of the difference between a chiptune that sounds nostalgic and
         * one that sounds like a machine talking.
         */
        val leadDuty: Float = 0.5f,
        /**
         * How much of the mix is allowed to live above the bass, 1 being the
         * original balance.
         *
         * Opening the filter turned out to be only half of "less lo-fi". The
         * other half is that the bright elements were not there to let
         * through: hats at a twelfth of the kick's level and a lead under half
         * the bass's, so a track could be wide open and still measure as
         * bass-and-nothing-else. This lifts the hats and the lead together,
         * and above 1.2 it doubles the lead an octave up — the cheapest real
         * top end there is.
         */
        val air: Float = 1f,
        /**
         * Master high-pass corner in Hz.
         *
         * 38 Hz removes essentially nothing, which is right for a warm track.
         * Lifting it thins the low end so everything above it is heard rather
         * than merely present.
         */
        val lowCutHz: Float = 38f,
        /**
         * Extra transient on every bass note, 0 for none.
         *
         * A "harder" bass on a phone speaker is not a deeper one — the driver
         * rolls off long before the fundamental — so this adds a short swept
         * attack above the root rather than anything below it. That is what
         * actually reads as a hit on the device most people will play on.
         */
        val bassPunch: Float = 0f
    ) {
        GAME(bpm = 72f, cutoffHz = 2600f, masterGain = 1.25f, wow = 1f),
        MENU(bpm = 132f, cutoffHz = 7200f, masterGain = 1.15f, wow = 0.15f),

        /**
         * HACK:AI's track.
         *
         * Slow and unsettled, but **not** lo-fi — and the difference is four
         * specific things rather than a mood. It renders at 24 kHz instead of
         * 16, so there is somewhere above 8 kHz for the hats and the lead's
         * upper harmonics to live at all. The filter sits at 6.4 kHz instead
         * of 1.9, so those harmonics actually arrive. The tape wow is
         * effectively off, so the tuning holds instead of warbling. And the
         * hiss is down near nothing, because hiss is a cassette artefact and
         * this is meant to sound like a machine.
         *
         * What stays is the writing: the same uneasy palette, the same slow
         * tempo, the same arrangement that never quite lifts. Dark is the
         * harmony's job, not the filter's.
         */
        BOTTLE(
            bpm = 60f, cutoffHz = 6400f, masterGain = 1.10f, wow = 0.08f,
            sampleRate = 24_000, hiss = 0.0006f, leadDuty = 0.30f,
            air = 2.4f, lowCutHz = 74f
        ),

        /**
         * The same piece at speed, for the second map.
         *
         * Half again as fast, brighter still, and every bass note given a hard
         * swept attack. Same chords, same melody vocabulary, same
         * progressions — it is recognisably the other track, which is why it
         * is one palette and two arrangements rather than two pieces of music.
         */
        BOTTLE_DRIVE(
            bpm = 90f, cutoffHz = 7600f, masterGain = 1.10f, wow = 0.05f,
            sampleRate = 24_000, hiss = 0.0005f, leadDuty = 0.26f,
            air = 2.7f, lowCutHz = 80f, bassPunch = 1f
        )
    }

    /**
     * Timing and arrangement for the track being rendered.
     *
     * Object-level state rather than a parameter on twenty functions. Renders
     * are sequential and it is set once at the top of [writeWav], the same way
     * the noise generator is reset there.
     */
    private var bpm = Track.GAME.bpm
    private var track = Track.GAME
    private var sections: Array<Section> = emptyArray()
    private var palette = Palette.DIATONIC

    private val SECONDS_PER_BEAT get() = 60f / bpm
    private val SECONDS_PER_BAR get() = SECONDS_PER_BEAT * BEATS_PER_BAR

    private const val TWO_PI = (2.0 * PI).toFloat()

    // ----------------------------------------------------------- the music

    /** Which oscillator a note is played on. */
    private enum class Voice { LEAD, PAD, BASS, KICK, HAT, RIM, SUB }

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

    /**
     * A harmonic world: the chords available, the orders they are played in,
     * and the notes the melody may use.
     *
     * Pulled out of top-level constants so a track can have its own tonality
     * rather than every piece in the game being the same four diatonic
     * sevenths. That is most of what makes two tracks sound like different
     * music rather than the same music at different speeds.
     */
    private class Palette(
        val chords: Array<Chord>,
        /** Four-chord progressions, two bars per chord. */
        val progressions: Array<IntArray>,
        /** The melody's whole vocabulary, in MIDI numbers. */
        val scale: IntArray
    ) {
        companion object {

            /**
             * A minor, diatonic sevenths. The original, warm and resolved.
             */
            val DIATONIC = Palette(
                chords = arrayOf(
                    Chord(45, intArrayOf(57, 60, 64, 67)),  // Am7
                    Chord(50, intArrayOf(57, 62, 65, 69)),  // Dm7
                    Chord(43, intArrayOf(55, 59, 62, 67)),  // G
                    Chord(48, intArrayOf(55, 60, 64, 67)),  // Cmaj7
                    Chord(41, intArrayOf(57, 60, 65, 69)),  // Fmaj7
                    Chord(40, intArrayOf(55, 59, 62, 64))   // Em7
                ),
                progressions = arrayOf(
                    intArrayOf(0, 4, 3, 2),   // i  - VI - III - VII
                    intArrayOf(1, 2, 3, 0),   // iv - VII - III - i
                    intArrayOf(0, 5, 4, 2),   // i  - v  - VI  - VII
                    intArrayOf(4, 3, 1, 0)    // VI - III - iv - i
                ),
                // A minor pentatonic.
                scale = intArrayOf(69, 72, 74, 76, 79, 81)
            )

            /**
             * A Phrygian with a harmonic-minor dominant: the unsettled one.
             *
             * Two chords do the work. The **bII** (Bbmaj7) puts a flat second
             * directly above the tonic, which is the interval every piece of
             * uneasy music in history has reached for, and the **V7b9**
             * (E7b9) pulls back to the tonic hard enough that the flat second
             * reads as dread rather than as a mistake. Everything else is
             * ordinary A minor, so the two colour chords land rather than
             * becoming the whole texture.
             *
             * The melody scale is A Phrygian plus the raised seventh, so the
             * line can follow the dominant instead of stepping around it.
             */
            val UNEASY = Palette(
                chords = arrayOf(
                    Chord(45, intArrayOf(57, 59, 60, 64)),  // 0  Am(add9)
                    Chord(41, intArrayOf(57, 60, 64, 65)),  // 1  Fmaj7
                    Chord(50, intArrayOf(57, 62, 64, 65)),  // 2  Dm(add9)
                    Chord(46, intArrayOf(57, 58, 62, 65)),  // 3  Bbmaj7  (bII)
                    Chord(40, intArrayOf(56, 59, 62, 65)),  // 4  E7b9    (V)
                    Chord(43, intArrayOf(58, 62, 65, 69))   // 5  Gm9
                ),
                progressions = arrayOf(
                    intArrayOf(0, 3, 1, 0),   // i   - bII - VI  - i
                    intArrayOf(0, 2, 5, 4),   // i   - iv  - bVII- V7b9
                    intArrayOf(1, 3, 0, 4),   // VI  - bII - i   - V7b9
                    intArrayOf(2, 0, 3, 1)    // iv  - i   - bII - VI
                ),
                // A Phrygian + raised 7th: A Bb C D E F G G#.
                scale = intArrayOf(69, 70, 72, 74, 76, 77, 79, 80, 81)
            )
        }
    }


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

    /**
     * The menu track: four bright sections, about eighty seconds.
     *
     * Denser melody than the game track and no quiet intro, because a menu
     * track begins the moment the menu appears and gets a few seconds of
     * attention rather than an hour of tolerance.
     */
    private val MENU_ARRANGEMENT = arrayOf(
        Section(0, melodyDensity = 0.52f, drums = 2, melodyOctave = 1, padGain = 0.95f),
        Section(2, melodyDensity = 0.58f, drums = 2, melodyOctave = 1, padGain = 1.00f),
        Section(1, melodyDensity = 0.46f, drums = 2, melodyOctave = 0, padGain = 0.95f),
        Section(0, melodyDensity = 0.60f, drums = 2, melodyOctave = 1, padGain = 1.00f)
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

    /**
     * HACK:AI's arrangement: six sections, slow, and it never really lifts.
     *
     * Deliberately shaped differently from the standard track. That one opens
     * quiet, builds, breaks down and rebuilds — the ordinary arc. This one
     * arrives almost fully formed, thins in the middle, and ends emptier than
     * it started. A mode that takes an hour and usually ends badly does not
     * want a track that keeps promising a chorus.
     */
    private val BOTTLE_ARRANGEMENT = arrayOf(
        Section(0, melodyDensity = 0.00f, drums = 0, melodyOctave = 0, padGain = 0.78f,
            bassGain = 0.70f, bassPickup = false),
        Section(0, melodyDensity = 0.26f, drums = 1, melodyOctave = 0, padGain = 0.95f,
            bassGain = 0.90f),
        Section(2, melodyDensity = 0.34f, drums = 2, melodyOctave = 0, padGain = 1.00f),
        Section(1, melodyDensity = 0.00f, drums = 1, melodyOctave = 0, padGain = 0.68f,
            bassGain = 0.64f, bassPickup = false),
        Section(3, melodyDensity = 0.38f, drums = 2, melodyOctave = 0, padGain = 1.00f),
        Section(0, melodyDensity = 0.14f, drums = 0, melodyOctave = 0, padGain = 0.60f,
            bassGain = 0.58f, bassPickup = false)
    )

    /**
     * The same piece, driven: six sections with the floor never dropping out.
     *
     * Full kit from the first bar and no quiet intro, because this one plays
     * over a map rather than under an endurance mode. The one breakdown keeps
     * its drums, so the bass punch carries straight through it.
     */
    private val BOTTLE_DRIVE_ARRANGEMENT = arrayOf(
        Section(0, melodyDensity = 0.40f, drums = 2, melodyOctave = 0, padGain = 0.95f,
            bassGain = 1.05f),
        Section(2, melodyDensity = 0.50f, drums = 2, melodyOctave = 1, padGain = 1.00f,
            bassGain = 1.10f),
        Section(1, melodyDensity = 0.44f, drums = 2, melodyOctave = 0, padGain = 1.00f,
            bassGain = 1.10f),
        Section(3, melodyDensity = 0.20f, drums = 2, melodyOctave = 0, padGain = 0.80f,
            bassGain = 1.00f, bassPickup = false),
        Section(2, melodyDensity = 0.56f, drums = 2, melodyOctave = 1, padGain = 1.00f,
            bassGain = 1.12f),
        Section(0, melodyDensity = 0.46f, drums = 2, melodyOctave = 0, padGain = 0.95f,
            bassGain = 1.05f)
    )

    /**
     * Total length of [which] in seconds.
     *
     * Computed from the track rather than by borrowing the renderer's state
     * and putting it back. That save-and-restore worked while tempo was the
     * only thing a track changed; the moment sample rate joined it, the
     * restore was one line short and [totalFrames] started answering with the
     * wrong rate — which is the number `MusicEngine` checks a cached file's
     * length against. A function that reads no shared state cannot go stale
     * that way.
     */
    fun trackSeconds(which: Track = Track.GAME): Float =
        arrangementFor(which).size * BARS_PER_SECTION * secondsPerBar(which)

    fun totalFrames(which: Track = Track.GAME): Int =
        arrangementFor(which).size * framesPerSection(which)

    private fun secondsPerBar(which: Track): Float = (60f / which.bpm) * BEATS_PER_BAR

    /**
     * The block size a render writes at a time.
     *
     * The same function feeds the WAV header's length and the loop that
     * writes the samples, so the two cannot disagree by a rounding step and
     * leave a file that is a few bytes short of what its header claims.
     */
    private fun framesPerSection(which: Track): Int =
        (secondsPerBar(which) * BARS_PER_SECTION * which.sampleRate).toInt()

    private fun arrangementFor(which: Track): Array<Section> = when (which) {
        Track.MENU -> MENU_ARRANGEMENT
        Track.BOTTLE -> BOTTLE_ARRANGEMENT
        Track.BOTTLE_DRIVE -> BOTTLE_DRIVE_ARRANGEMENT
        Track.GAME -> ARRANGEMENT
    }

    /**
     * BOTTLE and BOTTLE_DRIVE share one palette on purpose: they are the same
     * piece of music, played twice.
     */
    private fun paletteFor(which: Track): Palette = when (which) {
        Track.BOTTLE, Track.BOTTLE_DRIVE -> Palette.UNEASY
        Track.GAME, Track.MENU -> Palette.DIATONIC
    }

    // --------------------------------------------------------------- output

    /**
     * Render the whole track as a 16-bit mono WAV onto [out].
     *
     * Written a section at a time so nothing bigger than a couple of megabytes
     * is ever live, which matters on the low-end devices this game targets.
     */
    fun writeWav(out: OutputStream, which: Track = Track.GAME) {
        track = which
        bpm = which.bpm
        sampleRate = which.sampleRate
        sections = arrangementFor(which)
        palette = paletteFor(which)
        val frames = totalFrames(which)
        out.write(wavHeader(frames))

        // The noise generator is object state, so a second render would start
        // where the first left off and produce a different track. Reset it.
        noiseState = NOISE_SEED

        val notes = compose()
        // One section at a time, so peak memory stays near a couple of MB.
        val blockFrames = framesPerSection(which)
        val mix = FloatArray(blockFrames)
        val bytes = ByteArray(blockFrames * 2)
        val master = Master(track, sampleRate)

        for (section in sections.indices) {
            java.util.Arrays.fill(mix, 0f)
            val blockStart = section * blockFrames
            val blockEnd = blockStart + blockFrames

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

        for (s in sections.indices) {
            val section = sections[s]
            val progression = palette.progressions[section.progression]

            for (bar in 0 until BARS_PER_SECTION) {
                val chord = palette.chords[progression[(bar / 2) % progression.size]]
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
        addBassPunch(notes, root, time, section)

        if (!section.bassPickup) return
        val offset = if (bar % 4 == 3) 2.0f else 2.5f
        notes += note(
            time + SECONDS_PER_BEAT * offset, SECONDS_PER_BEAT * 1.2f, root, Voice.BASS,
            amp = 0.26f * section.bassGain, attack = 0.012f, release = 0.10f, decay = 0.7f
        )
        addBassPunch(notes, root, time + SECONDS_PER_BEAT * offset, section, 0.8f)
    }

    /**
     * The transient that makes a bass note hit rather than arrive.
     *
     * A short note that starts two octaves above the root and falls onto it in
     * a fraction of a beat. It is not a sub — a phone speaker cannot reproduce
     * one, and writing an inaudible 40 Hz layer is how a mix ends up sounding
     * thin on the hardware it actually ships to. Starting high and dropping
     * puts the energy in the band the driver can move, and the ear hears the
     * arrival rather than the sweep.
     */
    private fun addBassPunch(
        notes: MutableList<Note>,
        root: Float,
        time: Float,
        section: Section,
        gain: Float = 1f
    ) {
        if (track.bassPunch <= 0f) return
        notes += Note(
            startFrame = frameOf(time),
            frames = frameOf(SECONDS_PER_BEAT * 0.42f),
            freq = root * 4f,
            endFreq = root,
            voice = Voice.SUB,
            amp = 0.30f * track.bassPunch * section.bassGain * gain,
            attack = 0.001f,
            release = 0.04f,
            decay = 9f,
            vibrato = 0f
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
            // On an airy track the hat is the only thing in the mix with any
            // real top end, and at a 70 decay it lasted about fifteen
            // milliseconds -- a tick, not a hat. Letting it ring is worth more
            // brightness than any amount of filter.
            notes += note(
                time + SECONDS_PER_BEAT * 0.5f * slot,
                0.06f * track.air.coerceAtLeast(1f), 0f, Voice.HAT,
                amp = (if (intensity >= 2) 0.075f else 0.05f) * track.air,
                attack = 0.001f, release = 0.01f, decay = 70f / track.air.coerceAtLeast(1f)
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
     * The melody walks the scale rather than jumping around it:
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
                (index + rng.int(5) - 2).coerceIn(0, palette.scale.size - 1)
            }

            val lengthSlots = when {
                strong && rng.float() < 0.45f -> 3
                rng.float() < 0.30f -> 2
                else -> 1
            }
            val duration = SECONDS_PER_BEAT * 0.5f * lengthSlots * 0.92f
            val pitch = palette.scale[index] + section.melodyOctave * 12

            val start = barTime + SECONDS_PER_BEAT * 0.5f * slot
            val base = (if (section.melodyOctave > 0) 0.10f else 0.135f) *
                (if (strong) 1f else 0.82f) * sqrt(track.air)
            notes += note(
                start, duration, midi(pitch.toFloat()), Voice.LEAD,
                amp = base, attack = 0.014f, release = 0.06f, decay = 1.1f, vibrato = 0.005f
            )
            if (track.air > 1.2f) {
                // A quiet octave above the line. It is not heard as a second
                // melody, it is heard as the first one being brighter.
                notes += note(
                    start, duration * 0.8f, midi(pitch + 12f), Voice.LEAD,
                    amp = base * 0.30f, attack = 0.010f, release = 0.05f,
                    decay = 1.6f, vibrato = 0.005f
                )
            }
            slot += lengthSlots
        }
        return index
    }

    /** Pull [pitchClass] into the scale and pick the degree nearest [near]. */
    private fun nearestScaleDegree(pitchClass: Int, near: Int): Int {
        var best = near
        var bestDistance = Int.MAX_VALUE
        for (i in palette.scale.indices) {
            if (palette.scale[i] % 12 != pitchClass) continue
            val distance = kotlin.math.abs(i - near)
            if (distance < bestDistance) {
                bestDistance = distance
                best = i
            }
        }
        // Not in the scale (the 7th of a maj7, say): step gently instead.
        return if (bestDistance == Int.MAX_VALUE) {
            (near + if (near < 3) 1 else -1).coerceIn(0, palette.scale.size - 1)
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

    private fun frameOf(seconds: Float): Int = (seconds * sampleRate).toInt().coerceAtLeast(1)

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

        val duration = note.frames.toFloat() / sampleRate
        // A note that began in an earlier block resumes with the right phase.
        val skipped = (from - note.startFrame).toFloat() / sampleRate
        var phase = (note.freq * skipped) % 1f

        for (frame in from until to) {
            val t = (frame - note.startFrame).toFloat() / sampleRate
            val absolute = frame.toFloat() / sampleRate

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
            if (note.voice != Voice.HAT && note.voice != Voice.RIM &&
                note.voice != Voice.SUB
            ) {
                freq *= 1f + track.wow * (
                    0.0024f * sin(TWO_PI * 0.13f * absolute) +
                        0.0012f * sin(TWO_PI * 0.37f * absolute + 1.7f)
                    )
            }

            phase += freq / sampleRate
            if (phase >= 1f) phase -= phase.toInt().toFloat()

            val raw = when (note.voice) {
                Voice.LEAD -> if (phase < track.leadDuty) 1f else -1f
                Voice.PAD -> if (phase < 0.25f) 1f else -1f
                Voice.BASS -> if (phase < 0.5f) 4f * phase - 1f else 3f - 4f * phase
                Voice.KICK -> sin(TWO_PI * phase)
                Voice.HAT -> noise()
                Voice.RIM -> noise() * 0.7f + sin(t * 1180f) * 0.3f
                // Driven hard on purpose: the master's soft saturation turns
                // the overdrive into grit rather than into clipping, which is
                // what makes a bass hit audible through a phone speaker.
                Voice.SUB -> {
                    val driven = sin(TWO_PI * phase) * 2.4f
                    driven / (1f + kotlin.math.abs(driven) * 0.55f)
                }
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
    private class Master(private val track: Track, private val rate: Int) {
        private var lowA = 0f
        private var lowB = 0f
        private var highY = 0f
        private var highX = 0f

        // Hoisted: these were recomputed (via a map lookup) once per sample,
        // and they cannot change inside a render.
        private val lowAlpha = lowAlphaFor(track.cutoffHz, rate)
        private val highAlpha = highAlphaFor(rate, track.lowCutHz)

        fun finish(mix: FloatArray, blockStart: Int, totalFrames: Int, out: ByteArray) {
            for (i in mix.indices) {
                var sample = mix[i] * track.masterGain + noise() * track.hiss

                lowA += lowAlpha * (sample - lowA)
                lowB += lowAlpha * (lowA - lowB)
                sample = lowB

                val input = sample
                highY = highAlpha * (highY + input - highX)
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
            val inFrames = rate * 2
            val outFrames = rate * 3
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
        int32(sampleRate)
        int32(sampleRate * 2)        // byte rate
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

    /** One-pole low-pass coefficient for a cutoff at a given rate. */
    private fun lowAlphaFor(cutoffHz: Float, rate: Int): Float {
        val dt = 1f / rate
        val rc = 1f / (TWO_PI * cutoffHz)
        return dt / (rc + dt)
    }

    private fun highAlphaFor(rate: Int, cornerHz: Float): Float {
        val dt = 1f / rate
        val rc = 1f / (TWO_PI * cornerHz)
        return rc / (rc + dt)
    }
}
