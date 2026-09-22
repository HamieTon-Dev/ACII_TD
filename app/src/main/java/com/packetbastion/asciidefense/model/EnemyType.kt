package com.packetbastion.asciidefense.model

/**
 * Damage families. An agent can be strong or weak against a family, which is
 * what makes tower composition matter instead of "buy the highest DPS".
 */
enum class ThreatTrait {
    /** Ordinary traffic; no special interactions. */
    STANDARD,

    /** Encrypted: resistant to raw damage until a Cryptographer breaks it. */
    ENCRYPTED,

    /** Armoured: flat damage reduction per hit. */
    ARMORED,

    /** Swarm: individually weak, arrives in packs. */
    SWARM,

    /** Elite: high-value target; Analysts hit these harder. */
    ELITE
}

/**
 * A hostile packet archetype. Base stats here are the wave-1 values; the wave
 * generator multiplies them through [com.packetbastion.asciidefense.core.Balance].
 */
enum class EnemyType(
    val displayName: String,
    val glyph: String,
    val baseHealth: Float,
    /** World units per second at wave 1. */
    val baseSpeed: Float,
    /** Server integrity lost if this packet arrives. */
    val serverDamage: Int,
    /** Flat damage subtracted from every hit. */
    val baseArmor: Float,
    val traits: Set<ThreatTrait>,
    val rewardTier: RewardTier,
    /** How wide the glyph draws, in world units — bosses are deliberately large. */
    val glyphScale: Float,
    val codexEntry: String
) {
    SQL_INJECTION(
        displayName = "SQL INJECTION",
        // Five characters wide against the usual three, so it renders smaller
        // to stay the same size on the battlefield as everything around it.
        glyph = "[SQL]",
        baseHealth = 22f,
        baseSpeed = 62f,
        serverDamage = 1,
        baseArmor = 0f,
        traits = setOf(ThreatTrait.STANDARD),
        rewardTier = RewardTier.NORMAL,
        glyphScale = 0.72f,
        codexEntry = "The most common attack on the internet, and the baseline " +
            "threat here. An attacker types database commands into a field that " +
            "expected a name or a password; if the application passes that " +
            "straight through, the database obeys."
    ),
    MALWARE(
        displayName = "MALWARE",
        glyph = "[M]",
        baseHealth = 46f,
        baseSpeed = 50f,
        serverDamage = 2,
        baseArmor = 0f,
        traits = setOf(ThreatTrait.STANDARD),
        rewardTier = RewardTier.NORMAL,
        glyphScale = 1.05f,
        codexEntry = "Malicious software: code written to damage, disrupt or steal. " +
            "It carries more payload than a plain packet, so it soaks up more " +
            "damage before it breaks apart."
    ),
    BOT(
        displayName = "BOT",
        glyph = "[B]",
        baseHealth = 13f,
        baseSpeed = 74f,
        serverDamage = 1,
        baseArmor = 0f,
        traits = setOf(ThreatTrait.SWARM),
        rewardTier = RewardTier.NORMAL,
        glyphScale = 0.92f,
        codexEntry = "A single compromised machine taking orders from someone else. " +
            "One bot is harmless. A botnet of thousands is not — which is why " +
            "these always arrive in groups."
    ),
    TROJAN(
        displayName = "TROJAN",
        glyph = "[T]",
        baseHealth = 60f,
        baseSpeed = 38f,
        serverDamage = 3,
        baseArmor = 2.5f,
        traits = setOf(ThreatTrait.ARMORED),
        rewardTier = RewardTier.NORMAL,
        glyphScale = 1.1f,
        codexEntry = "Hostile code disguised as something you asked for. Slow to " +
            "move but heavily wrapped — armour blunts every individual hit, so " +
            "fast weak shots waste themselves on it."
    ),
    EXPLOIT(
        displayName = "EXPLOIT",
        glyph = "[X]",
        baseHealth = 26f,
        baseSpeed = 106f,
        serverDamage = 3,
        baseArmor = 0f,
        traits = setOf(ThreatTrait.STANDARD),
        rewardTier = RewardTier.NORMAL,
        glyphScale = 1f,
        codexEntry = "Code that abuses a specific flaw in a system. Exploits move " +
            "fast and hit hard; detection range matters more than raw damage " +
            "when these are inbound."
    ),
    ENCRYPTED(
        displayName = "ENCRYPTED PACKET",
        glyph = "[E]",
        baseHealth = 40f,
        baseSpeed = 58f,
        serverDamage = 2,
        baseArmor = 0f,
        traits = setOf(ThreatTrait.ENCRYPTED),
        rewardTier = RewardTier.NORMAL,
        glyphScale = 1f,
        codexEntry = "Its contents are scrambled, so most defences cannot read what " +
            "they are shooting at and only land partial damage. A CRYPTOGRAPHER " +
            "agent cuts straight through the cipher."
    ),
    SQL_BLIND(
        displayName = "BLIND SQLi",
        glyph = "[SQL2]",
        baseHealth = 72f,
        baseSpeed = 56f,
        serverDamage = 3,
        baseArmor = 3f,
        traits = setOf(ThreatTrait.ARMORED),
        rewardTier = RewardTier.NORMAL,
        glyphScale = 0.66f,
        codexEntry = "A blind injection gets no error messages back, so the " +
            "attacker infers the answer one true-or-false question at a time. " +
            "It is slower and far more patient than a normal injection, and it " +
            "arrives hardened against the obvious defences."
    ),
    DDOS(
        displayName = "DDoS PACKET",
        // Guillemets are Latin-1 Supplement, so every Android monospace face has
        // them. The glyph is six characters wide where most are three, hence the
        // reduced scale: it renders at a comparable size on the battlefield.
        glyph = "\u00AB\u00AB\u00AB\u00BB\u00BB\u00BB",
        baseHealth = 9f,
        baseSpeed = 132f,
        serverDamage = 1,
        baseArmor = 0f,
        traits = setOf(ThreatTrait.SWARM),
        rewardTier = RewardTier.NORMAL,
        glyphScale = 0.62f,
        codexEntry = "Distributed Denial of Service: the attack is the volume. Each " +
            "packet is trivial, but they arrive faster than anything else in the " +
            "game and there are always more."
    ),
    ZERO_DAY(
        displayName = "ZERO-DAY",
        glyph = "[0]",
        baseHealth = 130f,
        baseSpeed = 54f,
        serverDamage = 5,
        baseArmor = 4f,
        traits = setOf(ThreatTrait.ELITE, ThreatTrait.ARMORED),
        rewardTier = RewardTier.ELITE,
        glyphScale = 1.2f,
        codexEntry = "An attack against a flaw nobody has patched yet — the defender " +
            "has had zero days to prepare. Rare, tough, and worth real crypto to " +
            "shut down."
    ),
    BOSS(
        displayName = "INTRUSION",
        glyph = "[!!!]",
        baseHealth = 520f,
        baseSpeed = 30f,
        serverDamage = 12,
        baseArmor = 3f,
        traits = setOf(ThreatTrait.ELITE, ThreatTrait.ARMORED),
        rewardTier = RewardTier.BOSS,
        glyphScale = 2.1f,
        codexEntry = "A coordinated breach attempt with enough redundancy to shrug " +
            "off an unprepared network. Every fifth wave brings one, and each is " +
            "tougher than the last."
    );

    val isBoss: Boolean get() = this == BOSS
    val isElite: Boolean get() = ThreatTrait.ELITE in traits
}

