package com.cyopstd.game.ui.menu

import java.util.Calendar

/**
 * The legal and disclosure text, in one place because it is testable there.
 *
 * Kept out of the composable deliberately. Every string below is a claim about
 * the world rather than a piece of UI copy — what the app collects, who it is
 * affiliated with, who owns what — and the way those go wrong is by drifting
 * out of step with the code while nobody is reading them. `AboutTextTest`
 * asserts the properties that matter; it cannot do that against text buried in
 * a `Text(...)` call.
 *
 * That drift is not hypothetical here. Until this release the About screen told
 * players, in a panel headed WHAT THIS GAME DOES NOT DO, that the game had no
 * advertisements, no in-app purchases, no cloud save and no INTERNET
 * permission. Every one of those was true when it was written and false by the
 * time it shipped.
 */
object AboutText {

    /** The developer identity the project already uses. Not a legal entity. */
    const val DEVELOPER = "HamieTon.dev"

    const val GAME_TITLE = "CyOps TD"
    const val GAME_SUBTITLE = "Packet Bastion · ASCII Cyber Defense"

    /**
     * Copyright line.
     *
     * The year is read at runtime rather than baked in, so a build made next
     * year does not claim this one. There is no `LICENSE` file and neither
     * `README.md` nor `LICENSES.md` places the game's own code under an
     * open-source licence — `LICENSES.md` covers third-party dependencies and
     * states first-party content is original work — so "All rights reserved"
     * contradicts nothing. If the game is ever released under a licence, this
     * line has to change with it.
     */
    fun copyright(year: Int = Calendar.getInstance().get(Calendar.YEAR)): String =
        "© $year $DEVELOPER. All rights reserved."

    /** What the game is, in two sentences, for someone who has not played it. */
    const val DEVELOPMENT_STATEMENT =
        "An independently developed, offline-first tower defence game with a " +
            "cybersecurity theme. The scenarios, agents and terminology are " +
            "fictionalised for gameplay, with short real-world explanations " +
            "included so the concepts behind them are learnable. It teaches no " +
            "offensive technique and performs no real security function."

    /**
     * AI assistance disclosure.
     *
     * Three things it must not do, all of them ways of overclaiming in the
     * opposite direction from usual: imply the game was generated
     * autonomously, imply any AI company sponsors or endorses it, or carry a
     * company's logo or branding. It names no product and no vendor, which is
     * the most defensible position available and costs nothing.
     *
     * The owner's draft said "while learning Python". This project is Kotlin
     * and Android throughout; the only Python in the repository is two build
     * helper scripts under `tools/`. A statement whose entire purpose is
     * accuracy should not open with an inaccuracy, so it reads "programming
     * and software development" instead. Easily changed if the owner prefers
     * their original.
     */
    const val AI_DISCLOSURE_TITLE = "AI-ASSISTED DEVELOPMENT"

    const val AI_DISCLOSURE =
        "This game was developed with assistance from artificial intelligence " +
            "tools, used as educational and development aids while learning " +
            "programming and software development.\n\n" +
            "AI tools assisted with areas such as code development, " +
            "troubleshooting, documentation and learning. Final design " +
            "decisions, gameplay direction, testing and publication decisions " +
            "remain the responsibility of the developer.\n\n" +
            "This game is not generated, sponsored, endorsed, owned or " +
            "published by any artificial intelligence company."

    /**
     * Trademark and third-party terminology notice.
     *
     * Written to claim as little as possible, because every stronger phrasing
     * is a claim that would need evidence:
     *
     * - It says names "may be" the property of their respective owners rather
     *   than naming who owns what. Asserting ownership of a specific mark in a
     *   specific class is a legal claim, and "Blue Hat" in particular is a
     *   term whose status the owner explicitly asked not to be asserted.
     * - It never says anything is *licensed*. There is no evidence of a
     *   trademark licence anywhere in this repository, and claiming one that
     *   does not exist is worse than saying nothing at all.
     * - It names Red Hat and Microsoft only in the negative — as parties the
     *   game is *not* affiliated with — which is disclaiming, not invoking.
     */
    const val TRADEMARK_TITLE = "TRADEMARK AND THIRD-PARTY NOTICE"

    const val TRADEMARK_NOTICE =
        "Certain names, terms and trademarks referenced in this game may be " +
            "the property of their respective owners. Such references are used " +
            "solely for identification, commentary, educational context and " +
            "fictional representation within the game.\n\n" +
            "This game is independently developed and is not affiliated with, " +
            "sponsored by, endorsed by or approved by Red Hat, Microsoft or any " +
            "other third-party trademark owner.\n\n" +
            "All third-party trademarks, product names, company names and logos " +
            "remain the property of their respective owners."

    /**
     * What the game genuinely does not do, after the monetisation work.
     *
     * The previous version of this list is the reason `AboutTextTest` exists.
     * Each line here is either independent of build configuration or is
     * reported from it at runtime — nothing is asserted that a later release
     * could quietly falsify.
     */
    val DOES_NOT_DO: List<String> = listOf(
        "No real cryptocurrency, blockchain, wallet, mining or NFTs",
        "No gambling, loot boxes or randomised paid rewards",
        "No real hacking capability — nothing here touches a real network",
        "No personal data is collected by the game itself",
        "No account or login is required to play",
        "Plays fully offline"
    )

    const val CRYPTO_DISCLAIMER =
        "◇ Crypto and € Budget are fictional in-game resources. They have no " +
            "monetary value, cannot be exchanged for anything outside the game, " +
            "and cannot leave the device."

    /**
     * The honest version of the advertising and purchases disclosure.
     *
     * Reported from the build rather than written down, so it cannot say
     * "no ads" in a build that has them. A build with no AdMob ids really does
     * show none, and should be allowed to say so.
     */
    fun monetisationSummary(adsConfigured: Boolean, purchasesAvailable: Boolean): String {
        val parts = buildList {
            if (adsConfigured) {
                add(
                    "This build shows advertisements supplied by Google AdMob. " +
                        "Watching an ad to continue a run is always optional and " +
                        "is never required to play."
                )
            } else {
                add("This build shows no advertisements.")
            }
            if (purchasesAvailable) {
                add(
                    "Optional in-app purchases are available through Google Play. " +
                        "Nothing in the game is gated behind one."
                )
            } else {
                add("This build has no in-app purchases.")
            }
        }
        return parts.joinToString("\n\n")
    }
}
