package com.cyopstd.game

import com.cyopstd.game.engine.GameEngine
import com.cyopstd.game.engine.PlacementResult
import com.cyopstd.game.model.AgentType
import com.cyopstd.game.model.BossVariant
import com.cyopstd.game.model.COUNTER_MULTIPLIER
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RED HAT and BLUE HAT (D2), and the boss counters they carry (C2).
 *
 * These exist because of a specific, tested complaint: *"wave 101 is
 * impossible with the best build on the map"*. The cause is structural rather
 * than numerical — every other agent in the roster picks its target by
 * position along the lane, so when four bosses arrive inside a swarm, the
 * swarm is what gets shot. No amount of damage fixes a targeting problem,
 * which is why the fix is an agent that ignores position entirely.
 */
class HatAgentTest {

    private val hats = listOf(AgentType.REDHAT, AgentType.BLUEHAT)

    // ------------------------------------------------------- the spec itself

    @Test
    fun `both hats match the owner's specification exactly`() {
        for (hat in hats) {
            assertEquals("${hat.displayName} cost", 400, hat.cost)
            assertEquals("${hat.displayName} unlock wave", 30, hat.unlockWave)
            assertEquals("${hat.displayName} deployment cap", 4, hat.maxDeployed)
            assertTrue("${hat.displayName} must always target bosses", hat.alwaysPrioritisesBosses)
        }
    }

    @Test
    fun `the hats fire as fast as the fastest agent in the roster`() {
        // "as high fire rate as the highest available now". Computed rather
        // than hard-coded, so if a future agent is ever made faster this fails
        // and somebody has to decide, rather than the hats quietly becoming
        // second best at the one thing they are for.
        val fastestOther = AgentType.entries
            .filter { it !in hats }
            .maxOf { it.baseFireRate }
        for (hat in hats) {
            assertEquals(
                "${hat.displayName} should match the roster's fastest rate",
                fastestOther,
                hat.baseFireRate,
                0.001f
            )
        }
    }

    @Test
    fun `no other agent targets bosses unconditionally`() {
        // The hats' identity. If a second agent gains it, the cap of four
        // stops being the limit on boss-focused damage.
        val others = AgentType.entries.filter { it !in hats && it.alwaysPrioritisesBosses }
        assertTrue("$others also force boss targeting", others.isEmpty())
    }

    // ------------------------------------------------------- C2, the counters

    @Test
    fun `each hat doubles its damage against the boss it counters`() {
        assertEquals(2f, COUNTER_MULTIPLIER, 0.001f)
        assertEquals(
            "RED HAT must counter [GG] GOOD GAME",
            COUNTER_MULTIPLIER,
            BossVariant.GOOD_GAME.bonusDamageFrom["REDHAT"]
        )
        assertEquals(
            "BLUE HAT must counter [!!!] BREACH",
            COUNTER_MULTIPLIER,
            BossVariant.BREACH.bonusDamageFrom["BLUEHAT"]
        )
    }

    @Test
    fun `a counter is specific - the wrong hat gets no bonus`() {
        assertEquals(null, BossVariant.GOOD_GAME.bonusDamageFrom["BLUEHAT"])
        assertEquals(null, BossVariant.BREACH.bonusDamageFrom["REDHAT"])
        // ...and no ordinary agent is silently carrying one.
        for (variant in BossVariant.entries) {
            for (key in variant.bonusDamageFrom.keys) {
                assertTrue(
                    "${variant.displayName} grants a bonus to '$key', which is not a hat",
                    key == "REDHAT" || key == "BLUEHAT"
                )
            }
        }
    }

    @Test
    fun `the counter table names agents that actually exist`() {
        // Keyed by name to avoid enum-init circularity, which means a typo
        // would silently disable the counter rather than fail to compile.
        val names = AgentType.entries.map { it.name }.toSet()
        for (variant in BossVariant.entries) {
            for (key in variant.bonusDamageFrom.keys) {
                assertTrue("'$key' is not an AgentType", key in names)
            }
        }
    }

    // -------------------------------------------------------------- the cap

