package com.cyopstd.game

import com.cyopstd.game.core.Balance
import com.cyopstd.game.core.Maps
import com.cyopstd.game.engine.GameEngine
import com.cyopstd.game.engine.PlacementResult
import com.cyopstd.game.engine.RunPhase
import com.cyopstd.game.engine.SpawnOrder
import com.cyopstd.game.engine.WaveGenerator
import com.cyopstd.game.model.AgentType
import com.cyopstd.game.model.BossVariant
import com.cyopstd.game.model.MAP_PROGRESSION
import com.cyopstd.game.model.EnemyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * ACE [♤] (backlog ♡6, owner, 2026-09-30): unlocked by wave 100 on DDoS;
 * builds walls threats must break; hits every boss of levels 1–5 very hard;
 * balanced to slow attacks, not stop them.
 */
class AceTest {

    private val ace = AgentType.ACE

    private fun engine(map: com.cyopstd.game.core.GameMap = Maps.DDOS, seed: Int = 1): GameEngine {
        val random = Random(seed)
        return GameEngine(random, WaveGenerator(random)).apply {
            isAgentUnlocked = { true }
            selectMap(map)
            startNewRun()
            addCrypto(1_000_000, countAsEarned = false)
        }
    }

    @Test
    fun `matches the request`() {
        assertEquals("♤", ace.glyph)
        assertEquals("[♤]", ace.renderedGlyph(1))
        assertTrue(ace.buildsWalls)
        assertEquals("ddos", ace.unlockMapId)
        assertEquals(100, ace.unlockWave)
        assertTrue("bachelor's degree" in ace.realWorld)
        assertEquals("Unlock this agent by reaching wave 100 on DDoS.", ace.unlockRequirement)
        val glyphs = AgentType.entries.map { it.glyph }
        assertEquals(glyphs.size, glyphs.toSet().size)
    }

    @Test
    fun `only wave 100 on DDoS earns it`() {
        assertFalse(ace in AgentType.earnedBy(500, 500, mapOf("perimeter" to 500, "ddos" to 99)))
        assertTrue(ace in AgentType.earnedBy(0, 0, mapOf("ddos" to 100)))

        // And the engine hands it out on DDoS, in the break before wave 101.
        val engine = engine()
        val unlocked = mutableSetOf<AgentType>()
        engine.isAgentUnlocked = { it in unlocked }
        engine.onAgentUnlocked = { unlocked += it }
        engine.restore(100, 100, 0, emptyList(), 0, 0, 0, 0, 0, 0)
        assertTrue(ace in unlocked)
        val elsewhere = engine(Maps.PERIMETER)
        val other = mutableSetOf<AgentType>()
        elsewhere.isAgentUnlocked = { it in other }
        elsewhere.onAgentUnlocked = { other += it }
        elsewhere.restore(150, 100, 0, emptyList(), 0, 0, 0, 0, 0, 0)
        assertFalse(ace in other)
    }

    @Test
    fun `hits every boss of the first five levels four times as hard`() {
        val engine = engine()
        for (variant in BossVariant.entries) {
            if (!variant.isChapterOne) {
                // Chapter two's own bosses (♡5) are not ACE's target.
                assertTrue(variant.mapId in MAP_PROGRESSION.drop(5))
                continue
            }
            engine.enemySystem().spawn(SpawnOrder(0f, EnemyType.BOSS, 0, false, true, bossVariant = variant), 50)
            val boss = engine.enemies.items.last { it.active }
            val withAce = engine.projectileSystem().damageMultiplier(boss, ace)
            val withFirewall = engine.projectileSystem().damageMultiplier(boss, AgentType.FIREWALL)
            assertEquals(variant.name, withFirewall * Balance.ACE_BOSS_MULTIPLIER *
                (variant.bonusDamageFrom[ace.name] ?: 1f) / (variant.bonusDamageFrom[AgentType.FIREWALL.name] ?: 1f),
                withAce, 0.01f)
            boss.active = false
        }
    }

