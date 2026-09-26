package com.cyopstd.game.engine

import com.cyopstd.game.core.Balance
import com.cyopstd.game.core.WorldGeometry
import com.cyopstd.game.model.BossModifier
import com.cyopstd.game.model.BossVariant
import com.cyopstd.game.model.Enemy
import com.cyopstd.game.model.EnemyType
import com.cyopstd.game.model.ThreatTrait
import kotlin.random.Random

/**
 * Spawns packets, walks them along their lane's waypoint path, and runs boss
 * modifier behaviour. Pathing is deliberately waypoint-following rather than a
 * general pathfinder: it is exact, costs nothing, and a future bent lane only
 * needs extra waypoints in [WorldGeometry].
 */
class EnemySystem(private val engine: GameEngine, private val random: Random) {

    fun spawn(order: SpawnOrder, wave: Int) {
        val enemy = engine.enemies.obtain() ?: run {
            // Pool exhausted. The packet is dropped from the wave count so the
            // wave can still finish, instead of hanging forever waiting on a
            // spawn that is never going to happen.
            engine.notifyEnemyRemoved(wasKilled = false, enemy = null)
            return
        }
        configure(
            enemy, order.type, order.lane, wave, order.elite, order.boss,
            order.bossModifiers, order.bossVariant
        )
    }

    /** Boss PACKET_REPLICATION escorts route through here too. */
    fun spawnEscort(type: EnemyType, lane: Int, progress: Float, wave: Int) {
        val enemy = engine.enemies.obtain() ?: return
        configure(enemy, type, lane, wave, elite = false, boss = false, modifiers = emptyList())
        enemy.progress = progress.coerceAtLeast(0f)
        placeOnPath(enemy)
        // Replicated escorts are not part of the wave plan, so they must not be
        // subtracted from the wave counter when they die.
        enemy.reward = (enemy.reward / 2).coerceAtLeast(1)
        escortIds.add(enemy)
    }

    private val escortIds = HashSet<Enemy>()

    /** Rotates through the corridor so consecutive spawns never coincide. */
    private var laneSlotCursor = 0

    private fun configure(
        enemy: Enemy,
        type: EnemyType,
        lane: Int,
        wave: Int,
        elite: Boolean,
        boss: Boolean,
        modifiers: List<BossModifier>,
        variant: BossVariant = BossVariant.BREACH
    ) {
        enemy.reset()
        enemy.active = true
        enemy.type = type
        enemy.lane = lane.coerceIn(0, engine.map.laneCount - 1)
        enemy.progress = 0f
        enemy.phase = random.nextFloat() * 6.283f
        // A boss fills the corridor on its own and stays on the centreline.
        enemy.laneOffset = if (boss) 0f else LANE_SLOTS[laneSlotCursor++ % LANE_SLOTS.size]

        val healthScale =
            (if (boss) Balance.bossHealthMultiplier(wave) else Balance.healthMultiplier(wave)) *
                engine.mode.healthScale
        var health = type.baseHealth * healthScale.toFloat()
        var armor = type.baseArmor + Balance.waveArmorBonus(wave).toFloat()
        var speed = type.baseSpeed * Balance.speedMultiplier(wave).toFloat()
        var damage = type.serverDamage

        enemy.isBoss = boss
        enemy.isElite = elite || type.isElite || boss

        if (elite && !boss) {
            // An elite variant of an ordinary archetype: tougher, meaner, slower.
            health *= 2.1f
            armor += 1.5f
            damage += 2
            speed *= 0.92f
        }

        if (boss) {
            // The variant's own weighting, applied before the modifiers so a
            // modifier is still worth the same proportion of whatever this
            // opponent is.
            enemy.variant = variant
            health *= variant.healthScale
            armor += variant.armorBonus
            speed *= variant.speedScale

            for (modifier in modifiers) enemy.addModifier(modifier)
            if (enemy.hasModifier(BossModifier.ARMOR_PLATING)) armor += 8f + wave * 0.25f
            enemy.burstTimer = 6f
            enemy.replicateTimer = 4.5f
            enemy.disruptTimer = 5.5f
            // The first variant jam lands a full interval after it walks out
            // of the spawn, not the moment it does.
            enemy.variantJamTimer = BossVariant.VARIANT_JAM_INTERVAL
        }

        enemy.maxHealth = health
        enemy.health = health
        enemy.armor = armor
        enemy.baseSpeed = speed
        enemy.serverDamage = damage
        enemy.reward = (EconomySystem.rewardFor(enemy, wave, random) * engine.mode.rewardScale)
            .toInt().coerceAtLeast(1)

        placeOnPath(enemy)
    }

