package com.cyopstd.game.engine

import com.cyopstd.game.core.Balance
import com.cyopstd.game.core.GameMode
import com.cyopstd.game.core.WorldGeometry
import com.cyopstd.game.model.BossModifier
import com.cyopstd.game.model.EnemyType
import kotlin.math.max
import kotlin.random.Random

/** One scheduled spawn inside a wave plan. */
data class SpawnOrder(
    /** Seconds from wave start. */
    val time: Float,
    val type: EnemyType,
    val lane: Int,
    val elite: Boolean,
    val boss: Boolean,
    val bossModifiers: List<BossModifier> = emptyList()
)

data class WavePlan(
    val wave: Int,
    val isBossWave: Boolean,
    val orders: List<SpawnOrder>,
    val bossModifiers: List<BossModifier>
) {
    val enemyCount: Int get() = orders.size
    val duration: Float get() = orders.lastOrNull()?.time ?: 0f
}

/**
 * Builds a wave plan procedurally from the wave number. There is no hand-written
 * wave table anywhere in the game — waves 1 through 4 are shaped by explicit
 * early-game rules so the opening is a good teacher, and everything from wave 6
 * onward is composed from a weighted archetype pool that widens as the run goes.
 */
class WaveGenerator(private val random: Random = Random.Default) {

    /**
     * The mode the plan is built for. Scales the gap between spawns, so a
     * harder mode presses harder rather than merely hitting harder.
     */
    var mode: GameMode = GameMode.STANDARD

    fun generate(wave: Int): WavePlan {
        if (Balance.isBossWave(wave)) return generateBossWave(wave)
        return generateStandardWave(wave)
    }

    // ------------------------------------------------------------- standard

    private fun generateStandardWave(wave: Int): WavePlan {
        val count = Balance.waveEnemyCount(wave)
        val interval = Balance.spawnInterval(wave) * mode.spawnIntervalScale
        val eliteChance = Balance.eliteChance(wave)
        val pool = archetypePool(wave)

        val orders = ArrayList<SpawnOrder>(count)
        var time = 0.6f
        var index = 0

        while (index < count) {
            val type = pickWeighted(pool)

            // Swarm archetypes arrive as a tight burst in a single lane, which
            // reads very differently from a steady trickle and is the whole
            // point of DDoS and BOT packets.
            val burst = if (type.isSwarm()) swarmSize(wave) else 1
            val lane = random.nextInt(WorldGeometry.LANE_COUNT)

            val burstDelay = Balance.swarmBurstDelay(type.baseSpeed)
            for (b in 0 until burst) {
                if (index >= count) break
                val elite = !type.isBoss && random.nextFloat() < eliteChance
                orders += SpawnOrder(
                    time = time + b * burstDelay,
                    type = type,
                    lane = if (burst > 1) lane else random.nextInt(WorldGeometry.LANE_COUNT),
                    elite = elite,
                    boss = false
                )
                index++
            }

            time += if (burst > 1) interval * 1.35f else interval
        }

        return WavePlan(wave, isBossWave = false, orders = orders, bossModifiers = emptyList())
    }

    private fun EnemyType.isSwarm(): Boolean = this == EnemyType.BOT || this == EnemyType.DDOS

    private fun swarmSize(wave: Int): Int = when {
        wave < 6 -> 3
        wave < 12 -> 4
        wave < 20 -> 5
        wave < 32 -> 6
        else -> 7
    }

    // ----------------------------------------------------------------- boss

    private fun generateBossWave(wave: Int): WavePlan {
        val cycle = Balance.bossCycle(wave)
        val modifiers = rollModifiers(cycle)
        val orders = ArrayList<SpawnOrder>()

        // How many bosses: one for the first few cycles, then pairs and trios so
        // late boss waves get harder without becoming an enemy-count problem.
        val bossCount = when {
            cycle < 4 -> 1
            cycle < 9 -> 2
            else -> 3
        }

        // Escort wave first: the boss walks in behind its own traffic.
        val escortCount = (4 + cycle * 2).coerceAtMost(22)
        val escortPool = archetypePool(wave)
        var time = 0.5f
        repeat(escortCount) {
            orders += SpawnOrder(
                time = time,
                type = pickWeighted(escortPool),
                lane = random.nextInt(WorldGeometry.LANE_COUNT),
                elite = random.nextFloat() < Balance.eliteChance(wave),
                boss = false
            )
            time += max(0.30f, Balance.spawnInterval(wave) * mode.spawnIntervalScale * 0.85f)
        }

        // Boss routes rotate by cycle, so consecutive boss waves never arrive
        // down the same route and a board built for one side is not a permanent
        // answer. Extra bosses in a cycle take the remaining routes.
        val firstLane = (cycle - 1).mod(WorldGeometry.LANE_COUNT)
        for (i in 0 until bossCount) {
            orders += SpawnOrder(
                time = 1.2f + i * 2.4f,
                type = EnemyType.BOSS,
                lane = (firstLane + i).mod(WorldGeometry.LANE_COUNT),
                elite = false,
                boss = true,
                bossModifiers = modifiers
            )
        }

        orders.sortBy { it.time }
        return WavePlan(wave, isBossWave = true, orders = orders, bossModifiers = modifiers)
    }

