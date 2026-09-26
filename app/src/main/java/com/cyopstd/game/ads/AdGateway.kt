package com.cyopstd.game.ads

/**
 * The game's two adverts: the interstitial after a lost run, and the rewarded
 * ad that buys a revive.
 *
 * An interface with a no-op implementation, for the same reason billing is:
 * ads need an AdMob account and unit ids that only the owner can create, and
 * nothing about them can be exercised in a test. What *can* be tested is
 * everything around them — when a revive is granted, who never sees an ad, and
 * the fact that the game continues either way — so the rules live in
 * [AdPolicy] where they can be checked.
 *
 * The interstitial was removed in 1.35.0 and restored in 1.37.0 at the
 * owner's instruction. Everything 1.35.0 added stays: every request, the
 * interstitial's included, is child-directed, non-personalized and rated G
 * (see [AdPrivacy]). There is still no banner, app-open or native format, and
 * none should be added without the owner asking.
 */
interface AdGateway {

    /** True when an interstitial is loaded and could be shown right now. */
    val isReady: Boolean get() = false

    /**
     * Show the lost-run interstitial.
     *
     * [onFinished] must be called exactly once whether the ad played, was
     * dismissed, or failed — the game is waiting on it, and a callback that
     * never arrives is a game that never returns to the menu.
     */
    fun showInterstitial(onFinished: () -> Unit) = onFinished()

    /** Load an interstitial ahead of the moment it is needed. */
    fun preload() = Unit

    /** True when a rewarded ad is loaded and could be shown right now. */
    val isRewardedReady: Boolean

    /**
     * Show a rewarded ad, and report whether the reward was actually earned.
     *
     * [onResult] must be called exactly once on every path — shown and earned,
     * shown and skipped, failed to show, none loaded, no activity attached, or
     * the SDK throwing. A player who is owed a revive and gets a dead screen
     * instead has lost a run to a bug.
     *
     * This is a different format from [showInterstitial] and the difference is
     * the whole point: an interstitial calls back on *dismissal*, and a
     * revive granted there is a revive granted for closing the ad.
     *
     * `earned` is true only when the SDK's own reward callback fired. Nothing
     * else may set it, and in particular dismissal must not: a revive granted
     * on dismissal is a revive granted for closing the ad after two seconds.
     */
    fun showRewarded(onResult: (earned: Boolean) -> Unit)

    /** Load a rewarded ad ahead of the moment it is needed. */
    fun preloadRewarded()
}

/**
 * The gateway that ships until AdMob is configured.
 *
 * It never has an ad, so the game plays exactly as it does today. That is the
 * important property: the ad path must be something the game survives the
 * absence of.
 */
class NoAdGateway : AdGateway {
    override val isRewardedReady: Boolean get() = false

    /**
     * No ad, so no reward. Not `true` "to be nice": the revive is worth
     * something, and a build with no ads must not hand it out free while a
     * configured build charges an ad for it. The UI asks [isRewardedReady]
     * first and never offers the button here, so this path is the backstop.
     */
    override fun showRewarded(onResult: (earned: Boolean) -> Unit) = onResult(false)
    override fun preloadRewarded() = Unit
}

/**
 * When the lost-run interstitial is allowed.
 *
 * The owner's rules (2026-09-26), and the only ones:
 *
 * 1. **Only on a loss**, and only once the player has answered NO to the
 *    revive (or no revive was on offer). Never mid-run, never on quitting,
 *    never on backgrounding the app. That part is the caller's: it decides
 *    *when* to ask; this decides *whether*.
 * 2. **Only if the run lasted at least three minutes of real play.** A player
 *    who lost inside three minutes goes straight back to the menu with no ad.
 * 3. **Paying to remove ads removes ads.**
 *
 * There is deliberately no cooldown between ads: every qualifying loss gets
 * one, and nothing else ever does.
 */
class AdPolicy(
    private val minRunSeconds: Float = MIN_RUN_SECONDS
) {
    fun shouldShowOnRunLost(adsRemoved: Boolean, ready: Boolean, runSeconds: Float): Boolean {
        if (adsRemoved) return false
        if (!ready) return false
        return runSeconds >= minRunSeconds
    }

    companion object {
        /** Three minutes of real, unpaused play. */
        const val MIN_RUN_SECONDS = 180f
    }
}
