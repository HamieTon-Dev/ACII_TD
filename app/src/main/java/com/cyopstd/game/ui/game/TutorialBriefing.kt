package com.cyopstd.game.ui.game

import com.cyopstd.game.model.AgentType
import com.cyopstd.game.model.BossVariant
import com.cyopstd.game.model.EnemyType

/**
 * The optional rundown of what is coming, built from the catalog.
 *
 * Every word of this already exists — `EnemyType.codexEntry`, the boss
 * variants' signatures, the agents' ability text — so the briefing is
 * *generated* from those rather than written a second time. A briefing written
 * out by hand is a briefing that describes the game as it was on the day it was
 * written: the first agent rebalanced, threat renamed or boss added leaves it
 * quietly lying to new players, which is the worst possible audience to lie to.
 *
 * Kept short on purpose. The offer promises twenty seconds.
 */
object TutorialBriefing {

    /** One line per threat: glyph, name, and the first sentence about it. */
    fun threats(limit: Int = 5): List<String> =
        EnemyType.entries
            .filterNot { it.isBoss }
            .take(limit)
            .map { "${it.glyph} ${it.displayName} — ${firstSentence(it.codexEntry)}" }

    /**
     * One line per boss identity the player can meet anywhere.
     *
     * Map-specific opponents are left out. The briefing is offered before the
     * first run, on the first level; listing four bosses that only exist on a
     * level the player has not unlocked is not a briefing, it is a spoiler
     * with no use attached.
     */
    fun bosses(): List<String> =
        BossVariant.entries
            .filter { it.mapId == null }
            .map { "${it.glyph} ${it.displayName} — ${it.signature}" }

    /**
     * The two mechanics a new player meets without being told their names.
     *
     * JAM and the TARPIT aura are both things that happen *to* you or *for*
     * you without a message, so a player who has not been told simply sees
     * their agents firing slower and the threats moving oddly.
     */
    fun mechanics(): List<String> = listOf(
        "JAM — a boss can disrupt an agent, halving its fire rate while it " +
            "lasts. ${AgentType.FIREWALL.displayName} is built not to care.",
        "${AgentType.TARPIT.displayName} — ${AgentType.TARPIT.abilitySummary} " +
            "It deals no damage; it buys your other agents time."
    )

    /** The whole briefing as the card's body. */
    fun body(): String = buildString {
        appendLine("WHAT IS ATTACKING")
        threats().forEach { appendLine("  $it") }
        appendLine()
        appendLine("BOSSES — every fifth wave")
        bosses().forEach { appendLine("  $it") }
        appendLine()
        appendLine("TWO THINGS NOBODY TELLS YOU")
        mechanics().forEach { appendLine("  $it") }
    }.trimEnd()

    /**
     * The first sentence of a codex entry.
     *
     * The entries are paragraphs written for the codex screen, where there is
     * room for them. Here there is not, and a truncated paragraph with an
     * ellipsis reads as broken rather than brief.
     */
    private fun firstSentence(text: String): String {
        val end = text.indexOf(". ")
        return if (end <= 0) text else text.substring(0, end + 1).trim()
    }
}