    private fun rollModifiers(cycle: Int): List<BossModifier> {
        val pool = BossModifier.poolForCycle(cycle)
        val count = BossModifier.countForCycle(cycle).coerceAtMost(pool.size)
        if (count <= 0 || pool.isEmpty()) return emptyList()
        return pool.shuffled(random).take(count)
    }

    // ------------------------------------------------------- archetype pool

    /**
     * Weighted archetype availability. The early waves are tightly scripted:
     * wave 1-2 is plain packets, wave 3 introduces fast traffic, wave 4 mixes
     * types, and from wave 6 the pool opens up gradually.
     */
    private fun archetypePool(wave: Int): List<Pair<EnemyType, Int>> = when {
        wave <= 2 -> listOf(EnemyType.SQL_INJECTION to 100)

        wave == 3 -> listOf(
            EnemyType.SQL_INJECTION to 62,
            EnemyType.BOT to 38
        )

        wave == 4 -> listOf(
            EnemyType.SQL_INJECTION to 48,
            EnemyType.BOT to 28,
            EnemyType.MALWARE to 24
        )

        wave <= 7 -> listOf(
            EnemyType.SQL_INJECTION to 38,
            EnemyType.BOT to 22,
            EnemyType.MALWARE to 26,
            EnemyType.EXPLOIT to 14
        )

        wave <= 10 -> listOf(
            EnemyType.SQL_INJECTION to 26,
            EnemyType.BOT to 18,
            EnemyType.MALWARE to 24,
            EnemyType.EXPLOIT to 16,
            EnemyType.TROJAN to 16
        )

        wave <= 14 -> listOf(
            EnemyType.SQL_INJECTION to 18,
            EnemyType.BOT to 14,
            EnemyType.MALWARE to 18,
            EnemyType.EXPLOIT to 16,
            EnemyType.TROJAN to 16,
            EnemyType.ENCRYPTED to 12,
            EnemyType.SQL_BLIND to 6
        )

        wave <= 20 -> listOf(
            EnemyType.SQL_INJECTION to 12,
            EnemyType.BOT to 12,
            EnemyType.MALWARE to 16,
            EnemyType.EXPLOIT to 14,
            EnemyType.TROJAN to 16,
            EnemyType.ENCRYPTED to 14,
            EnemyType.SQL_BLIND to 10,
            EnemyType.DDOS to 8
        )

        wave <= 30 -> listOf(
            EnemyType.SQL_INJECTION to 8,
            EnemyType.BOT to 10,
            EnemyType.MALWARE to 14,
            EnemyType.EXPLOIT to 14,
            EnemyType.TROJAN to 16,
            EnemyType.ENCRYPTED to 14,
            EnemyType.SQL_BLIND to 12,
            EnemyType.DDOS to 10,
            EnemyType.ZERO_DAY to 4
        )

        else -> listOf(
            EnemyType.SQL_INJECTION to 6,
            EnemyType.BOT to 8,
            EnemyType.MALWARE to 12,
            EnemyType.EXPLOIT to 14,
            EnemyType.TROJAN to 16,
            EnemyType.ENCRYPTED to 14,
            EnemyType.SQL_BLIND to 14,
            EnemyType.DDOS to 12,
            EnemyType.ZERO_DAY to 8
        )
    }

    private fun pickWeighted(pool: List<Pair<EnemyType, Int>>): EnemyType {
        var total = 0
        for ((_, weight) in pool) total += weight
        if (total <= 0) return EnemyType.SQL_INJECTION
        var roll = random.nextInt(total)
        for ((type, weight) in pool) {
            roll -= weight
            if (roll < 0) return type
        }
        return pool.last().first
    }
}
