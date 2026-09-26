package com.cyopstd.game.engine

import com.cyopstd.game.core.Balance
import com.cyopstd.game.core.GameMode
import com.cyopstd.game.core.Maps
import com.cyopstd.game.model.BossModifier
import com.cyopstd.game.model.BossVariant
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
    val bossModifiers: List<BossModifier> = emptyList(),
    val bossVariant: BossVariant = BossVariant.BREACH
)

data class WavePlan(
    val wave: Int,
    val isBossWave: Boolean,
    val orders: List<SpawnOrder>,
    val bossModifiers: List<BossModifier>,
    val bossVariant: BossVariant = BossVariant.BREACH
) {
    val enemyCount: Int get() = orders.size

    /** Every boss type in this wave, in the order they arrive, without repeats. */
    val bossVariants: List<BossVariant>
        get() = orders.filter { it.boss }.map { it.bossVariant }.distinct()
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

    /**
     * How many routes the current level has.
     *
     * Set by the engine when the map is chosen. A wave plan that spreads
     * across two routes on a three-route map leaves one of them permanently
     * empty, which is not a difficulty setting, it is a bug.
     */
    var laneCount: Int = Maps.PERIMETER.laneCount

    /**
     * Which level the plan is for, by [com.cyopstd.game.core.GameMap.id].
     *
     * Set by the engine alongside [laneCount]. Boss identities are partly
     * per-map now — the gauntlet fields four of its own — and a generator that
     * does not know where it is would either never roll them or roll them on
     * the wrong board.
     */
    var mapId: String = Maps.PERIMETER.id

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
            val lane = random.nextInt(laneCount)

            val burstDelay = Balance.swarmBurstDelay(type.baseSpeed)
            for (b in 0 until burst) {
                if (index >= count) break
                val elite = !type.isBoss && random.nextFloat() < eliteChance
                orders += SpawnOrder(
                    time = time + b * burstDelay,
                    type = type,
                    lane = if (burst > 1) lane else random.nextInt(laneCount),
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
                lane = random.nextInt(laneCount),
                elite = random.nextFloat() < Balance.eliteChance(wave),
                boss = false
            )
            time += max(0.30f, Balance.spawnInterval(wave) * mode.spawnIntervalScale * 0.85f)
        }

        // Boss routes rotate by cycle, so consecutive boss waves never arrive
        // down the same route and a board built for one side is not a permanent
        // answer. Extra bosses in a cycle take the remaining routes.
        // Which opponents these are. A wave with more than one boss now
        // fields different types where the pool allows (owner, 2026-09-26:
        // "make sure different types of bosses spawn on a wave"); it used to
        // roll one type for the whole wave. Only repeats once every type
        // available on this map and cycle is already in the wave.
        val variants = rollVariants(cycle, bossCount)
        val variant = variants.first()

        val firstLane = (cycle - 1).mod(laneCount)
        for (i in 0 until bossCount) {
            orders += SpawnOrder(
                time = 1.2f + i * 2.4f,
                type = EnemyType.BOSS,
                lane = (firstLane + i).mod(laneCount),
                elite = false,
                boss = true,
                bossModifiers = modifiers,
                bossVariant = variants[i]
            )
        }

        orders.sortBy { it.time }
        return WavePlan(
            wave,
            isBossWave = true,
            orders = orders,
            bossModifiers = modifiers,
            bossVariant = variant
        )
    }

    private fun rollVariants(cycle: Int, count: Int): List<BossVariant> {
        val pool = BossVariant.poolFor(cycle, mapId)
        if (pool.isEmpty()) return List(count) { BossVariant.BREACH }
        val shuffled = pool.shuffled(random)
        return List(count) { shuffled[it % shuffled.size] }
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
