package com.packetbastion.asciidefense.engine

import com.packetbastion.asciidefense.core.Balance
import com.packetbastion.asciidefense.model.Agent
import com.packetbastion.asciidefense.model.AgentType
import com.packetbastion.asciidefense.model.BossModifier
import com.packetbastion.asciidefense.model.Enemy
import com.packetbastion.asciidefense.model.Projectile
import com.packetbastion.asciidefense.model.ThreatTrait
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Flies shots to their target and resolves damage.
 *
 * All of the "who counters what" rules live in [damageMultiplier], so the
 * counter-play table is readable in one place rather than scattered across the
 * agent definitions.
 */
class ProjectileSystem(private val engine: GameEngine, private val random: Random) {

    fun launch(agent: Agent, target: Enemy) {
        val projectile = engine.projectiles.obtain() ?: return
        projectile.reset()
        projectile.active = true
        projectile.x = agent.x
        projectile.y = agent.y
        projectile.originX = agent.x
        projectile.originY = agent.y
        projectile.targetEnemy = target
        projectile.targetX = target.x
        projectile.targetY = target.y
        projectile.style = agent.type.attackStyle
        projectile.sourceType = agent.type
        projectile.sourceNodeId = agent.nodeId
        projectile.speed = Balance.PROJECTILE_SPEED

        val critical = engine.combatSystem().rollCritical(agent)
        projectile.critical = critical
        projectile.damage = agent.effectiveDamage() *
            (if (critical) CombatSystem.HUNTER_CRIT_MULTIPLIER else 1f)

        projectile.ignoresArmor = when (agent.type) {
            AgentType.ZERO_DAY_HUNTER, AgentType.ROOT_ADMIN -> true
            else -> false
        }

        if (agent.type == AgentType.SANDBOX) {
            // Slow deepens as the sandbox levels: 0.60x at L1 down to 0.42x at L10.
            projectile.slowFactor = (0.60f - (agent.level - 1) * 0.02f).coerceAtLeast(0.42f)
            projectile.slowDuration = SANDBOX_SLOW_SECONDS
        }

        if (agent.type == AgentType.QUANTUM_DEFENDER) {
            projectile.chainsLeft = QUANTUM_CHAINS
        }

        projectile.angle = atan2(target.y - agent.y, target.x - agent.x)
        agent.lifetimeDamage += projectile.damage
    }

    fun update(dt: Float) {
        val projectiles = engine.projectiles.items
        for (i in projectiles.indices) {
            val projectile = projectiles[i]
            if (!projectile.active) continue

            val target = projectile.targetEnemy
            if (target != null && target.active && target.health > 0f) {
                projectile.targetX = target.x
                projectile.targetY = target.y
            }

            val dx = projectile.targetX - projectile.x
            val dy = projectile.targetY - projectile.y
            val distance = sqrt(dx * dx + dy * dy)
            val stepLength = projectile.speed * dt

            if (distance <= stepLength || distance <= 1f) {
                projectile.x = projectile.targetX
                projectile.y = projectile.targetY
                onImpact(projectile)
                projectile.reset()
                continue
            }

            val inverse = 1f / distance
            projectile.x += dx * inverse * stepLength
            projectile.y += dy * inverse * stepLength
            projectile.angle = atan2(dy, dx)
            projectile.travelled += stepLength

            // Safety valve: a shot chasing a target that went away is retired
            // rather than left to fly forever.
            if (projectile.travelled > MAX_TRAVEL) projectile.reset()
        }
    }

    private fun onImpact(projectile: Projectile) {
        val target = projectile.targetEnemy
        engine.effectSystem().spawnHit(projectile.x, projectile.y, projectile.critical)

        if (target == null || !target.active || target.health <= 0f) return

        applyDamage(
            enemy = target,
            rawDamage = projectile.damage,
            sourceType = projectile.sourceType,
            ignoresArmor = projectile.ignoresArmor,
            critical = projectile.critical,
            sourceNodeId = projectile.sourceNodeId
        )

        if (projectile.slowDuration > 0f) {
            engine.enemySystem().applySlow(target, projectile.slowFactor, projectile.slowDuration)
        }

        if (projectile.chainsLeft > 0) {
            chain(projectile, target)
        }

        engine.soundListener?.invoke(GameSound.PACKET_HIT)
    }

