package com.cyopstd.game

import com.cyopstd.game.core.Balance
import com.cyopstd.game.engine.GameEngine
import com.cyopstd.game.engine.WaveGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * ☆1 (owner, 2026-09-28): "amount of € earned after every boss wave needs to
 * scale exponentially after wave 100", and the € earned this run is shown.
 */
class BudgetAfterHundredTest {

    @Test
    fun `up to wave 100 nothing changes`() {
        for (wave in 1..100) {
            val m = wave / 10
            assertEquals("wave $wave", if (wave % 10 == 0) 50 * m * m else 0, Balance.budgetAward(wave))
        }
    }

    @Test
    fun `past 100 every boss wave pays, and each pays more than the last by a fixed factor`() {
        var last = Balance.budgetAward(100)
        for (wave in 101..400) {
            val award = Balance.budgetAward(wave)
            if (Balance.isBossWave(wave)) {
                assertTrue("wave $wave pays nothing", award > 0)
                assertEquals("wave $wave", last * Balance.BUDGET_EXPONENTIAL_GROWTH, award.toDouble(), last * 0.001 + 1)
                last = award
            } else {
                assertEquals("wave $wave must pay nothing", 0, award)
            }
        }
        println("€ per boss wave: 105=${Balance.budgetAward(105)}, 150=${Balance.budgetAward(150)}, " +
            "200=${Balance.budgetAward(200)}, 300=${Balance.budgetAward(300)}")
        // Exponential, not polynomial: wave 300 pays far more than the old
        // square curve would have (50 * 30^2 = 45,000).
        assertTrue(Balance.budgetAward(300) > 400_000)
        // And it never overflows.
        assertEquals(Balance.BUDGET_AWARD_CAP, Balance.budgetAward(5_000))
    }

    @Test
    fun `the run's € survives a resume`() {
        val engine = GameEngine(Random(1), WaveGenerator(Random(1)))
        engine.startNewRun()
        engine.restore(
            wave = 120, serverHp = 100, crypto = 0, placements = emptyList(),
            attacksBlocked = 0, cryptoEarned = 0, bossesDefeated = 0,
            serverDamageTaken = 0, agentsDeployed = 0, agentUpgrades = 0,
            budgetEarned = 42_000
        )
        assertEquals(42_000, engine.runBudgetEarned)
    }
}
