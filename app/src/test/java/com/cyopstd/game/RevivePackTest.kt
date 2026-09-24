package com.cyopstd.game

import com.cyopstd.game.core.Balance
import com.cyopstd.game.store.Entitlements
import com.cyopstd.game.store.Sku
import com.cyopstd.game.ui.menu.STORE_SECTIONS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The REVIVE PACK, and the line between it and REMOVE ADS.
 *
 * "No ads" appearing on two different products is exactly the thing that
 * generates refund requests when a player assumes one covers the other. The
 * rule the owner set is that REMOVE ADS buys freedom from ads the player did
 * not ask for, and the revive is one they did — so it is asserted here rather
 * than left as a comment somebody later "tidies up".
 */
class RevivePackTest {

    @Test
    fun `the pack grants three revives per run`() {
        val owner = Entitlements(setOf(Sku.REVIVE_PACK.id))
        assertEquals(3, owner.revivesPerRun)
        assertTrue(owner.reviveAdsRemoved)
    }

    @Test
    fun `owning nothing leaves the free entitlement alone`() {
        val nobody = Entitlements()
        assertEquals(
            "a product should only ever say what it changes",
            null,
            nobody.revivesPerRun
        )
        assertFalse(nobody.reviveAdsRemoved)
        assertEquals(1, Balance.REVIVES_PER_RUN)
    }

    @Test
    fun `REMOVE ADS does not cover the revive ad`() {
        val adFree = Entitlements(setOf(Sku.NO_ADS.id))
        assertTrue("it still removes the ads it sells", adFree.adsRemoved)
        assertFalse(
            "REMOVE ADS buys freedom from ads the player did not ask for; " +
                "the revive is one they did",
            adFree.reviveAdsRemoved
        )
        assertEquals(null, adFree.revivesPerRun)
    }

    @Test
    fun `the starter pack's bundled no-ads does not smuggle in free revives`() {
        // STARTER_PACK unlocks no_ads, and a naive "adsRemoved implies revive
        // ads removed" would have handed three revives to a $4.99 bundle that
        // never claimed to sell them.
        val starter = Entitlements(Sku.unlockedBy(Sku.STARTER_PACK))
        assertTrue(starter.adsRemoved)
        assertFalse(starter.reviveAdsRemoved)
    }

    @Test
    fun `two products that touch revives do not stack`() {
        val both = Entitlements(setOf(Sku.REVIVE_PACK.id, Sku.NO_ADS.id))
        assertEquals(
            "revives must never add up into a number nobody priced",
            3,
            both.revivesPerRun
        )
    }

    @Test
    fun `every product that removes revive ads also says so in its summary`() {
        // A product that silently changes the revive rules is a support ticket.
        for (sku in Sku.entries) {
            if (!sku.removesReviveAds) continue
            assertTrue(
                "${sku.id} changes the revive rules without saying so",
                sku.summary.contains("revive", ignoreCase = true)
            )
        }
    }

    @Test
    fun `the euro packs still get better value as they get larger`() {
        // The ×10 preserves ratios, which is the point of the rescale -- but
        // this is the assertion that proves it rather than assuming it.
        val packs = listOf(Sku.BUDGET_SMALL, Sku.BUDGET_MEDIUM, Sku.BUDGET_LARGE)
        val perDollar = packs.map { sku ->
            val dollars = sku.fallbackPrice.removePrefix("$").toDouble()
            sku.grantsBudget / dollars
        }
        for (i in 1 until perDollar.size) {
            assertTrue(
                "${packs[i].id} is worse value per dollar than ${packs[i - 1].id}",
                perDollar[i] > perDollar[i - 1]
            )
        }
    }

    @Test
    fun `every product in the catalog is reachable in the store`() {
        // REVIVE_PACK was added to the catalog and to the Play Console table
        // before it was added to any screen. A product that exists, has a
        // price and can never be bought is worse than one that does not exist.
        val onScreen = STORE_SECTIONS.flatMap { it.items }.toSet() +
            Sku.coreSkins + Sku.backgrounds + Sku.SKIN_AGENTS_SPECTRUM
        val missing = Sku.entries.filterNot { it in onScreen }
        assertTrue(
            "not reachable from the store screen: ${missing.map { it.id }}",
            missing.isEmpty()
        )
    }

    @Test
    fun `every euro amount named in a title or summary matches what it grants`() {
        // The titles carry the numbers, so a rescale that misses one is a
        // product that lies about what it pays.
        val euros = Regex("€([\\d,]+)")
        for (sku in Sku.entries) {
            val claimed = (euros.findAll(sku.title) + euros.findAll(sku.summary))
                .map { it.groupValues[1].replace(",", "").toInt() }
                .toList()
            if (claimed.isEmpty()) continue
            assertTrue(
                "${sku.id} advertises €${claimed.joinToString()} but grants " +
                    "€${sku.grantsBudget}",
                claimed.all { it == sku.grantsBudget }
            )
        }
    }
}
