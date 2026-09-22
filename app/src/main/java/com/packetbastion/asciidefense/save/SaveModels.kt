package com.packetbastion.asciidefense.save

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
    val packetsBlocked: Int = 0,
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
    val totalPacketsBlocked: Long = 0,
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
