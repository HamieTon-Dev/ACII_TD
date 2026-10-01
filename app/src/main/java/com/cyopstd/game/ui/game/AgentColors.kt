package com.cyopstd.game.ui.game

import androidx.compose.ui.graphics.Color
import com.cyopstd.game.model.AgentType
import com.cyopstd.game.ui.theme.Palette

/**
 * Each agent class's colour on the board. One table, so the deploy bar's
 * icons are always the colour the agent will be once placed.
 */
fun agentClassColor(type: AgentType): Color = when (type) {
    AgentType.TARPIT -> Palette.Blue
    AgentType.FIREWALL -> Palette.Green
    AgentType.IDS -> Palette.Cyan
    AgentType.IPS -> Palette.Blue
    AgentType.ANALYST -> Palette.Crypto
    AgentType.CRYPTOGRAPHER -> Palette.Purple
    AgentType.ZERO_DAY_HUNTER -> Palette.Orange
    AgentType.AI_SENTINEL -> Palette.Cyan
    AgentType.QUANTUM_DEFENDER -> Palette.Purple
    AgentType.ROOT_ADMIN -> Palette.Green
    AgentType.NETWORK_ARCHITECT -> Palette.Purple
    // The hats are named for their colour, so they are drawn in it.
    AgentType.REDHAT -> Palette.Red
    AgentType.BLUEHAT -> Palette.Blue
    // Green, like the "+ +" it floats over the core.
    AgentType.SERVER_SYSTEMS_ENGINEER -> Palette.Green
    // The spade of a deck: bright and plain, to stand apart from every class colour.
    AgentType.ACE -> Palette.TextPrimary
    // The hologram's green; on the board it shimmers into yellow (♡2).
    AgentType.ANTI_DUCK -> Palette.HologramGreen
    // The logo's mint face.
    AgentType.CYBER_OPERATIVE -> Palette.LogoMint
}
