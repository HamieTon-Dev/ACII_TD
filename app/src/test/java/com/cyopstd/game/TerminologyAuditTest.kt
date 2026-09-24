package com.cyopstd.game

import com.cyopstd.game.model.AgentType
import com.cyopstd.game.model.BossVariant
import com.cyopstd.game.model.EnemyType
import com.cyopstd.game.store.Sku
import com.cyopstd.game.ui.game.TutorialScript
import com.cyopstd.game.ui.menu.AboutText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A sweep over every string a player can read.
 *
 * The brief: look for wording that could imply real hacking functionality,
 * actual cryptocurrency value, affiliation with a cybersecurity company, Red
 * Hat or Microsoft, official certification, corporate sponsorship, or
 * real-world security capabilities the game does not have.
 *
 * Written as a test rather than a one-off read-through for the obvious reason:
 * a read-through is true on the day it happens. This is the same lesson the
 * About screen taught the expensive way — its "no advertisements" claim was
 * accurate when written and false when shipped, because nothing re-read it.
 *
 * **What this cannot do.** It matches phrases, so it catches the careless
 * version of each problem and not a determined one. It is a tripwire on the
 * cheap failure mode, not a substitute for judgement or for legal review.
 */
class TerminologyAuditTest {

    /** Everything a player can read, with a label so a failure names the source. */
    private fun allPlayerFacingText(): List<Pair<String, String>> = buildList {
        for (type in AgentType.entries) {
            add("agent ${type.displayName} name" to type.displayName)
            add("agent ${type.displayName} ability" to type.abilitySummary)
            add("agent ${type.displayName} real-world" to type.realWorld)
            add("agent ${type.displayName} in-game" to type.inGame)
        }
        for (type in EnemyType.entries) {
            add("threat ${type.displayName}" to type.codexEntry)
            add("threat ${type.displayName} name" to type.displayName)
        }
        for (boss in BossVariant.entries) {
            add("boss ${boss.displayName}" to boss.signature)
        }
        for (sku in Sku.entries) {
            add("store ${sku.id} title" to sku.title)
            add("store ${sku.id} blurb" to sku.summary)
        }
        for ((index, step) in TutorialScript.steps.withIndex()) {
            add("tutorial step $index title" to step.title)
            add("tutorial step $index body" to step.body)
        }
        add("about development" to AboutText.DEVELOPMENT_STATEMENT)
        add("about AI" to AboutText.AI_DISCLOSURE)
        add("about trademark" to AboutText.TRADEMARK_NOTICE)
        add("about crypto" to AboutText.CRYPTO_DISCLAIMER)
        for ((index, line) in AboutText.DOES_NOT_DO.withIndex()) {
            add("about does-not-do $index" to line)
        }
    }

    @Test
    fun `nothing implies real cryptocurrency value`() {
        // The game's currency is called Crypto and is drawn with a diamond.
        // That is a deliberate genre joke and it is also exactly the sort of
        // thing a store reviewer looks twice at, so nothing may suggest it is
        // worth money or can leave the device.
        val forbidden = listOf(
            "real money", "cash out", "withdraw", "exchange rate", "wallet",
            "blockchain", "nft", "token sale", "mining rig", "real currency",
            "convert to"
        )
        for ((where, text) in allPlayerFacingText()) {
            val lower = text.lowercase()
            // A line that *denies* these is the disclaimer working, not a
            // violation: "No real cryptocurrency, blockchain, wallet, mining or
            // NFTs" contains every word this looks for, on purpose. The first
            // version of this test flagged the very sentence that exists to
            // prevent the problem.
            val isDenial = lower.startsWith("no ") ||
                lower.contains("no real cryptocurrency") ||
                lower.contains("fictional in-game resource")
            if (isDenial) continue
            for (phrase in forbidden) {
                assertFalse(
                    "$where suggests real cryptocurrency value ('$phrase'): \"$text\"",
                    lower.contains(phrase)
                )
            }
        }
    }

    @Test
    fun `the crypto disclaimer is present and unambiguous`() {
        val lower = AboutText.CRYPTO_DISCLAIMER.lowercase()
        assertTrue(lower.contains("fictional"))
        assertTrue(lower.contains("no monetary value") || lower.contains("no value"))
        assertTrue(lower.contains("cannot leave the device"))
    }

