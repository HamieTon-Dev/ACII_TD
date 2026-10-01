package com.cyopstd.game

import com.cyopstd.game.core.Balance
import com.cyopstd.game.core.Maps
import com.cyopstd.game.engine.GameEngine
import com.cyopstd.game.engine.PlacementResult
import com.cyopstd.game.engine.SpawnOrder
import com.cyopstd.game.engine.WaveGenerator
import com.cyopstd.game.model.AgentType
import com.cyopstd.game.model.BossVariant
import com.cyopstd.game.model.EnemyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * CYBER OPERATIVE `[>_<]` (backlog ♡3): wave 100 on HUGGING-FACE, two
 * maximum, TARPIT's range, protects agents in range from jamming, and hits
 * WHITE EYE and BLACK EYE hard and fast.
 */
class CyberOperativeTest {

    private val op = AgentType.CYBER_OPERATIVE

    private fun engine(): GameEngine {
        val random = Random(1)
        return GameEngine(random, WaveGenerator(random)).apply {
            isAgentUnlocked = { true }
            selectMap(Maps.HUGGING_FACE)
            startNewRun()
            addCrypto(1_000_000, countAsEarned = false)
        }
    }

    @Test
    fun `matches the request`() {
        assertEquals("[>_<]", op.renderedGlyph(1))
        assertEquals(AgentType.TARPIT.baseRange, op.baseRange, 0.01f)
        assertEquals(2, op.maxDeployed)
        assertEquals(Maps.HUGGING_FACE.id, op.unlockMapId)
        assertFalse(op in AgentType.earnedBy(500, 500, mapOf("hugging_face" to 99, "ddos" to 500)))
        assertTrue(op in AgentType.earnedBy(0, 0, mapOf("hugging_face" to 100)))
        val glyphs = AgentType.entries.map { it.glyph }
        assertEquals(glyphs.size, glyphs.toSet().size)
    }

    @Test
    fun `agents in its range cannot be jammed, agents outside it can`() {
        val engine = engine()
        val nodes = Maps.HUGGING_FACE.nodes.filter { !it.serverSlot }
        val home = nodes.first()
        assertEquals(PlacementResult.SUCCESS, engine.placeAgent(op, home.id))
        val operative = engine.agentAt(home.id)!!
        val reach = operative.range()
        val near = nodes.first { it.id != home.id && dist(it.x, it.y, home.x, home.y) < reach * 0.8f }
        val far = nodes.first { dist(it.x, it.y, home.x, home.y) > reach * 1.2f }
        engine.placeAgent(AgentType.REDHAT, near.id)
        engine.placeAgent(AgentType.REDHAT, far.id)
        engine.combatSystem().refreshBuffs()
        for (agent in engine.agents.items.filter { it.active }) agent.jam(3f)
        assertEquals(0f, operative.disruptedFor, 0.001f)
        assertEquals(0f, engine.agentAt(near.id)!!.disruptedFor, 0.001f)
        assertEquals(3f, engine.agentAt(far.id)!!.disruptedFor, 0.001f)

        // Sold, the field goes with it.
        engine.sellAgent(home.id)
        engine.combatSystem().refreshBuffs()
        engine.agentAt(near.id)!!.jam(3f)
        assertEquals(3f, engine.agentAt(near.id)!!.disruptedFor, 0.001f)
    }

    private fun dist(ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = ax - bx
        val dy = ay - by
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    @Test
    fun `hits WHITE EYE and BLACK EYE hard, and outdamages a hat against them`() {
        val engine = engine()
        for (variant in listOf(BossVariant.WHITE_EYE, BossVariant.BLACK_EYE, BossVariant.BREACH)) {
            engine.enemySystem().spawn(SpawnOrder(0f, EnemyType.BOSS, 0, false, true, bossVariant = variant), 50)
            val boss = engine.enemies.items.last { it.active }
            val mult = engine.projectileSystem().damageMultiplier(boss, op)
            val expected = if (variant == BossVariant.BREACH) 1f else Balance.CYBER_OPERATIVE_EYE_MULTIPLIER
            assertEquals(variant.name, expected, mult, 0.01f)
            if (variant != BossVariant.BREACH) {
                val opDps = op.baseDamage * op.baseFireRate * mult
                val hatDps = AgentType.REDHAT.baseDamage * AgentType.REDHAT.baseFireRate *
                    engine.projectileSystem().damageMultiplier(boss, AgentType.REDHAT)
                assertTrue(opDps > hatDps * 2f)
            }
            boss.active = false
        }
    }
}
