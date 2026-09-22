package com.packetbastion.asciidefense.model

import com.packetbastion.asciidefense.core.WorldGeometry

/**
 * Runtime entities are deliberately mutable classes rather than data classes.
 * They are recycled through object pools so the simulation loop allocates
 * essentially nothing per frame, which is what keeps the game at 60 FPS on a
 * mid-range phone without the GC stuttering the render.
 *
 * `active == false` means "this instance is parked in the pool, ignore it".
 */

/**
 * Common contract for anything that lives in an [ObjectPool]. Having this means
 * the pool never has to type-check its contents, which matters because
 * `obtain()` and `activeCount()` run in the simulation's hot path.
 */
interface Poolable {
    var active: Boolean
    fun reset()
}

class Enemy : Poolable {
    override var active = false

    var type: EnemyType = EnemyType.SQL_INJECTION
    var lane: Int = 0

    /** Distance travelled along the lane path, in world units. */
    var progress: Float = 0f
    var x: Float = 0f
    var y: Float = 0f

    var maxHealth: Float = 1f
    var health: Float = 1f

    /** World units per second, before slow effects. */
    var baseSpeed: Float = 60f
    var armor: Float = 0f
    var serverDamage: Int = 1
    var reward: Int = 1

    /** Elite variants are ordinary archetypes that were rolled up a tier. */
    var isElite: Boolean = false
    var isBoss: Boolean = false

    /** Remaining seconds of slow, and the strongest slow currently applied. */
    var slowRemaining: Float = 0f
    var slowFactor: Float = 1f

    /** Seconds remaining on the white "just got hit" flash. */
    var hitFlash: Float = 0f

    /** Boss-only state. */
    var modifiers: Int = 0          // bit set over BossModifier ordinals
    var burstTimer: Float = 0f
    var burstActive: Float = 0f
    var regenAccumulator: Float = 0f
    var replicateTimer: Float = 0f
    var disruptTimer: Float = 0f

    /** Animation phase so identical packets do not pulse in lockstep. */
    var phase: Float = 0f

    val encrypted: Boolean
        get() = ThreatTrait.ENCRYPTED in type.traits ||
            hasModifier(BossModifier.ENCRYPTION_SHIELD)

    fun hasModifier(modifier: BossModifier): Boolean =
        isBoss && (modifiers and (1 shl modifier.ordinal)) != 0

    fun addModifier(modifier: BossModifier) {
        modifiers = modifiers or (1 shl modifier.ordinal)
    }

    fun activeModifiers(): List<BossModifier> =
        if (!isBoss || modifiers == 0) emptyList()
        else BossModifier.entries.filter { hasModifier(it) }

    /** Current speed including slows and boss speed bursts. */
    fun currentSpeed(): Float {
        var speed = baseSpeed
        if (slowRemaining > 0f) speed *= slowFactor
        if (burstActive > 0f) speed *= 2.1f
        return speed
    }

    /** 0f at the spawn point, 1f at the server. Used for FIRST/LAST targeting. */
    fun pathFraction(): Float {
        val length = WorldGeometry.laneLength.getOrElse(lane) { 1f }
        return if (length <= 0f) 0f else (progress / length).coerceIn(0f, 1f)
    }

    override fun reset() {
        active = false
        progress = 0f
        slowRemaining = 0f
        slowFactor = 1f
        hitFlash = 0f
        modifiers = 0
        burstTimer = 0f
        burstActive = 0f
        regenAccumulator = 0f
        replicateTimer = 0f
        disruptTimer = 0f
        isElite = false
        isBoss = false
    }
}

class Agent : Poolable {
    override var active = false

    var type: AgentType = AgentType.FIREWALL
    var nodeId: Int = 0
    var x: Float = 0f
    var y: Float = 0f

    var level: Int = 1
    var cooldownRemaining: Float = 0f
    var targetingMode: TargetingMode = TargetingMode.FIRST

    /** Seconds remaining on the muzzle-flash animation. */
    var fireFlash: Float = 0f

    /** Seconds remaining on the level-up burst animation. */
    var upgradeFlash: Float = 0f

    /** Boss AGENT_DISRUPTION jam timer — halves effective fire rate while > 0. */
    var disruptedFor: Float = 0f