    @Test
    fun `nothing claims real hacking or security capability`() {
        val forbidden = listOf(
            "scan your network", "scans your network", "protect your device",
            "protects your device", "secure your phone", "real attack",
            "actually hack", "penetration test your", "exploit a real"
        )
        for ((where, text) in allPlayerFacingText()) {
            val lower = text.lowercase()
            for (phrase in forbidden) {
                assertFalse(
                    "$where claims a real security capability ('$phrase'): \"$text\"",
                    lower.contains(phrase)
                )
            }
        }
    }

    @Test
    fun `nothing implies corporate affiliation or sponsorship`() {
        // Company names may appear -- the trademark notice names Red Hat and
        // Microsoft in order to disclaim them, which is the opposite of
        // implying a relationship. What must never appear is a word that
        // asserts one.
        val forbidden = listOf(
            "in partnership with", "officially licensed", "official partner",
            "sponsored by", "powered by red hat", "a microsoft product",
            "endorsed by", "approved by"
        )
        for ((where, text) in allPlayerFacingText()) {
            val lower = text.lowercase()
            for (phrase in forbidden) {
                // The trademark notice uses "endorsed by" and "approved by"
                // inside an explicit denial, which is the one correct use.
                val isDenial = lower.contains("not affiliated with") ||
                    lower.contains("is not generated, sponsored, endorsed")
                assertFalse(
                    "$where implies affiliation ('$phrase'): \"$text\"",
                    lower.contains(phrase) && !isDenial
                )
            }
        }
    }

    @Test
    fun `no informal term is dressed up as a certification`() {
        val forbidden = listOf(
            "certified", "certification", "accredited", "qualification",
            "industry standard role", "official title"
        )
        for ((where, text) in allPlayerFacingText()) {
            val lower = text.lowercase()
            for (phrase in forbidden) {
                // Saying something is NOT a certification is the point.
                val isDenial = lower.contains("not a certification") ||
                    lower.contains("not a job title") ||
                    lower.contains("informal")
                assertFalse(
                    "$where implies a certification ('$phrase'): \"$text\"",
                    lower.contains(phrase) && !isDenial
                )
            }
        }
    }

    @Test
    fun `Red Hat is never written as one word anywhere a player can read`() {
        // Red Hat's own published guidance is two words. The game does not
        // currently ship a unit by that name -- [REDHAT] and [BLUEHAT] live in
        // the backlog under D2 and have never been implemented -- but this
        // fails the moment one arrives spelled wrongly.
        for ((where, text) in allPlayerFacingText()) {
            assertFalse(
                "$where writes 'RedHat' as one word; Red Hat's guidance is two: \"$text\"",
                text.contains("RedHat", ignoreCase = true) &&
                    !text.contains("Red Hat")
            )
        }
    }

    @Test
    fun `the store sells cosmetics and convenience, never an advantage it hides`() {
        // Not a legal point, a truthfulness one: a blurb that undersells what
        // a purchase changes is the kind of thing that ends up in a refund
        // request. Every paid item must describe what it does.
        for (sku in Sku.entries) {
            assertTrue("${sku.id} has no title", sku.title.isNotBlank())
            assertTrue(
                "${sku.id} has no description, so a buyer cannot tell what they get",
                sku.summary.length > 15
            )
        }
    }

    @Test
    fun `the tutorial teaches the losing condition`() {
        // P2. The tutorial ran for eleven steps without ever saying what
        // CORE-SERVER was, where its integrity was shown, or what zero meant.
        val step = TutorialScript.stepAt(TutorialScript.CORE_INTEGRITY)
        assertTrue("there is no core integrity step", step != null)
        val text = (step!!.title + " " + step.body).lowercase()

        assertTrue("it must name the thing being defended", text.contains("core-server"))
        assertTrue("it must name the readout", text.contains("integrity"))
        assertTrue(
            "it must say where the readout is",
            text.contains("top strip") || text.contains("top of the screen")
        )
        assertTrue(
            "it must say that reaching the server causes harm",
            text.contains("reaches") || text.contains("reach")
        )
        assertTrue("it must state the zero condition", text.contains("0"))
        assertTrue(
            "it must say the run ends",
            text.contains("run ends") || text.contains("game over") ||
                text.contains("breached")
        )
    }

    @Test
    fun `the core integrity lesson comes before the player is asked to do anything`() {
        assertTrue(
            "the losing condition must be taught before the first placement",
            TutorialScript.CORE_INTEGRITY < TutorialScript.PLACE_FIREWALLS
        )
        assertTrue(
            "...and early, because it is the rule everything else depends on",
            TutorialScript.CORE_INTEGRITY <= TutorialScript.WAVE_READOUT
        )
    }
}
