package com.cyopstd.game.core

import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * The battlefield is simulated in fixed "world units" and then letterboxed onto
 * whatever the device screen happens to be. Nothing in the game logic or the
 * renderer knows about pixels, which is what keeps the layout correct on every
 * phone from a 16:9 budget device to a 21:9 flagship.
 *
 * ## The map
 *
 * Two serpentine routes rather than three straight corridors. Straight lanes
 * made slow targets — bosses above all — nearly unkillable: an agent only ever
 * had a target inside its radius for the few seconds it took to cross that
 * radius once. A route that doubles back past the same pocket puts the same
 * target under the same guns three or four times.
 *
 * The numbers bear it out. A straight lane gave a node beside it about 295
 * world units of covered path at base range; the bend pockets here give up to
 * 732, and the routes themselves are 2,353 units long against the old 1,378.
 *
 * The two routes stay apart for most of their length and deliberately come
 * close twice: once running parallel across the middle (the upper route at
 * y=352, the lower at y=424), and once where they merge at (1150, 383) for the
 * final approach to CORE-SERVER. A node in either convergence pocket covers
 * both routes at once, which is the decision the map is built around.
 */
object WorldGeometry {

    const val WIDTH = 1600f
    const val HEIGHT = 760f

    /** Number of routes on this map. */
    const val LANE_COUNT = 2

    /** Where hostile traffic enters. */
    const val SPAWN_X = -60f

    /** Left edge of the server rack; anything reaching this has "arrived". */
    const val SERVER_X = 1318f
    const val SERVER_WIDTH = 258f
    const val SERVER_TOP = 168f
    const val SERVER_HEIGHT = 430f

    /** Visual width of a route corridor. */
    const val LANE_HEIGHT = 54f

    /**
     * The routes, as waypoint chains. Movement, rendering and node placement all
     * derive from these — change a waypoint and the whole map follows, including
     * which deployment nodes exist.
     */
    val laneWaypoints: Array<Array<Waypoint>> = arrayOf(
        // Upper route: right, down, back left, down, long run right, up, right,
        // down into the convergence.
        arrayOf(
            Waypoint(SPAWN_X, 140f),
            Waypoint(400f, 140f),
            Waypoint(400f, 262f),
            Waypoint(170f, 262f),
            Waypoint(170f, 352f),
            Waypoint(700f, 352f),
            Waypoint(700f, 196f),
            Waypoint(1050f, 196f),
            Waypoint(1050f, 330f),
            Waypoint(1150f, 383f),
            Waypoint(SERVER_X, 383f)
        ),
        // Lower route: the same shape mirrored, so neither side of the board is
        // the safe one.
        arrayOf(
            Waypoint(SPAWN_X, 636f),
            Waypoint(400f, 636f),
            Waypoint(400f, 514f),
            Waypoint(170f, 514f),
            Waypoint(170f, 424f),
            Waypoint(700f, 424f),
            Waypoint(700f, 580f),
            Waypoint(1050f, 580f),
            Waypoint(1050f, 446f),
            Waypoint(1150f, 383f),
            Waypoint(SERVER_X, 383f)
        )
    )

    /** Total path length of each route, used for "how far along" sorting. */
    val laneLength: FloatArray = FloatArray(LANE_COUNT) { lane ->
        var total = 0f
        val pts = laneWaypoints[lane]
        for (i in 0 until pts.size - 1) total += pts[i].distanceTo(pts[i + 1])
        total
    }

    /** Where each route enters the board, for the entry labels. */
    fun entryPoint(lane: Int): Waypoint = laneWaypoints[lane].first()

    /**
     * Resolve a distance along a route into a world position and heading.
     *
     * [out] receives `[x, y, angleRadians]`. The caller supplies the array so
     * that this can be used from the simulation loop and the renderer without
     * allocating.
     */
    fun positionAt(lane: Int, distance: Float, out: FloatArray) {
        val points = laneWaypoints[lane.coerceIn(0, LANE_COUNT - 1)]
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
        for (lane in 0 until LANE_COUNT) {
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
        for (lane in 0 until LANE_COUNT) {
            val points = laneWaypoints[lane]
            for (i in 0 until points.size - 1) {
                val a = points[i]
                val b = points[i + 1]
                val length = a.distanceTo(b)
                val steps = max(1, (length / COVERAGE_STEP).toInt())
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

    // ------------------------------------------------------- deployment nodes

    const val NODE_RADIUS = 26f

    /** Clearance a node needs from any route, so it never sits on the path. */
    private val NODE_CLEARANCE = LANE_HEIGHT / 2f + NODE_RADIUS + 5f

    /** A node must cover at least this much route, or it is not worth offering. */
    private const val MIN_NODE_COVERAGE = 90f

    /** Reference range used when judging a candidate node — the FIREWALL's. */
    private const val REFERENCE_RANGE = 168f

    private const val COVERAGE_STEP = 4f

    private const val GRID_COLUMNS = 10
    private const val GRID_ROWS = 6
    private const val GRID_LEFT = 90f
    private const val GRID_RIGHT = 1240f
    private const val GRID_TOP = 70f
    private const val GRID_BOTTOM = 700f

    /**
     * Deployment nodes, derived from the routes rather than hand-placed.
     *
     * A candidate grid is filtered down to positions that (a) clear every route
     * by [NODE_CLEARANCE], (b) sit clear of the server rack, and (c) actually
     * cover some route. Deriving them means the map cannot drift out of sync
     * with itself: moving a waypoint moves the nodes.
     */
    val nodes: Array<NodePosition> = buildList {
        var id = 0
        for (col in 0 until GRID_COLUMNS) {
            val x = GRID_LEFT + (GRID_RIGHT - GRID_LEFT) * col / (GRID_COLUMNS - 1f)
            if (x > SERVER_X - 55f) continue
            for (row in 0 until GRID_ROWS) {
                val y = GRID_TOP + (GRID_BOTTOM - GRID_TOP) * row / (GRID_ROWS - 1f)
                if (distanceToNearestLane(x, y) < NODE_CLEARANCE) continue
                if (laneCoverage(x, y, REFERENCE_RANGE) < MIN_NODE_COVERAGE) continue
                add(NodePosition(id = id++, column = col, row = row, x = x, y = y))
            }
        }
    }.toTypedArray()

    fun node(id: Int): NodePosition? = nodes.getOrNull(id)

    /** Nodes ordered so consecutive picks are spread across the board. */
    val nodesByCoverage: List<NodePosition> =
        nodes.sortedByDescending { laneCoverage(it.x, it.y, REFERENCE_RANGE) }
}

data class Waypoint(val x: Float, val y: Float) {
    fun distanceTo(other: Waypoint): Float = hypot(other.x - x, other.y - y)
}

data class NodePosition(
    val id: Int,
    val column: Int,
    val row: Int,
    val x: Float,
    val y: Float
)
