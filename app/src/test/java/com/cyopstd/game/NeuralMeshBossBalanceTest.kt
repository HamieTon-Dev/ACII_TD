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
 * MODEL COLLAPSE and LICENSE fought for real on NEURAL-MESH: one boss walking
 * the whole route into a fixed board, timed. They must be harder than the
 * plain BREACH against the board they punish, and beatable by the board they
 * ask for.
 */
class NeuralMeshBossBalanceTest {

    private val nodes = Maps.NEURAL_MESH.nodesByCoverage.map { it.id }

    private data class Fight(val killed: Boolean, val seconds: Float)

    private fun fight(variant: BossVariant, board: List<AgentType>, level: Int, wave: Int = 100): Fight {
        val random = Random(9)
        val engine = GameEngine(random, WaveGenerator(random))
        engine.isAgentUnlocked = { true }
        engine.selectMap(Maps.NEURAL_MESH)
        engine.startNewRun()
        engine.addCrypto(100_000_000, countAsEarned = false)
        board.forEachIndexed { i, type ->
            engine.placeAgent(type, nodes[i])
            engine.upgradeAgent(nodes[i], level - 1)
        }
        engine.enemySystem().spawn(
            SpawnOrder(0f, EnemyType.BOSS, 0, elite = false, boss = true, bossVariant = variant), wave
        )
        val boss = engine.enemies.items.first { it.active }
        var t = 0f
        while (t < 240f) {
            // No wave is running, so nothing else spawns: just this boss and the board.
            engine.update(0.02f, 1f)
            t += 0.02f
            if (engine.runBossesDefeated > 0) return Fight(true, t)
            // Reached CORE-SERVER: it got through.
            if (!boss.active) return Fight(false, t)
        }
        return Fight(false, t)
    }

    private val blob = List(12) { AgentType.ANALYST }
    private val mixed = listOf(
        AgentType.ROOT_ADMIN, AgentType.ZERO_DAY_HUNTER, AgentType.ANALYST, AgentType.QUANTUM_DEFENDER,
        AgentType.REDHAT, AgentType.BLUEHAT, AgentType.CRYPTOGRAPHER, AgentType.AI_SENTINEL,
        AgentType.IDS, AgentType.FIREWALL, AgentType.NETWORK_ARCHITECT, AgentType.IPS
    )
    private val heavies = listOf(
        AgentType.ROOT_ADMIN, AgentType.ROOT_ADMIN, AgentType.ZERO_DAY_HUNTER, AgentType.ZERO_DAY_HUNTER
    )

    @Test
    fun `the new bosses are harder against the board they punish and fair against the right one`() {
        val level = 12
        val results = linkedMapOf(
            "BREACH vs 12 ANALYST" to fight(BossVariant.BREACH, blob, level),
            "MODEL COLLAPSE vs 12 ANALYST" to fight(BossVariant.MODEL_COLLAPSE, blob, level),
            "BREACH vs 4 heavies" to fight(BossVariant.BREACH, heavies, level),
            "MODEL COLLAPSE vs 4 heavies" to fight(BossVariant.MODEL_COLLAPSE, heavies, level),
            "LICENSE vs 12 ANALYST" to fight(BossVariant.LICENSE, blob, level),
            "BREACH vs mixed 12" to fight(BossVariant.BREACH, mixed, level),
            "LICENSE vs mixed 12" to fight(BossVariant.LICENSE, mixed, level)
        )
        for ((name, r) in results) println("NMB $name: ${if (r.killed) "killed in %.1fs".format(r.seconds) else "GOT THROUGH at %.1fs".format(r.seconds) + ""}")

        val collapseBlob = results.getValue("MODEL COLLAPSE vs 12 ANALYST")
        val breachBlob = results.getValue("BREACH vs 12 ANALYST")
        assertTrue("MODEL COLLAPSE should outlast BREACH against a blob",
            !collapseBlob.killed || collapseBlob.seconds > breachBlob.seconds * 1.2f)
        assertTrue("four heavy hitters must still kill MODEL COLLAPSE",
            results.getValue("MODEL COLLAPSE vs 4 heavies").killed)
        val licenseBlob = results.getValue("LICENSE vs 12 ANALYST")
        assertTrue("LICENSE should outlast BREACH against one agent type",
            !licenseBlob.killed || licenseBlob.seconds > breachBlob.seconds * 1.2f)
        assertTrue("a mixed board must kill LICENSE", results.getValue("LICENSE vs mixed 12").killed)
    }
}
