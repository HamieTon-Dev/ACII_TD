package com.cyopstd.game

import android.app.Application
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.cyopstd.game.model.BossModifier
import com.cyopstd.game.model.BossVariant
import com.cyopstd.game.state.GameViewModel
import com.cyopstd.game.ui.game.BossDossier
import com.cyopstd.game.ui.game.BossDossierPanel
import com.cyopstd.game.ui.theme.CyOpsTheme
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The boss dossier.
 *
 * Every number in it already existed in the engine and none of it was
 * reachable: a boss could arrive carrying four modifiers, each with a written
 * description, and the player's only clue was a banner that flashed past before
 * the fight. "Why is this one not dying?" had an answer the game never gave.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1600dp-h760dp-land-mdpi")
class BossDossierTest {

    @get:Rule
    val compose = createComposeRule()

    private val sample = BossDossier(
        variant = BossVariant.GOOD_GAME,
        health = 420f,
        maxHealth = 1_000f,
        armor = 14f,
        speed = 38f,
        modifiers = listOf(BossModifier.ARMOR_PLATING, BossModifier.REGENERATION),
        revived = false,
        distanceToCore = 640f
    )

    private fun show(dossier: BossDossier = sample, onClose: () -> Unit = {}) {
        compose.setContent {
            CyOpsTheme {
                Box(Modifier.fillMaxSize()) {
                    BossDossierPanel(dossier = dossier, onClose = onClose)
                }
            }
        }
    }

    @Test
    fun `it names the boss and says what it is`() {
        show()
        compose.onNodeWithText(BossVariant.GOOD_GAME.displayName).assertIsDisplayed()
        compose.onNodeWithText(BossVariant.GOOD_GAME.glyph).assertIsDisplayed()
        compose.onNodeWithText(BossVariant.GOOD_GAME.signature).assertIsDisplayed()
    }

    @Test
    fun `it shows the numbers a player is actually asking about`() {
        show()
        compose.onNodeWithText("420 / 1000").assertIsDisplayed()
        compose.onNodeWithText("14").assertIsDisplayed()
        compose.onNodeWithText("38 u/s").assertIsDisplayed()
        compose.onNodeWithText("640 u").assertIsDisplayed()
    }

    @Test
    fun `every modifier is named and explained`() {
        show()
        // The tag alone is not an explanation -- the description is the answer
        // to "why is this one not dying".
        for (modifier in sample.modifiers) {
            compose.onNodeWithText("[${modifier.tag}]").assertIsDisplayed()
            compose.onNodeWithText(modifier.description).assertIsDisplayed()
        }
    }

    @Test
    fun `a clean fight says so rather than showing an empty box`() {
        show(sample.copy(modifiers = emptyList()))
        compose.onNodeWithText("None. This one is a straight fight.").assertIsDisplayed()
    }

    @Test
    fun `a reanimated boss says it will not come back again`() {
        show(sample.copy(variant = BossVariant.ZOMBIE, revived = true))
        compose.onNodeWithText("REANIMATED — it will not come back again").assertIsDisplayed()
    }

    @Test
    fun `it closes`() {
        var closed = 0
        show(onClose = { closed++ })
        compose.onNodeWithText("CLOSE").performClick()
        assertEquals(1, closed)
    }

    // ------------------------------------------------------- the live source

    @Test
    fun `there is no dossier when there is no boss`() {
        val viewModel = freshViewModel()
        viewModel.startNewGame()
        shadowOf(Looper.getMainLooper()).idle()
        assertNull("a dossier with no boss on the field", viewModel.bossDossier())
    }

    @Test
    fun `the dossier reports the boss that is actually on the field`() {
        val viewModel = freshViewModel()
        viewModel.startNewGame()
        shadowOf(Looper.getMainLooper()).idle()
        viewModel.engine.restore(
            wave = 4, serverHp = 100, crypto = 0, placements = emptyList(),
            attacksBlocked = 0, cryptoEarned = 0, bossesDefeated = 0,
            serverDamageTaken = 0, agentsDeployed = 0, agentUpgrades = 0
        )
        viewModel.engine.startNextWave()
        repeat(900) { viewModel.engine.update(1f / 60f, 1f) }

        val boss = viewModel.engine.enemies.items.firstOrNull { it.active && it.isBoss }
        assertNotNull("no boss spawned on a boss wave", boss)
        val dossier = viewModel.bossDossier()
        assertNotNull("a boss is on the field but there is no dossier", dossier)

        assertEquals(boss!!.variant, dossier!!.variant)
        assertEquals(boss.health, dossier.health, 0.01f)
        assertEquals(boss.maxHealth, dossier.maxHealth, 0.01f)
        assertEquals(boss.armor, dossier.armor, 0.01f)
        assertTrue("the distance to the core should be a real number", dossier.distanceToCore > 0f)
        // Exactly the modifiers it is carrying, no more.
        for (modifier in BossModifier.entries) {
            assertEquals(
                "dossier disagrees about ${modifier.tag}",
                boss.hasModifier(modifier),
                modifier in dossier.modifiers
            )
        }
    }

    @Test
    fun `the panel closes itself when the boss it describes is gone`() {
        val viewModel = freshViewModel()
        viewModel.startNewGame()
        shadowOf(Looper.getMainLooper()).idle()
        viewModel.engine.restore(
            wave = 4, serverHp = 100, crypto = 0, placements = emptyList(),
            attacksBlocked = 0, cryptoEarned = 0, bossesDefeated = 0,
            serverDamageTaken = 0, agentsDeployed = 0, agentUpgrades = 0
        )
        viewModel.engine.startNextWave()
        repeat(900) { viewModel.engine.update(1f / 60f, 1f) }
        assertNotNull("no boss spawned on a boss wave", viewModel.bossDossier())

        viewModel.toggleBossPanel()
        assertTrue("the panel should open on a boss", viewModel.showBossPanel)

        // Clear the field, then let the view model see it through its own
        // frame tick -- which is the only thing that refreshes the HUD.
        for (enemy in viewModel.engine.enemies.items) if (enemy.active) enemy.active = false
        viewModel.onFrame(1f / 60f)
        shadowOf(Looper.getMainLooper()).idle()
        assertNull("the field was not actually cleared", viewModel.bossDossier())

        assertFalse(
            "the dossier stayed open with nothing to describe, so the next " +
                "boss would throw it over the board unasked",
            viewModel.showBossPanel
        )
    }

    private fun freshViewModel(): GameViewModel {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = GameViewModel(application, TestStores.isolatedRepository())
        shadowOf(Looper.getMainLooper()).idle()
        return viewModel
    }
}
