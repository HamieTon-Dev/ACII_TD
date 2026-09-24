package com.cyopstd.game.ui.menu

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * The main menu powering on, as a function of elapsed seconds.
 *
 * Four beats, and each of them is something a rack actually does: dark, then
 * one LED catching, then the panels arriving unevenly and unstably, then
 * everything settled and ready.
 *
 * It is a plain function rather than a set of animation specs for two reasons.
 * The obvious one is that a held beat that is a frame short reads as a flicker
 * rather than as a timing bug, and a function can be asserted at the moments
 * that matter. The less obvious one is that the flicker has to be **seeded**:
 * a menu that boots differently every launch reads as a rendering fault, not
 * as a feature, which is the same reason the shard bursts in 1.20.0 are
 * derived from a seed rather than from `Random`.
 */
object MenuBoot {

    /** A black screen, long enough to read as a room with the lights off. */
    const val DARK_SECONDS = 0.26f

    /** One LED catching, before anything else. */
    const val LED_SECONDS = 0.42f

    /** Panels arriving out of order and unstably. */
    const val FLICKER_SECONDS = 0.80f

    /** Everything up, nothing still moving. */
    const val SETTLE_SECONDS = 0.16f

    const val TOTAL_SECONDS =
        DARK_SECONDS + LED_SECONDS + FLICKER_SECONDS + SETTLE_SECONDS

    /** How dim an element goes between flickers. Never fully out. */
    private const val FLICKER_FLOOR = 0.10f

    private const val TWO_PI = (2.0 * PI).toFloat()

    /** True once the menu is finished booting and can be used normally. */
    fun isSettled(elapsed: Float): Boolean = elapsed >= TOTAL_SECONDS

    /**
     * The power LED, which comes up before any of the interface does.
     *
     * It **overshoots and settles** rather than ramping. That overshoot is
     * most of what reads as electrical — a linear fade up reads as a fade up,
     * which every app does and no piece of hardware does.
     */
    fun ledGlow(elapsed: Float): Float {
        if (elapsed <= DARK_SECONDS) return 0f
        val t = (elapsed - DARK_SECONDS) / LED_SECONDS
        // Tuned so the rise and the first swing of the ring land together:
        // at 11 and 5.5 the two barely reached 1.02 between them, which
        // measures as a ramp and looks like one.
        val rise = 1f - exp(-16f * t)
        val ring = 0.55f * exp(-4f * t) * sin(TWO_PI * 2.4f * t)
        val lit = (rise + ring).coerceAtLeast(0f)

        // Once the interface starts arriving the LED is no longer the only
        // thing on screen, so it gives way rather than competing.
        if (t <= 1f) return lit
        val fade = ((elapsed - DARK_SECONDS - LED_SECONDS) / FLICKER_SECONDS)
            .coerceIn(0f, 1f)
        return lit * (1f - fade)
    }

    /**
     * How lit element [index] is.
     *
     * Elements have their own turn-on times and their own flicker patterns,
     * both derived from the index, so the order is scattered but identical on
     * every launch. An element that has turned on can still drop out for a
     * frame or two before it holds, which is what a cold fluorescent does and
     * what makes this read as hardware rather than as a stagger.
     */
    fun elementAlpha(index: Int, elapsed: Float): Float {
        if (elapsed >= TOTAL_SECONDS) return 1f
        val h = hash(index)

        // Scattered across the first two thirds of the flicker window, so the
        // last element still has time to settle before the end.
        val offset = ((h ushr 7) and 0xFFF) / 4095f
        val turnOn = DARK_SECONDS + LED_SECONDS + offset * FLICKER_SECONDS * 0.62f
        if (elapsed < turnOn) return 0f

        val since = elapsed - turnOn
        val unstableFor = FLICKER_SECONDS * 0.34f
        if (since >= unstableFor) return 1f

        // The last third of an element's own window holds steady, so it
        // resolves rather than being cut off mid-flicker by the deadline.
        if (since > unstableFor * 0.62f) return 1f

        val period = 0.035f + 0.030f * (((h ushr 19) and 0x7) / 7f)
        val step = (since / period).toInt()
        val lit = ((h ushr (step % 13)) and 1) == 1
        return if (lit) 1f else FLICKER_FLOOR
    }

    /** How many distinct elements the menu reveals. */
    const val ELEMENTS = 20

    /**
     * Deterministic per-element scatter.
     *
     * A hash rather than a `Random`: the same element must get the same
     * behaviour on every launch and on every device, or the boot looks like a
     * different fault each time somebody watches it.
     */
    private fun hash(index: Int): Int {
        var h = (index + 1) * -0x61c88647
        h = h xor (h ushr 16)
        h *= -0x7a143595
        h = h xor (h ushr 13)
        h *= -0x3d4d51cb
        h = h xor (h ushr 16)
        return h and 0x7FFFFFFF
    }
}
