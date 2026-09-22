package com.packetbastion.asciidefense.ui.codex

import com.packetbastion.asciidefense.model.AgentType
import com.packetbastion.asciidefense.model.BossModifier
import com.packetbastion.asciidefense.model.EnemyType

/**
 * The educational layer.
 *
 * Agent and threat entries are pulled straight from the game data so the Codex
 * can never drift out of sync with what the game actually does. The network
 * glossary is hand-written, short, and aimed squarely at someone who has never
 * configured a firewall in their life.
 */
object CodexContent {

    data class Entry(
        val glyph: String,
        val title: String,
        val subtitle: String,
        val body: String,
        val footnote: String? = null
    )

    enum class Section(val title: String, val description: String) {
        AGENTS("CYBER AGENTS", "The defences you deploy"),
        THREATS("THREAT PACKETS", "What is coming down the lanes"),
        BOSSES("BOSSES", "Every fifth wave, and what it brings"),
        TERMS("NETWORK TERMS", "Plain-language glossary")
    }

    fun entriesFor(section: Section): List<Entry> = when (section) {
        Section.AGENTS -> AgentType.catalog.map { type ->
            Entry(
                glyph = "[${type.glyph}]",
                title = type.displayName,
                subtitle = if (type.unlockWave <= 0) {
                    "◇ ${type.cost} · available from the start"
                } else {
                    "◇ ${type.cost} · unlocks at wave ${type.unlockWave}"
                },
                body = type.codexEntry,
                footnote = "${type.abilityName}: ${type.abilitySummary}"
            )
        }

        Section.THREATS -> EnemyType.entries
            .filter { !it.isBoss }
            .map { type ->
                Entry(
                    glyph = type.glyph,
                    title = type.displayName,
                    subtitle = buildString {
                        append("HP ${type.baseHealth.toInt()}")
                        append(" · SPD ${type.baseSpeed.toInt()}")
                        append(" · IMPACT ${type.serverDamage}")
                        if (type.baseArmor > 0f) append(" · ARMOR ${type.baseArmor.toInt()}")
                    },
                    body = type.codexEntry,
                    footnote = counterAdviceFor(type)
                )
            }

        Section.BOSSES -> buildList {
            val boss = EnemyType.BOSS
            add(
                Entry(
                    glyph = boss.glyph,
                    title = "INTRUSION (BOSS)",
                    subtitle = "Arrives on every fifth wave",
                    body = boss.codexEntry,
                    footnote = "Bosses move slowly but hit CORE-SERVER for " +
                        "${boss.serverDamage} integrity. They pay out the most crypto " +
                        "in the game, and they get tougher every cycle."
                )
            )
            BossModifier.entries.forEach { modifier ->
                add(
                    Entry(
                        glyph = "<${modifier.tag}>",
                        title = modifier.displayName,
                        subtitle = "Boss modifier",
                        body = modifier.description,
                        footnote = counterAdviceFor(modifier)
                    )
                )
            }
        }

        Section.TERMS -> GLOSSARY
    }

    private fun counterAdviceFor(type: EnemyType): String = when (type) {
        EnemyType.SQL_INJECTION -> "Anything kills these. Do not over-build for them."
        EnemyType.MALWARE -> "Sustained damage wins. IPS or a levelled FIREWALL."
        EnemyType.BOT -> "IPS does 35% extra to swarms. Position it where lanes converge."
        EnemyType.TROJAN -> "Armour blunts small hits: bring ANALYST or ROOT ADMIN, not IPS."
        EnemyType.EXPLOIT -> "IDS does 45% extra to fast packets and sees them coming first."
        EnemyType.ENCRYPTED -> "Everything except CRYPTOGRAPHER loses over half its damage here."
        EnemyType.SQL_BLIND -> "Armoured and patient. Heavy single hits beat it; rapid fire wastes itself."
        EnemyType.DDOS -> "Slow them with SANDBOX, then let IPS clear the backlog."
        EnemyType.ZERO_DAY -> "ANALYST does 80% extra to elites. Armour-ignoring agents help."
        EnemyType.BOSS -> "Focus fire. ANALYST and ROOT ADMIN carry boss waves."
    }

    private fun counterAdviceFor(modifier: BossModifier): String = when (modifier) {
        BossModifier.FIREWALL_RESISTANCE -> "Lean on non-FIREWALL agents for this wave."
        BossModifier.ENCRYPTION_SHIELD -> "A single CRYPTOGRAPHER swings the whole fight."
        BossModifier.ARMOR_PLATING -> "ZERO-DAY HUNTER and ROOT ADMIN ignore armour entirely."
        BossModifier.SPEED_BURST -> "Keep a SANDBOX on the lane to cancel the acceleration."
        BossModifier.REGENERATION -> "Burst it down; chip damage will never out-pace the repair."
        BossModifier.PACKET_REPLICATION -> "Leave swarm clear-up to IPS so your heavy hitters stay on the boss."
        BossModifier.AGENT_DISRUPTION -> "Spread your agents out so one jam cannot silence the line."
    }

