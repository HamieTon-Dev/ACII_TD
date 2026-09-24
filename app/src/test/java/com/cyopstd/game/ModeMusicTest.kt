package com.cyopstd.game

import com.cyopstd.game.audio.ChiptuneComposer
import com.cyopstd.game.audio.ChiptuneComposer.Track
import com.cyopstd.game.audio.trackForMode
import com.cyopstd.game.core.GameMode
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two mode tracks.
 *
 * BOTTLE and BOTTLE_DRIVE are one piece of music played twice — same chords,
 * same progressions, same melodic vocabulary — so most of what matters here is
 * that they are *recognisably* related and still obviously different renders.
 * The rest is the same thing every generated track has to survive: a level that
 * does not clip, a length nobody hears repeat, and a loop seam that cannot
 * click.
 */
class ModeMusicTest {

    private val rendered = HashMap<Track, FloatArray>()

    private fun samples(track: Track): FloatArray = rendered.getOrPut(track) {
        val bytes = ByteArrayOutputStream(44 + ChiptuneComposer.totalFrames(track) * 2)
            .also { ChiptuneComposer.writeWav(it, track) }
            .toByteArray()
        val frames = ChiptuneComposer.totalFrames(track)
        FloatArray(frames) { i ->
            val lo = bytes[44 + i * 2].toInt() and 0xFF
            val hi = bytes[44 + i * 2 + 1].toInt()
            ((hi shl 8) or lo).toShort() / 32768f
        }
    }

    private fun rms(values: FloatArray, from: Int = 0, to: Int = values.size): Float {
        var sum = 0.0
        for (i in from until to) sum += values[i].toDouble() * values[i]
        return sqrt(sum / (to - from)).toFloat()
    }

    private fun peak(values: FloatArray): Float {
        var top = 0f
        for (v in values) if (abs(v) > top) top = abs(v)
        return top
    }

    // ------------------------------------------------------- which plays when

    @Test
    fun `HACK AI gets its own track and standard keeps the old one`() {
        assertEquals(Track.BOTTLE, trackForMode(GameMode.HACK_AI))
        assertEquals(Track.GAME, trackForMode(GameMode.STANDARD))
    }

    @Test
    fun `every mode has a track and no two modes share one by accident`() {
        // The mapping is an exhaustive `when`, so a new mode cannot compile
        // without answering this. The assertion is that nobody answered it by
        // pointing the new mode at whatever was nearest.
        val chosen = GameMode.entries.associateWith { trackForMode(it) }
        assertEquals(
            "two modes ended up on the same track: $chosen",
            GameMode.entries.size,
            chosen.values.distinct().size
        )
    }

    // ------------------------------------------------------ it is one piece

    @Test
    fun `the two mode tracks are the same piece at different speeds`() {
        // Same music means the same bars take a proportional time. Four bars
        // at 58bpm and four at 88bpm differ by exactly the tempo ratio, and
        // that holds only if the arrangements are the same length in bars.
        val slow = ChiptuneComposer.trackSeconds(Track.BOTTLE)
        val fast = ChiptuneComposer.trackSeconds(Track.BOTTLE_DRIVE)
        val expected = Track.BOTTLE_DRIVE.bpm / Track.BOTTLE.bpm
        assertEquals(
            "the two versions are not the same number of bars",
            expected.toDouble(),
            (slow / fast).toDouble(),
            0.001
        )
    }

    @Test
    fun `the driven version is faster and hits harder`() {
        assertTrue(
            "${Track.BOTTLE_DRIVE.bpm} is not faster than ${Track.BOTTLE.bpm}",
            Track.BOTTLE_DRIVE.bpm > Track.BOTTLE.bpm * 1.3f
        )
        assertTrue("the driven version has no bass punch", Track.BOTTLE_DRIVE.bassPunch > 0f)
        assertEquals("the slow version should not punch", 0f, Track.BOTTLE.bassPunch, 0.001f)

        // Measured as transient impact, not as loudness. A track hits harder
        // by having sharper attacks, not by being turned up -- chasing RMS
        // here just pushed the peak toward clipping while the punch itself
        // stayed the same.
        val driven = bassPunch(Track.BOTTLE_DRIVE)
        val slow = bassPunch(Track.BOTTLE)
        assertTrue(
            "the driven version's low end moves $driven against the slow " +
                "version's $slow -- it is not hitting any harder",
            driven > slow * 1.2f
        )
    }

