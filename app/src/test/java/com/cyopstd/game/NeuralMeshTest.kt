package com.cyopstd.game

import com.cyopstd.game.core.GameMode
import com.cyopstd.game.core.Maps
import com.cyopstd.game.engine.GameEngine
import com.cyopstd.game.engine.SpawnOrder
import com.cyopstd.game.model.AgentType
import com.cyopstd.game.model.BossVariant
import com.cyopstd.game.model.Enemy
import com.cyopstd.game.model.EnemyType
import com.cyopstd.game.model.MAP_PROGRESSION
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Map 3, NEURAL-MESH (the owner picked the BACKPROP layout), its unlock
 * (*"wave 100 on hugging face"*), and its two bosses, MODEL COLLAPSE and
 * LICENSE, which stack on top of every earlier map's.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NeuralMeshTest {

    private val map = Maps.NEURAL_MESH

    // ------------------------------------------------------------- the map

    @Test
    fun `is the third level, two routes, the backprop layout`() {
        assertEquals(listOf(Maps.PERIMETER, Maps.HUGGING_FACE, Maps.NEURAL_MESH), Maps.all)
        assertEquals(2, map.laneCount)
        for (length in map.laneLength) assertEquals(3662f, length, 1f)
        assertTrue("only ${map.nodes.size} build spots", map.nodes.size >= 60)
        assertEquals("the boss progression must list the levels in order",
            Maps.all.map { it.id }, MAP_PROGRESSION)
    }

    // ------------------------------------------------------------ unlocking

    @Test
    fun `unlocks at wave 100 on HUGGING-FACE and nowhere else`() {
        fun unlocked(hf: Int, standard: Int = 0, hackAi: Int = 0) = map.unlockedBy(
            bestWaveOnMap = { id -> if (id == Maps.HUGGING_FACE.id) hf else 0 },
            bestWaveOnMode = { mode -> if (mode == GameMode.HACK_AI) hackAi else standard }
        )
        assertFalse(unlocked(hf = 99))
        assertTrue(unlocked(hf = 100))
        assertFalse("a huge record elsewhere must not open it", unlocked(hf = 0, standard = 400, hackAi = 400))
        assertEquals("clear wave 100 on HUGGING-FACE", map.unlockRequirement)
    }

    @Test
    fun `the per-level record is kept for each level, in any mode`() = runBlocking {
        val repository = TestStores.isolatedRepository()
        repository.updateHighestWave(100, GameMode.HACK_AI.id, Maps.HUGGING_FACE.id)
        repository.updateHighestWave(60, GameMode.STANDARD.id, Maps.HUGGING_FACE.id)
        repository.updateHighestWave(140, GameMode.STANDARD.id, Maps.PERIMETER.id)
        val bests = repository.stats.first().highestWaveByMap
        assertEquals(100, bests[Maps.HUGGING_FACE.id])
        assertEquals(140, bests[Maps.PERIMETER.id])
        assertTrue(map.unlockedBy(bestWaveOnMap = { bests[it] ?: 0 }) { 0 })
    }

    // -------------------------------------------------------- the boss pool

    @Test
    fun `each level fields its own bosses and every earlier level's`() {
        val late = 40
        val perimeter = BossVariant.poolFor(late, Maps.PERIMETER.id).toSet()
        val gauntlet = BossVariant.poolFor(late, Maps.HUGGING_FACE.id).toSet()
        val mesh = BossVariant.poolFor(late, Maps.NEURAL_MESH.id).toSet()
        assertEquals(setOf(BossVariant.BREACH, BossVariant.GOOD_GAME, BossVariant.ZOMBIE), perimeter)
        assertTrue(gauntlet.containsAll(perimeter))
        assertFalse(BossVariant.MODEL_COLLAPSE in gauntlet || BossVariant.LICENSE in gauntlet)
        assertEquals("NEURAL-MESH fields every boss in the game", BossVariant.entries.toSet(), mesh)
    }

    // ------------------------------------------------------------- bosses

    private fun bossOf(variant: BossVariant): Pair<GameEngine, Enemy> {
        val engine = GameEngine()
        engine.selectMap(map)
        engine.startNewRun()
        engine.enemySystem().spawn(
            SpawnOrder(0f, EnemyType.BOSS, 0, elite = false, boss = true, bossVariant = variant), 30
        )
        val boss = engine.enemies.items.first { it.active }
        boss.baseSpeed = 0f
        return engine to boss
    }

    @Test
    fun `MODEL COLLAPSE feeds on a crowd, not on two agents`() {
        assertEquals(0f, BossVariant.collapseHealShare(2), 0f)
        assertEquals(0.08f, BossVariant.collapseHealShare(3), 0.0001f)
        assertEquals(0.4f, BossVariant.collapseHealShare(7), 0.0001f)
        assertEquals(BossVariant.COLLAPSE_MAX_HEAL, BossVariant.collapseHealShare(16), 0.0001f)

        val (engine, boss) = bossOf(BossVariant.MODEL_COLLAPSE)
        boss.maxHealth = 100_000f; boss.health = 100_000f
        for (node in 0 until 7) {
            engine.projectileSystem().applyDamage(boss, 100f, AgentType.IPS, ignoresArmor = true, heavy = false, sourceNodeId = node)
        }
        val crowdHit = boss.health
        engine.projectileSystem().applyDamage(boss, 100f, AgentType.IPS, ignoresArmor = true, heavy = false, sourceNodeId = 0)
        assertEquals("with seven on it, a 100 hit lands as 60", 60f, crowdHit - boss.health, 0.5f)
    }

    @Test
    fun `MODEL COLLAPSE learns who hit it from real shots`() {
        val (engine, boss) = bossOf(BossVariant.MODEL_COLLAPSE)
        for (node in 0 until 3) {
            engine.projectileSystem().applyDamage(boss, 5f, AgentType.FIREWALL, false, false, sourceNodeId = node)
        }
        assertEquals(3, boss.distinctAttackersWithin(engine.elapsedTime, BossVariant.COLLAPSE_WINDOW))
    }

    @Test
    fun `LICENSE punishes one agent type and rewards a varied board`() {
        fun dealt(types: List<AgentType>): Float {
            val (engine, boss) = bossOf(BossVariant.LICENSE)
            boss.maxHealth = 100_000f; boss.health = 100_000f
            val before = boss.health
            for (type in types) {
                engine.projectileSystem().applyDamage(boss, 10f, type, ignoresArmor = true, heavy = false)
            }
            return before - boss.health
        }
        val same = dealt(List(90) { AgentType.FIREWALL })
        val varied = dealt(List(30) { AgentType.FIREWALL } + List(30) { AgentType.IDS } + List(30) { AgentType.ROOT_ADMIN })
        assertTrue("one type dealt $same, three types dealt $varied", varied > same * 1.3f)
        assertEquals(1f, BossVariant.licenseMultiplier(0), 0.0001f)
        assertEquals(BossVariant.LICENSE_FLOOR, BossVariant.licenseMultiplier(10_000), 0.0001f)
    }
}
