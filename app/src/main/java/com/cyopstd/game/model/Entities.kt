package com.cyopstd.game.model


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

    /** Direction of travel in radians, derived from the route. */
    var heading: Float = 0f

    /**
     * Sideways offset from the route's centreline, in world units.
     *
     * Threats do not all walk the exact centre of the corridor. Without this
     * a swarm burst — several of the same type spawned within a fraction of a
     * second — sits at one point and draws as a single smeared chip. Fanning
     * them across the corridor makes a swarm read as a crowd.
     */
    var laneOffset: Float = 0f

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

    /** Which boss this is. Meaningless unless [isBoss]. */
    var variant: BossVariant = BossVariant.BREACH

    /** A ZOMBIE only comes back once. */
    var revived: Boolean = false

    /** SYN-STORM splits once; both halves carry this so neither splits again. */
    var split: Boolean = false

    /** GRADIENT's momentum, 0 (untouched) to 1 (being hammered). */
    var gradientHeat: Float = 0f

    /** Countdown to RANSOM's next upgrade lock. */
    var ransomTimer: Float = 0f

    /**
     * The chapter-two bosses' own clock (♡5): BOTMASTER's next drop, ROOTKIT's
     * hide cycle, SPOOFER's next decoy.
     */
    var variantTimer: Float = 0f

    /** ROOTKIT while it is hidden: no agent will pick it as a target. */
    var hidden: Boolean = false

    /** WORM: 0 for the original, 1 and 2 for the pieces it breaks into. */
    var wormGeneration: Int = 0

    /**
     * A SPOOFER decoy: draws fire, does no harm, and is not part of the wave.
     * [decoyOwner] is the SPOOFER that cast it, so its decoys go with it.
     */
    var decoy: Boolean = false
    var decoyOwner: Enemy? = null

    /** Whether an agent may pick this as a target at all. */
    val targetable: Boolean get() = !hidden

    /**
     * LICENSE: how many hits each agent type has landed on it, by
     * `AgentType.ordinal`. Sized generously so a new agent never overflows it.
     */
    val licenseHits = IntArray(32)

    /**
     * MODEL COLLAPSE: the last few agents to hit it, by node id, and when (in
     * engine seconds). A small ring rather than a set, so recording a hit
     * never allocates.
     */
    val recentAttackerNodes = IntArray(16) { -1 }
    val recentAttackerTimes = FloatArray(16)
    var recentAttackerCursor: Int = 0

    /** Record that the agent on [nodeId] hit this at [time]. */
    fun recordAttacker(nodeId: Int, time: Float) {
        if (nodeId < 0) return
        for (i in recentAttackerNodes.indices) {
            if (recentAttackerNodes[i] == nodeId) {
                recentAttackerTimes[i] = time
                return
            }
        }
        recentAttackerNodes[recentAttackerCursor] = nodeId
        recentAttackerTimes[recentAttackerCursor] = time
        recentAttackerCursor = (recentAttackerCursor + 1) % recentAttackerNodes.size
    }

    /** How many different agents have hit this within [window] seconds of [now]. */
    fun distinctAttackersWithin(now: Float, window: Float): Int {
        var count = 0
        for (i in recentAttackerNodes.indices) {
            if (recentAttackerNodes[i] >= 0 && now - recentAttackerTimes[i] <= window) count++
        }
        return count
    }

    /**
     * Brings a ZOMBIE back, once, instead of dying.
     *
     * Returns true if the death was cancelled. Kept on the enemy rather than
     * in the damage path so that every way of dealing a killing blow — a
     * shot, splash, a chain — goes through the same rule and none of them has
     * to remember it.
     */
    fun tryRevive(): Boolean {
        if (!isBoss || revived || variant != BossVariant.ZOMBIE) return false
        revived = true
        health = maxHealth * BossVariant.ZOMBIE_REVIVE_FRACTION
        return true
    }

    /** What to draw for this threat. */
    fun renderedGlyph(): String = if (isBoss) variant.glyph else type.glyph

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

    /**
     * Countdown to this *variant's* next jam.
     *
     * Separate from [disruptTimer], which belongs to the rolled
     * `AGENT_DISRUPTION` modifier. They are different mechanics that happen to
     * share a verb: the modifier jams everything nearby that can be jammed,
     * the variant jams exactly one agent type. Sharing one timer would make a
     * WHITE EYE that rolled DISRUPT fire both on the same beat, which is a
     * spike the design did not ask for and nobody could read.
     */
    var variantJamTimer: Float = 0f

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
        if (isBoss && variant == BossVariant.GRADIENT) speed *= BossVariant.gradientSpeed(gradientHeat)
        return speed
    }

    /**
     * 0f at the spawn point, 1f at the server. Used for FIRST/LAST targeting.
     *
     * [routeLength] is supplied by the caller rather than stored here. With
     * more than one map there is no longer a single global answer to "how long
     * is lane 1", and the first attempt at this cached the length on the enemy
     * at spawn — which worked, and quietly broke every targeting test, because
     * anything that set `progress` without also setting the cached length made
     * every threat read as 100% advanced. A number that has to be kept in sync
     * is a number that will not be; asking the map each time cannot go stale.
     */
    fun pathFraction(routeLength: Float): Float =
        if (routeLength <= 0f) 0f else (progress / routeLength).coerceIn(0f, 1f)

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
        variantJamTimer = 0f
        isElite = false
        isBoss = false
        variant = BossVariant.BREACH
        revived = false
        split = false
        gradientHeat = 0f
        ransomTimer = 0f
        variantTimer = 0f
        hidden = false
        wormGeneration = 0
        decoy = false
        decoyOwner = null
        licenseHits.fill(0)
        recentAttackerNodes.fill(-1)
        recentAttackerTimes.fill(0f)
        recentAttackerCursor = 0
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
        private set

    /**
     * Jams this agent for [seconds], unless it is built not to care.
     *
     * The immunity check lives here rather than at the call site on purpose.
     * There is one thing that jams today and there are two more coming, and a
     * rule enforced by every caller remembering to check it is a rule that
     * eventually is not enforced. Asking the agent means a new jammer gets the
     * behaviour for free.
     */
    fun jam(seconds: Float) {
        if (type.immuneToJam || jamShielded) return
        if (seconds > disruptedFor) disruptedFor = seconds
    }

    /** Runs the jam timer down. */
    fun tickJam(dt: Float) {
        if (disruptedFor > 0f) disruptedFor -= dt
        if (ransomedFor > 0f) ransomedFor -= dt
    }

    /** Inside a CYBER OPERATIVE's field (♡3): no jam lands. Recomputed each tick. */
    var jamShielded: Boolean = false

    /** Seconds left on a RANSOM boss's upgrade lock; zero when free. */
    var ransomedFor: Float = 0f

    /** Buff contributed by nearby NETWORK_ARCHITECT agents; recomputed each tick. */
    var damageBuff: Float = 1f
    var rateBuff: Float = 1f

    var lifetimeKills: Int = 0
    var lifetimeDamage: Float = 0f

    /** ACE: seconds until a fallen wall is rebuilt. */
    var wallCooldown: Float = 0f

    /** AGENT FIRMWARE bought for this agent's type, as multipliers. */
    var firmwareDamage: Float = 1f
        private set
    var firmwareRate: Float = 1f
        private set
    var firmwareRange: Float = 1f
        private set

    fun applyFirmware(firmware: AgentFirmware) {
        firmwareDamage = firmware.damageMultiplier
        firmwareRate = firmware.rateMultiplier
        firmwareRange = firmware.rangeMultiplier
    }

    fun stats(): AgentStats = type.statsAtLevel(level)

    fun effectiveDamage(): Float = stats().damage * damageBuff * firmwareDamage

    fun effectiveCooldown(): Float {
        val rate = stats().fireRate * rateBuff * firmwareRate * (if (disruptedFor > 0f) 0.5f else 1f)
        return if (rate <= 0f) Float.MAX_VALUE else 1f / rate
    }

    fun range(): Float = stats().range * firmwareRange

    override fun reset() {
        active = false
        level = 1
        cooldownRemaining = 0f
        fireFlash = 0f
        upgradeFlash = 0f
        disruptedFor = 0f
        ransomedFor = 0f
        jamShielded = false
        damageBuff = 1f
        rateBuff = 1f
        targetingMode = TargetingMode.FIRST
        lifetimeKills = 0
        lifetimeDamage = 0f
        firmwareDamage = 1f
        firmwareRate = 1f
        firmwareRange = 1f
        wallCooldown = 0f
    }
}