    fun update(dt: Float) {
        val enemies = engine.enemies.items
        for (i in enemies.indices) {
            val enemy = enemies[i]
            if (!enemy.active) continue

            if (enemy.hitFlash > 0f) enemy.hitFlash -= dt
            if (enemy.slowRemaining > 0f) {
                enemy.slowRemaining -= dt
                if (enemy.slowRemaining <= 0f) enemy.slowFactor = 1f
            }

            if (enemy.isBoss) updateBoss(enemy, dt)

            enemy.progress += enemy.currentSpeed() * dt
            placeOnPath(enemy)

            if (enemy.progress >= engine.map.laneLength[enemy.lane]) {
                onReachedServer(enemy)
            }
        }
    }

    private fun updateBoss(enemy: Enemy, dt: Float) {
        if (enemy.burstActive > 0f) enemy.burstActive -= dt

        if (enemy.hasModifier(BossModifier.SPEED_BURST)) {
            enemy.burstTimer -= dt
            if (enemy.burstTimer <= 0f) {
                enemy.burstTimer = 7.5f
                enemy.burstActive = 1.4f
                engine.effectSystem().spawnText(
                    enemy.x, enemy.y - 44f, "SPEED BURST",
                    GameEngine.COLOR_WARNING, 0.8f
                )
            }
        }

        if (enemy.hasModifier(BossModifier.REGENERATION) && enemy.health < enemy.maxHealth) {
            enemy.regenAccumulator += enemy.maxHealth * 0.012f * dt
            if (enemy.regenAccumulator >= 1f) {
                val healed = enemy.regenAccumulator.toInt()
                enemy.health = (enemy.health + healed).coerceAtMost(enemy.maxHealth)
                enemy.regenAccumulator -= healed
            }
        }

        if (enemy.hasModifier(BossModifier.PACKET_REPLICATION)) {
            enemy.replicateTimer -= dt
            if (enemy.replicateTimer <= 0f) {
                enemy.replicateTimer = 5.0f
                val escortType = if (random.nextBoolean()) EnemyType.BOT else EnemyType.SQL_INJECTION
                spawnEscort(escortType, enemy.lane, enemy.progress - 30f, engine.currentWave)
                engine.effectSystem().spawnText(
                    enemy.x, enemy.y - 52f, "REPLICATING",
                    GameEngine.COLOR_HOSTILE, 0.8f
                )
            }
        }

        updateVariantJam(enemy, dt)

        if (enemy.hasModifier(BossModifier.AGENT_DISRUPTION)) {
            enemy.disruptTimer -= dt
            if (enemy.disruptTimer <= 0f) {
                enemy.disruptTimer = 8f
                var jammed = 0
                for (agent in engine.agents.items) {
                    if (!agent.active) continue
                    val dx = agent.x - enemy.x
                    val dy = agent.y - enemy.y
                    if (dx * dx + dy * dy <= DISRUPT_RADIUS_SQ) {
                        // Agent.jam decides whether it lands; some are built to
                        // stand in this.
                        agent.jam(3.0f)
                        if (agent.disruptedFor > 0f) jammed++
                    }
                }
                if (jammed > 0) {
                    engine.effectSystem().spawnText(
                        enemy.x, enemy.y - 52f, "AGENTS JAMMED",
                        GameEngine.COLOR_HOSTILE, 1.0f
                    )
                }
            }
        }
    }

    /**
     * The HUGGING-FACE eyes: jam one agent type, and only that one.
     *
     * The type test is here rather than in `Agent.jam` on purpose. `jam`
     * answers "can this agent be jammed at all" — that is the agent's own
     * business and FIREWALL's whole identity. "Which agents is *this* boss
     * looking for" is the boss's business, and putting it in the agent would
     * mean every future jammer editing a `when` in the wrong file.
     */
    private fun updateVariantJam(enemy: Enemy, dt: Float) {
        val target = enemy.variant.jamsAgentType ?: return
        enemy.variantJamTimer -= dt
        if (enemy.variantJamTimer > 0f) return
        enemy.variantJamTimer = BossVariant.VARIANT_JAM_INTERVAL

        var jammed = 0
        for (agent in engine.agents.items) {
            if (!agent.active || agent.type.name != target) continue
            val dx = agent.x - enemy.x
            val dy = agent.y - enemy.y
            if (dx * dx + dy * dy > VARIANT_JAM_RADIUS_SQ) continue
            agent.jam(BossVariant.VARIANT_JAM_SECONDS)
            if (agent.disruptedFor > 0f) jammed++
        }

        if (jammed > 0) {
            val label = com.cyopstd.game.model.AgentType.entries
                .first { it.name == target }
                .displayName
            engine.effectSystem().spawnText(
                enemy.x, enemy.y - 52f, "$label JAMMED",
                GameEngine.COLOR_HOSTILE, 1.0f
            )
        }
    }

