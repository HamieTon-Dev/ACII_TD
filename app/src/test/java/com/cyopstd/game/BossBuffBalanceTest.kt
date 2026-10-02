package com.cyopstd.game

import com.cyopstd.game.core.Balance
import com.cyopstd.game.core.Maps
import com.cyopstd.game.engine.CombatSystem
import com.cyopstd.game.engine.GameEngine
import com.cyopstd.game.engine.SpawnOrder
import com.cyopstd.game.engine.WaveGenerator
import com.cyopstd.game.model.AgentType
import com.cyopstd.game.model.BossModifier
import com.cyopstd.game.model.BossVariant
import com.cyopstd.game.model.Enemy
import com.cyopstd.game.model.EnemyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Owner, 2026-10-02: levels 3 and 4 were *"almost impossible to get to wave
 * 100"*. *"Tarpit should slow the bosses down double what they do now. Regen
 * makes it almost impossible to kill these bosses."*
 */
class BossBuffBalanceTest {

    private fun engineWithBoss(): Pair<GameEngine, Enemy> {
        val random = Random(3)
        val engine = GameEngine(random, WaveGenerator(random))
        engine.isAgentUnlocked = { true }
        engine.selectMap(Maps.PERIMETER)
        engine.startNewRun()
        engine.enemySystem().spawn(
            SpawnOrder(0f, EnemyType.BOSS, 0, elite = false, boss = true, bossVariant = BossVariant.BREACH), 20
        )
        return engine to engine.enemies.items.first { it.active && it.isBoss }
    }

    @Test
    fun `TARPIT slows a boss twice as much as an ordinary threat, floored`() {
        val normal = 1f - CombatSystem.tarpitSlowFactor(1)
        val boss = 1f - CombatSystem.tarpitBossSlowFactor(1)
        assertEquals(normal * 2f, boss, 0.001f)
        for (level in 1..30) {
            assertTrue(CombatSystem.tarpitBossSlowFactor(level) >= Balance.TARPIT_BOSS_MIN_FACTOR)
        }
    }

    @Test
    fun `a boss in a TARPIT field gets the boss slow`() {
        val (engine, boss) = engineWithBoss()
        engine.addCrypto(1_000_000, countAsEarned = false)
        // Walk it in front of a TARPIT and let the field catch it.
        repeat(200) { engine.update(0.05f, 1f) }
        val node = Maps.PERIMETER.nodes.minBy { kotlin.math.hypot(it.x - boss.x, it.y - boss.y) }
        engine.placeAgent(AgentType.TARPIT, node.id)
        engine.update(0.02f, 1f)
        assertEquals(CombatSystem.tarpitBossSlowFactor(1), boss.slowFactor, 0.001f)
    }

    @Test
    fun `REGENERATION does not repair a boss under fire, and repairs it once left alone`() {
        val (engine, boss) = engineWithBoss()
        boss.modifiers = 1 shl BossModifier.REGENERATION.ordinal
        boss.health = boss.maxHealth * 0.5f

        // Hit every half second for five seconds: no repair in between.
        repeat(10) {
            engine.projectileSystem().applyDamage(boss, 1f, AgentType.FIREWALL, ignoresArmor = true, heavy = false)
            val before = boss.health
            repeat(25) { engine.update(0.02f, 1f) }
            assertEquals("it healed while under fire", before, boss.health, 0.001f)
        }

        // Left alone: it waits out the pause, then repairs at the new rate.
        val before = boss.health
        repeat(250) { engine.update(0.02f, 1f) } // 5 seconds
        val healed = boss.health - before
        val expected = boss.maxHealth * Balance.REGEN_SHARE_PER_SECOND * (5f - Balance.REGEN_PAUSE_AFTER_HIT)
        assertTrue("healed $healed, expected about $expected", healed > 0f)
        assertEquals(expected, healed, boss.maxHealth * 0.003f)
    }

    @Test
    fun `the BOSS and OTHER focus buttons pick what an agent may shoot, and are saved`() {
        val random = Random(5)
        val engine = GameEngine(random, WaveGenerator(random))
        engine.isAgentUnlocked = { true }
        engine.selectMap(Maps.PERIMETER)
        engine.startNewRun()
        engine.addCrypto(1_000_000, countAsEarned = false)
        val node = Maps.PERIMETER.nodesByCoverage.first()
        engine.placeAgent(AgentType.FIREWALL, node.id)
        val agent = engine.agentAt(node.id)!!
        engine.setFocus(node.id, com.cyopstd.game.model.TargetFocus.BOSSES)

        // A plain threat right in front of it: it holds fire.
        engine.enemySystem().spawn(SpawnOrder(0f, EnemyType.SQL_INJECTION, 0, elite = false, boss = false), 5)
        val packet = engine.enemies.items.first { it.active }
        packet.x = agent.x + 20f; packet.y = agent.y
        assertEquals(null, engine.combatSystem().selectTarget(agent))

        // A boss in range: that is the target.
        engine.enemySystem().spawn(
            SpawnOrder(0f, EnemyType.BOSS, 0, elite = false, boss = true, bossVariant = BossVariant.BREACH), 5
        )
        val boss = engine.enemies.items.first { it.active && it.isBoss }
        boss.x = agent.x + 60f; boss.y = agent.y
        assertEquals(boss, engine.combatSystem().selectTarget(agent))
        assertEquals("BOSSES", engine.snapshotPlacements().first { it.nodeId == node.id }.focus)

        // OTHER: the packet, never the boss, even with the boss in range.
        engine.setFocus(node.id, com.cyopstd.game.model.TargetFocus.OTHERS)
        assertEquals(packet, engine.combatSystem().selectTarget(agent))
        assertEquals("OTHERS", engine.snapshotPlacements().first { it.nodeId == node.id }.focus)
    }
}
