package com.packetbastion.asciidefense.core

/**
 * The battlefield is simulated in fixed "world units" and then letterboxed onto
 * whatever the device screen happens to be. Nothing in the game logic or the
 * renderer knows about pixels, which is what keeps the layout correct on every
 * phone from a 16:9 budget device to a 21:9 flagship.
 */
object WorldGeometry {

    const val WIDTH = 1600f
    const val HEIGHT = 760f

    /** Number of network lanes on the first (and currently only) map. */
    const val LANE_COUNT = 3

    /** Where hostile packets enter. */
    const val SPAWN_X = -60f

    /** Left edge of the server rack; a packet reaching this has "arrived". */
    const val SERVER_X = 1318f
    const val SERVER_WIDTH = 258f
    const val SERVER_TOP = 168f
    const val SERVER_HEIGHT = 430f

    /** Vertical centre of each lane. */
    val laneY = floatArrayOf(232f, 392f, 552f)

    /** Visual height of a lane corridor. */
    const val LANE_HEIGHT = 96f

    /**
     * Waypoints a packet walks through, per lane. Lanes are straight today but
     * the traversal code is waypoint-based, so a future map can add bends and
     * junctions without touching the enemy movement system.
     */
    val laneWaypoints: Array<Array<Waypoint>> = Array(LANE_COUNT) { lane ->
        arrayOf(
            Waypoint(SPAWN_X, laneY[lane]),
            Waypoint(SERVER_X, laneY[lane])
        )
    }

    /** Total path length of a lane, used for "how far along" sorting. */
    val laneLength: FloatArray = FloatArray(LANE_COUNT) { lane ->
        var total = 0f
        val pts = laneWaypoints[lane]
        for (i in 0 until pts.size - 1) {
            total += pts[i].distanceTo(pts[i + 1])
        }
        total
    }

    // ------------------------------------------------------- deployment nodes

    const val NODE_ROWS = 4
    const val NODE_COLUMNS = 8

    private const val NODE_FIRST_X = 126f
    private const val NODE_SPACING_X = 152f

    /** Y position of each deployment row: above lane 1, between lanes, below lane 3. */
    val nodeRowY = floatArrayOf(146f, 312f, 472f, 638f)

    /** Which lanes a node row can realistically cover (purely informational). */
    val nodeRowLanes = arrayOf(
        intArrayOf(0),
        intArrayOf(0, 1),
        intArrayOf(1, 2),
        intArrayOf(2)
    )

    const val NODE_RADIUS = 30f

    /** Flat list of every deployment node on the map, in stable index order. */
    val nodes: Array<NodePosition> = Array(NODE_ROWS * NODE_COLUMNS) { index ->
        val row = index / NODE_COLUMNS
        val col = index % NODE_COLUMNS
        NodePosition(
            id = index,
            row = row,
            column = col,
            x = NODE_FIRST_X + col * NODE_SPACING_X,
            y = nodeRowY[row]
        )
    }

    fun node(id: Int): NodePosition? = nodes.getOrNull(id)
}

data class Waypoint(val x: Float, val y: Float) {
    fun distanceTo(other: Waypoint): Float {
        val dx = other.x - x
        val dy = other.y - y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}

data class NodePosition(
    val id: Int,
    val row: Int,
    val column: Int,
    val x: Float,
    val y: Float
)
