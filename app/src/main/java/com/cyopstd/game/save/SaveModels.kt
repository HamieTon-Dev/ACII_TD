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
    val savedAtMillis: Long = 0L
) {
    /** A run is only worth offering as CONTINUE if the server is still standing. */
    val isResumable: Boolean get() = serverHp > 0 && wave >= 0
}

@Serializable
data class PlayerStats(
    val highestWave: Int = 0,
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
    val lifetimeBudgetEarned: Long = 0
)

const val SAVE_VERSION = 1

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
