package com.cyopstd.game.core

/**
 * The difficulty a run is played at.
 *
 * A mode is a set of multipliers over the existing curves rather than a second
 * copy of the balance table. That means every rebalance of the normal game
 * carries into the hard one automatically, and there is no way for the two to
 * drift apart — which is what happens when a "hard mode" is a duplicated set
 * of numbers somebody has to remember to update twice.
 */
enum class GameMode(
    val id: String,
    /** Shown at the top of a run. */
    val runName: String,
    /** How far the player must have reached in [STANDARD] to unlock this. */
    val unlockAtWave: Int,
    /** Multiplies the health curve. */
    val healthScale: Double,
    /** Multiplies crypto rewards, so a harder run is not also a poorer one. */
    val rewardScale: Float,
    /** Multiplies the gap between spawns; below 1 means heavier pressure. */
    val spawnIntervalScale: Float,
    /** Starting integrity. */
    val serverHp: Int
) {
    STANDARD(
        id = "standard",
        runName = "NETWORK DEFENCE",
        unlockAtWave = 0,
        healthScale = 1.0,
        rewardScale = 1f,
        spawnIntervalScale = 1f,
        serverHp = Balance.SERVER_MAX_HP
    ),

    /**
     * Unlocked only by clearing wave 100 on [STANDARD].
     *
     * Harder in three ways at once rather than one: threats are tougher, they
     * arrive closer together, and the core has less to give. Rewards are lifted
     * so the mode is a harder *game* and not merely a slower one.
     */
    HACK_AI(
        id = "hack_ai",
        runName = "HACK:AI",
        unlockAtWave = 100,
        healthScale = 2.35,
        rewardScale = 1.5f,
        spawnIntervalScale = 0.72f,
        serverHp = 70
    );

    val isUnlockedByDefault: Boolean get() = unlockAtWave <= 0

    fun unlockedBy(highestWaveReached: Int): Boolean = highestWaveReached >= unlockAtWave

    companion object {
        fun fromIdSafe(id: String?): GameMode =
            entries.firstOrNull { it.id == id } ?: STANDARD
    }
}
