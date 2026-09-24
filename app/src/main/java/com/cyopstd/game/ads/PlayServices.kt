package com.cyopstd.game.ads

import com.cyopstd.game.BuildConfig

/**
 * Whether this build has Google Play services configured, and which
 * implementations that implies.
 *
 * The ids are build configuration and default to empty, so a checkout with no
 * Play Console behind it selects the no-op gateways and behaves exactly as the
 * offline build did. That is deliberate: a developer, a CI run and a reviewer
 * should all be able to build and play this without an AdMob account, and the
 * *absence* of monetisation must never be a crash.
 */
object PlayServices {

    /** AdMob ids as supplied at build time. */
    val adMobAppId: String get() = BuildConfig.ADMOB_APP_ID
    val adMobInterstitialId: String get() = BuildConfig.ADMOB_INTERSTITIAL_ID
    val adMobRewardedId: String get() = BuildConfig.ADMOB_REWARDED_ID

    /**
     * True only when *both* ids are present and look like AdMob ids.
     *
     * The shape check is not pedantry: the manifest falls back to Google's
     * documented sample application id so the SDK can initialise at all, and
     * without this a build that forgot to set the real ids would happily serve
     * test ads to real players.
     */
    val adsConfigured: Boolean
        get() = adMobAppId.startsWith(ADMOB_PREFIX) &&
            adMobInterstitialId.startsWith(ADMOB_PREFIX) &&
            adMobAppId != SAMPLE_APP_ID &&
            adMobInterstitialId != SAMPLE_INTERSTITIAL_ID

    /**
     * True when this build can offer a revive on a rewarded ad.
     *
     * Deliberately separate from [adsConfigured]: they are different ad units
     * and different formats, and a build that has one and not the other must
     * do the half it can rather than the half it cannot. A build with no
     * rewarded id never shows the revive button at all — the rule from 1.16.0,
     * that a control which cannot act must not be offered.
     */
    val rewardedConfigured: Boolean
        get() = adsConfigured &&
            adMobRewardedId.startsWith(ADMOB_PREFIX) &&
            adMobRewardedId != SAMPLE_REWARDED_ID

    /** The Play Games Services project id, as supplied at build time. */
    val gamesAppId: String get() = BuildConfig.GAMES_APP_ID

    /**
     * True when this build has a real Play Games project behind it.
     *
     * Play Games project ids are numeric, so the shape check is exact rather
     * than a guess. An unconfigured build selects the no-op cloud-save gateway
     * and never calls `PlayGamesSdk.initialize`, which would otherwise throw on
     * the placeholder id the manifest has to carry.
     */
    val cloudSaveConfigured: Boolean
        get() = gamesAppId.isNotBlank() &&
            gamesAppId.all { it.isDigit() } &&
            gamesAppId != PLACEHOLDER_GAMES_APP_ID

    /** What `resValue` writes when no id is configured. Never a real project. */
    const val PLACEHOLDER_GAMES_APP_ID = "0"

    private const val ADMOB_PREFIX = "ca-app-pub-"

    /** Google's documented samples. Never to be shipped as if they were ours. */
    const val SAMPLE_APP_ID = "ca-app-pub-3940256099942544~3347511713"
    const val SAMPLE_INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712"
    const val SAMPLE_REWARDED_ID = "ca-app-pub-3940256099942544/5224354917"
}
