package com.cyopstd.game

import android.app.Application
import android.os.Looper
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.cyopstd.game.core.Balance
import com.cyopstd.game.core.Maps
import com.cyopstd.game.core.WorldGeometry
import com.cyopstd.game.model.AgentType
import com.cyopstd.game.state.GameViewModel
import com.cyopstd.game.ui.game.GameScreen
import com.cyopstd.game.ui.game.TutorialBriefing
import com.cyopstd.game.ui.game.TutorialGate
import com.cyopstd.game.ui.game.TutorialScript
import com.cyopstd.game.ui.game.TutorialTarget
import com.cyopstd.game.ui.theme.CyOpsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The guided first run.
 *
 * The owner asked for four things: forced placements, arrows that point at the
 * real readouts, a SKIP button that gets out of the way, and an optional
 * briefing. The arrows are proved against pixels in `FieldStatusAnchorTest`;
 * this covers the rest, plus the one thing that would make the whole feature
 * unfinishable — a tutorial that asks for four agents the player cannot afford.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1600dp-h760dp-land-mdpi")
class TutorialTest {

    @get:Rule
    val compose = createComposeRule()

    // ------------------------------------------------------------ the script

    @Test
    fun `the forced placements are affordable on the starting purse`() {
        // The one that would make the tutorial impossible to finish. Agent
        // costs and the starting crypto are edited by different people for
        // different reasons, and nothing else would notice them crossing.
        assertTrue(
            "the guided run asks for ${TutorialScript.requiredSpend} crypto but " +
                "a new run starts with ${Balance.STARTING_CRYPTO}",
            TutorialScript.requiredSpend <= Balance.STARTING_CRYPTO
        )
    }

    @Test
    fun `it forces two firewalls and two tarpits, as asked`() {
        val firewalls = TutorialScript.stepAt(TutorialScript.PLACE_FIREWALLS)
        assertEquals(AgentType.FIREWALL, firewalls?.requiresAgent)
        assertEquals(2, firewalls?.requiresCount)

        val tarpits = TutorialScript.stepAt(TutorialScript.PLACE_TARPITS)
        assertEquals(AgentType.TARPIT, tarpits?.requiresAgent)
        assertEquals(2, tarpits?.requiresCount)
    }

    @Test
    fun `the crypto card carries the owner's words`() {
        val card = TutorialScript.stepAt(TutorialScript.CRYPTO_READOUT)
        assertNotNull(card)
        assertTrue(
            "the crypto copy was specified rather than described, so it is " +
                "quoted rather than paraphrased",
            card!!.body.contains("earned for every kill and every wave completed", true)
        )
        assertEquals(TutorialTarget.CRYPTO_READOUT, card.target)
    }

    @Test
    fun `the readouts are explained before the placements spend the crypto`() {
        // Ordering with a reason: the card points at "◇ 120", and the forced
        // placements cost exactly the starting purse. Explain afterwards and
        // the arrow points at ◇ 0.
        assertTrue(
            "the crypto step must come before anything is bought",
            TutorialScript.CRYPTO_READOUT < TutorialScript.PLACE_FIREWALLS
        )
        assertEquals(
            "and it should be pointing at the full starting purse",
            Balance.STARTING_CRYPTO,
            Balance.STARTING_CRYPTO - 0
        )
    }

    @Test
    fun `a step that waits for an action offers no button to press`() {
        // A CONTINUE that does nothing until you do something else is a button
        // that looks broken.
        for ((index, step) in TutorialScript.steps.withIndex()) {
            if (step.gate != TutorialGate.ACTION) continue
            assertTrue(
                "step $index waits for an action; it must not also claim to " +
                    "advance on acknowledgement",
                step.requiresAgent != null || step.target == TutorialTarget.NONE
            )
        }
    }

    // ------------------------------------------------------- driving it

    private fun freshViewModel(): GameViewModel {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = GameViewModel(application, TestStores.isolatedRepository())
        shadowOf(Looper.getMainLooper()).idle()
        viewModel.startNewGame()
        shadowOf(Looper.getMainLooper()).idle()
        return viewModel
    }

