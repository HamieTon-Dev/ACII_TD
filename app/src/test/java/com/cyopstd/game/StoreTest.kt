package com.cyopstd.game

import com.cyopstd.game.core.Balance
import com.cyopstd.game.save.PlayerIdentity
import com.cyopstd.game.store.CosmeticChoice
import com.cyopstd.game.store.Entitlements
import com.cyopstd.game.store.Sku
import com.cyopstd.game.store.SkuKind
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
        for (skin in Sku.coreSkins) {
            assertTrue("${skin.id} was not granted by the pack", owned.owns(skin))
        }
        assertEquals(3, owned.coreSkins.size)

        val starter = Entitlements().plus(Sku.STARTER_PACK)
        assertTrue(starter.adsRemoved)
        assertTrue(starter.spectrumAgents)
        assertTrue(starter.owns(Sku.BG_DRIFT))

        val backgrounds = Entitlements().plus(Sku.BG_PACK)
        assertEquals(3, backgrounds.backgrounds.size)
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
}
