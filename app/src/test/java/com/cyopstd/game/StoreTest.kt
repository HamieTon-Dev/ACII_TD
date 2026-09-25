package com.cyopstd.game

import com.cyopstd.game.core.Balance
import com.cyopstd.game.save.PlayerIdentity
import com.cyopstd.game.store.CosmeticChoice
import com.cyopstd.game.store.Entitlements
import com.cyopstd.game.store.Sku
import com.cyopstd.game.store.SkuKind
import com.cyopstd.game.ui.theme.CoreSkin
import com.cyopstd.game.ui.theme.LivingBackground
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The store catalog and what owning things means.
 *
 * None of this needs Play, a network or a device, which is the point of keeping
 * the catalog as plain data: the rules about what a bundle grants and what a
 * player may select are exactly the rules that are easy to get quietly wrong
 * and expensive to get wrong in production.
 */
class StoreTest {

    @Test
    fun `every product id is unique and stable-looking`() {
        val ids = Sku.entries.map { it.id }
        assertEquals("duplicate product id", ids.size, ids.toSet().size)
        for (id in ids) {
            // Play product ids are lowercase alphanumeric plus _ and .
            assertTrue("'$id' is not a usable Play product id",
                id.matches(Regex("[a-z0-9_.]{3,}")))
        }
    }

    @Test
    fun `a bundle grants everything it advertises`() {
        val owned = Entitlements().plus(Sku.CORE_SKIN_PACK)
        for (skin in Sku.coreSkins.filter { it != Sku.CORE_SKIN_NEONGRID }) {
            assertTrue("${skin.id} was not granted by the pack", owned.owns(skin))
        }
        // Everything except the premium skin, which is sold on its own.
        assertEquals(Sku.coreSkins.size - 1, owned.coreSkins.size)
        assertFalse(
            "NEONGRID is the premium skin and must not come free with the pack",
            owned.owns(Sku.CORE_SKIN_NEONGRID)
        )

        val starter = Entitlements().plus(Sku.STARTER_PACK)
        assertTrue(starter.adsRemoved)
        assertTrue(starter.spectrumAgents)
        assertTrue(starter.owns(Sku.BG_DRIFT))

        // Asserted against the catalog rather than a literal: this test used
        // to say 3, which stayed green while two backgrounds were added to the
        // game and not to the pack that claims to contain all of them.
        val backgrounds = Entitlements().plus(Sku.BG_PACK)
        assertEquals(Sku.backgrounds.size, backgrounds.backgrounds.size)
        for (bg in Sku.backgrounds) {
            assertTrue("${bg.id} was not granted by ALL LIVING BACKGROUNDS",
                backgrounds.owns(bg))
        }
    }

    @Test
    fun `every bundle costs less than its parts`() {
        // If a pack is not cheaper than buying its contents separately, it is
        // not a pack, it is a trap.
        fun cents(price: String) = (price.removePrefix("$").toDouble() * 100).toInt()
        for (bundle in Sku.entries.filter { it.alsoUnlocks.size > 1 }) {
            val parts = bundle.alsoUnlocks.mapNotNull { Sku.byId(it) }
            val partsTotal = parts.sumOf { cents(it.fallbackPrice) }
            assertTrue(
                "${bundle.id} costs ${bundle.fallbackPrice} but its parts total " +
                    "${partsTotal / 100.0}",
                cents(bundle.fallbackPrice) < partsTotal
            )
        }
    }

    @Test
    fun `budget packs get better the more you spend`() {
        val packs = listOf(Sku.BUDGET_SMALL, Sku.BUDGET_MEDIUM, Sku.BUDGET_LARGE)
        fun cents(price: String) = (price.removePrefix("$").toDouble() * 100).toInt()
        val perCent = packs.map { it.grantsBudget.toDouble() / cents(it.fallbackPrice) }
        for (i in 1 until perCent.size) {
            assertTrue(
                "${packs[i].id} gives ${perCent[i]} € per cent against " +
                    "${packs[i - 1].id}'s ${perCent[i - 1]}",
                perCent[i] > perCent[i - 1]
            )
        }
    }

    @Test
    fun `only consumables may be bought twice`() {
        for (sku in Sku.entries) {
            val granted = Sku.unlockedBy(sku)
            if (sku.kind == SkuKind.CONSUMABLE) {
                assertFalse(
                    "${sku.id} is consumable but grants a permanent unlock of itself",
                    sku.id in granted
                )
            } else {
                assertTrue("${sku.id} is permanent but grants nothing", granted.isNotEmpty())
            }
        }
    }

    @Test
    fun `a selection the player does not own is dropped`() {
        // A refund, a restore onto another account, or a renamed product can
        // all leave a choice pointing at something unowned. The renderer must
        // never be handed one.
        val choice = CosmeticChoice(
            coreSkinId = Sku.CORE_SKIN_REACTOR.id,
            backgroundId = Sku.BG_AURORA.id,
            spectrumAgents = true
        )
        val nothing = choice.resolvedAgainst(Entitlements())
        assertEquals(null, nothing.coreSkinId)
        assertEquals(null, nothing.backgroundId)
        assertFalse(nothing.spectrumAgents)

        val some = choice.resolvedAgainst(Entitlements().plus(Sku.CORE_SKIN_REACTOR))
        assertEquals(Sku.CORE_SKIN_REACTOR.id, some.coreSkinId)
        assertEquals("an unowned background survived", null, some.backgroundId)
    }

    @Test
    fun `an unknown owned id survives a round trip`() {
        // A product removed or renamed in the console must not silently strip
        // a player of something they paid for.
        val owned = Entitlements(setOf("some_future_skin", Sku.NO_ADS.id))
        assertTrue(owned.owns("some_future_skin"))
        assertTrue(owned.adsRemoved)
    }

