package com.cyopstd.game

import com.cyopstd.game.core.Balance
import com.cyopstd.game.model.AgentType
import com.cyopstd.game.model.EnemyType
import com.cyopstd.game.core.Maps
import com.cyopstd.game.core.WorldGeometry
import com.cyopstd.game.engine.GameEngine
import com.cyopstd.game.engine.PlacementResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The 1.19.0 balance pass: range that actually grows, an aura worth building
 * around, and two agents that finally do something nobody else does.
 *
 * The owner's diagnosis was that the late game feels impossible, and the
 * numbers agreed with them — damage multiplied twentyfold over a run while
 * range moved 1.69×, so a levelled agent hit like a truck and could not see
 * anything. These tests pin the new shape of that curve and the two identities
 * that came with it.
 */
class AgentIdentityTest {

    // ------------------------------------------------------------- the curve

    @Test
    fun `range climbs in five-level steps`() {
        val step = Balance.UPGRADE_RANGE_STEP
        assertEquals(1f, Balance.rangeMultiplier(1), 0.001f)
        assertEquals(1f, Balance.rangeMultiplier(4), 0.001f)
        assertEquals(step, Balance.rangeMultiplier(5), 0.001f)
        assertEquals(step, Balance.rangeMultiplier(9), 0.001f)
        assertEquals(step * step, Balance.rangeMultiplier(10), 0.001f)
    }

    @Test
    fun `the curve stops at the ceiling rather than erasing the map`() {
        // Uncapped, x1.25 every five levels reaches x86 by level 100, and the
        // map is 1600 units wide: every agent would cover all of it from
        // wherever it stood and placement would stop being a decision.
        assertEquals(Balance.UPGRADE_RANGE_CAP, Balance.rangeMultiplier(100), 0.001f)
        assertTrue(
            "the ceiling must be reachable inside a run, or it is not a ceiling",
            Balance.rangeMultiplier(50) == Balance.UPGRADE_RANGE_CAP
        )
        // And it has to leave somewhere on the board out of reach.
        val longest = AgentType.entries.maxOf { it.baseRange } * Balance.UPGRADE_RANGE_CAP
        assertTrue(
            "a maxed agent reaches $longest, which covers the whole 1600x760 board",
            longest < 2_000f
        )
    }

    @Test
    fun `the late game got materially more possible`() {
        // The point of the change, stated as a number. Under the old curve a
        // level-50 agent had x1.343 of its base reach.
        val old50 = 1f + 0.007f * 49f
        val new50 = Balance.rangeMultiplier(50)
        assertTrue(
            "level 50 went from ${old50}x to ${new50}x, which is not a change a " +
                "player would feel",
            new50 > old50 * 3f
        )
    }

    // -------------------------------------------------------- the identities

    @Test
    fun `exactly the agents meant to be jam-proof are`() {
        val immune = AgentType.entries.filter { it.immuneToJam }
        assertEquals(listOf(AgentType.FIREWALL), immune)
    }

    @Test
    fun `a jam lands on an ordinary agent and slides off a hardened one`() {
        val engine = GameEngine()
        engine.startNewRun()
        engine.placeAgent(AgentType.IDS, Maps.PERIMETER.nodes[0].id)
        engine.placeAgent(AgentType.FIREWALL, Maps.PERIMETER.nodes[1].id)
        val ordinary = engine.agentAt(Maps.PERIMETER.nodes[0].id)!!
        val hardened = engine.agentAt(Maps.PERIMETER.nodes[1].id)!!

        ordinary.jam(3f)
        hardened.jam(3f)

        assertTrue("an ordinary agent must be jammable", ordinary.disruptedFor > 0f)
        assertEquals(
            "a hardened agent must shrug the jam off",
            0f,
            hardened.disruptedFor,
            0.0001f
        )
    }

