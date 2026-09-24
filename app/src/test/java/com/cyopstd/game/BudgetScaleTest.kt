package com.cyopstd.game

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.cyopstd.game.core.Balance
import com.cyopstd.game.save.GameRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The ×10 € rescale, and the save it has to carry across.
 *
 * The owner asked for ten times the € in every store pack, so a pack looks
 * worth what it costs. Ten times the € *in the packs alone* would have been a
 * tenfold buff to paying, so the whole economy moved together — wave payouts,
 * firmware costs and packs. These tests are the proof that it moved together
 * rather than in one place, because a mistake there is invisible until someone
 * buys a pack and maxes the upgrade tree with it.
 */
class BudgetScaleTest {

    @Test
    fun `a pack buys the same number of firmware levels as it did before`() {
        // The whole claim of the rescale in one assertion: €9,000 against the
        // new curve reaches the same level €900 reached against the old one.
        val oldCurve = { level: Int -> 3 + level }
        var oldLevels = 0
        var spent = 0L
        while (spent + oldCurve(oldLevels) <= 900L) {
            spent += oldCurve(oldLevels)
            oldLevels++
        }

        val newLevels = Balance.firmwareLevelsAffordable(
            fromLevel = 0,
            budget = 900L * Balance.BUDGET_SCALE
        )
        assertEquals(
            "the ×10 was supposed to change how the numbers look, not what " +
                "they buy",
            oldLevels,
            newLevels
        )
    }

    @Test
    fun `a wave milestone pays ten times what it used to`() {
        // 5 * milestone^2 was the old award; the scale is the only change.
        for (wave in listOf(10, 30, 100)) {
            val milestone = wave / Balance.BUDGET_MILESTONE_INTERVAL
            assertEquals(
                5 * milestone * milestone * Balance.BUDGET_SCALE,
                Balance.budgetAward(wave)
            )
        }
    }

    @Test
    fun `an old save is multiplied once, and only once`() = runBlocking {
        val store = TestStores.isolatedStore()
        val budgetKey = longPreferencesKey("budget")
        val lifetimeKey = longPreferencesKey("lifetime_budget")

        // A save from before the rescale: no scale stamp on it.
        store.edit { prefs ->
            prefs[budgetKey] = 900L
            prefs[lifetimeKey] = 2_400L
        }

        val repository = GameRepository(store)
        repository.migrateBudgetScale()

        var progress = repository.progress.first()
        assertEquals(9_000L, progress.budget)
        assertEquals(24_000L, progress.lifetimeBudgetEarned)

        // Running it again must not pay the player a second time. This is the
        // one that would have been found by a user, not by a developer.
        repository.migrateBudgetScale()
        repository.migrateBudgetScale()
        progress = repository.progress.first()
        assertEquals("the migration ran more than once", 9_000L, progress.budget)
        assertEquals(24_000L, progress.lifetimeBudgetEarned)
    }

    @Test
    fun `a fresh save is stamped rather than left to be migrated later`() = runBlocking {
        val repository = GameRepository(TestStores.isolatedStore())
        repository.migrateBudgetScale()
        assertEquals(0L, repository.progress.first().budget)

        // Earn something, then run the migration again as a later launch would.
        repository.awardBudget(500)
        repository.migrateBudgetScale()
        assertEquals(
            "a stamped save must never be multiplied again",
            500L,
            repository.progress.first().budget
        )
    }
}