    private fun GameViewModel.place(type: AgentType, nodeIndex: Int) {
        choosePendingAgent(type)
        val node = Maps.PERIMETER.nodes[nodeIndex]
        onBattlefieldTap(Offset(node.x, node.y))
    }

    @Test
    fun `the second placement is what clears the step, not the first`() {
        val viewModel = freshViewModel()
        viewModel.toggleDeployPanel()
        assertEquals(TutorialScript.PICK_FIREWALL, viewModel.tutorialStep)

        viewModel.place(AgentType.FIREWALL, 10)
        assertEquals(
            "one is not two",
            TutorialScript.PLACE_FIREWALLS,
            viewModel.tutorialStep
        )

        viewModel.place(AgentType.FIREWALL, 12)
        assertEquals(
            "the second one moves the script on",
            TutorialScript.PICK_TARPIT,
            viewModel.tutorialStep
        )
    }

    @Test
    fun `selling one back does not count as having built it`() {
        // The count comes from the board, not from a tally. A player who
        // places, sells and places again has one, and a counter incremented on
        // each placement would have said two.
        val viewModel = freshViewModel()
        viewModel.toggleDeployPanel()
        viewModel.place(AgentType.FIREWALL, 10)

        val node = Maps.PERIMETER.nodes[10]
        viewModel.onBattlefieldTap(Offset(node.x, node.y))
        viewModel.sellSelectedAgent()

        viewModel.place(AgentType.FIREWALL, 12)
        assertEquals(
            "the board holds one FIREWALL, so the step is not done",
            TutorialScript.PLACE_FIREWALLS,
            viewModel.tutorialStep
        )
    }

    @Test
    fun `picking the wrong agent says so instead of moving on`() {
        val viewModel = freshViewModel()
        viewModel.toggleDeployPanel()
        assertEquals(TutorialScript.PICK_FIREWALL, viewModel.tutorialStep)

        viewModel.choosePendingAgent(AgentType.TARPIT)
        assertEquals(
            "the guided run is forced, so the wrong pick must not advance it",
            TutorialScript.PICK_FIREWALL,
            viewModel.tutorialStep
        )
        assertNotNull("and it must say why nothing happened", viewModel.transientMessage)
    }

    @Test
    fun `the whole guided run can be completed`() {
        // End to end on the real view model: every card, both forced pairs,
        // and the crypto to pay for them.
        val viewModel = freshViewModel()
        viewModel.advanceTutorial()   // wave readout
        viewModel.advanceTutorial()   // crypto readout
        viewModel.advanceTutorial()   // briefing offer
        assertEquals(TutorialScript.BRIEFING_OFFER, viewModel.tutorialStep)
        viewModel.answerBriefing(wanted = true)
        assertEquals(TutorialScript.BRIEFING, viewModel.tutorialStep)
        viewModel.advanceTutorial()
        assertEquals(TutorialScript.OPEN_ROSTER, viewModel.tutorialStep)

        viewModel.toggleDeployPanel()
        viewModel.place(AgentType.FIREWALL, 10)
        viewModel.place(AgentType.FIREWALL, 12)
        viewModel.place(AgentType.TARPIT, 14)
        viewModel.place(AgentType.TARPIT, 16)

        assertEquals(TutorialScript.START_WAVE, viewModel.tutorialStep)
        assertEquals(
            "the guided run should leave the player with exactly what it asked for",
            4,
            viewModel.engine.activeAgentCount()
        )

        viewModel.startNextWave()
        assertEquals("starting the wave ends the tutorial", -1, viewModel.tutorialStep)
    }

    @Test
    fun `declining the briefing skips straight to playing`() {
        val viewModel = freshViewModel()
        repeat(3) { viewModel.advanceTutorial() }
        viewModel.answerBriefing(wanted = false)
        assertEquals(TutorialScript.OPEN_ROSTER, viewModel.tutorialStep)
    }

    // ---------------------------------------------------------- the briefing

