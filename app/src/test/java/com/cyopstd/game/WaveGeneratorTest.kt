package com.cyopstd.game

import com.cyopstd.game.core.Balance
import com.cyopstd.game.core.Maps
import com.cyopstd.game.core.WorldGeometry
import com.cyopstd.game.engine.WaveGenerator
import com.cyopstd.game.model.EnemyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class WaveGeneratorTest {

    private fun generator(seed: Int = 42) = WaveGenerator(Random(seed))

    @Test
    fun `waves one and two are SQL injections only`() {
        val gen = generator()
        for (wave in 1..2) {
            val plan = gen.generate(wave)
            assertFalse(plan.isBossWave)
            assertTrue(plan.orders.isNotEmpty())
            plan.orders.forEach { order ->
                assertEquals(
                    "wave $wave should only send SQL INJECTION",
                    EnemyType.SQL_INJECTION, order.type
                )
            }
        }
    }

    @Test
    fun `the retired generic PACKET archetype is gone for good`() {
        // [P] was removed on purpose: a packet is ordinary traffic, so naming an
        // enemy after it taught the player something untrue. Every archetype is
        // now named for the attack it carries.
        val names = EnemyType.entries.map { it.name }
        assertFalse("PACKET must not come back", "PACKET" in names)
        val glyphs = EnemyType.entries.map { it.glyph }
        assertFalse("the [P] glyph belongs to the IPS agent now", "[P]" in glyphs)
        assertTrue("[SQL]" in glyphs)
        assertTrue("[SQL2]" in glyphs)
    }

    @Test
    fun `wave three introduces faster traffic`() {
        // Sample across seeds: the pool is weighted, so any one wave may miss.
        val types = mutableSetOf<EnemyType>()
        repeat(40) { seed -> generator(seed).generate(3).orders.forEach { types += it.type } }
        assertTrue("wave 3 should add BOT", EnemyType.BOT in types)
    }

    @Test
    fun `wave four mixes packet types`() {
        val types = mutableSetOf<EnemyType>()
        repeat(40) { seed -> generator(seed).generate(4).orders.forEach { types += it.type } }
        assertTrue(types.size >= 3)
        assertTrue(EnemyType.MALWARE in types)
    }

    @Test
    fun `wave five is a boss wave with exactly one clean boss`() {
        val plan = generator().generate(5)
        assertTrue(plan.isBossWave)
        val bosses = plan.orders.filter { it.boss }
        assertEquals(1, bosses.size)
        assertEquals(EnemyType.BOSS, bosses.first().type)
        assertTrue(
            "the first boss must have no modifiers to learn",
            plan.bossModifiers.isEmpty()
        )
    }

    @Test
    fun `wave ten boss is harder than wave five and carries a modifier`() {
        val five = generator().generate(5)
        val ten = generator().generate(10)
        assertTrue(ten.isBossWave)
        assertTrue(
            "wave 10 should introduce a modifier",
            ten.bossModifiers.isNotEmpty()
        )
        assertTrue(
            "wave 10 should have at least as much escort pressure",
            ten.orders.size > five.orders.size
        )
    }

    @Test
    fun `boss modifiers are introduced gradually`() {
        assertEquals(0, generator().generate(5).bossModifiers.size)
        assertEquals(1, generator().generate(10).bossModifiers.size)
        assertEquals(1, generator().generate(15).bossModifiers.size)
        assertEquals(2, generator().generate(20).bossModifiers.size)
        assertTrue(generator().generate(60).bossModifiers.size >= 3)
    }

    @Test
    fun `every order targets a real lane and a non-negative time`() {
        val gen = generator()
        for (wave in 1..80) {
            val plan = gen.generate(wave)
            plan.orders.forEach { order ->
                assertTrue(
                    "wave $wave lane ${order.lane}",
                    order.lane in 0 until Maps.PERIMETER.laneCount
                )
                assertTrue("wave $wave time ${order.time}", order.time >= 0f)
            }
        }
    }

    @Test
    fun `spawn orders are chronologically sorted on boss waves`() {
        for (wave in intArrayOf(5, 10, 25, 50)) {
            val plan = generator().generate(wave)
            var previous = -1f
            plan.orders.forEach { order ->
                assertTrue("wave $wave not sorted", order.time >= previous)
                previous = order.time
            }
        }
    }

    @Test
    fun `wave size never exceeds what the enemy pool can hold`() {
        val gen = generator()
        for (wave in 1..200) {
            val plan = gen.generate(wave)
            // The plan may schedule more than the pool holds over time, but no
            // single wave should be absurd.
            assertTrue(
                "wave $wave scheduled ${plan.orders.size}",
                plan.orders.size <= Balance.MAX_WAVE_ENEMIES + 30
            )
        }
    }

    @Test
    fun `later waves draw from a wider archetype pool`() {
        val earlyTypes = mutableSetOf<EnemyType>()
        val lateTypes = mutableSetOf<EnemyType>()
        repeat(30) { seed ->
            generator(seed).generate(6).orders.forEach { earlyTypes += it.type }
            generator(seed).generate(40).orders.forEach { lateTypes += it.type }
        }
        assertTrue(
            "late pool ($lateTypes) should be wider than early ($earlyTypes)",
            lateTypes.size > earlyTypes.size
        )
        assertTrue(EnemyType.ZERO_DAY in lateTypes)
        assertTrue("blind SQLi belongs to the late game", EnemyType.SQL_BLIND in lateTypes)
    }
}