    /** QUANTUM DEFENDER: strike nearby packets for a fraction of the hit. */
    private fun chain(projectile: Projectile, origin: Enemy) {
        var remaining = projectile.chainsLeft
        val enemies = engine.enemies.items
        val chainDamage = projectile.damage * QUANTUM_CHAIN_RATIO

        for (i in enemies.indices) {
            if (remaining <= 0) break
            val enemy = enemies[i]
            if (!enemy.active || enemy === origin || enemy.health <= 0f) continue
            val dx = enemy.x - origin.x
            val dy = enemy.y - origin.y
            if (dx * dx + dy * dy > QUANTUM_CHAIN_RADIUS_SQ) continue

            applyDamage(
                enemy = enemy,
                rawDamage = chainDamage,
                sourceType = projectile.sourceType,
                ignoresArmor = false,
                critical = false,
                sourceNodeId = projectile.sourceNodeId
            )
            engine.effectSystem().spawnHit(enemy.x, enemy.y, critical = false)
            remaining--
        }
    }

    /**
     * Single entry point for hurting a packet. Applies the counter-play
     * multiplier, then armour, then checks for death.
     */
    fun applyDamage(
        enemy: Enemy,
        rawDamage: Float,
        sourceType: AgentType,
        ignoresArmor: Boolean,
        critical: Boolean,
        sourceNodeId: Int = -1
    ) {
        if (!enemy.active || enemy.health <= 0f) return

        var damage = rawDamage * damageMultiplier(enemy, sourceType)

        if (!ignoresArmor && enemy.armor > 0f) {
            // Armour never fully negates a hit — a minimum fraction always lands,
            // so a swarm-killer is weakened against a Trojan but not useless.
            damage = maxOf(damage - enemy.armor, rawDamage * MIN_ARMOR_PENETRATION)
        }

        damage = damage.coerceAtLeast(0.5f)

        enemy.health -= damage
        enemy.hitFlash = HIT_FLASH_SECONDS

        engine.effectSystem().spawnDamageNumber(enemy.x, enemy.y, damage, critical)

        if (enemy.health <= 0f) {
            enemy.health = 0f
            // Credit the kill to the agent that landed the finishing hit, so the
            // management panel can show what each deployment is actually doing.
            if (sourceNodeId >= 0) engine.agentAt(sourceNodeId)?.lifetimeKills++
            engine.enemySystem().onEnemyDestroyed(enemy)
        }
    }

    /**
     * The counter-play table.
     *
     * Positive interactions are large enough to be worth building around, and
     * negative ones never drop below a third of normal damage so that no
     * combination of packets can make an agent completely dead weight.
     */
    fun damageMultiplier(enemy: Enemy, sourceType: AgentType): Float {
        var multiplier = 1f

        if (enemy.encrypted) {
            multiplier *= if (sourceType == AgentType.CRYPTOGRAPHER) {
                CRYPTOGRAPHER_VS_ENCRYPTED
            } else {
                ENCRYPTED_RESISTANCE
            }
        }

        if (sourceType == AgentType.ANALYST && (enemy.isElite || enemy.isBoss)) {
            multiplier *= ANALYST_VS_ELITE
        }

        if (sourceType == AgentType.IDS && enemy.type.isFastArchetype()) {
            multiplier *= IDS_VS_FAST
        }

        if (sourceType == AgentType.IPS && ThreatTrait.SWARM in enemy.type.traits) {
            multiplier *= IPS_VS_SWARM
        }

        if (sourceType == AgentType.FIREWALL && enemy.hasModifier(BossModifier.FIREWALL_RESISTANCE)) {
            multiplier *= FIREWALL_RESISTED
        }

        return multiplier
    }

    companion object {
        const val HIT_FLASH_SECONDS = 0.11f
        const val MAX_TRAVEL = 1400f

        const val MIN_ARMOR_PENETRATION = 0.18f

        const val CRYPTOGRAPHER_VS_ENCRYPTED = 3.0f
        const val ENCRYPTED_RESISTANCE = 0.45f
        const val ANALYST_VS_ELITE = 1.8f
        const val IDS_VS_FAST = 1.45f
        const val IPS_VS_SWARM = 1.35f
        const val FIREWALL_RESISTED = 0.65f

        const val SANDBOX_SLOW_SECONDS = 2.2f
        const val QUANTUM_CHAINS = 2
        const val QUANTUM_CHAIN_RATIO = 0.55f
        private const val QUANTUM_CHAIN_RADIUS = 130f
        const val QUANTUM_CHAIN_RADIUS_SQ = QUANTUM_CHAIN_RADIUS * QUANTUM_CHAIN_RADIUS
    }
}
