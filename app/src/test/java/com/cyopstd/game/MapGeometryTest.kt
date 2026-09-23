package com.cyopstd.game

import com.cyopstd.game.core.WorldGeometry
import com.cyopstd.game.model.AgentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/**
 * The map is derived, not drawn by hand, so these guard the derivation.
 *
 * The bug they exist for: an earlier layout had 90-unit pockets between route
 * levels. They looked like obvious tower spots, and the node filter silently
 * rejected every one of them because a node needs 116 units of room. The map
 * offered places to build that could not be built on.
 */
class MapGeometryTest {

    /** The x-span over which route A runs at A3 and route B runs at B1. */
    private val CONVERGENCE_LEFT = 150f
    private val CONVERGENCE_RIGHT = 720f


    private val requiredClearance =
        WorldGeometry.LANE_HEIGHT / 2f + WorldGeometry.NODE_RADIUS + 5f

    @Test
    fun `every pocket between route levels is wide enough to hold a tower`() {
        val pockets = listOf(
            "upper route, top pocket" to (WorldGeometry.A1 to WorldGeometry.A2),
            "upper route, lower pocket" to (WorldGeometry.A2 to WorldGeometry.A3),
            "lower route, upper pocket" to (WorldGeometry.B1 to WorldGeometry.B2),
            "lower route, bottom pocket" to (WorldGeometry.B2 to WorldGeometry.B3)
        )
        for ((label, bounds) in pockets) {
            val gap = bounds.second - bounds.first
            assertTrue(
                "$label is $gap units between route centrelines; a node needs " +
                    "${2 * requiredClearance} to stand in it",
                gap >= 2 * requiredClearance
            )
        }
    }

    @Test
    fun `the convergence gap is deliberately too tight to build inside`() {
        val gap = WorldGeometry.B1 - WorldGeometry.A3
        assertTrue(
            "the two routes should run close together across the middle",
            gap < 2 * requiredClearance
        )
        // And nothing may be placed in it. Note the x bound: A3 and B1 are
        // only a route apart across the middle of the map. Further right both
        // routes have turned inward and that same band of y is a wide-open
        // corridor 130 units from anything, which is prime building ground --
        // checking y alone would forbid the best spots on the board.
        val inConvergence = WorldGeometry.nodes.count {
            it.y > WorldGeometry.A3 && it.y < WorldGeometry.B1 &&
                it.x > CONVERGENCE_LEFT && it.x < CONVERGENCE_RIGHT
        }
        assertEquals("no node may sit inside the convergence", 0, inConvergence)
    }

    @Test
    fun `the mid-map corridor between the routes is buildable`() {
        // The counterpart to the test above: right of the convergence the two
        // routes are far apart, and that band is where the strongest spots on
        // the map are -- a tower there covers both routes at once.
        val corridor = WorldGeometry.nodes.filter {
            it.y > WorldGeometry.A3 && it.y < WorldGeometry.B1 && it.x >= CONVERGENCE_RIGHT
        }
        assertTrue("the mid-map corridor has no nodes", corridor.isNotEmpty())

        val all = WorldGeometry.nodes
            .map { WorldGeometry.laneCoverage(it.x, it.y, 168f) }
            .sorted()
        val median = all[all.size / 2]
        val bestInCorridor = corridor.maxOf { WorldGeometry.laneCoverage(it.x, it.y, 168f) }
        assertTrue(
            "the corridor's best spot covers $bestInCorridor, median is $median",
            bestInCorridor > median * 1.5f
        )
        assertTrue(
            "the corridor's best spot covers only $bestInCorridor units",
            bestInCorridor >= 400f
        )
    }

    @Test
    fun `every pocket actually received deployment nodes`() {
        val pocketCentres = listOf(
            (WorldGeometry.A1 + WorldGeometry.A2) / 2f,
            (WorldGeometry.A2 + WorldGeometry.A3) / 2f,
            (WorldGeometry.B1 + WorldGeometry.B2) / 2f,
            (WorldGeometry.B2 + WorldGeometry.B3) / 2f
        )
        for (centre in pocketCentres) {
            val count = WorldGeometry.nodes.count { hypot(0f, it.y - centre) < 1f }
            assertTrue("pocket centred at $centre has no nodes", count >= 2)
        }
    }

