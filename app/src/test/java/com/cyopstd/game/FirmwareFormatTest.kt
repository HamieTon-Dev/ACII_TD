package com.cyopstd.game

import com.cyopstd.game.core.Balance
import com.cyopstd.game.ui.common.FirmwareFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The firmware readout used to round a real purchase away: at two decimals,
 * buying level 1 moved "×1.00" to "×1.00". These tests state the promise the
 * formatting now has to keep — **every level you buy visibly changes the
 * number on screen** — so nobody can quietly widen the rounding again.
 */
class FirmwareFormatTest {

    @Test
    fun `every single level changes the multiplier on screen`() {
        // The complaint that started this: one purchase, no visible change.
        assertNotEquals(FirmwareFormat.multiplier(0), FirmwareFormat.multiplier(1))
        for (level in 0 until 400) {
            assertNotEquals(
                "level $level and ${level + 1} print the same",
                FirmwareFormat.multiplier(level),
                FirmwareFormat.multiplier(level + 1)
            )
        }
    }

    @Test
    fun `the first few levels read exactly as the maths says`() {
        assertEquals("×1.000", FirmwareFormat.multiplier(0))
        assertEquals("×1.005", FirmwareFormat.multiplier(1))
        assertEquals("×1.010", FirmwareFormat.multiplier(2))
        assertEquals("×1.050", FirmwareFormat.multiplier(10))
        assertEquals("×1.500", FirmwareFormat.multiplier(100))
    }

    @Test
    fun `the per-level figure is never rounded down to nothing`() {
        // It used to be (0.005 * 100).toInt() -- literally "+0%", which told
        // the player an upgrade does nothing.
        assertEquals("+0.5%", FirmwareFormat.perLevel())
        assertNotEquals("+0%", FirmwareFormat.perLevel())
    }

    @Test
    fun `a bulk purchase states what it is actually buying`() {
        assertEquals("+0.5%", FirmwareFormat.gain(1))
        assertEquals("+5%", FirmwareFormat.gain(10))
        assertEquals("+2.5%", FirmwareFormat.gain(5))
        assertEquals("+50%", FirmwareFormat.gain(100))
    }

    @Test
    fun `the printed multiplier matches the damage actually dealt`() {
        // A readout that drifts from the engine is worse than no readout.
        for (level in intArrayOf(0, 1, 7, 50, 250, 1_000)) {
            val printed = FirmwareFormat.multiplier(level).removePrefix("×").toFloat()
            val real = Balance.firmwareDamageMultiplier(level)
            assertTrue(
                "level $level prints $printed but deals $real",
                kotlin.math.abs(printed - real) < 0.0005f
            )
        }
    }
}
