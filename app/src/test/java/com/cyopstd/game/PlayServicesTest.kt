package com.cyopstd.game

import com.cyopstd.game.ads.PlayServices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which gateways a build selects.
 *
 * This is the switch between "real money changes hands" and "nothing
 * happens", so the two ways it can be wrong both matter: a configured build
 * that silently uses the no-op gateway sells nothing, and an unconfigured
 * build that thinks it is configured serves Google's *test* ads to real
 * players — which is a policy violation, not a cosmetic bug.
 */
class PlayServicesTest {

    @Test
    fun `a release with no ids configured never claims to have ads`() {
        // The rule that matters, asked of the *release* inputs.
        //
        // This used to be asserted by reading `PlayServices.adsConfigured`
        // directly, which worked only while debug and release shared one
        // configuration. Unit tests compile against the debug build config, so
        // once debug gained its own test ids that assertion was answering a
        // different question than the one it claimed to. The rule is now a
        // function of its inputs, so both build types can be asked properly.
        assertFalse(
            "a release with no AdMob ids must select the no-op gateway",
            PlayServices.adsConfigured(usingTestAds = false, appId = "", interstitialId = "")
        )
        assertFalse(
            "a rewarded unit alone is not a configuration",
            PlayServices.rewardedConfigured(
                usingTestAds = false,
                appId = "",
                interstitialId = "",
                rewardedId = "ca-app-pub-1234567890123456/1111111111"
            )
        )
    }

    @Test
    fun `google's sample ids never count as a configured release`() {
        // The manifest falls back to the sample application id so the SDK can
        // initialise at all. Without this check, a release that forgot to set
        // the real ids would serve test ads to real players -- which earns
        // nothing and breaks AdMob policy.
        assertTrue(PlayServices.SAMPLE_APP_ID.startsWith("ca-app-pub-"))
        assertTrue(PlayServices.SAMPLE_INTERSTITIAL_ID.startsWith("ca-app-pub-"))

        assertFalse(
            "the sample ids look real but must not configure a release",
            PlayServices.adsConfigured(
                usingTestAds = false,
                appId = PlayServices.SAMPLE_APP_ID,
                interstitialId = PlayServices.SAMPLE_INTERSTITIAL_ID
            )
        )
        assertFalse(
            "a real app id with a sample rewarded unit is still a test ad",
            PlayServices.rewardedConfigured(
                usingTestAds = false,
                appId = "ca-app-pub-1234567890123456~1234567890",
                interstitialId = "ca-app-pub-1234567890123456/2222222222",
                rewardedId = PlayServices.SAMPLE_REWARDED_ID
            )
        )
    }

    @Test
    fun `a properly configured release is recognised`() {
        // The other way to be wrong: a build that is configured and silently
        // uses the no-op gateway sells nothing at all.
        assertTrue(
            PlayServices.rewardedConfigured(
                usingTestAds = false,
                appId = "ca-app-pub-1234567890123456~1234567890",
                interstitialId = "ca-app-pub-1234567890123456/2222222222",
                rewardedId = "ca-app-pub-1234567890123456/3333333333"
            )
        )
    }

    @Test
    fun `a debug build is configured, and only with Google's own units`() {
        // Debug carries the test units so the whole revive path can be tried
        // on a phone without an AdMob account. It must not accept anything
        // else under that flag.
        assertTrue(
            PlayServices.rewardedConfigured(
                usingTestAds = true,
                appId = PlayServices.SAMPLE_APP_ID,
                interstitialId = PlayServices.SAMPLE_INTERSTITIAL_ID,
                rewardedId = PlayServices.SAMPLE_REWARDED_ID
            )
        )
        assertFalse(
            "a test-ad build must not be pointed at production units",
            PlayServices.adsConfigured(
                usingTestAds = true,
                appId = "ca-app-pub-1234567890123456~1234567890",
                interstitialId = "ca-app-pub-1234567890123456/2222222222"
            )
        )
    }

    @Test
    fun `an unconfigured build never claims to have cloud save`() {
        // As checked in: no Play Games project id. The no-op gateway is
        // selected and PlayGamesSdk.initialize() is never called, which
        // matters because it throws on the placeholder id the manifest has to
        // carry for the SDK to even parse.
        assertFalse(PlayServices.cloudSaveConfigured)
    }

    @Test
    fun `the placeholder games id is never mistaken for a real project`() {
        assertEquals("0", PlayServices.PLACEHOLDER_GAMES_APP_ID)
        assertFalse(
            "the placeholder must not enable cloud save",
            PlayServices.PLACEHOLDER_GAMES_APP_ID.isNotBlank() &&
                PlayServices.gamesAppId == PlayServices.PLACEHOLDER_GAMES_APP_ID &&
                PlayServices.cloudSaveConfigured
        )
    }

    @Test
    fun `the shipped catalog ids are valid Play product ids`() {
        // These have to be created verbatim in the Play Console, so a typo
        // here is a product that can never be bought.
        for (sku in com.cyopstd.game.store.Sku.entries) {
            assertTrue(
                "'${sku.id}' is not a usable Play product id",
                sku.id.matches(Regex("[a-z][a-z0-9_.]{2,}"))
            )
            assertFalse("'${sku.id}' has uppercase", sku.id.any { it.isUpperCase() })
        }
    }
}
