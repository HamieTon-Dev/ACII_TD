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
    val serverHp: Int,
    /**
     * For a mode unlocked on one level in one mode rather than by a lifetime
     * best: that level's `GameMap.id` and that mode's [id]. Strings, because
     * an enum cannot name its own entries in its constructor.
     */
    val unlockMapId: String? = null,
    val unlockModeId: String? = null,
    /** How the unlock reads, e.g. "DDoS in HACK:AI"; null for "any level". */
    val unlockWhere: String? = null
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
    ),

    /**
     * ♡4 (owner, 2026-10-01): KERNEL MODE, unlocked by clearing wave 100 on
     * DDoS in HACK:AI. HACK:AI pushed further on every axis: threats about
     * 1.7× as tough again, closer still, half the integrity of NETWORK
     * DEFENCE. Rewards double so it pays for the trouble.
     */
    KERNEL_MODE(
        id = "kernel_mode",
        runName = "KERNEL MODE",
        unlockAtWave = 100,
        healthScale = 4.0,
        rewardScale = 2.0f,
        spawnIntervalScale = 0.6f,
        serverHp = 50,
        unlockMapId = "ddos",
        unlockModeId = "hack_ai",
        unlockWhere = "DDoS in HACK:AI"
    );

    val isUnlockedByDefault: Boolean get() = unlockAtWave <= 0

    fun unlockedBy(highestWaveReached: Int): Boolean =
        unlockMapId == null && highestWaveReached >= unlockAtWave

    /**
     * Whether a player with these records has earned this mode. [bestOn] is
     * the best wave on a level in a mode, by ids.
     */
    fun unlockedBy(highestWaveReached: Int, bestOn: (mapId: String, modeId: String) -> Int): Boolean =
        if (unlockMapId != null && unlockModeId != null) bestOn(unlockMapId, unlockModeId) >= unlockAtWave
        else highestWaveReached >= unlockAtWave

    /** The best counting toward this mode's unlock. */
    fun bestTowardUnlock(highestWaveReached: Int, bestOn: (mapId: String, modeId: String) -> Int): Int =
        if (unlockMapId != null && unlockModeId != null) bestOn(unlockMapId, unlockModeId)
        else highestWaveReached

    /** What unlocks it, in words: "clear wave 100 on DDoS in HACK:AI". */
    val unlockRequirement: String
        get() = "clear wave $unlockAtWave" + (unlockWhere?.let { " on $it" } ?: "")

    companion object {
        fun fromIdSafe(id: String?): GameMode =
            entries.firstOrNull { it.id == id } ?: STANDARD
    }
}
