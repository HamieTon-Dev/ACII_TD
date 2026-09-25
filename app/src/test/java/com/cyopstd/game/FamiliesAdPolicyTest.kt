package com.cyopstd.game

import com.cyopstd.game.ads.AdPrivacy
import com.cyopstd.game.ads.PlayServices
import com.google.android.gms.ads.AgeRestrictedTreatment
import com.google.android.gms.ads.RequestConfiguration
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The advertising treatment, asserted rather than trusted.
 *
 * CyOps TD is a general-audience game that includes children, collects no age
 * and has no age gate. Every one of those is a product decision, and together
 * they mean the game has no basis on which to show anybody a personalized
 * advert. This file is what stops that decision being quietly undone by a
 * later edit — the settings it checks are invisible at runtime, produce no
 * error when wrong, and the only symptom of getting them wrong is a policy
 * email months later.
 */
class FamiliesAdPolicyTest {

    private val config = AdPrivacy.requestConfiguration()

    // ------------------------------------------------- 1, 2, 3: the treatment

    @Test
    fun `every ad request is child-directed`() {
        assertEquals(
            "ads must be treated as child-directed for every player, because " +
                "the game never learns which players are children",
            AgeRestrictedTreatment.CHILD,
            config.ageRestrictedTreatment
        )
    }

    @Test
    fun `the legacy COPPA tag says the same thing, not a different one`() {
        // Carried alongside the modern enum deliberately: the two must agree.
        // A build where the new API says CHILD and the old tag says FALSE
        // would be telling Google two different things about the same player.
        assertEquals(
            RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_TRUE,
            config.tagForChildDirectedTreatment
        )
    }

    @Test
    fun `maximum ad content rating is G`() {
        assertEquals(RequestConfiguration.MAX_AD_CONTENT_RATING_G, config.maxAdContentRating)
    }

    @Test
    fun `publisher privacy personalization is disabled`() {
        // This is the setting that actually switches off behavioural targeting
        // and remarketing, as opposed to merely declining consent for them.
        assertEquals(
            RequestConfiguration.PublisherPrivacyPersonalizationState.DISABLED,
            config.publisherPrivacyPersonalizationState
        )
    }

    @Test
    fun `no age is asserted in either direction`() {
        // Under-age-of-consent is a claim about a specific user. The game does
        // not know it about anyone, so it must say nothing -- `false` would be
        // a false statement and so would `true`.
        assertEquals(
            "the game must not assert that a player is, or is not, under the " +
                "age of consent",
            RequestConfiguration.TAG_FOR_UNDER_AGE_OF_CONSENT_UNSPECIFIED,
            config.tagForUnderAgeOfConsent
        )
    }

    @Test
    fun `the consent layer makes no age assertion either`() {
        // The UMP request used to carry setTagForUnderAgeOfConsent(false),
        // written when the game was documented as 13+. Nothing may assert an
        // age anywhere.
        val source = File("src/main/java/com/cyopstd/game/ads/UmpConsentGateway.kt").readText()
        val code = source.lines().filterNot {
            val t = it.trim()
            t.startsWith("*") || t.startsWith("//") || t.startsWith("/*")
        }
        assertTrue(
            "UmpConsentGateway asserts an age to Google: " +
                code.filter { it.contains("setTagForUnderAgeOfConsent") },
            code.none { it.contains("setTagForUnderAgeOfConsent") }
        )
    }

    // ------------------------------------------- 5, 10: what is NOT in here

