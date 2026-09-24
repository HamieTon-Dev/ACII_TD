package com.cyopstd.game

import com.cyopstd.game.ui.menu.MenuBoot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The main menu powering on.
 *
 * Four beats were asked for — dark, an LED catching, the interface flickering
 * in, then settled — and each is checked separately, because each fails in a
 * way that looks like a different bug. A missing dark beat reads as a slow
 * frame. An LED that ramps instead of overshooting reads as a fade. A flicker
 * that is not seeded reads as a rendering fault. And a settle that never
 * arrives leaves a player unsure whether their tap will land.
 */
class MenuBootTest {

    @Test
    fun `it starts in the dark`() {
        // Not "starts dim". A room with the lights off.
        assertEquals(0f, MenuBoot.ledGlow(0f), 0.0001f)
        for (index in 0 until MenuBoot.ELEMENTS) {
            assertEquals(
                "element $index was already lit at t=0",
                0f,
                MenuBoot.elementAlpha(index, 0f),
                0.0001f
            )
        }
        // And stays dark for long enough to register as deliberate.
        val justBefore = MenuBoot.DARK_SECONDS - 0.01f
        assertEquals(0f, MenuBoot.ledGlow(justBefore), 0.0001f)
        assertTrue("the dark beat is too short to read", MenuBoot.DARK_SECONDS > 0.15f)
    }

    @Test
    fun `the LED comes up before any of the interface does`() {
        // The order is the whole idea: one light, then the machine behind it.
        val duringLed = MenuBoot.DARK_SECONDS + MenuBoot.LED_SECONDS * 0.5f
        assertTrue("the LED is not lit yet", MenuBoot.ledGlow(duringLed) > 0.3f)
        for (index in 0 until MenuBoot.ELEMENTS) {
            assertEquals(
                "element $index arrived while the LED was still coming up",
                0f,
                MenuBoot.elementAlpha(index, duringLed),
                0.0001f
            )
        }
    }

    @Test
    fun `the LED overshoots rather than ramping`() {
        // A linear fade up is what every app does; hardware does not. The
        // overshoot is most of what reads as electrical, so it is asserted
        // rather than left as an intention in a comment.
        var peak = 0f
        var t = MenuBoot.DARK_SECONDS
        while (t < MenuBoot.DARK_SECONDS + MenuBoot.LED_SECONDS) {
            peak = maxOf(peak, MenuBoot.ledGlow(t))
            t += 1f / 240f
        }
        assertTrue("the LED peaked at $peak, which is a ramp, not a surge", peak > 1.05f)

        val settled = MenuBoot.ledGlow(MenuBoot.DARK_SECONDS + MenuBoot.LED_SECONDS)
        assertTrue("the LED never came back down from $peak", settled < peak * 0.95f)
    }

    @Test
    fun `the interface arrives out of order`() {
        // Everything arriving together is a fade. The scatter is the effect.
        val turnOn = (0 until MenuBoot.ELEMENTS).map { index ->
            var t = 0f
            while (t < MenuBoot.TOTAL_SECONDS && MenuBoot.elementAlpha(index, t) <= 0f) {
                t += 1f / 240f
            }
            t
        }
        assertTrue(
            "every element turned on at the same moment ($turnOn)",
            turnOn.distinct().size > MenuBoot.ELEMENTS / 2
        )
        assertTrue(
            "the elements arrive in index order, which is a stagger, not a scatter",
            turnOn != turnOn.sorted()
        )
    }

    @Test
    fun `elements flicker after they arrive rather than simply appearing`() {
        // A cold fluorescent does not come on cleanly. At least some elements
        // have to drop out again before they hold.
        var flickered = 0
        for (index in 0 until MenuBoot.ELEMENTS) {
            var sawLit = false
            var droppedAfterLit = false
            var t = 0f
            while (t < MenuBoot.TOTAL_SECONDS) {
                val alpha = MenuBoot.elementAlpha(index, t)
                if (alpha >= 1f) sawLit = true
                if (sawLit && alpha < 1f) droppedAfterLit = true
                t += 1f / 240f
            }
            if (droppedAfterLit) flickered++
        }
        assertTrue(
            "only $flickered of ${MenuBoot.ELEMENTS} elements flickered; the rest " +
                "just faded in",
            flickered >= MenuBoot.ELEMENTS / 3
        )
    }

    @Test
    fun `the same launch happens every launch`() {
        // A menu that boots differently each time reads as a bug, not as a
        // feature. Same reason the shard bursts are seeded.
        for (index in 0 until MenuBoot.ELEMENTS) {
            var t = 0f
            while (t < MenuBoot.TOTAL_SECONDS) {
                assertEquals(
                    MenuBoot.elementAlpha(index, t),
                    MenuBoot.elementAlpha(index, t),
                    0f
                )
                t += 0.01f
            }
        }
        // And different elements genuinely get different patterns, rather
        // than the hash collapsing and giving everything the same one.
        val signatures = (0 until MenuBoot.ELEMENTS).map { index ->
            (0..40).map { MenuBoot.elementAlpha(index, it * 0.03f) }
        }
        assertEquals(
            "some elements share a flicker pattern",
            MenuBoot.ELEMENTS,
            signatures.distinct().size
        )
    }

    @Test
    fun `it settles, and stays settled`() {
        // The beat a player needs most: it has to be unmistakable that the
        // menu is ready, or they will not know whether a tap will land.
        assertTrue(MenuBoot.isSettled(MenuBoot.TOTAL_SECONDS))
        for (index in 0 until MenuBoot.ELEMENTS) {
            for (t in listOf(MenuBoot.TOTAL_SECONDS, MenuBoot.TOTAL_SECONDS + 5f)) {
                assertEquals(
                    "element $index is still moving at ${t}s",
                    1f,
                    MenuBoot.elementAlpha(index, t),
                    0.0001f
                )
            }
        }
        assertEquals(
            "the LED is still burning after the menu is ready",
            0f,
            MenuBoot.ledGlow(MenuBoot.TOTAL_SECONDS + 1f),
            0.05f
        )
    }

    @Test
    fun `nothing is left half-lit when the boot ends`() {
        // Every element has to resolve on its own, not get cut off mid-flicker
        // by the deadline -- otherwise the last frame of the boot snaps.
        val justBefore = MenuBoot.TOTAL_SECONDS - 1f / 60f
        for (index in 0 until MenuBoot.ELEMENTS) {
            assertEquals(
                "element $index was still flickering one frame before the end",
                1f,
                MenuBoot.elementAlpha(index, justBefore),
                0.0001f
            )
        }
    }

    @Test
    fun `it is short enough to sit in front of a menu`() {
        // Start-up is already several seconds of ident and boot screen before
        // this runs. A power-on that outstays its welcome is the first thing a
        // player resents about the game.
        assertTrue(
            "the boot runs for ${MenuBoot.TOTAL_SECONDS} seconds",
            MenuBoot.TOTAL_SECONDS < 2f
        )
    }

    @Test
    fun `the beats are in the order they were asked for`() {
        assertNotEquals(0f, MenuBoot.DARK_SECONDS)
        assertEquals(
            MenuBoot.TOTAL_SECONDS,
            MenuBoot.DARK_SECONDS + MenuBoot.LED_SECONDS +
                MenuBoot.FLICKER_SECONDS + MenuBoot.SETTLE_SECONDS,
            0.0001f
        )
    }
}