    /**
     * How hard the low end hits: the average jump in the bass envelope.
     *
     * Low-passed first, so this is about the bass and the kick rather than
     * hats and lead; then the mean of the *positive* steps between short
     * windows, normalised by the level. A sustained bass line scores near
     * zero however loud it is, and a line of hard hits scores high however
     * quiet. That is the property the driven track was asked for.
     */
    private fun bassPunch(track: Track): Float {
        val s = samples(track)
        val rate = track.sampleRate
        // One-pole low-pass at roughly 250 Hz.
        val alpha = 1f - kotlin.math.exp(-2f * Math.PI.toFloat() * 250f / rate)
        var low = 0f
        // 40ms windows. At 10ms the envelope is dominated by the waveform's
        // own ripple rather than by note onsets, and the measurement washed
        // out the difference it exists to find.
        val window = rate / 25
        val levels = ArrayList<Float>()
        var acc = 0f
        var n = 0
        for (i in rate * 8 until s.size - rate * 8) {
            low += alpha * (s[i] - low)
            acc += low * low
            if (++n == window) {
                levels += kotlin.math.sqrt(acc / window)
                acc = 0f
                n = 0
            }
        }
        val mean = levels.average().toFloat()
        if (mean <= 0f) return 0f
        var jumps = 0f
        for (i in 1 until levels.size) {
            val step = levels[i] - levels[i - 1]
            if (step > 0f) jumps += step
        }
        return jumps / levels.size / mean
    }

    @Test
    fun `the mode tracks are not lo-fi`() {
        // The first version of these was, and it was the wrong call: it is a
        // machine, not a cassette. Dark is the harmony's job -- the palette
        // has a bII and a b9 in it -- and a filter closed down over the whole
        // mix is not "dark", it is "muffled".
        //
        // Four things made it lo-fi and all four are asserted, because each
        // one on its own is enough to put it back.
        for (track in listOf(Track.BOTTLE, Track.BOTTLE_DRIVE)) {
            assertTrue(
                "$track renders at ${track.sampleRate}Hz, so nothing above " +
                    "${track.sampleRate / 2}Hz can exist in it at all",
                track.sampleRate >= 24_000
            )
            assertTrue(
                "$track is filtered at ${track.cutoffHz}Hz, darker than the " +
                    "standard track's ${Track.GAME.cutoffHz}Hz",
                track.cutoffHz > Track.GAME.cutoffHz * 2f
            )
            assertTrue(
                "$track still has ${track.wow} of tape wow; warble is the " +
                    "single most recognisable lo-fi tell",
                track.wow < 0.15f
            )
            assertTrue(
                "$track still has an audible hiss floor at ${track.hiss}",
                track.hiss < Track.GAME.hiss / 4f
            )
        }
    }

    @Test
    fun `the mode tracks actually carry high frequencies, not just permission to`() {
        // Opening the filter was only half of it. The bright elements were not
        // in the mix to let through -- hats at a twelfth of the kick and a lead
        // under half the bass -- so the track measured as bass and nothing
        // else however wide open the filter was. This measures the render
        // rather than the settings, because the settings lied about this once.
        val plain = brightness(Track.GAME)
        for (track in listOf(Track.BOTTLE, Track.BOTTLE_DRIVE)) {
            val bright = brightness(track)
            assertTrue(
                "$track's spectral centroid is ${bright.toInt()}Hz against the " +
                    "standard track's ${plain.toInt()}Hz -- that is still lo-fi",
                bright > plain * 1.5f
            )
        }
    }

    /** Spectral centroid in Hz: the single honest number for "how bright". */
    private fun brightness(track: Track): Float =
        centroid(track, track.sampleRate * 8, samples(track).size - track.sampleRate * 8)

