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

/** Map 3. `BossVariantTest` asserts it matches `Maps.NEURAL_MESH.id`. */
private const val NEURAL_MESH_MAP_ID = "neural_mesh"

/** Map 4. `BossVariantTest`-style checks assert it matches `Maps.DUCK_USB.id`. */
private const val DUCK_USB_MAP_ID = "duck_usb"

/** Map 5. `DdosTest` asserts it matches `Maps.DDOS.id`. */
private const val DDOS_MAP_ID = "ddos"

/**
 * The levels in the order they are unlocked, by `GameMap.id`.
 *
 * The owner's rule for new bosses: *"two per map, all previous bosses will be
 * included in subsequent maps stacking variants of bosses."* So a map fields
 * its own bosses and every earlier map's. `BossVariantTest` asserts this
 * matches `Maps.all`.
 */
val MAP_PROGRESSION: List<String> = listOf("perimeter", HUGGING_FACE_MAP_ID, NEURAL_MESH_MAP_ID, DUCK_USB_MAP_ID, DDOS_MAP_ID)

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
    ),

    /**
     * NEURAL-MESH, the pair that punishes a lazy board (backlog §J).
     *
     * MODEL COLLAPSE: *"heals while three or more agents hit it at once —
     * punishes blobbing."* A model trained on too much of the same signal
     * degrades; this one feeds on it. While [COLLAPSE_SWARM] or more different
     * agents have hit it inside [COLLAPSE_WINDOW] seconds, it heals back part
     * of every hit: [COLLAPSE_HEAL_PER_AGENT] for each agent beyond two, up to
     * [COLLAPSE_MAX_HEAL]. Scaled rather than flat because a flat heal
     * punished four ROOT ADMINs exactly as hard as twelve IPS, and the whole
     * point is the blob. AI SENTINEL's three bolts are one agent.
     */
    MODEL_COLLAPSE(
        id = "model_collapse",
        displayName = "MODEL COLLAPSE",
        glyph = "[\u2206\u2206\u2206]",
        healthScale = 1.1f,
        armorBonus = 0f,
        speedScale = 0.95f,
        signature = "Heals back part of every hit while three or more agents " +
            "hit it at once, more the bigger the crowd. Fewer, heavier hitters " +
            "starve it.",
        firstCycle = 3,
        mapId = NEURAL_MESH_MAP_ID,
        palette = BossPalette.SPECTRUM
    ),

    /**
     * LICENSE: *"takes less damage from any agent type that already hit it —
     * forces a varied board."* Every hit from an agent type makes the next
     * hit from that type weaker, down to [LICENSE_FLOOR]. The count is per
     * type, not per agent, so ten FIREWALLs wear out their welcome ten times
     * as fast as one FIREWALL, one IDS and one ANALYST do.
     */
    LICENSE(
        id = "license",
        displayName = "LICENSE",
        glyph = "[\u00A9\u00A9\u00A9]",
        healthScale = 1f,
        armorBonus = 2f,
        speedScale = 1f,
        signature = "Shrugs off any agent type that keeps hitting it. A varied " +
            "board keeps hurting it.",
        firstCycle = 4,
        mapId = NEURAL_MESH_MAP_ID,
        palette = BossPalette.ICE
    ),

    /**
     * 🦆 DUCK-USB, the pair that changes shape mid-fight (backlog §J).
     *
     * SYN-STORM: *"splits into two half-health bosses at 50% — turns one lane
     * problem into two."* At half health it splits: the original carries on
     * and a copy with the same health appears on another route, at the same
     * distance along it. Neither half splits again, and each is worth half the
     * reward, so a split is never a farm.
     */
    SYN_STORM(
        id = "syn_storm",
        displayName = "SYN-STORM",
        glyph = "[SS]",
        healthScale = 1f,
        armorBonus = 0f,
        speedScale = 1.05f,
        signature = "Splits in two at half health, and the second half takes " +
            "another route.",
        firstCycle = 3,
        mapId = DUCK_USB_MAP_ID,
        palette = BossPalette.SPECTRUM
    ),

    /**
     * GRADIENT: *"speeds up as it takes damage, slows when untouched —
     * rewards burst over chip damage."* Every hit adds the same momentum,
     * however hard it lands, and momentum bleeds away when nothing is hitting
     * it. So a board of many small, fast hits drives it up to [GRADIENT_FAST],
     * and a few heavy hitters leave it near [GRADIENT_SLOW]. Measured on hits
     * rather than damage on purpose: measured on damage, every board that
     * was winning made it faster, which rewarded nothing.
     */
    GRADIENT(
        id = "gradient",
        displayName = "GRADIENT",
        glyph = "[\u2211\u2211\u2211]",
        healthScale = 1.1f,
        armorBonus = 0f,
        speedScale = 1f,
        signature = "Every hit speeds it up, however small; it slows when left " +
            "alone. Few big hits beat many small ones.",
        firstCycle = 4,
        mapId = DUCK_USB_MAP_ID,
        palette = BossPalette.VIOLET
    ),

    /**
     * DDoS, the pair that attacks something other than the wall (backlog §J).
     *
     * RANSOM: *"locks a random agent's upgrades for 8s — attacks the
     * economy, not the wall."* Every [RANSOM_INTERVAL] seconds while it is on
     * the board, one agent that is not already locked has its upgrades held
     * for [RANSOM_SECONDS]. It still fires; it just cannot be levelled or
     * rescued by money in the middle of the fight.
     */
    RANSOM(
        id = "ransom",
        displayName = "RANSOM",
        glyph = "[RM]",
        healthScale = 1.1f,
        armorBonus = 1f,
        speedScale = 1f,
        signature = "Every few seconds, locks one agent's upgrades for 8 " +
            "seconds. Upgrade before it arrives.",
        firstCycle = 3,
        mapId = DDOS_MAP_ID,
        palette = BossPalette.HOSTILE
    ),

    /**
     * EXFIL: *"steals in-run crypto on hit instead of integrity — a
     * different kind of loss."* If it reaches CORE-SERVER it takes
     * [EXFIL_STEAL] of the crypto in hand and no integrity at all. Quick on
     * its feet, so letting it through is a real choice with a real price.
     */
    EXFIL(
        id = "exfil",
        displayName = "EXFIL",
        glyph = "[XX]",
        healthScale = 0.9f,
        armorBonus = 0f,
        speedScale = 1.25f,
        signature = "Fast. If it reaches the core it steals half your crypto " +
            "instead of integrity.",
        firstCycle = 4,
        mapId = DDOS_MAP_ID,
        palette = BossPalette.SPECTRUM
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

        /** MODEL COLLAPSE feeds while this many different agents hit it... */
        const val COLLAPSE_SWARM = 3

        /** ...within this many seconds of each other... */
        const val COLLAPSE_WINDOW = 1f

        /** ...healing back this much of each hit per agent beyond two... */
        const val COLLAPSE_HEAL_PER_AGENT = 0.08f

        /** ...up to this much. */
        const val COLLAPSE_MAX_HEAL = 0.6f

        /** The share of a hit MODEL COLLAPSE heals back with [attackers] on it. */
        fun collapseHealShare(attackers: Int): Float =
            if (attackers < COLLAPSE_SWARM) 0f
            else (COLLAPSE_HEAL_PER_AGENT * (attackers - 2)).coerceAtMost(COLLAPSE_MAX_HEAL)

        /** LICENSE: hits from one type before that type's damage is halved. */
        const val LICENSE_HALF_HITS = 25f

        /** LICENSE never takes less than this fraction of a hit. */
        const val LICENSE_FLOOR = 0.35f

        /** RANSOM: seconds between locks, and how long each lasts. */
        const val RANSOM_INTERVAL = 6f
        const val RANSOM_SECONDS = 8f

        /** EXFIL: the share of crypto in hand it takes on reaching the core. */
        const val EXFIL_STEAL = 0.5f

        /** SYN-STORM splits when its health falls to this fraction. */
        const val SYN_STORM_SPLIT_AT = 0.5f

        /** GRADIENT's speed with no momentum, and flat out. */
        const val GRADIENT_SLOW = 0.7f
        const val GRADIENT_FAST = 1.7f

        /** Momentum gained per hit, whatever its size. Ten hits a second holds it flat out. */
        const val GRADIENT_HEAT_PER_HIT = 0.04f

        /** Momentum lost a second. */
        const val GRADIENT_COOL_PER_SECOND = 0.4f

        fun gradientSpeed(heat: Float): Float =
            GRADIENT_SLOW + (GRADIENT_FAST - GRADIENT_SLOW) * heat.coerceIn(0f, 1f)

        /** LICENSE's multiplier for a type that has already landed [hits]. */
        fun licenseMultiplier(hits: Int): Float =
            (1f / (1f + hits / LICENSE_HALF_HITS)).coerceAtLeast(LICENSE_FLOOR)

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
        fun poolFor(cycle: Int, mapId: String?): List<BossVariant> {
            val here = MAP_PROGRESSION.indexOf(mapId).coerceAtLeast(0)
            return entries.filter {
                cycle >= it.firstCycle &&
                    (it.mapId == null || MAP_PROGRESSION.indexOf(it.mapId) in 0..here)
            }
        }
    }
}
