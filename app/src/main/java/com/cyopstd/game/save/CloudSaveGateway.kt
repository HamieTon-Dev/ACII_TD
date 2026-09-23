package com.cyopstd.game.save

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Where a player's progress goes when it needs to outlive the device.
 *
 * The interface is deliberately tiny — sign in, read, write — because the hard
 * part of cloud save is not the transport. It is deciding what happens when two
 * devices disagree, and that decision lives in [CloudSaveMerge], where it can be
 * tested without a network, an account or a phone.
 */
interface CloudSaveGateway {

    val status: Flow<CloudSaveStatus>

    /** The linked account's display name, once Play tells us one. */
    val accountName: Flow<String?>

    /**
     * Asks the player to link their Google account.
     *
     * Returns true only if the account is genuinely linked afterwards. The
     * player can decline, and declining must leave a completely playable game
     * behind — linking buys portability, not access.
     */
    suspend fun signIn(): Boolean

    /** The snapshot Google holds, or null if there is none (or it is unreadable). */
    suspend fun load(): CloudSave?

    /** Writes [save]. Returns false if it did not land, so the UI can say so. */
    suspend fun save(save: CloudSave): Boolean

    fun release()
}

enum class CloudSaveStatus {
    /** No Play Games configured in this build, or no Play Services on the device. */
    UNAVAILABLE,

    /** Available, but the player has not linked an account. */
    NOT_LINKED,

    CONNECTING,

    /** Linked. Progress is being carried. */
    LINKED,

    /** Linked, but the last operation failed — usually no network. */
    ERROR
}

/**
 * The gateway used when there is no Play Games to talk to.
 *
 * This is what an unconfigured checkout ships with, and what a unit test gets.
 * It links nothing and stores nothing, and the game is completely playable:
 * saves stay on the device exactly as they did before cloud save existed.
 * Android's own Auto Backup still carries them to a new phone on first install,
 * which is the floor this class is allowed to fall back to.
 */
class NoCloudSaveGateway : CloudSaveGateway {
    override val status = MutableStateFlow(CloudSaveStatus.UNAVAILABLE)
    override val accountName = MutableStateFlow<String?>(null)
    override suspend fun signIn(): Boolean = false
    override suspend fun load(): CloudSave? = null
    override suspend fun save(save: CloudSave): Boolean = false
    override fun release() = Unit
}
