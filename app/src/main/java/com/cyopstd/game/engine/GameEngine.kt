package com.cyopstd.game.engine

import com.cyopstd.game.core.Balance
import com.cyopstd.game.core.GameMode
import com.cyopstd.game.core.WorldGeometry
import com.cyopstd.game.model.Agent
import com.cyopstd.game.model.AgentType
import com.cyopstd.game.model.Effect
import com.cyopstd.game.model.EffectKind
import com.cyopstd.game.model.Enemy
import com.cyopstd.game.model.EnemyType
import com.cyopstd.game.model.ObjectPool
import com.cyopstd.game.model.Projectile
import com.cyopstd.game.model.TargetingMode
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

    /**
     * The difficulty this run is played at.
     *
     * Applied as multipliers over the shared curves, so the hard mode inherits
     * every balance change made to the normal one.
     */
    var mode: GameMode = GameMode.STANDARD
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
    var activeBossModifiers: List<com.cyopstd.game.model.BossModifier> = emptyList()
        private set

    // ------------------------------------------------------------- wave state

    private var plan: WavePlan? = null
    private var waveTimer: Float = 0f
    private var nextOrderIndex: Int = 0

    /** How many packets of this wave have not yet been spawned or killed. */
    var enemiesRemaining: Int = 0
        private set

    // ------------------------------------------------------------- run totals

    var runAttacksBlocked: Int = 0
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

    /** Raised when a ten-wave milestone awards € BUDGET. */
    var onBudgetEarned: ((amount: Int, wave: Int) -> Unit)? = null

    /** Supplied by the app so the engine knows which agents the player owns. */
    var isAgentUnlocked: (AgentType) -> Boolean = { it.unlockedByDefault }

    // ---------------------------------------------------------------- systems

    private val enemySystem = EnemySystem(this, random)
    private val combatSystem = CombatSystem(this, random)
    private val projectileSystem = ProjectileSystem(this, random)
    private val effectSystem = EffectSystem(this, random)
    private val economySystem = EconomySystem(this)

    /**
     * Permanent damage multiplier bought with € BUDGET between runs. Supplied by
     * the app; 1f means no firmware. Applied to every shot every agent fires.
     */
    var firmwareDamageMultiplier: Float = 1f

    /** € BUDGET earned during this run, from ten-wave milestones. */
    var runBudgetEarned: Int = 0
        private set

    /** Reduced-effect mode; gameplay maths is untouched, only cosmetics thin out. */
    var batterySaver: Boolean = false

    /** Whether floating damage numbers are spawned at all. */
    var showDamageNumbers: Boolean = true

    // --------------------------------------------------------------- lifecycle

    /** Sets the mode for the next run. Takes effect on [startNewRun]. */
    fun selectMode(next: GameMode) {
        mode = next
        waveGenerator.mode = next
    }

    fun startNewRun() {
        enemies.clear()
        agents.clear()
        projectiles.clear()
        effects.clear()
        enemySystem.clearEscorts()

        phase = RunPhase.PREPARING
        currentWave = 0
        serverMaxHp = mode.serverHp
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

        runAttacksBlocked = 0
        runCryptoEarned = 0
        runBossesDefeated = 0
        runServerDamageTaken = 0
        runAgentsDeployed = 0
        runAgentUpgrades = 0
        runBudgetEarned = 0
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
        attacksBlocked: Int,
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
        runAttacksBlocked = attacksBlocked
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
        val bossBonus = Balance.bossClearBonus(currentWave)
        economySystem.award(bonus + bossBonus)

        val payout = if (bossBonus > 0) "+${bonus + bossBonus} (BOSS)" else "+$bonus"
        effectSystem.spawnText(
            WorldGeometry.WIDTH * 0.5f,
            WorldGeometry.HEIGHT * 0.34f,
            "WAVE $currentWave SECURED  \u25C7$payout",
            COLOR_SUCCESS,
            1.6f,
            scale = 1.5f
        )

        // Every tenth wave banks permanent meta-currency.
        val budget = Balance.budgetAward(currentWave)
        if (budget > 0) {
            runBudgetEarned += budget
            effectSystem.spawnText(
                WorldGeometry.WIDTH * 0.5f,
                WorldGeometry.HEIGHT * 0.44f,
                "\u20AC$budget BUDGET BANKED",
                COLOR_BUDGET,
                2.0f,
                scale = 1.4f
            )
            onBudgetEarned?.invoke(budget, currentWave)
        }

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

    /**
     * One packet from the current wave plan is off the board, either destroyed
     * or having reached the server. [enemy] is null when a spawn was skipped
     * because the pool was full — the wave still has to stop counting it, but
     * there is no entity to credit.
     */
    internal fun notifyEnemyRemoved(wasKilled: Boolean, enemy: Enemy?) {
        enemiesRemaining = (enemiesRemaining - 1).coerceAtLeast(0)
        if (wasKilled && enemy != null) {
            runAttacksBlocked++
            if (enemy.isBoss) runBossesDefeated++
        }
    }

    /**
     * A boss-replicated escort died. It was never part of the wave plan, so the
     * wave counter must not move — but the player still blocked a packet.
     */
    internal fun notifyEscortDestroyed() {
        runAttacksBlocked++
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

    fun upgradeAgent(nodeId: Int): Boolean = upgradeAgent(nodeId, times = 1) > 0

    /**
     * Buy up to [times] levels for the agent on [nodeId], stopping at the level
     * cap or when the crypto runs out. Returns how many levels were actually
     * bought.
     *
     * Bulk upgrading exists because agents go to level 100: tapping UPGRADE
     * ninety-nine times is not a design, it is an ordeal.
     */
    fun upgradeAgent(nodeId: Int, times: Int): Int {
        val agent = agentAt(nodeId) ?: return 0
        if (times <= 0) return 0

        var bought = 0
        while (bought < times && agent.level < Balance.MAX_AGENT_LEVEL) {
            val cost = agent.type.upgradeCost(agent.level)
            if (crypto < cost) break
            economySystem.spend(cost)
            agent.level++
            bought++
        }

        if (bought == 0) {
            soundListener?.invoke(GameSound.INSUFFICIENT)
            return 0
        }

        agent.upgradeFlash = 0.6f
        runAgentUpgrades += bought
        soundListener?.invoke(GameSound.AGENT_UPGRADED)
        hapticListener?.invoke(if (bought > 1) HapticCue.MEDIUM else HapticCue.LIGHT)
        effectSystem.spawnEffect(
            EffectKind.UPGRADE, agent.x, agent.y - 42f,
            "LV ${agent.level}", COLOR_CRYPTO, 0.9f
        )
        return bought
    }

    /** How many levels the current crypto balance could buy on [nodeId]. */
    fun affordableUpgrades(nodeId: Int): Int {
        val agent = agentAt(nodeId) ?: return 0
        var budget = crypto
        var level = agent.level
        var count = 0
        while (level < Balance.MAX_AGENT_LEVEL) {
            val cost = agent.type.upgradeCost(level)
            if (budget < cost) break
            budget -= cost
            level++
            count++
        }
        return count
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
        const val COLOR_BUDGET = 0xFF7CE0FF.toInt()
    }
}

/** Enemy types kept out of the hot import path for readability. */
internal fun EnemyType.isFastArchetype(): Boolean = baseSpeed >= 100f
