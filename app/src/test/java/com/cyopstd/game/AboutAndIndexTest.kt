package com.cyopstd.game

import com.cyopstd.game.model.AgentType
import com.cyopstd.game.ui.menu.AboutText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The claims the game makes about itself.
 *
 * This file exists because of a specific failure. The About screen carried a
 * panel headed WHAT THIS GAME DOES NOT DO which told players there were no
 * advertisements, no in-app purchases, no cloud save and no INTERNET
 * permission. Every line was true when it was written. By the time it shipped,
 * five of the seven were false — a store listing whose own About screen denies
 * that the app shows ads, sitting in front of the player rather than buried in
 * a document nobody opens.
 *
 * Prose does not drift on its own; it drifts because nothing was checking it.
 */
class AboutAndIndexTest {

    // ------------------------------------------------------- what it claims

    @Test
    fun `the about screen never asserts there are no ads or purchases`() {
        // The specific regression. Whether this build shows ads is reported
        // from the build config at render time, so the only way the screen can
        // claim otherwise is if someone hard-codes it back into the static
        // text -- which is what this catches.
        val staticText = listOf(
            AboutText.DEVELOPMENT_STATEMENT,
            AboutText.AI_DISCLOSURE,
            AboutText.TRADEMARK_NOTICE,
            AboutText.CRYPTO_DISCLAIMER
        ) + AboutText.DOES_NOT_DO

        for (line in staticText) {
            val lower = line.lowercase()
            for (forbidden in listOf(
                "no advertisement",
                "no ads",
                "no in-app purchase",
                "no internet",
                "no cloud save"
            )) {
                assertFalse(
                    "About text hard-codes '$forbidden', which this build cannot " +
                        "promise: \"$line\"",
                    lower.contains(forbidden)
                )
            }
        }
    }

    @Test
    fun `the monetisation summary tells the truth either way`() {
        val withAds = AboutText.monetisationSummary(
            adsConfigured = true,
            purchasesAvailable = true
        ).lowercase()
        assertTrue("a build with ads must say so", withAds.contains("advertisements"))
        assertFalse("...and must not also deny it", withAds.contains("no advertisements"))
        assertTrue("purchases must be disclosed", withAds.contains("in-app purchases"))
        assertTrue(
            "the ad revive must be described as optional",
            withAds.contains("optional")
        )

        val without = AboutText.monetisationSummary(
            adsConfigured = false,
            purchasesAvailable = false
        ).lowercase()
        assertTrue(
            "a build with no ads is allowed to say so",
            without.contains("no advertisements")
        )
    }

    // ------------------------------------------------------- the disclosures

    @Test
    fun `the AI disclosure does not overclaim in either direction`() {
        val text = AboutText.AI_DISCLOSURE.lowercase()

        // It must say what it is.
        assertTrue(text.contains("artificial intelligence"))
        assertTrue(text.contains("educational"))

        // It must not imply the game was generated autonomously, nor that the
        // developer is not responsible for it.
        assertTrue(
            "the developer must remain responsible for the result",
            text.contains("responsibility of the developer")
        )
        for (overclaim in listOf("entirely generated", "autonomously", "fully automated")) {
            assertFalse("AI disclosure overclaims: '$overclaim'", text.contains(overclaim))
        }

        // It must not imply sponsorship, and must name no vendor or product --
        // naming one is allowed by the brief as plain text, but claiming a
        // relationship with one never is, and the safest text names none.
        assertTrue(
            "the disclosure must disclaim sponsorship",
            text.contains("not generated, sponsored, endorsed, owned or published")
        )
        for (vendor in listOf("openai", "anthropic", "claude", "chatgpt", "gemini", "copilot")) {
            assertFalse(
                "the disclosure names a vendor ('$vendor'), which invites a " +
                    "branding or endorsement question it does not need",
                text.contains(vendor)
            )
        }
    }

    @Test
    fun `the trademark notice disclaims rather than claims`() {
        val text = AboutText.TRADEMARK_NOTICE
        val lower = text.lowercase()

        // The single most important property: it must never claim a licence.
        // There is no evidence of one anywhere in this repository, and an
        // unsupported licence claim is worse than saying nothing.
        for (claim in listOf("licensed", "license from", "licence from", "under licence")) {
            assertFalse(
                "the notice claims a trademark licence ('$claim') that does not exist",
                lower.contains(claim)
            )
        }

        // It must not assert who owns a specific contested mark.
        assertFalse(
            "the notice asserts ownership of 'Blue Hat', which the owner " +
                "explicitly asked not to claim without verification",
            Regex("blue ?hat is a (registered )?trademark").containsMatchIn(lower)
        )

        // Red Hat is two words, per Red Hat's own published guidance.
        assertTrue("Red Hat must appear as two words", text.contains("Red Hat"))
        assertFalse("'RedHat' as one word contradicts Red Hat's guidance", text.contains("RedHat"))

        // And the disclaimers themselves must be present.
        assertTrue(lower.contains("not affiliated with"))
        assertTrue(lower.contains("independently developed"))
        assertTrue(lower.contains("respective owners"))
    }

