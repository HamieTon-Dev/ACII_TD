package com.cyopstd.game.engine

import com.cyopstd.game.i18n.tr

import com.cyopstd.game.core.Balance
import com.cyopstd.game.model.Agent
import com.cyopstd.game.model.Enemy
import com.cyopstd.game.model.Wall
import kotlin.math.hypot

/**
 * ACE's walls (backlog ♡6): building them across the nearest route in range,
 * stopping threats at them, and letting threats break them down.
 */
class WallSystem(private val engine: GameEngine) {

    private val point = FloatArray(3)

    fun update(dt: Float) {
        for (wall in engine.walls.items) if (wall.active && wall.hitFlash > 0f) wall.hitFlash -= dt
        if (engine.phase != RunPhase.IN_WAVE) return
        for (agent in engine.agents.items) {
            if (!agent.active || !agent.type.buildsWalls) continue
            if (wallOf(agent) != null) continue
            if (agent.wallCooldown > 0f) {
                agent.wallCooldown -= dt
                continue
            }
            build(agent)
        }
    }

    fun wallOf(agent: Agent): Wall? =
        engine.walls.items.firstOrNull { it.active && it.ownerNodeId == agent.nodeId }

    /** Removes [agent]'s wall, if it has one (it was sold). */
    fun demolish(agent: Agent) {
        wallOf(agent)?.reset()
    }

    private fun build(agent: Agent) {
        val map = engine.map
        val reach = agent.range()
        // The nearest point of any route within reach, away from the very start
        // and the very end of the route.
        var bestX = 0f
        var bestY = 0f
        var bestDistance = Float.MAX_VALUE
        for (lane in 0 until map.laneCount) {
            val length = map.laneLength[lane]
            var d = EDGE_MARGIN
            while (d < length - EDGE_MARGIN) {
                map.positionAt(lane, d, point)
                val distance = hypot(point[0] - agent.x, point[1] - agent.y)
                if (distance <= reach && distance < bestDistance) {
                    bestDistance = distance
                    bestX = point[0]
                    bestY = point[1]
                }
                d += STEP
            }
        }
        if (bestDistance == Float.MAX_VALUE) return
        val wall = engine.walls.obtain() ?: return
        wall.reset()
        wall.active = true
        wall.ownerNodeId = agent.nodeId
        wall.x = bestX
        wall.y = bestY
        wall.maxHealth = Balance.aceWallHealth(agent.level, engine.currentWave)
        wall.health = wall.maxHealth
        wall.progressByLane = FloatArray(map.laneCount) { lane -> nearestProgress(lane, bestX, bestY) }
        engine.effectSystem().spawnText(bestX, bestY - 34f, tr("WALL UP"), GameEngine.COLOR_FRIENDLY, 0.7f)
    }

    /** Where on [lane] the point (x, y) lies, or NaN if the lane does not pass it. */
    private fun nearestProgress(lane: Int, x: Float, y: Float): Float {
        val map = engine.map
        var best = Float.NaN
        var bestDistance = SHARED_ROAD
        var d = 0f
        while (d <= map.laneLength[lane]) {
            map.positionAt(lane, d, point)
            val distance = hypot(point[0] - x, point[1] - y)
            if (distance <= bestDistance) {
                bestDistance = distance
                best = d
            }
            d += STEP / 2f
        }
        return best
    }

    /**
     * How far [enemy] may move this step, given the walls ahead of it. A threat
     * that reaches a wall stops there and damages it instead.
     */
    fun allowedStep(enemy: Enemy, step: Float, dt: Float): Float {
        var allowed = step
        // A SPOOFER decoy is not really there; a wall does not stop it.
        if (enemy.decoy) return allowed
        for (wall in engine.walls.items) {
            if (!wall.active) continue
            val at = wall.progressAt(enemy.lane)
            if (at.isNaN()) continue
            val stop = at - STAND_OFF
            if (enemy.progress > stop + 0.5f) continue
            val room = (stop - enemy.progress).coerceAtLeast(0f)
            if (room < allowed) allowed = room
            if (room <= 1f) hit(wall, enemy, dt)
        }
        return allowed
    }

    private fun hit(wall: Wall, enemy: Enemy, dt: Float) {
        val factor = when {
            enemy.isBoss -> Balance.ACE_WALL_BOSS_FACTOR
            enemy.isElite -> Balance.ACE_WALL_ELITE_FACTOR
            else -> 1f
        }
        wall.health -= Balance.ACE_WALL_DPS * factor * dt
        wall.hitFlash = 0.15f
        if (wall.health <= 0f) {
            engine.agents.items.firstOrNull { it.active && it.nodeId == wall.ownerNodeId }
                ?.wallCooldown = Balance.ACE_WALL_REBUILD_SECONDS
            engine.effectSystem().spawnText(wall.x, wall.y - 34f, tr("WALL DOWN"), GameEngine.COLOR_HOSTILE, 0.8f)
            wall.reset()
        }
    }

    companion object {
        private const val STEP = 8f
        private const val EDGE_MARGIN = 80f
        /** A route within this of the wall's point counts as passing through it. */
        private const val SHARED_ROAD = 14f
        /** How far short of the wall a threat stands. */
        const val STAND_OFF = 22f
        const val MAX_WALLS = 8
    }
}
