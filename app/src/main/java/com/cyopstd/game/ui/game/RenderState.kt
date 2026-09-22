package com.cyopstd.game.ui.game

import com.cyopstd.game.model.AgentType

/**
 * Everything the battlefield renderer needs that is *not* simulation state:
 * what the player has selected, and which cosmetic settings are on.
 */
data class BattlefieldRenderOptions(
    val backgroundAnimation: Boolean = true,
    val showAgentRange: Boolean = true,
    val screenShake: Boolean = true,
    val batterySaver: Boolean = false
)

/** What the player currently has picked up or picked out. */
data class BattlefieldSelection(
    /** An agent chosen in the deploy panel and waiting for a node tap. */
    val pendingAgent: AgentType? = null,
    /** A deployed agent the player tapped, whose management panel is open. */
    val selectedNodeId: Int? = null
)
