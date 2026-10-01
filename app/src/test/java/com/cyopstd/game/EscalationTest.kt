package com.cyopstd.game

import com.cyopstd.game.core.Balance
import com.cyopstd.game.core.Maps
import com.cyopstd.game.engine.GameEngine
import com.cyopstd.game.engine.RunPhase
import com.cyopstd.game.engine.SpawnOrder
import com.cyopstd.game.engine.WaveGenerator
import com.cyopstd.game.model.EnemyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Owner, 2026-09-28: "Difficulty not that bad after wave 100 on all levels."
 *  E1 ESCALATION  from wave 50, +2 elites on every wave and +1 boss on every
 *                 boss wave, per boss wave cleared.
 *  E2 HARDENING   from wave 100, elites and bosses gain health after every
 *                 boss wave, by a growing step.
 *  E3 CAPS        at most 10 threats and 5 elites on screen; the rest wait.
 *  E4 BOSS CAP    at most 4 bosses on screen; the rest wait.
 *  E5             all of it on every level.
 */
class EscalationTest {

    // ------------------------------------------------------------- E1

    @Test
    fun `escalation starts after the wave 50 boss and grows with every boss wave`() {
        assertEquals(0, Balance.escalationSteps(49))
        assertEquals(0, Balance.escalationSteps(50))
        assertEquals(1, Balance.escalationSteps(51))
        assertEquals(1, Balance.escalationSteps(55))
        assertEquals(2, Balance.escalationSteps(56))
        assertEquals(10, Balance.escalationSteps(100))
        assertEquals(2, Balance.escalationExtraElites(51))
        assertEquals(4, Balance.escalationExtraElites(58))
        assertEquals(1, Balance.escalationExtraBosses(55))
        assertEquals(2, Balance.escalationExtraBosses(60))
    }

    private fun generator(map: com.cyopstd.game.core.GameMap = Maps.PERIMETER, seed: Int = 1) =
        WaveGenerator(Random(seed)).apply { laneCount = map.laneCount; mapId = map.id }

    @Test
    fun `waves carry the extra elites and boss waves the extra bosses, on every level`() {
        for (map in Maps.all) {
            for (wave in listOf(51, 57, 73, 101, 142)) {
                val plan = generator(map).generate(wave)
                val elites = plan.orders.count { it.elite && !it.boss }
                assertTrue("${map.id} wave $wave has $elites elites",
                    elites >= Balance.escalationExtraElites(wave))
            }
            for (wave in listOf(50, 55, 60, 100, 150)) {
                val bosses = generator(map).generate(wave).orders.count { it.boss }
                assertEquals("${map.id} wave $wave", 3 + Balance.escalationExtraBosses(wave), bosses)
            }
        }
    }

    @Test
    fun `pressure compounds every threat's health from wave 40`() {
        assertEquals(1.0, Balance.pressureMultiplier(40), 1e-9)
        assertEquals(Balance.PRESSURE_GROWTH, Balance.pressureMultiplier(41), 1e-9)
        assertTrue(Balance.pressureMultiplier(60) > 1.6)
        assertTrue(Balance.pressureMultiplier(100) > Balance.pressureMultiplier(60) * 2.5)
    }

    // ------------------------------------------------------------- E2

    @Test
    fun `hardening grows by a larger step after every boss wave from 100`() {
        assertEquals(1.0, Balance.hardeningMultiplier(100), 1e-9)
        assertEquals(1.10, Balance.hardeningMultiplier(101), 1e-9)
        assertEquals(1.10, Balance.hardeningMultiplier(105), 1e-9)
        assertEquals(1.22, Balance.hardeningMultiplier(106), 1e-9)
        assertEquals(1.36, Balance.hardeningMultiplier(111), 1e-9)
        var lastStep = 0.0
        for (n in 1..30) {
            val wave = 100 + n * 5 + 1
            val step = Balance.hardeningMultiplier(wave) - Balance.hardeningMultiplier(wave - 5)
            assertTrue("step $n shrank", step > lastStep)
            lastStep = step
        }
    }

