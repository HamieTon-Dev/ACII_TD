package com.cyopstd.game

import android.app.Application
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.cyopstd.game.core.WorldGeometry
import com.cyopstd.game.engine.GameEngine
import com.cyopstd.game.engine.PlacementResult
import com.cyopstd.game.model.Agent
import com.cyopstd.game.model.AgentType
import androidx.test.core.app.ApplicationProvider
import com.cyopstd.game.state.GameViewModel
import com.cyopstd.game.ui.game.AgentManagementPanel
import com.cyopstd.game.ui.game.GameScreen
import com.cyopstd.game.ui.game.TutorialOverlay
import com.cyopstd.game.ui.theme.CyOpsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The two panels a player spends a match inside.
 *
 * Both of these were reported by the player rather than found here, and both
 * failures were the same shape: something they needed was real, and was out of
 * reach. The upgrade buttons sat below the fold of a scrolling card with
 * nothing to say they were there, and the tutorial's only SKIP lived inside a
 * card that several steps do not draw.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1600dp-h760dp-land-mdpi")
class InGamePanelsTest {

    @get:Rule
    val compose = createComposeRule()

    private fun agent(level: Int = 20): Agent {
        val engine = GameEngine()
        engine.startNewRun()
        val node = WorldGeometry.nodes.first {
            engine.placeAgent(AgentType.FIREWALL, it.id) == PlacementResult.SUCCESS
        }
        return engine.agentAt(node.id)!!.also { it.level = level }
    }

    private fun panel(
        level: Int = 20,
        crypto: Int = 5_000,
        onUpgrade: (Int) -> Unit = {},
        onSell: () -> Unit = {},
        onClose: () -> Unit = {}
    ) {
        compose.setContent {
            CyOpsTheme {
                Box(Modifier.fillMaxSize()) {
                    AgentManagementPanel(
                        agent = agent(level),
                        crypto = crypto,
                        affordableLevels = 8,
                        onUpgrade = onUpgrade,
                        onSell = onSell,
                        onCycleTargeting = {},
                        onClose = onClose,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    )
                }
            }
        }
    }

    @Test
    fun `every action is on screen without scrolling`() {
        // The report: "nothing indicates the players to scroll down to upgrade
        // towers". The fix is that there is nothing to scroll to -- the
        // actions live in their own column, outside the scrolling one.
        panel()
        compose.onNodeWithText("+1").assertIsDisplayed()
        compose.onNodeWithText("+10").assertIsDisplayed()
        compose.onNodeWithText("MAX +8").assertIsDisplayed()
        compose.onNodeWithText("CLOSE").assertIsDisplayed()
        compose.onNodeWithText("UPGRADE COST").assertIsDisplayed()
    }

    @Test
    fun `the upgrade buttons work from where they are`() {
        var bought = 0
        panel(onUpgrade = { bought += it })

        compose.onNodeWithText("+1").performClick()
        compose.onNodeWithText("+10").performClick()
        assertEquals(11, bought)
    }

    @Test
    fun `a maxed agent offers no upgrade, only sell and close`() {
        panel(level = com.cyopstd.game.core.Balance.MAX_AGENT_LEVEL)
        compose.onNodeWithText("MAXIMUM LEVEL REACHED").assertIsDisplayed()
        compose.onNodeWithText("CLOSE").assertIsDisplayed()
    }

    @Test
    fun `the tutorial can be skipped from a step that draws no buttons`() {
        // Step 1 waits for the player to tap AGENTS and shows no CONTINUE. The
        // old SKIP lived beside CONTINUE, so on exactly these steps there was
        // no way out of the tutorial at all.
        compose.setContent {
            CyOpsTheme {
                Box(Modifier.fillMaxSize()) {
                    TutorialOverlay(step = 1, onAdvance = {})
                }
            }
        }
        // The card itself carries no buttons here...
        compose.onNodeWithText("CONTINUE").assertDoesNotExist()
        // ...which is why SKIP is the screen's, not the card's. It is placed
        // by GameScreen in the top-right corner; this asserts the card does
        // not claim to own it.
        compose.onNodeWithText("SKIP").assertDoesNotExist()
    }

    @Test
    fun `the game screen puts SKIP in the corner for the whole tutorial`() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = GameViewModel(application, TestStores.isolatedRepository())
        shadowOf(Looper.getMainLooper()).idle()
        viewModel.startNewGame()
        shadowOf(Looper.getMainLooper()).idle()

        // The match screen drives the simulation from the frame clock, so it
        // never goes idle. The clock is driven by hand instead of waiting for
        // a quiescence that is never coming.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CyOpsTheme {
                GameScreen(viewModel = viewModel, onExitToMenu = {}, onOpenSettings = {})
            }
        }
        compose.mainClock.advanceTimeBy(64)

        // Step 0 explains and offers CONTINUE; SKIP is in the corner either
        // way, which is the point -- it does not come and go with the card.
        compose.onNodeWithText("SKIP \u00D7").assertIsDisplayed()

        viewModel.advanceTutorial()
        shadowOf(Looper.getMainLooper()).idle()
        compose.mainClock.advanceTimeBy(64)

        // Step 1 waits for a tap on AGENTS and draws no buttons of its own.
        compose.onNodeWithText("SKIP \u00D7").assertIsDisplayed()
        compose.onNodeWithText("SKIP \u00D7").performClick()
        assertEquals(-1, viewModel.tutorialStep)
    }

    @Test
    fun `an explaining step still offers CONTINUE`() {
        compose.setContent {
            CyOpsTheme {
                Box(Modifier.fillMaxSize()) {
                    TutorialOverlay(step = 0, onAdvance = {})
                }
            }
        }
        compose.onNodeWithText("CONTINUE").assertIsDisplayed()
    }
}
