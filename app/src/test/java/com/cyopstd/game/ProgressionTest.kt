package com.cyopstd.game

import com.cyopstd.game.core.Balance
import com.cyopstd.game.core.Maps
import com.cyopstd.game.core.WorldGeometry
import com.cyopstd.game.engine.GameEngine
import com.cyopstd.game.engine.RunPhase
import com.cyopstd.game.engine.WaveGenerator
import com.cyopstd.game.model.AgentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The progression systems: hundred-level agents, the boss payout that keeps a
 * run solvent, and the € BUDGET / CORE FIRMWARE meta-loop that carries strength
 * between runs.
 */
class ProgressionTest {

    private fun newEngine(seed: Int = 7): GameEngine {
        val random = Random(seed)
        val engine = GameEngine(random, WaveGenerator(random))
        engine.isAgentUnlocked = { true }
        engine.startNewRun()
        return engine
    }

    /** Distance along [lane] whose world position is nearest to a point. */
    private fun closestProgressTo(x: Float, y: Float, lane: Int): Float {
        val scratch = FloatArray(3)
        var bestProgress = 0f
        var bestDistance = Float.MAX_VALUE
        var progress = 0f
        val length = Maps.PERIMETER.laneLength[lane]
        while (progress <= length) {
            Maps.PERIMETER.positionAt(lane, progress, scratch)
            val d = kotlin.math.hypot(scratch[0] - x, scratch[1] - y)
            if (d < bestDistance) {
                bestDistance = d
                bestProgress = progress
            }
            progress += 4f
        }
        return bestProgress
    }

    private fun GameEngine.runWaveToCompletion(budgetSeconds: Float = 300f): Boolean {
        var elapsed = 0f
        while (elapsed < budgetSeconds) {
            update(0.02f, 1f)
            elapsed += 0.02f
            if (phase == RunPhase.PREPARING || phase == RunPhase.GAME_OVER) return true
        }
        return false
    }

    // ------------------------------------------------- hundred-level agents

    @Test
    fun `agents upgrade all the way to level 100`() {
        assertEquals(100, Balance.MAX_AGENT_LEVEL)

        val engine = newEngine()
        engine.placeAgent(AgentType.FIREWALL, 5)
        engine.addCrypto(5_000_000, countAsEarned = false)

        val bought = engine.upgradeAgent(5, Balance.MAX_AGENT_LEVEL)

        assertEquals(Balance.MAX_AGENT_LEVEL - 1, bought)
        assertEquals(Balance.MAX_AGENT_LEVEL, engine.agentAt(5)!!.level)
        assertEquals(
            "a maxed agent cannot be upgraded further",
            0, engine.upgradeAgent(5, 10)
        )
    }

    @Test
    fun `every level raises damage and the climb is worth making`() {
        val type = AgentType.FIREWALL
        var previous = 0f
        for (level in 1..Balance.MAX_AGENT_LEVEL) {
            val damage = type.statsAtLevel(level).damage
            assertTrue("level $level must beat level ${level - 1}", damage > previous)
            previous = damage
        }

        val l1 = type.statsAtLevel(1).damage
        val l100 = type.statsAtLevel(100).damage
        assertTrue("level 100 should be a transformation, not a nudge", l100 > l1 * 15f)
    }

    @Test
    fun `range is the one stat that is capped`() {
        val type = AgentType.IDS
        val maxRange = type.statsAtLevel(Balance.MAX_AGENT_LEVEL).range
        assertTrue(
            "unbounded range would make node placement irrelevant",
            maxRange <= type.baseRange * Balance.UPGRADE_RANGE_CAP + 0.01f
        )
    }

    @Test
    fun `bulk upgrading stops when the crypto runs out`() {
        val engine = newEngine()
        engine.placeAgent(AgentType.FIREWALL, 6)
        engine.removeCrypto(engine.crypto)
        engine.addCrypto(60, countAsEarned = false)

        val affordable = engine.affordableUpgrades(6)
        val bought = engine.upgradeAgent(6, 50)

        assertEquals("MAX must buy exactly what was affordable", affordable, bought)
        assertTrue("it should buy more than one level", bought > 1)
        assertTrue("and it must not overspend", engine.crypto >= 0)
        assertEquals(0, engine.affordableUpgrades(6))
    }

