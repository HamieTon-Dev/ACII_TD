package com.cyopstd.game.state

import com.cyopstd.game.engine.RunPhase

/**
 * An immutable, cheap-to-compare view of everything the HUD shows.
 *
 * The battlefield redraws every frame from live engine data, but the HUD is
 * Compose and should only recompose when a number actually changes. Rebuilding
 * this small snapshot each frame and assigning it only when it differs gives us
 * exactly that: 60 FPS rendering with a handful of HUD recompositions a second.
 */
data class HudSnapshot(
    val phase: RunPhase = RunPhase.PREPARING,
    val wave: Int = 0,
    val bestWave: Int = 0,
    val serverHp: Int = 100,
    val serverMaxHp: Int = 100,
    val crypto: Int = 0,
    val enemiesRemaining: Int = 0,
    val enemiesOnField: Int = 0,
    val nextWaveIsBoss: Boolean = false,
    val bossOnField: Boolean = false,
    val autoStartRemaining: Int = 0
) {
    val serverFraction: Float
        get() = if (serverMaxHp <= 0) 0f else serverHp.toFloat() / serverMaxHp
}

/** End-of-run report shown on the NETWORK COMPROMISED screen. */
data class GameOverSummary(
    val waveReached: Int,
    val attacksBlocked: Int,
    val cryptoEarned: Int,
    val bossesDefeated: Int,
    val bestWave: Int,
    val isNewRecord: Boolean
)
