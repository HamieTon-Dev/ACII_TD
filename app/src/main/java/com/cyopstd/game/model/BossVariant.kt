package com.cyopstd.game.model

/**
 * Which kind of boss this is.
 *
 * Until 1.21.0 there was exactly one boss in the game — `EnemyType.BOSS`,
 * glyph `[!!!]` — and every boss anyone ever fought was that same thing with a
 * different set of [BossModifier]s rolled onto it. Modifiers make a boss
 * *harder*; they do not make it a different opponent, and a player who has
 * cleared wave 60 has seen `[!!!]` a dozen times.
 *
 * A variant is the opponent's identity: its own glyph, its own weighting of
 * health, armour and speed, and where it is allowed to show up. Everything
 * else — the modifier roll, the rewards, the routes — is unchanged and applies
 * on top, so a `[GG]` on cycle 6 is still a `[GG]` and still rolls its two
 * modifiers.
 *
 * Adding one is a row in this table. That is deliberate: the backlog has
 * several more waiting on the owner to choose, and two AI bosses that must
 * exist before the second map can.
 */
/**
 * What a counter is worth. The owner's answer to C2: *"2x damage for both."*
 *
 * Flat rather than a curve, so a player can reason about it without a
 * spreadsheet: the right hat against the right boss hits twice as hard.
 */
const val COUNTER_MULTIPLIER = 2f

/**
 * The map the four extra bosses belong to, by `GameMap.id`.
 *
 * A literal, because the model layer does not depend on the map catalog.
 * `BossVariantTest` asserts it matches `Maps.HUGGING_FACE.id`.
 */
private const val HUGGING_FACE_MAP_ID = "hugging_face"

/** The owner's number for the two heavies: *"maybe 1.2x more"* health. */
private const val HEAVY_HEALTH_SCALE = 1.2f

/**
 * How a boss is lit.
 *
 * The model has no business holding ARGB values — it never has — so the table
 * names a *theme* and the renderer decides what that looks like. The owner's
 * ask for the HUGGING-FACE bosses was "a cool color theme", which is a
 * statement about the whole family rather than about four hex codes, and this
 * is the shape that keeps it that way.
 */
enum class BossPalette {
    /** Red, the way every boss has looked since 1.0. */
    HOSTILE,

    /** Cycles rapidly through the cool end of the spectrum. */
    SPECTRUM,

    /** Steady cyan. */
    ICE,

    /** Steady violet. */
    VIOLET
}