    @Test
    fun `the copyright line uses the project's own identity and a live year`() {
        assertEquals("© 2031 HamieTon.dev. All rights reserved.", AboutText.copyright(2031))
        // Not baked in: a build made next year must not claim this year.
        assertFalse(AboutText.copyright(2031).contains("2026"))
        assertTrue(
            "the developer identity must be the one the splash already uses",
            AboutText.copyright().contains(
                com.cyopstd.game.ui.splash.DEVELOPER_NAME
            )
        )
    }

    // ------------------------------------------------- the agent unit index

    @Test
    fun `every agent has both halves of an index entry`() {
        for (type in AgentType.entries) {
            assertTrue(
                "${type.displayName} has no real-world explanation",
                type.realWorld.length > 60
            )
            assertTrue(
                "${type.displayName} has no in-game explanation",
                type.inGame.length > 40
            )
            assertTrue(
                "${type.displayName} still has no ability summary",
                type.abilitySummary.isNotBlank()
            )
        }
    }

    @Test
    fun `the real-world half contains no gameplay`() {
        // The exact confusion this split exists to remove: "It sees further
        // than anything else you can deploy" was the second sentence of what
        // an IDS *is*. A beginner cannot tell which half of that paragraph is
        // true of the world.
        // Phrases in the *gameplay* sense, not merely words that also occur
        // in security writing. The first version of this list held a bare
        // "deploy" and flagged "quantum key distribution ... is deployed only
        // in rare, specialised settings", which is exactly the real-world
        // usage this half is supposed to contain. A check that cannot tell the
        // two senses apart pushes correct prose out of the text.
        val gameplayPhrases = listOf(
            "you deploy", "you can deploy", "deployment node", "this agent",
            "this unit", "fire rate", "◇", "targeting selector",
            "splash", "agent in range", "on the board"
        )
        for (type in AgentType.entries) {
            val lower = type.realWorld.lowercase()
            for (phrase in gameplayPhrases) {
                assertFalse(
                    "${type.displayName}'s REAL-WORLD text contains gameplay " +
                        "('$phrase'): \"${type.realWorld}\"",
                    lower.contains(phrase)
                )
            }
        }
    }

    @Test
    fun `no informal term is presented as a certification or job title`() {
        // The brief is explicit: do not claim an informal term is an official
        // certification or standardised job title unless it actually is.
        // ZERO-DAY HUNTER and ROOT ADMIN are both informal, and both say so.
        val hunter = AgentType.ZERO_DAY_HUNTER.realWorld.lowercase()
        assertTrue(
            "'zero-day hunter' must be flagged as informal",
            hunter.contains("informal") || hunter.contains("not a certification")
        )
        val root = AgentType.ROOT_ADMIN.realWorld.lowercase()
        assertTrue(
            "'root admin' must be flagged as an access level, not a job title",
            root.contains("not a job title") || root.contains("shorthand")
        )
    }

    @Test
    fun `the cryptographer does not imply real encryption can be broken`() {
        val text = AgentType.CRYPTOGRAPHER.realWorld + " " + AgentType.CRYPTOGRAPHER.inGame
        val lower = text.lowercase()
        assertTrue(
            "the fictional licence must be stated outright",
            lower.contains("fictional") || lower.contains("does not work this way")
        )
        assertTrue(
            "it must say plainly that no real cipher is broken",
            lower.contains("nothing here breaks any real cipher") ||
                lower.contains("does not break")
        )
    }

    @Test
    fun `the quantum defender separates QKD from post-quantum cryptography`() {
        // Two different things share the word "quantum" in security writing and
        // conflating them is the most common beginner error about it.
        val real = AgentType.QUANTUM_DEFENDER.realWorld.lowercase()
        assertTrue(real.contains("quantum key distribution"))
        assertTrue(real.contains("post-quantum"))

        // The "this is fiction" flag lives in the IN-GAME half, because that
        // is what it is a statement about. Keeping it in the real-world half
        // was what made that half fail the no-gameplay check -- correctly.
        val game = AgentType.QUANTUM_DEFENDER.inGame.lowercase()
        assertTrue(
            "the unit must be flagged as fiction",
            game.contains("science fiction") || game.contains("speculative")
        )
    }

    @Test
    fun `nothing in the index claims a real-world capability the game has`() {
        for (type in AgentType.entries) {
            val lower = (type.realWorld + " " + type.inGame).lowercase()
            for (claim in listOf(
                "this game can",
                "scans your network",
                "protects your device",
                "certified",
                "industry standard certification"
            )) {
                assertFalse(
                    "${type.displayName} claims a real capability: '$claim'",
                    lower.contains(claim)
                )
            }
        }
    }
}
