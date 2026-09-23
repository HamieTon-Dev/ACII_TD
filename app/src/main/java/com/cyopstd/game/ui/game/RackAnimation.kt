package com.cyopstd.game.ui.game

import kotlin.math.PI

/**
 * The clocks behind the two rack animations whose *speed* changes with board
 * load: the status LEDs and the chase circuit around the integrity readout.
 *
 * This exists as its own class for one reason: it holds the fix for a bug that
 * could not be seen by reading the drawing code. Both animations were written
 * as `time * rate`, with `rate` derived from the number of live threats. That
 * looks harmless, and it is — right up until the rate changes. Position is the
 * product of the whole elapsed time and the rate, so the instant a threat died
 * the rate changed and the position jumped with it, by
 *
 *     elapsed x (newRate - oldRate)
 *
 * which a minute into a run is tens of whole cycles. The chase teleported and
 * the LEDs flickered **every time the player killed something** — precisely
 * when they are looking at the board.
 *
 * Integrating instead (`phase += delta * rate`) makes the phase continuous
 * across a rate change: speeding up changes how fast the lights move from
 * where they are, rather than moving them somewhere else.
 */
class RackAnimation {

    /** 0..1 around the chase circuit. */
    var chasePhase = 0f
        private set

    /** Radians, wrapped, for the LED blink. */
    var ledPhase = 0f
        private set

    private var lastTime = Float.NaN

    /**
     * Advances to wall-clock [time] at the rates implied by [load].
     *
     * Returns the delta actually integrated, which is what the tests assert on.
     * A frame after a pause, a resume, or the app being backgrounded can be
     * arbitrarily long, and integrating all of it is its own visible jump — so
     * the step is clamped. A clock that went *backwards* means a new run: the
     * phases carry on from where they are rather than unwinding.
     */
    fun advance(time: Float, load: Float): Float {
        val previous = lastTime
        lastTime = time
        if (previous.isNaN() || time < previous) return 0f

        val delta = (time - previous).coerceAtMost(MAX_STEP)
        chasePhase = (chasePhase + delta * chaseRate(load)) % 1f
        ledPhase = (ledPhase + delta * ledRate(load)) % TWO_PI
        return delta
    }

    /** Forgets the clock, so the next [advance] starts a fresh interval. */
    fun reset() {
        lastTime = Float.NaN
    }

    companion object {
        /** Longest frame step that will be integrated, in seconds. */
        const val MAX_STEP = 0.25f

        const val CHASE_BASE_RATE = 0.32f
        const val CHASE_LOAD_RATE = 0.30f
        const val LED_BASE_RATE = 1.4f
        const val LED_LOAD_RATE = 3.4f

        const val TWO_PI = (2.0 * PI).toFloat()

        /** Cycles per second of the chase at a given board load. */
        fun chaseRate(load: Float): Float = CHASE_BASE_RATE + load * CHASE_LOAD_RATE

        /** Radians per second of the LED blink at a given board load. */
        fun ledRate(load: Float): Float = LED_BASE_RATE + load * LED_LOAD_RATE
    }
}
