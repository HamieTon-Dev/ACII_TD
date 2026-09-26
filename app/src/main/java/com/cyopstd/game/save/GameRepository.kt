package com.cyopstd.game.save

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cyopstd.game.core.Balance
import com.cyopstd.game.core.GameMode
import com.cyopstd.game.model.AgentType
import com.cyopstd.game.store.CosmeticChoice
import com.cyopstd.game.store.Entitlements
import com.cyopstd.game.store.Sku
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "cyops_td")

/**
 * All persistence for CyOps TD.
 *
 * Preferences DataStore holds the scalar settings and statistics; the in-progress
 * run and the structured stat map are stored as JSON strings inside it. That mix
 * keeps the dependency surface (and the APK) small while still giving atomic,
 * corruption-tolerant writes.
 *
 * Every read path is defensive: a malformed or truncated value is logged and
 * replaced with a default rather than being allowed to crash the game. A player
 * whose save got mangled by a bad shutdown loses that save, not the app.
 */
class GameRepository(private val store: DataStore<Preferences>) {

    /** Production entry point: the app's single shared preferences store. */
    constructor(context: Context) : this(context.dataStore)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    // ------------------------------------------------------------------ flows

    val settings: Flow<GameSettings> = store.data
        .catch { cause -> emitSafely(cause) }
        .map { prefs ->
            GameSettings(
                musicVolume = prefs[Keys.MUSIC_VOLUME] ?: 0.5f,
                sfxVolume = prefs[Keys.SFX_VOLUME] ?: 0.8f,
                vibrationEnabled = prefs[Keys.VIBRATION] ?: true,
                backgroundAnimation = prefs[Keys.BACKGROUND_ANIMATION] ?: true,
                damageNumbers = prefs[Keys.DAMAGE_NUMBERS] ?: true,
                showAgentRange = prefs[Keys.SHOW_RANGE] ?: true,
                autoStartWaves = prefs[Keys.AUTO_START] ?: false,
                autoStartBossWaves = prefs[Keys.AUTO_START_BOSS] ?: false,
                screenShake = prefs[Keys.SCREEN_SHAKE] ?: true,
                batterySaver = prefs[Keys.BATTERY_SAVER] ?: false
            )
        }

    val stats: Flow<PlayerStats> = store.data
        .catch { cause -> emitSafely(cause) }
        .map { prefs ->
            PlayerStats(
                highestWave = prefs[Keys.HIGHEST_WAVE] ?: 0,
                highestWaveHackAi = prefs[Keys.HIGHEST_WAVE_HACK_AI] ?: 0,
                totalAttacksBlocked = (prefs[Keys.TOTAL_PACKETS] ?: 0).toLong(),
                totalBossesDefeated = (prefs[Keys.TOTAL_BOSSES] ?: 0).toLong(),
                totalCryptoEarned = (prefs[Keys.TOTAL_CRYPTO] ?: 0).toLong(),
                totalGamesPlayed = (prefs[Keys.TOTAL_GAMES] ?: 0).toLong(),
                totalServerDamageTaken = (prefs[Keys.TOTAL_SERVER_DAMAGE] ?: 0).toLong(),
                totalAgentsDeployed = (prefs[Keys.TOTAL_DEPLOYED] ?: 0).toLong(),
                totalAgentUpgrades = (prefs[Keys.TOTAL_UPGRADES] ?: 0).toLong(),
                deploymentsByAgent = decodeDeployments(prefs[Keys.DEPLOYMENTS_JSON])
            )
        }

    val progress: Flow<PlayerProgress> = store.data
        .catch { cause -> emitSafely(cause) }
        .map { prefs ->
            val stored = prefs[Keys.UNLOCKED_AGENTS]
                ?.split('|')
                ?.filter { it.isNotBlank() }
                ?.toSet()
                ?: emptySet()
            PlayerProgress(
                unlockedAgents = stored + AgentType.starters.map { it.name },
                tutorialCompleted = prefs[Keys.TUTORIAL_DONE] ?: false,
                menuGuideSeen = prefs[Keys.MENU_GUIDE_SEEN] ?: false,
                firmwareGuideSeen = prefs[Keys.FIRMWARE_GUIDE_SEEN] ?: false,
                budget = prefs[Keys.BUDGET] ?: 0L,
                firmwareLevel = prefs[Keys.FIRMWARE_LEVEL] ?: 0,
                lifetimeBudgetEarned = prefs[Keys.LIFETIME_BUDGET] ?: 0L
            )
        }

