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
        if (order.anonymous) {
            enemy.maxHealth *= Balance.ANON_HACK_HEALTH
            enemy.health = enemy.maxHealth
            enemy.baseSpeed *= Balance.ANON_HACK_SPEED
            // A thousand kills at the usual rate would flood the economy.
            enemy.reward = 1
        }
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

    /** Whether [enemy] was replicated by a boss rather than spawned from the plan. */
    internal fun isEscort(enemy: Enemy): Boolean = enemy in escortIds

    /** A restored escort (mid-wave save): not part of the wave's count. */
    internal fun markEscort(enemy: Enemy) {
        escortIds.add(enemy)
    }

    /** Puts a restored enemy at its place on its route. */
    internal fun place(enemy: Enemy) = placeOnPath(enemy)

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
                engine.mode.healthScale * engine.map.threatHealthScale
        var health = type.baseHealth * (healthScale * Balance.pressureMultiplier(wave)).toFloat()
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
            enemy.disruptTimer = 11f
            // The first variant jam lands a full interval after it walks out
            // of the spawn, not the moment it does.
            enemy.variantJamTimer = BossVariant.VARIANT_JAM_INTERVAL
            enemy.ransomTimer = BossVariant.RANSOM_INTERVAL
            enemy.variantTimer = when (variant) {
                BossVariant.BOTMASTER -> BossVariant.BOTMASTER_INTERVAL
                BossVariant.SPOOFER -> FIRST_DECOY_AFTER
                else -> 0f
            }
        }

        // HARDENING: past wave 100, elites and bosses grow tougher after
        // every boss wave.
        if (enemy.isElite) health *= Balance.hardeningMultiplier(wave).toFloat()

        enemy.maxHealth = health
        enemy.health = health
        enemy.armor = armor
        enemy.baseSpeed = speed
        enemy.serverDamage = damage
        enemy.reward = (EconomySystem.rewardFor(enemy, wave, random) * engine.mode.rewardScale *
            engine.map.rewardScale)
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

            // A decoy is a picture of a boss, not a boss: it walks and nothing else.
            if (enemy.isBoss && !enemy.decoy) updateBoss(enemy, dt)

            // ACE's walls stop a threat that reaches them until it breaks through.
            enemy.progress += engine.wallSystem().allowedStep(enemy, enemy.currentSpeed() * dt, dt)
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

        if (enemy.regenPause > 0f) {
            enemy.regenPause -= dt
        } else if (enemy.hasModifier(BossModifier.REGENERATION) && enemy.health < enemy.maxHealth) {
            enemy.regenAccumulator += enemy.maxHealth * Balance.REGEN_SHARE_PER_SECOND * dt
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
        when (enemy.variant) {
            BossVariant.BOTMASTER -> updateBotmaster(enemy, dt)
            BossVariant.ROOTKIT -> updateRootkit(enemy, dt)
            BossVariant.SPOOFER -> updateSpoofer(enemy, dt)
            else -> Unit
        }
        updateLicense(enemy, dt)
        updateRansom(enemy, dt)
        if (enemy.variant == BossVariant.GRADIENT && enemy.gradientHeat > 0f) {
            enemy.gradientHeat = (enemy.gradientHeat - BossVariant.GRADIENT_COOL_PER_SECOND * dt)
                .coerceAtLeast(0f)
        }

        if (enemy.hasModifier(BossModifier.AGENT_DISRUPTION)) {
            enemy.disruptTimer -= dt
            if (enemy.disruptTimer <= 0f) {
                enemy.disruptTimer = 16f
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
     * SYN-STORM at half health: the original carries on and a copy with the
     * same health appears on another route, the same distance along it. The
     * copy joins the wave, so the wave does not end until both are gone.
     */
    fun splitSynStorm(enemy: Enemy) {
        enemy.split = true
        enemy.reward = (enemy.reward / 2).coerceAtLeast(1)
        val copy = engine.enemies.obtain() ?: return
        copy.reset()
        copy.active = true
        copy.type = enemy.type
        copy.isBoss = true
        copy.isElite = true
        copy.variant = enemy.variant
        copy.modifiers = enemy.modifiers
        copy.split = true
        copy.maxHealth = enemy.maxHealth
        copy.health = enemy.health
        copy.armor = enemy.armor
        copy.baseSpeed = enemy.baseSpeed
        copy.serverDamage = enemy.serverDamage
        copy.reward = enemy.reward
        copy.phase = enemy.phase + 1.7f
        copy.laneOffset = 0f
        copy.burstTimer = enemy.burstTimer
        copy.replicateTimer = enemy.replicateTimer
        copy.disruptTimer = enemy.disruptTimer
        val lanes = engine.map.laneCount
        copy.lane = if (lanes > 1) (enemy.lane + 1) % lanes else enemy.lane
        val fraction = enemy.pathFraction(engine.map.laneLength[enemy.lane])
        copy.progress = fraction * engine.map.laneLength[copy.lane]
        placeOnPath(copy)
        engine.addToWave()
        engine.effectSystem().spawnText(
            enemy.x, enemy.y - 56f, "SYN-STORM SPLIT", GameEngine.COLOR_HOSTILE, 1.1f
        )
    }

    /** BOTMASTER drops BOTs behind itself. See [BossVariant.BOTMASTER]. */
    private fun updateBotmaster(enemy: Enemy, dt: Float) {
        enemy.variantTimer -= dt
        if (enemy.variantTimer > 0f) return
        enemy.variantTimer = BossVariant.BOTMASTER_INTERVAL
        for (i in 0 until BossVariant.BOTMASTER_DROP) {
            spawnEscort(EnemyType.BOT, enemy.lane, enemy.progress - 24f - i * 14f, engine.currentWave)
        }
        engine.effectSystem().spawnText(
            enemy.x, enemy.y - 52f, "BOTNET", GameEngine.COLOR_HOSTILE, 0.8f
        )
    }

    /** ROOTKIT's hide cycle. See [BossVariant.ROOTKIT]. */
    private fun updateRootkit(enemy: Enemy, dt: Float) {
        enemy.variantTimer = (enemy.variantTimer + dt) % BossVariant.ROOTKIT_CYCLE
        val hide = enemy.variantTimer >= BossVariant.ROOTKIT_CYCLE - BossVariant.ROOTKIT_HIDDEN
        if (hide && !enemy.hidden) {
            engine.effectSystem().spawnText(
                enemy.x, enemy.y - 52f, "HIDDEN", GameEngine.COLOR_HOSTILE, 0.8f
            )
        }
        enemy.hidden = hide
    }

    /** SPOOFER casts decoys of itself just ahead. See [BossVariant.SPOOFER]. */
    private fun updateSpoofer(enemy: Enemy, dt: Float) {
        enemy.variantTimer -= dt
        if (enemy.variantTimer > 0f) return
        enemy.variantTimer = BossVariant.SPOOFER_INTERVAL
        var alive = 0
        for (other in engine.enemies.items) {
            if (other.active && other.decoy && other.decoyOwner === enemy) alive++
        }
        if (alive >= BossVariant.SPOOFER_MAX_DECOYS) return
        val decoy = engine.enemies.obtain() ?: return
        decoy.reset()
        decoy.active = true
        decoy.type = enemy.type
        decoy.isBoss = true
        decoy.isElite = true
        decoy.variant = enemy.variant
        decoy.decoy = true
        decoy.decoyOwner = enemy
        decoy.maxHealth = enemy.maxHealth * BossVariant.SPOOFER_DECOY_HEALTH
        decoy.health = decoy.maxHealth
        decoy.armor = enemy.armor
        decoy.baseSpeed = enemy.baseSpeed
        decoy.serverDamage = 0
        decoy.reward = 0
        decoy.phase = enemy.phase + 2.3f
        decoy.lane = enemy.lane
        val length = engine.map.laneLength[enemy.lane]
        decoy.progress = (enemy.progress + BossVariant.SPOOFER_DECOY_LEAD)
            .coerceAtMost(length - DECOY_CLEARANCE).coerceAtLeast(enemy.progress)
        placeOnPath(decoy)
        escortIds.add(decoy)
        engine.effectSystem().spawnText(
            enemy.x, enemy.y - 52f, "SPOOFED", GameEngine.COLOR_HOSTILE, 0.8f
        )
    }

    /** A SPOOFER's decoys go with it. */
    private fun dropDecoysOf(owner: Enemy) {
        for (other in engine.enemies.items) {
            if (!other.active || !other.decoy || other.decoyOwner !== owner) continue
            escortIds.remove(other)
            engine.effectSystem().spawnText(
                other.x, other.y - 40f, "SPOOF DROPPED", GameEngine.COLOR_ELITE, 0.8f
            )
            other.reset()
        }
    }

    /**
     * WORM breaks into [BossVariant.WORM_PIECES] smaller worms, spread along
     * its route. Each joins the wave; the smallest do not break again.
     */
    private fun breakWorm(enemy: Enemy) {
        if (enemy.wormGeneration >= BossVariant.WORM_MAX_GENERATION) return
        val length = engine.map.laneLength[enemy.lane]
        var made = 0
        for (i in 0 until BossVariant.WORM_PIECES) {
            val piece = engine.enemies.obtain() ?: break
            piece.reset()
            piece.active = true
            piece.type = enemy.type
            piece.isBoss = true
            piece.isElite = true
            piece.variant = BossVariant.WORM
            piece.wormGeneration = enemy.wormGeneration + 1
            piece.maxHealth = enemy.maxHealth * BossVariant.WORM_PIECE_HEALTH
            piece.health = piece.maxHealth
            piece.armor = enemy.armor
            piece.baseSpeed = enemy.baseSpeed * WORM_PIECE_SPEED
            piece.serverDamage = (enemy.serverDamage / 2).coerceAtLeast(1)
            piece.reward = (enemy.reward / BossVariant.WORM_PIECES).coerceAtLeast(1)
            piece.phase = enemy.phase + i * 2.1f
            piece.lane = enemy.lane
            piece.laneOffset = (i - 1) * 9f
            piece.progress = (enemy.progress + WORM_SPREAD[i % WORM_SPREAD.size])
                .coerceIn(0f, length - 1f)
            placeOnPath(piece)
            engine.addToWave()
            made++
        }
        if (made > 0) {
            engine.effectSystem().spawnText(
                enemy.x, enemy.y - 56f, "WORM SPLIT", GameEngine.COLOR_HOSTILE, 1.0f
            )
        }
    }

    /** KERNEL PANIC's last act: jam everything near it. */
    private fun kernelPanic(enemy: Enemy) {
        val radiusSq = BossVariant.KERNEL_PANIC_RADIUS * BossVariant.KERNEL_PANIC_RADIUS
        var jammed = 0
        for (agent in engine.agents.items) {
            if (!agent.active) continue
            val dx = agent.x - enemy.x
            val dy = agent.y - enemy.y
            if (dx * dx + dy * dy > radiusSq) continue
            agent.jam(BossVariant.KERNEL_PANIC_SECONDS)
            if (agent.disruptedFor > 0f) jammed++
        }
        engine.effectSystem().spawnText(
            enemy.x, enemy.y - 90f,
            if (jammed > 0) "KERNEL PANIC \u00B7 $jammed JAMMED" else "KERNEL PANIC",
            GameEngine.COLOR_HOSTILE, 1.4f
        )
    }

    /** RANSOM holds one agent's upgrades at a time. See [BossVariant.RANSOM]. */
    private fun updateRansom(enemy: Enemy, dt: Float) {
        if (enemy.variant != BossVariant.RANSOM) return
        enemy.ransomTimer -= dt
        if (enemy.ransomTimer > 0f) return
        enemy.ransomTimer = BossVariant.RANSOM_INTERVAL
        val free = engine.agents.items.filter { it.active && it.ransomedFor <= 0f }
        if (free.isEmpty()) return
        val victim = free[random.nextInt(free.size)]
        victim.ransomedFor = BossVariant.RANSOM_SECONDS
        engine.effectSystem().spawnText(
            victim.x, victim.y - 44f, "RANSOMED", GameEngine.COLOR_HOSTILE, 1.2f
        )
    }

    /**
     * LICENSE's enforcement cycle (owner, 2026-10-02: *"only work for 2s then
     * cooldown for 15 seconds"*). [Enemy.variantTimer] runs round the cycle;
     * the damage cut applies only in its first [BossVariant.LICENSE_ACTIVE_SECONDS],
     * and each new window starts the hit counts afresh.
     */
    private fun updateLicense(enemy: Enemy, dt: Float) {
        if (enemy.variant != BossVariant.LICENSE || enemy.decoy) return
        enemy.variantTimer += dt
        if (enemy.variantTimer >= BossVariant.LICENSE_CYCLE_SECONDS) {
            enemy.variantTimer -= BossVariant.LICENSE_CYCLE_SECONDS
            enemy.licenseHits.fill(0)
            engine.effectSystem().spawnText(
                enemy.x, enemy.y - 50f, "LICENSE ENFORCED", GameEngine.COLOR_ELITE, 0.9f
            )
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
        if (enemy.decoy) {
            // A decoy was never a threat; it just stops being drawn.
            escortIds.remove(enemy)
            enemy.reset()
            return
        }
        if (enemy.isBoss && enemy.variant == BossVariant.SPOOFER) dropDecoysOf(enemy)
        if (enemy.isBoss && enemy.variant == BossVariant.EXFIL) {
            // Takes crypto, not integrity. See BossVariant.EXFIL.
            val stolen = (engine.crypto * BossVariant.EXFIL_STEAL).toInt()
            if (stolen > 0) engine.removeCrypto(stolen)
            engine.effectSystem().spawnText(
                WorldGeometry.SERVER_X - 24f, enemy.y,
                "-\u25C7$stolen EXFILTRATED", GameEngine.COLOR_HOSTILE, 1.4f
            )
        } else {
            engine.damageServer(enemy.serverDamage)
            engine.effectSystem().spawnText(
                WorldGeometry.SERVER_X - 24f, enemy.y,
                "-${enemy.serverDamage}", GameEngine.COLOR_HOSTILE, 0.8f
            )
        }
        val wasEscort = escortIds.remove(enemy)
        if (!wasEscort) engine.notifyEnemyRemoved(wasKilled = false, enemy = enemy)
        enemy.reset()
    }

    /** Called by [ProjectileSystem] when an enemy's health reaches zero. */
    fun onEnemyDestroyed(enemy: Enemy) {
        if (enemy.decoy) {
            // Nothing to pay and nothing to count: it was never there.
            engine.effectSystem().spawnText(
                enemy.x, enemy.y - 40f, "DECOY", GameEngine.COLOR_ELITE, 0.8f
            )
            engine.effectSystem().spawnDeath(enemy.x, enemy.y, false)
            escortIds.remove(enemy)
            enemy.reset()
            return
        }
        if (enemy.isBoss) {
            when (enemy.variant) {
                BossVariant.WORM -> breakWorm(enemy)
                BossVariant.KERNEL_PANIC -> kernelPanic(enemy)
                BossVariant.SPOOFER -> dropDecoysOf(enemy)
                else -> Unit
            }
        }
        // What was actually credited, firmware bonus included, so the "+◇"
        // popup shows what the player really got.
        val reward = engine.economySystem().award(enemy.reward)
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

        /** A SPOOFER's first decoy comes this soon after it walks out. */
        private const val FIRST_DECOY_AFTER = 2f

        /** A decoy never appears closer than this to the core. */
        private const val DECOY_CLEARANCE = 30f

        /** WORM pieces keep the pace of what they broke from. */
        private const val WORM_PIECE_SPEED = 1f

        /**
         * Where along the route each WORM piece lands, relative to the break:
         * never ahead of it, so a kill near the core is not a free leak.
         */
        private val WORM_SPREAD = floatArrayOf(-40f, -20f, 0f)

        private const val DISRUPT_RADIUS = 260f
        private const val DISRUPT_RADIUS_SQ = DISRUPT_RADIUS * DISRUPT_RADIUS
        private const val VARIANT_JAM_RADIUS_SQ =
            BossVariant.VARIANT_JAM_RADIUS * BossVariant.VARIANT_JAM_RADIUS
    }
}
