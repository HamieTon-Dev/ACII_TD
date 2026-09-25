package com.cyopstd.game.model

import com.cyopstd.game.core.Balance

/**
 * How an agent picks its victim. FIRST (closest to the server) is the default
 * and the only mode simple agents expose; advanced agents let the player switch.
 */
enum class TargetingMode(val label: String, val description: String) {
    FIRST("FIRST", "Closest to CORE-SERVER"),
    LAST("LAST", "Furthest from CORE-SERVER"),
    STRONGEST("STRONGEST", "Highest remaining health"),
    WEAKEST("WEAKEST", "Lowest remaining health");

    companion object {
        fun fromOrdinalSafe(ordinal: Int): TargetingMode =
            entries.getOrElse(ordinal) { FIRST }
    }
}

/** The visual signature an agent's attack leaves on the battlefield. */
enum class AttackStyle(val trail: String) {
    BOLT("--->"),
    SCAN("~~>"),
    BURST(">>>>"),
    PRECISION("*--->"),
    THROTTLE("~->"),
    CIPHER("{==>}"),
    HUNTER("-=>"),
    SENTINEL(":::>"),
    QUANTUM("<#>"),
    ROOT("###>"),
    ARCHITECT("+-+")
}

/**
 * A deployable cyber agent. Base stats are level-1 values; [statsAtLevel] applies
 * the shared upgrade curve from [Balance].
 */
/** The fastest rate anything in the roster fires at. IPS set it; the hats match it. */
private const val HIGHEST_FIRE_RATE = 3.3f

/** How many of each hat agent may stand on the board at once. */
private const val MAX_HATS = 4