    private val GLOSSARY = listOf(
        Entry(
            glyph = "[>]",
            title = "PACKET",
            subtitle = "The unit of network traffic",
            body = "Data sent across a network is chopped into packets: small " +
                "chunks with a destination address and a payload. A packet is not " +
                "an attack \u2014 almost all of them are ordinary traffic. What you " +
                "shoot here are packets carrying a specific attack, which is why " +
                "each one is named for the attack rather than for the packet."
        ),
        Entry(
            glyph = "[SQL]",
            title = "SQL INJECTION",
            subtitle = "The most common web attack there is",
            body = "Applications ask databases questions in SQL. If user input is " +
                "pasted straight into that question, an attacker can write their " +
                "own ending to it \u2014 dumping a user table, bypassing a login, or " +
                "deleting the lot. The fix is old and well known (parameterised " +
                "queries), which is what makes it so frustrating that it is still " +
                "the attack you will meet most often."
        ),
        Entry(
            glyph = "[SQL2]",
            title = "BLIND SQL INJECTION",
            subtitle = "Injection with the lights off",
            body = "Same flaw, no feedback. The application returns no error and " +
                "no data, so the attacker asks yes-or-no questions and watches " +
                "what changes \u2014 timing, page length, status codes \u2014 to read the " +
                "database one bit at a time. Slow, patient, and much harder to " +
                "spot in a log."
        ),
        Entry(
            glyph = "[|]",
            title = "LANE / ROUTE",
            subtitle = "The path traffic takes",
            body = "Real traffic follows a route through switches and routers to " +
                "reach its destination. The three lanes here are that route, drawn " +
                "flat so you can see every hop at once."
        ),
        Entry(
            glyph = "[F]",
            title = "FIREWALL",
            subtitle = "Rule-based traffic filter",
            body = "A firewall checks traffic against a list of rules and drops " +
                "anything that does not match. It is the oldest and still the most " +
                "important perimeter defence there is."
        ),
        Entry(
            glyph = "[I]",
            title = "IDS",
            subtitle = "Intrusion Detection System",
            body = "An IDS monitors network activity for suspicious behaviour and " +
                "security threats, then raises an alert. It watches; it does not " +
                "block."
        ),
        Entry(
            glyph = "[P]",
            title = "IPS",
            subtitle = "Intrusion Prevention System",
            body = "An IPS is an IDS with the authority to act. When it recognises " +
                "an attack it drops the traffic itself instead of only telling " +
                "someone about it."
        ),
        Entry(
            glyph = "[S]",
            title = "SANDBOX",
            subtitle = "Isolated analysis environment",
            body = "A sandbox runs untrusted code in a sealed environment and " +
                "watches what it tries to do. If it turns out to be malicious, it " +
                "did its damage to a throwaway machine."
        ),
        Entry(
            glyph = "{#}",
            title = "ENCRYPTION",
            subtitle = "Making data unreadable without a key",
            body = "Encryption scrambles data so only someone with the right key " +
                "can read it. It protects legitimate traffic — and it equally well " +
                "hides an attacker's payload from inspection."
        ),
        Entry(
            glyph = "[0]",
            title = "ZERO-DAY",
            subtitle = "An unpatched, unknown flaw",
            body = "A zero-day is a vulnerability the defender has had zero days to " +
                "fix, because nobody knew it existed. There is no signature to match " +
                "against, which is what makes them so dangerous."
        ),
        Entry(
            glyph = "\u00AB\u00AB\u00BB\u00BB",
            title = "DDoS",
            subtitle = "Distributed Denial of Service",
            body = "Thousands of machines send traffic at one target at once. No " +
                "single request is an attack; the flood is. The goal is not to break " +
                "in, it is to make the service unusable."
        ),
        Entry(
            glyph = "[B]",
            title = "BOTNET",
            subtitle = "A network of hijacked machines",
            body = "Compromised computers quietly taking orders from a central " +
                "controller. Their owners usually have no idea. Botnets are what " +
                "make large DDoS attacks possible."
        ),
        Entry(
            glyph = "[T]",
            title = "TROJAN",
            subtitle = "Malware in a useful disguise",
            body = "Named after the wooden horse: software that looks like " +
                "something you want, carrying something you very much do not."
        ),
        Entry(
            glyph = "[#]",
            title = "ROOT / ADMIN",
            subtitle = "Unrestricted system access",
            body = "The account that can do anything on a system. Attackers want " +
                "it; defenders guard it carefully. Most security work is about " +
                "limiting who gets it and for how long."
        ),
        Entry(
            glyph = "[N]",
            title = "SEGMENTATION",
            subtitle = "Dividing a network into zones",
            body = "Splitting a network so a breach in one area cannot reach the " +
                "rest. It is why one compromised laptop should not mean a " +
                "compromised company."
        ),
        Entry(
            glyph = "[@]",
            title = "SOC",
            subtitle = "Security Operations Centre",
            body = "The team and the room watching the alerts. If this game has a " +
                "setting, it is a SOC wall display at 3am."
        )
    )
}