    @Test
    fun `builds a wall during a wave, a threat stops and breaks it, then it rebuilds`() {
        val engine = engine()
        val node = Maps.DDOS.nodesByCoverage[0].id
        assertEquals(PlacementResult.SUCCESS, engine.placeAgent(ace, node))
        val agent = engine.agentAt(node)!!
        engine.update(0.1f, 1f)
        assertNull("no wall outside a wave", engine.wallSystem().wallOf(agent))

        engine.startNextWave()
        engine.update(0.02f, 1f)
        val wall = assertNotNull(engine.wallSystem().wallOf(agent)).let { engine.wallSystem().wallOf(agent)!! }
        assertTrue("wall is out of ACE's reach",
            kotlin.math.hypot(wall.x - agent.x, wall.y - agent.y) <= agent.range() + 1f)
        assertEquals(Balance.aceWallHealth(1, 1), wall.maxHealth, 0.01f)

        // A plain threat walks up to it on a route it blocks and stops.
        val lane = (0 until Maps.DDOS.laneCount).first { !wall.progressAt(it).isNaN() }
        engine.enemySystem().spawn(SpawnOrder(0f, EnemyType.SQL_INJECTION, lane, false, false), 1)
        val threat = engine.enemies.items.last { it.active }
        threat.maxHealth = 1e9f; threat.health = 1e9f
        threat.progress = wall.progressAt(lane) - 200f
        var t = 0f
        while (t < 20f && wall.active) { engine.wallSystem().allowedStep(threat, 0f, 0f); engine.enemySystem().update(0.02f); engine.wallSystem().update(0.02f); t += 0.02f }
        assertTrue("threat walked through the wall",
            threat.progress <= wall.progressAt(lane) - com.cyopstd.game.engine.WallSystem.STAND_OFF + 1f || !wall.active)
        // One threat takes health / dps seconds to break it.
        var seconds = 0f
        while (wall.active && seconds < 120f) { engine.enemySystem().update(0.02f); seconds += 0.02f }
        assertFalse("the wall never broke", wall.active)
        println("one plain threat broke a level-1 wave-1 wall in ${"%.1f".format(seconds + t)} s")
        assertEquals(Balance.ACE_WALL_REBUILD_SECONDS, agent.wallCooldown, 0.1f)
    }

    @Test
    fun `selling ACE takes its wall down`() {
        val engine = engine()
        val node = Maps.DDOS.nodesByCoverage[0].id
        engine.placeAgent(ace, node)
        engine.startNextWave()
        engine.update(0.02f, 1f)
        assertNotNull(engine.wallSystem().wallOf(engine.agentAt(node)!!))
        engine.sellAgent(node)
        assertEquals(0, engine.walls.items.count { it.active })
    }

    // -------------------------------------------------------------- balance

    private data class Outcome(val reached: Int, val hpLost: Int, val wallsBroken: Int, val wallSeconds: Float)

    /** A strong late board on DDoS; [aces] of its slots are ACE or, for comparison, ROOT ADMIN. */
    private fun play(useAce: Boolean, fromWave: Int, waves: Int, seed: Int): Outcome {
        val engine = engine(seed = seed)
        val nodes = Maps.DDOS.nodesByCoverage
        val roster = listOf(AgentType.ROOT_ADMIN, AgentType.ANALYST, AgentType.ZERO_DAY_HUNTER,
            AgentType.QUANTUM_DEFENDER, AgentType.TARPIT, AgentType.CRYPTOGRAPHER, AgentType.AI_SENTINEL,
            AgentType.IPS, AgentType.NETWORK_ARCHITECT, AgentType.REDHAT, AgentType.BLUEHAT, AgentType.IDS)
        val placements = (0 until 15).map { i ->
            val type = when {
                i < 3 -> if (useAce) ace else AgentType.ROOT_ADMIN
                else -> roster[i % roster.size]
            }
            GameEngine.SavedPlacement(nodes[i].id, type.name, 60, 0)
        }
        engine.restore(fromWave - 1, engine.serverMaxHp, 0, placements, 0, 0, 0, 0, 0, 0)
        var broken = 0
        var wallTime = 0f
        var hpLost = 0
        val target = engine.currentWave + waves
        while (engine.phase != RunPhase.GAME_OVER && engine.currentWave < target) {
            engine.startNextWave()
            var guard = 0
            while (engine.phase != RunPhase.PREPARING && engine.phase != RunPhase.GAME_OVER) {
                val before = engine.walls.items.count { it.active }
                val hp = engine.serverHp
                engine.update(0.05f, 1f)
                if (engine.serverHp < hp) hpLost += hp - engine.serverHp
                wallTime += 0.05f * engine.walls.items.count { it.active }
                if (engine.walls.items.count { it.active } < before) broken++
                check(guard++ < 400_000) { "wave ${engine.currentWave} never ended — a wall must never stop a wave" }
            }
        }
        return Outcome(engine.currentWave, hpLost, broken, wallTime)
    }

    @org.junit.Ignore(
        "Owner, 2026-10-02: skip the balance tests; the boss retune is play-tested on a real build instead."
    )
    @Test
    fun `balance - three ACEs hold a deep DDoS run better than three ROOT ADMINs, and never stall it`() {
        for (seed in 1..2) {
            val root = play(useAce = false, fromWave = 80, waves = 40, seed = seed)
            val withAce = play(useAce = true, fromWave = 80, waves = 40, seed = seed)
            println("ACE balance seed $seed: ROOT x3 reached ${root.reached}, lost ${root.hpLost} HP; " +
                "ACE x3 reached ${withAce.reached}, lost ${withAce.hpLost} HP, " +
                "${withAce.wallsBroken} walls broken, average hold " +
                "${"%.1f".format(if (withAce.wallsBroken == 0) 0f else withAce.wallSeconds / withAce.wallsBroken)} s")
            assertTrue("ACE should do at least as well as ROOT ADMIN in its slots",
                withAce.reached > root.reached || (withAce.reached == root.reached && withAce.hpLost <= root.hpLost))
            assertTrue("walls must actually be broken: they slow, they do not stop", withAce.wallsBroken > 0)
        }
    }
}
