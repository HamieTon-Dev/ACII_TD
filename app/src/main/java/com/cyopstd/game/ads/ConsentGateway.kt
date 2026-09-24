package com.cyopstd.game.ads

import android.app.Activity

/**
 * Whether this build is allowed to ask for an ad yet.
 *
 * Google's User Messaging Platform (UMP) is the consent layer Play requires in
 * front of advertising in the regions that mandate one — the EEA and UK under
 * GDPR, and the US states with their own rules. It decides three things, and
 * they are genuinely separate:
 *
 * 1. **Whether a form must be shown at all.** Most of the world, no.
 * 2. **Whether ads may be requested** once consent has settled, which is not
 *    the same as "the form was shown": a player may decline.
 * 3. **Whether a Privacy Options entry point is required** in the app's own
 *    settings, so a player can change their mind later. Google tells us this
 *    per region; where it is required and missing, the listing is
 *    non-compliant.
 *
 * Modelled as an interface for the same reason [AdGateway] is: none of it can
 * run in a unit test, and the parts that *can* be tested — what the game does
 * with each answer — then have somewhere to live. [NoConsentGateway] is what a
 * build with no AdMob account gets, and it says no to everything, because a
 * build that cannot show ads has nothing to consent to.
 */
interface ConsentGateway {

    /**
     * True when the SDK may be initialised and an ad requested.
     *
     * Read *after* [refresh] settles. Reading it before is not an error and
     * returns whatever consent was stored from a previous session, which is
     * why the ad flow waits for the callback rather than polling this.
     */
    val canRequestAds: Boolean

    /** True when Google says this player must be offered a privacy control. */
    val privacyOptionsRequired: Boolean

    /**
     * Bring consent up to date, showing a form if one is required.
     *
     * [onSettled] is called exactly once on every path — form shown and
     * answered, form not required, network unavailable, SDK error. An app that
     * waits forever on a consent callback is an app that never starts, so
     * "settled" here means "stop waiting", not "consent was granted"; ask
     * [canRequestAds] for that.
     */
    fun refresh(activity: Activity, onSettled: () -> Unit)

    /**
     * Show the privacy options form on demand, from Settings.
     *
     * [onError] receives a human-readable reason, or null on success. It is
     * shown to the player as a transient message rather than swallowed: a
     * button that silently does nothing is worse than one that says why.
     */
    fun showPrivacyOptions(activity: Activity, onError: (String?) -> Unit)
}

/**
 * The gateway for a build with no AdMob configuration.
 *
 * No ads means nothing to consent to and no privacy entry point to offer.
 * [canRequestAds] is false rather than true: the only caller is the ad
 * gateway, which in such a build is the no-op one anyway, and false is the
 * answer that cannot cause an unconsented request if that ever changes.
 */
class NoConsentGateway : ConsentGateway {
    override val canRequestAds: Boolean get() = false
    override val privacyOptionsRequired: Boolean get() = false
    override fun refresh(activity: Activity, onSettled: () -> Unit) = onSettled()
    override fun showPrivacyOptions(activity: Activity, onError: (String?) -> Unit) =
        onError("This build has no advertising to configure.")
}
