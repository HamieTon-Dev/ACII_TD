package com.cyopstd.game.save

import kotlinx.serialization.Serializable

/**
 * Persisted shapes. Every field carries a default so that a save written by an
 * older build still deserializes cleanly against a newer one — adding a field is
 * a non-breaking change, which matters for a game meant to be expanded.
 */

@Serializable
data class SavedAgent(
    val nodeId: Int = 0,
    val type: String = "FIREWALL",
    val level: Int = 1,
    val targeting: Int = 0
)

@Serializable
data class SavedRun(
    val version: Int = SAVE_VERSION,
    /** The wave the player was on. It is replayed from the start on resume. */
    val wave: Int = 0,
    val serverHp: Int = 100,
    val crypto: Int = 0,
    val agents: List<SavedAgent> = emptyList(),
    val attacksBlocked: Int = 0,
    val cryptoEarned: Int = 0,
    val bossesDefeated: Int = 0,
    val serverDamageTaken: Int = 0,
    val agentsDeployed: Int = 0,
    val agentUpgrades: Int = 0,
    val budgetEarned: Int = 0,
    val savedAtMillis: Long = 0L,
    /**
     * Which level the run was played on.
     *
     * **This is what makes the save safe.** Agents are stored by node id, and
     * a node id is a position in a per-map array — node 17 on one level is a
     * different patch of ground on another. Without this, resuming after the
     * second map shipped would have scattered a player's whole board across
     * the wrong map, silently, with no error anywhere.
     *
     * Defaults to the original map so saves written before this field existed
     * resume onto the level they were actually played on.
     */
    val mapId: String = "perimeter",
    /**
     * Which mode the run was played on.
     *
     * Same reasoning, different symptom: without it a saved HACK:AI run
     * resumed as a standard one — wrong integrity, wrong health curve, wrong
     * spawn pressure — and nothing said so.
     */
    val modeId: String = "standard",
    /**
     * Rewarded revives already spent on this run.
     *
     * Carried in the save because the rule is *one revive per run*, and a run
     * outlives the process it was started in. Without this, backgrounding the
     * game after a revive and coming back to a killed process handed the
     * player a second one — the counter lived only in the ViewModel, which is
     * exactly as durable as the Activity that owns it.
     *
     * Defaults to zero, which is both the right answer for a save written
     * before this field existed and the right answer for a fresh run.
     */
    val revivesUsed: Int = 0,
    /**
     * Real seconds of unpaused play in this run, across revives and restarts
     * of the app. The lost-run ad is only shown once a run has lasted three
     * minutes, and a run resumed from CONTINUE has to remember the time it
     * had already put in. Zero for saves written before this field existed.
     */
    val playSeconds: Float = 0f
) {
    /** A run is only worth offering as CONTINUE if the server is still standing. */
    val isResumable: Boolean get() = serverHp > 0 && wave >= 0

    /**
     * ...and only if this build can still make sense of it.
     *
     * Kept as one property rather than two checks because the menu and the
     * restore have to agree: offering CONTINUE for a save that the restore
     * then refuses is a button that does nothing.
     */
    val isRestorable: Boolean get() = isResumable && version == SAVE_VERSION
}

@Serializable
data class PlayerStats(
    val highestWave: Int = 0,
    /**
     * The best wave reached **on HACK:AI specifically**.
     *
     * Separate from [highestWave] because the second level is unlocked by
     * clearing wave 100 *on that mode*, and a lifetime best cannot answer
     * "on which mode". Defaults to 0, so a player who earned it before this
     * existed re-earns it rather than being handed it on a technicality.
     */
    val highestWaveHackAi: Int = 0,
    /**
     * The best wave on the beginner level: NETWORK PERIMETER in NETWORK
     * DEFENCE mode. Unlocks the SERVER SYSTEMS ENGINEER at 100.
     */
    val highestWaveBeginner: Int = 0,
    val totalAttacksBlocked: Long = 0,
    val totalBossesDefeated: Long = 0,
    val totalCryptoEarned: Long = 0,
    val totalGamesPlayed: Long = 0,
    val totalServerDamageTaken: Long = 0,
    val totalAgentsDeployed: Long = 0,
    val totalAgentUpgrades: Long = 0,
    /** Deployment count keyed by AgentType.name; the max is the favourite agent. */
    val deploymentsByAgent: Map<String, Int> = emptyMap()
) {
    val favoriteAgent: String?
        get() = deploymentsByAgent.maxByOrNull { it.value }?.key
}

@Serializable
data class PlayerProgress(
    /** AgentType.name values the player has unlocked permanently. */
    val unlockedAgents: Set<String> = emptySet(),
    val tutorialCompleted: Boolean = false,
    /** Unspent meta-currency, earned at every tenth wave. */
    val budget: Long = 0,
    /** Purchased CORE FIRMWARE level; a permanent damage multiplier. */
    val firmwareLevel: Int = 0,
    /** Lifetime € earned, for the statistics screen. */
    val lifetimeBudgetEarned: Long = 0,
    /** The main-menu tour has been shown (or skipped). */
    val menuGuideSeen: Boolean = false,
    /** The FIRMWARE screen's first-visit explainer has been shown (or skipped). */
    val firmwareGuideSeen: Boolean = false
)

/**
 * Bumped whenever a change makes an older save mean something different.
 *
 * Adding a field never needs this — every field has a default, so an old save
 * still reads cleanly. What does need it is a change to what the existing
 * numbers *refer to*, and so far that means the deployment grid: agents are
 * stored by node id, a node id is a position in a derived array, and version 2
 * drops the stacked duplicate nodes the perimeter map used to generate. A
 * version 1 save restored against that array would put a player's whole board
 * on the wrong patches of ground, quietly.
 *
 * Older runs are discarded rather than migrated. There is no mapping to
 * migrate *to* — the node a save refers to may no longer exist — and a run is
 * one sitting, not a profile: nothing permanent (progress, purchases, stats)
 * lives in [SavedRun].
 */
const val SAVE_VERSION = 2

/**
 * Everything the store screen needs in one snapshot.
 *
 * [redeemedOrders] is carried alongside the entitlements because it is the only
 * thing preventing a consumable from paying out repeatedly: Play re-reports
 * owned products on every connect and every restore.
 */
@Serializable
data class StoreState(
    val entitlements: com.cyopstd.game.store.Entitlements =
        com.cyopstd.game.store.Entitlements(),
    val cosmetics: com.cyopstd.game.store.CosmeticChoice =
        com.cyopstd.game.store.CosmeticChoice(),
    val redeemedOrders: Set<String> = emptySet()
)

/** Who the player is on the leaderboard, and what they have to show for it. */
@Serializable
data class PlayerIdentity(
    val username: String = "",
    val highestWave: Int = 0,
    val bestDamage: Long = 0
) {
    val registered: Boolean get() = username.isNotBlank()

    companion object {
        const val MIN_LENGTH = 3
        const val MAX_LENGTH = 16

        /**
         * Usernames are uppercase, ASCII, and free of anything the ASCII UI
         * cannot render in a monospace cell. Applied on the way in so a stored
         * name never needs sanitising again on the way out.
         */
        fun sanitize(raw: String): String = raw
            .uppercase()
            .filter { it in 'A'..'Z' || it in '0'..'9' || it == '_' || it == '-' }
            .take(MAX_LENGTH)

        fun isValid(raw: String): Boolean = sanitize(raw).length >= MIN_LENGTH
    }
}
