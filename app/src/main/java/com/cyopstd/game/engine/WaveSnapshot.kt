package com.cyopstd.game.engine

import kotlinx.serialization.Serializable

/**
 * A wave caught mid-fight, for the save (owner, 2026-10-02).
 *
 * Saves used to keep only the wave number, the crypto and the board, and
 * CONTINUE replayed the wave from its start. Crypto earned during the wave
 * stayed, so leaving to the menu and continuing, over and over, paid for the
 * same wave again and again: a game-breaking money exploit. Now the wave is
 * saved as it stands — what has still to spawn, what is waiting at the gate,
 * and every enemy on the board with its position, health and boss state — and
 * CONTINUE carries on from exactly there.
 *
 * Names rather than ordinals throughout, so a reordered enum cannot turn one
 * enemy into another in an old save.
 */
@Serializable
data class WaveSnapshot(
    val isBossWave: Boolean,
    /** "IN_WAVE" or "BOSS_WARNING". */
    val phase: String,
    val bossWarningRemaining: Float = 0f,
    /** Seconds into the wave; the pending spawns' times are against this clock. */
    val waveTimer: Float,
    /** Spawns not yet due, in order. */
    val pending: List<SpawnSnapshot>,
    /** Spawns that were due but waiting at the gate for room. */
    val held: List<SpawnSnapshot>,
    /** Packets of this wave not yet spawned or finished. */
    val enemiesRemaining: Int,
    val bossModifiers: List<String> = emptyList(),
    val bossVariant: String = "breach",
    val event: String? = null,
    val enemies: List<EnemySnapshot> = emptyList()
)

@Serializable
data class SpawnSnapshot(
    val time: Float,
    val type: String,
    val lane: Int,
    val elite: Boolean,
    val boss: Boolean,
    val bossModifiers: List<String> = emptyList(),
    val bossVariant: String = "breach",
    val anonymous: Boolean = false
)

@Serializable
data class EnemySnapshot(
    val type: String,
    val lane: Int,
    val progress: Float,
    val laneOffset: Float,
    val health: Float,
    val maxHealth: Float,
    val baseSpeed: Float,
    val armor: Float,
    val serverDamage: Int,
    val reward: Int,
    val elite: Boolean,
    val boss: Boolean,
    val variant: String = "breach",
    /** Bit set over `BossModifier` names, stored as the names. */
    val modifiers: List<String> = emptyList(),
    val revived: Boolean = false,
    val split: Boolean = false,
    val gradientHeat: Float = 0f,
    val ransomTimer: Float = 0f,
    val variantTimer: Float = 0f,
    val hidden: Boolean = false,
    val wormGeneration: Int = 0,
    val decoy: Boolean = false,
    /** Index in the snapshot's enemy list of the SPOOFER this decoy belongs to, or -1. */
    val decoyOwner: Int = -1,
    /** Replicated by a boss rather than spawned from the plan (not counted by the wave). */
    val escort: Boolean = false,
    val slowRemaining: Float = 0f,
    val slowFactor: Float = 1f,
    val burstTimer: Float = 0f,
    val burstActive: Float = 0f,
    val replicateTimer: Float = 0f,
    val disruptTimer: Float = 0f,
    val variantJamTimer: Float = 0f,
    val phase: Float = 0f
)