    @Test
    fun `no interstitial, banner, app-open or native ad code remains`() {
        val forbidden = mapOf(
            "InterstitialAd" to "an interstitial",
            "AdView" to "a banner",
            "AppOpenAd" to "an app-open ad",
            "NativeAd" to "a native ad",
            "AdSize" to "a banner size"
        )
        val offences = mutableListOf<String>()
        for (file in File("src/main/java").walkTopDown().filter { it.extension == "kt" }) {
            file.readLines().forEachIndexed { index, raw ->
                val line = raw.trim()
                if (line.startsWith("*") || line.startsWith("//") || line.startsWith("/*")) {
                    return@forEachIndexed
                }
                for ((needle, what) in forbidden) {
                    if (line.contains(needle)) offences += "${file.name}:${index + 1} has $what"
                }
            }
        }
        assertTrue(
            "the rewarded revive is the only advertising this game may contain:\n" +
                offences.joinToString("\n"),
            offences.isEmpty()
        )
    }

    @Test
    fun `no mediation adapter or third-party ad network is present`() {
        // A mediation adapter would route requests to a network that has not
        // made any of the guarantees above, and Families requires every ad
        // source to be compatible.
        val gradle = File("build.gradle.kts").readText()
        val forbidden = listOf(
            "mediation", "applovin", "unity-ads", "unityads", "ironsource",
            "vungle", "adcolony", "chartboost", "inmobi", "pangle", "fyber",
            "facebook.ads", "audience-network", "mintegral", "liftoff"
        )
        val lower = gradle.lowercase()
        for (needle in forbidden) {
            assertFalse(
                "a third-party ad network or mediation adapter is declared: '$needle'",
                lower.contains(needle)
            )
        }
    }

    // ------------------------------------------------------- 7, 8, 9: the ids

    @Test
    fun `a debug build uses Google's official test ids`() {
        assertTrue(PlayServices.usingTestAds)
        assertEquals(PlayServices.SAMPLE_APP_ID, PlayServices.adMobAppId)
        assertEquals(
            "ca-app-pub-3940256099942544/5224354917",
            PlayServices.adMobRewardedId
        )
        assertTrue("debug must be able to exercise the revive", PlayServices.rewardedConfigured)
    }

    @Test
    fun `a release needs a real app id AND a real rewarded unit`() {
        val realApp = "ca-app-pub-1234567890123456~1234567890"
        val realUnit = "ca-app-pub-1234567890123456/3333333333"

        assertTrue(
            "a properly configured release must be recognised",
            PlayServices.rewardedConfigured(false, realApp, realUnit)
        )
        // Each half alone is not enough.
        assertFalse(PlayServices.rewardedConfigured(false, "", realUnit))
        assertFalse(PlayServices.rewardedConfigured(false, realApp, ""))
        // And a Google test id in a release is never "configured".
        assertFalse(
            PlayServices.rewardedConfigured(false, PlayServices.SAMPLE_APP_ID, realUnit)
        )
        assertFalse(
            PlayServices.rewardedConfigured(false, realApp, PlayServices.SAMPLE_REWARDED_ID)
        )
    }

    @Test
    fun `the build no longer asks for an interstitial unit`() {
        val gradle = File("build.gradle.kts").readText()
        assertFalse(
            "the build still reads an interstitial ad unit id",
            gradle.contains("cyops.admob.interstitialId")
        )
        assertTrue("the rewarded unit is still read", gradle.contains("cyops.admob.rewardedId"))
        assertTrue("the app id is still read", gradle.contains("cyops.admob.appId"))
    }

    // ----------------------------------------------------- 4: the manifest

    @Test
    fun `the source manifest strips the advertising id permission`() {
        // The merged manifest is what actually matters and is checked against
        // the built artifact by tools/verify-release.sh -- a unit test cannot
        // see it. What can be checked here is that the removal directive is
        // present and has not been dropped by an edit.
        val manifest = File("src/main/AndroidManifest.xml").readText()
        val adIdBlock = manifest.substringAfter("gms.permission.AD_ID").take(120)
        assertTrue(
            "AD_ID must be removed with tools:node=\"remove\", not merely absent " +
                "from our own manifest -- the Mobile Ads SDK declares it and it " +
                "merges in otherwise",
            adIdBlock.contains("tools:node=\"remove\"")
        )
    }
}
