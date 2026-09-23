package com.cyopstd.game.save

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.PlayGamesSdk
import com.google.android.gms.games.SnapshotsClient
import com.google.android.gms.games.snapshot.Snapshot
import com.google.android.gms.games.snapshot.SnapshotMetadataChange
import com.google.android.gms.tasks.Task
import java.lang.ref.WeakReference
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json

/**
 * Cloud save on Play Games Services *Saved Games*.
 *
 * Why this and not a server: the save already has a home. Play Games keeps a
 * per-game snapshot in the player's own Google account (in the app-private part
 * of their Drive, which is why linking asks for permission to manage this app's
 * data). It costs nothing to run, it is covered by Google's own privacy terms
 * rather than ones this project would have to write and honour, and it is the
 * mechanism a player already recognises — it is what every other Android game
 * that survives a new phone uses. A custom backend would mean an account
 * system, a password reset flow, a privacy policy, a server to keep alive, and
 * a new way for a player to lose everything.
 *
 * Three rules shape this class:
 *
 * 1. **Linking is optional and reversible.** Declining leaves a completely
 *    playable game with local saves; the cloud buys portability, not access.
 * 2. **A failed sync is never a lost save.** Every path writes locally first
 *    and treats the cloud as a copy. If Google is unreachable, the player keeps
 *    playing and the next sync catches up.
 * 3. **Nothing here decides what a player keeps.** Conflicts are resolved by
 *    [CloudSaveMerge], which is pure and tested. This class only moves bytes.
 */
