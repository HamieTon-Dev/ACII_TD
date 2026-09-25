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
    // Five lane bands, 132 apart. The gap is what lets a deployment node fit
    // between two routes at all: a node needs NODE_CLEARANCE (58) from each.
    private const val G_TOP = 118f
    private const val G_UPPER = 250f
    private const val G_LOWER = 510f
    private const val G_BOTTOM = 642f

    /** Where both lanes meet and the shared tail begins. */
    private const val G_TAIL_IN = 1035f

    /**
     * The circuit before the rack: up, across, down, back, and in.
     *
     * Shared by both lanes, so the last 1,900 units are ground every threat
     * covers. An agent posted here works both routes.
     */
    private val GAUNTLET_TAIL = arrayOf(
        Waypoint(G_TAIL_IN, 96f),
        Waypoint(1262f, 96f),
        Waypoint(1262f, 664f),
        Waypoint(1120f, 664f),
        Waypoint(1120f, WorldGeometry.CORE_Y),
        Waypoint(WorldGeometry.SERVER_X, WorldGeometry.CORE_Y)
    )

    /**
     * The owner's own layout, sketched and then measured.
     *
     * Two lanes that switchback across the full width, converge, and then run
     * a long circuit around a block before reaching the rack. **4340 world
     * units** on both routes, against 1772 on the perimeter — a threat spends
     * nearly two and a half times as long inside the defence.
     *
     * That is the entire point of it. The owner's report was specific: *"wave
     * 101 is impossible with the best build on the map."* More damage does not
     * answer that; more *time under fire* does, and it compounds with the hat
     * agents rather than merely adding to them.
     *
     * The spacing is not arbitrary. The first build of this shape packed the
     * switchbacks 110 units apart and came out with 38 nodes and almost none
     * alongside the tail, because a node must clear every route by
     * `NODE_CLEARANCE` on both sides. Undefended path is only delay — a boss
     * that walks a corridor nothing can shoot arrives just as healthy. Five
     * bands 132 apart is what the derivation actually wants.
     */
    val HUGGING_FACE = GameMap(
        id = "hugging_face",
        displayName = "HUGGING-FACE",
        tagline = "A gauntlet. Two routes, doubled back, then a circuit before the rack.",
        laneWaypoints = arrayOf(
            arrayOf(
                Waypoint(WorldGeometry.SPAWN_X, G_TOP),
                Waypoint(880f, G_TOP),
                Waypoint(880f, G_UPPER),
                Waypoint(240f, G_UPPER),
                Waypoint(240f, WorldGeometry.CORE_Y),
                Waypoint(G_TAIL_IN, WorldGeometry.CORE_Y),
                *GAUNTLET_TAIL
            ),
            arrayOf(
                Waypoint(WorldGeometry.SPAWN_X, G_BOTTOM),
                Waypoint(880f, G_BOTTOM),
                Waypoint(880f, G_LOWER),
                Waypoint(240f, G_LOWER),
                Waypoint(240f, WorldGeometry.CORE_Y),
                Waypoint(G_TAIL_IN, WorldGeometry.CORE_Y),
                *GAUNTLET_TAIL
            )
        ),
        candidateRows = floatArrayOf(56f, 184f, 315f, 445f, 576f, 704f),
        /**
         * Spots the owner marked by hand on a render of the map.
         *
         * Each is a pocket the grid declined because no column lands there:
         * the strip above the tail, the gap before its first vertical, two
         * against the left edge, and the inside of the loop the tail wraps
         * around — which the route passes on three sides and is the strongest
         * ground on the board.
         *
         * Five of the eight were nudged a few units clear of a route. They sat
         * 40–56 units away where 58 is required, so an agent there would have
         * been drawn overlapping the lane it was shooting into.
         */
        extraNodes = listOf(
            Waypoint(1000f, 36f),
            Waypoint(1160f, 34f),
            Waypoint(944f, 197f),
            Waypoint(178f, 297f),
            Waypoint(176f, 427f),
            Waypoint(1181f, 463f),
            Waypoint(1190f, 560f),
            Waypoint(1180f, 726f)
        ),
        // *"This level unlocks by reaching wave 100 of Hack AI level."*
        unlockMode = GameMode.HACK_AI,
        unlockAtWave = 100
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