    @Test
    fun `early upgrades are cheap enough to actually buy`() {
        // The original curve priced the first upgrade above a second agent,
        // which is why a real run reached wave 9 with everything still level 1.
        val firewall = AgentType.FIREWALL
        assertTrue(
            "the first upgrade must cost well under a new deployment",
            firewall.upgradeCost(1) < firewall.cost / 2
        )
        assertTrue(Balance.STARTING_CRYPTO >= firewall.cost * 2)
    }

    // --------------------------------------------------------- boss payouts

    @Test
    fun `boss waves pay a bonus that starts at 20 and climbs each cycle`() {
        assertEquals(0, Balance.bossClearBonus(4))
        assertEquals(0, Balance.bossClearBonus(7))

        val first = Balance.bossClearBonus(5)
        assertTrue("the first boss should pay about 20", first >= 20)

        var previous = 0
        for (wave in intArrayOf(5, 10, 15, 20, 30, 50)) {
            val bonus = Balance.bossClearBonus(wave)
            assertTrue("wave $wave bonus $bonus must climb", bonus > previous)
            previous = bonus
        }
    }

    @Test
    fun `clearing a boss wave actually pays the bonus into the run`() {
        val engine = newEngine(seed = 3)
        engine.addCrypto(200_000, countAsEarned = false)
        for (node in intArrayOf(0, 8, 16, 24, 1, 9, 17, 25)) {
            engine.placeAgent(AgentType.ANALYST, node)
            engine.upgradeAgent(node, 40)
        }

        // Walk to the wave before the first boss.
        while (engine.currentWave < 4) {
            engine.startNextWave()
            engine.runWaveToCompletion()
        }

        val before = engine.crypto
        engine.startNextWave()
        assertTrue("wave 5 is a boss wave", engine.isBossWave())
        engine.runWaveToCompletion()

        val gained = engine.crypto - before
        val expectedFloor =
            Balance.waveClearBonus(5) + Balance.bossClearBonus(5)
        assertTrue(
            "a cleared boss wave should pay at least its bonuses (got $gained)",
            gained >= expectedFloor
        )
    }

    // --------------------------------------------------- budget milestones

    @Test
    fun `budget is banked at every tenth wave and nowhere else`() {
        for (wave in 1..60) {
            val award = Balance.budgetAward(wave)
            if (wave % 10 == 0) {
                assertTrue("wave $wave should pay budget", award > 0)
            } else {
                assertEquals("wave $wave must pay nothing", 0, award)
            }
        }
    }

    @Test
    fun `deeper milestones are worth disproportionately more`() {
        val at10 = Balance.budgetAward(10)
        val at20 = Balance.budgetAward(20)
        val at50 = Balance.budgetAward(50)

        assertTrue(at20 > at10 * 2)
        assertTrue(
            "one run to wave 50 should beat five runs to wave 10",
            at50 > at10 * 5
        )
    }

    @Test
    fun `the engine raises the budget callback on a tenth wave`() {
        val engine = newEngine(seed = 9)
        val awards = mutableListOf<Pair<Int, Int>>()
        engine.onBudgetEarned = { amount, wave -> awards += amount to wave }

        engine.addCrypto(500_000, countAsEarned = false)
        for (node in 0 until 16) {
            engine.placeAgent(AgentType.ANALYST, node)
            engine.upgradeAgent(node, 60)
        }

        repeat(10) {
            if (engine.phase == RunPhase.GAME_OVER) return@repeat
            engine.startNextWave()
            engine.runWaveToCompletion()
        }

        assertTrue("wave 10 should have banked budget", awards.isNotEmpty())
        assertEquals(10, awards.first().second)
        assertEquals(Balance.budgetAward(10), awards.first().first)
        assertEquals(engine.runBudgetEarned, awards.sumOf { it.first })
    }

    // -------------------------------------------------------- core firmware

    @Test
    fun `firmware multiplies damage and starts at exactly one`() {
        assertEquals(1f, Balance.firmwareDamageMultiplier(0), 0.0001f)
        assertTrue(Balance.firmwareDamageMultiplier(1) > 1f)

        var previous = 0f
        for (level in 0..500) {
            val multiplier = Balance.firmwareDamageMultiplier(level)
            assertTrue(multiplier > previous)
            previous = multiplier
        }

        // The nominal ceiling is 10,000 levels; past that it must not keep going.
        assertEquals(
            Balance.firmwareDamageMultiplier(Balance.MAX_FIRMWARE_LEVEL),
            Balance.firmwareDamageMultiplier(Balance.MAX_FIRMWARE_LEVEL + 5_000),
            0.0001f
        )
    }

