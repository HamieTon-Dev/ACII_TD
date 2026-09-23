package com.cyopstd.game.save

import android.util.Log
import kotlinx.coroutines.flow.first

/** What a sync attempt actually did, in terms the UI can show a player. */
enum class CloudSyncResult {
    /** No Play Games in this build, or none on the device. */
    UNAVAILABLE,

    /** The player was asked to link an account and chose not to. */
    DECLINED,

    /** Linked, and the two saves were combined. */
    MERGED,

    /** Already linked; the local save was uploaded. */
    UPLOADED,

    /** Linked, but Google could not be reached. Nothing was lost. */
    FAILED
}

/**
 * Keeps the device's save and the account's save in step.
 *
 * Every path here is built around one promise: **a sync can fail, but it can
 * never cost the player progress.** So the order is always pull, merge, apply,
 * push — never push-then-pull, and never apply a remote save on its own. The
 * merge is [CloudSaveMerge], which is pure and tested, so the interesting
 * behaviour of cloud save is verifiable without an account or a network.
 *
 * The device label exists for one reason: when two devices disagree, a player
 * deserves to be told which one they are looking at.
 */
class CloudSaveSync(
    private val repository: GameRepository,
    private val gateway: CloudSaveGateway,
    private val device: String
) {

    /** True once an account is linked, so callers can skip quiet syncs. */
    suspend fun isLinked(): Boolean = gateway.status.first() == CloudSaveStatus.LINKED

    /**
     * Links an account and reconciles the two saves.
     *
     * This is what the player presses. It may show Google's own consent dialog,
     * asking for permission to manage this game's saved data in their account.
     */
    suspend fun link(): CloudSyncResult {
        if (gateway.status.first() == CloudSaveStatus.UNAVAILABLE) return CloudSyncResult.UNAVAILABLE
        if (!gateway.signIn()) return CloudSyncResult.DECLINED
        return sync()
    }

    /**
     * Pull, merge, apply, push.
     *
     * The merged save is applied locally *before* it is uploaded, so a failure
     * on the way up leaves the player holding the better of the two saves
     * rather than the worse one.
     */
    suspend fun sync(): CloudSyncResult {
        val status = gateway.status.first()
        if (status == CloudSaveStatus.UNAVAILABLE) return CloudSyncResult.UNAVAILABLE
        if (status != CloudSaveStatus.LINKED && status != CloudSaveStatus.ERROR) {
            return CloudSyncResult.DECLINED
        }

        val local = repository.exportCloudSave(device)
        val remote = try {
            gateway.load()
        } catch (error: Exception) {
            Log.w(TAG, "Cloud read failed", error)
            null
        }

        val merged = if (remote == null) local else CloudSaveMerge.merge(local, remote)
        if (remote != null && merged != local) {
            repository.importCloudSave(merged)
        }

        val uploaded = gateway.save(merged)
        return when {
            !uploaded -> CloudSyncResult.FAILED
            remote != null -> CloudSyncResult.MERGED
            else -> CloudSyncResult.UPLOADED
        }
    }

    /**
     * A quiet upload, for leaving the app or finishing a run.
     *
     * Does nothing at all when no account is linked. It never prompts: a player
     * who has not linked must not be nagged by a dialog every time they pause.
     */
    suspend fun syncQuietly(): CloudSyncResult {
        if (!isLinked()) return CloudSyncResult.DECLINED
        return sync()
    }

    private companion object {
        const val TAG = "CyOpsCloud"
    }
}
