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
 * Map 4, 🦆 DUCK-USB (the owner picked BRAID and named it), its unlock
 * (wave 100 on NEURAL-MESH), and its bosses SYN-STORM and GRADIENT.
 */
class DuckUsbTest {

    private val map = Maps.DUCK_USB

    @Test
    fun `is the fourth level, three routes, with the duck in its name`() {
        assertEquals(map, Maps.all[3])
        assertEquals("🦆 DUCK-USB", map.displayName)
        assertEquals(3, map.laneCount)
        assertTrue("only ${map.nodes.size} build spots", map.nodes.size >= 61)
    }

    @Test
    fun `unlocks at wave 100 on NEURAL-MESH and nowhere else`() {
        fun unlocked(mesh: Int, gauntlet: Int = 0) = map.unlockedBy(
            bestWaveOnMap = { id ->
                when (id) {
                    Maps.NEURAL_MESH.id -> mesh
                    Maps.HUGGING_FACE.id -> gauntlet
                    else -> 0
                }
            }
        ) { mode -> if (mode == GameMode.HACK_AI) 400 else 400 }
        assertFalse(unlocked(mesh = 99, gauntlet = 400))
        assertTrue(unlocked(mesh = 100))
        assertEquals("clear wave 100 on NEURAL-MESH", map.unlockRequirement)
    }

    @Test
    fun `fields its own two bosses and every earlier level's`() {
        val pool = BossVariant.poolFor(40, map.id).toSet()
        assertEquals(BossVariant.entries.filter { it.isChapterOne && it.mapId != Maps.DDOS.id }.toSet(), pool)
        val mesh = BossVariant.poolFor(40, Maps.NEURAL_MESH.id).toSet()
        assertFalse(BossVariant.SYN_STORM in mesh || BossVariant.GRADIENT in mesh)
    }

    private fun bossOf(variant: BossVariant): Pair<GameEngine, Enemy> {
        val engine = GameEngine()
        engine.selectMap(map)
        engine.startNewRun()
        engine.enemySystem().spawn(
            SpawnOrder(0f, EnemyType.BOSS, 0, elite = false, boss = true, bossVariant = variant), 30
        )
        val boss = engine.enemies.items.first { it.active }
        boss.baseSpeed = 0f
        boss.progress = map.laneLength[0] * 0.4f
        return engine to boss
    }

    @Test
    fun `SYN-STORM splits once at half health onto another route`() {
        val (engine, boss) = bossOf(BossVariant.SYN_STORM)
        val before = engine.enemiesRemaining
        val hit = boss.maxHealth * 0.55f
        engine.projectileSystem().applyDamage(boss, hit, AgentType.ROOT_ADMIN, ignoresArmor = true, heavy = false)

        val bosses = engine.enemies.items.filter { it.active && it.isBoss }
        assertEquals("it should have split in two", 2, bosses.size)
        val copy = bosses.first { it !== boss }
        assertTrue("the copy must take another route", copy.lane != boss.lane)
        assertEquals(boss.health, copy.health, 0.01f)
        assertEquals(before + 1, engine.enemiesRemaining)
        assertEquals(0.4f, copy.pathFraction(map.laneLength[copy.lane]), 0.01f)

        // Neither half splits again.
        engine.projectileSystem().applyDamage(copy, copy.health * 0.9f, AgentType.ROOT_ADMIN, true, false)
        engine.projectileSystem().applyDamage(boss, boss.health * 0.9f, AgentType.ROOT_ADMIN, true, false)
        assertEquals(2, engine.enemies.items.count { it.active && it.isBoss })
    }

    @Test
    fun `SYN-STORM's split must be finished before the wave ends`() {
        val (engine, boss) = bossOf(BossVariant.SYN_STORM)
        engine.projectileSystem().applyDamage(boss, boss.maxHealth * 0.6f, AgentType.ROOT_ADMIN, true, false)
        val remaining = engine.enemiesRemaining
        engine.projectileSystem().applyDamage(boss, boss.maxHealth, AgentType.ROOT_ADMIN, true, false)
        assertEquals("one half down, one to go", remaining - 1, engine.enemiesRemaining)
    }

    @Test
    fun `GRADIENT speeds up with every hit, not with damage, and cools when left alone`() {
        val (engine, boss) = bossOf(BossVariant.GRADIENT)
        boss.baseSpeed = 100f
        assertEquals(100f * BossVariant.GRADIENT_SLOW, boss.currentSpeed(), 0.01f)

        // One enormous hit barely moves it...
        engine.projectileSystem().applyDamage(boss, boss.maxHealth * 0.3f, AgentType.ROOT_ADMIN, true, false)
        assertTrue(boss.currentSpeed() < 100f * (BossVariant.GRADIENT_SLOW + 0.1f))
        // ...twenty-five small ones max it out.
        repeat(25) { engine.projectileSystem().applyDamage(boss, 1f, AgentType.IPS, true, false) }
        assertEquals(100f * BossVariant.GRADIENT_FAST, boss.currentSpeed(), 0.5f)

        repeat(150) { engine.enemySystem().update(0.02f) }
        assertEquals("three untouched seconds cool it fully",
            100f * BossVariant.GRADIENT_SLOW, boss.currentSpeed(), 0.5f)
    }
}