    private fun centroid(track: Track, from: Int, to: Int): Float {
        val s = samples(track)
        val rate = track.sampleRate
        val window = 2048
        val windows = (to - from) / window
        var weighted = 0.0
        var total = 0.0
        for (w in 0 until windows) {
            val re = DoubleArray(window)
            val im = DoubleArray(window)
            for (i in 0 until window) {
                // Hann, so the transform is not dominated by edge steps.
                val hann = 0.5 - 0.5 * kotlin.math.cos(2.0 * Math.PI * i / (window - 1))
                re[i] = s[from + w * window + i] * hann
            }
            dft(re, im)
            for (k in 1 until window / 2) {
                val mag = kotlin.math.sqrt(re[k] * re[k] + im[k] * im[k])
                weighted += mag * k * rate / window
                total += mag
            }
        }
        return if (total == 0.0) 0f else (weighted / total).toFloat()
    }

    /** In-place radix-2 FFT. A DFT would take minutes; this takes milliseconds. */
    private fun dft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                re[i] = re[j].also { re[j] = re[i] }
                im[i] = im[j].also { im[j] = im[i] }
            }
        }
        var len = 2
        while (len <= n) {
            val angle = -2.0 * Math.PI / len
            val wr = kotlin.math.cos(angle)
            val wi = kotlin.math.sin(angle)
            var i = 0
            while (i < n) {
                var curR = 1.0
                var curI = 0.0
                for (k in 0 until len / 2) {
                    val ur = re[i + k]
                    val ui = im[i + k]
                    val vr = re[i + k + len / 2] * curR - im[i + k + len / 2] * curI
                    val vi = re[i + k + len / 2] * curI + im[i + k + len / 2] * curR
                    re[i + k] = ur + vr
                    im[i + k] = ui + vi
                    re[i + k + len / 2] = ur - vr
                    im[i + k + len / 2] = ui - vi
                    val nextR = curR * wr - curI * wi
                    curI = curR * wi + curI * wr
                    curR = nextR
                }
                i += len
            }
            len = len shl 1
        }
    }

    // --------------------------------------------- the usual survival checks

    @Test
    fun `neither track clips`() {
        for (track in listOf(Track.BOTTLE, Track.BOTTLE_DRIVE)) {
            val top = peak(samples(track))
            assertTrue("$track peaks at $top, which is clipping", top < 0.99f)
            assertTrue("$track peaks at $top, which is far too quiet", top > 0.45f)
        }
    }

    @Test
    fun `neither track is loud enough to fight the game`() {
        // Music is atmosphere. A track mixed as hot as the effects means a
        // player turns the music off, which is worse than having none.
        for (track in listOf(Track.BOTTLE, Track.BOTTLE_DRIVE)) {
            assertTrue(
                "$track sits at ${rms(samples(track))} RMS",
                rms(samples(track)) < 0.30f
            )
        }
    }

    @Test
    fun `both ends fade through silence so the loop seam cannot click`() {
        for (track in listOf(Track.BOTTLE, Track.BOTTLE_DRIVE)) {
            val s = samples(track)
            assertTrue("$track starts loud", abs(s[0]) < 0.02f)
            assertTrue("$track ends loud", abs(s[s.size - 1]) < 0.02f)
        }
    }

    // ----------------------------------------------------------- the form

    /**
     * The form as it was asked for, in the words it was asked in:
     *
     * *"hard bass hits faster beat and a little tune, followed by distorted
     * guitar riffs, shakey tune followed by a repeat of the beat. some higher
     * end synth a repeat / glitch chords x6 and then repeat the whole thing"*
     */
    private val requested = listOf("GROOVE", "GUITAR", "SHAKY", "GROOVE", "SYNTH", "GLITCH")

    @Test
    fun `the form is the one that was asked for, and it repeats`() {
        for (track in listOf(Track.BOTTLE, Track.BOTTLE_DRIVE)) {
            val form = ChiptuneComposer.form(track)
            assertEquals(
                "$track's form is not the requested one played twice",
                requested + requested,
                form
            )
        }
    }

    @Test
    fun `both mode tracks are the same form, so the fast one is the same song`() {
        assertEquals(
            ChiptuneComposer.form(Track.BOTTLE),
            ChiptuneComposer.form(Track.BOTTLE_DRIVE)
        )
        assertEquals(
            ChiptuneComposer.sectionBars(Track.BOTTLE),
            ChiptuneComposer.sectionBars(Track.BOTTLE_DRIVE)
        )
    }

    @Test
    fun `each part of the form actually sounds different from its neighbours`() {
        // Measured per real section rather than by cutting the track into
        // equal slices. Equal slices stopped working the moment sections
        // stopped being the same length -- each slice averaged two parts
        // together and every part came out looking identical, which is a
        // measurement failure that reads exactly like a music failure.
        for (track in listOf(Track.BOTTLE, Track.BOTTLE_DRIVE)) {
            val parts = sectionAudio(track)
            // One pass of the form, skipping the faded opening section.
            val window = parts.drop(1).take(5)
            val loud = window.map { rms(samples(track), it.first, it.second) }
            val bright = window.map { centroid(track, it.first, it.second) }

            // Adjacent pairs, not the range across all of them. The range
            // rewards having one odd part and punishes fixing it -- brightening
            // the dullest section made this metric *worse* while making the
            // track better, which is a sign the metric was measuring the wrong
            // thing. What a listener notices is the change at a boundary.
            for (i in 1 until window.size) {
                val loudness = kotlin.math.abs(loud[i] - loud[i - 1]) / loud.max()
                val colour = kotlin.math.abs(bright[i] - bright[i - 1]) / bright.max()
                assertTrue(
                    "$track: nothing happens between part ${i - 1} and part $i " +
                        "(loudness moved ${(loudness * 100).toInt()}%, brightness " +
                        "${(colour * 100).toInt()}%)",
                    loudness > 0.06f || colour > 0.06f
                )
            }
        }
    }

    @Test
    fun `the glitch is the sharpest thing in the track, not the quietest`() {
        // It was the quietest, first time around, which is backwards: it is
        // the one moment the rhythm section stops, so it has the whole mix to
        // itself and has to cut.
        for (track in listOf(Track.BOTTLE, Track.BOTTLE_DRIVE)) {
            val parts = sectionAudio(track)
            val form = ChiptuneComposer.form(track)
            val glitch = parts[form.indexOf("GLITCH")]
            val grooves = form.withIndex().filter { it.value == "GROOVE" }
                .map { centroid(track, parts[it.index].first, parts[it.index].second) }

            val sharp = centroid(track, glitch.first, glitch.second)
            assertTrue(
                "the glitch ($sharp Hz) is duller than the grooves it interrupts " +
                    "($grooves Hz)",
                sharp > grooves.max()
            )
        }
    }

    // ------------------------------------------------------------- the kits

    @Test
    fun `the kit swaps every riff, and the two kits are really different`() {
        // The backbeat lands on beat 3. Bars 1-3 are played on the chip kit
        // and bars 5-7 on the acoustic one, so the same beat of the same
        // groove is available on both and everything around it is comparable.
        //
        // Measured in a narrow band around the acoustic snare's shell
        // frequency and in a tight window on the transient. A wider window
        // does not work: over 300ms and 150-300Hz the bass line swamps the
        // snare completely and the two kits measure as identical, which is
        // what the first version of this measurement concluded.
        for (track in listOf(Track.BOTTLE, Track.BOTTLE_DRIVE)) {
            val chip = backbeatEnergy(track, listOf(1, 2, 3), 170f, 210f)
            val real = backbeatEnergy(track, listOf(5, 6, 7), 170f, 210f)
            assertTrue(
                "$track measures $real at the snare shell on the acoustic riff " +
                    "against $chip on the chip riff -- the two kits sound the same",
                real > chip * 2f
            )
        }
    }

    @Test
    fun `the chip kit stays flat, which is the whole point of it`() {
        // A noise-channel snare has no tuned component at all. If the chip
        // riff ever grows one, the two kits have quietly become one kit.
        for (track in listOf(Track.BOTTLE, Track.BOTTLE_DRIVE)) {
            val shell = backbeatEnergy(track, listOf(1, 2, 3), 170f, 210f)
            val static = backbeatEnergy(track, listOf(1, 2, 3), 600f, 4000f)
            assertTrue(
                "$track's chip snare has grown a pitched body: $shell at the " +
                    "shell against $static of static",
                shell < static
            )
        }
    }

    /**
     * Energy between [lo] and [hi] in the 45ms after the backbeat of each of
     * [bars], as a fraction of that window's total.
     */
    private fun backbeatEnergy(
        track: Track,
        bars: List<Int>,
        lo: Float,
        hi: Float
    ): Float {
        val s = samples(track)
        val rate = track.sampleRate
        val beat = ChiptuneComposer.barSeconds(track) / 4f
        val window = 512
        var inBand = 0.0
        var total = 0.0
        for (bar in bars) {
            // Beat 3 of the bar, which is where the backbeat is written.
            val at = ((bar * 4 + 2) * beat * rate).toInt()
            val windows = (0.045f * rate).toInt() / window
            for (w in 0 until windows) {
                val re = DoubleArray(window)
                val im = DoubleArray(window)
                for (i in 0 until window) {
                    val hann = 0.5 - 0.5 * kotlin.math.cos(2.0 * Math.PI * i / (window - 1))
                    re[i] = s[at + w * window + i] * hann
                }
                dft(re, im)
                for (k in 1 until window / 2) {
                    val hz = k.toFloat() * rate / window
                    val power = re[k] * re[k] + im[k] * im[k]
                    total += power
                    if (hz >= lo && hz < hi) inBand += power
                }
            }
        }
        return if (total == 0.0) 0f else (inBand / total).toFloat()
    }

    @Test
    fun `the glitch fires exactly six stabs`() {
        // "x6" is a count. Counted off the rendered audio rather than off the
        // constant, because an earlier version fired the burst once per bar
        // and produced twelve while the constant still said six.
        assertEquals(6, ChiptuneComposer.GLITCH_STABS)
        for (track in listOf(Track.BOTTLE, Track.BOTTLE_DRIVE)) {
            val form = ChiptuneComposer.form(track)
            val (from, to) = sectionAudio(track)[form.indexOf("GLITCH")]
            assertEquals(
                "$track's glitch section does not fire six stabs",
                6,
                onsets(samples(track), from, to)
            )
        }
    }

    /** Start and end frame of every section, in order. */
    private fun sectionAudio(track: Track): List<Pair<Int, Int>> {
        val perBar = ChiptuneComposer.barSeconds(track) * track.sampleRate
        var at = 0
        return ChiptuneComposer.sectionBars(track).map { bars ->
            val frames = (perBar * bars).toInt()
            val span = at to at + frames
            at += frames
            span
        }
    }

    /**
     * Counts attacks: a jump in the envelope that follows a quiet patch.
     *
     * Deliberately crude, because what it has to distinguish is six stabs from
     * twelve, not one drum from another.
     */
    private fun onsets(s: FloatArray, from: Int, to: Int): Int {
        val window = 128
        val levels = ArrayList<Float>()
        var i = from
        while (i + window < to) {
            levels += rms(s, i, i + window)
            i += window
        }
        val threshold = levels.max() * 0.35f
        var count = 0
        var armed = true
        for (level in levels) {
            if (armed && level > threshold) {
                count++
                armed = false
            } else if (level < threshold * 0.5f) {
                armed = true
            }
        }
        return count
    }

    @Test
    fun `HACK AI's track is long enough that nobody hears it loop`() {
        // It plays under the longest runs in the game.
        assertTrue(
            "${ChiptuneComposer.trackSeconds(Track.BOTTLE)}s",
            ChiptuneComposer.trackSeconds(Track.BOTTLE) > 180f
        )
    }

    @Test
    fun `the render is deterministic`() {
        // The cache keys on a version number, not on content, so a track that
        // rendered differently twice would leave players on whichever one they
        // happened to get first.
        for (track in listOf(Track.BOTTLE, Track.BOTTLE_DRIVE)) {
            val first = ByteArrayOutputStream()
                .also { ChiptuneComposer.writeWav(it, track) }.toByteArray()
            val second = ByteArrayOutputStream()
                .also { ChiptuneComposer.writeWav(it, track) }.toByteArray()
            assertTrue("$track rendered differently twice", first.contentEquals(second))
        }
    }
}
