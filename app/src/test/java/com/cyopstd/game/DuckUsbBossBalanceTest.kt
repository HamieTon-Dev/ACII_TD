package com.cyopstd.game

import com.cyopstd.game.core.Maps
import com.cyopstd.game.engine.GameEngine
import com.cyopstd.game.engine.SpawnOrder
import com.cyopstd.game.engine.WaveGenerator
import com.cyopstd.game.model.AgentType
import com.cyopstd.game.model.BossVariant
import com.cyopstd.game.model.EnemyType
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * SYN-STORM and GRADIENT fought for real on DUCK-USB: one boss walking
 * the whole route into a fixed board, timed. They must be harder than the
 * plain BREACH against the board they punish, and beatable by the board they
 * ask for.
 */
class DuckUsbBossBalanceTest {

    private val nodes = Maps.DUCK_USB.nodesByCoverage.map { it.id }

    private data class Fight(val killed: Boolean, val seconds: Float)

    private fun fight(variant: BossVariant, board: List<AgentType>, level: Int, wave: Int = 100): Fight {
        val random = Random(9)
        val engine = GameEngine(random, WaveGenerator(random))
        engine.isAgentUnlocked = { true }
        engine.selectMap(Maps.DUCK_USB)
        engine.startNewRun()
        engine.addCrypto(100_000_000, countAsEarned = false)
        board.forEachIndexed { i, type ->
            engine.placeAgent(type, nodes[i])
            engine.upgradeAgent(nodes[i], level - 1)
        }
        engine.enemySystem().spawn(
            SpawnOrder(0f, EnemyType.BOSS, 0, elite = false, boss = true, bossVariant = variant), wave
        )
        val startHp = engine.serverHp
        var t = 0f
        while (t < 240f) {
            // No wave is running, so nothing else spawns: just this boss and the board.
            engine.update(0.02f, 1f)
            t += 0.02f
            // Every boss (both halves of a SYN-STORM) must be gone...
            if (engine.enemies.items.none { it.active && it.isBoss }) {
                // ...and none of them may have reached CORE-SERVER.
                return Fight(engine.serverHp == startHp, t)
            }
        }
        return Fight(false, t)
    }

    private val chip = List(12) { AgentType.IPS }
    private val burst = listOf(
        AgentType.ROOT_ADMIN, AgentType.ROOT_ADMIN, AgentType.ROOT_ADMIN,
        AgentType.ZERO_DAY_HUNTER, AgentType.ZERO_DAY_HUNTER, AgentType.ANALYST
    )
    private val mixed = listOf(
        AgentType.ROOT_ADMIN, AgentType.ZERO_DAY_HUNTER, AgentType.ANALYST, AgentType.QUANTUM_DEFENDER,
        AgentType.REDHAT, AgentType.BLUEHAT, AgentType.CRYPTOGRAPHER, AgentType.AI_SENTINEL,
        AgentType.IDS, AgentType.FIREWALL, AgentType.NETWORK_ARCHITECT, AgentType.IPS
    )

    @Test
    fun `the new bosses are harder against the board they punish and fair against the right one`() {
        val results = linkedMapOf(
            "BREACH vs 12 IPS (chip)" to fight(BossVariant.BREACH, chip, 14),
            "GRADIENT vs 12 IPS (chip)" to fight(BossVariant.GRADIENT, chip, 14),
            "BREACH vs 6 heavies (burst)" to fight(BossVariant.BREACH, burst, 14),
            "GRADIENT vs 6 heavies (burst)" to fight(BossVariant.GRADIENT, burst, 14),
            "BREACH vs mixed 12" to fight(BossVariant.BREACH, mixed, 12),
            "SYN-STORM vs mixed 12" to fight(BossVariant.SYN_STORM, mixed, 12)
        )
        for ((name, r) in results) println("DUB $name: ${if (r.killed) "killed in %.1fs".format(r.seconds) else "GOT THROUGH at %.1fs".format(r.seconds)}")
        assertTrue("a burst board must kill GRADIENT", results.getValue("GRADIENT vs 6 heavies (burst)").killed)
        assertTrue("chip damage must drive GRADIENT in faster than BREACH",
            results.getValue("GRADIENT vs 12 IPS (chip)").seconds < results.getValue("BREACH vs 12 IPS (chip)").seconds * 0.85f)
        assertTrue("a mixed board must beat SYN-STORM", results.getValue("SYN-STORM vs mixed 12").killed)
    }
}
