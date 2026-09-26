package com.cyopstd.game.save

import kotlinx.serialization.Serializable

/** Everything the SETTINGS screen controls. Defaults are the shipped experience. */
@Serializable
data class GameSettings(
    val musicVolume: Float = 0.5f,
    val sfxVolume: Float = 0.8f,
    val vibrationEnabled: Boolean = true,
    val backgroundAnimation: Boolean = true,
    val damageNumbers: Boolean = true,
    val showAgentRange: Boolean = true,
    val autoStartWaves: Boolean = false,
    /**
     * Whether auto-start also starts boss waves. Off by default: a boss wave
     * waits for the player to press start, so they can build up for it
     * (owner, 2026-09-26). Only matters when [autoStartWaves] is on.
     */
    val autoStartBossWaves: Boolean = false,
    /**
     * How opaque the in-game pop-up panels are (boss briefing, boss dossier,
     * agent panel, deploy bar), so the board shows through them (owner,
     * 2026-09-26). Backgrounds only: text stays solid. Kept within
     * [MIN_PANEL_OPACITY]..1.
     */
    val panelOpacity: Float = DEFAULT_PANEL_OPACITY,
    val screenShake: Boolean = true,
    val batterySaver: Boolean = false,
    /**
     * Whether the main menu powers on rather than simply appearing.
     *
     * Off means off, not faster: the menu is drawn complete with no fade at
     * all. Battery saver also suppresses it, for the same reason it drops the
     * living backgrounds -- it is a decorative animation.
     */
    val menuBootSequence: Boolean = true
)

/** Below this a panel's text fights the board behind it. */
const val MIN_PANEL_OPACITY = 0.4f

/** 35% see-through: inside the 30-50% the owner asked for. */
const val DEFAULT_PANEL_OPACITY = 0.65f