enum class AgentType(
    val displayName: String,
    val shortName: String,
    val glyph: String,
    val cost: Int,
    val baseDamage: Float,
    /** Attacks per second. */
    val baseFireRate: Float,
    /** Radius in world units. */
    val baseRange: Float,
    val attackStyle: AttackStyle,
    /** Wave the player must reach (in any run) before this agent can be bought. */
    val unlockWave: Int,
    val abilityName: String,
    val abilitySummary: String,
    /**
     * What the real cybersecurity concept is, with no gameplay in it.
     *
     * Kept strictly separate from [inGame] because the two were blended and
     * the blend taught the wrong thing: "An Intrusion Detection System watches
     * network activity for suspicious behaviour and raises the alarm. It sees
     * further than anything else you can deploy." The first sentence is true
     * of the world; the second is true only of this game, and a beginner has
     * no way to tell which is which.
     *
     * Rules these lines are written to, from the owner's brief: beginner
     * friendly, no exaggeration of real roles, no offensive procedure, and no
     * informal term presented as an official certification or standardised job
     * title. Where a term genuinely is ambiguous or contested in the industry,
     * the line says so rather than picking a definition and asserting it.
     */
    val realWorld: String,
    /** What the fictional agent actually does on the board. */
    val inGame: String,
    /** Advanced agents expose the targeting selector. */
    val allowsTargetingModes: Boolean = false,
    /**
     * Cannot be jammed.
     *
     * JAM (`BossModifier.AGENT_DISRUPTION`) halves an agent's fire rate for
     * three seconds, and it is the only debuff in the game that lands on the
     * defender. Immunity is therefore a real identity rather than a stat, and
     * it belongs to the agent that has no choice but to stand close enough to
     * be jammed in the first place.
     */
    val immuneToJam: Boolean = false,
    /**
     * Radius of the collateral damage each shot deals, or zero for none.
     *
     * The answer to a swarm. Splash exists because every cheap agent in the
     * roster killed one thing at a time, which made a pack of BOTs a test of
     * how many turrets you had rather than of what you built.
     */
    val splashRadius: Float = 0f,
    /**
     * Always shoots a boss in range before anything else.
     *
     * Ordinary agents prioritise bosses only in FIRST and STRONGEST modes;
     * this overrides the mode entirely. It is the identity of the two hat
     * agents and the reason they exist: past wave 100 the board carries four
     * or more bosses at once, and every other agent picks its target by
     * *position* along the lane, so a swarm of ordinary packets walking in
     * front of a boss pulls the whole roster off it.
     */
    val alwaysPrioritisesBosses: Boolean = false,
    /**
     * How many of this agent may be on the board at once. Zero means no cap.
     *
     * A cap rather than a price is what keeps a boss-seeker from becoming the
     * only thing anybody builds: at four, it answers a four-boss wave and
     * cannot answer everything else as well.
     */
    val maxDeployed: Int = 0
) {
    TARPIT(
        displayName = "TARPIT",
        shortName = "TARPIT",
        glyph = "~",
        cost = 20,
        // A token amount. What you buy is the field, not the output.
        baseDamage = 1.2f,
        baseFireRate = 1.6f,
        baseRange = 300f,
        attackStyle = AttackStyle.THROTTLE,
        unlockWave = 0,
        abilityName = "RATE LIMIT",
        abilitySummary = "Cheap and nearly harmless. Everything inside its radius crawls.",
        realWorld = "A tarpit accepts a hostile connection and then answers as " +
            "slowly as it is allowed to, holding the attacker open on a socket " +
            "that is going nowhere. It is a delaying tactic, not a blocking " +
            "one: the value is the time it costs the other side.",
        inGame = "Deals almost no damage. Everything inside its radius crawls, " +
            "which keeps threats in range of your damage dealers for far " +
            "longer. Cheap, and useless on its own — it is a force multiplier " +
            "for whatever is shooting next to it."
    ),
    FIREWALL(
        displayName = "FIREWALL",
        shortName = "FIREWALL",
        glyph = "F",
        cost = 40,
        baseDamage = 9f,
        baseFireRate = 1.15f,
        baseRange = 195f,
        attackStyle = AttackStyle.BOLT,
        unlockWave = 0,
        abilityName = "HARDENED",
        abilitySummary = "Reliable all-round damage, and it cannot be jammed.",
        realWorld = "A firewall checks traffic against a set of rules and drops " +
            "anything the rules do not allow. It is usually the first control " +
            "placed between an untrusted network and something worth " +
            "protecting, and it is judged on the rules it is given rather than " +
            "on cleverness.",
        inGame = "Reliable mid-range damage at a low price, and the only agent " +
            "immune to a boss JAM. Short reach, so it has to stand close to the " +
            "lane — which is exactly why that immunity matters.",
        // The agent with the shortest reach has no choice but to stand where a
        // boss can jam it, so immunity is the identity that makes standing
        // there worth doing.
        immuneToJam = true
    ),
    IDS(
        displayName = "IDS",
        shortName = "IDS",
        glyph = "I",
        cost = 55,
        baseDamage = 9f,
        baseFireRate = 1.0f,
        baseRange = 330f,
        attackStyle = AttackStyle.SCAN,
        unlockWave = 0,
        abilityName = "DEEP SCAN",
        abilitySummary = "Very long detection range; +45% damage to fast attacks.",
        realWorld = "An Intrusion Detection System watches network or host " +
            "activity for patterns that look like an attack and raises an " +
            "alert. It reports; it does not block. Someone, or something else, " +
            "still has to act on what it finds.",
        inGame = "The longest detection range in the roster, and +45% damage " +
            "against fast attacks. Slow rate of fire — it is a spotter that " +
            "happens to shoot, best placed where it can cover a lot of lane."
    ),
    IPS(
        displayName = "IPS",
        shortName = "IPS",
        glyph = "P",
        cost = 70,
        baseDamage = 5.4f,
        baseFireRate = 3.3f,
        baseRange = 260f,
        attackStyle = AttackStyle.BURST,
        unlockWave = 3,
        abilityName = "BLAST RADIUS",
        abilitySummary = "Extremely high rate of fire, and every shot splashes.",
        realWorld = "An Intrusion Prevention System sits in the traffic path and " +
            "is permitted to act on what it detects, dropping or resetting a " +
            "connection rather than only alerting. The trade-off is real: an " +
            "IPS that misjudges legitimate traffic blocks it.",
        inGame = "Very high rate of fire and every shot splashes, so it is the " +
            "answer to a swarm rather than to a single heavy target. Costs " +
            "more than an IDS and reaches less far.",
        // IPS was the roster's outlier: dearer than IDS and reaching 120 units
        // less, with an identity nobody could feel next to FIREWALL. Splash is
        // the job no other cheap agent does -- the answer to a swarm.
        splashRadius = 78f
    ),
    ANALYST(
        displayName = "ANALYST",
        shortName = "ANALYST",
        glyph = "A",
        cost = 95,
        baseDamage = 38f,
        baseFireRate = 0.62f,
        baseRange = 268f,
        attackStyle = AttackStyle.PRECISION,
        unlockWave = 5,
        abilityName = "THREAT ASSESSMENT",
        abilitySummary = "Slow, heavy hits. +80% damage to elites and bosses.",
        realWorld = "Security analyst is a real job. The human in the loop takes " +
            "what the automated tools flagged, works out what actually " +
            "happened, and decides what to do about it. Slower than a machine, " +
            "and far better on the cases that do not match a known pattern.",
        inGame = "Slow, heavy single hits with +80% damage against elites and " +
            "bosses. Poor against swarms. Exposes the targeting selector, so " +
            "you can aim it at the thing you actually want dead.",
        allowsTargetingModes = true
    ),
    CRYPTOGRAPHER(
        displayName = "CRYPTOGRAPHER",
        shortName = "CRYPTO",
        glyph = "C",
        cost = 105,
        baseDamage = 26f,
        baseFireRate = 1.05f,
        baseRange = 276f,
        attackStyle = AttackStyle.CIPHER,
        unlockWave = 10,
        abilityName = "CIPHER BREAK",
        abilitySummary = "Ignores encryption entirely and deals triple damage to it.",
        realWorld = "Cryptography protects data in transit and at rest, so that " +
            "intercepting it is not the same as reading it. Attackers use it " +
            "too — a lot of hostile traffic is encrypted — which is why " +
            "defenders inspect at endpoints they control rather than trying to " +
            "break the encryption itself.",
        inGame = "Fictional licence, clearly flagged: this agent simply ignores " +
            "the ENCRYPTED trait instead of being slowed by it, and deals " +
            "triple damage to anything carrying it. Real cryptography does not " +
            "work this way and nothing here breaks any real cipher."
    ),
    ZERO_DAY_HUNTER(
        displayName = "ZERO-DAY HUNTER",
        shortName = "HUNTER",
        glyph = "Z",
        cost = 150,
        baseDamage = 42f,
        baseFireRate = 1.15f,
        baseRange = 284f,
        attackStyle = AttackStyle.HUNTER,
        unlockWave = 15,
        abilityName = "ZERO-DAY STRIKE",
        abilitySummary = "Heavy single-target damage, identical every shot. " +
            "Ignores all armour.",
        realWorld = "A zero-day is a flaw with no fix available yet — the " +
            "defender has had zero days to patch it. Vulnerability researchers " +
            "look for these so they can be reported and fixed before someone " +
            "else finds them first. \"Zero-day hunter\" is informal shorthand, " +
            "not a certification or a formal job title.",
        inGame = "Heavy single-target damage, identical on every shot rather " +
            "than a gamble, and it ignores armour. Expensive and slow; one of " +
            "these does not hold a lane on its own.",
        allowsTargetingModes = true
    ),
    AI_SENTINEL(
        displayName = "AI SENTINEL",
        shortName = "SENTINEL",
        glyph = "@",
        cost = 185,
        baseDamage = 22f,
        baseFireRate = 1.5f,
        baseRange = 292f,
        attackStyle = AttackStyle.SENTINEL,
        unlockWave = 20,
        abilityName = "MULTI-LOCK",
        abilitySummary = "Engages up to 3 separate threats with every volley.",
        realWorld = "Machine-learning tools flag unusual patterns across far more " +
            "signals than a person can read, and are used to narrow down what " +
            "deserves attention. They assist analysts rather than replace them, " +
            "and they produce false positives — a flag is a lead, not a verdict.",
        inGame = "Engages up to three separate threats with every volley, which " +
            "makes it the roster's answer to a wide wave. Exposes the targeting " +
            "selector.",
        allowsTargetingModes = true
    ),
    QUANTUM_DEFENDER(
        displayName = "QUANTUM DEFENDER",
        shortName = "QUANTUM",
        glyph = "Q",
        cost = 240,
        baseDamage = 60f,
        baseFireRate = 1.05f,
        baseRange = 300f,
        attackStyle = AttackStyle.QUANTUM,
        unlockWave = 30,
        abilityName = "ENTANGLED CHAIN",
        abilitySummary = "Each hit chains to 2 nearby threats for 55% damage.",
        realWorld = "Two different things get called \"quantum\" in security and " +
            "they are worth keeping apart. Quantum key distribution uses " +
            "physics to detect eavesdropping on a link, and is deployed only in " +
            "rare, specialised settings. Post-quantum cryptography is ordinary " +
            "software maths designed to resist future quantum computers, and is " +
            "the one actually being rolled out.",
        inGame = "Named after the first and firmly science fiction: each hit " +
            "chains to two nearby threats for 55% damage. Strong against " +
            "clustered waves, wasted on a lone target. Exposes the targeting " +
            "selector.",
        allowsTargetingModes = true
    ),
    ROOT_ADMIN(
        displayName = "ROOT ADMIN",
        shortName = "ROOT",
        glyph = "#",
        cost = 320,
        baseDamage = 122f,
        baseFireRate = 0.95f,
        baseRange = 316f,
        attackStyle = AttackStyle.ROOT,
        unlockWave = 40,
        abilityName = "SUDO TERMINATE",
        abilitySummary = "Overwhelming single-target damage. Ignores all armour.",
        realWorld = "Root, or administrator, is the highest level of authority on " +
            "a system: it can stop any process and change anything. Precisely " +
            "because of that, good practice is to hand it out as rarely as " +
            "possible and for as short a time as possible. \"Root admin\" is " +
            "shorthand for the access level, not a job title.",
        inGame = "Overwhelming single-target damage that ignores all armour. The " +
            "most expensive agent in the game and unlocked very late. Exposes " +
            "the targeting selector.",
        allowsTargetingModes = true
    ),
    /**
     * The boss-seekers. Late, expensive, capped, and deliberately narrow.
     *
     * Added because the owner tested the wall and named it precisely: *"wave
     * 101 is impossible with the best build on the map"*. The reason is
     * structural rather than a numbers problem — every other agent in the
     * roster chooses its target by position along the lane, so four bosses
     * arriving inside a swarm are the *last* things anything shoots at. No
     * amount of damage fixes a targeting problem.
     *
     * The colour split is the "hat" convention from security, and the
     * educational text says so rather than implying either is a job title.
     */
    REDHAT(
        displayName = "RED HAT",
        shortName = "REDHAT",
        glyph = "R",
        cost = 400,
        baseDamage = 34f,
        baseFireRate = HIGHEST_FIRE_RATE,
        baseRange = 272f,
        attackStyle = AttackStyle.PRECISION,
        unlockWave = 30,
        abilityName = "OFFENSIVE SWEEP",
        abilitySummary = "Fires as fast as anything in the roster and always " +
            "shoots a boss first. Double damage to [GG] GOOD GAME. Four maximum.",
        realWorld = "A \"red team\" attacks a system with permission, to find what " +
            "an attacker would find first. \"Red hat\" is informal slang " +
            "rather than a job title or a certification, and its meaning varies " +
            "between people who use it; the red team half is the part that is " +
            "standard.",
        inGame = "Very high rate of fire, always targets bosses over anything " +
            "else in range, and deals double damage to [GG] GOOD GAME. " +
            "Unlocks at wave 30 and no more than four may be deployed.",
        allowsTargetingModes = false,
        alwaysPrioritisesBosses = true,
        maxDeployed = MAX_HATS
    ),
    BLUEHAT(
        displayName = "BLUE HAT",
        shortName = "BLUEHAT",
        glyph = "B",
        cost = 400,
        baseDamage = 34f,
        baseFireRate = HIGHEST_FIRE_RATE,
        baseRange = 272f,
        attackStyle = AttackStyle.SENTINEL,
        unlockWave = 30,
        abilityName = "DEFENSIVE SWEEP",
        abilitySummary = "Fires as fast as anything in the roster and always " +
            "shoots a boss first. Double damage to [!!!] BREACH. Four maximum.",
        realWorld = "A \"blue team\" defends a system and responds to incidents — " +
            "the counterpart to a red team. \"Blue hat\" is used in more than one " +
            "way in the industry and is not a standardised term, so only the " +
            "blue team meaning is described here.",
        inGame = "Very high rate of fire, always targets bosses over anything " +
            "else in range, and deals double damage to [!!!] BREACH. " +
            "Unlocks at wave 30 and no more than four may be deployed.",
        allowsTargetingModes = false,
        alwaysPrioritisesBosses = true,
        maxDeployed = MAX_HATS
    ),
    NETWORK_ARCHITECT(
        displayName = "NETWORK ARCHITECT",
        shortName = "ARCHITECT",
        glyph = "N",
        cost = 210,
        baseDamage = 16f,
        baseFireRate = 0.8f,
        baseRange = 296f,
        attackStyle = AttackStyle.ARCHITECT,
        unlockWave = 50,
        abilityName = "SEGMENT UPLINK",
        abilitySummary = "Buffs every agent in range: +30% damage, +20% fire rate.",
        realWorld = "Network architects design how a network is divided up. " +
            "Segmentation means a compromise in one area cannot simply walk " +
            "into the next, which limits the damage of any single failure. It " +
            "is design work done before an incident, not a tool used during one.",
        inGame = "Deals no damage of its own. Buffs every agent in range by +30% " +
            "damage and +20% fire rate, so its value is entirely in where you " +
            "put it."
    );

    /** Agents available from the very first run. */
    val unlockedByDefault: Boolean get() = unlockWave <= 0

    fun statsAtLevel(level: Int): AgentStats {
        val steps = (level - 1).coerceIn(0, Balance.MAX_AGENT_LEVEL - 1)
        return AgentStats(
            damage = baseDamage * (1f + Balance.UPGRADE_DAMAGE_GROWTH * steps),
            fireRate = baseFireRate * (1f + Balance.UPGRADE_RATE_GROWTH * steps),
            // Range climbs in five-level steps and stops at a ceiling; see
            // Balance.rangeMultiplier for why it is shaped that way.
            range = baseRange * Balance.rangeMultiplier(level)
        )
    }

    /**
     * Glyph decoration that makes progress visible on the battlefield across a
     * hundred levels:
     *
     * `[F]` -> `[F+]` -> `[F++]` -> `[F#]` -> `[F##]` -> `[F*]` -> `[F**]` -> `[F***]`
     *
     * Eight tiers rather than one per level, so the board stays readable while
     * still telling you at a glance which agent you have been feeding.
     */
    fun renderedGlyph(level: Int): String = when {
        level >= 80 -> "[$glyph***]"
        level >= 60 -> "[$glyph**]"
        level >= 40 -> "[$glyph*]"
        level >= 25 -> "[$glyph##]"
        level >= 15 -> "[$glyph#]"
        level >= 8 -> "[$glyph++]"
        level >= 3 -> "[$glyph+]"
        else -> "[$glyph]"
    }

    fun upgradeCost(level: Int): Int = Balance.upgradeCost(cost, level)

    fun sellValue(level: Int): Int = Balance.sellValue(cost, level)

    companion object {
        val starters: List<AgentType> = entries.filter { it.unlockedByDefault }

        /** Catalog order used by the deployment panel and the AGENTS screen. */
        val catalog: List<AgentType> = entries.sortedBy { it.unlockWave }

        fun fromNameSafe(name: String?): AgentType? =
            entries.firstOrNull { it.name == name }
    }
}

data class AgentStats(
    val damage: Float,
    val fireRate: Float,
    val range: Float
) {
    /** Seconds between shots. */
    val cooldown: Float get() = if (fireRate <= 0f) Float.MAX_VALUE else 1f / fireRate
}
