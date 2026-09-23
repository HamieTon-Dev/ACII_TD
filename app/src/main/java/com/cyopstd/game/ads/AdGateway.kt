package com.cyopstd.game.ads

/**
 * Showing an interstitial after a failed run.
 *
 * An interface with a no-op implementation, for the same reason billing is:
 * ads need an AdMob account and unit ids that only the owner can create, and
 * nothing about them can be exercised in a test. What *can* be built and
 * tested is everything around them — when an ad is allowed to show, who never
 * sees one, and the fact that the game continues either way — so that lives in
 * [AdPolicy] where it can be checked.
 */
interface AdGateway {
    /** True when an ad is loaded and could be shown right now. */
    val isReady: Boolean

    /**
     * Show the interstitial.
     *
     * [onFinished] must be called exactly once whether the ad played, was
     * dismissed, or failed — the game is waiting on it, and a callback that
     * never arrives is a game that never returns to the menu.
     */
    fun showInterstitial(onFinished: () -> Unit)

    fun preload()
}

/**
 * The gateway that ships until AdMob is configured.
 *
 * It never has an ad and finishes immediately, so the game plays exactly as it
 * does today. That is the important property: the ad path must be something
 * the game survives the absence of.
 */
class NoAdGateway : AdGateway {
    override val isReady: Boolean get() = false
    override fun showInterstitial(onFinished: () -> Unit) = onFinished()
    override fun preload() = Unit
}

/**
 * When an interstitial is allowed.
 *
 * Separated from the gateway because this is the part with actual rules in it,
 * and the part a player will be angry about if it is wrong. Three of them:
 *
 * 1. **Paying to remove ads removes ads.** No exceptions, no "just this one".
 * 2. **One per failed run**, never mid-run and never on a run the player
 *    walked away from — an ad for quitting to the menu would punish the one
 *    action the player took deliberately.
 * 3. **A cooldown**, so a player losing repeatedly on an early wave is not
 *    shown an ad every thirty seconds. That player is the one most likely to
 *    uninstall.
 */
class AdPolicy(
    private val cooldownSeconds: Long = DEFAULT_COOLDOWN_SECONDS,
    private val now: () -> Long = { System.currentTimeMillis() / 1000 }
) {
    /**
     * Null until something has been shown.
     *
     * Deliberately nullable rather than a sentinel like `Long.MIN_VALUE`:
     * `now() - Long.MIN_VALUE` overflows to a negative number, so the cooldown
     * check failed on the very first run of a session and the player never saw
     * the first ad. "Nothing has happened yet" is a different state from "it
     * happened a long time ago" and is worth modelling as one.
     */
    private var lastShownAt: Long? = null

    fun shouldShowOnRunLost(adsRemoved: Boolean, ready: Boolean): Boolean {
        if (adsRemoved) return false
        if (!ready) return false
        val last = lastShownAt ?: return true
        return now() - last >= cooldownSeconds
    }

    fun recordShown() {
        lastShownAt = now()
    }

    /** Seconds until an ad could next be shown; zero when one could now. */
    fun secondsUntilEligible(): Long {
        val last = lastShownAt ?: return 0
        return (cooldownSeconds - (now() - last)).coerceAtLeast(0)
    }

    companion object {
        const val DEFAULT_COOLDOWN_SECONDS = 180L
    }
}