    val savedRun: Flow<SavedRun?> = store.data
        .catch { cause -> emitSafely(cause) }
        .map { prefs -> decodeRun(prefs[Keys.SAVED_RUN]) }

    // ------------------------------------------------------------- mutations

    suspend fun updateSettings(transform: (GameSettings) -> GameSettings) {
        val current = settings.first()
        val updated = transform(current)
        writeSafely { prefs ->
            prefs[Keys.MUSIC_VOLUME] = updated.musicVolume.coerceIn(0f, 1f)
            prefs[Keys.SFX_VOLUME] = updated.sfxVolume.coerceIn(0f, 1f)
            prefs[Keys.VIBRATION] = updated.vibrationEnabled
            prefs[Keys.BACKGROUND_ANIMATION] = updated.backgroundAnimation
            prefs[Keys.DAMAGE_NUMBERS] = updated.damageNumbers
            prefs[Keys.SHOW_RANGE] = updated.showAgentRange
            prefs[Keys.AUTO_START] = updated.autoStartWaves
            prefs[Keys.AUTO_START_BOSS] = updated.autoStartBossWaves
            prefs[Keys.SCREEN_SHAKE] = updated.screenShake
            prefs[Keys.BATTERY_SAVER] = updated.batterySaver
        }
    }

    suspend fun saveRun(run: SavedRun) {
        val encoded = try {
            json.encodeToString(SavedRun.serializer(), run)
        } catch (error: Exception) {
            Log.w(TAG, "Could not encode run; skipping save", error)
            return
        }
        writeSafely { prefs -> prefs[Keys.SAVED_RUN] = encoded }
    }

    suspend fun clearSavedRun() {
        writeSafely { prefs -> prefs.remove(Keys.SAVED_RUN) }
    }

    suspend fun hasSavedRun(): Boolean = savedRun.first()?.isResumable == true

    /**
     * Brings a pre-1.24.0 save onto the ×10 € scale, exactly once.
     *
     * 1.24.0 multiplied the whole € economy by ten — what a wave pays out,
     * what a firmware level costs, and what every store pack grants — so that
     * the packs could show bigger numbers without becoming ten times better
     * value. A save written before that carries € at the old scale, and
     * leaving it there would have made every existing player ten times poorer
     * against the new firmware costs overnight. That is not a rounding error;
     * it is somebody's purchase.
     *
     * So the stored budget and the lifetime total are multiplied once and the
     * save is stamped. The stamp is what makes it once: running this twice
     * would be as wrong as never running it.
     *
     * Firmware *level* is untouched. Levels already bought stay bought; only
     * the currency changes denomination.
     */
    suspend fun migrateBudgetScale() {
        val prefs = store.data.catch { emitSafely(it) }.first()
        if (prefs[Keys.BUDGET_SCALE_VERSION] == Balance.BUDGET_SCALE) return

        val budget = prefs[Keys.BUDGET] ?: 0L
        val lifetime = prefs[Keys.LIFETIME_BUDGET] ?: 0L
        writeSafely { updated ->
            updated[Keys.BUDGET] = budget * Balance.BUDGET_SCALE
            updated[Keys.LIFETIME_BUDGET] = lifetime * Balance.BUDGET_SCALE
            updated[Keys.BUDGET_SCALE_VERSION] = Balance.BUDGET_SCALE
        }
    }

