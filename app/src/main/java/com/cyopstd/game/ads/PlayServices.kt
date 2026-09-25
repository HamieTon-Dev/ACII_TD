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
    val adMobRewardedId: String get() = BuildConfig.ADMOB_REWARDED_ID

    /**
     * True when this build deliberately uses Google's test ad units.
     *
     * Set by the build type, not by inspecting the ids: debug builds always
     * get the test units and release builds never do. Making it a build-config
     * constant rather than a runtime guess is what lets `ReleaseConfigTest`
     * assert the rule instead of restating it.
     */
    val usingTestAds: Boolean get() = BuildConfig.USING_TEST_ADS

    /**
     * True when this build may show ads at all.
     *
     * Two ways to be true and they are genuinely different:
     *
     * - a **debug** build, which always carries Google's test units, so the
     *   whole flow — load, show, reward, revive — can be exercised on a device
     *   without an AdMob account and without a single real impression;
     * - a **release** build with real ids configured through
     *   `cyops.admob.appId` and `cyops.admob.rewardedId`.
     *
     * A release build that carries a *sample* id is explicitly false. The
     * manifest has to fall back to the sample application id or the SDK
     * refuses to initialise, and without this check a release that forgot its
     * ids would serve Google's test creatives to real players — which is both
     * a policy violation and a revenue of exactly zero.
     */
    val adsConfigured: Boolean get() = adsConfigured(usingTestAds, adMobAppId, adMobRewardedId)

    /**
     * True when this build can offer a revive on a rewarded ad.
     *
     * Identical to [adsConfigured] now that the rewarded unit is the only ad
     * in the game. Both names are kept because they answer different
     * questions -- "does this build show advertising at all" and "can it offer
     * the revive" -- and a future format would make them diverge again.
     */
    val rewardedConfigured: Boolean get() = adsConfigured

    /**
     * The rule itself, as a function of its inputs rather than of the build.
     *
     * Split out because the interesting half cannot otherwise be tested. Unit
     * tests compile against the *debug* build config, so
     * `PlayServices.adsConfigured` can only ever answer the debug question --
     * and the question that matters for a release ("would a release with no
     * ids configured think it had ads?") had no way to be asked at all.
     */
    fun adsConfigured(
        usingTestAds: Boolean,
        appId: String,
        rewardedId: String
    ): Boolean = if (usingTestAds) {
        // A test-ad build is configured by definition: the ids are Google's
        // and they always work.
        appId == SAMPLE_APP_ID && rewardedId == SAMPLE_REWARDED_ID
    } else {
        appId.startsWith(ADMOB_PREFIX) &&
            rewardedId.startsWith(ADMOB_PREFIX) &&
            appId != SAMPLE_APP_ID &&
            rewardedId != SAMPLE_REWARDED_ID
    }

    /** Kept as the name the revive path reads. */
    fun rewardedConfigured(
        usingTestAds: Boolean,
        appId: String,
        rewardedId: String
    ): Boolean = adsConfigured(usingTestAds, appId, rewardedId)

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
