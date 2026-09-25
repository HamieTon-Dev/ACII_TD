package com.cyopstd.game.core

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max

/**
 * The battlefield is simulated in fixed "world units" and then letterboxed onto
 * whatever the device screen happens to be. Nothing in the game logic or the
 * renderer knows about pixels, which is what keeps the layout correct on every
 * phone from a 16:9 budget device to a 21:9 flagship.
 *
 * This object holds the parts of the world that **every map shares**: the
 * frame, the server rack, and how big a deployment node is. The routes and the
 * nodes derived from them belong to a [GameMap], of which there is now more
 * than one.
 *
 * Splitting it this way rather than making the whole thing a class was a
 * deliberate choice. Thirty-seven of the sixty call sites are `WIDTH` and
 * `HEIGHT`, which are properties of the screen rather than of the level, and
 * threading a map through the transform, the HUD anchors and the letterboxing
 * would have been churn that made none of them more correct.
 */
object WorldGeometry {

    const val WIDTH = 1600f
    const val HEIGHT = 760f

    /** Where hostile traffic enters. */
    const val SPAWN_X = -60f

    /** Left edge of the server rack; anything reaching this has "arrived". */
    const val SERVER_X = 1318f
    const val SERVER_WIDTH = 258f
    const val SERVER_TOP = 168f
    const val SERVER_HEIGHT = 430f

    /** Visual width of a route corridor. */
    const val LANE_HEIGHT = 54f

    /** Where routes meet for the final approach to the rack. */
    const val CORE_Y = 380f

    const val NODE_RADIUS = 26f

    /**
     * How close two deployment nodes may be, centre to centre.
     *
     * The perimeter map generated pairs exactly [NODE_RADIUS] apart — two
     * circles of radius 26 whose centres are 26 units apart, i.e. drawn almost
     * entirely on top of each other, fourteen times across the board. It was
     * invisible on a desk monitor and unusable on a phone: letterboxed onto a
     * 568x320dp window those two nodes are **9dp** apart, so a fingertip
     * covers both and the player gets whichever one the arithmetic preferred.
     *
     * Deriving the fix rather than moving the rows by hand is deliberate, and
     * for the same reason the nodes are derived at all: a future map cannot
     * reintroduce this by choosing unlucky candidate rows.
     *
     * The value is a full node apart plus a little, which leaves every pocket
     * its node while dropping the duplicate crowded against it.
     */
    internal const val MIN_NODE_SPACING = NODE_RADIUS * 2f + 4f

    /** Clearance a node needs from any route, so it never sits on the path. */
    internal val NODE_CLEARANCE = LANE_HEIGHT / 2f + NODE_RADIUS + 5f

    /** A node must cover at least this much route, or it is not worth offering. */
    internal const val MIN_NODE_COVERAGE = 90f

    /** Reference range used when *ranking* candidate nodes — the FIREWALL's. */
    internal const val REFERENCE_RANGE = 168f

    /**
     * Range a spot is judged *eligible* at — the longest any agent reaches.
     *
     * Judging eligibility at the shortest agent's range emptied the whole outer
     * band of the map: down the right-hand side both routes have turned inward
     * toward the core, so a spot out there is 170-200 units from anything, and
     * every one of them was silently dropped even though an ANALYST posted
     * there covers 400 units of route. A spot is offered if *some* agent can
     * work from it; the deploy overlay then dims the ones the agent actually
     * in hand cannot reach, so nobody pays for a tower that shoots at nothing.
     */
    internal const val ELIGIBILITY_RANGE = 290f

    internal const val COVERAGE_STEP = 4f

    internal const val GRID_COLUMNS = 12
    internal const val GRID_LEFT = 80f
    internal const val GRID_RIGHT = 1240f
}

/**
 * One level: its routes, and the deployment nodes those routes imply.
 *
 * A map is a set of waypoint chains and a set of candidate rows. Everything
 * else — path length, where a threat is at a given distance, which spots are
 * buildable and how good each one is — is *derived*, so a new level is a new
 * table of numbers rather than a new set of hand-placed towers that can drift
 * out of sync with the routes they are meant to cover.
 */