    /**
     * Records an unlock.
     *
     * Read and written inside one `edit`, which DataStore runs atomically.
     * It used to read the set first and write it afterwards, and three agents
     * unlock together at wave 30: all three reads saw the old set, each write
     * added only its own agent, and the last one won. QUANTUM and RED HAT were
     * lost and only BLUE HAT was kept.
     */
    suspend fun unlockAgent(type: AgentType) {
        writeSafely { prefs ->
            val current = prefs[Keys.UNLOCKED_AGENTS]
                ?.split('|')
                ?.filter { it.isNotBlank() }
                ?.toSet()
                ?: emptySet()
            if (type.name !in current) {
                prefs[Keys.UNLOCKED_AGENTS] = (current + type.name).joinToString("|")
            }
        }
    }

    suspend fun setTutorialCompleted(completed: Boolean) {
        writeSafely { prefs -> prefs[Keys.TUTORIAL_DONE] = completed }
    }

    suspend fun setMenuGuideSeen(seen: Boolean) {
        writeSafely { prefs -> prefs[Keys.MENU_GUIDE_SEEN] = seen }
    }

    suspend fun setFirmwareGuideSeen(seen: Boolean) {
        writeSafely { prefs -> prefs[Keys.FIRMWARE_GUIDE_SEEN] = seen }
    }

    /** Bank € BUDGET earned by clearing a ten-wave milestone. */
    suspend fun awardBudget(amount: Int) {
        if (amount <= 0) return
        writeSafely { prefs ->
            prefs[Keys.BUDGET] = (prefs[Keys.BUDGET] ?: 0L) + amount
            prefs[Keys.LIFETIME_BUDGET] = (prefs[Keys.LIFETIME_BUDGET] ?: 0L) + amount
        }
    }

    /**
     * Buy [levels] of CORE FIRMWARE, spending € BUDGET. Returns how many were
     * actually bought — the balance is re-read inside the transaction, so two
     * rapid taps can never spend the same € twice.
     */
    suspend fun buyFirmware(levels: Int): Int {
        if (levels <= 0) return 0
        var bought = 0
        writeSafely { prefs ->
            var balance = prefs[Keys.BUDGET] ?: 0L
            var level = prefs[Keys.FIRMWARE_LEVEL] ?: 0
            while (bought < levels && level < Balance.MAX_FIRMWARE_LEVEL) {
                val cost = Balance.firmwareCost(level)
                if (balance < cost) break
                balance -= cost
                level++
                bought++
            }
            if (bought > 0) {
                prefs[Keys.BUDGET] = balance
                prefs[Keys.FIRMWARE_LEVEL] = level
            }
        }
        return bought
    }

    /**
     * Fold the totals from a finished (or abandoned) run into the lifetime
     * statistics. Called once per run, when the player loses or leaves a match.
     */
    suspend fun recordRunResult(
        waveReached: Int,
        attacksBlocked: Int,
        bossesDefeated: Int,
        cryptoEarned: Int,
        serverDamageTaken: Int,
        agentsDeployed: Int,
        agentUpgrades: Int,
        deploymentsByType: Map<String, Int>,
        countAsGamePlayed: Boolean,
        /**
         * Which mode the run was played on, by `GameMode.id`.
         *
         * Defaulted so that every existing caller and test keeps working, and
         * so a run whose mode is somehow unknown is counted as the standard
         * one rather than quietly unlocking the second level.
         */
        modeId: String = GameMode.STANDARD.id
    ) {
        writeSafely { prefs ->
            val bestWave = prefs[Keys.HIGHEST_WAVE] ?: 0
            if (waveReached > bestWave) prefs[Keys.HIGHEST_WAVE] = waveReached
            if (modeId == GameMode.HACK_AI.id &&
                waveReached > (prefs[Keys.HIGHEST_WAVE_HACK_AI] ?: 0)
            ) {
                prefs[Keys.HIGHEST_WAVE_HACK_AI] = waveReached
            }

            prefs[Keys.TOTAL_PACKETS] = (prefs[Keys.TOTAL_PACKETS] ?: 0) + attacksBlocked
            prefs[Keys.TOTAL_BOSSES] = (prefs[Keys.TOTAL_BOSSES] ?: 0) + bossesDefeated
            prefs[Keys.TOTAL_CRYPTO] = (prefs[Keys.TOTAL_CRYPTO] ?: 0) + cryptoEarned
            prefs[Keys.TOTAL_SERVER_DAMAGE] =
                (prefs[Keys.TOTAL_SERVER_DAMAGE] ?: 0) + serverDamageTaken
            prefs[Keys.TOTAL_DEPLOYED] = (prefs[Keys.TOTAL_DEPLOYED] ?: 0) + agentsDeployed
            prefs[Keys.TOTAL_UPGRADES] = (prefs[Keys.TOTAL_UPGRADES] ?: 0) + agentUpgrades
            if (countAsGamePlayed) {
                prefs[Keys.TOTAL_GAMES] = (prefs[Keys.TOTAL_GAMES] ?: 0) + 1
            }

            val existing = decodeDeployments(prefs[Keys.DEPLOYMENTS_JSON]).toMutableMap()
            for ((agent, count) in deploymentsByType) {
                existing[agent] = (existing[agent] ?: 0) + count
            }
            prefs[Keys.DEPLOYMENTS_JSON] = encodeDeployments(existing)
        }
    }

