package com.packetbastion.asciidefense.engine

import com.packetbastion.asciidefense.core.Balance
import com.packetbastion.asciidefense.core.WorldGeometry
import com.packetbastion.asciidefense.model.Agent
import com.packetbastion.asciidefense.model.AgentType
import com.packetbastion.asciidefense.model.Effect
import com.packetbastion.asciidefense.model.EffectKind
import com.packetbastion.asciidefense.model.Enemy
import com.packetbastion.asciidefense.model.EnemyType
import com.packetbastion.asciidefense.model.ObjectPool
import com.packetbastion.asciidefense.model.Projectile
import com.packetbastion.asciidefense.model.TargetingMode
import kotlin.math.min
import kotlin.random.Random

/**
 * The authoritative simulation.
 *
 * The engine owns every entity pool and all run state, and advances the whole
 * world with [update]. It is pure Kotlin — no Android, no Compose — so the same
 * class can be driven by a frame clock in the app or by a plain loop in a test.
 *
 * Division of labour:
 *  - [WaveGenerator] decides *what* spawns.
 *  - [EnemySystem] moves packets and runs boss modifier behaviour.
 *  - [CombatSystem] decides what each agent shoots at and when.
 *  - [ProjectileSystem] flies shots and resolves damage.
 *  - [EffectSystem] owns the short-lived ASCII flourishes.
 *  - [EconomySystem] is the single place crypto is added or removed.
 */
