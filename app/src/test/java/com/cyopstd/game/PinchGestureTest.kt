package com.cyopstd.game

import android.app.Application
import android.os.Looper
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.cyopstd.game.model.AgentType
import com.cyopstd.game.state.GameViewModel
import com.cyopstd.game.ui.game.BATTLEFIELD_TAG
import com.cyopstd.game.ui.game.GameScreen
import com.cyopstd.game.ui.game.WorldTransform
import com.cyopstd.game.ui.theme.CyOpsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * A pinch is never a placement.
 *
 * The single requirement in the zoom brief that cannot be satisfied by careful
 * arithmetic, and the one most likely to be wrong: two fingers land on the
 * board, and the game must not treat either of them as a tap. Every variant
 * matters — the second finger arriving after the first, a pinch that begins
 * directly over a node, one that ends over a node, a pinch with an agent
 * already selected.
 *
 * Driven through the real `GameScreen` with real multi-touch events rather
 * than by calling the gesture code directly, because the thing being tested is
 * precisely how Compose's pointer stream is interpreted. Reading the handler
 * and reasoning about it is how the original bug gets written in the first
 * place.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w640dp-h360dp-land-mdpi")
class PinchGestureTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var viewModel: GameViewModel

    private fun startMatch(withPendingAgent: AgentType? = AgentType.FIREWALL) {
        val application = ApplicationProvider.getApplicationContext<Application>()
        viewModel = GameViewModel(application, TestStores.isolatedRepository())
        shadowOf(Looper.getMainLooper()).idle()
        viewModel.startNewGame()
        shadowOf(Looper.getMainLooper()).idle()
        // The tutorial gates placement on its own steps; this is about the
        // gesture, so step past it.
        viewModel.skipTutorial()
        withPendingAgent?.let(viewModel::choosePendingAgent)
        shadowOf(Looper.getMainLooper()).idle()

        compose.mainClock.autoAdvance = false
        compose.setContent {
            CyOpsTheme {
                GameScreen(viewModel = viewModel, onExitToMenu = {}, onOpenSettings = {})
            }
        }
        compose.mainClock.advanceTimeBy(64)
    }

    private fun agentCount() = viewModel.engine.snapshotPlacements().size

    /**
     * Where a deployment node sits on screen **right now**.
     *
     * Re-measured on every call, and it has to be: the battlefield changes
     * size during play. Opening the agent roster takes height from it and
     * closing it gives that height back, which moves the whole letterboxed
     * board. An earlier version of this test measured once and tapped twice,
     * and the second tap landed 95 world units from where it was aimed —
     * which looked exactly like a broken gesture handler and was not.
     */
    private fun screenPositionOfFirstNode(): Offset {
        val node = viewModel.engine.map.nodes.first()
        val field = compose.onNodeWithTag(BATTLEFIELD_TAG).fetchSemanticsNode()
        val transform = WorldTransform(
            field.size.width.toFloat(),
            field.size.height.toFloat(),
            viewModel.viewport.zoom,
            viewModel.viewport.panX,
            viewModel.viewport.panY
        )
        return Offset(transform.toScreenX(node.x), transform.toScreenY(node.y))
    }

    // ------------------------------------------------------ the baseline

    @Test
    fun `a single tap on a node still deploys`() {
        // The control. If this ever fails, the gesture rewrite broke the most
        // common action in the game and every other assertion here is moot.
        startMatch()
        val before = agentCount()
        val node = screenPositionOfFirstNode()

        compose.onNodeWithTag(BATTLEFIELD_TAG).performTouchInput {
            down(0, node)
            up(0)
        }
        compose.mainClock.advanceTimeBy(64)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("a plain tap no longer places an agent", before + 1, agentCount())
    }

    // ---------------------------------------------------- the actual rule

    @Test
    fun `a pinch over a node places nothing`() {
        startMatch()
        val before = agentCount()
        val node = screenPositionOfFirstNode()

        compose.onNodeWithTag(BATTLEFIELD_TAG).performTouchInput {
            // First finger lands squarely on a node -- the worst case.
            down(0, node)
            // Second finger arrives; from here nothing may be a tap.
            down(1, node + Offset(40f, 0f))
            moveTo(0, node + Offset(-40f, 0f))
            moveTo(1, node + Offset(80f, 0f))
            up(0)
            up(1)
        }
        compose.mainClock.advanceTimeBy(64)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("a pinch deployed an agent", before, agentCount())
        assertTrue("the pinch did not zoom", viewModel.viewport.isZoomed)
    }

    @Test
    fun `a pinch that ends over a node places nothing`() {
        // The second finger lifting last, directly on a node, is the other
        // way a stray placement gets through: the gesture is over, one pointer
        // is still down, and a naive handler calls that a tap.
        startMatch()
        val before = agentCount()
        val node = screenPositionOfFirstNode()

        compose.onNodeWithTag(BATTLEFIELD_TAG).performTouchInput {
            down(0, node + Offset(-60f, -30f))
            down(1, node + Offset(60f, 30f))
            moveTo(0, node + Offset(-20f, -10f))
            moveTo(1, node)
            up(0)
            up(1)
        }
        compose.mainClock.advanceTimeBy(64)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("lifting the last finger on a node deployed an agent", before, agentCount())
    }

    @Test
    fun `a pinch never places twice`() {
        // Two fingers, two chances for a handler to fire once each.
        startMatch()
        val before = agentCount()
        val node = screenPositionOfFirstNode()

        repeat(3) {
            compose.onNodeWithTag(BATTLEFIELD_TAG).performTouchInput {
                down(0, node)
                down(1, node + Offset(50f, 20f))
                moveTo(1, node + Offset(90f, 40f))
                up(1)
                up(0)
            }
            compose.mainClock.advanceTimeBy(64)
        }
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("pinching produced phantom placements", before, agentCount())
    }

    @Test
    fun `a pinch does not clear an existing selection`() {
        // "No accidental deselection caused by the second finger." Place an
        // agent, select it, then pinch: the selection must survive.
        startMatch()
        compose.onNodeWithTag(BATTLEFIELD_TAG).performTouchInput {
            val at = screenPositionOfFirstNode()
            down(0, at)
            up(0)
        }
        compose.mainClock.advanceTimeBy(64)
        shadowOf(Looper.getMainLooper()).idle()

        // Tap it again with nothing pending: that selects the deployed agent.
        // Measured afresh — the board moved when the roster closed.
        compose.onNodeWithTag(BATTLEFIELD_TAG).performTouchInput {
            down(0, screenPositionOfFirstNode())
            up(0)
        }
        compose.mainClock.advanceTimeBy(64)
        shadowOf(Looper.getMainLooper()).idle()
        val selected = viewModel.selection.selectedNodeId
        assertTrue(
            "nothing was selected, so this proves nothing " +
                "(placements=${viewModel.engine.snapshotPlacements().size})",
            selected != null
        )

        val node = screenPositionOfFirstNode()
        compose.onNodeWithTag(BATTLEFIELD_TAG).performTouchInput {
            down(0, node + Offset(-30f, 0f))
            down(1, node + Offset(30f, 0f))
            moveTo(0, node + Offset(-90f, 0f))
            moveTo(1, node + Offset(90f, 0f))
            up(0)
            up(1)
        }
        compose.mainClock.advanceTimeBy(64)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            "the second finger cleared the selection",
            selected,
            viewModel.selection.selectedNodeId
        )
    }

    // ----------------------------------------------------------- panning

    @Test
    fun `a drag while zoomed pans instead of placing`() {
        startMatch()
        val node = screenPositionOfFirstNode()

        // Zoom in first.
        compose.onNodeWithTag(BATTLEFIELD_TAG).performTouchInput {
            down(0, node + Offset(-30f, 0f))
            down(1, node + Offset(30f, 0f))
            moveTo(0, node + Offset(-120f, 0f))
            moveTo(1, node + Offset(120f, 0f))
            up(0)
            up(1)
        }
        compose.mainClock.advanceTimeBy(64)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(viewModel.viewport.isZoomed)

        val before = agentCount()
        val panBefore = viewModel.viewport.panX

        compose.onNodeWithTag(BATTLEFIELD_TAG).performTouchInput {
            down(0, node)
            moveTo(0, node + Offset(-120f, 0f))
            up(0)
        }
        compose.mainClock.advanceTimeBy(64)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("a pan deployed an agent", before, agentCount())
        assertTrue(
            "the board did not pan",
            kotlin.math.abs(viewModel.viewport.panX - panBefore) > 1f
        )
    }

    @Test
    fun `a tap still works after zooming, at the zoomed position`() {
        // The end-to-end claim: zoom in, and a tap on where a node now appears
        // hits that node. This is the whole feature -- if the node moved on
        // screen and the hit test did not follow, nothing above matters.
        startMatch()
        val node = screenPositionOfFirstNode()

        compose.onNodeWithTag(BATTLEFIELD_TAG).performTouchInput {
            down(0, node + Offset(-30f, 0f))
            down(1, node + Offset(30f, 0f))
            moveTo(0, node + Offset(-110f, 0f))
            moveTo(1, node + Offset(110f, 0f))
            up(0)
            up(1)
        }
        compose.mainClock.advanceTimeBy(64)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("the board never zoomed", viewModel.viewport.isZoomed)

        val before = agentCount()
        // Recomputed at the new zoom, which is the point.
        val moved = screenPositionOfFirstNode()
        compose.onNodeWithTag(BATTLEFIELD_TAG).performTouchInput {
            down(0, moved)
            up(0)
        }
        compose.mainClock.advanceTimeBy(64)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            "a tap on the node's zoomed position did not hit the node",
            before + 1,
            agentCount()
        )
    }
}