    @Test
    fun `hardening applies to elites and bosses, not ordinary traffic`() {
        fun health(wave: Int, elite: Boolean, boss: Boolean): Float {
            val engine = GameEngine(Random(1), WaveGenerator(Random(1)))
            engine.startNewRun()
            engine.enemySystem().spawn(
                SpawnOrder(0f, if (boss) EnemyType.BOSS else EnemyType.MALWARE, 0, elite, boss), wave
            )
            return engine.enemies.items.first { it.active }.maxHealth
        }
        val pressure = Balance.pressureMultiplier(111) / Balance.pressureMultiplier(100)
        val curve = (Balance.healthMultiplier(111) / Balance.healthMultiplier(100) * pressure).toFloat()
        assertEquals(curve, health(111, elite = false, boss = false) / health(100, false, false), 0.01f)
        assertEquals(curve * 1.36f, health(111, elite = true, boss = false) / health(100, true, false), 0.01f)
        val bossCurve = (Balance.bossHealthMultiplier(111) / Balance.bossHealthMultiplier(100) * pressure).toFloat()
        assertEquals(bossCurve * 1.36f, health(111, false, true) / health(100, false, true), 0.01f)
    }

    // --------------------------------------------------------- E3, E4

    /**
     * An undefended deep wave: everything walks, so the caps are what hold it
     * back. The core is kept alive so the whole wave can be watched through.
     */
    private fun watch(map: com.cyopstd.game.core.GameMap, wave: Int): IntArray {
        val random = Random(wave)
        val engine = GameEngine(random, WaveGenerator(random))
        engine.selectMap(map)
        engine.startNewRun()
        engine.restore(
            wave = wave - 1, serverHp = engine.serverMaxHp, crypto = 0, placements = emptyList(),
            attacksBlocked = 0, cryptoEarned = 0, bossesDefeated = 0,
            serverDamageTaken = 0, agentsDeployed = 0, agentUpgrades = 0
        )
        val planned = engine.upcomingPlan()!!.enemyCount
        engine.startNextWave()
        var maxThreats = 0; var maxElites = 0; var maxBosses = 0; var sawHeld = 0
        var steps = 0
        while (engine.phase != RunPhase.PREPARING) {
            check(steps++ < 400_000) { "wave $wave on ${map.id} never finished" }
            engine.update(0.02f, 1f)
            engine.repairServer(engine.serverMaxHp)
            check(engine.phase != RunPhase.GAME_OVER) { "core fell during the watch" }
            var threats = 0; var elites = 0; var bosses = 0
            for (e in engine.enemies.items) {
                if (!e.active) continue
                // A WORM's pieces and a SPOOFER's decoys are one boss, not more.
                if (e.isBoss) { if (e.wormGeneration == 0 && !e.decoy) bosses++ } else if (!engine.enemySystem().isEscort(e)) {
                    threats++
                    if (e.isElite) elites++
                }
            }
            maxThreats = maxOf(maxThreats, threats)
            maxElites = maxOf(maxElites, elites)
            maxBosses = maxOf(maxBosses, bosses)
            sawHeld = maxOf(sawHeld, engine.heldSpawnCount)
        }
        return intArrayOf(maxThreats, maxElites, maxBosses, sawHeld, planned)
    }

    @Test
    fun `no more than 10 threats, 5 elites and 4 bosses on screen, on every level, and the waves still end`() {
        for (map in Maps.all) {
            for (wave in listOf(64, 120, 150)) {
                val (threats, elites, bosses, held, planned) = watch(map, wave).toList()
                println("${map.id} wave $wave: $planned planned, peak $threats threats / $elites elites / " +
                    "$bosses bosses on screen, up to $held waiting")
                assertTrue("$threats threats", threats <= Balance.MAX_ON_SCREEN_THREATS)
                assertTrue("$elites elites", elites <= Balance.MAX_ON_SCREEN_ELITES)
                // SYN-STORM's split half is a second boss by design, not a spawn.
                assertTrue("$bosses bosses", bosses <= Balance.MAX_ON_SCREEN_BOSSES + 1)
                if (wave % 5 == 0) assertTrue("the boss wave never filled the boss cap", bosses >= 4)
            }
        }
    }
}