enum class RewardTier { NORMAL, ELITE, BOSS }

/**
 * Optional modifiers layered onto bosses as the boss cycle climbs. They are
 * introduced one at a time so the player can learn each in isolation.
 */
enum class BossModifier(
    val displayName: String,
    val tag: String,
    val description: String
) {
    FIREWALL_RESISTANCE(
        "FIREWALL RESISTANCE", "FW-RES",
        "Takes 35% less damage from FIREWALL agents."
    ),
    ENCRYPTION_SHIELD(
        "ENCRYPTION SHIELD", "ENC-SHD",
        "Counts as encrypted: most agents land reduced damage."
    ),
    ARMOR_PLATING(
        "ARMOR PLATING", "ARMOR+",
        "Heavy flat damage reduction on every hit."
    ),
    SPEED_BURST(
        "SPEED BURST", "BURST",
        "Periodically accelerates toward the server."
    ),
    REGENERATION(
        "REGENERATION", "REGEN",
        "Slowly repairs itself while it is still moving."
    ),
    PACKET_REPLICATION(
        "PACKET REPLICATION", "REPLICATE",
        "Spawns escort packets as it advances."
    ),
    AGENT_DISRUPTION(
        "AGENT DISRUPTION", "DISRUPT",
        "Briefly jams nearby agents, slowing their fire rate."
    );

    companion object {
        /**
         * Modifiers unlocked by boss cycle. Cycle 1 (wave 5) is a clean fight so
         * that a new player meets a boss with no extra rules attached.
         */
        fun poolForCycle(cycle: Int): List<BossModifier> = when {
            cycle <= 1 -> emptyList()
            cycle == 2 -> listOf(ARMOR_PLATING)
            cycle == 3 -> listOf(ARMOR_PLATING, SPEED_BURST)
            cycle == 4 -> listOf(ARMOR_PLATING, SPEED_BURST, FIREWALL_RESISTANCE)
            cycle == 5 -> listOf(ARMOR_PLATING, SPEED_BURST, FIREWALL_RESISTANCE, REGENERATION)
            cycle == 6 -> listOf(
                ARMOR_PLATING, SPEED_BURST, FIREWALL_RESISTANCE, REGENERATION,
                ENCRYPTION_SHIELD
            )
            cycle == 7 -> listOf(
                ARMOR_PLATING, SPEED_BURST, FIREWALL_RESISTANCE, REGENERATION,
                ENCRYPTION_SHIELD, PACKET_REPLICATION
            )
            else -> entries.toList()
        }

        /** How many modifiers a boss of [cycle] rolls. */
        fun countForCycle(cycle: Int): Int = when {
            cycle <= 1 -> 0
            cycle <= 3 -> 1
            cycle <= 6 -> 2
            cycle <= 10 -> 3
            else -> 4
        }
    }
}
