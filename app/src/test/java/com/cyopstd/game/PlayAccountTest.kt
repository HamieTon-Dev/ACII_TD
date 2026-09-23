package com.cyopstd.game

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.cyopstd.game.save.PlayerIdentity
import com.cyopstd.game.store.BillingStatus
import com.cyopstd.game.store.Entitlements
import com.cyopstd.game.store.PlayLinks
import com.cyopstd.game.store.Sku
import com.cyopstd.game.ui.menu.PlayAccountScreen
import com.cyopstd.game.ui.theme.CyOpsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Google Play account screen.
 *
 * What is worth testing here is not the layout but the **claims**: this screen
 * exists to tell a player where their purchases live, and a wrong answer costs
 * them money or costs them trust. So the assertions are about honesty — a build
 * with no billing must not offer a working restore, a player who owns nothing
 * must not be told they own something, and the one thing that does *not*
 * survive a new phone must be labelled as such.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1920dp-h1080dp-land-xhdpi")
class PlayAccountTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(
        entitlements: Entitlements = Entitlements(),
        identity: PlayerIdentity = PlayerIdentity(),
        status: BillingStatus = BillingStatus.READY,
        adsConfigured: Boolean = true,
        onRestore: () -> Unit = {},
        onCallsign: () -> Unit = {}
    ) {
        compose.setContent {
            CyOpsTheme {
                PlayAccountScreen(
                    entitlements = entitlements,
                    identity = identity,
                    budget = 250,
                    status = status,
                    adsConfigured = adsConfigured,
                    backgroundAnimation = false,
                    onRestore = onRestore,
                    onOpenOrders = {},
                    onOpenListing = {},
                    onCallsign = onCallsign,
                    onBack = {}
                )
            }
        }
    }

    @Test
    fun `a build with no billing says so and cannot restore`() {
        show(status = BillingStatus.UNAVAILABLE)

        compose.onNodeWithText("NOT AVAILABLE").assertExists()
        // A restore button that looks live but does nothing is worse than a
        // dead one: the player presses it, sees nothing, and concludes their
        // purchases are gone.
        compose.onNodeWithText("RESTORE PURCHASES").assertIsNotEnabled()
    }

    @Test
    fun `a connected build offers a restore and reports it`() {
        var restored = 0
        show(status = BillingStatus.READY, onRestore = { restored++ })

        compose.onNodeWithText("CONNECTED").assertExists()
        compose.onNodeWithText("RESTORE PURCHASES").assertIsEnabled()
        compose.onNodeWithText("RESTORE PURCHASES").performClick()
        assertEquals(1, restored)
    }

    @Test
    fun `an unreachable Play does not imply the purchases are lost`() {
        show(status = BillingStatus.ERROR)

        compose.onNodeWithText("UNREACHABLE").assertExists()
        // Restore stays pressable: this state is usually just a missing
        // network, and the fix is to press it again once there is one.
        compose.onNodeWithText("RESTORE PURCHASES").assertIsEnabled()
    }

    @Test
    fun `owning nothing is reported as owning nothing`() {
        show(entitlements = Entitlements())
        compose.onNodeWithText("0 / ${Sku.coreSkins.size}").assertExists()
        compose.onNodeWithText("0 / ${Sku.backgrounds.size}").assertExists()
    }

    @Test
    fun `a bundle is counted as the skins it actually grants`() {
        // Compose allows one setContent per test, so the owning and not-owning
        // cases are two tests rather than one with a re-render.
        show(
            entitlements = Entitlements().plus(Sku.CORE_SKIN_PACK),
            identity = PlayerIdentity(username = "NULLSEC", highestWave = 42)
        )
        // Six of seven: NEONGRID is sold separately and the screen must not
        // imply it came with the pack.
        compose.onNodeWithText("${Sku.coreSkins.size - 1} / ${Sku.coreSkins.size}")
            .assertExists()
        compose.onNodeWithText("NULLSEC").assertExists()
        compose.onNodeWithText("42").assertExists()
    }

    @Test
    fun `removing ads changes what the screen says about ads`() {
        show(entitlements = Entitlements().plus(Sku.NO_ADS))
        compose.onNodeWithText("REMOVED · NONE").assertExists()
    }

    @Test
    fun `the screen distinguishes what survives a new phone from what does not`() {
        show()
        // The whole point of the screen. Purchases follow the Google account;
        // run progress does not, and a player deserves to know which is which
        // before they factory-reset a phone.
        compose.onNodeWithText("YOUR GOOGLE ACCOUNT").assertExists()
        compose.onNodeWithText("THIS DEVICE ONLY").assertExists()
    }

    @Test
    fun `the callsign is offered for registration and for change`() {
        var taps = 0
        show(identity = PlayerIdentity(), onCallsign = { taps++ })
        compose.onNodeWithText("REGISTER CALLSIGN").performClick()
        assertEquals(1, taps)
    }

    @Test
    fun `the Play links are real and ordered app-first`() {
        val orders = PlayLinks.orderHistoryUris()
        assertTrue(orders.isNotEmpty())
        assertEquals("https", orders.first().scheme)
        assertTrue(orders.first().toString().contains("orderhistory"))

        val listing = PlayLinks.listingUris("com.cyopstd.game")
        // market:// first so the Play app handles it; the web URL is the
        // fallback for a device without the Play app, which is precisely the
        // device where the first intent throws.
        assertEquals("market", listing.first().scheme)
        assertTrue(listing.all { it.toString().contains("com.cyopstd.game") })
        assertTrue(listing.any { it.scheme == "https" })
    }
}