    @Test
    fun `no node sits on a route`() {
        for (node in WorldGeometry.nodes) {
            val distance = WorldGeometry.distanceToNearestLane(node.x, node.y)
            assertTrue(
                "node ${node.id} at (${node.x}, ${node.y}) is $distance from a " +
                    "route; it needs $requiredClearance",
                distance >= requiredClearance
            )
        }
    }

    @Test
    fun `no node is useless to every agent`() {
        // A spot is offered if *some* agent can work from it. Judging that by
        // the shortest range instead emptied the whole outer band of the map,
        // where both routes have turned in towards the core and everything is
        // 170-200 units from anything -- ground an ANALYST covers perfectly
        // well. What must never exist is a spot no agent at all can use.
        val longestRange = AgentType.entries.maxOf { it.baseRange }
        for (node in WorldGeometry.nodes) {
            val coverage = WorldGeometry.laneCoverage(node.x, node.y, longestRange)
            assertTrue(
                "node ${node.id} covers only $coverage units at any range",
                coverage >= 90f
            )
        }
    }

    @Test
    fun `a node beyond an agent's reach is knowable before it is paid for`() {
        // The above only holds because the deploy overlay can tell the player
        // which spots the agent in hand actually reaches. That depends on
        // laneDistance being both present and honest.
        for (node in WorldGeometry.nodes) {
            assertEquals(
                "node ${node.id} reports the wrong distance to the route",
                WorldGeometry.distanceToNearestLane(node.x, node.y),
                node.laneDistance,
                0.01f
            )
        }

        val shortest = AgentType.entries.minOf { it.baseRange }
        for (node in WorldGeometry.nodes) {
            val coverage = WorldGeometry.laneCoverage(node.x, node.y, shortest)
            // Anything the shortest-ranged agent cannot cover must be reported
            // as out of its reach, or the overlay would wave it through.
            if (coverage <= 0f) {
                // >= rather than >: a route exactly at the range limit is
                // touched at a single point, which is zero length of cover.
                assertTrue(
                    "node ${node.id} covers nothing at range $shortest yet " +
                        "claims to be ${node.laneDistance} away",
                    node.laneDistance >= shortest
                )
            }
        }
    }

    @Test
    fun `nodes are clear of the server rack and inside the world`() {
        for (node in WorldGeometry.nodes) {
            assertTrue(
                "node ${node.id} overlaps the server rack",
                node.x + WorldGeometry.NODE_RADIUS < WorldGeometry.SERVER_X
            )
            assertTrue(node.y - WorldGeometry.NODE_RADIUS >= 0f)
            assertTrue(node.y + WorldGeometry.NODE_RADIUS <= WorldGeometry.HEIGHT)
        }
    }

    @Test
    fun `both routes are long enough to give towers repeated passes`() {
        // The straight-lane map was 1378 units and gave a tower one pass at each
        // target, which is what made slow bosses unkillable.
        for (lane in 0 until WorldGeometry.LANE_COUNT) {
            assertTrue(
                "route $lane is only ${WorldGeometry.laneLength[lane]} units",
                WorldGeometry.laneLength[lane] > 2000f
            )
        }
    }

    @Test
    fun `both routes end at the core`() {
        for (lane in 0 until WorldGeometry.LANE_COUNT) {
            val last = WorldGeometry.laneWaypoints[lane].last()
            assertEquals(WorldGeometry.SERVER_X, last.x, 0.01f)
            assertEquals(WorldGeometry.CORE_Y, last.y, 0.01f)
        }
    }

    @Test
    fun `position and heading are derived from route progress`() {
        val scratch = FloatArray(3)
        for (lane in 0 until WorldGeometry.LANE_COUNT) {
            val length = WorldGeometry.laneLength[lane]
            var previousX = Float.NEGATIVE_INFINITY
            var progress = 0f
            while (progress <= length) {
                WorldGeometry.positionAt(lane, progress, scratch)
                assertTrue(
                    "progress $progress on route $lane left the path",
                    WorldGeometry.distanceToNearestLane(scratch[0], scratch[1]) < 1f
                )
                progress += 25f
            }
            // The end of the route must be the core, not somewhere short of it.
            WorldGeometry.positionAt(lane, length, scratch)
            assertEquals(WorldGeometry.SERVER_X, scratch[0], 1f)
            previousX = scratch[0]
            assertTrue(previousX > 0f)
        }
    }
}