    @Test
    fun `the briefing is generated from the catalog rather than written twice`() {
        val body = TutorialBriefing.body()
        // Named things, so a renamed threat or a new boss cannot leave the
        // briefing quietly describing a game that no longer exists.
        assertTrue(body.contains(AgentType.TARPIT.displayName))
        assertTrue("JAM is a thing that happens with no message", body.contains("JAM"))
        for (line in TutorialBriefing.bosses()) {
            assertTrue("the briefing dropped a boss: $line", body.contains(line.trim()))
        }
    }

    @Test
    fun `the briefing names FIREWALL as the answer to jamming`() {
        // It is the only jam-proof agent, and a player who does not know that
        // has no way of finding out.
        assertTrue(AgentType.FIREWALL.immuneToJam)
        assertTrue(
            TutorialBriefing.mechanics().any {
                it.contains("JAM") && it.contains(AgentType.FIREWALL.displayName)
            }
        )
    }

    // ------------------------------------------------------------- SKIP

    private fun showGameScreen(viewModel: GameViewModel) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CyOpsTheme {
                GameScreen(viewModel = viewModel, onExitToMenu = {}, onOpenSettings = {})
            }
        }
        compose.mainClock.advanceTimeBy(64)
    }

    @Test
    fun `SKIP moves off the readouts on the steps that point at them`() {
        // The owner's words: the SKIP button must not block "Wave 1" and
        // "◇120" when the tutorial is pointing at them. SKIP's home is the top
        // right, which is exactly where they are.
        val viewModel = freshViewModel()
        showGameScreen(viewModel)

        val home = compose.onNodeWithText("SKIP ×").fetchSemanticsNode().boundsInRoot

        viewModel.advanceTutorial()   // the wave readout, top right
        shadowOf(Looper.getMainLooper()).idle()
        compose.mainClock.advanceTimeBy(64)

        val moved = compose.onNodeWithText("SKIP ×").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "SKIP stayed at $moved while the arrow pointed into the same corner",
            moved.top > home.top
        )
        compose.onNodeWithText("SKIP ×").assertIsDisplayed()
    }

    @Test
    fun `SKIP is reachable on every step of the tutorial`() {
        val viewModel = freshViewModel()
        showGameScreen(viewModel)

        // Walked, not jumped: the tutorial's own transitions are what decide
        // which steps a player can actually be standing on.
        compose.onNodeWithText("SKIP ×").assertIsDisplayed()

        val visited = mutableListOf(viewModel.tutorialStep)
        fun settle() {
            shadowOf(Looper.getMainLooper()).idle()
            compose.mainClock.advanceTimeBy(64)
            compose.onNodeWithText("SKIP ×").assertIsDisplayed()
            visited += viewModel.tutorialStep
        }

        viewModel.advanceTutorial(); settle()   // wave readout
        viewModel.advanceTutorial(); settle()   // crypto readout
        viewModel.advanceTutorial(); settle()   // briefing offer
        viewModel.answerBriefing(wanted = true); settle()
        viewModel.advanceTutorial(); settle()   // open roster
        viewModel.toggleDeployPanel(); settle()
        viewModel.place(AgentType.FIREWALL, 10); settle()
        viewModel.place(AgentType.FIREWALL, 12); settle()
        viewModel.place(AgentType.TARPIT, 14); settle()
        viewModel.place(AgentType.TARPIT, 16); settle()

        assertEquals(
            "every card in the script should have been stood on",
            TutorialScript.steps.indices.toList(),
            visited.distinct().sorted()
        )
    }

    @Test
    fun `skipping it and starting again does not hand it back`() {
        // The flag is written asynchronously. A progress emission from before
        // that write still says "not completed", and it used to overwrite the
        // in-memory flag -- so skip, lose, RETRY handed the tutorial straight
        // back to a player who had just dismissed it.
        val viewModel = freshViewModel()
        viewModel.skipTutorial()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(-1, viewModel.tutorialStep)

        viewModel.startNewGame()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse("the tutorial is a first-run thing", viewModel.tutorialStep >= 0)
    }
}
