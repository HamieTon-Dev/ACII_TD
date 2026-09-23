package com.cyopstd.game

import com.cyopstd.game.core.Balance
import com.cyopstd.game.core.WorldGeometry
import com.cyopstd.game.engine.CombatSystem
import com.cyopstd.game.engine.GameEngine
import com.cyopstd.game.model.AgentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.random.Random

/**
 * The TARPIT is the cheap support unit: 20 crypto, 200 range, almost no
 * damage, and everything it hits crawls.
 *
 * A cheap unit is the easiest thing in a tower defence to get wrong. These
 * pin the two failure modes. It must actually do its job — an earlier build
 * of the SANDBOX slow shipped wired to nothing — and it must not be the most
 * efficient thing on the board, which at its first damage figure it was: four
 * of them cleared wave 12 better than two FIREWALLs for the same money.
 */
class TarpitTest {

    private fun armed(wave: Int, seed: Int, crypto: Int = 100_000): GameEngine {
        val engine = GameEngine(random = Random(seed))
        engine.startNewRun()
        engine.restore(
            wave = wave - 1, serverHp = 900, crypto = crypto, placements = emptyList(),
            attacksBlocked = 0, cryptoEarned = 0, bossesDefeated = 0,
            serverDamageTaken = 0, agentsDeployed = 0, agentUpgrades = 0
        )
        return engine
    }

    /**
     * Plays one wave and returns the integrity lost.
     *
     * Damage agents take the best-covered spots; support is placed next to the
     * first of them, which is how anyone would actually play it — a slow is
     * worth nothing where nothing shoots. The integrity pool is deliberately
     * huge so a board that is losing keeps reporting *how badly*, instead of
     * dying early and making every failing board look identical.
     */
    private fun leak(
        wave: Int,
        damage: List<AgentType>,
        support: List<AgentType> = emptyList(),
        upgrades: Int = 0,
        seed: Int
    ): Int {
        val engine = armed(wave, seed)
        val nodes = WorldGeometry.nodesByCoverage
        val used = ArrayList<Int>()
        var next = 0
        for (type in damage) {
            while (next < nodes.size) {
                engine.placeAgent(type, nodes[next].id)
                if (engine.agentAt(nodes[next].id) != null) { used += nodes[next].id; next++; break }
                next++
            }
        }
        val anchor = WorldGeometry.node(used.first())!!
        val neighbours = nodes.filter { it.id !in used }
            .sortedBy { hypot(it.x - anchor.x, it.y - anchor.y) }
        var n = 0
        for (type in support) {
            while (n < neighbours.size) {
                engine.placeAgent(type, neighbours[n].id)
                if (engine.agentAt(neighbours[n].id) != null) { n++; break }
                n++
            }
        }
        assertEquals(
            "the board was not fully placed",
            damage.size + support.size, engine.activeAgentCount()
        )
        repeat(upgrades) { i -> engine.upgradeAgent(used[i % used.size], 1) }

        val before = engine.serverHp
        engine.startNextWave()
        var elapsed = 0f
        while (elapsed < 400f && engine.enemiesRemaining > 0) {
            engine.update(1f / 30f, 1f)
            elapsed += 1f / 30f
        }
        assertTrue("wave $wave never finished", engine.enemiesRemaining == 0)
        return before - engine.serverHp
    }

    /**
     * Averaged over seeds and waves. A single wave is mostly dice — an earlier
     * version of these tests read one wave and drew the opposite conclusion.
     * The engine's generator is injected, so every figure here is reproducible.
     */
    private fun averageLeak(
        damage: List<AgentType>,
        support: List<AgentType> = emptyList(),
        upgrades: Int = 0
    ): Double {
        val samples = ArrayList<Int>()
        for (seed in 1..4) for (wave in intArrayOf(8, 12, 16)) {
            samples += leak(wave, damage, support, upgrades, seed)
        }
        return samples.average()
    }

    @Test
    fun `a tarpit slows everything inside its radius, not just what it shoots`() {
        // It is a field, not an on-hit effect. Applied on hit it could hold
        // about two threats at a time against a wave of twenty, which measured
        // as 0.6 hp of integrity saved for 20 crypto -- worse value than an
        // upgrade, i.e. a unit nobody would ever buy.
        val engine = armed(wave = 12, seed = 7)
        val node = WorldGeometry.nodesByCoverage.first()
        engine.placeAgent(AgentType.TARPIT, node.id)
        engine.startNextWave()

        var everSlowed = 0
        var mostAtOnce = 0
        var observedFactor = 1f
        var inRangeButUnslowed = 0
        repeat(4000) {
            engine.update(1f / 60f, 1f)
            var slowedNow = 0
            for (enemy in engine.enemies.items) {
                if (!enemy.active) continue
                val inRange = hypot(enemy.x - node.x, enemy.y - node.y) <= AgentType.TARPIT.baseRange
                if (enemy.slowRemaining > 0f) {
                    everSlowed++
                    slowedNow++
                    observedFactor = enemy.slowFactor
                } else if (inRange) {
                    inRangeButUnslowed++
                }
            }
            if (slowedNow > mostAtOnce) mostAtOnce = slowedNow
        }

        assertTrue("nothing was ever slowed", everSlowed > 100)
        assertEquals("a level 1 tarpit should slow to 0.72x", 0.72f, observedFactor, 0.001f)
        assertEquals(
            "a threat stood inside the field without being slowed",
            0, inRangeButUnslowed
        )
        assertTrue(
            "only $mostAtOnce threats were ever slowed at once, which is what " +
                "an on-hit slow would manage",
            mostAtOnce >= 3
        )
    }

