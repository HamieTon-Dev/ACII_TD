package com.cyopstd.game.ui.codex

import com.cyopstd.game.i18n.tr

import com.cyopstd.game.model.AgentType
import com.cyopstd.game.model.BossModifier
import com.cyopstd.game.model.EnemyType

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
        AGENTS(tr("CYBER AGENTS"), tr("The defences you deploy")),
        THREATS(tr("CYBERATTACKS"), tr("What is coming down the lanes")),
        BOSSES(tr("BOSSES"), tr("Every fifth wave, and what it brings")),
        TERMS(tr("NETWORK TERMS"), tr("Plain-language glossary"))
    }

    fun entriesFor(section: Section): List<Entry> = when (section) {
        Section.AGENTS -> AgentType.catalog.map { type ->
            Entry(
                glyph = "[${type.glyph}]",
                title = type.displayName,
                subtitle = if (type.unlockWave <= 0) {
                    tr("◇ {0} · available from the start", type.cost)
                } else {
                    if (type.beginnerLevelOnly) {
                        tr("◇ {0} · unlocks at wave {1} on {2}", type.cost, type.unlockWave, AgentType.BEGINNER_LEVEL_NAME)
                    } else {
                        tr("◇ {0} · unlocks at wave {1}", type.cost, type.unlockWave)
                    }
                },
                body = type.realWorld,
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
                        append(tr("HP {0}", type.baseHealth.toInt()))
                        append(tr(" · SPD {0}", type.baseSpeed.toInt()))
                        append(tr(" · IMPACT {0}", type.serverDamage))
                        if (type.baseArmor > 0f) append(tr(" · ARMOR {0}", type.baseArmor.toInt()))
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
                    title = tr("INTRUSION (BOSS)"),
                    subtitle = tr("Arrives on every fifth wave"),
                    body = boss.codexEntry,
                    footnote = tr("Bosses move slowly but hit CORE-SERVER for " +
                        "{0} integrity. They pay out the most crypto " +
                        "in the game, and they get tougher every cycle.", boss.serverDamage)
                )
            )
            BossModifier.entries.forEach { modifier ->
                add(
                    Entry(
                        glyph = "<${modifier.tag}>",
                        title = modifier.displayName,
                        subtitle = tr("Boss modifier"),
                        body = modifier.description,
                        footnote = counterAdviceFor(modifier)
                    )
                )
            }
        }

        Section.TERMS -> GLOSSARY
    }

    private fun counterAdviceFor(type: EnemyType): String = when (type) {
        EnemyType.SQL_INJECTION -> tr("Anything kills these. Do not over-build for them.")
        EnemyType.MALWARE -> tr("Sustained damage wins. IPS or a levelled FIREWALL.")
        EnemyType.BOT -> tr("IPS does 35% extra to swarms. Position it where lanes converge.")
        EnemyType.TROJAN -> tr("Armour blunts small hits: bring ANALYST or ROOT ADMIN, not IPS.")
        EnemyType.EXPLOIT -> tr("IDS does 45% extra to fast attacks and sees them coming first.")
        EnemyType.ENCRYPTED -> tr("Everything except CRYPTOGRAPHER loses over half its damage here.")
        EnemyType.SQL_BLIND -> tr("Armoured and patient. Heavy single hits beat it; rapid fire wastes itself.")
        EnemyType.DDOS -> tr("Hold them in a TARPIT field, then let IPS clear the backlog.")
        EnemyType.ZERO_DAY -> tr("ANALYST does 80% extra to elites. Armour-ignoring agents help.")
        EnemyType.BOSS -> tr("Focus fire. ANALYST and ROOT ADMIN carry boss waves.")
    }

    private fun counterAdviceFor(modifier: BossModifier): String = when (modifier) {
        BossModifier.FIREWALL_RESISTANCE -> tr("Lean on non-FIREWALL agents for this wave.")
        BossModifier.ENCRYPTION_SHIELD -> tr("A single CRYPTOGRAPHER swings the whole fight.")
        BossModifier.ARMOR_PLATING -> tr("ZERO-DAY HUNTER and ROOT ADMIN ignore armour entirely.")
        BossModifier.SPEED_BURST -> tr("Keep a TARPIT on the route to bleed off the acceleration.")
        BossModifier.REGENERATION -> tr("Keep it under fire: it only repairs after a moment unhit, so cover the gaps between kill zones.")
        BossModifier.PACKET_REPLICATION -> tr("Leave swarm clear-up to IPS so your heavy hitters stay on the boss.")
        BossModifier.AGENT_DISRUPTION -> tr("Spread your agents out so one jam cannot silence the line.")
    }

    private val GLOSSARY = listOf(
        Entry(
            glyph = "[>]",
            title = tr("PACKET"),
            subtitle = tr("The unit of network traffic"),
            body = tr("Data sent across a network is chopped into packets: small " +
                "chunks with a destination address and a payload. A packet is not " +
                "an attack \u2014 almost all of them are ordinary traffic. What you " +
                "shoot here are packets carrying a specific attack, which is why " +
                "each one is named for the attack rather than for the packet.")
        ),
        Entry(
            glyph = "[SQL]",
            title = tr("SQL INJECTION"),
            subtitle = tr("The most common web attack there is"),
            body = tr("Applications ask databases questions in SQL. If user input is " +
                "pasted straight into that question, an attacker can write their " +
                "own ending to it \u2014 dumping a user table, bypassing a login, or " +
                "deleting the lot. The fix is old and well known (parameterised " +
                "queries), which is what makes it so frustrating that it is still " +
                "the attack you will meet most often.")
        ),
        Entry(
            glyph = "[SQL2]",
            title = tr("BLIND SQL INJECTION"),
            subtitle = tr("Injection with the lights off"),
            body = tr("Same flaw, no feedback. The application returns no error and " +
                "no data, so the attacker asks yes-or-no questions and watches " +
                "what changes \u2014 timing, page length, status codes \u2014 to read the " +
                "database one bit at a time. Slow, patient, and much harder to " +
                "spot in a log.")
        ),
        Entry(
            glyph = "[|]",
            title = tr("ROUTE"),
            subtitle = tr("The path traffic takes"),
            body = tr("Real traffic follows a route through switches and routers to " +
                "reach its destination. The two serpentine routes here are that " +
                "path, doubling back so you can see every hop at once.")
        ),
        Entry(
            glyph = "[F]",
            title = "FIREWALL",
            subtitle = tr("Rule-based traffic filter"),
            body = tr("A firewall checks traffic against a list of rules and drops " +
                "anything that does not match. It is the oldest and still the most " +
                "important perimeter defence there is.")
        ),
        Entry(
            glyph = "[I]",
            title = "IDS",
            subtitle = tr("Intrusion Detection System"),
            body = tr("An IDS monitors network activity for suspicious behaviour and " +
                "security threats, then raises an alert. It watches; it does not " +
                "block.")
        ),
        Entry(
            glyph = "[P]",
            title = "IPS",
            subtitle = tr("Intrusion Prevention System"),
            body = tr("An IPS is an IDS with the authority to act. When it recognises " +
                "an attack it drops the traffic itself instead of only telling " +
                "someone about it.")
        ),
        Entry(
            glyph = "{#}",
            title = tr("ENCRYPTION"),
            subtitle = tr("Making data unreadable without a key"),
            body = tr("Encryption scrambles data so only someone with the right key " +
                "can read it. It protects legitimate traffic — and it equally well " +
                "hides an attacker's payload from inspection.")
        ),
        Entry(
            glyph = "[0]",
            title = "ZERO-DAY",
            subtitle = tr("An unpatched, unknown flaw"),
            body = tr("A zero-day is a vulnerability the defender has had zero days to " +
                "fix, because nobody knew it existed. There is no signature to match " +
                "against, which is what makes them so dangerous.")
        ),
        Entry(
            glyph = "\u00AB\u00AB\u00BB\u00BB",
            title = "DDoS",
            subtitle = tr("Distributed Denial of Service"),
            body = tr("Thousands of machines send traffic at one target at once. No " +
                "single request is an attack; the flood is. The goal is not to break " +
                "in, it is to make the service unusable.")
        ),
        Entry(
            glyph = "[B]",
            title = tr("BOTNET"),
            subtitle = tr("A network of hijacked machines"),
            body = tr("Compromised computers quietly taking orders from a central " +
                "controller. Their owners usually have no idea. Botnets are what " +
                "make large DDoS attacks possible.")
        ),
        Entry(
            glyph = "[T]",
            title = "TROJAN",
            subtitle = tr("Malware in a useful disguise"),
            body = tr("Named after the wooden horse: software that looks like " +
                "something you want, carrying something you very much do not.")
        ),
        Entry(
            glyph = "[#]",
            title = tr("ROOT / ADMIN"),
            subtitle = tr("Unrestricted system access"),
            body = tr("The account that can do anything on a system. Attackers want " +
                "it; defenders guard it carefully. Most security work is about " +
                "limiting who gets it and for how long.")
        ),
        Entry(
            glyph = "[N]",
            title = tr("SEGMENTATION"),
            subtitle = tr("Dividing a network into zones"),
            body = tr("Splitting a network so a breach in one area cannot reach the " +
                "rest. It is why one compromised laptop should not mean a " +
                "compromised company.")
        ),
        Entry(
            glyph = "[@]",
            title = tr("SOC"),
            subtitle = tr("Security Operations Centre"),
            body = tr("The team and the room watching the alerts. If this game has a " +
                "setting, it is a SOC wall display at 3am.")
        )
    )
}