    @Test
    fun `a jam slows the agent it lands on`() {
        val engine = GameEngine()
        engine.startNewRun()
        engine.placeAgent(AgentType.IDS, Maps.PERIMETER.nodes[0].id)
        val agent = engine.agentAt(Maps.PERIMETER.nodes[0].id)!!
        val before = agent.effectiveCooldown()

        agent.jam(3f)

        assertTrue(
            "being jammed should slow the agent down: ${agent.effectiveCooldown()} " +
                "seconds between shots against $before",
            agent.effectiveCooldown() > before
        )
    }

    @Test
    fun `exactly the agents meant to splash do`() {
        val splashers = AgentType.entries.filter { it.splashRadius > 0f }
        assertEquals(listOf(AgentType.IPS), splashers)
    }

    @Test
    fun `a splashing agent hits more than one threat per shot`() {
        // Driven end to end rather than by calling the splash directly: what
        // matters is that a real IPS shot at a real pack damages more than the
        // thing it was aimed at, and the control run proves the scenario is
        // fair rather than the assertion being trivially true.
        assertEquals(
            "IPS splashes and FIREWALL does not, which is what makes this an A/B",
            0f,
            AgentType.FIREWALL.splashRadius,
            0f
        )

        assertEquals(1, damagedByFirstShot(AgentType.FIREWALL))
        assertTrue(
            "an IPS shot into a pack should hurt more than the one it aimed at",
            damagedByFirstShot(AgentType.IPS) > 1
        )
    }

    /**
     * How many threats were hurt the moment the first shot landed.
     *
     * The pack is spawned along one lane at nearly the same progress, because
     * position on a route is derived from progress -- assigning x and y
     * directly does nothing at all, a lesson this project has learned more than
     * once.
     */
    private fun damagedByFirstShot(type: AgentType): Int {
        val engine = GameEngine()
        // IPS unlocks at wave 3, and this scenario is about what it does when
        // it is on the board rather than about when it becomes available.
        engine.isAgentUnlocked = { true }
        engine.startNewRun()

        val node = Maps.PERIMETER.nodes.first { it.laneDistance <= type.baseRange * 0.5f }
        assertEquals(
            "could not place a $type in reach of a lane",
            PlacementResult.SUCCESS,
            engine.placeAgent(type, node.id)
        )

        // Five BOTs shoulder to shoulder. They are spawned along the route by
        // progress and then walked into the agent's reach, because position is
        // derived from progress -- assigning x and y directly does nothing at
        // all, a lesson this project has learned more than once.
        repeat(5) { i ->
            engine.enemySystem().spawnEscort(EnemyType.BOT, 0, 40f + i * 12f, 3)
        }
        val pack = engine.enemies.items.filter { it.active }
        val full = pack.associateWith { it.health }

        repeat(600) {
            engine.update(1f / 60f, 1f)
            val hurt = pack.count { it.active && it.health < (full[it] ?: 0f) }
            if (hurt > 0) return hurt
        }
        return 0
    }

    // ------------------------------------------------------------- the aura

    @Test
    fun `the tarpit aura is worth building around`() {
        assertEquals(300f, AgentType.TARPIT.baseRange, 0.01f)
        // The owner's complaint was that a level-50 tarpit still could not
        // cover enough of a late-game board to matter.
        val at50 = AgentType.TARPIT.statsAtLevel(50).range
        assertTrue("a maxed tarpit only reaches $at50", at50 > 1_000f)
    }

    @Test
    fun `reach still has to be paid for`() {
        // The owner's rule from the 1.8.0 rebalance, restated: a dearer agent
        // must not reach less far than a cheaper one. Fixing IPS broke this,
        // and it was fixed by raising the others rather than cutting IPS --
        // also the owner's rule.
        assertFalse(
            "IPS should no longer be the roster's embarrassment",
            AgentType.IPS.baseRange < AgentType.FIREWALL.baseRange
        )
        assertTrue(
            "the IDS must still see furthest of anything",
            AgentType.entries.all {
                it == AgentType.IDS || it.baseRange <= AgentType.IDS.baseRange
            }
        )
    }
}