class PlayGamesCloudSave(
    context: Context,
    private val deviceLabel: String
) : CloudSaveGateway {

    private val appContext = context.applicationContext
    private var activityRef = WeakReference<Activity?>(null)
    private var initialized = false

    override val status = MutableStateFlow(CloudSaveStatus.NOT_LINKED)
    override val accountName = MutableStateFlow<String?>(null)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    /**
     * Held weakly, like the billing and ad gateways.
     *
     * Play Games' clients are Activity-scoped because linking and the consent
     * dialog are UI. A strong reference here would outlive a rotation and leak
     * the whole window.
     */
    fun attach(activity: Activity) {
        activityRef = WeakReference(activity)
        if (!initialized) {
            initialized = true
            runCatching { PlayGamesSdk.initialize(appContext) }
                .onFailure { Log.w(TAG, "Play Games SDK would not initialise", it) }
        }
    }

    /** Refreshes [status] without prompting. Safe to call on every resume. */
    suspend fun refresh() {
        val activity = activityRef.get() ?: return
        val result = runCatching {
            PlayGames.getGamesSignInClient(activity).isAuthenticated.await()
        }.getOrNull()
        if (result?.isAuthenticated == true) {
            status.value = CloudSaveStatus.LINKED
            loadAccountName()
        } else if (status.value == CloudSaveStatus.LINKED) {
            // Signed out elsewhere, or the account was removed from the device.
            status.value = CloudSaveStatus.NOT_LINKED
            accountName.value = null
        }
    }

    override suspend fun signIn(): Boolean {
        val activity = activityRef.get() ?: return false
        status.value = CloudSaveStatus.CONNECTING
        val client = PlayGames.getGamesSignInClient(activity)

        // Already authenticated is the common case: Play Games v2 signs a
        // player in automatically when they have opted into that, and asking
        // again would show a dialog for nothing.
        val existing = runCatching { client.isAuthenticated.await() }.getOrNull()
        if (existing?.isAuthenticated == true) {
            status.value = CloudSaveStatus.LINKED
            loadAccountName()
            return true
        }

        val signedIn = runCatching { client.signIn().await() }.getOrNull()
        return if (signedIn?.isAuthenticated == true) {
            status.value = CloudSaveStatus.LINKED
            loadAccountName()
            true
        } else {
            // A decline is not an error. It is a choice, and the game carries
            // on locally, so it must not leave a red banner behind.
            status.value = CloudSaveStatus.NOT_LINKED
            false
        }
    }

    override suspend fun load(): CloudSave? {
        val snapshots = snapshotsClient() ?: return null
        return try {
            val opened = snapshots.open(
                CloudSave.SNAPSHOT_NAME,
                /* createIfNotFound = */ true,
                SnapshotsClient.RESOLUTION_POLICY_MOST_RECENTLY_MODIFIED
            ).await() ?: return null

            val snapshot: Snapshot = opened.data ?: run {
                // A conflict Play could not resolve on its own. Our own merge
                // handles disagreement, so the safe move is to leave both
                // copies alone and let the next write settle it rather than
                // guess here.
                Log.w(TAG, "Snapshot conflict; leaving it for the next sync")
                status.value = CloudSaveStatus.ERROR
                return null
            }

            val bytes = snapshot.snapshotContents.readFully()
            snapshots.discardAndClose(snapshot)
            status.value = CloudSaveStatus.LINKED
            decode(bytes)
        } catch (error: Exception) {
            Log.w(TAG, "Could not read the cloud save", error)
            status.value = CloudSaveStatus.ERROR
            null
        }
    }

    override suspend fun save(save: CloudSave): Boolean {
        val snapshots = snapshotsClient() ?: return false
        return try {
            val opened = snapshots.open(
                CloudSave.SNAPSHOT_NAME,
                true,
                SnapshotsClient.RESOLUTION_POLICY_MOST_RECENTLY_MODIFIED
            ).await() ?: return false
            val snapshot: Snapshot = opened.data ?: return false

            // Merge against whatever is already up there before overwriting it.
            // Another device may have played since this one last looked, and a
            // blind overwrite is exactly how a player loses an evening.
            val existing = runCatching { decode(snapshot.snapshotContents.readFully()) }
                .getOrNull()
            val merged = if (existing == null) save else CloudSaveMerge.merge(save, existing)

            snapshot.snapshotContents.writeBytes(encode(merged))
            val change = SnapshotMetadataChange.Builder()
                .setDescription(describe(merged))
                // Play's own automatic conflict resolution can use this, and
                // giving it the same number our merge orders by means the two
                // can never disagree about which save is further along.
                .setProgressValue(merged.progressValue)
                .build()
            snapshots.commitAndClose(snapshot, change).await()
            status.value = CloudSaveStatus.LINKED
            true
        } catch (error: Exception) {
            Log.w(TAG, "Could not write the cloud save", error)
            status.value = CloudSaveStatus.ERROR
            false
        }
    }

    override fun release() {
        activityRef = WeakReference(null)
    }

    // ------------------------------------------------------------- internals

    private fun snapshotsClient(): SnapshotsClient? {
        val activity = activityRef.get() ?: return null
        if (status.value != CloudSaveStatus.LINKED && status.value != CloudSaveStatus.ERROR) {
            return null
        }
        return runCatching { PlayGames.getSnapshotsClient(activity) }.getOrNull()
    }

    private suspend fun loadAccountName() {
        val activity = activityRef.get() ?: return
        val player = runCatching {
            PlayGames.getPlayersClient(activity).currentPlayer.await()
        }.getOrNull()
        accountName.value = player?.displayName
    }

    private fun encode(save: CloudSave): ByteArray =
        json.encodeToString(CloudSave.serializer(), save).toByteArray(Charsets.UTF_8)

    private fun decode(bytes: ByteArray?): CloudSave? {
        if (bytes == null || bytes.isEmpty()) return null
        return try {
            val decoded = json.decodeFromString(
                CloudSave.serializer(),
                String(bytes, Charsets.UTF_8)
            )
            // A save from a future build may mean something different by the
            // same field. Refusing to read it loses nothing; misreading it
            // could overwrite good progress with a wrong interpretation.
            if (decoded.schema > CloudSave.SCHEMA) {
                Log.w(TAG, "Cloud save schema ${decoded.schema} is newer than this build")
                null
            } else {
                decoded
            }
        } catch (error: Exception) {
            Log.w(TAG, "Cloud save is unreadable; keeping the local one", error)
            null
        }
    }

    /** What the player sees in Play Games' own saved-games list. */
    private fun describe(save: CloudSave) =
        "Wave ${save.stats.highestWave} · ${save.progress.unlockedAgents.size} agents · $deviceLabel"

    /**
     * Bridges a Play Services [Task] into a coroutine.
     *
     * Written by hand rather than pulling in kotlinx-coroutines-play-services
     * for one function. The continuation is resumed exactly once on every
     * path — a Task that both succeeds and cancels would otherwise crash the
     * app, and a cloud save is not worth a crash.
     */
    private suspend fun <T> Task<T>.await(): T? = suspendCancellableCoroutine { cont ->
        var resumed = false
        addOnCompleteListener { task ->
            if (resumed) return@addOnCompleteListener
            resumed = true
            if (task.isSuccessful) {
                cont.resume(task.result)
            } else {
                Log.i(TAG, "Play Games task failed", task.exception)
                cont.resume(null)
            }
        }
    }

    private companion object {
        const val TAG = "CyOpsCloud"
    }
}