/**
 * ACE's wall across a route (backlog ♡6). Where routes share a stretch of
 * road, one wall blocks every route through that point, so it is stored as a
 * stopping distance per route ([Float.NaN] where a route does not pass it).
 */
class Wall : Poolable {
    override var active = false
    var ownerNodeId: Int = -1
    var x: Float = 0f
    var y: Float = 0f
    var health: Float = 0f
    var maxHealth: Float = 1f
    var hitFlash: Float = 0f
    var progressByLane: FloatArray = FloatArray(0)

    fun progressAt(lane: Int): Float = progressByLane.getOrElse(lane) { Float.NaN }

    override fun reset() {
        active = false
        ownerNodeId = -1
        health = 0f
        hitFlash = 0f
        progressByLane = FloatArray(0)
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
    /** Drawn with emphasis: a heavy hit rather than a routine one. */
    var heavy: Boolean = false
    /** Remaining chain bounces (Quantum Defender). */
    var chainsLeft: Int = 0

    var angle: Float = 0f
    var travelled: Float = 0f

    override fun reset() {
        active = false
        targetEnemy = null
        chainsLeft = 0
        heavy = false
        ignoresArmor = false
        slowFactor = 1f
        slowDuration = 0f
        travelled = 0f
    }
}

/** Short-lived ASCII flourishes: hit sparks, death glyphs, floating numbers. */
enum class EffectKind {
    HIT, DEATH, BOSS_DEATH, DAMAGE_NUMBER, TEXT, CRYPTO_GAIN, UPGRADE,

    /**
     * The pixel blast a boss or elite leaves behind.
     *
     * One effect, not one per shard. Every shard's angle, speed and size is
     * derived in the renderer from [Effect.seed] and the effect's progress, so
     * a three-hundred-piece explosion costs a single pooled object and
     * allocates nothing — which is the rule the whole simulation is built on.
     */
    SHARD_BURST
}

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

    /** Seeds a derived effect, so two explosions never look identical. */
    var seed: Int = 0

    /**
     * A boss blast: every shard leaves the view area, the centre empties, and
     * the screen shakes briefly. Elites keep the smaller, contained burst.
     */
    var toEdge: Boolean = false

    /** True for the boss blast and its starburst: aged in real time, not game time. */
    val onRealTime: Boolean get() = toEdge || kind == EffectKind.BOSS_DEATH

    val progress: Float get() = if (lifetime <= 0f) 1f else (age / lifetime).coerceIn(0f, 1f)

    override fun reset() {
        active = false
        age = 0f
        text = ""
        scale = 1f
        velocityY = 0f
        seed = 0
        toEdge = false
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
