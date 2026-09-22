package com.packetbastion.asciidefense.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.packetbastion.asciidefense.audio.AudioEngine
import com.packetbastion.asciidefense.audio.HapticEngine
import com.packetbastion.asciidefense.core.Balance
import com.packetbastion.asciidefense.core.WorldGeometry
import com.packetbastion.asciidefense.engine.GameEngine
import com.packetbastion.asciidefense.engine.GameSound
import com.packetbastion.asciidefense.engine.PlacementResult
import com.packetbastion.asciidefense.engine.RunPhase
import com.packetbastion.asciidefense.model.AgentType
import com.packetbastion.asciidefense.model.TargetingMode
import com.packetbastion.asciidefense.save.GameRepository
import com.packetbastion.asciidefense.save.GameSettings
import com.packetbastion.asciidefense.save.PlayerStats
import com.packetbastion.asciidefense.save.SavedAgent
import com.packetbastion.asciidefense.save.SavedRun
import com.packetbastion.asciidefense.ui.game.BattlefieldSelection
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The single source of truth the UI talks to.
 *
 * It owns the engine, drives it from the Compose frame clock, mirrors settings
 * and progress out of DataStore, and turns engine events into sound, haptics and
 * persistence. Screens stay thin: they read state and call methods here.
 */
class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = GameRepository(application)
    private val audio = AudioEngine(application)
    private val haptics = HapticEngine(application)

    val engine = GameEngine()

    // ------------------------------------------------------------ UI state

    var hud by mutableStateOf(HudSnapshot())
        private set

    /** Bumped every simulated frame; the battlefield Canvas reads it to redraw. */
    var frameTick by mutableIntStateOf(0)
        private set

    /**
     * Seconds of wall-clock time since the run began; drives idle animations.
     * Backed by a float state so the per-frame write does not box.
     */
    var renderTime by mutableFloatStateOf(0f)
        private set

    var settings by mutableStateOf(GameSettings())
        private set

    var stats by mutableStateOf(PlayerStats())
        private set

    var unlockedAgents by mutableStateOf(AgentType.starters.map { it.name }.toSet())
        private set

    var tutorialCompleted by mutableStateOf(false)
        private set

    var hasSavedRun by mutableStateOf(false)
        private set

    var selection by mutableStateOf(BattlefieldSelection())
        private set

    var paused by mutableStateOf(false)
        private set

    var speedIndex by mutableIntStateOf(0)
        private set

    var showDeployPanel by mutableStateOf(false)
        private set

    /** Transient message shown over the battlefield (e.g. INSUFFICIENT CRYPTO). */
    var transientMessage by mutableStateOf<String?>(null)
        private set

    private var transientMessageExpiry: Long = 0L

    /** Agent unlock celebration currently on screen, if any. */
    var unlockBanner by mutableStateOf<AgentType?>(null)
        private set

    private var unlockBannerExpiry: Long = 0L

    var gameOverSummary by mutableStateOf<GameOverSummary?>(null)
        private set

    /** -1 means no tutorial running. */
    var tutorialStep by mutableIntStateOf(-1)
        private set

    var matchActive by mutableStateOf(false)
        private set

    private var runRecorded = false
    private var collectJobs = mutableListOf<Job>()

    val currentSpeed: Float get() = Balance.GAME_SPEEDS[speedIndex.coerceIn(0, Balance.GAME_SPEEDS.lastIndex)]

    // --------------------------------------------------------------- setup

    init {
        audio.initialize(viewModelScope)
        wireEngine()
        observePersistence()
    }

    private fun wireEngine() {
        engine.soundListener = { sound -> audio.play(sound) }
        engine.hapticListener = { cue -> haptics.fire(cue) }
        engine.isAgentUnlocked = { type -> type.name in unlockedAgents }

        engine.onAgentUnlocked = { type ->
            if (type.name !in unlockedAgents) {
                unlockedAgents = unlockedAgents + type.name
                viewModelScope.launch { repository.unlockAgent(type) }
                unlockBanner = type
                unlockBannerExpiry = System.currentTimeMillis() + UNLOCK_BANNER_MS
                audio.play(GameSound.UNLOCK)
            }
        }

        engine.onWaveCleared = { wave ->
            viewModelScope.launch {
                repository.updateHighestWave(wave)
                persistRun()
            }
        }

        engine.onGameOver = { onRunEnded() }
    }

    private fun observePersistence() {
        collectJobs += viewModelScope.launch {
            repository.settings.collectLatest { loaded ->
                settings = loaded
                applySettingsToSystems(loaded)
            }
        }
        collectJobs += viewModelScope.launch {
            repository.stats.collectLatest { loaded -> stats = loaded }
        }
        collectJobs += viewModelScope.launch {
            repository.progress.collectLatest { loaded ->
                unlockedAgents = loaded.unlockedAgents + AgentType.starters.map { it.name }
                tutorialCompleted = loaded.tutorialCompleted
            }
        }
        collectJobs += viewModelScope.launch {
            repository.savedRun.collectLatest { run -> hasSavedRun = run?.isResumable == true }
        }
    }

    private fun applySettingsToSystems(loaded: GameSettings) {
        audio.applyVolumes(loaded.musicVolume, loaded.sfxVolume)
        haptics.enabled = loaded.vibrationEnabled
        engine.batterySaver = loaded.batterySaver
        engine.showDamageNumbers = loaded.damageNumbers
        engine.autoStartWaves = loaded.autoStartWaves
    }

    // ----------------------------------------------------------- run control

    fun startNewGame() {
        engine.startNewRun()
        engine.autoStartWaves = settings.autoStartWaves
        engine.batterySaver = settings.batterySaver
        engine.showDamageNumbers = settings.damageNumbers
        selection = BattlefieldSelection()
        showDeployPanel = false
        paused = false
        speedIndex = 0
        gameOverSummary = null
        runRecorded = false
        matchActive = true
        tutorialStep = if (tutorialCompleted) -1 else 0
        pushHud()

        viewModelScope.launch {
            repository.recordGameStarted()
            repository.clearSavedRun()
        }
        audio.startAmbient()
    }

    /**
     * Resume a saved run. Loading is asynchronous, so the caller is told whether
     * it succeeded rather than assuming it did — navigating into an empty match
     * because the save had vanished would be worse than saying so.
     */
    fun continueGame(onLoaded: () -> Unit = {}, onFailed: () -> Unit = {}) {
        viewModelScope.launch {
            val run = repository.savedRun.first()
            if (run == null || !run.isResumable) {
                hasSavedRun = false
                onFailed()
                return@launch
            }
            engine.restore(
                wave = run.wave,
                serverHp = run.serverHp,
                crypto = run.crypto,
                placements = run.agents.map {
                    GameEngine.SavedPlacement(it.nodeId, it.type, it.level, it.targeting)
                },
                packetsBlocked = run.packetsBlocked,
                cryptoEarned = run.cryptoEarned,
                bossesDefeated = run.bossesDefeated,
                serverDamageTaken = run.serverDamageTaken,
                agentsDeployed = run.agentsDeployed,
                agentUpgrades = run.agentUpgrades
            )
            engine.autoStartWaves = settings.autoStartWaves
            engine.batterySaver = settings.batterySaver
            engine.showDamageNumbers = settings.damageNumbers
            selection = BattlefieldSelection()
            showDeployPanel = false
            paused = false
            speedIndex = 0
            gameOverSummary = null
            runRecorded = false
            matchActive = true
            tutorialStep = -1
            pushHud()
            audio.startAmbient()
            onLoaded()
        }
    }

    /** Frame tick, called from the Compose frame clock by the game screen. */
    fun onFrame(deltaSeconds: Float) {
        renderTime += deltaSeconds

        if (!paused && matchActive && engine.phase != RunPhase.GAME_OVER) {
            engine.update(deltaSeconds, currentSpeed)
        }

        frameTick++
        pushHud()
        expireTransients()
    }

    private fun expireTransients() {
        val now = System.currentTimeMillis()
        if (transientMessage != null && now > transientMessageExpiry) transientMessage = null
        if (unlockBanner != null && now > unlockBannerExpiry) unlockBanner = null
    }

    private fun pushHud() {
        val snapshot = HudSnapshot(
            phase = engine.phase,
            wave = engine.currentWave,
            bestWave = maxOf(stats.highestWave, engine.currentWave),
            serverHp = engine.serverHp,
            serverMaxHp = engine.serverMaxHp,
            crypto = engine.crypto,
            enemiesRemaining = engine.enemiesRemaining,
            enemiesOnField = engine.activeEnemyCount(),
            nextWaveIsBoss = engine.nextWaveIsBoss(),
            bossOnField = engine.bossOnField(),
            autoStartRemaining = kotlin.math.ceil(engine.autoStartRemaining).toInt()
        )
        if (snapshot != hud) hud = snapshot
    }

    fun togglePause() {
        paused = !paused
        audio.play(GameSound.UI_CLICK)
    }

    fun applyPaused(value: Boolean) {
        paused = value
    }

    fun cycleSpeed() {
        speedIndex = (speedIndex + 1) % Balance.GAME_SPEEDS.size
        audio.play(GameSound.UI_CLICK)
    }

    fun applySpeedIndex(index: Int) {
        speedIndex = index.coerceIn(0, Balance.GAME_SPEEDS.lastIndex)
        audio.play(GameSound.UI_CLICK)
    }

    fun startNextWave() {
        if (!engine.canStartNextWave()) return
        engine.startNextWave()
        audio.play(GameSound.UI_CLICK)
        // Starting a wave is the last tutorial beat, whatever step the player
        // happens to be on when they do it.
        if (tutorialStep >= 0) completeTutorial()
        pushHud()
    }

    // ------------------------------------------------------- deployment flow

    fun toggleDeployPanel() {
        showDeployPanel = !showDeployPanel
        if (showDeployPanel) {
            selection = selection.copy(selectedNodeId = null)
            // The tutorial advances on the action itself, not on having
            // acknowledged the previous card. A player who ignores the prompts
            // and just plays must never be left staring at a stale step.
            if (tutorialStep in TUTORIAL_INTRO..TUTORIAL_TAP_AGENTS) {
                tutorialStep = TUTORIAL_SELECT_FIREWALL
            }
        } else {
            selection = selection.copy(pendingAgent = null)
        }
        audio.play(GameSound.UI_CLICK)
    }

    fun choosePendingAgent(type: AgentType) {
        if (type.name !in unlockedAgents) {
            showTransient("AGENT LOCKED — REACH WAVE ${type.unlockWave}")
            return
        }
        selection = selection.copy(pendingAgent = type, selectedNodeId = null)
        showDeployPanel = false
        audio.play(GameSound.UI_CLICK)
        // Any agent advances the step; the tutorial suggests FIREWALL, it does
        // not insist on it.
        if (tutorialStep in TUTORIAL_INTRO..TUTORIAL_SELECT_FIREWALL) {
            tutorialStep = TUTORIAL_TAP_NODE
        }
    }

    fun clearPendingAgent() {
        selection = selection.copy(pendingAgent = null)
    }

    /**
     * Handle a tap on the battlefield. Tapping a node places or selects; tapping
     * empty space closes whatever was open. Hit radii are generous — this is a
     * phone, not a mouse.
     */
    fun onBattlefieldTap(worldPoint: Offset) {
        val node = nearestNode(worldPoint)
        val pending = selection.pendingAgent

        if (node == null) {
            selection = BattlefieldSelection()
            return
        }

        val occupant = engine.agentAt(node.id)

        if (pending != null && occupant == null) {
            when (engine.placeAgent(pending, node.id)) {
                PlacementResult.SUCCESS -> {
                    selection = BattlefieldSelection()
                    if (tutorialStep in TUTORIAL_INTRO..TUTORIAL_TAP_NODE) {
                        tutorialStep = TUTORIAL_START_WAVE
                    }
                }
                PlacementResult.INSUFFICIENT_CRYPTO -> showTransient("INSUFFICIENT CRYPTO")
                PlacementResult.NODE_OCCUPIED -> showTransient("NODE OCCUPIED")
                PlacementResult.AGENT_LOCKED -> showTransient("AGENT LOCKED")
                PlacementResult.NODE_INVALID -> showTransient("INVALID NODE")
                PlacementResult.NO_CAPACITY -> showTransient("DEPLOYMENT LIMIT REACHED")
            }
            pushHud()
            return
        }

        if (occupant != null) {
            selection = BattlefieldSelection(selectedNodeId = node.id)
            showDeployPanel = false
            audio.play(GameSound.UI_CLICK)
        } else {
            selection = BattlefieldSelection()
        }
    }

    private fun nearestNode(point: Offset): com.packetbastion.asciidefense.core.NodePosition? {
        var best: com.packetbastion.asciidefense.core.NodePosition? = null
        var bestDistanceSq = Float.MAX_VALUE
        for (node in WorldGeometry.nodes) {
            val dx = node.x - point.x
            val dy = node.y - point.y
            val distanceSq = dx * dx + dy * dy
            if (distanceSq < bestDistanceSq) {
                bestDistanceSq = distanceSq
                best = node
            }
        }
        val tapRadius = WorldGeometry.NODE_RADIUS * TAP_RADIUS_MULTIPLIER
        return if (bestDistanceSq <= tapRadius * tapRadius) best else null
    }

    fun closeSelection() {
        selection = BattlefieldSelection()
    }

    fun upgradeSelectedAgent() {
        val nodeId = selection.selectedNodeId ?: return
        if (!engine.upgradeAgent(nodeId)) {
            showTransient("INSUFFICIENT CRYPTO")
        }
        pushHud()
    }

    fun sellSelectedAgent() {
        val nodeId = selection.selectedNodeId ?: return
        engine.sellAgent(nodeId)
        selection = BattlefieldSelection()
        pushHud()
    }

    fun cycleTargetingMode() {
        val nodeId = selection.selectedNodeId ?: return
        val agent = engine.agentAt(nodeId) ?: return
        if (!agent.type.allowsTargetingModes) return
        val next = TargetingMode.entries[(agent.targetingMode.ordinal + 1) % TargetingMode.entries.size]
        engine.setTargetingMode(nodeId, next)
        audio.play(GameSound.UI_CLICK)
    }

    fun showTransient(message: String) {
        transientMessage = message
        transientMessageExpiry = System.currentTimeMillis() + TRANSIENT_MS
    }

    // --------------------------------------------------------------- tutorial

    fun skipTutorial() {
        completeTutorial()
    }

    private fun completeTutorial() {
        if (tutorialStep < 0) return
        tutorialStep = -1
        if (!tutorialCompleted) {
            tutorialCompleted = true
            viewModelScope.launch { repository.setTutorialCompleted(true) }
        }
    }

    fun advanceTutorial() {
        if (tutorialStep == TUTORIAL_INTRO) tutorialStep = TUTORIAL_TAP_AGENTS
    }

    // ------------------------------------------------------- run termination

    private fun onRunEnded() {
        if (runRecorded) return
        runRecorded = true
        matchActive = false
        audio.stopAmbient()

        val isRecord = engine.currentWave > stats.highestWave
        gameOverSummary = GameOverSummary(
            waveReached = engine.currentWave,
            packetsBlocked = engine.runPacketsBlocked,
            cryptoEarned = engine.runCryptoEarned,
            bossesDefeated = engine.runBossesDefeated,
            bestWave = maxOf(stats.highestWave, engine.currentWave),
            isNewRecord = isRecord
        )

        viewModelScope.launch {
            repository.recordRunResult(
                waveReached = engine.currentWave,
                packetsBlocked = engine.runPacketsBlocked,
                bossesDefeated = engine.runBossesDefeated,
                cryptoEarned = engine.runCryptoEarned,
                serverDamageTaken = engine.runServerDamageTaken,
                agentsDeployed = engine.runAgentsDeployed,
                agentUpgrades = engine.runAgentUpgrades,
                deploymentsByType = engine.runDeploymentsByType.mapKeys { it.key.name },
                countAsGamePlayed = false
            )
            repository.clearSavedRun()
        }
    }

    /**
     * Leaving an active match: persist it so CONTINUE works, and fold the run's
     * totals into lifetime statistics so nothing is lost by walking away.
     */
    fun leaveMatch() {
        if (!matchActive) return
        viewModelScope.launch {
            persistRun()
            if (!runRecorded) {
                runRecorded = true
                repository.recordRunResult(
                    waveReached = engine.currentWave,
                    packetsBlocked = engine.runPacketsBlocked,
                    bossesDefeated = engine.runBossesDefeated,
                    cryptoEarned = engine.runCryptoEarned,
                    serverDamageTaken = engine.runServerDamageTaken,
                    agentsDeployed = engine.runAgentsDeployed,
                    agentUpgrades = engine.runAgentUpgrades,
                    deploymentsByType = engine.runDeploymentsByType.mapKeys { it.key.name },
                    countAsGamePlayed = false
                )
            }
        }
        matchActive = false
        paused = false
        audio.stopAmbient()
    }

    /** Called from onStop so an app kill never costs the player their run. */
    fun saveIfActive() {
        if (!matchActive || engine.phase == RunPhase.GAME_OVER) return
        viewModelScope.launch { persistRun() }
    }

    private suspend fun persistRun() {
        if (engine.phase == RunPhase.GAME_OVER || engine.serverHp <= 0) return
        // The wave in progress is stored as-is and replayed on resume; a
        // half-finished assault cannot be reconstructed meaningfully.
        val waveToResume = if (engine.phase == RunPhase.PREPARING) {
            engine.currentWave
        } else {
            (engine.currentWave - 1).coerceAtLeast(0)
        }
        repository.saveRun(
            SavedRun(
                wave = waveToResume,
                serverHp = engine.serverHp,
                crypto = engine.crypto,
                agents = engine.snapshotPlacements().map {
                    SavedAgent(it.nodeId, it.agentTypeName, it.level, it.targetingOrdinal)
                },
                packetsBlocked = engine.runPacketsBlocked,
                cryptoEarned = engine.runCryptoEarned,
                bossesDefeated = engine.runBossesDefeated,
                serverDamageTaken = engine.runServerDamageTaken,
                agentsDeployed = engine.runAgentsDeployed,
                agentUpgrades = engine.runAgentUpgrades,
                savedAtMillis = System.currentTimeMillis()
            )
        )
        hasSavedRun = true
    }

    fun restartAfterGameOver() {
        startNewGame()
    }

    fun abandonMatch() {
        matchActive = false
        gameOverSummary = null
        audio.stopAmbient()
    }

    // ---------------------------------------------------------------- settings

    fun updateSettings(transform: (GameSettings) -> GameSettings) {
        val updated = transform(settings)
        settings = updated
        applySettingsToSystems(updated)
        viewModelScope.launch { repository.updateSettings { updated } }
    }

    fun resetAllProgress() {
        viewModelScope.launch {
            repository.resetAllProgress()
            unlockedAgents = AgentType.starters.map { it.name }.toSet()
            tutorialCompleted = false
            hasSavedRun = false
            stats = PlayerStats()
            settings = GameSettings()
            applySettingsToSystems(settings)
        }
        engine.startNewRun()
        matchActive = false
        gameOverSummary = null
    }

    fun playClick() {
        audio.play(GameSound.UI_CLICK)
    }

    fun onAppPaused() {
        audio.stopAmbient()
        saveIfActive()
    }

    fun onAppResumed() {
        if (matchActive && settings.musicVolume > 0.01f) audio.startAmbient()
    }

    override fun onCleared() {
        super.onCleared()
        collectJobs.forEach { it.cancel() }
        audio.release()
    }

    companion object {
        const val TUTORIAL_INTRO = 0
        const val TUTORIAL_TAP_AGENTS = 1
        const val TUTORIAL_SELECT_FIREWALL = 2
        const val TUTORIAL_TAP_NODE = 3
        const val TUTORIAL_START_WAVE = 4

        private const val TRANSIENT_MS = 1600L
        private const val UNLOCK_BANNER_MS = 3200L
        private const val TAP_RADIUS_MULTIPLIER = 2.0f
    }
}
