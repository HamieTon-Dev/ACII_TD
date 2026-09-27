package com.cyopstd.game

import com.cyopstd.game.core.GameMap
import com.cyopstd.game.core.Maps
import com.cyopstd.game.engine.GameEngine
import com.cyopstd.game.engine.RunPhase
import com.cyopstd.game.engine.WaveGenerator
import com.cyopstd.game.model.AgentType
import org.junit.Test
import kotlin.random.Random

/**
 * *"Scaling difficulty as maps progress"* (owner). One fixed board, played
 * until it falls, must get less far on each level than on the one before.
 * Layout alone got this backwards (the long routes were easier than the first
 * level), which is what `GameMap.threatHealthScale` corrects.
 */
class MapDifficultyTest {
    private fun reach(map: GameMap, seed: Int): Int {
        val random = Random(seed)
        val engine = GameEngine(random, WaveGenerator(random))
        engine.isAgentUnlocked = { true }
        engine.selectMap(map)
        engine.startNewRun()
        engine.addCrypto(100_000_000, countAsEarned = false)
        val board = listOf(AgentType.ROOT_ADMIN, AgentType.ANALYST, AgentType.QUANTUM_DEFENDER,
            AgentType.REDHAT, AgentType.BLUEHAT, AgentType.AI_SENTINEL, AgentType.ZERO_DAY_HUNTER,
            AgentType.IPS, AgentType.TARPIT, AgentType.NETWORK_ARCHITECT, AgentType.CRYPTOGRAPHER, AgentType.IDS)
        val nodes = map.nodesByCoverage.map { it.id }
        board.forEachIndexed { i, t -> engine.placeAgent(t, nodes[i]); engine.upgradeAgent(nodes[i], 9) }
        var guard = 0
        while (engine.phase != RunPhase.GAME_OVER && engine.currentWave < 200 && guard++ < 5_000_000) {
            if (engine.phase == RunPhase.PREPARING) engine.startNextWave()
            engine.update(0.05f, 1f)
        }
        return engine.currentWave
    }

    @Test
    fun `each level is harder than the one before it`() {
        val averages = Maps.all.map { map ->
            val waves = (1..4).map { reach(map, it) }
            println("DIFFICULTY ${map.id}: reached $waves")
            map to waves.average()
        }
        for ((earlier, later) in averages.zipWithNext()) {
            org.junit.Assert.assertTrue(
                "${later.first.id} (${later.second}) should be harder than ${earlier.first.id} (${earlier.second})",
                later.second < earlier.second
            )
        }
    }
}
