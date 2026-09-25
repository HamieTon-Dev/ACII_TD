package com.cyopstd.game.ads

/**
 * The rewarded ad that buys a revive. The only advertising in this game.
 *
 * An interface with a no-op implementation, for the same reason billing is:
 * ads need an AdMob account and unit ids that only the owner can create, and
 * nothing about them can be exercised in a test. What *can* be tested is
 * everything around them — when a revive is granted, who never sees an ad, and
 * the fact that the game continues either way.
 *
 * **There is deliberately no interstitial, banner, app-open or native format
 * here, and none should be added.** The game shows advertising in exactly one
 * place: a button the player presses, on the game-over screen, to continue a
 * run they have just lost. That is the whole surface. A game whose audience
 * includes children should not carry an advert the player did not ask for, and
 * an unsolicited full-screen interstitial after a lost run — which this game
 * used to show — is precisely that.
 */
interface AdGateway {

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
