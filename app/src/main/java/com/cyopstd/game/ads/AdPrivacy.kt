package com.cyopstd.game.ads

import com.google.android.gms.ads.AgeRestrictedTreatment
import com.google.android.gms.ads.RequestConfiguration

/**
 * The privacy treatment every ad request in this game carries.
 *
 * CyOps TD is a general-audience game that **includes children**, and it
 * deliberately does not collect the player's age and has no age gate. That
 * single product decision settles everything here: if you never learn who is
 * playing, the only defensible treatment is the most restrictive one, applied
 * to everybody, always.
 *
 * So there is no per-user branch in this file and there must never be one. A
 * branch implies knowledge the game does not have.
 *
 * ### What this produces
 *
 * | | |
 * | --- | --- |
 * | Advertising ID (AAID) | not read, not transmitted |
 * | Personalized ads | no |
 * | Behavioural targeting | no |
 * | Remarketing | no |
 * | Max ad content rating | G |
 *
 * ### Why it is set globally rather than per request
 *
 * [RequestConfiguration] is handed to `MobileAds.setRequestConfiguration`
 * **before** `MobileAds.initialize`, so it governs the SDK itself rather than
 * one call site. Per-request `Bundle` extras — the old `npa=1` trick — only
 * cover the requests somebody remembered to attach them to, and a request
 * added later silently misses out. A global configuration cannot be forgotten
 * by a future call site because there is nothing for that call site to
 * remember.
 */
object AdPrivacy {

    /**
     * The one configuration, built fresh so callers cannot mutate a shared one.
     *
     * **Why both `setAgeRestrictedTreatment` and `setTagForChildDirectedTreatment`.**
     * [AgeRestrictedTreatment.CHILD] is the current API and is what this is
     * built around. `TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE` is the older COPPA
     * signal and is set to the *same* meaning alongside it — not a
     * contradiction, the same statement twice.
     *
     * That redundancy is deliberate and is a judgement call worth stating. The
     * new enum is what Google documents now; the old tag is what every
     * previously shipped version of the SDK and its backends understand. For a
     * setting whose failure mode is "a child saw a behaviourally targeted
     * advert", carrying both costs nothing and removes a dependency on which
     * of the two the serving stack happens to honour. Delete the TFCD line if
     * you would rather track the current API exactly; it is one line and the
     * CHILD treatment above already carries the intent.
     *
     * **What is deliberately NOT set: `setTagForUnderAgeOfConsent`.** That flag
     * asserts the user *is known to be* under the applicable age of consent.
     * This game does not know that, about anybody. Asserting it would be a
     * false statement to Google, and it also changes what the consent SDK is
     * allowed to show. Leaving it unspecified is the honest position, and the
     * ads are already non-personalized regardless of what consent returns —
     * see [UmpConsentGateway].
     */
    fun requestConfiguration(): RequestConfiguration = RequestConfiguration.Builder()
        .setAgeRestrictedTreatment(AgeRestrictedTreatment.CHILD)
        .setTagForChildDirectedTreatment(
            RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE
        )
        .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_G)
        // Publisher-level opt out of personalization, which is what actually
        // turns off behavioural targeting and remarketing rather than merely
        // declining consent for them.
        .setPublisherPrivacyPersonalizationState(
            RequestConfiguration.PublisherPrivacyPersonalizationState.DISABLED
        )
        .build()
}
