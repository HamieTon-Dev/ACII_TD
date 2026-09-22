package com.packetbastion.asciidefense.model

import com.packetbastion.asciidefense.core.Balance

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
    CONTAINMENT("[::]"),
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
    val codexEntry: String,
    /** Advanced agents expose the targeting selector. */
    val allowsTargetingModes: Boolean = false
) {
    FIREWALL(
        displayName = "FIREWALL",
        shortName = "FIREWALL",
        glyph = "F",
        cost = 40,
        baseDamage = 9f,
        baseFireRate = 1.15f,
        baseRange = 168f,
        attackStyle = AttackStyle.BOLT,
        unlockWave = 0,
        abilityName = "PACKET FILTER",
        abilitySummary = "Reliable all-round damage. No weaknesses, no tricks.",
        codexEntry = "A firewall inspects traffic against a rule set and drops " +
            "anything that does not belong. It is the first thing you put " +
            "between the outside world and anything you care about."
    ),
    IDS(
        displayName = "IDS",
        shortName = "IDS",
        glyph = "I",
        cost = 55,
        baseDamage = 7f,
        baseFireRate = 1.0f,
        baseRange = 268f,
        attackStyle = AttackStyle.SCAN,
        unlockWave = 0,
        abilityName = "DEEP SCAN",
        abilitySummary = "Very long detection range; +45% damage to fast packets.",
        codexEntry = "An Intrusion Detection System watches network activity for " +
            "suspicious behaviour and raises the alarm. It sees further than " +
            "anything else you can deploy."
    ),
    IPS(
        displayName = "IPS",
        shortName = "IPS",
        glyph = "P",
        cost = 70,
        baseDamage = 4.4f,
        baseFireRate = 3.3f,
        baseRange = 158f,
        attackStyle = AttackStyle.BURST,
        unlockWave = 3,
        abilityName = "RAPID BLOCK",
        abilitySummary = "Extremely high rate of fire. Shreds swarms.",
        codexEntry = "An Intrusion Prevention System is an IDS that is allowed to " +
            "act: instead of only reporting a threat it blocks the traffic " +
            "outright, and it does so continuously."
    ),
    ANALYST(
        displayName = "ANALYST",
        shortName = "ANALYST",
        glyph = "A",
        cost = 95,
        baseDamage = 30f,
        baseFireRate = 0.62f,
        baseRange = 200f,
        attackStyle = AttackStyle.PRECISION,
        unlockWave = 5,
        abilityName = "THREAT ASSESSMENT",
        abilitySummary = "Slow, heavy hits. +80% damage to elites and bosses.",
        codexEntry = "The human in the loop. A security analyst investigates what " +
            "the automated tools flagged and decides what it really is — slower " +
            "than a machine, far better against the hard cases.",
        allowsTargetingModes = true
    ),
    SANDBOX(
        displayName = "SANDBOX",
        shortName = "SANDBOX",
        glyph = "S",
        cost = 80,
        baseDamage = 3f,
        baseFireRate = 0.85f,
        baseRange = 186f,
        attackStyle = AttackStyle.CONTAINMENT,
        unlockWave = 8,
        abilityName = "DETONATION CHAMBER",
        abilitySummary = "Slows every packet it hits by up to 45% while analysing.",
        codexEntry = "A sandbox runs suspicious code in an isolated environment to " +
            "see what it does before it is allowed anywhere near production. " +
            "Anything under analysis is not going anywhere fast."
    ),
    CRYPTOGRAPHER(
        displayName = "CRYPTOGRAPHER",
        shortName = "CRYPTO",
        glyph = "C",
        cost = 105,
        baseDamage = 14f,
        baseFireRate = 1.05f,
        baseRange = 205f,
        attackStyle = AttackStyle.CIPHER,
        unlockWave = 10,
        abilityName = "CIPHER BREAK",
        abilitySummary = "Ignores encryption entirely and deals triple damage to it.",
        codexEntry = "Cryptography protects data in transit — and the same maths " +
            "used to protect a message is used to analyse a hostile one. " +
            "Encrypted payloads hold no secrets from this agent."
    ),
    ZERO_DAY_HUNTER(
        displayName = "ZERO-DAY HUNTER",
        shortName = "HUNTER",
        glyph = "Z",
        cost = 150,
        baseDamage = 26f,
        baseFireRate = 1.15f,
        baseRange = 225f,
        attackStyle = AttackStyle.HUNTER,
        unlockWave = 15,
        abilityName = "CRITICAL FIND",
        abilitySummary = "25% chance for a 3x critical hit. Ignores armour.",
        codexEntry = "A researcher who hunts for undiscovered flaws before an " +
            "attacker finds them. When the hunt pays off, it pays off big.",
        allowsTargetingModes = true
    ),
    AI_SENTINEL(
        displayName = "AI SENTINEL",
        shortName = "SENTINEL",
        glyph = "@",
        cost = 185,
        baseDamage = 15f,
        baseFireRate = 1.5f,
        baseRange = 235f,
        attackStyle = AttackStyle.SENTINEL,
        unlockWave = 20,
        abilityName = "MULTI-LOCK",
        abilitySummary = "Engages up to 3 separate packets with every volley.",
        codexEntry = "Machine-learning defence that correlates signals across the " +
            "whole network at once. It does not have to pick a single target " +
            "the way a human operator does.",
        allowsTargetingModes = true
    ),
    QUANTUM_DEFENDER(
        displayName = "QUANTUM DEFENDER",
        shortName = "QUANTUM",
        glyph = "Q",
        cost = 240,
        baseDamage = 34f,
        baseFireRate = 1.05f,
        baseRange = 245f,
        attackStyle = AttackStyle.QUANTUM,
        unlockWave = 30,
        abilityName = "ENTANGLED CHAIN",
        abilitySummary = "Each hit chains to 2 nearby packets for 55% damage.",
        codexEntry = "Speculative defence built on quantum key distribution, where " +
            "observing the channel changes it. Here, striking one packet " +
            "collapses the state of its neighbours too.",
        allowsTargetingModes = true
    ),
    ROOT_ADMIN(
        displayName = "ROOT ADMIN",
        shortName = "ROOT",
        glyph = "#",
        cost = 320,
        baseDamage = 78f,
        baseFireRate = 0.95f,
        baseRange = 255f,
        attackStyle = AttackStyle.ROOT,
        unlockWave = 40,
        abilityName = "SUDO TERMINATE",
        abilitySummary = "Overwhelming single-target damage. Ignores all armour.",
        codexEntry = "Root is total authority over a system — it can end any " +
            "process without asking. Expensive, late, and absolutely final.",
        allowsTargetingModes = true
    ),
    NETWORK_ARCHITECT(
        displayName = "NETWORK ARCHITECT",
        shortName = "ARCHITECT",
        glyph = "N",
        cost = 210,
        baseDamage = 8f,
        baseFireRate = 0.8f,
        baseRange = 230f,
        attackStyle = AttackStyle.ARCHITECT,
        unlockWave = 50,
        abilityName = "SEGMENT UPLINK",
        abilitySummary = "Buffs every agent in range: +30% damage, +20% fire rate.",
        codexEntry = "Good security is designed in, not bolted on. A network " +
            "architect segments the network so every other defence you own " +
            "works better than it would alone."
    );

    /** Agents available from the very first run. */
    val unlockedByDefault: Boolean get() = unlockWave <= 0

    fun statsAtLevel(level: Int): AgentStats {
        val steps = (level - 1).coerceIn(0, Balance.MAX_AGENT_LEVEL - 1)
        return AgentStats(
            damage = baseDamage * (1f + Balance.UPGRADE_DAMAGE_GROWTH * steps),
            fireRate = baseFireRate * (1f + Balance.UPGRADE_RATE_GROWTH * steps),
            // Range alone is capped: unbounded range would make node placement
            // stop mattering long before level 100.
            range = baseRange * (1f + Balance.UPGRADE_RANGE_GROWTH * steps)
                .coerceAtMost(Balance.UPGRADE_RANGE_CAP)
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
