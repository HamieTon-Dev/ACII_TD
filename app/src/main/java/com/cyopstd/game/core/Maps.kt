package com.cyopstd.game.core

/**
 * The levels.
 *
 * Each is a table of waypoints and a table of candidate rows; the routes, the
 * path lengths and every deployment node come out of those. That derivation is
 * what makes a second level tractable at all — the alternative, hand-placing
 * seventy-odd towers against a new set of corridors, is how a map ends up with
 * spots that look buildable and are not.
 */
object Maps {

    /**
     * NETWORK PERIMETER — the original.
     *
     * Two serpentine routes rather than three straight corridors. Straight
     * lanes made slow targets — bosses above all — nearly unkillable: an agent
     * only ever had a target inside its radius for the few seconds it took to
     * cross that radius once. A route that doubles back past the same pocket
     * puts the same target under the same guns three or four times.
     *
     * The numbers bore it out. A straight lane gave a node beside it about 295
     * world units of covered path at base range; the bend pockets here give up
     * to 732, and the routes themselves are 2,353 units long against the old
     * 1,378.
     *
     * The two routes stay apart for most of their length and deliberately come
     * close twice: once running parallel across the middle, and once where they
     * merge for the final approach to CORE-SERVER. A node in either convergence
     * pocket covers both routes at once, which is the decision the map is built
     * around.
     *
     * ## Why the levels are where they are
     *
     * A deployment node needs [WorldGeometry.NODE_CLEARANCE] from a route's
     * centreline, so a pocket between two adjacent levels has to be at least
     * twice that — 116 units — before a tower can stand in it at all. An
     * earlier layout used 90-unit pockets, which looked like obvious tower
     * spots and silently refused to accept one.
     *
     * Every pocket is therefore [POCKET_HEIGHT], comfortably over that
     * threshold. The single exception is the gap between A3 and B1: the two
     * routes run deliberately close there, and that convergence is meant to be
     * covered from the pockets above and below it rather than built inside.
     */
    private const val POCKET_HEIGHT = 122f
    private const val CONVERGENCE_GAP = 72f

    // Public because [PERIMETER]'s layout tests assert about the pockets
    // between them -- that every pocket is wide enough to actually hold a
    // tower is a property of this map, and a test that re-derives the levels
    // from the waypoints would be asserting its own arithmetic.
    const val A1 = 100f
    const val A2 = A1 + POCKET_HEIGHT          // 222
    const val A3 = A2 + POCKET_HEIGHT          // 344
    const val B1 = A3 + CONVERGENCE_GAP        // 416
    const val B2 = B1 + POCKET_HEIGHT          // 538
    const val B3 = B2 + POCKET_HEIGHT          // 660

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

    val PERIMETER = GameMap(
        id = "perimeter",
        displayName = "NETWORK PERIMETER",
        tagline = "Two serpentine routes that double back past the same guns.",
        laneWaypoints = arrayOf(
            // Upper route: right, down, back left, down, long run right, up,
            // right, down into the convergence.
            arrayOf(
                Waypoint(WorldGeometry.SPAWN_X, A1),
                Waypoint(400f, A1),
                Waypoint(400f, A2),
                Waypoint(170f, A2),
                Waypoint(170f, A3),
                Waypoint(700f, A3),
                Waypoint(700f, A2),
                Waypoint(1050f, A2),
                Waypoint(1050f, A3),
                Waypoint(1150f, WorldGeometry.CORE_Y),
                Waypoint(WorldGeometry.SERVER_X, WorldGeometry.CORE_Y)
            ),
            // Lower route: the same shape mirrored, so neither side of the
            // board is the safe one.
            arrayOf(
                Waypoint(WorldGeometry.SPAWN_X, B3),
                Waypoint(400f, B3),
                Waypoint(400f, B2),
                Waypoint(170f, B2),
                Waypoint(170f, B1),
                Waypoint(700f, B1),
                Waypoint(700f, B2),
                Waypoint(1050f, B2),
                Waypoint(1050f, B1),
                Waypoint(1150f, WorldGeometry.CORE_Y),
                Waypoint(WorldGeometry.SERVER_X, WorldGeometry.CORE_Y)
            )
        ),
        candidateRows = floatArrayOf(
            A1 - MARGIN_ROW_OFFSET,      // above the upper route
            A1 - INNER_MARGIN_OFFSET,    // ...and closer in, where the route allows
            (A1 + A2) / 2f,              // upper route's top pocket
            (A2 + A3) / 2f,              // upper route's lower pocket
            WorldGeometry.CORE_Y,        // the mid-map band between the two routes
            (B1 + B2) / 2f,              // lower route's upper pocket
            (B2 + B3) / 2f,              // lower route's bottom pocket
            B3 + INNER_MARGIN_OFFSET,    // below the lower route, closer in
            B3 + MARGIN_ROW_OFFSET       // ...and the outer margin
        )
    )

