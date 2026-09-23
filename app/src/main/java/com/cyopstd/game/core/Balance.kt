package com.cyopstd.game.core

import kotlin.math.pow

/**
 * Every number that shapes how CyOps TD *feels* lives here.
 *
 * Nothing in this file depends on Android or Compose, so it can be tweaked and
 * unit-tested freely. See BALANCE.md for a prose explanation of each group.
 */
object Balance {

    // ---------------------------------------------------------------- server

    /** Starting integrity of CORE-SERVER. */
    const val SERVER_MAX_HP = 100

    /**
     * Crypto the player starts a run with. Enough for two FIREWALLs, or a
     * FIREWALL plus an IDS — a real opening rather than a single tower.
     */
    const val STARTING_CRYPTO = 120

    // ------------------------------------------------------------ enemy scale

    /**
     * Health multiplier applied to an enemy archetype's base health on [wave].
     *
     * Hybrid linear + gentle-exponential curve. Deliberately shallow: the point
     * of an endless mode is that you lose to composition and positioning, not
     * to a health wall at wave 9.
     */
    const val HEALTH_LINEAR = 0.06
    const val HEALTH_POWER_COEFF = 0.008
    const val HEALTH_POWER_EXP = 1.25

    fun healthMultiplier(wave: Int): Double =
        1.0 + wave * HEALTH_LINEAR + wave.toDouble().pow(HEALTH_POWER_EXP) * HEALTH_POWER_COEFF

    /** Speed creeps up very slowly and hard-caps so late waves stay readable. */
    const val SPEED_PER_WAVE = 0.005
    const val SPEED_MAX_MULTIPLIER = 1.55

    fun speedMultiplier(wave: Int): Double =
        (1.0 + wave * SPEED_PER_WAVE).coerceAtMost(SPEED_MAX_MULTIPLIER)

    /** Armour (flat damage reduction) gained from wave number alone. */
    fun waveArmorBonus(wave: Int): Double = (wave / 15) * 0.5

    // ----------------------------------------------------------- wave shaping

    /**
     * How many enemies a normal wave contains. Grows sub-linearly on purpose:
     * we would rather send *stronger* packets than flood the renderer.
     */
    fun waveEnemyCount(wave: Int): Int =
        (5 + wave * 0.95 + wave.toDouble().pow(1.10) * 0.25).toInt().coerceAtMost(MAX_WAVE_ENEMIES)

    const val MAX_WAVE_ENEMIES = 42

    /** Seconds between spawns; tightens with wave number but never below the floor. */
    fun spawnInterval(wave: Int): Float =
        (1.15f - wave * 0.010f).coerceAtLeast(0.38f)

    /**
     * World units between consecutive members of a swarm burst.
     *
     * Expressed as a distance rather than a delay because that is what it is
     * really about: a swarm should arrive as a visible column, and at 0.16 s
     * apart a BOT covers 14 units, less than a quarter of the width of its own
     * chip, so the whole burst drew on one spot. The delay is derived from this
     * and the archetype's speed, which keeps the spacing right whether the type
     * is quick or slow.
     */
    const val SWARM_BURST_SPACING = 46f

    /** Seconds between two members of a swarm burst travelling at [speed]. */
    fun swarmBurstDelay(speed: Float): Float =
        if (speed <= 1f) 0.5f else SWARM_BURST_SPACING / speed

    /** Probability that a given spawn is upgraded to an elite variant. */
    fun eliteChance(wave: Int): Float =
        ((wave - 6) * 0.012f).coerceIn(0f, 0.28f)

    /** Every Nth wave is a boss wave. */
    const val BOSS_WAVE_INTERVAL = 5

    fun isBossWave(wave: Int): Boolean = wave > 0 && wave % BOSS_WAVE_INTERVAL == 0

    /** Which boss cycle a wave belongs to: wave 5 -> 1, wave 10 -> 2, ... */
    fun bossCycle(wave: Int): Int = wave / BOSS_WAVE_INTERVAL

    /**
     * Bosses scale on their own, much steeper curve than ordinary traffic.
     *
     * The first boss is deliberately soft. A wave-5 boss that a starting board
     * cannot kill is not a difficulty curve, it is a wall — and combined with
     * the targeting bug that made bosses the last thing anything shot at, it
     * made wave 5 read as impossible. The steep per-cycle term is what keeps
     * wave 30, 50 and 100 bosses genuinely dangerous.
     */
    const val BOSS_CYCLE_SCALING = 0.45

    /**
     * The first boss is deliberately under-scaled ([BOSS_FIRST_CYCLE_SOFTENING]
     * below 1.0) rather than merely "not yet boosted".
     *
     * This number was measured, not guessed. Instrumenting a wave-5 fight showed
     * a five-agent board keeps the boss under fire for only about 11 seconds of
     * its 50-second journey — five towers cannot cover a 2,353-unit route. Boss
     * health had been tuned as though the whole route were defended, so the boss
     * arrived at the server with health to spare no matter what the player did.
     */
    const val BOSS_FIRST_CYCLE_SOFTENING = 0.65

