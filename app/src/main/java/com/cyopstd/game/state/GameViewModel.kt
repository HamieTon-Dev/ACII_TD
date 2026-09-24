package com.cyopstd.game.state

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.cyopstd.game.audio.AudioEngine
import com.cyopstd.game.audio.HapticEngine
import com.cyopstd.game.audio.trackForMode
import com.cyopstd.game.core.Balance
import android.util.Log
import com.cyopstd.game.ads.AdGateway
import com.cyopstd.game.ads.AdMobGateway
import com.cyopstd.game.ads.PlayServices
import com.cyopstd.game.ads.AdPolicy
import com.cyopstd.game.ads.NoAdGateway
import com.cyopstd.game.core.GameMode
import com.cyopstd.game.model.BossModifier
import com.cyopstd.game.ui.game.BossDossier
import com.cyopstd.game.ui.game.TutorialGate
import com.cyopstd.game.ui.game.TutorialScript
import com.cyopstd.game.save.LeaderboardEntry
import com.cyopstd.game.save.LeaderboardGateway
import com.cyopstd.game.save.LocalLeaderboard
import com.cyopstd.game.save.PlayerIdentity
import com.cyopstd.game.store.BillingGateway
import com.cyopstd.game.store.BillingStatus
import com.cyopstd.game.store.CosmeticChoice
import com.cyopstd.game.store.Entitlements
import com.cyopstd.game.store.NoBillingGateway
import com.cyopstd.game.store.PlayBillingGateway
import com.cyopstd.game.store.Sku
import com.cyopstd.game.core.WorldGeometry
import com.cyopstd.game.engine.GameEngine
import com.cyopstd.game.engine.GameSound
import com.cyopstd.game.engine.PlacementResult
import com.cyopstd.game.engine.RunPhase
import com.cyopstd.game.model.AgentType
import com.cyopstd.game.model.TargetingMode
import com.cyopstd.game.save.CloudSaveGateway
import com.cyopstd.game.save.CloudSaveStatus
import com.cyopstd.game.save.CloudSaveSync
import com.cyopstd.game.save.CloudSyncResult
import com.cyopstd.game.save.GameRepository
import com.cyopstd.game.save.GameSettings
import com.cyopstd.game.save.NoCloudSaveGateway
import com.cyopstd.game.save.PlayGamesCloudSave
import com.cyopstd.game.save.PlayerStats
import com.cyopstd.game.save.SavedAgent
import com.cyopstd.game.save.SavedRun
import com.cyopstd.game.ui.game.BattlefieldSelection
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
class GameViewModel @JvmOverloads constructor(
    application: Application,
    /**
     * Injectable so tests can supply an isolated store. Production always uses
     * the default, which is the app's single shared repository.
     */
    private val repository: GameRepository = GameRepository(application),
    /**
     * Injectable so the revive can be tested without an AdMob account.
     *
     * Production passes nothing and gets the gateway the build is configured
     * for, exactly as before. There is no way to reach a test gateway from a
     * shipped build.
     */
    adsOverride: AdGateway? = null
) : AndroidViewModel(application) {

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

    /** Unspent € BUDGET. */
    var budget by mutableStateOf(0L)
        private set

    /** Purchased CORE FIRMWARE level. */
    var firmwareLevel by mutableIntStateOf(0)
        private set

    var lifetimeBudgetEarned by mutableStateOf(0L)
        private set

    /** The damage multiplier the current firmware level is worth. */
    val firmwareMultiplier: Float
        get() = Balance.firmwareDamageMultiplier(firmwareLevel)

    /**
     * The between-waves banner sits over the battlefield, so it hides itself
     * rather than covering the top lane's deployment nodes indefinitely.
     */
    var prepBannerVisible by mutableStateOf(false)
        private set

    private var prepBannerExpiry = 0L
    private var lastPhaseSeen: RunPhase? = null

    var hasSavedRun by mutableStateOf(false)
        private set

    var selection by mutableStateOf(BattlefieldSelection())
        private set

    var paused by mutableStateOf(false)
        private set

    var speedIndex by mutableIntStateOf(0)
        private set

    /**
     * Whether the fifth simulation speed is available. Set from the store, and
     * deliberately not private: the store is the owner of this fact, not the
     * view model.
     */
    var fifthSpeedUnlocked by mutableStateOf(false)

    /** The boss dossier, open or not. Only reachable while a boss is up. */
    var showBossPanel by mutableStateOf(false)
        private set

    fun toggleBossPanel() {
        playClick()
        showBossPanel = !showBossPanel
    }

    /**
     * What the game knows about the boss on the field, or null if there is
     * none.
     *
     * Built on demand from the live enemy rather than snapshotted into the HUD
     * each tick: the dossier is opened *during* a fight, and a frozen copy of
     * the numbers would be worse than not showing them. The panel reads
     * `frameTick` so it recomposes with the simulation.
     */
    fun bossDossier(): BossDossier? {
        val boss = engine.enemies.items.firstOrNull { it.active && it.isBoss } ?: return null
        return BossDossier(
            variant = boss.variant,
            health = boss.health,
            maxHealth = boss.maxHealth,
            armor = boss.armor,
            speed = boss.currentSpeed(),
            modifiers = BossModifier.entries.filter { boss.hasModifier(it) },
            revived = boss.revived,
            distanceToCore = (engine.map.laneLength[boss.lane] - boss.progress)
                .coerceAtLeast(0f)
        )
    }

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

    /**
     * Revives spent on the current run.
     *
     * The owner's rule is one per run, and the button says so, so nobody
     * watches an ad expecting a second. [Balance.REVIVES_PER_RUN] is where the
     * number lives because §F2's revive pack raises it.
     */
    var revivesUsed by mutableIntStateOf(0)
        private set

    /** Revives this run is entitled to; the REVIVE PACK raises it to three. */
    val revivesAllowed: Int
        get() = entitlements.revivesPerRun ?: Balance.REVIVES_PER_RUN

    /**
     * True when the player has bought their way past the revive ad.
     *
     * The button then reads CONTINUE rather than WATCH AD TO CONTINUE and
     * grants the revive directly. Nothing else about the revive changes, which
     * is the point: the pack sells away the ad, not a different feature.
     */
    val reviveIsFree: Boolean get() = entitlements.reviveAdsRemoved

    /**
     * True while a rewarded ad is on screen for a revive.
     *
     * Separate from [showingAd], which is the interstitial: the two cover the
     * screen for different reasons and the game-over overlay has to know which
     * one it is waiting on.
     */
    var showingReviveAd by mutableStateOf(false)
        private set

    /** Set once a revive has been spent, so the run's loss ad is suppressed. */
    private var reviveSpentThisRun = false

    /**
     * Whether the game-over screen may offer a revive right now.
     *
     * Every clause is a reason a player would otherwise be shown a button that
     * does nothing: the run must actually be lost, the entitlement must not be
     * spent, and there must be a rewarded ad loaded to pay for it. An
     * unconfigured build has no rewarded gateway and so never reaches the
     * last clause — the rule from 1.16.0, that a control which cannot act must
     * not be offered at all.
     */
    val canReviveNow: Boolean
        get() = engine.phase == RunPhase.GAME_OVER &&
            !runRecorded &&
            revivesUsed < revivesAllowed &&
            (reviveIsFree || ads.isRewardedReady)

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

        engine.onBudgetEarned = { amount, _ ->
            viewModelScope.launch { repository.awardBudget(amount) }
        }

        engine.onGameOver = { onRunEnded() }
    }

    // ------------------------------------------------------------------ store

    /**
     * The billing gateway. [NoBillingGateway] until the app is registered in
     * the Play Console — see PROGRESS.md. Swapping in the real one is the only
     * change needed here; nothing else in the view model knows the difference.
     */
    /**
     * Real Play Billing when the app can reach Play, the no-op gateway when it
     * cannot. `PlayBillingGateway` reports what Play says is owned;
     * [GameRepository.applyPurchase] decides what that means, guarded by order
     * id so Play re-reporting a purchase cannot pay out twice.
     */
    private val billing: BillingGateway = runCatching {
        PlayBillingGateway(
            context = application,
            scope = viewModelScope,
            onPurchaseConfirmed = { sku, orderId ->
                viewModelScope.launch { repository.applyPurchase(sku, orderId) }
            }
        )
    }.getOrElse {
        Log.w("CyOpsStore", "Play Billing unavailable; the store will say so", it)
        NoBillingGateway()
    }

    /**
     * Hands the current Activity to the gateways that need one.
     *
     * Billing cannot launch a purchase flow without it and AdMob cannot show
     * an interstitial without it, but neither may hold one: both keep a weak
     * reference, so a rotated-away Activity is collected rather than leaked.
     */
    fun attachActivity(activity: android.app.Activity) {
        (billing as? PlayBillingGateway)?.attach(activity)
        (ads as? AdMobGateway)?.attach(activity)
        ads.preload()
        // Loaded up front, because the moment it is wanted -- the instant the
        // core falls -- is the worst possible moment to start fetching one.
        ads.preloadRewarded()
        (cloud as? PlayGamesCloudSave)?.let { games ->
            games.attach(activity)
            // Refresh rather than sign in: a player who has linked before is
            // signed in again silently by Play Games, and one who has not must
            // not be met by a dialog they did not ask for.
            viewModelScope.launch { games.refresh() }
        }
    }

    var entitlements by mutableStateOf(Entitlements())
        private set

    /** The tag shown on the persistent identity strip. */
    var playerTag by mutableStateOf("")
        private set

    // ------------------------------------------------------------ cloud save

    /**
     * Where progress goes so it can outlive this device.
     *
     * Real Play Games only when the build has a games project id; otherwise the
     * no-op gateway, and saves stay local exactly as they always have. Android's
     * own Auto Backup still carries them to a new phone on a fresh install, so
     * even the unconfigured build is not a dead end.
     */
    private val cloud: CloudSaveGateway = if (PlayServices.cloudSaveConfigured) {
        runCatching {
            PlayGamesCloudSave(application, android.os.Build.MODEL ?: "this device")
        }.getOrElse {
            Log.w("CyOpsCloud", "Play Games unavailable; saves stay on the device", it)
            NoCloudSaveGateway()
        }
    } else {
        NoCloudSaveGateway()
    }

    private val cloudSync =
        CloudSaveSync(repository, cloud, android.os.Build.MODEL ?: "this device")

    var cloudStatus by mutableStateOf(CloudSaveStatus.UNAVAILABLE)
        private set

    var cloudAccount by mutableStateOf<String?>(null)
        private set

    /** Epoch millis of the last successful sync, or null if there has not been one. */
    var lastCloudSync by mutableStateOf<Long?>(null)
        private set

    /** True while a sync is in flight, so the screen can say so. */
    var cloudBusy by mutableStateOf(false)
        private set

    /**
     * Links a Google account, then reconciles the two saves.
     *
     * This is the button that shows Google's consent prompt for managing this
     * game's saved data. Declining is a normal outcome and leaves a completely
     * playable game behind.
     */
    fun linkCloudSave() {
        if (cloudBusy) return
        playClick()
        viewModelScope.launch {
            cloudBusy = true
            announce(cloudSync.link())
            cloudBusy = false
        }
    }

    /** SYNC NOW. Pull, merge, apply, push. */
    fun syncCloudSave() {
        if (cloudBusy) return
        playClick()
        viewModelScope.launch {
            cloudBusy = true
            announce(cloudSync.sync())
            cloudBusy = false
        }
    }

    /**
     * A sync nobody asked for: leaving the app, or finishing a run.
     *
     * Silent on purpose. It does nothing when no account is linked, and it
     * never reports failure to the player — the local save is already written
     * by the time this runs, so a failed upload costs nothing and interrupting
     * someone to tell them about it would.
     */
    private suspend fun syncCloudQuietly() {
        if (cloudStatus != CloudSaveStatus.LINKED) return
        if (cloudSync.syncQuietly() != CloudSyncResult.FAILED) {
            lastCloudSync = System.currentTimeMillis()
            repository.setLastCloudSync(lastCloudSync!!)
        }
    }

    private suspend fun announce(result: CloudSyncResult) {
        when (result) {
            CloudSyncResult.MERGED -> {
                lastCloudSync = System.currentTimeMillis()
                repository.setLastCloudSync(lastCloudSync!!)
                showTransient("PROGRESS SYNCED")
            }
            CloudSyncResult.UPLOADED -> {
                lastCloudSync = System.currentTimeMillis()
                repository.setLastCloudSync(lastCloudSync!!)
                showTransient("PROGRESS SAVED TO YOUR ACCOUNT")
            }
            CloudSyncResult.DECLINED -> showTransient("NOT LINKED")
            CloudSyncResult.FAILED -> showTransient("GOOGLE UNREACHABLE \u2014 SAVE KEPT ON DEVICE")
            CloudSyncResult.UNAVAILABLE -> showTransient("CLOUD SAVE NOT AVAILABLE")
        }
    }

    // -------------------------------------------------------------------- ads

    /**
     * Real AdMob when this build has ids, the no-op gateway otherwise.
     *
     * Selected rather than hard-coded so a checkout with no AdMob account
     * still builds and plays: the absence of monetisation must never be a
     * crash, and it must never quietly become Google's *test* ads shown to
     * real players either.
     */
    private val ads: AdGateway = adsOverride ?: if (PlayServices.adsConfigured) {
        AdMobGateway(
            application,
            PlayServices.adMobInterstitialId,
            if (PlayServices.rewardedConfigured) PlayServices.adMobRewardedId else ""
        )
    } else {
        NoAdGateway()
    }
    private val adPolicy = AdPolicy()

    /** True while an interstitial is on screen and the game is waiting on it. */
    var showingAd by mutableStateOf(false)
        private set

    /**
     * Shows an interstitial after a lost run, if the rules allow one.
     *
     * [then] runs either way. The game must not depend on an ad completing —
     * a gateway that never calls back would otherwise strand the player on a
     * dead screen, so the continuation is the caller's and is always invoked.
     */
    private fun maybeShowLossAd(then: () -> Unit) {
        if (!adPolicy.shouldShowOnRunLost(entitlements.adsRemoved, ads.isReady)) {
            then()
            return
        }
        adPolicy.recordShown()
        showingAd = true
        ads.showInterstitial {
            showingAd = false
            ads.preload()
            then()
        }
    }

    // ------------------------------------------------------------ leaderboard

    private val leaderboard: LeaderboardGateway = LocalLeaderboard(repository)

    var identity by mutableStateOf(PlayerIdentity())
        private set

    var leaderboardEntries by mutableStateOf(emptyList<LeaderboardEntry>())
        private set

    fun registerUsername(name: String) {
        if (!PlayerIdentity.isValid(name)) return
        playClick()
        viewModelScope.launch {
            repository.setUsername(name)
            refreshLeaderboard()
        }
    }

    fun refreshLeaderboard() {
        viewModelScope.launch { leaderboardEntries = leaderboard.top() }
    }

    /** Modes this player has earned the right to play. */
    var availableModes by mutableStateOf(listOf(GameMode.STANDARD))
        private set

    /** The mode the next run will start in. */
    var selectedMode by mutableStateOf(GameMode.STANDARD)
        private set

    fun selectMode(mode: GameMode) {
        if (mode !in availableModes) return
        playClick()
        selectedMode = mode
    }

    var cosmetics by mutableStateOf(CosmeticChoice())
        private set

    var billingPrices by mutableStateOf(emptyMap<String, String>())
        private set

    var billingStatus by mutableStateOf(BillingStatus.UNAVAILABLE)
        private set

    fun buy(sku: Sku) {
        playClick()
        billing.purchase(sku)
    }

    fun restorePurchases() {
        playClick()
        billing.restore()
    }

    /**
     * Equips a look.
     *
     * Each of these is written straight through to the repository rather than
     * held in memory first: the choice has to survive the process, and the
     * repository already resolves a selection the player does not own (a
     * refund, or a restore onto another account) so nothing downstream has to
     * check entitlements again.
     */
    fun chooseCoreSkin(id: String?) {
        playClick()
        viewModelScope.launch { repository.chooseCoreSkin(id) }
    }

    fun chooseBackground(id: String?) {
        playClick()
        viewModelScope.launch { repository.chooseBackground(id) }
    }

    fun setSpectrumAgents(enabled: Boolean) {
        playClick()
        viewModelScope.launch { repository.setSpectrumAgents(enabled) }
    }

    /**
     * Started from its own `init` block rather than from [observePersistence].
     *
     * Kotlin runs property initializers and `init` blocks in declaration order,
     * and [observePersistence] is called from an `init` block that sits *above*
     * these properties — so collecting from there dereferenced `billing` and
     * the store state before either existed, and every collector died on a
     * NullPointerException before the view model finished being built.
     */
    private fun observeStore() {
        collectJobs += viewModelScope.launch {
            repository.identity.collectLatest { identity ->
                this@GameViewModel.identity = identity
                playerTag = if (identity.registered) {
                    "AGENT ${identity.username}"
                } else {
                    "AGENT UNREGISTERED"
                }
                availableModes = GameMode.entries.filter { it.unlockedBy(identity.highestWave) }
                // A mode can only be lost by a progress reset, but if it is,
                // the selection must not survive it.
                if (selectedMode !in availableModes) selectedMode = GameMode.STANDARD
            }
        }
        collectJobs += viewModelScope.launch {
            repository.storeState.collectLatest { state ->
                entitlements = state.entitlements
                cosmetics = state.cosmetics
                fifthSpeedUnlocked = state.entitlements.fifthSpeedUnlocked
                // A player who owned 5x, then lost it (refund, or a restore
                // onto another account), must not be left running at a speed
                // they no longer have.
                if (speedIndex >= Balance.speedCount(fifthSpeedUnlocked)) {
                    applySpeedIndex(0)
                }
            }
        }
        collectJobs += viewModelScope.launch {
            billing.prices.collectLatest { billingPrices = it }
        }
        collectJobs += viewModelScope.launch {
            billing.status.collectLatest { billingStatus = it }
        }
        collectJobs += viewModelScope.launch {
            cloud.status.collectLatest { cloudStatus = it }
        }
        collectJobs += viewModelScope.launch {
            cloud.accountName.collectLatest { cloudAccount = it }
        }
        collectJobs += viewModelScope.launch {
            repository.lastCloudSync.collectLatest { lastCloudSync = it }
        }
        // Purchases are applied by the gateway's own confirmation callback,
        // which carries the order id. This collector exists only so the store
        // screen can show what is owned while Play is still answering.
        (billing as? PlayBillingGateway)?.connect()
    }

    init {
        observeStore()
    }

    private fun observePersistence() {
        // Before anything reads the budget: a save written before the 1.24.0
        // rescale carries € at a tenth of the current scale, and reading it
        // without migrating would show a player a tenth of what they own.
        collectJobs += viewModelScope.launch { repository.migrateBudgetScale() }
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
                // Never un-complete it. The flag is written asynchronously,
                // so an emission from before that write still says false --
                // and a player who skips the tutorial and immediately hits
                // RETRY would be handed it again. In-memory is the truth
                // until the write lands. `resetAllProgress` clears both
                // deliberately, after the store is emptied.
                tutorialCompleted = loaded.tutorialCompleted || tutorialCompleted
                budget = loaded.budget
                firmwareLevel = loaded.firmwareLevel
                lifetimeBudgetEarned = loaded.lifetimeBudgetEarned
                // Firmware bought between runs takes effect immediately.
                engine.firmwareDamageMultiplier = Balance.firmwareDamageMultiplier(
                    loaded.firmwareLevel
                )
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
        // The mode decides the music as well as the numbers, and it is read
        // here rather than inside the audio layer so the second map can change
        // one call site instead of a rule buried two packages away.
        audio.setInMatch(true, trackForMode(selectedMode))
        // Selected before the run starts: the mode sets starting integrity, so
        // it has to be in place before startNewRun reads it.
        engine.selectMode(selectedMode)
        engine.startNewRun()
        engine.autoStartWaves = settings.autoStartWaves
        engine.batterySaver = settings.batterySaver
        engine.showDamageNumbers = settings.damageNumbers
        engine.firmwareDamageMultiplier = Balance.firmwareDamageMultiplier(firmwareLevel)
        lastPhaseSeen = null
        selection = BattlefieldSelection()
        showDeployPanel = false
        paused = false
        speedIndex = 0
        gameOverSummary = null
        runRecorded = false
        revivesUsed = 0
        reviveSpentThisRun = false
        matchActive = true
        tutorialStep = if (tutorialCompleted) -1 else 0
        pushHud()

        viewModelScope.launch {
            repository.recordGameStarted()
            repository.clearSavedRun()
        }
        audio.startMusic()
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
                attacksBlocked = run.attacksBlocked,
                cryptoEarned = run.cryptoEarned,
                bossesDefeated = run.bossesDefeated,
                serverDamageTaken = run.serverDamageTaken,
                agentsDeployed = run.agentsDeployed,
                agentUpgrades = run.agentUpgrades
            )
            engine.autoStartWaves = settings.autoStartWaves
            engine.batterySaver = settings.batterySaver
            engine.showDamageNumbers = settings.damageNumbers
            engine.firmwareDamageMultiplier = Balance.firmwareDamageMultiplier(firmwareLevel)
            lastPhaseSeen = null
            selection = BattlefieldSelection()
            showDeployPanel = false
            paused = false
            speedIndex = 0
            gameOverSummary = null
            runRecorded = false
            revivesUsed = 0
            reviveSpentThisRun = false
            matchActive = true
            tutorialStep = -1
            pushHud()
            // Says "in a match" as well as "play something". Without it a
            // resumed run kept the menu's track running underneath it, because
            // startMusic() only picks the match track once it has been told a
            // match is happening.
            audio.setInMatch(true, trackForMode(engine.mode))
            audio.startMusic()
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
        if (prepBannerVisible && now > prepBannerExpiry) prepBannerVisible = false
    }

    /** Show the between-waves banner briefly whenever a wave ends. */
    private fun trackPhaseForBanner(phase: RunPhase) {
        if (phase == lastPhaseSeen) return
        lastPhaseSeen = phase
        if (phase == RunPhase.PREPARING) {
            prepBannerVisible = true
            prepBannerExpiry =
                System.currentTimeMillis() + (Balance.PREP_BANNER_SECONDS * 1000).toLong()
        } else {
            prepBannerVisible = false
        }
    }

    fun dismissPrepBanner() {
        prepBannerVisible = false
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
        // The dossier is about one specific opponent. When that opponent is
        // gone the panel has nothing to say, and leaving the flag set would
        // have the *next* boss throw it open over the board unasked.
        if (showBossPanel && !snapshot.bossOnField) showBossPanel = false
        trackPhaseForBanner(engine.phase)
    }

    fun togglePause() {
        paused = !paused
        audio.play(GameSound.UI_CLICK)
    }

    fun applyPaused(value: Boolean) {
        paused = value
    }

    fun cycleSpeed() {
        speedIndex = (speedIndex + 1) % Balance.speedCount(fifthSpeedUnlocked)
        audio.play(GameSound.UI_CLICK)
    }

    fun applySpeedIndex(index: Int) {
        // A locked speed says so rather than quietly becoming a different one.
        // Coercing into range meant tapping 5x on a save that does not own it
        // selected 3x and looked like the button had worked -- which is worse
        // than a button that does nothing, because it also lies about what
        // speed the match is running at.
        if (index >= Balance.speedCount(fifthSpeedUnlocked)) {
            showTransient("5\u00D7 SPEED IS IN THE STORE")
            return
        }
        speedIndex = index.coerceIn(0, Balance.speedCount(fifthSpeedUnlocked) - 1)
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
            // A player who ignores the cards and just plays must never be
            // left staring at a stale step. Opening the roster from anywhere
            // ahead of it jumps the script to the pick, explanations and all:
            // they acted, so skipping what they skipped is their call.
            if (tutorialStep in TutorialScript.INTRO until TutorialScript.PICK_FIREWALL) {
                tutorialStep = TutorialScript.PICK_FIREWALL
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
        // The guided run does insist, because the owner asked it to: two
        // FIREWALLs and two TARPITs. Picking the wrong one says so rather than
        // silently moving on, which is what "forced" has to mean if the step
        // after it is going to count placements of a specific agent.
        when {
            tutorialStep < 0 -> Unit

            tutorialStep <= TutorialScript.PICK_FIREWALL ->
                if (type == AgentType.FIREWALL) {
                    tutorialStep = TutorialScript.PLACE_FIREWALLS
                } else {
                    showTransient("THE GUIDED RUN NEEDS FIREWALL FIRST")
                }

            tutorialStep == TutorialScript.PICK_TARPIT ->
                if (type == AgentType.TARPIT) {
                    tutorialStep = TutorialScript.PLACE_TARPITS
                } else {
                    showTransient("THE GUIDED RUN NEEDS TARPIT NEXT")
                }
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
                    advanceTutorialOnPlacement()
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

    private fun nearestNode(point: Offset): com.cyopstd.game.core.NodePosition? {
        var best: com.cyopstd.game.core.NodePosition? = null
        var bestDistanceSq = Float.MAX_VALUE
        for (node in engine.map.nodes) {
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

    fun upgradeSelectedAgent(times: Int = 1) {
        val nodeId = selection.selectedNodeId ?: return
        if (engine.upgradeAgent(nodeId, times) == 0) {
            showTransient("INSUFFICIENT CRYPTO")
        }
        pushHud()
    }

    /** Levels the current balance could buy on the selected agent. */
    fun affordableUpgradesForSelection(): Int {
        val nodeId = selection.selectedNodeId ?: return 0
        return engine.affordableUpgrades(nodeId)
    }

    /** Spend € BUDGET on permanent firmware. */
    fun buyFirmware(levels: Int) {
        viewModelScope.launch {
            val bought = repository.buyFirmware(levels)
            if (bought == 0) showTransient("INSUFFICIENT \u20AC BUDGET")
        }
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

    /**
     * CONTINUE on a card that is just telling the player something.
     *
     * Only the acknowledge-gated steps move here. A step that waits for an
     * action shows no button at all, so there is nothing to press that would
     * skip past the thing the player is meant to be learning by doing.
     */
    fun advanceTutorial() {
        val current = TutorialScript.stepAt(tutorialStep) ?: return
        if (current.gate != TutorialGate.ACKNOWLEDGE) return
        playClick()
        tutorialStep = nextStepAfter(tutorialStep)
    }

    /** The briefing question: yes reads it, no goes straight to playing. */
    fun answerBriefing(wanted: Boolean) {
        if (tutorialStep != TutorialScript.BRIEFING_OFFER) return
        playClick()
        tutorialStep = if (wanted) TutorialScript.BRIEFING else TutorialScript.OPEN_ROSTER
    }

    /**
     * Counts what the player has actually built, and moves on when it is enough.
     *
     * The count comes from the board rather than from a tally kept alongside
     * it: a player who places a FIREWALL, sells it and places another has one
     * FIREWALL, and a counter incremented on each placement would have said
     * two. Asking the engine cannot be wrong.
     */
    private fun advanceTutorialOnPlacement() {
        val current = TutorialScript.stepAt(tutorialStep) ?: return
        val required = current.requiresAgent ?: return
        val built = engine.agents.items.count { it.active && it.type == required }
        if (built < current.requiresCount) return
        tutorialStep = nextStepAfter(tutorialStep)
    }

    /** The step after [index], ending the tutorial when there is none. */
    private fun nextStepAfter(index: Int): Int =
        if (index >= TutorialScript.lastIndex) -1 else index + 1

    // ------------------------------------------------------- run termination

    /**
     * The run is over — but not necessarily finished.
     *
     * This used to be one method that recorded the run, submitted the
     * leaderboard entry, cleared the save and showed the loss ad, all at the
     * moment the core fell. A revive after that would have double-counted
     * everything: one run would have posted two leaderboard entries, one at
     * the wave it died on and one at the wave it finally reached, and its
     * kills, crypto and damage would have landed twice in lifetime stats.
     *
     * So the order is now: show the summary, *offer* the revive, and record
     * nothing until the player has finished with the run. [finalizeRun] is the
     * only thing that writes, and it is idempotent.
     */
    private fun onRunEnded() {
        if (runRecorded) return
        matchActive = false
        audio.setInMatch(false)
        showSummary()

        // A revive on offer means the run may not be over. Nothing is written,
        // no leaderboard entry is posted, and the save is left alone until the
        // player either takes it or walks away from it.
        if (canReviveNow) return

        finalizeRun()
    }

    /** The summary the game-over screen reads, from wherever the run got to. */
    private fun showSummary() {
        val isRecord = engine.currentWave > stats.highestWave
        gameOverSummary = GameOverSummary(
            waveReached = engine.currentWave,
            attacksBlocked = engine.runAttacksBlocked,
            cryptoEarned = engine.runCryptoEarned,
            bossesDefeated = engine.runBossesDefeated,
            bestWave = maxOf(stats.highestWave, engine.currentWave),
            isNewRecord = isRecord
        )
    }

    /**
     * Writes the run down, once.
     *
     * Called when the player declines the revive, leaves the game-over screen,
     * backgrounds the app on it, or when there was never a revive to offer.
     * The guard is the whole contract: a revived run reaches here exactly once,
     * at the wave it finally reached.
     */
    private fun finalizeRun() {
        if (runRecorded) return
        runRecorded = true
        matchActive = false
        audio.setInMatch(false)
        // Only a lost run carries an ad. A player who quit to the menu chose
        // to leave, and charging them for that is the fastest way to make
        // leaving permanent. A player who already watched a rewarded ad for a
        // revive has paid this run's ad budget and is not charged twice.
        if (engine.phase == RunPhase.GAME_OVER && !reviveSpentThisRun) maybeShowLossAd { }

        val isRecord = engine.currentWave > stats.highestWave
        gameOverSummary = GameOverSummary(
            waveReached = engine.currentWave,
            attacksBlocked = engine.runAttacksBlocked,
            cryptoEarned = engine.runCryptoEarned,
            bossesDefeated = engine.runBossesDefeated,
            bestWave = maxOf(stats.highestWave, engine.currentWave),
            isNewRecord = isRecord
        )

        viewModelScope.launch {
            // Submitted before the stats write so a crash between the two
            // loses the aggregate, not the run itself.
            repository.recordDamage(engine.runDamageDealt.toLong())
            leaderboard.submit(
                LeaderboardEntry(
                    username = identity.username,
                    wave = engine.currentWave,
                    damage = engine.runDamageDealt.toLong(),
                    modeId = engine.mode.id,
                    at = System.currentTimeMillis() / 1000
                )
            )
            refreshLeaderboard()
            repository.recordRunResult(
                waveReached = engine.currentWave,
                attacksBlocked = engine.runAttacksBlocked,
                bossesDefeated = engine.runBossesDefeated,
                cryptoEarned = engine.runCryptoEarned,
                serverDamageTaken = engine.runServerDamageTaken,
                agentsDeployed = engine.runAgentsDeployed,
                agentUpgrades = engine.runAgentUpgrades,
                deploymentsByType = engine.runDeploymentsByType.mapKeys { it.key.name },
                countAsGamePlayed = false
            )
            repository.clearSavedRun()
            // A finished run is the other moment worth carrying up: it is the
            // one a player would be most upset to repeat on another device.
            syncCloudQuietly()
        }
    }

    /**
     * Watch a rewarded ad to continue the run.
     *
     * The revive hangs off the ad's reward callback and nothing else. An
     * interstitial calls back on dismissal, so granting a revive there would
     * be granting it for closing the ad after two seconds; [AdGateway.showRewarded]
     * reports whether the reward was actually earned, and a `false` leaves the
     * player exactly where they were, on the game-over screen, with the offer
     * still standing if the ad simply failed to show.
     *
     * The entitlement is only spent on a revive that actually happened.
     */
    fun watchAdToRevive() {
        if (!canReviveNow || showingReviveAd) return
        playClick()
        // Someone who bought the REVIVE PACK is not shown an ad they already
        // paid to be rid of. Same revive, no ad in front of it.
        if (reviveIsFree) {
            grantRevive()
            return
        }
        showingReviveAd = true
        ads.showRewarded { earned ->
            showingReviveAd = false
            ads.preloadRewarded()
            if (!earned) {
                // Nothing spent, nothing granted, and the button is still
                // there. Saying so matters: silence here reads as the game
                // having taken the ad and given nothing back.
                showTransient("AD NOT COMPLETED — NOTHING SPENT")
                return@showRewarded
            }
            grantRevive(spentAnAd = true)
        }
    }

    /**
     * Puts the run back on its feet.
     *
     * Shared by the ad path and the REVIVE PACK path so there is exactly one
     * description of what a revive does. [spentAnAd] only decides whether this
     * run has already paid its ad budget, which is what suppresses the loss
     * interstitial at the end.
     */
    private fun grantRevive(spentAnAd: Boolean = false) {
        if (!engine.reviveRun()) {
            showTransient("REVIVE FAILED — RUN ALREADY ENDED")
            return
        }
        revivesUsed += 1
        if (spentAnAd) reviveSpentThisRun = true
        gameOverSummary = null
        matchActive = true
        paused = false
        audio.setInMatch(true, trackForMode(engine.mode))
        if (settings.musicVolume > 0.01f) audio.startMusic()
        selection = BattlefieldSelection()
        showBossPanel = false
        showDeployPanel = false
        pushHud()
        showTransient("SYSTEMS RESTORED — INTEGRITY ${engine.serverHp}")
    }

    /**
     * The player is done with this run: write it down.
     *
     * Every way off the game-over screen goes through here, including
     * backgrounding the app on it, because a run whose revive was offered and
     * never taken must still count.
     */
    fun finishRun() {
        if (engine.phase != RunPhase.GAME_OVER) return
        finalizeRun()
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
                    attacksBlocked = engine.runAttacksBlocked,
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
        audio.setInMatch(false)
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
                attacksBlocked = engine.runAttacksBlocked,
                cryptoEarned = engine.runCryptoEarned,
                bossesDefeated = engine.runBossesDefeated,
                serverDamageTaken = engine.runServerDamageTaken,
                agentsDeployed = engine.runAgentsDeployed,
                agentUpgrades = engine.runAgentUpgrades,
                budgetEarned = engine.runBudgetEarned,
                savedAtMillis = System.currentTimeMillis()
            )
        )
        hasSavedRun = true
    }

    fun restartAfterGameOver() {
        finishRun()
        startNewGame()
    }

    fun abandonMatch() {
        finishRun()
        matchActive = false
        gameOverSummary = null
        audio.setInMatch(false)
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
            budget = 0L
            firmwareLevel = 0
            lifetimeBudgetEarned = 0L
            engine.firmwareDamageMultiplier = 1f
            hasSavedRun = false
            stats = PlayerStats()
            settings = GameSettings()
            applySettingsToSystems(settings)
        }
        engine.startNewRun()
        matchActive = false
        gameOverSummary = null
    }

    /** The menu is on screen: start its music. */
    fun onMenuShown() {
        audio.enterMenu()
    }

    /** The studio ident is on screen: play the machine coming up. */
    fun playBootChime() {
        audio.playBootChime()
    }

    fun playClick() {
        audio.play(GameSound.UI_CLICK)
    }

    fun onAppPaused() {
        // Silence, not "switch to the menu track". This used to call
        // setInMatch(false), which *starts* the menu music -- so backgrounding
        // the game during a run swapped to menu music and kept playing it over
        // whatever the player had opened instead.
        audio.stopMusic()
        // One coroutine, in order: the run is written first and the upload
        // reads it afterwards. Launching both separately raced, and the race
        // was silent — the pushed snapshot would simply be missing the run the
        // player had just walked away from.
        // A revive on offer means the run is deliberately unrecorded. If the
        // player leaves the app there rather than answering, the run still
        // happened and still counts -- otherwise backgrounding the game would
        // be a way to erase a bad run.
        finishRun()
        viewModelScope.launch {
            if (matchActive && engine.phase != RunPhase.GAME_OVER) persistRun()
            syncCloudQuietly()
        }
    }

    fun onAppResumed() {
        // Whichever screen they left, not only a match: pausing no longer
        // clears which track that was, so startMusic picks the right one.
        if (settings.musicVolume > 0.01f) audio.startMusic()
    }

    /** Whether any music is currently asked to play. */
    val musicWanted: Boolean get() = audio.musicWanted

    override fun onCleared() {
        super.onCleared()
        collectJobs.forEach { it.cancel() }
        audio.release()
        billing.release()
        cloud.release()
    }

    companion object {
        private const val TRANSIENT_MS = 1600L
        private const val UNLOCK_BANNER_MS = 3200L
        private const val TAP_RADIUS_MULTIPLIER = 2.0f
    }
}