    /** Called when a run starts, so "games played" counts attempts, not wins. */
    suspend fun recordGameStarted() {
        writeSafely { prefs ->
            prefs[Keys.TOTAL_GAMES] = (prefs[Keys.TOTAL_GAMES] ?: 0) + 1
        }
    }

    suspend fun updateHighestWave(wave: Int, modeId: String = GameMode.STANDARD.id) {
        writeSafely { prefs ->
            if (wave > (prefs[Keys.HIGHEST_WAVE] ?: 0)) prefs[Keys.HIGHEST_WAVE] = wave
            if (modeId == GameMode.HACK_AI.id &&
                wave > (prefs[Keys.HIGHEST_WAVE_HACK_AI] ?: 0)
            ) {
                prefs[Keys.HIGHEST_WAVE_HACK_AI] = wave
            }
        }
    }

    /** RESET PROGRESS. Wipes everything, including settings. */
    suspend fun resetAllProgress() {
        writeSafely { prefs -> prefs.clear() }
    }

    // ------------------------------------------------------------- internals

    private suspend fun writeSafely(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        try {
            store.edit(block)
        } catch (error: IOException) {
            // Disk full, permissions, or a corrupt file. The run continues in
            // memory; losing a save is survivable, crashing is not.
            Log.w(TAG, "DataStore write failed", error)
        } catch (error: Exception) {
            Log.w(TAG, "Unexpected DataStore write failure", error)
        }
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<Preferences>.emitSafely(cause: Throwable) {
        Log.w(TAG, "DataStore read failed; falling back to defaults", cause)
        emit(emptyPreferences())
    }

    private fun decodeRun(raw: String?): SavedRun? {
        if (raw.isNullOrBlank()) return null
        return try {
            val run = json.decodeFromString(SavedRun.serializer(), raw)
            if (run.isResumable) run else null
        } catch (error: Exception) {
            Log.w(TAG, "Saved run is corrupt; discarding it", error)
            null
        }
    }

    private fun decodeDeployments(raw: String?): Map<String, Int> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            json.decodeFromString(DeploymentMapSerializer, raw)
        } catch (error: Exception) {
            Log.w(TAG, "Deployment stats are corrupt; resetting them", error)
            emptyMap()
        }
    }

    private fun encodeDeployments(map: Map<String, Int>): String = try {
        json.encodeToString(DeploymentMapSerializer, map)
    } catch (error: Exception) {
        Log.w(TAG, "Could not encode deployment stats", error)
        "{}"
    }

    private object Keys {
        val MUSIC_VOLUME = floatPreferencesKey("music_volume")
        val SFX_VOLUME = floatPreferencesKey("sfx_volume")
        val VIBRATION = booleanPreferencesKey("vibration")
        val BACKGROUND_ANIMATION = booleanPreferencesKey("background_animation")
        val DAMAGE_NUMBERS = booleanPreferencesKey("damage_numbers")
        val SHOW_RANGE = booleanPreferencesKey("show_range")
        val AUTO_START = booleanPreferencesKey("auto_start")
        val AUTO_START_BOSS = booleanPreferencesKey("auto_start_boss")
        val SCREEN_SHAKE = booleanPreferencesKey("screen_shake")
        val BATTERY_SAVER = booleanPreferencesKey("battery_saver")

        val HIGHEST_WAVE = intPreferencesKey("highest_wave")
        val HIGHEST_WAVE_HACK_AI = intPreferencesKey("highest_wave_hack_ai")
        val TOTAL_PACKETS = intPreferencesKey("total_attacks")
        val TOTAL_BOSSES = intPreferencesKey("total_bosses")
        val TOTAL_CRYPTO = intPreferencesKey("total_crypto")
        val TOTAL_GAMES = intPreferencesKey("total_games")
        val TOTAL_SERVER_DAMAGE = intPreferencesKey("total_server_damage")
        val TOTAL_DEPLOYED = intPreferencesKey("total_deployed")
        val TOTAL_UPGRADES = intPreferencesKey("total_upgrades")
        val DEPLOYMENTS_JSON = stringPreferencesKey("deployments_json")

        val BUDGET = longPreferencesKey("budget")

        /**
         * Which € scale this save is written in.
         *
         * Absent means the pre-1.24.0 scale, before the whole € economy was
         * multiplied by ten. See [migrateBudgetScale].
         */
        val BUDGET_SCALE_VERSION = intPreferencesKey("budget_scale_version")
        val LIFETIME_BUDGET = longPreferencesKey("lifetime_budget")
        val FIRMWARE_LEVEL = intPreferencesKey("firmware_level")
        val UNLOCKED_AGENTS = stringPreferencesKey("unlocked_agents")
        val TUTORIAL_DONE = booleanPreferencesKey("tutorial_done")
        val MENU_GUIDE_SEEN = booleanPreferencesKey("menu_guide_seen")
        val FIRMWARE_GUIDE_SEEN = booleanPreferencesKey("firmware_guide_seen")
        val SAVED_RUN = stringPreferencesKey("saved_run")

        // --- store, identity and the local leaderboard ---------------------
        val OWNED_PRODUCTS = stringPreferencesKey("owned_products")
        /** Order ids already credited, so a consumable pays out exactly once. */
        val REDEEMED_ORDERS = stringPreferencesKey("redeemed_orders")
        val CORE_SKIN = stringPreferencesKey("core_skin")
        val BACKGROUND_SKIN = stringPreferencesKey("background_skin")
        val SPECTRUM_AGENTS = booleanPreferencesKey("spectrum_agents")
        val USERNAME = stringPreferencesKey("username")
        val BEST_DAMAGE = longPreferencesKey("best_damage")
        val LEADERBOARD = stringPreferencesKey("leaderboard_json")
        val LAST_CLOUD_SYNC = longPreferencesKey("last_cloud_sync")
    }

    // ------------------------------------------- store, identity, leaderboard

    /**
     * Products this player owns, and the cosmetic they have chosen.
     *
     * Stored in the same DataStore as the rest of the save on purpose: crediting
     * a € pack has to move the *same* budget the game reads, and two stores
     * would be two writes that can disagree.
     */
    val storeState: Flow<StoreState> = store.data
        .catch { error ->
            Log.w(TAG, "Store state unreadable; starting from nothing owned", error)
            emit(emptyPreferences())
        }
        .map { prefs ->
            val owned = prefs[Keys.OWNED_PRODUCTS].toIdSet()
            val entitlements = Entitlements(owned)
            StoreState(
                entitlements = entitlements,
                cosmetics = CosmeticChoice(
                    coreSkinId = prefs[Keys.CORE_SKIN],
                    backgroundId = prefs[Keys.BACKGROUND_SKIN],
                    spectrumAgents = prefs[Keys.SPECTRUM_AGENTS] ?: true
                ).resolvedAgainst(entitlements),
                redeemedOrders = prefs[Keys.REDEEMED_ORDERS].toIdSet()
            )
        }

    /**
     * Record a confirmed purchase and apply what it grants.
     *
     * Returns the € credited, which is zero when this order was already
     * applied. Play re-reports owned products on every connect and every
     * restore, so crediting on each report would hand out free currency for
     * the life of the install. The order id is the guard, and it is kept even
     * for permanent products so the same rule covers everything.
     */
    suspend fun applyPurchase(sku: Sku, orderId: String): Int {
        var credited = 0
        store.edit { prefs ->
            val redeemed = prefs[Keys.REDEEMED_ORDERS].toIdSet()
            val alreadyApplied = orderId.isNotEmpty() && orderId in redeemed

            val owned = prefs[Keys.OWNED_PRODUCTS].toIdSet()
            prefs[Keys.OWNED_PRODUCTS] = (owned + Sku.unlockedBy(sku)).joinToString(SEPARATOR)

            if (!alreadyApplied && sku.grantsBudget > 0) {
                prefs[Keys.BUDGET] = (prefs[Keys.BUDGET] ?: 0L) + sku.grantsBudget
                prefs[Keys.LIFETIME_BUDGET] =
                    (prefs[Keys.LIFETIME_BUDGET] ?: 0L) + sku.grantsBudget
                credited = sku.grantsBudget
            }
            if (orderId.isNotEmpty()) {
                prefs[Keys.REDEEMED_ORDERS] = (redeemed + orderId).joinToString(SEPARATOR)
            }
        }
        return credited
    }

    suspend fun chooseCoreSkin(id: String?) = store.edit { prefs ->
        if (id == null) prefs.remove(Keys.CORE_SKIN) else prefs[Keys.CORE_SKIN] = id
    }.let { }

    suspend fun chooseBackground(id: String?) = store.edit { prefs ->
        if (id == null) prefs.remove(Keys.BACKGROUND_SKIN) else prefs[Keys.BACKGROUND_SKIN] = id
    }.let { }

    suspend fun setSpectrumAgents(enabled: Boolean) = store.edit { prefs ->
        prefs[Keys.SPECTRUM_AGENTS] = enabled
    }.let { }

    // ------------------------------------------------- identity, leaderboard

    val identity: Flow<PlayerIdentity> = store.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            PlayerIdentity(
                username = prefs[Keys.USERNAME].orEmpty(),
                highestWave = prefs[Keys.HIGHEST_WAVE] ?: 0,
                bestDamage = prefs[Keys.BEST_DAMAGE] ?: 0L
            )
        }

    suspend fun setUsername(name: String) = store.edit { prefs ->
        prefs[Keys.USERNAME] = PlayerIdentity.sanitize(name)
    }.let { }

    /** Records a run's damage total if it beats the player's best. */
    suspend fun recordDamage(total: Long) = store.edit { prefs ->
        if (total > (prefs[Keys.BEST_DAMAGE] ?: 0L)) prefs[Keys.BEST_DAMAGE] = total
    }.let { }

    /**
     * The local leaderboard.
     *
     * Decoded defensively like every other stored blob: a board that failed to
     * parse must cost the player their scores, not their ability to open the
     * screen.
     */
    suspend fun leaderboard(): List<LeaderboardEntry> {
        val raw = store.data.first()[Keys.LEADERBOARD] ?: return emptyList()
        return try {
            Json.decodeFromString(LeaderboardSerializer, raw)
        } catch (error: Exception) {
            Log.w(TAG, "Leaderboard unreadable; starting a fresh board", error)
            emptyList()
        }
    }

    suspend fun saveLeaderboard(entries: List<LeaderboardEntry>) {
        val encoded = try {
            Json.encodeToString(LeaderboardSerializer, entries)
        } catch (error: Exception) {
            Log.w(TAG, "Could not encode the leaderboard", error)
            return
        }
        store.edit { prefs -> prefs[Keys.LEADERBOARD] = encoded }
    }

    // ------------------------------------------------------------ cloud save

    /** When the account's copy was last confirmed, or null if never. */
    val lastCloudSync: Flow<Long?> = store.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> prefs[Keys.LAST_CLOUD_SYNC]?.takeIf { it > 0L } }

    suspend fun setLastCloudSync(millis: Long) = store.edit { prefs ->
        prefs[Keys.LAST_CLOUD_SYNC] = millis
    }.let { }

    // ---------------------------------------------- cloud save: the payload

    /**
     * Everything worth carrying to another device, in one snapshot.
     *
     * Read through the same flows the game reads, so a cloud save can never
     * disagree with what the player is looking at.
     */
    suspend fun exportCloudSave(device: String): CloudSave = CloudSave(
        schema = CloudSave.SCHEMA,
        savedAtMillis = System.currentTimeMillis(),
        device = device,
        stats = stats.first(),
        progress = progress.first(),
        identity = identity.first(),
        cosmetics = storeState.first().cosmetics,
        run = savedRun.first(),
        leaderboard = leaderboard()
    )

    /**
     * Writes a merged save back.
     *
     * One `edit` for the whole payload, because a half-applied restore is the
     * worst outcome available here: a player with someone else's wave count and
     * their own wallet, or a firmware level they did not pay for. Either the
     * save lands or none of it does.
     *
     * Entitlements are untouched on purpose — Play owns those, and a save file
     * must never be able to grant paid content.
     */
    suspend fun importCloudSave(save: CloudSave) {
        val runJson = save.run?.let {
            try {
                json.encodeToString(SavedRun.serializer(), it)
            } catch (error: Exception) {
                Log.w(TAG, "Restored run could not be encoded; dropping it", error)
                null
            }
        }
        val boardJson = try {
            Json.encodeToString(LeaderboardSerializer, save.leaderboard)
        } catch (error: Exception) {
            Log.w(TAG, "Restored leaderboard could not be encoded; dropping it", error)
            null
        }

        writeSafely { prefs ->
            prefs[Keys.HIGHEST_WAVE] = save.stats.highestWave
            prefs[Keys.TOTAL_PACKETS] = save.stats.totalAttacksBlocked.toInt()
            prefs[Keys.TOTAL_BOSSES] = save.stats.totalBossesDefeated.toInt()
            prefs[Keys.TOTAL_CRYPTO] = save.stats.totalCryptoEarned.toInt()
            prefs[Keys.TOTAL_GAMES] = save.stats.totalGamesPlayed.toInt()
            prefs[Keys.TOTAL_SERVER_DAMAGE] = save.stats.totalServerDamageTaken.toInt()
            prefs[Keys.TOTAL_DEPLOYED] = save.stats.totalAgentsDeployed.toInt()
            prefs[Keys.TOTAL_UPGRADES] = save.stats.totalAgentUpgrades.toInt()
            prefs[Keys.DEPLOYMENTS_JSON] = encodeDeployments(save.stats.deploymentsByAgent)

            prefs[Keys.BUDGET] = save.progress.budget
            prefs[Keys.LIFETIME_BUDGET] = save.progress.lifetimeBudgetEarned
            prefs[Keys.FIRMWARE_LEVEL] = save.progress.firmwareLevel
            prefs[Keys.UNLOCKED_AGENTS] = save.progress.unlockedAgents.joinToString("|")
            prefs[Keys.TUTORIAL_DONE] = save.progress.tutorialCompleted

            if (save.identity.username.isNotBlank()) {
                prefs[Keys.USERNAME] = save.identity.username
            }
            prefs[Keys.BEST_DAMAGE] = save.identity.bestDamage

            save.cosmetics.coreSkinId?.let { prefs[Keys.CORE_SKIN] = it }
            save.cosmetics.backgroundId?.let { prefs[Keys.BACKGROUND_SKIN] = it }
            prefs[Keys.SPECTRUM_AGENTS] = save.cosmetics.spectrumAgents

            if (runJson != null) prefs[Keys.SAVED_RUN] = runJson else prefs.remove(Keys.SAVED_RUN)
            if (boardJson != null) prefs[Keys.LEADERBOARD] = boardJson
        }
    }

    private fun String?.toIdSet(): Set<String> =
        this?.split(SEPARATOR)?.filter { it.isNotBlank() }?.toSet() ?: emptySet()

    private companion object {
        const val TAG = "CyOpsSave"
        const val SEPARATOR = "\u001F"
        val DeploymentMapSerializer = MapSerializer(String.serializer(), Int.serializer())
        val LeaderboardSerializer = ListSerializer(LeaderboardEntry.serializer())
    }
}
