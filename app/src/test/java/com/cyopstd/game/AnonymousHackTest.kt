package com.cyopstd.game

import com.cyopstd.game.core.Balance
import com.cyopstd.game.core.Maps
import com.cyopstd.game.engine.GameEngine
import com.cyopstd.game.engine.RunPhase
import com.cyopstd.game.engine.SpawnOrder
import com.cyopstd.game.engine.WaveEvent
import com.cyopstd.game.engine.WaveGenerator
import com.cyopstd.game.model.EnemyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * ANONYMOUS HACK (owner, 2026-09-28): "Random event called anonymous hack
 * where 1k small enemies attack with double speed and health of small
 * enemies."
 */
class AnonymousHackTest {

    @Test
    fun `a thousand BOTs, never before wave 30, never on a boss wave`() {
        val plan = WaveGenerator(Random(1)).generateAnonymousHack(60)
        assertEquals(WaveEvent.ANONYMOUS_HACK, plan.event)
        assertEquals(1_000, plan.enemyCount)
        assertTrue(plan.orders.all { it.type == EnemyType.BOT && it.anonymous && !it.boss && !it.elite })

        val generator = WaveGenerator(Random(7))
        var events = 0
        var eligible = 0
        for (wave in 1..2_000) {
            val p = generator.generate(wave)
            if (p.event != null) {
                events++
                assertTrue("event on wave $wave", wave >= Balance.ANON_HACK_START_WAVE)
                assertTrue("event on boss wave $wave", !Balance.isBossWave(wave))
            }
            if (wave >= Balance.ANON_HACK_START_WAVE && !Balance.isBossWave(wave)) eligible++
        }
        val rate = events.toFloat() / eligible
        println("anonymous hack: $events in $eligible eligible waves (${"%.1f".format(rate * 100)}%)")
        assertTrue("rate $rate", rate in 0.02f..0.07f)
    }

    @Test
    fun `its BOTs have double the speed and health of ordinary ones`() {
        fun bot(anonymous: Boolean): com.cyopstd.game.model.Enemy {
            val engine = GameEngine(Random(1), WaveGenerator(Random(1)))
            engine.startNewRun()
            engine.enemySystem().spawn(
                SpawnOrder(0f, EnemyType.BOT, 0, elite = false, boss = false, anonymous = anonymous), 60
            )
            return engine.enemies.items.first { it.active }
        }
        val plain = bot(false)
        val hacked = bot(true)
        assertEquals(plain.maxHealth * 2f, hacked.maxHealth, 0.01f)
        assertEquals(plain.baseSpeed * 2f, hacked.baseSpeed, 0.01f)
        assertEquals(1, hacked.reward)
    }

    @Test
    fun `the whole thousand plays out under the screen cap and the wave ends`() {
        val random = Random(3)
        val generator = WaveGenerator(random)
        val engine = GameEngine(random, generator)
        engine.selectMap(Maps.DDOS)
        engine.startNewRun()
        engine.restore(
            wave = 60, serverHp = engine.serverMaxHp, crypto = 0, placements = emptyList(),
            attacksBlocked = 0, cryptoEarned = 0, bossesDefeated = 0,
            serverDamageTaken = 0, agentsDeployed = 0, agentUpgrades = 0
        )
        // Force the event for wave 61.
        engine.upcomingPlan()
        val field = GameEngine::class.java.getDeclaredField("previewedPlan").apply { isAccessible = true }
        field.set(engine, generator.generateAnonymousHack(61))
        engine.startNextWave()
        assertEquals(WaveEvent.ANONYMOUS_HACK, engine.activeEvent)

        var peak = 0
        var seconds = 0f
        while (engine.phase != RunPhase.PREPARING) {
            engine.update(0.02f, 1f)
            engine.repairServer(engine.serverMaxHp)
            seconds += 0.02f
            check(seconds < 3_600f) { "the hack never finished" }
            check(engine.phase != RunPhase.GAME_OVER)
            peak = maxOf(peak, engine.enemies.items.count { it.active })
        }
        println("anonymous hack undefended: ${seconds.toInt()} s, peak $peak on screen")
        assertTrue("peak $peak", peak <= Balance.MAX_ON_SCREEN_THREATS)
        assertEquals(0, engine.enemiesRemaining)
    }
}
