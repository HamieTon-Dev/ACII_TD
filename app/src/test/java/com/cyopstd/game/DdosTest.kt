package com.cyopstd.game

import com.cyopstd.game.core.GameMode
import com.cyopstd.game.core.Maps
import com.cyopstd.game.engine.GameEngine
import com.cyopstd.game.engine.SpawnOrder
import com.cyopstd.game.model.AgentType
import com.cyopstd.game.model.BossVariant
import com.cyopstd.game.model.Enemy
import com.cyopstd.game.model.EnemyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Map 5, DDoS (the owner picked TWIN COMB, "almost double the agent
 * spaces"), its unlock (wave 100 on DUCK-USB), and its bosses RANSOM and EXFIL.
 */
class DdosTest {

    private val map = Maps.DDOS

    @Test
    fun `is the fifth level with about double the build spots`() {
        assertEquals(map, Maps.all[4])
        assertEquals("DDoS", map.displayName)
        assertEquals(2, map.laneCount)
        // The candidate as first drawn offered 25.
        assertTrue("only ${map.nodes.size} build spots", map.nodes.size >= 65)
    }

    @Test
    fun `unlocks at wave 100 on DUCK-USB and nowhere else`() {
        fun unlocked(duck: Int) = map.unlockedBy(
            bestWaveOnMap = { id -> if (id == Maps.DUCK_USB.id) duck else 500 }
        ) { 500 }
        assertFalse(unlocked(99))
        assertTrue(unlocked(100))
        assertEquals("clear wave 100 on 🦆 DUCK-USB", map.unlockRequirement)
    }

    @Test
    fun `fields every boss in the game, and no earlier level fields its two`() {
        assertEquals(BossVariant.entries.filter { it.isChapterOne }.toSet(), BossVariant.poolFor(40, map.id).toSet())
        val duck = BossVariant.poolFor(40, Maps.DUCK_USB.id).toSet()
        assertFalse(BossVariant.RANSOM in duck || BossVariant.EXFIL in duck)
    }

    private fun bossOf(variant: BossVariant): Pair<GameEngine, Enemy> {
        val engine = GameEngine()
        engine.isAgentUnlocked = { true }
        engine.selectMap(map)
        engine.startNewRun()
        engine.enemySystem().spawn(
            SpawnOrder(0f, EnemyType.BOSS, 0, elite = false, boss = true, bossVariant = variant), 30
        )
        return engine to engine.enemies.items.first { it.active }
    }

    @Test
    fun `RANSOM locks one agent's upgrades for 8 seconds at a time`() {
        val (engine, boss) = bossOf(BossVariant.RANSOM)
        boss.baseSpeed = 0f
        engine.addCrypto(1_000_000, countAsEarned = false)
        val node = map.nodesByCoverage.first().id
        engine.placeAgent(AgentType.FIREWALL, node)
        assertEquals("free before the first lock", 1, engine.upgradeAgent(node, 1))

        repeat((BossVariant.RANSOM_INTERVAL / 0.02f).toInt() + 2) { engine.enemySystem().update(0.02f) }
        assertTrue(engine.isUpgradeLocked(node))
        assertEquals("locked: nothing bought", 0, engine.upgradeAgent(node, 1))
        assertEquals(0, engine.affordableUpgrades(node))

        // The lock wears off, and the agent is free again.
        repeat((BossVariant.RANSOM_SECONDS / 0.02f).toInt() + 2) { engine.combatSystem().update(0.02f) }
        assertFalse(engine.isUpgradeLocked(node))
        assertEquals(1, engine.upgradeAgent(node, 1))
    }

    @Test
    fun `EXFIL steals half the crypto at the core and no integrity`() {
        val (engine, boss) = bossOf(BossVariant.EXFIL)
        engine.addCrypto(1_000, countAsEarned = false)
        val crypto = engine.crypto
        val hp = engine.serverHp
        boss.progress = map.laneLength[boss.lane] - 1f
        engine.enemySystem().update(0.1f)
        assertFalse(boss.active)
        assertEquals("integrity untouched", hp, engine.serverHp)
        assertEquals(crypto - (crypto * BossVariant.EXFIL_STEAL).toInt(), engine.crypto)
    }

    @Test
    fun `an ordinary boss still costs integrity at the core`() {
        val (engine, boss) = bossOf(BossVariant.BREACH)
        val hp = engine.serverHp
        boss.progress = map.laneLength[boss.lane] - 1f
        engine.enemySystem().update(0.1f)
        assertTrue(engine.serverHp < hp)
    }
}
