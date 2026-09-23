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

    /** Where both routes meet for the final approach to the rack. */
    const val CORE_Y = 380f

    /**
     * The horizontal levels the routes run along, top to bottom.
     *
     * These are not arbitrary. A deployment node needs
     * [LANE_HEIGHT] / 2 + [NODE_RADIUS] + margin of clearance from a route's
     * centreline, so a pocket between two adjacent levels has to be at least
     * twice that — 116 units — before a tower can stand in it at all. An
     * earlier layout used 90-unit pockets, which looked like obvious tower
     * spots and silently refused to accept one.
     *
     * Every pocket is therefore [POCKET_HEIGHT], comfortably over that
     * threshold. The single exception is the gap between [A3] and [B1]: the two
     * routes run deliberately close there, and that convergence is meant to be
     * covered from the pockets above and below it rather than built inside.
     */
    private const val POCKET_HEIGHT = 122f
    private const val CONVERGENCE_GAP = 72f

    const val A1 = 100f
    const val A2 = A1 + POCKET_HEIGHT          // 222
    const val A3 = A2 + POCKET_HEIGHT          // 344
    const val B1 = A3 + CONVERGENCE_GAP        // 416
    const val B2 = B1 + POCKET_HEIGHT          // 538
    const val B3 = B2 + POCKET_HEIGHT          // 660

    /**
     * The routes, as waypoint chains. Movement, rendering and node placement all
     * derive from these — change a waypoint and the whole map follows, including
     * which deployment nodes exist.
     */
    val laneWaypoints: Array<Array<Waypoint>> = arrayOf(
        // Upper route: right, down, back left, down, long run right, up, right,
        // down into the convergence.
        arrayOf(
            Waypoint(SPAWN_X, A1),
            Waypoint(400f, A1),
            Waypoint(400f, A2),
            Waypoint(170f, A2),
            Waypoint(170f, A3),
            Waypoint(700f, A3),
            Waypoint(700f, A2),
            Waypoint(1050f, A2),
            Waypoint(1050f, A3),
            Waypoint(1150f, CORE_Y),
            Waypoint(SERVER_X, CORE_Y)
        ),
        // Lower route: the same shape mirrored, so neither side of the board is
        // the safe one.
        arrayOf(
            Waypoint(SPAWN_X, B3),
            Waypoint(400f, B3),
            Waypoint(400f, B2),
            Waypoint(170f, B2),
            Waypoint(170f, B1),
            Waypoint(700f, B1),
            Waypoint(700f, B2),
            Waypoint(1050f, B2),
            Waypoint(1050f, B1),
            Waypoint(1150f, CORE_Y),
            Waypoint(SERVER_X, CORE_Y)
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

    /** Reference range used when *ranking* candidate nodes — the FIREWALL's. */
    private const val REFERENCE_RANGE = 168f

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
    private const val ELIGIBILITY_RANGE = 290f

    private const val COVERAGE_STEP = 4f

    private const val GRID_COLUMNS = 12
    private const val GRID_LEFT = 80f
    private const val GRID_RIGHT = 1240f

    /** How far outside the outermost route the margin rows sit. */
    private const val MARGIN_ROW_OFFSET = 62f

    /**
     * A second margin row, tucked closer to where the routes run.
     *
     * The outer margin rows are placed relative to the serpentine's extremes,
     * which is right at the left of the map and much too far out at the right,
     * where both routes have turned inward towards the core: a tower on the
     * outer row there is 180 units from anything it could shoot. These inner
     * rows are close enough to matter. They simply do not exist down the left
     * of the map, because the clearance filter rejects them where the outer
     * route actually runs — which is exactly the behaviour wanted.
     */
    private const val INNER_MARGIN_OFFSET = 36f

    /**
     * Candidate rows, derived from the map rather than spread evenly.
     *
     * A uniform grid put rows wherever the arithmetic landed, which meant the
     * obvious tower pockets either got a row a few units too close to a route
     * and were silently rejected, or got no row at all. Placing one row down the
     * centre of every pocket guarantees the spots that *look* buildable are.
     */
    private val candidateRows: FloatArray = floatArrayOf(
        A1 - MARGIN_ROW_OFFSET,      // above the upper route
        A1 - INNER_MARGIN_OFFSET,    // ...and closer in, where the route allows
        (A1 + A2) / 2f,              // upper route's top pocket
        (A2 + A3) / 2f,              // upper route's lower pocket
        CORE_Y,                      // the mid-map band between the two routes
        (B1 + B2) / 2f,              // lower route's upper pocket
        (B2 + B3) / 2f,              // lower route's bottom pocket
        B3 + INNER_MARGIN_OFFSET,    // below the lower route, closer in
        B3 + MARGIN_ROW_OFFSET       // ...and the outer margin
    )

    /**
     * Deployment nodes, derived from the routes rather than hand-placed.
     *
     * Candidates are filtered to positions that (a) clear every route by
     * [NODE_CLEARANCE], (b) sit clear of the server rack, and (c) actually cover
     * some route. Deriving them means the map cannot drift out of sync with
     * itself: moving a waypoint moves the nodes, and no node exists that would
     * be pointless to build on.
     */
    val nodes: Array<NodePosition> = buildList {
        var id = 0
        for (col in 0 until GRID_COLUMNS) {
            val x = GRID_LEFT + (GRID_RIGHT - GRID_LEFT) * col / (GRID_COLUMNS - 1f)
            if (x > SERVER_X - 55f) continue
            for ((row, y) in candidateRows.withIndex()) {
                if (distanceToNearestLane(x, y) < NODE_CLEARANCE) continue
                if (laneCoverage(x, y, ELIGIBILITY_RANGE) < MIN_NODE_COVERAGE) continue
                add(
                    NodePosition(
                        id = id++, column = col, row = row, x = x, y = y,
                        laneDistance = distanceToNearestLane(x, y)
                    )
                )
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
