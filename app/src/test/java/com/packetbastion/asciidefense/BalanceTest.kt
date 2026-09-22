package com.packetbastion.asciidefense

import com.packetbastion.asciidefense.core.Balance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards on the difficulty curve. These are not "does the code run" tests — they
 * assert the *shape* of the progression, which is the thing that quietly breaks
 * when someone tweaks a constant.
 */
class BalanceTest {

    @Test
    fun `boss waves land on every fifth wave`() {
        for (wave in 1..60) {
            val expected = wave % 5 == 0
            assertEquals("wave $wave", expected, Balance.isBossWave(wave))
        }
        assertFalse(Balance.isBossWave(0))
        assertTrue(Balance.isBossWave(5))
        assertTrue(Balance.isBossWave(10))
    }

    @Test
    fun `health scaling rises monotonically and stays gentle early`() {
        var previous = 0.0
        for (wave in 1..100) {
            val multiplier = Balance.healthMultiplier(wave)
            assertTrue("wave $wave must scale upward", multiplier > previous)
            previous = multiplier
        }

        // Wave 10 must not be a wall relative to wave 9.
        val nine = Balance.healthMultiplier(9)
        val ten = Balance.healthMultiplier(10)
        assertTrue("wave 10 jump too large", ten / nine < 1.15)

        // And the early game must stay approachable.
        assertTrue(Balance.healthMultiplier(1) < 1.15)
        assertTrue(Balance.healthMultiplier(5) < 1.6)
        assertTrue(Balance.healthMultiplier(10) < 2.2)
    }

    @Test
    fun `speed scaling is capped so late packets stay readable`() {
        for (wave in 1..500) {
            assertTrue(Balance.speedMultiplier(wave) <= Balance.SPEED_MAX_MULTIPLIER + 1e-6)
        }
        assertEquals(
            Balance.SPEED_MAX_MULTIPLIER,
            Balance.speedMultiplier(1000),
            1e-6
        )
    }

    @Test
    fun `enemy count grows but never exceeds the render cap`() {
        var previous = 0
        for (wave in 1..200) {
            val count = Balance.waveEnemyCount(wave)
            assertTrue("wave $wave: $count over cap", count <= Balance.MAX_WAVE_ENEMIES)
            assertTrue("wave $wave must not shrink", count >= previous)
            previous = count
        }
        assertTrue("wave 1 should be a gentle introduction", Balance.waveEnemyCount(1) <= 10)
    }

    @Test
    fun `spawn interval tightens but never hits zero`() {
        for (wave in 1..300) {
            val interval = Balance.spawnInterval(wave)
            assertTrue("wave $wave interval $interval", interval >= 0.34f - 1e-4f)
        }
        assertTrue(Balance.spawnInterval(1) > Balance.spawnInterval(30))
    }

    @Test
    fun `elite chance stays within bounds and starts at zero`() {
        assertEquals(0f, Balance.eliteChance(1), 1e-6f)
        assertEquals(0f, Balance.eliteChance(4), 1e-6f)
        assertTrue(Balance.eliteChance(10) > 0f)
        for (wave in 1..500) {
            val chance = Balance.eliteChance(wave)
            assertTrue(chance in 0f..0.32f)
        }
    }

    @Test
    fun `rewards grow far slower than enemy health`() {
        // The economy must not outrun difficulty, or the late game trivialises.
        val healthGrowth = Balance.healthMultiplier(50) / Balance.healthMultiplier(1)
        val rewardGrowth = Balance.rewardMultiplier(50) / Balance.rewardMultiplier(1)
        assertTrue(
            "rewards ($rewardGrowth) must lag health ($healthGrowth)",
            rewardGrowth < healthGrowth
        )
    }

    @Test
    fun `upgrade costs rise with level`() {
        var previous = 0
        for (level in 1 until Balance.MAX_AGENT_LEVEL) {
            val cost = Balance.upgradeCost(100, level)
            assertTrue("level $level cost $cost", cost > previous)
            previous = cost
        }
    }

    @Test
    fun `selling always refunds less than was invested`() {
        for (level in 1..Balance.MAX_AGENT_LEVEL) {
            val invested = Balance.investedCrypto(100, level)
            val refund = Balance.sellValue(100, level)
            assertTrue("level $level: $refund vs $invested", refund < invested)
            assertTrue(refund > 0)
        }
    }

    @Test
    fun `boss cycles map waves to escalating difficulty`() {
        assertEquals(1, Balance.bossCycle(5))
        assertEquals(2, Balance.bossCycle(10))
        assertEquals(6, Balance.bossCycle(30))
        assertTrue(Balance.bossHealthMultiplier(10) > Balance.bossHealthMultiplier(5))
        assertTrue(Balance.bossHealthMultiplier(25) > Balance.bossHealthMultiplier(20))
    }
}