class GameMap(
    /** Stable id. It goes in the save, so a run cannot be restored onto the wrong level. */
    val id: String,
    val displayName: String,
    /** One line for the menu. */
    val tagline: String,
    /**
     * The routes, as waypoint chains. Movement, rendering and node placement
     * all derive from these — change a waypoint and the whole map follows,
     * including which deployment nodes exist.
     */
    val laneWaypoints: Array<Array<Waypoint>>,
    /**
     * Candidate rows, derived from the layout rather than spread evenly.
     *
     * A uniform grid put rows wherever the arithmetic landed, which meant the
     * obvious tower pockets either got a row a few units too close to a route
     * and were silently rejected, or got no row at all. Placing one row down
     * the centre of every pocket guarantees the spots that *look* buildable
     * are.
     */
    private val candidateRows: FloatArray,
    /**
     * Node positions placed by hand, in addition to the derived grid.
     *
     * The grid is a fixed set of columns crossed with [candidateRows], and it
     * is deliberately conservative: it only offers a spot where one is clearly
     * safe. That is right for most of a map and wrong in the corners — a
     * pocket inside a tight loop, or a strip along the board edge, can be
     * perfectly good ground that no column happens to land on.
     *
     * These fill those in. They are **not** a way around the rules: every one
     * is still checked for route clearance and for spacing against every other
     * node, exactly as a derived candidate is, so a hand-placed node can never
     * sit on a lane or on top of its neighbour. If a position fails those
     * checks it is silently dropped rather than bending them.
     */
    private val extraNodes: List<Waypoint> = emptyList()
) {

    val laneCount: Int get() = laneWaypoints.size

    /** Total path length of each route, used for "how far along" sorting. */
    val laneLength: FloatArray = FloatArray(laneWaypoints.size) { lane ->
        var total = 0f
        val pts = laneWaypoints[lane]
        for (i in 0 until pts.size - 1) total += pts[i].distanceTo(pts[i + 1])
        total
    }

    /** Where each route enters the board, for the entry labels. */
    fun entryPoint(lane: Int): Waypoint = laneWaypoints[lane.coerceIn(0, laneCount - 1)].first()

    /**
     * Resolve a distance along a route into a world position and heading.
     *
     * [out] receives `[x, y, angleRadians]`. The caller supplies the array so
     * that this can be used from the simulation loop and the renderer without
     * allocating.
     */
    fun positionAt(lane: Int, distance: Float, out: FloatArray) {
        val points = laneWaypoints[lane.coerceIn(0, laneCount - 1)]
        var remaining = max(0f, distance)

        for (i in 0 until points.size - 1) {
            val from = points[i]
            val to = points[i + 1]
            val segment = from.distanceTo(to)
            if (remaining <= segment || i == points.size - 2) {
                val t = if (segment <= 0f) 0f else (remaining / segment).coerceIn(0f, 1f)
                out[0] = from.x + (to.x - from.x) * t
                out[1] = from.y + (to.y - from.y) * t
                out[2] = atan2(to.y - from.y, to.x - from.x)
                return
            }
            remaining -= segment
        }
    }

    /** Shortest distance from a point to any route. */
    fun distanceToNearestLane(x: Float, y: Float): Float {
        var best = Float.MAX_VALUE
        for (lane in laneWaypoints.indices) {
            val points = laneWaypoints[lane]
            for (i in 0 until points.size - 1) {
                val d = distanceToSegment(x, y, points[i], points[i + 1])
                if (d < best) best = d
            }
        }
        return best
    }

    private fun distanceToSegment(px: Float, py: Float, a: Waypoint, b: Waypoint): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val lengthSq = dx * dx + dy * dy
        if (lengthSq <= 0f) return hypot(px - a.x, py - a.y)
        val t = (((px - a.x) * dx + (py - a.y) * dy) / lengthSq).coerceIn(0f, 1f)
        return hypot(px - (a.x + t * dx), py - (a.y + t * dy))
    }

    /**
     * How much route lies within [radius] of a point, in world units.
     *
     * This is the number that actually decides whether a node is worth
     * deploying to, so it also decides which candidate positions become nodes
     * at all.
     */
    fun laneCoverage(x: Float, y: Float, radius: Float): Float {
        var total = 0f
        for (lane in laneWaypoints.indices) {
            val points = laneWaypoints[lane]
            for (i in 0 until points.size - 1) {
                val a = points[i]
                val b = points[i + 1]
                val length = a.distanceTo(b)
                val steps = max(1, (length / WorldGeometry.COVERAGE_STEP).toInt())
                val stepLength = length / steps
                for (k in 0 until steps) {
                    val t = (k + 0.5f) / steps
                    val sx = a.x + (b.x - a.x) * t
                    val sy = a.y + (b.y - a.y) * t
                    if (hypot(sx - x, sy - y) <= radius) total += stepLength
                }
            }
        }
        return total
    }

    /**
     * Deployment nodes, derived from the routes rather than hand-placed.
     *
     * Candidates are filtered to positions that (a) clear every route by
     * [WorldGeometry.NODE_CLEARANCE], (b) sit clear of the server rack,
     * (c) actually cover some route, and (d) are not stacked on a node
     * already accepted. Deriving them means the map cannot drift out of sync
     * with itself: moving a waypoint moves the nodes, and no node exists that
     * would be pointless to build on or impossible to aim at.
     */
    val nodes: Array<NodePosition> = buildList {
        for (col in 0 until WorldGeometry.GRID_COLUMNS) {
            val x = WorldGeometry.GRID_LEFT +
                (WorldGeometry.GRID_RIGHT - WorldGeometry.GRID_LEFT) * col /
                (WorldGeometry.GRID_COLUMNS - 1f)
            if (x > WorldGeometry.SERVER_X - 55f) continue

            // Everything this column could offer, best first. Sorting by
            // coverage matters because of the spacing rule below: when two
            // candidates are too close to both exist, the one kept should be
            // the one worth deploying to, not whichever row happened to come
            // first in the table.
            val column = ArrayList<Pair<Int, Float>>()
            for ((row, y) in candidateRows.withIndex()) {
                if (distanceToNearestLane(x, y) < WorldGeometry.NODE_CLEARANCE) continue
                val coverage = laneCoverage(x, y, WorldGeometry.ELIGIBILITY_RANGE)
                if (coverage < WorldGeometry.MIN_NODE_COVERAGE) continue
                column += row to coverage
            }
            column.sortWith(compareByDescending<Pair<Int, Float>> { it.second }.thenBy { it.first })

            val taken = ArrayList<Float>()
            for ((row, _) in column) {
                val y = candidateRows[row]
                if (taken.any { abs(it - y) < WorldGeometry.MIN_NODE_SPACING }) continue
                taken += y
                add(Triple(col, row, y))
            }
        }
    }
        .let { grid ->
            // The grid decides ids; hand-placed nodes are appended after it so
            // that adding one cannot renumber the others.
            grid
        }
        // Top to bottom within each column, so ids read down the board the way
        // the board looks. They are also save keys, which is why the ordering
        // is pinned here rather than left to whatever the filter produced.
        .sortedWith(compareBy({ it.first }, { it.third }))
        .mapIndexed { index, (col, row, y) ->
            val x = WorldGeometry.GRID_LEFT +
                (WorldGeometry.GRID_RIGHT - WorldGeometry.GRID_LEFT) * col /
                (WorldGeometry.GRID_COLUMNS - 1f)
            NodePosition(
                id = index, column = col, row = row, x = x, y = y,
                laneDistance = distanceToNearestLane(x, y)
            )
        }
        .let { derived ->
            // Hand-placed nodes, held to the same rules as the derived ones.
            val all = derived.toMutableList()
            for (spot in extraNodes) {
                if (distanceToNearestLane(spot.x, spot.y) < WorldGeometry.NODE_CLEARANCE) continue
                if (spot.x > WorldGeometry.SERVER_X - 55f) continue
                // Keep it on the board: a node whose circle hangs off the edge
                // is drawn clipped and tapped unreliably.
                if (spot.y < WorldGeometry.NODE_RADIUS + 6f) continue
                if (spot.y > WorldGeometry.HEIGHT - WorldGeometry.NODE_RADIUS - 6f) continue
                if (all.any {
                        hypot(it.x - spot.x, it.y - spot.y) < WorldGeometry.MIN_NODE_SPACING
                    }
                ) {
                    continue
                }
                all += NodePosition(
                    id = all.size, column = -1, row = -1, x = spot.x, y = spot.y,
                    laneDistance = distanceToNearestLane(spot.x, spot.y)
                )
            }
            all
        }
        .toTypedArray()

    fun node(id: Int): NodePosition? = nodes.getOrNull(id)

    /** Nodes ordered so consecutive picks are spread across the board. */
    val nodesByCoverage: List<NodePosition> =
        nodes.sortedByDescending { laneCoverage(it.x, it.y, WorldGeometry.REFERENCE_RANGE) }

    override fun toString(): String = "GameMap($id, ${nodes.size} nodes)"
}

data class Waypoint(val x: Float, val y: Float) {
    fun distanceTo(other: Waypoint): Float = hypot(other.x - x, other.y - y)
}

data class NodePosition(
    val id: Int,
    val column: Int,
    val row: Int,
    val x: Float,
    val y: Float,
    /**
     * How far this spot is from the nearest route.
     *
     * Every node covers *something* at the reference range, but a short-ranged
     * agent dropped on a distant one would sit there shooting at nothing. The
     * deploy overlay compares this against the agent being placed so the
     * player can see which spots actually work for it, rather than finding out
     * after paying.
     */
    val laneDistance: Float
)