    /** Scratch for [com.cyopstd.game.core.GameMap.positionAt]; the mover must not allocate. */
    private val pathScratch = FloatArray(3)

    /**
     * Convert path distance into a world position and heading.
     *
     * Progress along the route is authoritative: x, y and heading are all
     * derived from it every step, which is what lets the routes bend without
     * any other system needing to know.
     */
    private fun placeOnPath(enemy: Enemy) {
        engine.map.positionAt(enemy.lane, enemy.progress, pathScratch)
        val heading = pathScratch[2]
        enemy.heading = heading
        // Perpendicular to the direction of travel, so the offset holds through
        // every bend in the serpentine rather than flipping sides at a corner.
        enemy.x = pathScratch[0] - kotlin.math.sin(heading) * enemy.laneOffset
        enemy.y = pathScratch[1] + kotlin.math.cos(heading) * enemy.laneOffset
    }

    private fun onReachedServer(enemy: Enemy) {
        engine.damageServer(enemy.serverDamage)
        engine.effectSystem().spawnText(
            WorldGeometry.SERVER_X - 24f, enemy.y,
            "-${enemy.serverDamage}", GameEngine.COLOR_HOSTILE, 0.8f
        )
        val wasEscort = escortIds.remove(enemy)
        if (!wasEscort) engine.notifyEnemyRemoved(wasKilled = false, enemy = enemy)
        enemy.reset()
    }

    /** Called by [ProjectileSystem] when an enemy's health reaches zero. */
    fun onEnemyDestroyed(enemy: Enemy) {
        val reward = enemy.reward
        engine.economySystem().award(reward)
        engine.effectSystem().spawnDeath(enemy.x, enemy.y, enemy.isBoss)

        // Bosses and elites go out with a bang. Ordinary traffic does not, or
        // the board would be a firework display at every wave.
        if (enemy.isBoss || enemy.isElite) {
            engine.effectSystem().spawnShards(
                x = enemy.x,
                y = enemy.y,
                colorArgb = if (enemy.isBoss) {
                    GameEngine.COLOR_HOSTILE
                } else {
                    GameEngine.COLOR_ELITE
                },
                radius = if (enemy.isBoss) Balance.BOSS_SHARD_RADIUS else Balance.ELITE_SHARD_RADIUS,
                lifetime = if (enemy.isBoss) {
                    Balance.BOSS_SHARD_LIFETIME
                } else {
                    Balance.ELITE_SHARD_LIFETIME
                },
                toEdge = enemy.isBoss
            )
        }
        engine.effectSystem().spawnCryptoGain(enemy.x, enemy.y - 24f, reward)
        engine.effectSystem().maybeSpawnTerminalMessage(enemy.x, enemy.y)

        if (enemy.isBoss) {
            engine.soundListener?.invoke(GameSound.BOSS_DESTROYED)
            engine.hapticListener?.invoke(HapticCue.HEAVY)
            engine.effectSystem().spawnText(
                enemy.x, enemy.y - 70f, "INTRUSION CONTAINED",
                GameEngine.COLOR_SUCCESS, 1.4f, scale = 1.4f
            )
        } else {
            engine.soundListener?.invoke(GameSound.PACKET_DESTROYED)
        }

        val wasEscort = escortIds.remove(enemy)
        if (!wasEscort) {
            engine.notifyEnemyRemoved(wasKilled = true, enemy = enemy)
        } else {
            // Escorts still count toward "packets blocked" statistics.
            engine.notifyEscortDestroyed()
        }
        enemy.reset()
    }

    fun clearEscorts() = escortIds.clear()

    /** Applies a slow, keeping whichever slow is currently the strongest. */
    fun applySlow(enemy: Enemy, factor: Float, duration: Float) {
        if (factor >= 1f || duration <= 0f) return
        if (factor <= enemy.slowFactor || enemy.slowRemaining <= 0f) {
            enemy.slowFactor = factor
        }
        enemy.slowRemaining = maxOf(enemy.slowRemaining, duration)
    }

    companion object {
        /**
         * Sideways slots across the corridor, cycled per spawn.
         *
         * Kept under 11 units so a threat chip still sits inside the 54-unit
         * corridor, and ordered so that consecutive spawns land on opposite
         * sides rather than drifting across one at a time.
         */
        private val LANE_SLOTS = floatArrayOf(0f, 10f, -10f, 5f, -5f)

        private const val DISRUPT_RADIUS = 260f
        private const val DISRUPT_RADIUS_SQ = DISRUPT_RADIUS * DISRUPT_RADIUS
        private const val VARIANT_JAM_RADIUS_SQ =
            BossVariant.VARIANT_JAM_RADIUS * BossVariant.VARIANT_JAM_RADIUS
    }
}