    @Test
    fun `a fifth hat cannot be deployed`() {
        val engine = GameEngine()
        // The engine gates placement on the player's unlock set, which
        // defaults to "only what is unlocked from wave 0". The hats unlock at
        // wave 30, so without this every placement below is refused as
        // AGENT_LOCKED and the cap is never reached -- which is exactly how
        // the first version of this test "proved" the cap worked.
        engine.isAgentUnlocked = { true }
        engine.startNewRun()
        engine.restore(
            wave = 30, serverHp = 100, crypto = 99_999, placements = emptyList(),
            attacksBlocked = 0, cryptoEarned = 0, bossesDefeated = 0,
            serverDamageTaken = 0, agentsDeployed = 0, agentUpgrades = 0
        )

        val nodes = engine.map.nodes
        var placed = 0
        var refusal: PlacementResult? = null
        for (node in nodes) {
            val result = engine.placeAgent(AgentType.REDHAT, node.id)
            if (result == PlacementResult.SUCCESS) {
                placed++
            } else if (result == PlacementResult.TYPE_LIMIT_REACHED) {
                refusal = result
                break
            }
        }

        assertEquals("exactly four RED HATs should fit", 4, placed)
        assertEquals(
            "the fifth must be refused for the right reason",
            PlacementResult.TYPE_LIMIT_REACHED,
            refusal
        )
        assertEquals(4, engine.activeCountOf(AgentType.REDHAT))
    }

    @Test
    fun `the two caps are independent`() {
        // Four red and four blue, not four between them.
        val engine = GameEngine()
        // The engine gates placement on the player's unlock set, which
        // defaults to "only what is unlocked from wave 0". The hats unlock at
        // wave 30, so without this every placement below is refused as
        // AGENT_LOCKED and the cap is never reached -- which is exactly how
        // the first version of this test "proved" the cap worked.
        engine.isAgentUnlocked = { true }
        engine.startNewRun()
        engine.restore(
            wave = 30, serverHp = 100, crypto = 99_999, placements = emptyList(),
            attacksBlocked = 0, cryptoEarned = 0, bossesDefeated = 0,
            serverDamageTaken = 0, agentsDeployed = 0, agentUpgrades = 0
        )
        var red = 0
        var blue = 0
        for (node in engine.map.nodes) {
            val type = if (red <= blue) AgentType.REDHAT else AgentType.BLUEHAT
            when (engine.placeAgent(type, node.id)) {
                PlacementResult.SUCCESS -> if (type == AgentType.REDHAT) red++ else blue++
                else -> {}
            }
            if (red == 4 && blue == 4) break
        }
        assertEquals(4, engine.activeCountOf(AgentType.REDHAT))
        assertEquals(4, engine.activeCountOf(AgentType.BLUEHAT))
    }

    @Test
    fun `an uncapped agent is still uncapped`() {
        // maxDeployed = 0 must mean "no limit", not "zero allowed" -- an easy
        // and total way to break every other agent in the game.
        assertEquals(0, AgentType.FIREWALL.maxDeployed)
        val engine = GameEngine()
        // The engine gates placement on the player's unlock set, which
        // defaults to "only what is unlocked from wave 0". The hats unlock at
        // wave 30, so without this every placement below is refused as
        // AGENT_LOCKED and the cap is never reached -- which is exactly how
        // the first version of this test "proved" the cap worked.
        engine.isAgentUnlocked = { true }
        engine.startNewRun()
        engine.restore(
            wave = 30, serverHp = 100, crypto = 99_999, placements = emptyList(),
            attacksBlocked = 0, cryptoEarned = 0, bossesDefeated = 0,
            serverDamageTaken = 0, agentsDeployed = 0, agentUpgrades = 0
        )
        var placed = 0
        for (node in engine.map.nodes.take(12)) {
            if (engine.placeAgent(AgentType.FIREWALL, node.id) == PlacementResult.SUCCESS) placed++
        }
        assertTrue("an uncapped agent was capped after $placed", placed > 4)
    }

    @Test
    fun `the hats are not available before wave 30`() {
        for (hat in hats) {
            assertFalse("${hat.displayName} must not be unlocked by default", hat.unlockedByDefault)
        }
    }
}
