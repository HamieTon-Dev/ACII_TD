package com.packetbastion.asciidefense.engine

import com.packetbastion.asciidefense.model.Agent
import com.packetbastion.asciidefense.model.AgentType
import com.packetbastion.asciidefense.model.Enemy
import com.packetbastion.asciidefense.model.TargetingMode
import kotlin.random.Random

/**
 * Decides what each deployed agent shoots at, and when.
 *
 * Targeting is an O(agents x enemies) sweep. With the pool caps in place that is
 * at most 32 x 72 distance comparisons per sub-step using squared distances and
 * no allocation, which is comfortably cheap on a phone.
 */
class CombatSystem(private val engine: GameEngine, private val random: Random) {

    /** Scratch buffer reused by AI SENTINEL's multi-target volley. */
    private val multiTargets = arrayOfNulls<Enemy>(MAX_MULTI_TARGETS)

    /**
     * Recompute NETWORK_ARCHITECT aura buffs. Buffs do not stack: an agent takes
     * the strongest aura covering it, which keeps the effect legible instead of
     * turning into an architect-stacking exploit.
     */
    fun refreshBuffs() {
        val agents = engine.agents.items

        for (i in agents.indices) {
            val agent = agents[i]
            if (!agent.active) continue
            agent.damageBuff = 1f
            agent.rateBuff = 1f
        }

        for (i in agents.indices) {
            val architect = agents[i]
            if (!architect.active || architect.type != AgentType.NETWORK_ARCHITECT) continue
            val radius = architect.range()
            val radiusSq = radius * radius
            val damageBonus = 1f + ARCHITECT_DAMAGE_BONUS * (1f + (architect.level - 1) * 0.05f)
            val rateBonus = 1f + ARCHITECT_RATE_BONUS * (1f + (architect.level - 1) * 0.05f)

            for (j in agents.indices) {
                if (i == j) continue
                val other = agents[j]
                if (!other.active) continue
                val dx = other.x - architect.x
                val dy = other.y - architect.y
                if (dx * dx + dy * dy > radiusSq) continue
                if (damageBonus > other.damageBuff) other.damageBuff = damageBonus
                if (rateBonus > other.rateBuff) other.rateBuff = rateBonus
            }
        }
    }

    fun update(dt: Float) {
        val agents = engine.agents.items
        for (i in agents.indices) {
            val agent = agents[i]
            if (!agent.active) continue

            if (agent.fireFlash > 0f) agent.fireFlash -= dt
            if (agent.upgradeFlash > 0f) agent.upgradeFlash -= dt
            if (agent.disruptedFor > 0f) agent.disruptedFor -= dt

            if (agent.cooldownRemaining > 0f) {
                agent.cooldownRemaining -= dt
                continue
            }

            val fired = fire(agent)
            if (fired) {
                agent.cooldownRemaining = agent.effectiveCooldown()
                agent.fireFlash = FIRE_FLASH_SECONDS
            }
        }
    }

    private fun fire(agent: Agent): Boolean {
        return when (agent.type) {
            AgentType.AI_SENTINEL -> fireMulti(agent, SENTINEL_TARGETS)
            else -> {
                val target = selectTarget(agent) ?: return false
                engine.projectileSystem().launch(agent, target)
                true
            }
        }
    }

    /** AI SENTINEL engages several distinct packets with one volley. */
    private fun fireMulti(agent: Agent, count: Int): Boolean {
        val found = selectTargets(agent, count)
        if (found == 0) return false
        for (i in 0 until found) {
            val target = multiTargets[i] ?: continue
            engine.projectileSystem().launch(agent, target)
        }
        return true
    }

    /** Fills [multiTargets] with up to [count] distinct in-range enemies. */
    private fun selectTargets(agent: Agent, count: Int): Int {
        val limit = count.coerceAtMost(MAX_MULTI_TARGETS)
        for (i in 0 until limit) multiTargets[i] = null

        var found = 0
        val range = agent.range()
        val rangeSq = range * range
        val enemies = engine.enemies.items

        // Fill by "closest to the server first", which matches the default
        // targeting rule and keeps the volley focused on the real danger.
        while (found < limit) {
            var best: Enemy? = null
            var bestScore = -Float.MAX_VALUE
            for (i in enemies.indices) {
                val enemy = enemies[i]
                if (!enemy.active || enemy.health <= 0f) continue
                var alreadyPicked = false
                for (k in 0 until found) {
                    if (multiTargets[k] === enemy) { alreadyPicked = true; break }
                }
                if (alreadyPicked) continue
                val dx = enemy.x - agent.x
                val dy = enemy.y - agent.y
                if (dx * dx + dy * dy > rangeSq) continue
                val score = enemy.pathFraction()
                if (score > bestScore) {
                    bestScore = score
                    best = enemy
                }
            }
            if (best == null) break
            multiTargets[found] = best
            found++
        }
        return found
    }

    /** Picks a single victim according to the agent's targeting mode. */
    fun selectTarget(agent: Agent): Enemy? {
        val range = agent.range()
        val rangeSq = range * range
        val enemies = engine.enemies.items

        var best: Enemy? = null
        var bestScore = -Float.MAX_VALUE

        for (i in enemies.indices) {
            val enemy = enemies[i]
            if (!enemy.active || enemy.health <= 0f) continue
            val dx = enemy.x - agent.x
            val dy = enemy.y - agent.y
            if (dx * dx + dy * dy > rangeSq) continue

            val score = when (agent.targetingMode) {
                TargetingMode.FIRST -> enemy.pathFraction()
                TargetingMode.LAST -> -enemy.pathFraction()
                TargetingMode.STRONGEST -> enemy.health
                TargetingMode.WEAKEST -> -enemy.health
            }
            if (score > bestScore) {
                bestScore = score
                best = enemy
            }
        }
        return best
    }

    /** Roll a Zero-Day Hunter critical. */
    fun rollCritical(agent: Agent): Boolean =
        agent.type == AgentType.ZERO_DAY_HUNTER &&
            random.nextFloat() < HUNTER_CRIT_CHANCE

    companion object {
        const val FIRE_FLASH_SECONDS = 0.13f
        const val SENTINEL_TARGETS = 3
        const val MAX_MULTI_TARGETS = 4
        const val HUNTER_CRIT_CHANCE = 0.25f
        const val HUNTER_CRIT_MULTIPLIER = 3f
        const val ARCHITECT_DAMAGE_BONUS = 0.30f
        const val ARCHITECT_RATE_BONUS = 0.20f
    }
}