    @Test
    fun `stacking tarpits buys area, never a deeper slow`() {
        // This is what keeps a 20-crypto unit from being spammed into a lock.
        val engine = armed(wave = 12, seed = 3)
        val nodes = WorldGeometry.nodesByCoverage
        var placed = 0
        var i = 0
        while (placed < 4 && i < nodes.size) {
            engine.placeAgent(AgentType.TARPIT, nodes[i].id)
            if (engine.agentAt(nodes[i].id) != null) placed++
            i++
        }
        assertEquals(4, engine.activeAgentCount())
        engine.startNextWave()

        var deepest = 1f
        repeat(3000) {
            engine.update(1f / 60f, 1f)
            for (enemy in engine.enemies.items) {
                if (enemy.active && enemy.slowRemaining > 0f && enemy.slowFactor < deepest) {
                    deepest = enemy.slowFactor
                }
            }
        }
        assertEquals("four tarpits slowed deeper than one", 0.72f, deepest, 0.001f)
    }

    @Test
    fun `the slow deepens with levels but never becomes a stun`() {
        // Slows do not stack -- only the strongest applies -- so the floor is
        // what stops a wall of cheap tarpits becoming a lock rather than a
        // delay.
        val engine = armed(wave = 8, seed = 5, crypto = 1_000_000)
        val node = WorldGeometry.nodesByCoverage.first().id
        engine.placeAgent(AgentType.TARPIT, node)
        engine.upgradeAgent(node, 60)
        engine.startNextWave()

        var deepest = 1f
        repeat(3000) {
            engine.update(1f / 60f, 1f)
            for (enemy in engine.enemies.items) {
                if (enemy.active && enemy.slowRemaining > 0f && enemy.slowFactor < deepest) {
                    deepest = enemy.slowFactor
                }
            }
        }
        assertTrue("a fully levelled tarpit only reached ${deepest}x", deepest <= 0.56f)
        assertTrue("the slow became a stun at ${deepest}x", deepest >= 0.55f)
    }

    @Test
    fun `a board of nothing but tarpits is not a strategy`() {
        // The cheapest unit must not also be the most efficient one. Four
        // tarpits and two FIREWALLs both cost 80.
        val tarpits = averageLeak(List(4) { AgentType.TARPIT })
        val firewalls = averageLeak(List(2) { AgentType.FIREWALL })
        assertTrue(
            "four tarpits leak $tarpits against two firewalls' $firewalls, " +
                "so the cheap unit is also the strong one",
            tarpits > firewalls
        )
    }

    @Test
    fun `but a tarpit beside damage beats spending the same on upgrades`() {
        // It has to be worth the 20 crypto against the obvious alternative,
        // which is putting that crypto into a tower you already own.
        val bare = averageLeak(List(2) { AgentType.FIREWALL })
        val upgraded = averageLeak(List(2) { AgentType.FIREWALL }, upgrades = 1)
        val supported = averageLeak(List(2) { AgentType.FIREWALL }, listOf(AgentType.TARPIT))

        assertTrue("a tarpit made no difference ($supported vs $bare)", supported < bare)
        assertTrue(
            "20 crypto of tarpit leaks $supported where 17 of upgrade leaks " +
                "$upgraded, so the tarpit is the worse buy",
            supported < upgraded
        )
    }

    @Test
    fun `the tarpit does not put the sandbox out of a job`() {
        // The SANDBOX costs four times as much and is the slow specialist. A
        // cheap unit that outslowed it would simply delete it from the game.
        for (level in 1..100) {
            val tarpit = CombatSystem.tarpitSlowFactor(level)
            val sandbox = (0.60f - (level - 1) * 0.02f).coerceAtLeast(0.42f)
            assertTrue(
                "at level $level the tarpit slows to $tarpit and the sandbox " +
                    "to $sandbox",
                tarpit > sandbox
            )
        }
    }

    @Test
    fun `the tarpit is a cheap starter with real reach`() {
        assertEquals(20, AgentType.TARPIT.cost)
        assertEquals(200f, AgentType.TARPIT.baseRange, 0.01f)
        assertEquals("it should be available from the first wave", 0, AgentType.TARPIT.unlockWave)
        assertTrue(
            "it must be the cheapest thing on the board",
            AgentType.entries.all { it == AgentType.TARPIT || it.cost > AgentType.TARPIT.cost }
        )
    }

    @Test
    fun `threats arrive far enough apart to be read one at a time`() {
        // The board used to put a threat down every third of a second deep in
        // a run, which is faster than one clears its own chip width.
        for (wave in 1..120) {
            val interval = Balance.spawnInterval(wave)
            assertTrue("wave $wave spawns every ${interval}s", interval in 1.00f..1.50f)
        }
        assertTrue(
            "the interval should still tighten as the run goes on",
            Balance.spawnInterval(1) > Balance.spawnInterval(60)
        )
    }
}
