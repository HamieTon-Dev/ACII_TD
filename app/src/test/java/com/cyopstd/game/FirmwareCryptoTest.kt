package com.cyopstd.game

import com.cyopstd.game.core.Balance
import com.cyopstd.game.engine.GameEngine
import org.junit.Assert.assertEquals
import org.junit.Test

/** Firmware also raises crypto earned in runs (owner, 2026-09-26). */
class FirmwareCryptoTest {

    @Test
    fun `the crypto bonus scales with firmware level and caps at double`() {
        assertEquals(1f, Balance.firmwareCryptoMultiplier(0), 0.0001f)
        assertEquals(1.25f, Balance.firmwareCryptoMultiplier(100), 0.0001f)
        assertEquals(2f, Balance.firmwareCryptoMultiplier(400), 0.0001f)
        assertEquals("capped at double", 2f, Balance.firmwareCryptoMultiplier(5_000), 0.0001f)
    }

    @Test
    fun `earnings are multiplied and refunds are not`() {
        val engine = GameEngine()
        engine.startNewRun()
        engine.firmwareCryptoMultiplier = Balance.firmwareCryptoMultiplier(100)
        val start = engine.crypto

        val earned = engine.economySystem().award(100)
        assertEquals(125, earned)
        assertEquals(start + 125, engine.crypto)

        val refunded = engine.economySystem().award(100, countAsEarned = false)
        assertEquals("a sell refund must not be boosted", 100, refunded)
        assertEquals(start + 225, engine.crypto)
    }

    @Test
    fun `with no firmware nothing changes`() {
        val engine = GameEngine()
        engine.startNewRun()
        assertEquals(37, engine.economySystem().award(37))
    }
}