enum class BossVariant(
    /** Stable id, for saves and for the leaderboard. */
    val id: String,
    val displayName: String,
    /** Drawn in place of the type's glyph. */
    val glyph: String,
    val healthScale: Float,
    val armorBonus: Float,
    val speedScale: Float,
    /** One line for the dossier panel and the codex. */
    val signature: String,
    /** The earliest boss cycle this may appear on. */
    val firstCycle: Int,
    /**
     * Agents that hit this variant harder, and by how much.
     *
     * Keyed by `AgentType.name` rather than by the enum itself, deliberately.
     * Two enums that name each other in their constructors can deadlock during
     * class initialisation, and the failure is a hang at startup with no stack
     * worth reading. A string key cannot do that.
     *
     * A table rather than a branch in the damage path, so a new counter is a
     * row here and nothing else changes.
     */
    val bonusDamageFrom: Map<String, Float> = emptyMap(),

    /**
     * The map this opponent belongs to, by `GameMap.id`, or null for one that
     * turns up everywhere.
     *
     * A string rather than the map object for the same reason
     * [bonusDamageFrom] is keyed by name: this is the model layer, and it does
     * not get to depend on the map catalog. `BossVariantTest` checks the id
     * against `Maps.HUGGING_FACE.id`, so a typo here fails a test rather than
     * quietly retiring four bosses.
     */
    val mapId: String? = null,

    /**
     * The one agent this boss jams, by `AgentType.name`, or null for none.
     *
     * Deliberately singular. The owner was explicit: *"they both cannot do
     * both, only the type I specified."* A set would allow the thing the
     * design rules out, and a field that can express an illegal state is a
     * field that eventually holds one.
     */
    val jamsAgentType: String? = null,

    /** How this one is lit. */
    val palette: BossPalette = BossPalette.HOSTILE
) {

    /**
     * The original, and still the one a player meets first.
     *
     * Cycle 1 is deliberately a plain fight with no modifiers, so it stays a
     * plain fight against a plain opponent too.
     */
    BREACH(
        id = "breach",
        displayName = "BREACH",
        glyph = "[!!!]",
        healthScale = 1f,
        armorBonus = 0f,
        speedScale = 1f,
        signature = "A coordinated breach attempt. No tricks, just weight.",
        firstCycle = 1,
        // BLUE HAT is the defensive counter: a breach is what a blue team is
        // for.
        bonusDamageFrom = mapOf("BLUEHAT" to COUNTER_MULTIPLIER)
    ),

    /**
     * The wall.
     *
     * Slow, enormous, and armoured past what raw damage can chew through — it
     * is the fight that asks whether you built anything that ignores armour.
     */
    GOOD_GAME(
        id = "gg",
        displayName = "GOOD GAME",
        glyph = "[GG]",
        healthScale = 1.6f,
        armorBonus = 7f,
        speedScale = 0.78f,
        signature = "Enormous and heavily armoured, but slow. Bring something " +
            "that ignores armour.",
        firstCycle = 2,
        // RED HAT is the offensive counter: GOOD GAME is a wall, and the way
        // past a wall is to go at it rather than wait it out.
        bonusDamageFrom = mapOf("REDHAT" to COUNTER_MULTIPLIER)
    ),

    /**
     * The one that gets back up.
     *
     * Comes back once at [ZOMBIE_REVIVE_FRACTION] of its health the first time
     * it is killed, which punishes a board that can only just manage a single
     * kill and rewards one with something left in reserve.
     */
    ZOMBIE(
        id = "zz",
        displayName = "ZOMBIE",
        glyph = "[ZZ]",
        healthScale = 0.85f,
        armorBonus = 0f,
        speedScale = 1.12f,
        signature = "Gets back up once, at 40% health. Killing it is not the " +
            "same as finishing it.",
        firstCycle = 3
    ),

    /**
     * HUGGING-FACE, the pair that reads the board before it hits it.
     *
     * The gauntlet is the map the hats were built for — four RED HAT and four
     * BLUE HAT, highest fire rate in the game, always shooting the boss first.
     * A map whose answer to every boss is the same eight agents does not have
     * a boss fight, it has a formality. The eyes take one of those two answers
     * away for [VARIANT_JAM_SECONDS] out of every [VARIANT_JAM_INTERVAL], and
     * only within [VARIANT_JAM_RADIUS] — far inside a hat's range, so a hat
     * posted wide still fires and a hat squeezed in beside the route does not.
     * Placement is the counterplay.
     *
     * Neither jams both. That is the owner's rule, and it is also what makes
     * the pair interesting: whichever hat is jammed, the other one is the
     * wrong colour for the fight.
     *
     * From cycle 6 — wave 30, the wave the hats unlock on. Jamming an agent
     * the player cannot own yet is not difficulty, it is nothing at all.
     */
    WHITE_EYE(
        id = "white_eye",
        displayName = "WHITE EYE",
        glyph = "[\u25CB_\u25CB]",
        healthScale = 1f,
        armorBonus = 0f,
        speedScale = 1f,
        signature = "Watches for offensive tooling. Jams RED HAT agents close " +
            "to it, briefly, every few seconds.",
        firstCycle = 6,
        mapId = HUGGING_FACE_MAP_ID,
        jamsAgentType = "REDHAT",
        palette = BossPalette.SPECTRUM
    ),

    /** The other eye: the same fight with the colours swapped. */
    BLACK_EYE(
        id = "black_eye",
        displayName = "BLACK EYE",
        glyph = "[\u25CF_\u25CF]",
        healthScale = 1f,
        armorBonus = 0f,
        speedScale = 1f,
        signature = "Watches for defensive tooling. Jams BLUE HAT agents close " +
            "to it, briefly, every few seconds.",
        firstCycle = 6,
        mapId = HUGGING_FACE_MAP_ID,
        jamsAgentType = "BLUEHAT",
        palette = BossPalette.SPECTRUM
    ),

    /**
     * The other HUGGING-FACE family: no trick at all, just more of it.
     *
     * The owner's spec was one line — *"these will not do jam to hat agents,
     * just make them higher than normal health (maybe 1.2x more)"* — and there
     * is nothing to add to it. Every boss wave on the gauntlet that is not an
     * eye is a straight damage check, which is what makes the eyes land: the
     * player cannot know which of the two problems is walking out of the
     * spawn until it does.
     */
    BOUNTY(
        id = "bounty",
        displayName = "BOUNTY",
        glyph = "[\u20A9\u20A9\u20A9]",
        healthScale = HEAVY_HEALTH_SCALE,
        armorBonus = 0f,
        speedScale = 0.95f,
        signature = "Paid to be here. No tricks, no jamming \u2014 twenty per " +
            "cent more of it than anything else on the board.",
        firstCycle = 2,
        mapId = HUGGING_FACE_MAP_ID,
        palette = BossPalette.ICE
    ),

    /** Its twin, and the reason a heavy is never a safe read. */
    PAYOUT(
        id = "payout",
        displayName = "PAYOUT",
        glyph = "[\u00A5\u00A5\u00A5]",
        healthScale = HEAVY_HEALTH_SCALE,
        armorBonus = 0f,
        speedScale = 0.95f,
        signature = "The collection run. No tricks, no jamming \u2014 twenty " +
            "per cent more of it than anything else on the board.",
        firstCycle = 2,
        mapId = HUGGING_FACE_MAP_ID,
        palette = BossPalette.VIOLET
    );

    companion object {
        /** How much health a ZOMBIE comes back with. */
        const val ZOMBIE_REVIVE_FRACTION = 0.4f

        /** Seconds between one variant jam and the next. The owner's number. */
        const val VARIANT_JAM_INTERVAL = 5f

        /** How long a variant jam holds. The owner's number: *"for 2 seconds."* */
        const val VARIANT_JAM_SECONDS = 2f

        /**
         * How far a variant jam reaches, in world units. The owner's number.
         *
         * Far under a hat's 272-unit range, and that is what makes it a
         * decision rather than a tax. A node sits at least `NODE_CLEARANCE`
         * from the route, so a hat squeezed in beside the lane — the greedy
         * placement, the one that maximises time on target — is inside this
         * and spends two seconds in five jammed. A hat posted back at the edge
         * of its own reach still fires through the entire fight.
         */
        const val VARIANT_JAM_RADIUS = 100f

        fun fromIdSafe(id: String?): BossVariant =
            entries.firstOrNull { it.id == id } ?: BREACH

        /**
         * The variants a boss on [cycle] may roll.
         *
         * Introduced one at a time, like the modifiers are: a player meets a
         * new opponent on its own rather than three at once, and cycle 1 is
         * always the plain fight.
         */
        fun poolForCycle(cycle: Int): List<BossVariant> = poolFor(cycle, null)

        /**
         * The variants a boss on [cycle] of the map [mapId] may roll.
         *
         * A map's own opponents are added to the common pool rather than
         * replacing it, so the gauntlet still fields [BREACH], [GOOD_GAME] and
         * [ZOMBIE] and the map-specific four are *extra* identities rather
         * than a separate game.
         */
        fun poolFor(cycle: Int, mapId: String?): List<BossVariant> =
            entries.filter {
                cycle >= it.firstCycle && (it.mapId == null || it.mapId == mapId)
            }
    }
}