    /** Buff contributed by nearby NETWORK_ARCHITECT agents; recomputed each tick. */
    var damageBuff: Float = 1f
    var rateBuff: Float = 1f

    var lifetimeKills: Int = 0
    var lifetimeDamage: Float = 0f

    fun stats(): AgentStats = type.statsAtLevel(level)

    fun effectiveDamage(): Float = stats().damage * damageBuff

    fun effectiveCooldown(): Float {
        val rate = stats().fireRate * rateBuff * (if (disruptedFor > 0f) 0.5f else 1f)
        return if (rate <= 0f) Float.MAX_VALUE else 1f / rate
    }

    fun range(): Float = stats().range

    override fun reset() {
        active = false
        level = 1
        cooldownRemaining = 0f
        fireFlash = 0f
        upgradeFlash = 0f
        disruptedFor = 0f
        damageBuff = 1f
        rateBuff = 1f
        targetingMode = TargetingMode.FIRST
        lifetimeKills = 0
        lifetimeDamage = 0f
    }
}

class Projectile : Poolable {
    override var active = false

    var x: Float = 0f
    var y: Float = 0f
    var originX: Float = 0f
    var originY: Float = 0f

    var targetEnemy: Enemy? = null
    /** Fallback destination if the target dies mid-flight. */
    var targetX: Float = 0f
    var targetY: Float = 0f

    var damage: Float = 0f
    var speed: Float = 900f
    var style: AttackStyle = AttackStyle.BOLT
    var sourceType: AgentType = AgentType.FIREWALL
    var sourceNodeId: Int = -1

    /** Slow applied on impact (1f means none). */
    var slowFactor: Float = 1f
    var slowDuration: Float = 0f

    var ignoresArmor: Boolean = false
    var critical: Boolean = false
    /** Remaining chain bounces (Quantum Defender). */
    var chainsLeft: Int = 0

    var angle: Float = 0f
    var travelled: Float = 0f

    override fun reset() {
        active = false
        targetEnemy = null
        chainsLeft = 0
        critical = false
        ignoresArmor = false
        slowFactor = 1f
        slowDuration = 0f
        travelled = 0f
    }
}

/** Short-lived ASCII flourishes: hit sparks, death glyphs, floating numbers. */
enum class EffectKind { HIT, DEATH, BOSS_DEATH, DAMAGE_NUMBER, TEXT, CRYPTO_GAIN, UPGRADE }

class Effect : Poolable {
    override var active = false

    var kind: EffectKind = EffectKind.HIT
    var x: Float = 0f
    var y: Float = 0f
    var velocityY: Float = 0f
    var age: Float = 0f
    var lifetime: Float = 0.4f
    var text: String = ""
    var colorArgb: Int = 0xFFFFFFFF.toInt()
    var scale: Float = 1f

    val progress: Float get() = if (lifetime <= 0f) 1f else (age / lifetime).coerceIn(0f, 1f)

    override fun reset() {
        active = false
        age = 0f
        text = ""
        scale = 1f
        velocityY = 0f
    }
}

/**
 * A fixed-capacity pool of [Poolable] entities.
 *
 * Obtaining an object never allocates: the pool is filled once at construction
 * and `obtain()` hands back the first parked slot. A rotating cursor means the
 * common case costs a single check rather than a scan from index zero.
 *
 * When the pool is exhausted [obtain] returns null and the caller simply skips
 * the spawn. That is a deliberate failure mode — a phone is far happier refusing
 * one more packet than growing an unbounded list it then has to simulate and
 * draw.
 */
class ObjectPool<T : Poolable>(val capacity: Int, factory: () -> T) {

    val items: List<T> = ArrayList<T>(capacity).apply {
        repeat(capacity) { add(factory()) }
    }

    /** Where the last successful obtain landed; the next search starts here. */
    private var cursor = 0

    fun obtain(): T? {
        val size = items.size
        if (size == 0) return null
        for (offset in 0 until size) {
            val index = (cursor + offset) % size
            val item = items[index]
            if (!item.active) {
                cursor = (index + 1) % size
                return item
            }
        }
        return null
    }

    fun activeCount(): Int {
        var count = 0
        for (i in items.indices) if (items[i].active) count++
        return count
    }

    fun clear() {
        for (i in items.indices) items[i].reset()
        cursor = 0
    }
}
