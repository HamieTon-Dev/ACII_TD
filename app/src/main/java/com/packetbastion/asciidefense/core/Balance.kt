package com.packetbastion.asciidefense.core

import kotlin.math.pow

/**
 * Every number that shapes how Packet Bastion *feels* lives here.
 *
 * Nothing in this file depends on Android or Compose, so it can be tweaked and
 * unit-tested freely. See BALANCE.md for a prose explanation of each group.
 */
object Balance {

    // ---------------------------------------------------------------- server

    /** Starting integrity of CORE-SERVER. */
    const val SERVER_MAX_HP = 100

    /** Crypto the player starts a run with. */
    const val STARTING_CRYPTO = 90

    // ------------------------------------------------------------ enemy scale

    /**
     * Health multiplier applied to an enemy archetype's base health on [wave].
     *
     * Hybrid linear + gentle-exponential curve: wave 1 is ~1.09x, wave 10 is
     * ~1.98x, wave 25 is ~3.55x, wave 50 is ~6.7x. Deliberately shallow early
     * so that wave 10 is not a wall for a first-time player.
     */
    const val HEALTH_LINEAR = 0.08
    const val HEALTH_POWER_COEFF = 0.010
    const val HEALTH_POWER_EXP = 1.25

    fun healthMultiplier(wave: Int): Double =
        1.0 + wave * HEALTH_LINEAR + wave.toDouble().pow(HEALTH_POWER_EXP) * HEALTH_POWER_COEFF

    /** Speed creeps up very slowly and hard-caps so late waves stay readable. */
    const val SPEED_PER_WAVE = 0.006
    const val SPEED_MAX_MULTIPLIER = 1.65

    fun speedMultiplier(wave: Int): Double =
        (1.0 + wave * SPEED_PER_WAVE).coerceAtMost(SPEED_MAX_MULTIPLIER)

    /** Armour (flat damage reduction) gained from wave number alone. */
    fun waveArmorBonus(wave: Int): Double = (wave / 12) * 0.5

    // ----------------------------------------------------------- wave shaping

    /**
     * How many enemies a normal wave contains. Grows sub-linearly on purpose:
     * we would rather send *stronger* packets than flood the renderer.
     */
    fun waveEnemyCount(wave: Int): Int =
        (6 + wave * 1.15 + wave.toDouble().pow(1.12) * 0.30).toInt().coerceAtMost(MAX_WAVE_ENEMIES)

    const val MAX_WAVE_ENEMIES = 46

    /** Seconds between spawns; tightens with wave number but never below the floor. */
    fun spawnInterval(wave: Int): Float =
        (1.05f - wave * 0.012f).coerceAtLeast(0.34f)

    /** Probability that a given spawn is upgraded to an elite variant. */
    fun eliteChance(wave: Int): Float =
        ((wave - 4) * 0.014f).coerceIn(0f, 0.32f)

    /** Every Nth wave is a boss wave. */
    const val BOSS_WAVE_INTERVAL = 5

    fun isBossWave(wave: Int): Boolean = wave > 0 && wave % BOSS_WAVE_INTERVAL == 0

    /** Which boss cycle a wave belongs to: wave 5 -> 1, wave 10 -> 2, ... */
    fun bossCycle(wave: Int): Int = wave / BOSS_WAVE_INTERVAL

    /** Bosses scale on their own, steeper curve than trash packets. */
    fun bossHealthMultiplier(wave: Int): Double {
        val cycle = bossCycle(wave)
        return healthMultiplier(wave) * (1.0 + (cycle - 1) * 0.30)
    }

    /** Seconds the "INTRUSION ALERT" banner is shown before a boss wave starts. */
    const val BOSS_WARNING_SECONDS = 2.6f

    // --------------------------------------------------------------- economy

    /** Crypto granted for a kill, before elite/boss multipliers. */
    const val REWARD_NORMAL = 1
    const val REWARD_ELITE_MIN = 2
    const val REWARD_ELITE_MAX = 3
    const val REWARD_BOSS_MIN = 5

    /**
     * Rewards inflate *much* more slowly than enemy health, which is what keeps
     * upgrades meaningful instead of trivially affordable at wave 40.
     */
    fun rewardMultiplier(wave: Int): Double = 1.0 + wave * 0.030

    /** Flat crypto bonus for surviving a wave. */
    fun waveClearBonus(wave: Int): Int = 8 + wave * 2

    /** Fraction of total invested crypto refunded when selling an agent. */
    const val SELL_REFUND_RATIO = 0.70f

    // ---------------------------------------------------------------- agents

    /** Standard maximum agent level. */
    const val MAX_AGENT_LEVEL = 10

    /** Per-level stat growth, applied multiplicatively from level 1. */
    const val UPGRADE_DAMAGE_GROWTH = 0.26f
    const val UPGRADE_RATE_GROWTH = 0.055f
    const val UPGRADE_RANGE_GROWTH = 0.035f

    /**
     * Cost to go from [level] to [level] + 1 for an agent whose deployment cost
     * is [baseCost]. Rises steadily so that spreading levels across several
     * agents stays competitive with maxing one.
     */
    fun upgradeCost(baseCost: Int, level: Int): Int =
        (baseCost * (0.55f + 0.30f * level) * (1f + level * 0.08f)).toInt().coerceAtLeast(5)

    /** Total crypto sunk into an agent at [level] (deployment + all upgrades). */
    fun investedCrypto(baseCost: Int, level: Int): Int {
        var total = baseCost
        for (l in 1 until level) total += upgradeCost(baseCost, l)
        return total
    }

    fun sellValue(baseCost: Int, level: Int): Int =
        (investedCrypto(baseCost, level) * SELL_REFUND_RATIO).toInt().coerceAtLeast(1)

    // ------------------------------------------------------------- combat fx

    /** World units per second travelled by a standard projectile. */
    const val PROJECTILE_SPEED = 980f

    /** Seconds a floating damage number stays on screen. */
    const val DAMAGE_NUMBER_LIFETIME = 0.75f

    /** Seconds a packet-destruction glyph sequence takes to play out. */
    const val DEATH_EFFECT_LIFETIME = 0.45f
    const val BOSS_DEATH_EFFECT_LIFETIME = 1.1f

    // ------------------------------------------------------------ pacing / UX

    /** Seconds of preparation time offered between waves when auto-start is on. */
    const val AUTO_START_DELAY = 4.0f

    /** Available game-speed multipliers. */
    val GAME_SPEEDS = floatArrayOf(1f, 2f, 3f)

    /** Hard cap on simulation step to keep physics stable after a stall. */
    const val MAX_FRAME_DELTA = 0.05f
}
