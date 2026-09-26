package com.cyopstd.game.engine

import com.cyopstd.game.core.Balance
import com.cyopstd.game.model.Enemy
import com.cyopstd.game.model.RewardTier
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Every crypto movement in the game funnels through here, which makes the
 * economy trivial to audit and to re-balance: there is exactly one formula for
 * kill rewards and exactly one place that spends.
 */
class EconomySystem(private val engine: GameEngine) {

    /**
     * Pays out crypto and returns what was actually credited.
     *
     * Earnings -- kills and wave bonuses -- are raised by firmware's crypto
     * multiplier. Refunds ([countAsEarned] false) are not: selling an agent
     * gives back part of what it cost, and a multiplier there would turn
     * buying and selling into a money machine.
     */
    fun award(amount: Int, countAsEarned: Boolean = true): Int {
        if (amount <= 0) return 0
        val credited = if (countAsEarned) {
            (amount * engine.firmwareCryptoMultiplier).roundToInt().coerceAtLeast(amount)
        } else {
            amount
        }
        engine.addCrypto(credited, countAsEarned)
        return credited
    }

    fun spend(amount: Int) {
        if (amount <= 0) return
        engine.removeCrypto(amount)
    }

    companion object {
        /**
         * Reward for destroying [enemy] on [wave].
         *
         * Base tier -> gentle wave multiplier. Rewards grow far slower than enemy
         * health does, which is what stops the late game from turning into an
         * unlimited-money sandbox.
         */
        fun rewardFor(enemy: Enemy, wave: Int, random: Random): Int {
            val base = when (enemy.type.rewardTier) {
                RewardTier.NORMAL ->
                    if (enemy.isElite) {
                        random.nextInt(Balance.REWARD_ELITE_MIN, Balance.REWARD_ELITE_MAX + 1)
                    } else {
                        Balance.REWARD_NORMAL
                    }

                RewardTier.ELITE ->
                    random.nextInt(Balance.REWARD_ELITE_MIN, Balance.REWARD_ELITE_MAX + 1) + 1

                RewardTier.BOSS ->
                    Balance.REWARD_BOSS_MIN + Balance.bossCycle(wave) * 3
            }
            val scaled = base * Balance.rewardMultiplier(wave)
            return scaled.roundToInt().coerceAtLeast(1)
        }
    }
}
