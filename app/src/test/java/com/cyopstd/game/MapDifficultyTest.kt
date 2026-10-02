package com.cyopstd.game

import com.cyopstd.game.core.GameMap
import com.cyopstd.game.core.Maps
import com.cyopstd.game.engine.GameEngine
import com.cyopstd.game.engine.RunPhase
import com.cyopstd.game.engine.WaveGenerator
import com.cyopstd.game.model.AgentType
import org.junit.Ignore
import org.junit.Test
import kotlin.random.Random

/**
 * *"Scaling difficulty as maps progress"* (owner). One fixed board, played
 * until it falls, must get less far on each level than on the one before.
 * Layout alone got this backwards (the long routes were easier than the first
 * level), which is what `GameMap.threatHealthScale` corrects.
 */
class MapDifficultyTest {
    /**
     * The spots a sensible player would build on first: most *distinct* route
     * within reach. `nodesByCoverage` counts a stretch once per route that uses
     * it, so where three routes share the last stretch before the core, a spot
     * there looked three times as good as it is and the test board crowded in
     * front of the core. Shared ground is counted once here.
     */
    private fun bestSpots(map: GameMap): List<Int> {
        val points = HashSet<Pair<Int, Int>>()
        val out = FloatArray(3)
        for (lane in 0 until map.laneCount) {
            var d = 0f
            while (d < map.laneLength[lane]) {
                map.positionAt(lane, d, out)
                points += (out[0] / 10f).toInt() to (out[1] / 10f).toInt()
                d += 10f
            }
        }
        val reach = 290f
        return map.nodes.sortedByDescending { node ->
            points.count { (px, py) -> kotlin.math.hypot(px * 10f - node.x, py * 10f - node.y) <= reach }
        }.map { it.id }
    }

    private fun reach(map: GameMap, seed: Int): Double {
        val random = Random(seed)
        val engine = GameEngine(random, WaveGenerator(random))
        engine.isAgentUnlocked = { true }
        engine.selectMap(map)
        engine.startNewRun()
        engine.addCrypto(100_000_000, countAsEarned = false)
        val board = listOf(AgentType.ROOT_ADMIN, AgentType.ANALYST, AgentType.QUANTUM_DEFENDER,
            AgentType.REDHAT, AgentType.BLUEHAT, AgentType.AI_SENTINEL, AgentType.ZERO_DAY_HUNTER,
            AgentType.IPS, AgentType.TARPIT, AgentType.NETWORK_ARCHITECT, AgentType.CRYPTOGRAPHER, AgentType.IDS)
        val nodes = bestSpots(map)
        board.forEachIndexed { i, t -> engine.placeAgent(t, nodes[i]); engine.upgradeAgent(nodes[i], 9) }
        var guard = 0
        var waveSize = 1
        while (engine.phase != RunPhase.GAME_OVER && engine.currentWave < 200 && guard++ < 5_000_000) {
            if (engine.phase == RunPhase.PREPARING) {
                engine.startNextWave()
                waveSize = engine.enemiesRemaining.coerceAtLeast(1)
            }
            engine.update(0.05f, 1f)
        }
        // Waves survived plus how far into the fatal one it got. A board almost
        // always falls on a boss wave, every fifth, so the wave number alone
        // can only tell levels apart in steps of five.
        val cleared = (waveSize - engine.enemiesRemaining).coerceAtLeast(0).toDouble() / waveSize
        return engine.currentWave - 1 + cleared
    }

    @Ignore(
        "Owner, 2026-10-02: skip the balance tests; the boss-buff retune (RNG modifiers, " +
            "TARPIT x2 on bosses, slower REGEN) is play-tested on a real build instead."
    )
    @Test
    fun `each level is harder than the one before it`() {
        val averages = Maps.all.map { map ->
            val waves = (1..8).map { reach(map, it) }
            println("DIFFICULTY ${map.id}: reached ${waves.map { "%.2f".format(it) }}")
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
