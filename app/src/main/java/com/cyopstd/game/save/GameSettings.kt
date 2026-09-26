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