class GameEngine(
    private val random: Random = Random.Default,
    private val waveGenerator: WaveGenerator = WaveGenerator(random)
) {

    // ------------------------------------------------------------ entity pools

    val enemies = ObjectPool(MAX_ENEMIES) { Enemy() }
    val agents = ObjectPool(WorldGeometry.nodes.size) { Agent() }
    val projectiles = ObjectPool(MAX_PROJECTILES) { Projectile() }
    val effects = ObjectPool(MAX_EFFECTS) { Effect() }

    // -------------------------------------------------------------- run state

    var phase: RunPhase = RunPhase.PREPARING
        private set

    var currentWave: Int = 0
        private set

    var serverMaxHp: Int = Balance.SERVER_MAX_HP
        private set

    var serverHp: Int = Balance.SERVER_MAX_HP
        private set

    var crypto: Int = Balance.STARTING_CRYPTO
        private set

    /** Wall-clock seconds of simulated time in this run. Drives idle animations. */
    var elapsedTime: Float = 0f
        private set

    /** Seconds left on the boss-warning banner. */
    var bossWarningRemaining: Float = 0f
        private set

    /** Countdown to the next wave when auto-start is enabled. */
    var autoStartRemaining: Float = 0f
        private set

    var autoStartWaves: Boolean = false

    /** Seconds remaining on the server damage flash/shake. */
    var serverHitFlash: Float = 0f
        private set

    /** Modifiers on the boss wave currently being fought (for the HUD banner). */
    var activeBossModifiers: List<com.packetbastion.asciidefense.model.BossModifier> = emptyList()
        private set

    // ------------------------------------------------------------- wave state

    private var plan: WavePlan? = null
    private var waveTimer: Float = 0f
    private var nextOrderIndex: Int = 0

    /** How many packets of this wave have not yet been spawned or killed. */
    var enemiesRemaining: Int = 0
        private set

    // ------------------------------------------------------------- run totals

    var runPacketsBlocked: Int = 0
        private set
    var runCryptoEarned: Int = 0
        private set
    var runBossesDefeated: Int = 0
        private set
    var runServerDamageTaken: Int = 0
        private set
    var runAgentsDeployed: Int = 0
        private set
    var runAgentUpgrades: Int = 0
        private set

    /** Deployment counts per agent type, used to work out the "favourite agent". */
    val runDeploymentsByType: MutableMap<AgentType, Int> = LinkedHashMap()

    /** Highest wave reached in this run (equals currentWave, kept for clarity). */
    val runHighestWave: Int get() = currentWave

    // ------------------------------------------------------------- listeners

    var soundListener: ((GameSound) -> Unit)? = null
    var hapticListener: ((HapticCue) -> Unit)? = null

    /** Raised when a wave completes; the app uses it to persist progress. */
    var onWaveCleared: ((Int) -> Unit)? = null

    /** Raised once per run when integrity hits zero. */
    var onGameOver: (() -> Unit)? = null

    /** Raised when a wave milestone unlocks a new agent. */
    var onAgentUnlocked: ((AgentType) -> Unit)? = null

    /** Supplied by the app so the engine knows which agents the player owns. */
    var isAgentUnlocked: (AgentType) -> Boolean = { it.unlockedByDefault }

    // ---------------------------------------------------------------- systems

    private val enemySystem = EnemySystem(this, random)
    private val combatSystem = CombatSystem(this, random)
    private val projectileSystem = ProjectileSystem(this, random)
    private val effectSystem = EffectSystem(this, random)
    private val economySystem = EconomySystem(this)

    /** Reduced-effect mode; gameplay maths is untouched, only cosmetics thin out. */
    var batterySaver: Boolean = false

    /** Whether floating damage numbers are spawned at all. */
    var showDamageNumbers: Boolean = true

    // --------------------------------------------------------------- lifecycle

    fun startNewRun() {
        enemies.clear()
        agents.clear()
        projectiles.clear()
        effects.clear()
        enemySystem.clearEscorts()

        phase = RunPhase.PREPARING
        currentWave = 0
        serverMaxHp = Balance.SERVER_MAX_HP
        serverHp = serverMaxHp
        crypto = Balance.STARTING_CRYPTO
        elapsedTime = 0f
        bossWarningRemaining = 0f
        autoStartRemaining = 0f
        serverHitFlash = 0f
        activeBossModifiers = emptyList()

        plan = null
        waveTimer = 0f
        nextOrderIndex = 0
        enemiesRemaining = 0

        runPacketsBlocked = 0
        runCryptoEarned = 0
        runBossesDefeated = 0
        runServerDamageTaken = 0
        runAgentsDeployed = 0
        runAgentUpgrades = 0
        runDeploymentsByType.clear()
    }

    /**
     * Rebuild a run from a saved snapshot. Packets that were mid-flight are not
     * persisted; the player resumes at the start of the wave they were fighting,
     * which is both simpler and kinder than dropping them into a half-finished
     * assault with no idea what is already on the board.
     */
    fun restore(
        wave: Int,
        serverHp: Int,
        crypto: Int,
        placements: List<SavedPlacement>,
        packetsBlocked: Int,
        cryptoEarned: Int,
        bossesDefeated: Int,
        serverDamageTaken: Int,
        agentsDeployed: Int,
        agentUpgrades: Int
    ) {
        startNewRun()
        currentWave = wave.coerceAtLeast(0)
        this.serverHp = serverHp.coerceIn(1, serverMaxHp)
        this.crypto = crypto.coerceAtLeast(0)
        runPacketsBlocked = packetsBlocked
        runCryptoEarned = cryptoEarned
        runBossesDefeated = bossesDefeated
        runServerDamageTaken = serverDamageTaken
        runAgentsDeployed = agentsDeployed
        runAgentUpgrades = agentUpgrades

        for (placement in placements) {
            val type = AgentType.fromNameSafe(placement.agentTypeName) ?: continue
            val node = WorldGeometry.node(placement.nodeId) ?: continue
            if (agentAt(placement.nodeId) != null) continue
            val agent = agents.obtain() ?: continue
            agent.reset()
            agent.active = true
            agent.type = type
            agent.nodeId = node.id
            agent.x = node.x
            agent.y = node.y
            agent.level = placement.level.coerceIn(1, Balance.MAX_AGENT_LEVEL)
            agent.targetingMode = TargetingMode.fromOrdinalSafe(placement.targetingOrdinal)
        }

        // The wave the player was on has not been completed, so it is replayed.
        phase = RunPhase.PREPARING
    }

    /** A persistable agent placement. */
    data class SavedPlacement(
        val nodeId: Int,
        val agentTypeName: String,
        val level: Int,
        val targetingOrdinal: Int
    )

    fun snapshotPlacements(): List<SavedPlacement> {
        val result = ArrayList<SavedPlacement>()
        for (agent in agents.items) {
            if (!agent.active) continue
            result += SavedPlacement(
                nodeId = agent.nodeId,
                agentTypeName = agent.type.name,
                level = agent.level,
                targetingOrdinal = agent.targetingMode.ordinal
            )
        }
        return result
    }

    // ------------------------------------------------------------ wave control

    fun canStartNextWave(): Boolean = phase == RunPhase.PREPARING

    fun startNextWave() {
        if (phase != RunPhase.PREPARING) return
        currentWave += 1
        val newPlan = waveGenerator.generate(currentWave)
        plan = newPlan
        waveTimer = 0f
        nextOrderIndex = 0
        enemiesRemaining = newPlan.enemyCount
        autoStartRemaining = 0f
        activeBossModifiers = newPlan.bossModifiers

        if (newPlan.isBossWave) {
            phase = RunPhase.BOSS_WARNING
            bossWarningRemaining = Balance.BOSS_WARNING_SECONDS
            soundListener?.invoke(GameSound.BOSS_WARNING)
            hapticListener?.invoke(HapticCue.BOSS_ALERT)
        } else {
            phase = RunPhase.IN_WAVE
            soundListener?.invoke(GameSound.WAVE_START)
        }

        unlockAgentsForWave(currentWave)
    }

    private fun unlockAgentsForWave(wave: Int) {
        for (type in AgentType.entries) {
            if (type.unlockWave == wave && wave > 0) {
                onAgentUnlocked?.invoke(type)
            }
        }
    }

    // ------------------------------------------------------------- simulation

    /**
     * Advance the world by [realDelta] seconds scaled by [speed].
     *
     * Large steps are split into sub-steps so that a stalled frame (or 3x speed)
     * cannot let a fast packet tunnel past an agent's range without ever being
     * inside it.
     */
    fun update(realDelta: Float, speed: Float) {
        if (phase == RunPhase.GAME_OVER) return

        val clamped = realDelta.coerceIn(0f, Balance.MAX_FRAME_DELTA)
        var remaining = clamped * speed
        var guard = 0
        while (remaining > 0f && guard < MAX_SUBSTEPS) {
            val step = min(remaining, MAX_SUBSTEP_SECONDS)
            step(step)
            remaining -= step
            guard++
            if (phase == RunPhase.GAME_OVER) return
        }
    }

    private fun step(dt: Float) {
        elapsedTime += dt
        if (serverHitFlash > 0f) serverHitFlash -= dt

        when (phase) {
            RunPhase.BOSS_WARNING -> {
                bossWarningRemaining -= dt
                if (bossWarningRemaining <= 0f) {
                    bossWarningRemaining = 0f
                    phase = RunPhase.IN_WAVE
                    soundListener?.invoke(GameSound.WAVE_START)
                }
            }

            RunPhase.PREPARING -> {
                if (autoStartWaves && autoStartRemaining > 0f) {
                    autoStartRemaining -= dt
                    if (autoStartRemaining <= 0f) {
                        autoStartRemaining = 0f
                        startNextWave()
                    }
                }
            }

            else -> Unit
        }

        if (phase == RunPhase.IN_WAVE) {
            waveTimer += dt
            processSpawns()
        }

        combatSystem.refreshBuffs()
        enemySystem.update(dt)
        combatSystem.update(dt)
        projectileSystem.update(dt)
        effectSystem.update(dt)

        if (phase == RunPhase.IN_WAVE && isWaveFinished()) {
            completeWave()
        }
    }

    private fun processSpawns() {
        val activePlan = plan ?: return
        while (nextOrderIndex < activePlan.orders.size) {
            val order = activePlan.orders[nextOrderIndex]
            if (order.time > waveTimer) break
            enemySystem.spawn(order, currentWave)
            nextOrderIndex++
        }
    }

    private fun isWaveFinished(): Boolean {
        val activePlan = plan ?: return false
        if (nextOrderIndex < activePlan.orders.size) return false
        return enemies.activeCount() == 0
    }

    private fun completeWave() {
        phase = RunPhase.PREPARING
        activeBossModifiers = emptyList()
        val bonus = Balance.waveClearBonus(currentWave)
        economySystem.award(bonus)
        effectSystem.spawnText(
            WorldGeometry.WIDTH * 0.5f,
            WorldGeometry.HEIGHT * 0.34f,
            "WAVE $currentWave SECURED  +$bonus",
            COLOR_SUCCESS,
            1.6f,
            scale = 1.5f
        )
        soundListener?.invoke(GameSound.WAVE_CLEARED)
        onWaveCleared?.invoke(currentWave)
        if (autoStartWaves) autoStartRemaining = Balance.AUTO_START_DELAY
    }

    // ------------------------------------------------------------ server state

    /** Called by [EnemySystem] when a packet reaches CORE-SERVER. */
    internal fun damageServer(amount: Int) {
        if (phase == RunPhase.GAME_OVER) return
        serverHp = (serverHp - amount).coerceAtLeast(0)
        runServerDamageTaken += amount
        serverHitFlash = 0.45f
        soundListener?.invoke(GameSound.SERVER_DAMAGE)
        hapticListener?.invoke(if (amount >= 5) HapticCue.HEAVY else HapticCue.MEDIUM)
        if (serverHp <= 0) {
            phase = RunPhase.GAME_OVER
            soundListener?.invoke(GameSound.GAME_OVER)
            hapticListener?.invoke(HapticCue.GAME_OVER)
            onGameOver?.invoke()
        }
    }

    internal fun notifyEnemyRemoved(wasKilled: Boolean, enemy: Enemy) {
        enemiesRemaining = (enemiesRemaining - 1).coerceAtLeast(0)
        if (wasKilled) {
            runPacketsBlocked++
            if (enemy.isBoss) runBossesDefeated++
        }
    }

    /**
     * A boss-replicated escort died. It was never part of the wave plan, so the
     * wave counter must not move — but the player still blocked a packet.
     */
    internal fun notifyEscortDestroyed() {
        runPacketsBlocked++
    }

    // ------------------------------------------------------------ agent actions

    fun agentAt(nodeId: Int): Agent? {
        for (agent in agents.items) {
            if (agent.active && agent.nodeId == nodeId) return agent
        }
        return null
    }

    fun placeAgent(type: AgentType, nodeId: Int): PlacementResult {
        val node = WorldGeometry.node(nodeId) ?: return PlacementResult.NODE_INVALID
        if (!isAgentUnlocked(type)) return PlacementResult.AGENT_LOCKED
        if (agentAt(nodeId) != null) return PlacementResult.NODE_OCCUPIED
        if (crypto < type.cost) {
            soundListener?.invoke(GameSound.INSUFFICIENT)
            return PlacementResult.INSUFFICIENT_CRYPTO
        }
        val agent = agents.obtain() ?: return PlacementResult.NO_CAPACITY

        economySystem.spend(type.cost)
        agent.reset()
        agent.active = true
        agent.type = type
        agent.nodeId = node.id
        agent.x = node.x
        agent.y = node.y
        agent.level = 1
        agent.upgradeFlash = 0.5f

        runAgentsDeployed++
        runDeploymentsByType[type] = (runDeploymentsByType[type] ?: 0) + 1

        soundListener?.invoke(GameSound.AGENT_PLACED)
        hapticListener?.invoke(HapticCue.LIGHT)
        effectSystem.spawnText(node.x, node.y - 40f, "DEPLOYED", COLOR_FRIENDLY, 0.8f)
        return PlacementResult.SUCCESS
    }

    fun upgradeAgent(nodeId: Int): Boolean {
        val agent = agentAt(nodeId) ?: return false
        if (agent.level >= Balance.MAX_AGENT_LEVEL) return false
        val cost = agent.type.upgradeCost(agent.level)
        if (crypto < cost) {
            soundListener?.invoke(GameSound.INSUFFICIENT)
            return false
        }
        economySystem.spend(cost)
        agent.level++
        agent.upgradeFlash = 0.6f
        runAgentUpgrades++
        soundListener?.invoke(GameSound.AGENT_UPGRADED)
        hapticListener?.invoke(if (agent.level % 5 == 0) HapticCue.MEDIUM else HapticCue.LIGHT)
        effectSystem.spawnEffect(
            EffectKind.UPGRADE, agent.x, agent.y - 42f,
            "LV ${agent.level}", COLOR_CRYPTO, 0.9f
        )
        return true
    }

    fun sellAgent(nodeId: Int): Boolean {
        val agent = agentAt(nodeId) ?: return false
        val refund = agent.type.sellValue(agent.level)
        economySystem.award(refund, countAsEarned = false)
        effectSystem.spawnText(agent.x, agent.y - 40f, "+$refund", COLOR_CRYPTO, 0.9f)
        agent.reset()
        soundListener?.invoke(GameSound.AGENT_SOLD)
        hapticListener?.invoke(HapticCue.LIGHT)
        return true
    }

    fun setTargetingMode(nodeId: Int, mode: TargetingMode) {
        agentAt(nodeId)?.targetingMode = mode
    }

    // ------------------------------------------------------------ economy hooks

    internal fun addCrypto(amount: Int, countAsEarned: Boolean) {
        crypto += amount
        if (countAsEarned) runCryptoEarned += amount
    }

    internal fun removeCrypto(amount: Int) {
        crypto = (crypto - amount).coerceAtLeast(0)
    }

    // ------------------------------------------------------------- accessors

    internal fun effectSystem(): EffectSystem = effectSystem
    internal fun economySystem(): EconomySystem = economySystem
    internal fun projectileSystem(): ProjectileSystem = projectileSystem
    internal fun enemySystem(): EnemySystem = enemySystem
    internal fun combatSystem(): CombatSystem = combatSystem

    /** Live count of packets currently on the battlefield. */
    fun activeEnemyCount(): Int = enemies.activeCount()

    fun activeAgentCount(): Int = agents.activeCount()

    /** Whether a boss is currently alive (drives the HUD's alert border). */
    fun bossOnField(): Boolean {
        for (enemy in enemies.items) if (enemy.active && enemy.isBoss) return true
        return false
    }

    fun isBossWave(): Boolean = Balance.isBossWave(currentWave)

    fun nextWaveIsBoss(): Boolean = Balance.isBossWave(currentWave + 1)

    companion object {
        /**
         * Pool sizes. These are hard caps on how much the renderer can ever be
         * asked to draw, which is deliberate: a phone is much happier refusing a
         * 200th simultaneous packet than trying to draw it.
         */
        const val MAX_ENEMIES = 72
        const val MAX_PROJECTILES = 140
        const val MAX_EFFECTS = 110

        private const val MAX_SUBSTEP_SECONDS = 0.02f
        private const val MAX_SUBSTEPS = 12

        const val COLOR_SUCCESS = 0xFF00FF9C.toInt()
        const val COLOR_FRIENDLY = 0xFF00E5FF.toInt()
        const val COLOR_CRYPTO = 0xFFFFC94D.toInt()
        const val COLOR_HOSTILE = 0xFFFF4D6A.toInt()
        const val COLOR_WARNING = 0xFFFF8A3D.toInt()
        const val COLOR_NEUTRAL = 0xFFD7E3F4.toInt()
    }
}

/** Enemy types kept out of the hot import path for readability. */
internal fun EnemyType.isFastArchetype(): Boolean = baseSpeed >= 100f