    fun bossHealthMultiplier(wave: Int): Double {
        val cycle = bossCycle(wave)
        val cycleTerm = BOSS_FIRST_CYCLE_SOFTENING + (cycle - 1) * BOSS_CYCLE_SCALING
        return healthMultiplier(wave) * cycleTerm
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
     * Rewards inflate more slowly than enemy health, which is what keeps
     * upgrades meaningful instead of trivially affordable at wave 40.
     */
    fun rewardMultiplier(wave: Int): Double = 1.0 + wave * 0.035

    /** Flat crypto bonus for surviving an ordinary wave. */
    fun waveClearBonus(wave: Int): Int = 12 + wave * 3

    /**
     * Extra crypto paid on clearing a boss wave, on top of [waveClearBonus].
     *
     * Boss waves are where a run either stabilises or dies, so they are also
     * where the run gets the capital to rebuild. Starts at 20 and steps up by
     * [BOSS_CLEAR_BONUS_STEP] per boss cycle, then rides the same gentle wave
     * multiplier as every other payout.
     */
    const val BOSS_CLEAR_BONUS_BASE = 20
    const val BOSS_CLEAR_BONUS_STEP = 15

    fun bossClearBonus(wave: Int): Int {
        if (!isBossWave(wave)) return 0
        val cycle = bossCycle(wave).coerceAtLeast(1)
        val flat = BOSS_CLEAR_BONUS_BASE + (cycle - 1) * BOSS_CLEAR_BONUS_STEP
        return (flat * rewardMultiplier(wave)).toInt()
    }

    /** Fraction of total invested crypto refunded when selling an agent. */
    const val SELL_REFUND_RATIO = 0.70f

    // ---------------------------------------------------------------- agents

    /**
     * Agents upgrade from level 1 to level 100.
     *
     * A hundred levels only works if each one is cheap enough to be a casual
     * decision, so the per-level stat step is small and the cost curve starts
     * very low. The reward for depth is that a long run can push a single agent
     * far past what a short one can.
     */
    const val MAX_AGENT_LEVEL = 100

    /** Per-level stat growth, as a fraction of the agent's base stat. */
    const val UPGRADE_DAMAGE_GROWTH = 0.20f
    const val UPGRADE_RATE_GROWTH = 0.018f
    const val UPGRADE_RANGE_GROWTH = 0.007f

    /**
     * Range is the strongest stat in a tower defence game — unbounded growth
     * would make node placement irrelevant, so it alone is capped.
     */
    const val UPGRADE_RANGE_CAP = 1.75f

    /**
     * Cost to go from [level] to [level] + 1 for an agent whose deployment cost
     * is [baseCost]. Linear in level: cheap and frequent early, a real
     * commitment late.
     */
    fun upgradeCost(baseCost: Int, level: Int): Int =
        (baseCost * (0.30f + 0.14f * level)).toInt().coerceAtLeast(3)

    /** Total crypto sunk into an agent at [level] (deployment + all upgrades). */
    fun investedCrypto(baseCost: Int, level: Int): Int {
        var total = baseCost
        for (l in 1 until level.coerceAtMost(MAX_AGENT_LEVEL)) {
            total += upgradeCost(baseCost, l)
        }
        return total
    }

    fun sellValue(baseCost: Int, level: Int): Int =
        (investedCrypto(baseCost, level) * SELL_REFUND_RATIO).toInt().coerceAtLeast(1)

    // ------------------------------------------------- budget and firmware

    /**
     * € BUDGET is the meta-currency. It is awarded at every tenth wave and,
     * unlike crypto, it survives the run that earned it.
     *
     * The award is quadratic in the milestone index, so reaching wave 50 once
     * is worth far more than reaching wave 10 five times — depth is the thing
     * being rewarded.
     */
    const val BUDGET_MILESTONE_INTERVAL = 10
    const val BUDGET_BASE = 5

    fun isBudgetMilestone(wave: Int): Boolean =
        wave > 0 && wave % BUDGET_MILESTONE_INTERVAL == 0

    fun budgetAward(wave: Int): Int {
        if (!isBudgetMilestone(wave)) return 0
        val milestone = wave / BUDGET_MILESTONE_INTERVAL
        return BUDGET_BASE * milestone * milestone
    }

    /**
     * CORE FIRMWARE is the permanent damage upgrade bought with € BUDGET. It
     * applies to every agent in every future match, for good.
     *
     * The cap is nominally 10,000 levels, which at the cost curve below is on
     * the order of fifty million € — effectively indefinite, which is the
     * point.
     */
    const val MAX_FIRMWARE_LEVEL = 10_000
    const val FIRMWARE_DAMAGE_PER_LEVEL = 0.005f

    /** Damage multiplier applied to every agent from persistent firmware. */
    fun firmwareDamageMultiplier(level: Int): Float =
        1f + level.coerceIn(0, MAX_FIRMWARE_LEVEL) * FIRMWARE_DAMAGE_PER_LEVEL

    /** € cost to go from firmware [level] to [level] + 1. */
    fun firmwareCost(level: Int): Int = 3 + level.coerceAtLeast(0)

    /** Total € needed to climb from [fromLevel] by [steps] levels. */
    fun firmwareCostFor(fromLevel: Int, steps: Int): Long {
        var total = 0L
        for (l in fromLevel until (fromLevel + steps).coerceAtMost(MAX_FIRMWARE_LEVEL)) {
            total += firmwareCost(l)
        }
        return total
    }

    /** How many firmware levels [budget] can buy starting from [fromLevel]. */
    fun firmwareLevelsAffordable(fromLevel: Int, budget: Long): Int {
        var spent = 0L
        var levels = 0
        var level = fromLevel
        while (level < MAX_FIRMWARE_LEVEL) {
            val cost = firmwareCost(level)
            if (spent + cost > budget) break
            spent += cost
            levels++
            level++
        }
        return levels
    }

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

    /**
     * Seconds the between-waves banner stays up before hiding itself. It sits
     * over the battlefield, so it must not outstay its welcome — the same
     * information lives permanently in the control bar.
     */
    const val PREP_BANNER_SECONDS = 3.5f

    /** Available game-speed multipliers. */
    val GAME_SPEEDS = floatArrayOf(1f, 2f, 3f)

    /** Hard cap on simulation step to keep physics stable after a stall. */
    const val MAX_FRAME_DELTA = 0.05f
}
