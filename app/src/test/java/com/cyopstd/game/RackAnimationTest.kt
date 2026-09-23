package com.cyopstd.game

import com.cyopstd.game.ui.game.RackAnimation
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The chase lights and the status LEDs, and the bug that made them glitch.
 *
 * The player's report was that the racetrack on the core "glitches while
 * defeating enemies" and the animation stutters. That is a precise symptom of
 * a precise mistake: both animations derived their position from
 * `time * rate`, and `rate` rises with the number of live threats. Position is
 * therefore the product of the *whole elapsed time* and a rate that changes
 * mid-run, so the moment a threat died the position jumped by
 *
 *     elapsed x (newRate - oldRate)
 *
 * The first test below measures that jump with the old formula, so the size of
 * the thing being fixed is on the record. The rest assert the new behaviour:
 * a phase that moves continuously no matter what the board is doing.
 */
class RackAnimationTest {

    /** How far a phase may legitimately move in one step at a given load. */
    private fun maxChaseStep(delta: Float, load: Float) =
        delta * RackAnimation.chaseRate(load)

    @Test
    fun `the old formula jumped whole cycles when a threat died`() {
        // What the code used to compute, at one minute into a run, when the
        // board went from busy to quiet.
        val elapsed = 60f
        val busy = elapsed * RackAnimation.chaseRate(1.4f)
        val quiet = elapsed * RackAnimation.chaseRate(0f)

        val jump = abs(busy - quiet)
        assertTrue(
            "the old formula's jump was $jump cycles; if this is small the " +
                "test no longer describes the bug",
            jump > 20f
        )
    }

    @Test
    fun `the phase never moves further than its rate allows`() {
        val rack = RackAnimation()
        var time = 0f
        rack.advance(time, 0f)

        // A run where the board fills up and empties out repeatedly, which is
        // every run: waves arrive, get killed, and the next one starts.
        val loads = listOf(0f, 1.6f, 0.2f, 1.4f, 0f, 0.9f, 0f, 1.6f, 0f)
        var previous = rack.chasePhase
        for (load in loads) {
            repeat(10) {
                time += 1f / 60f
                val delta = 1f / 60f
                rack.advance(time, load)
                var moved = rack.chasePhase - previous
                if (moved < 0f) moved += 1f // wrapped past the end of the loop
                assertTrue(
                    "the chase jumped $moved of a cycle in one frame at load $load",
                    moved <= maxChaseStep(delta, load) + 1e-4f
                )
                previous = rack.chasePhase
            }
        }
    }

    @Test
    fun `changing load changes speed, not position`() {
        val quiet = RackAnimation()
        val busy = RackAnimation()
        quiet.advance(0f, 0f)
        busy.advance(0f, 0f)

        // Same elapsed time, one board quiet throughout and one that goes busy
        // at the halfway point.
        var t = 0f
        repeat(60) {
            t += 1f / 60f
            quiet.advance(t, 0f)
            busy.advance(t, if (t > 0.5f) 1.6f else 0f)
        }

        // The busy one is further along, because it ran faster for half the
        // time -- not because it teleported.
        assertTrue(
            "a busier board must run the chase faster: ${busy.chasePhase} vs ${quiet.chasePhase}",
            busy.chasePhase > quiet.chasePhase
        )
        val extra = busy.chasePhase - quiet.chasePhase
        val expected = 0.5f * (RackAnimation.chaseRate(1.6f) - RackAnimation.chaseRate(0f))
        assertEquals("the extra travel is exactly the extra speed", expected, extra, 0.01f)
    }

    @Test
    fun `a long gap is clamped rather than caught up`() {
        val rack = RackAnimation()
        rack.advance(0f, 1.6f)

        // The app was backgrounded for a minute. Integrating all of it would
        // fling the lights around on the first frame back.
        val delta = rack.advance(60f, 1.6f)

        assertEquals(RackAnimation.MAX_STEP, delta, 1e-5f)
    }

    @Test
    fun `a rewound clock does not unwind the lights`() {
        val rack = RackAnimation()
        rack.advance(10f, 0f)
        rack.advance(10.5f, 0f)
        val before = rack.chasePhase

        // A new run restarts the render clock at zero.
        val delta = rack.advance(0f, 0f)

        assertEquals(0f, delta, 1e-6f)
        assertEquals("the phase must not move backwards", before, rack.chasePhase, 1e-6f)
    }

    @Test
    fun `the LED blink is continuous too`() {
        // Same bug, same fix: the status grid blinks faster under load, and it
        // used to jump for exactly the same reason.
        val rack = RackAnimation()
        var time = 0f
        rack.advance(time, 0f)
        var previous = rack.ledPhase
        for (load in listOf(0f, 1.6f, 0f, 1.2f)) {
            repeat(10) {
                time += 1f / 60f
                rack.advance(time, load)
                var moved = rack.ledPhase - previous
                if (moved < 0f) moved += RackAnimation.TWO_PI
                assertTrue(
                    "the LED phase jumped $moved radians in one frame",
                    moved <= (1f / 60f) * RackAnimation.ledRate(load) + 1e-4f
                )
                previous = rack.ledPhase
            }
        }
    }

    @Test
    fun `phases stay in range over a long run`() {
        // Float precision: an unwrapped phase after an hour of play loses the
        // resolution the animation needs.
        val rack = RackAnimation()
        var time = 0f
        repeat(3_600) {
            time += 1f
            rack.advance(time, 1.0f)
            assertTrue(rack.chasePhase in 0f..1f)
            assertTrue(rack.ledPhase in 0f..RackAnimation.TWO_PI)
        }
    }
}
