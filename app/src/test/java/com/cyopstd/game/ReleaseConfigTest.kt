package com.cyopstd.game

import com.cyopstd.game.ads.PlayServices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules about which ad ids a build may carry.
 *
 * These are the sort of thing that is obviously true when you write the build
 * file and silently false eight months later, and getting them wrong is not a
 * bug the game can report: a release serving Google's test creatives earns
 * nothing and breaks AdMob's policy, and a debug build requesting production
 * ads files invalid traffic against the owner's own account.
 *
 * Unit tests run against the **debug** build config, so what can be asserted
 * here directly is the debug half. The release half is asserted by construction
 * — the ids come from the build type, so the two cases are mutually exclusive
 * by the same switch — and by `tools/verify-release-ads.sh`, which reads the
 * ids back out of the built artifact.
 */
class ReleaseConfigTest {

    @Test
    fun `a debug build uses Google's published test units and nothing else`() {
        assertTrue("debug builds must be marked as test-ad builds", PlayServices.usingTestAds)
        assertEquals(PlayServices.SAMPLE_APP_ID, PlayServices.adMobAppId)
        assertEquals(
            "the rewarded unit must be Google's documented Android test unit",
            "ca-app-pub-3940256099942544/5224354917",
            PlayServices.adMobRewardedId
        )
    }

    @Test
    fun `the test rewarded unit is the one Google publishes for Android`() {
        // Written out rather than referenced so that a typo in the constant
        // fails here instead of at 3am against a live account.
        assertEquals("ca-app-pub-3940256099942544/5224354917", PlayServices.SAMPLE_REWARDED_ID)
        assertEquals("ca-app-pub-3940256099942544~3347511713", PlayServices.SAMPLE_APP_ID)
    }

    @Test
    fun `a debug build can exercise the whole rewarded revive path`() {
        // The point of shipping test units in debug: the revive is not
        // something that can only be tried in production.
        assertTrue(PlayServices.adsConfigured)
        assertTrue(PlayServices.rewardedConfigured)
    }

    @Test
    fun `an app id and an ad unit id are never confused`() {
        // A tilde separates the application id; a slash separates an ad unit.
        // Pasting one where the other belongs is the classic AdMob mistake and
        // fails at runtime with an unhelpful message, so it is worth a test
        // that says which is which.
        assertTrue("an application id uses a tilde", PlayServices.SAMPLE_APP_ID.contains('~'))
        assertFalse("an application id has no slash", PlayServices.SAMPLE_APP_ID.contains('/'))
        for (unit in listOf(PlayServices.SAMPLE_INTERSTITIAL_ID, PlayServices.SAMPLE_REWARDED_ID)) {
            assertTrue("an ad unit id uses a slash", unit.contains('/'))
            assertFalse("an ad unit id has no tilde", unit.contains('~'))
        }
    }

}
