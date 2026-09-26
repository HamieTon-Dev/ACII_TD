package com.cyopstd.game

import java.io.File
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

    /**
     * Play Billing must stay new enough for Play to accept an upload.
     *
     * Google retires Billing Library versions on a schedule and refuses new
     * uploads below the floor. That arrived as a Play Console warning on a
     * finished bundle — *"currently uses Play Billing Library version 7.1.1
     * and must update to at least version 8.0.0"* — which is the worst moment
     * to find out, because the build is already made and the release is
     * already in progress.
     *
     * Asserting the floor rather than an exact version: upgrading must stay
     * free, and only *falling behind* is the failure. The floor is raised by
     * hand when Google raises theirs, which is a deliberate decision with a
     * date on it rather than something that drifts.
     */
    @Test
    fun `the Play Billing Library is new enough for Play to accept an upload`() {
        val catalog = File("../gradle/libs.versions.toml").readText()
        val declared = Regex("""^billing\s*=\s*"([0-9.]+)"""", RegexOption.MULTILINE)
            .find(catalog)
            ?.groupValues
            ?.get(1)
        assertTrue("no billing version found in the version catalog", declared != null)

        val major = declared!!.substringBefore('.').toInt()
        assertTrue(
            "Play Billing $declared is below the floor Play accepts " +
                "($MINIMUM_BILLING_MAJOR.x). Raise the version in " +
                "gradle/libs.versions.toml.",
            major >= MINIMUM_BILLING_MAJOR
        )
    }

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
    fun `build-time ids are values, not the names of variables`() {
        // build.gradle.kts once wrote "${'$'}gamesAppId", which Kotlin
        // reads as a literal dollar sign followed by text: every build shipped
        // the text "$gamesAppId" instead of the configured id, and cloud
        // save and the global board could never switch on. This checkout
        // configures none of them, so each must be exactly empty.
        for ((name, value) in listOf(
            "GAMES_APP_ID" to com.cyopstd.game.BuildConfig.GAMES_APP_ID,
            "LEADERBOARD_STANDARD_ID" to com.cyopstd.game.BuildConfig.LEADERBOARD_STANDARD_ID,
            "LEADERBOARD_HACK_AI_ID" to com.cyopstd.game.BuildConfig.LEADERBOARD_HACK_AI_ID
        )) {
            assertFalse("$name is the literal \"$value\"", value.startsWith("${'$'}"))
        }
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

    private companion object {
        /**
         * The major version Play required as of 2026-09-25. Google's notice
         * named 8.0.0 as the minimum; the app ships 9.x.
         */
        const val MINIMUM_BILLING_MAJOR = 8
    }
}