    // ---------------------------------------------------------- Hugging-Face

    /**
     * Three routes, where the perimeter has two.
     *
     * The perimeter's shape is two long serpentines that each pass one bank of
     * towers repeatedly. This one asks a different question: **three shorter
     * routes that all squeeze through the same middle**, so the board has one
     * place that is worth far more than anywhere else and two flanks that
     * cannot be ignored while you build it.
     *
     * The centre route runs almost straight and is the fastest way in. The
     * outer two loop and double back, so they are slower but arrive from
     * directions the middle guns do not cover. A board built entirely around
     * the choke falls to the flanks; a board spread evenly never kills
     * anything in the middle.
     *
     * Three routes rather than two is also what makes it harder without
     * touching a single balance number: the same wave is split three ways and
     * arrives from three places at once.
     */
    private const val H_TOP = 118f
    private const val H_UPPER_MID = 250f
    private const val H_CENTRE = WorldGeometry.CORE_Y   // 380
    private const val H_LOWER_MID = 510f
    private const val H_BOTTOM = 642f

    /** Where all three routes pass within a few dozen units of each other. */
    private const val CHOKE_X = 880f

    val HUGGING_FACE = GameMap(
        id = "hugging_face",
        displayName = "HUGGING-FACE",
        tagline = "Three routes through one choke. Hold the middle, lose the flanks.",
        laneWaypoints = arrayOf(
            // Upper route: out wide, back in, then down into the choke.
            arrayOf(
                Waypoint(WorldGeometry.SPAWN_X, H_UPPER_MID),
                Waypoint(260f, H_UPPER_MID),
                Waypoint(260f, H_TOP),
                Waypoint(620f, H_TOP),
                Waypoint(620f, H_UPPER_MID),
                Waypoint(CHOKE_X, H_UPPER_MID),
                Waypoint(CHOKE_X, H_CENTRE),
                Waypoint(1140f, H_CENTRE),
                Waypoint(WorldGeometry.SERVER_X, WorldGeometry.CORE_Y)
            ),
            // Centre route: nearly straight, and the fastest way to the rack.
            arrayOf(
                Waypoint(WorldGeometry.SPAWN_X, H_CENTRE),
                Waypoint(430f, H_CENTRE),
                Waypoint(430f, H_CENTRE),
                Waypoint(CHOKE_X, H_CENTRE),
                Waypoint(1140f, H_CENTRE),
                Waypoint(WorldGeometry.SERVER_X, WorldGeometry.CORE_Y)
            ),
            // Lower route: the upper one mirrored.
            arrayOf(
                Waypoint(WorldGeometry.SPAWN_X, H_LOWER_MID),
                Waypoint(260f, H_LOWER_MID),
                Waypoint(260f, H_BOTTOM),
                Waypoint(620f, H_BOTTOM),
                Waypoint(620f, H_LOWER_MID),
                Waypoint(CHOKE_X, H_LOWER_MID),
                Waypoint(CHOKE_X, H_CENTRE),
                Waypoint(1140f, H_CENTRE),
                Waypoint(WorldGeometry.SERVER_X, WorldGeometry.CORE_Y)
            )
        ),
        candidateRows = floatArrayOf(
            H_TOP - 62f,
            (H_TOP + H_UPPER_MID) / 2f,
            (H_UPPER_MID + H_CENTRE) / 2f,
            (H_CENTRE + H_LOWER_MID) / 2f,
            (H_LOWER_MID + H_BOTTOM) / 2f,
            H_BOTTOM + 62f
        )
    )

    val all: List<GameMap> = listOf(PERIMETER, HUGGING_FACE)

    /**
     * Never throws, and never returns the wrong level silently.
     *
     * An unknown id means a save from a newer build, or a corrupted one. It
     * falls back to the original map, and the caller is expected to check the
     * id it asked for against the one it got — restoring a run onto the wrong
     * level would put every agent at a node that means something else.
     */
    fun fromIdSafe(id: String?): GameMap = all.firstOrNull { it.id == id } ?: PERIMETER
}