    @Test
    fun `firmware costs rise so the multiplier never runs away`() {
        var previous = 0
        for (level in 0..1000) {
            val cost = Balance.firmwareCost(level)
            assertTrue("level $level cost $cost", cost >= previous)
            previous = cost
        }
        assertTrue(Balance.firmwareCost(1000) > Balance.firmwareCost(10) * 10)
    }

    @Test
    fun `affordability maths agrees with what the levels actually cost`() {
        val budget = 500L
        val levels = Balance.firmwareLevelsAffordable(fromLevel = 0, budget = budget)
        val spend = Balance.firmwareCostFor(fromLevel = 0, steps = levels)

        assertTrue("must not promise more than the budget covers", spend <= budget)
        val oneMore = Balance.firmwareCostFor(fromLevel = 0, steps = levels + 1)
        assertTrue("and must not leave an affordable level unbought", oneMore > budget)
    }

    @Test
    fun `firmware raises the damage every agent actually deals`() {
        // Measured per shot, not per run: with firmware the packets die sooner,
        // so the agent spends part of the window with nothing to shoot and
        // total output stops being a fair comparison.
        fun damagePerShot(firmware: Int): Float {
            val engine = newEngine(seed = 5)
            engine.firmwareDamageMultiplier = Balance.firmwareDamageMultiplier(firmware)
            engine.addCrypto(10_000, countAsEarned = false)
            val node = Maps.PERIMETER.nodesByCoverage.first()
            engine.placeAgent(AgentType.FIREWALL, node.id)
            val agent = engine.agentAt(node.id)!!

            // A target too tough to die, parked inside the agent's range.
            // Position comes from route progress, not x/y: the mover recomputes
            // coordinates from progress every step, so assigning them directly
            // would not survive one tick. On a serpentine route progress is not
            // proportional to x either, so the closest point is searched for
            // rather than assumed.
            val dummy = engine.enemies.obtain()!!
            dummy.reset()
            dummy.active = true
            dummy.lane = 0
            dummy.progress = closestProgressTo(agent.x, agent.y, lane = 0)
            dummy.baseSpeed = 0f
            dummy.health = 1_000_000f
            dummy.maxHealth = 1_000_000f

            var elapsed = 0f
            while (elapsed < 5f) {
                engine.update(0.02f, 1f)
                elapsed += 0.02f
                val shot = engine.projectiles.items.firstOrNull { it.active }
                if (shot != null) return shot.damage
            }
            return 0f
        }

        val without = damagePerShot(0)
        val with500 = damagePerShot(500)

        assertTrue("the control shot must exist", without > 0f)
        assertTrue("firmware must do something", with500 > without)

        // Level 500 is +250%, so each shot should hit for about 3.5x.
        val expected = Balance.firmwareDamageMultiplier(500)
        assertEquals(
            "each shot should scale by exactly the firmware multiplier",
            expected, with500 / without, 0.05f
        )
    }

    @Test
    fun `a fresh run carries no firmware until it is bought`() {
        val engine = newEngine()
        assertEquals(
            "firmware is meta-progression, not a starting bonus",
            1f, engine.firmwareDamageMultiplier, 0.0001f
        )
        assertEquals(0, engine.runBudgetEarned)
    }

    @Test
    fun `difficulty was actually reduced, not merely declared`() {
        // A run reaching wave 9 with everything stuck at level 1 was the report
        // that prompted this. These assert the curve is genuinely gentler.
        assertTrue("wave 9 health scaling", Balance.healthMultiplier(9) < 1.75)
        assertTrue("wave 20 health scaling", Balance.healthMultiplier(20) < 2.7)
        assertTrue("wave 9 should send fewer packets", Balance.waveEnemyCount(9) <= 17)
        assertTrue("no elites before wave 7", Balance.eliteChance(6) == 0f)
        assertFalse("armour should not appear immediately", Balance.waveArmorBonus(9) > 0.0)
    }
}
