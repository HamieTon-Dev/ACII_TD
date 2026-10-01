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

    private const val NM_TOP = 96f
    private const val NM_UPPER = 250f
    private const val NM_LOWER = 510f
    private const val NM_BOTTOM = 664f
    private const val NM_TURN_X = 1160f
    private const val NM_MERGE_X = 160f

    // Per-level difficulty (see GameMap.threatHealthScale), tuned with MapDifficultyTest.
    const val HF_H = 2.25f
    const val HF_R = 1.15f
    const val NM_H = 2.5f
    const val NM_R = 1.2f
    const val DU_H = 1.1f
    const val DU_R = 1.25f

    const val DD_H = 2.6f
    const val DD_R = 1.3f
    private const val DD_EDGE = 60f
    private const val DD_INNER = 300f
    private const val DD_GAP_Y = 380f
    private const val DD_TURN_X = 1200f
    /**
     * Four wide teeth rather than six narrow ones. The first build packed six
     * teeth 180 apart and stacked spots 68 apart down each pocket: legal by
     * `MIN_NODE_SPACING`, but a deployed agent's ring and level label need
     * about 90, and the agents drew on top of each other. Pockets 250 wide
     * hold two columns 90 apart, each 80 clear of the route.
     */
    private val DD_TEETH_X = floatArrayOf(160f, 410f, 660f, 910f)
    /** Two columns down every pocket, plus the strip before the first tooth. */
    private val DD_POCKET_X = listOf(80f, 240f, 330f, 490f, 580f, 740f, 830f, 1010f, 1100f)
    private val DD_POCKET_Y = listOf(50f, 140f, 230f)
    /** The strip before the first tooth, and the pockets open to the middle gap. */
    private val DD_GAP_POCKET_X = listOf(80f, 490f, 580f, 1010f, 1100f)
    private const val DD_GAP_EDGE_Y = 300f

    private const val DU_TOP = 110f
    private const val DU_BOTTOM = 650f
    private const val DU_X1 = 250f
    private const val DU_X2 = 450f
    private const val DU_X3 = 650f
    private const val DU_X4 = 850f
    private const val DU_TURN_X = 1190f

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
            Waypoint(1180f, 726f),
            // Owner: "at least 4-5 more agent spots". Found by searching the
            // board for every position 90 clear of the other spots and 58 of
            // the route: these five are all there were.
            Waypoint(1254f, 34f),
            Waypoint(1204f, 244f),
            Waypoint(299f, 309f),
            Waypoint(299f, 439f),
            Waypoint(939f, 534f)
        ),
        // *"This level unlocks by reaching wave 100 of Hack AI level."*
        unlockMode = GameMode.HACK_AI,
        unlockAtWave = 100,
        threatHealthScale = HF_H,
        rewardScale = HF_R,
        // Owner: "dark green theme applied, lanes can be a slightly brighter
        // shade of green".
        theme = LevelTheme(
            // Darkened on request (was 0xFF061209); the lanes are unchanged.
            backdrop = 0xFF030A05.toInt(),
            grid = 0xFF113520.toInt(),
            laneFill = 0xFF133A26.toInt(),
            laneBorder = 0xFF2A8A52.toInt(),
            laneMarks = 0xFF3FBF73.toInt()
        )
    )

    /**
     * NEURAL-MESH — map 3, the owner's pick of four candidates (BACKPROP).
     *
     * Each route runs the full width, doubles back along the next band, and
     * the two meet at the far left to make one long shared return to the
     * rack. 3,662 units per route: longer than the perimeter, shorter than the
     * gauntlet, and the shared return is ground every threat walks.
     *
     * Bands are 154 units apart, so every pocket clears 2 × NODE_CLEARANCE and
     * the rows sit down the middle of each.
     */
    val NEURAL_MESH = GameMap(
        id = "neural_mesh",
        displayName = "NEURAL-MESH",
        tagline = "Out, back, and a long shared return to the rack.",
        laneWaypoints = arrayOf(
            arrayOf(
                Waypoint(WorldGeometry.SPAWN_X, NM_TOP),
                Waypoint(NM_TURN_X, NM_TOP),
                Waypoint(NM_TURN_X, NM_UPPER),
                Waypoint(NM_MERGE_X, NM_UPPER),
                Waypoint(NM_MERGE_X, WorldGeometry.CORE_Y),
                Waypoint(WorldGeometry.SERVER_X, WorldGeometry.CORE_Y)
            ),
            arrayOf(
                Waypoint(WorldGeometry.SPAWN_X, NM_BOTTOM),
                Waypoint(NM_TURN_X, NM_BOTTOM),
                Waypoint(NM_TURN_X, NM_LOWER),
                Waypoint(NM_MERGE_X, NM_LOWER),
                Waypoint(NM_MERGE_X, WorldGeometry.CORE_Y),
                Waypoint(WorldGeometry.SERVER_X, WorldGeometry.CORE_Y)
            )
        ),
        candidateRows = floatArrayOf(34f, 173f, 315f, 445f, 587f, 726f),
        // *"unlock at wave 100 on hugging face"*
        threatHealthScale = NM_H,
        rewardScale = NM_R,
        unlockMapId = HUGGING_FACE.id,
        unlockMapName = HUGGING_FACE.displayName,
        unlockAtWave = 100,
        // Owner: "very dark red background and slightly lighter red lanes".
        // Kept dark enough that the red threats still stand out on it.
        theme = LevelTheme(
            backdrop = 0xFF140507.toInt(),
            grid = 0xFF3A1016.toInt(),
            laneFill = 0xFF36101A.toInt(),
            laneBorder = 0xFF6A1E28.toInt(),
            laneMarks = 0xFFB0485A.toInt()
        )
    )

    /**
     * 🦆 DUCK-USB — map 4, the owner's pick of four candidates (BRAID), and
     * the owner's name: a nod to the keystroke-injection "rubber duck" USB.
     *
     * Three routes that swap places twice on diagonals before the rack. A node
     * beside a crossing covers two routes at once, which is the decision the
     * map is built around; the middle route is the shortest.
     */
    val DUCK_USB = GameMap(
        id = "duck_usb",
        displayName = "\uD83E\uDD86 DUCK-USB",
        tagline = "Three routes swap places twice. Every crossing is a spot that hits two.",
        laneWaypoints = arrayOf(
            arrayOf(
                Waypoint(WorldGeometry.SPAWN_X, DU_TOP), Waypoint(DU_X1, DU_TOP),
                Waypoint(DU_X2, WorldGeometry.CORE_Y), Waypoint(DU_X3, WorldGeometry.CORE_Y),
                Waypoint(DU_X4, DU_BOTTOM), Waypoint(DU_TURN_X, DU_BOTTOM),
                Waypoint(DU_TURN_X, WorldGeometry.CORE_Y),
                Waypoint(WorldGeometry.SERVER_X, WorldGeometry.CORE_Y)
            ),
            arrayOf(
                Waypoint(WorldGeometry.SPAWN_X, WorldGeometry.CORE_Y),
                Waypoint(DU_X1, WorldGeometry.CORE_Y), Waypoint(DU_X2, DU_TOP),
                Waypoint(DU_TURN_X, DU_TOP), Waypoint(DU_TURN_X, WorldGeometry.CORE_Y),
                Waypoint(WorldGeometry.SERVER_X, WorldGeometry.CORE_Y)
            ),
            arrayOf(
                Waypoint(WorldGeometry.SPAWN_X, DU_BOTTOM), Waypoint(DU_X3, DU_BOTTOM),
                Waypoint(DU_X4, WorldGeometry.CORE_Y),
                Waypoint(WorldGeometry.SERVER_X, WorldGeometry.CORE_Y)
            )
        ),
        candidateRows = floatArrayOf(36f, 200f, 290f, 470f, 560f, 724f),
        // Owner: "5-8 more agent spots". The ground either side of the last
        // vertical before the core, which no grid column reaches.
        extraNodes = listOf(1130f, 1255f).flatMap { x ->
            listOf(200f, 290f, 470f, 560f).map { y -> Waypoint(x, y) }
        },
        threatHealthScale = DU_H,
        rewardScale = DU_R,
        unlockMapId = NEURAL_MESH.id,
        unlockMapName = NEURAL_MESH.displayName,
        unlockAtWave = 100,
        // Owner: "very very dark yellow background and slightly lighter
        // yellow lanes". Kept well below crypto gold so money still pops.
        theme = LevelTheme(
            backdrop = 0xFF0E0C03.toInt(),
            grid = 0xFF2E2A0E.toInt(),
            laneFill = 0xFF2C280C.toInt(),
            laneBorder = 0xFF5A4F16.toInt(),
            laneMarks = 0xFFB8A040.toInt()
        )
    )

    /**
     * DDoS — map 5, the owner's pick of four candidates (TWIN COMB), with
     * *"almost double the agent spaces"*.
     *
     * Each route combs its own half of the board: down and up six times in
     * the top half, the mirror in the bottom, meeting only in front of the
     * rack. The gap between the two combs is ground that reaches both routes.
     * The derived grid offers only one spot per comb pocket, so every pocket
     * is filled by hand down its centre line — each is still checked for
     * route clearance and spacing like any other node.
     */
    val DDOS = GameMap(
        id = "ddos",
        displayName = "DDoS",
        tagline = "Two routes comb their own halves. Towers in the middle gap reach both.",
        laneWaypoints = arrayOf(ddosComb(top = true), ddosComb(top = false)),
        candidateRows = floatArrayOf(DD_GAP_Y),
        extraNodes = DD_POCKET_X.flatMap { x -> DD_POCKET_Y.map { y -> Waypoint(x, y) } } +
            DD_POCKET_X.flatMap { x -> DD_POCKET_Y.map { y -> Waypoint(x, WorldGeometry.HEIGHT - y) } } +
            // Owner: "at least 10 more spots". A third row in the pockets
            // that open onto the middle gap, and the strip before the first
            // tooth, each 80 clear of the route.
            DD_GAP_POCKET_X.flatMap { x ->
                listOf(Waypoint(x, DD_GAP_EDGE_Y), Waypoint(x, WorldGeometry.HEIGHT - DD_GAP_EDGE_Y))
            },
        unlockMapId = DUCK_USB.id,
        unlockMapName = DUCK_USB.displayName,
        unlockAtWave = 100,
        threatHealthScale = DD_H,
        rewardScale = DD_R,
        // Owner: "very very dark orange background and slightly lighter
        // orange lanes". Edges kept dark, clear of the orange warning colour.
        theme = LevelTheme(
            backdrop = 0xFF110703.toInt(),
            grid = 0xFF351A0C.toInt(),
            laneFill = 0xFF33190A.toInt(),
            laneBorder = 0xFF6A3A16.toInt(),
            laneMarks = 0xFFC06A30.toInt()
        )
    )

    /** One comb of DDoS: the top half's, or its mirror in the bottom. */
    private fun ddosComb(top: Boolean): Array<Waypoint> {
        fun y(v: Float) = if (top) v else WorldGeometry.HEIGHT - v
        val points = ArrayList<Waypoint>()
        points += Waypoint(WorldGeometry.SPAWN_X, y(DD_EDGE))
        var outer = true
        for (x in DD_TEETH_X) {
            points += Waypoint(x, y(if (outer) DD_EDGE else DD_INNER))
            points += Waypoint(x, y(if (outer) DD_INNER else DD_EDGE))
            outer = !outer
        }
        points += Waypoint(DD_TURN_X, y(DD_EDGE))
        points += Waypoint(DD_TURN_X, WorldGeometry.CORE_Y)
        points += Waypoint(WorldGeometry.SERVER_X, WorldGeometry.CORE_Y)
        return points.toTypedArray()
    }

    // ------------------------------------------------ chapter two (♡1)
    //
    // Levels 6–10: layouts and colours picked by the owner on 2026-09-30 from
    // `ChapterTwoPreview`. **The display names are working titles** (the
    // layout names) until the owner picks the real ones; ids are fixed now
    // because saves are keyed by them.

    private val S = WorldGeometry.SPAWN_X
    private val R = WorldGeometry.SERVER_X
    private val C = WorldGeometry.CORE_Y
    private fun w(x: Float, y: Float) = Waypoint(x, y)
    /** Dense candidate rows; the node filter keeps the ones that fit. */
    private val DENSE_ROWS = FloatArray(21) { 30f + it * 35f }

    private fun theme(b: Long, g: Long, f: Long, e: Long, m: Long) =
        LevelTheme(b.toInt(), g.toInt(), f.toInt(), e.toInt(), m.toInt())

    /** Level 6: layout 6B TRIDENT, colour C6 SYNTHWAVE. */
    val TRIDENT = GameMap(
        id = "trident",
        displayName = "TRIDENT",
        tagline = "Three routes: a straight shot down the middle, two that zigzag above and below it.",
        laneWaypoints = arrayOf(
            arrayOf(w(S, 90f), w(480f, 90f), w(480f, 230f), w(880f, 230f), w(880f, 90f), w(1190f, 90f), w(1190f, C), w(R, C)),
            arrayOf(w(S, C), w(R, C)),
            arrayOf(w(S, 670f), w(480f, 670f), w(480f, 530f), w(880f, 530f), w(880f, 670f), w(1190f, 670f), w(1190f, C), w(R, C))
        ),
        candidateRows = DENSE_ROWS,
        unlockMapId = DDOS.id,
        unlockMapName = DDOS.displayName,
        unlockAtWave = 100,
        threatHealthScale = 2.8f,
        rewardScale = 1.35f,
        // The pink lane edge of the preview sat too close to the threat
        // colours; violet keeps the look and stays readable.
        theme = theme(0xFF0A0418, 0xFF2A0F4A, 0xFF1A0F3A, 0xFF6A2A9A, 0xFF26C6DA)
    )

    /** Level 7: layout 6A SPIRAL, colour C3 MAGENTA. */
    val SPIRAL = GameMap(
        id = "spiral",
        displayName = "SPIRAL",
        tagline = "One long route drops in from the top and spirals outward to the rack.",
        laneWaypoints = arrayOf(
            arrayOf(w(560f, -60f), w(560f, C), w(840f, C), w(840f, 220f), w(290f, 220f), w(290f, 550f),
                w(1060f, 550f), w(1060f, 80f), w(1190f, 80f), w(1190f, C), w(R, C))
        ),
        candidateRows = DENSE_ROWS,
        unlockMapId = TRIDENT.id,
        unlockMapName = TRIDENT.displayName,
        unlockAtWave = 100,
        threatHealthScale = 7.1f,
        rewardScale = 1.4f,
        theme = theme(0xFF12040E, 0xFF3A0F2E, 0xFF3A0F30, 0xFF641A58, 0xFFD04CA8)
    )

    /** Level 8: layout 8C ZIGZAG, colour C8 COPPER. */
    val ZIGZAG = GameMap(
        id = "zigzag",
        displayName = "ZIGZAG",
        tagline = "One route slashes corner to corner across the board five times.",
        laneWaypoints = arrayOf(
            arrayOf(w(S, 90f), w(100f, 90f), w(320f, 670f), w(540f, 90f), w(760f, 670f), w(980f, 90f),
                w(1190f, 670f), w(1190f, C), w(R, C))
        ),
        candidateRows = DENSE_ROWS,
        unlockMapId = SPIRAL.id,
        unlockMapName = SPIRAL.displayName,
        unlockAtWave = 100,
        threatHealthScale = 7.6f,
        rewardScale = 1.45f,
        theme = theme(0xFF0C0605, 0xFF2E1A14, 0xFF32190F, 0xFF643622, 0xFFD08A5C)
    )

    /** Level 9: layout 10D HELIX, colour D8 ROSE GOLD. */
    val HELIX = GameMap(
        id = "helix",
        displayName = "HELIX",
        tagline = "Two routes weave through each other three times before the rack, like a strand of DNA.",
        laneWaypoints = arrayOf(
            arrayOf(w(S, 150f), w(150f, 150f), w(330f, 610f), w(510f, 610f), w(690f, 150f),
                w(870f, 150f), w(1050f, 610f), w(1190f, 610f), w(1190f, C), w(R, C)),
            arrayOf(w(S, 610f), w(150f, 610f), w(330f, 150f), w(510f, 150f), w(690f, 610f),
                w(870f, 610f), w(1050f, 150f), w(1190f, 150f), w(1190f, C), w(R, C))
        ),
        candidateRows = DENSE_ROWS,
        unlockMapId = ZIGZAG.id,
        unlockMapName = ZIGZAG.displayName,
        unlockAtWave = 100,
        threatHealthScale = 5.6f,
        rewardScale = 1.5f,
        theme = theme(0xFF120807, 0xFF3A2020, 0xFF3A1E1C, 0xFF5A3634, 0xFFF0A898)
    )

    /** Level 10: layout 9B TRIPLE BRAID, colour D4 INDIGO. The hardest level. */
    val BRAID = GameMap(
        id = "braid",
        displayName = "TRIPLE BRAID",
        tagline = "Three routes braid through each other twice: every lane changes side.",
        laneWaypoints = arrayOf(
            arrayOf(w(S, 120f), w(300f, 120f), w(500f, C), w(700f, C), w(900f, 640f), w(1150f, 640f), w(1190f, C), w(R, C)),
            arrayOf(w(S, C), w(300f, C), w(500f, 640f), w(700f, 640f), w(900f, 120f), w(1150f, 120f), w(1190f, C), w(R, C)),
            arrayOf(w(S, 640f), w(300f, 640f), w(500f, 120f), w(700f, 120f), w(900f, C), w(1190f, C), w(R, C))
        ),
        candidateRows = DENSE_ROWS,
        unlockMapId = HELIX.id,
        unlockMapName = HELIX.displayName,
        unlockAtWave = 100,
        threatHealthScale = 5.8f,
        rewardScale = 1.55f,
        theme = theme(0xFF06051A, 0xFF16124A, 0xFF1A1552, 0xFF3A30A0, 0xFF7F74F0)
    )

    val all: List<GameMap> = listOf(PERIMETER, HUGGING_FACE, NEURAL_MESH, DUCK_USB, DDOS,
        TRIDENT, SPIRAL, ZIGZAG, HELIX, BRAID)

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
