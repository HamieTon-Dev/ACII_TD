package com.packetbastion.asciidefense.save

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
import com.packetbastion.asciidefense.core.Balance
import com.packetbastion.asciidefense.model.AgentType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "packet_bastion")

/**
 * All persistence for Packet Bastion.
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
                screenShake = prefs[Keys.SCREEN_SHAKE] ?: true,
                batterySaver = prefs[Keys.BATTERY_SAVER] ?: false
            )
        }

    val stats: Flow<PlayerStats> = store.data
        .catch { cause -> emitSafely(cause) }
        .map { prefs ->
            PlayerStats(
                highestWave = prefs[Keys.HIGHEST_WAVE] ?: 0,
                totalPacketsBlocked = (prefs[Keys.TOTAL_PACKETS] ?: 0).toLong(),
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

    suspend fun unlockAgent(type: AgentType) {
        val current = progress.first().unlockedAgents
        if (type.name in current) return
        val merged = (current + type.name).joinToString("|")
        writeSafely { prefs -> prefs[Keys.UNLOCKED_AGENTS] = merged }
    }

    suspend fun setTutorialCompleted(completed: Boolean) {
        writeSafely { prefs -> prefs[Keys.TUTORIAL_DONE] = completed }
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
        packetsBlocked: Int,
        bossesDefeated: Int,
        cryptoEarned: Int,
        serverDamageTaken: Int,
        agentsDeployed: Int,
        agentUpgrades: Int,
        deploymentsByType: Map<String, Int>,
        countAsGamePlayed: Boolean
    ) {
        writeSafely { prefs ->
            val bestWave = prefs[Keys.HIGHEST_WAVE] ?: 0
            if (waveReached > bestWave) prefs[Keys.HIGHEST_WAVE] = waveReached

            prefs[Keys.TOTAL_PACKETS] = (prefs[Keys.TOTAL_PACKETS] ?: 0) + packetsBlocked
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

    suspend fun updateHighestWave(wave: Int) {
        writeSafely { prefs ->
            if (wave > (prefs[Keys.HIGHEST_WAVE] ?: 0)) prefs[Keys.HIGHEST_WAVE] = wave
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
        val SCREEN_SHAKE = booleanPreferencesKey("screen_shake")
        val BATTERY_SAVER = booleanPreferencesKey("battery_saver")

        val HIGHEST_WAVE = intPreferencesKey("highest_wave")
        val TOTAL_PACKETS = intPreferencesKey("total_packets")
        val TOTAL_BOSSES = intPreferencesKey("total_bosses")
        val TOTAL_CRYPTO = intPreferencesKey("total_crypto")
        val TOTAL_GAMES = intPreferencesKey("total_games")
        val TOTAL_SERVER_DAMAGE = intPreferencesKey("total_server_damage")
        val TOTAL_DEPLOYED = intPreferencesKey("total_deployed")
        val TOTAL_UPGRADES = intPreferencesKey("total_upgrades")
        val DEPLOYMENTS_JSON = stringPreferencesKey("deployments_json")

        val BUDGET = longPreferencesKey("budget")
        val LIFETIME_BUDGET = longPreferencesKey("lifetime_budget")
        val FIRMWARE_LEVEL = intPreferencesKey("firmware_level")
        val UNLOCKED_AGENTS = stringPreferencesKey("unlocked_agents")
        val TUTORIAL_DONE = booleanPreferencesKey("tutorial_done")
        val SAVED_RUN = stringPreferencesKey("saved_run")
    }

    private companion object {
        const val TAG = "PacketBastionSave"
        val DeploymentMapSerializer = MapSerializer(String.serializer(), Int.serializer())
    }
}
