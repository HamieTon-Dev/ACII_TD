package com.cyopstd.game

import com.cyopstd.game.audio.ChiptuneComposer
import com.cyopstd.game.audio.ChiptuneComposer.Track
import com.cyopstd.game.audio.trackForMode
import com.cyopstd.game.core.GameMode
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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

        val loud = rms(samples(Track.BOTTLE_DRIVE))
        val quiet = rms(samples(Track.BOTTLE))
        assertTrue(
            "driven RMS $loud is not meaningfully above the slow version's $quiet",
            loud > quiet * 1.15f
        )
    }

    @Test
    fun `the eerie tracks do not sound like the standard one`() {
        // Not a taste assertion: the two palettes have different chord roots,
        // so a track built from one cannot be built from the other.
        assertNotEquals(Track.GAME.bpm, Track.BOTTLE.bpm)
        assertTrue(
            "HACK:AI's track should be darker than the standard one",
            Track.BOTTLE.cutoffHz < Track.GAME.cutoffHz
        )
        assertTrue(
            "and more warped -- that is most of what 'slowed' sounds like",
            Track.BOTTLE.wow > Track.GAME.wow
        )
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

    @Test
    fun `the arrangements develop instead of repeating`() {
        for (track in listOf(Track.BOTTLE, Track.BOTTLE_DRIVE)) {
            val s = samples(track)
            val sections = 6
            val per = s.size / sections
            // Skip the first and last: both are faded, so they would show as
            // "different" for a reason that is not the arrangement.
            val levels = (1 until sections - 1).map { rms(s, it * per, (it + 1) * per) }
            val spread = (levels.max() - levels.min()) / levels.max()
            assertTrue(
                "$track's sections are all the same loudness ($levels), so the " +
                    "arrangement is not doing anything",
                spread > 0.15f
            )
        }
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
