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
