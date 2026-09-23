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
    fun `an unconfigured build never claims to have ads`() {
        // This is the state of the repository as checked in: no ids set.
        assertFalse(
            "a build with no AdMob ids must select the no-op gateway",
            PlayServices.adsConfigured
        )
    }

    @Test
    fun `google's sample ids do not count as configured`() {
        // The manifest falls back to the sample application id so the SDK can
        // initialise at all. Without this check, a build that forgot to set
        // the real ids would serve test ads to real players.
        assertTrue(PlayServices.SAMPLE_APP_ID.startsWith("ca-app-pub-"))
        assertTrue(PlayServices.SAMPLE_INTERSTITIAL_ID.startsWith("ca-app-pub-"))
        assertFalse(
            "the sample ids look real but must not be treated as configured",
            PlayServices.adMobAppId == PlayServices.SAMPLE_APP_ID &&
                PlayServices.adsConfigured
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