    @Test
    fun `the fifth speed is sold, not given`() {
        assertEquals(4, Balance.GAME_SPEEDS.size)
        assertEquals(5f, Balance.GAME_SPEEDS.last(), 0.001f)
        assertEquals("a locked player may only reach 3x", 3, Balance.speedCount(false))
        assertEquals(4, Balance.speedCount(true))
        assertFalse(Entitlements().fifthSpeedUnlocked)
        assertTrue(Entitlements().plus(Sku.SPEED_5X).fifthSpeedUnlocked)
    }

    @Test
    fun `usernames are cleaned on the way in`() {
        assertEquals("STEVE_01", PlayerIdentity.sanitize("steve_01"))
        assertEquals("ABC", PlayerIdentity.sanitize("  a b c  "))
        assertEquals("", PlayerIdentity.sanitize("!!!"))
        assertEquals(
            "a long name should be cut to the field width",
            PlayerIdentity.MAX_LENGTH,
            PlayerIdentity.sanitize("X".repeat(40)).length
        )
        assertFalse(PlayerIdentity.isValid("ab"))
        assertTrue(PlayerIdentity.isValid("abc"))
        assertFalse("an empty identity is not registered", PlayerIdentity().registered)
    }
    @Test
    fun `every paid cosmetic is locked behind owning it`() {
        // The guarantee: nothing that costs money can be selected, or reach
        // the renderer, without the product that grants it. Checked against
        // the real skin tables rather than a hand-written list, so a cosmetic
        // added later cannot quietly ship unlocked.
        val nothingOwned = Entitlements()

        for (skin in CoreSkin.purchasable) {
            val chosen = CosmeticChoice(coreSkinId = skin.productId)
                .resolvedAgainst(nothingOwned)
            assertEquals(
                "${skin.displayName} was selectable without being owned",
                null, chosen.coreSkinId
            )
            assertEquals(
                "${skin.displayName} reached the renderer unowned",
                CoreSkin.DEFAULT, CoreSkin.forProduct(chosen.coreSkinId)
            )
        }

        for (background in LivingBackground.purchasable) {
            val chosen = CosmeticChoice(backgroundId = background.productId)
                .resolvedAgainst(nothingOwned)
            assertEquals(
                "${background.displayName} was selectable without being owned",
                null, chosen.backgroundId
            )
            assertEquals(
                LivingBackground.NONE, LivingBackground.forProduct(chosen.backgroundId)
            )
        }

        assertFalse("the agent skin was on without being owned",
            CosmeticChoice(spectrumAgents = true).resolvedAgainst(nothingOwned).spectrumAgents)
    }

    @Test
    fun `owning a cosmetic lets it through`() {
        // The other half: the lock must not be so tight that a paying player
        // cannot use what they bought.
        val skin = CoreSkin.purchasable.first()
        val owned = Entitlements(setOf(skin.productId!!))
        val chosen = CosmeticChoice(coreSkinId = skin.productId).resolvedAgainst(owned)
        assertEquals(skin.productId, chosen.coreSkinId)
        assertEquals(skin, CoreSkin.forProduct(chosen.coreSkinId))
    }

    @Test
    fun `every purchasable cosmetic has a product in the catalog`() {
        // A skin with no product id in the store is unreachable; a product id
        // with no skin sells nothing. Both are silent failures.
        for (skin in CoreSkin.purchasable) {
            assertTrue(
                "core skin ${skin.displayName} has no catalog entry (${skin.productId})",
                Sku.byId(skin.productId!!) != null
            )
        }
        for (background in LivingBackground.purchasable) {
            assertTrue(
                "background ${background.displayName} has no catalog entry " +
                    "(${background.productId})",
                Sku.byId(background.productId!!) != null
            )
        }
    }


    // ------------------------------------------------ nothing unsellable

    @Test
    fun `REMOVE ADS is not sold, because there are no ads to remove`() {
        // Its entire function was suppressing the lost-run interstitial, and
        // that interstitial was removed in 1.35.0. Its own store text still
        // promises "No interstitial after a failed run, ever" -- a promise
        // about something the game no longer has.
        //
        // Hidden rather than deleted, pending an owner decision: the product
        // still grants EUR 5,000 and nobody has ever been able to buy it, so
        // both keeping and removing it are live options. What is not an option
        // is selling it as described.
        for (ads in listOf(true, false)) {
            val onSale = com.cyopstd.game.ui.menu.storeSections(adsConfigured = ads)
                .flatMap { it.items }
            assertFalse(
                "REMOVE ADS was offered with adsConfigured=$ads, but the game " +
                    "has no interstitials to remove",
                com.cyopstd.game.store.Sku.NO_ADS in onSale
            )
            assertTrue(
                "the revive pack must survive -- three revives per run is worth " +
                    "buying whether or not an ad ever stood in front of them",
                com.cyopstd.game.store.Sku.REVIVE_PACK in onSale
            )
        }
    }

    @Test
    fun `everything else in the catalogue is still on sale`() {
        val onSale = com.cyopstd.game.ui.menu.storeSections(adsConfigured = true)
            .flatMap { it.items }
        val expected = com.cyopstd.game.ui.menu.STORE_SECTIONS
            .flatMap { it.items }
            .filter { it != com.cyopstd.game.store.Sku.NO_ADS }
        assertEquals("the filter dropped something other than REMOVE ADS", expected, onSale)
    }

    @Test
    fun `no section is left empty by the filter`() {
        for (section in com.cyopstd.game.ui.menu.storeSections(adsConfigured = false)) {
            assertTrue("empty section '${section.title}' would render as a bare heading",
                section.items.isNotEmpty())
        }
    }
}
