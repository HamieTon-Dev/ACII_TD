package com.cyopstd.game

import android.app.Application
import android.os.Looper
import androidx.compose.ui.geometry.Offset
import androidx.test.core.app.ApplicationProvider
import com.cyopstd.game.core.WorldGeometry
import com.cyopstd.game.engine.RunPhase
import com.cyopstd.game.model.AgentType
import com.cyopstd.game.state.GameViewModel
import com.cyopstd.game.ui.game.WorldTransform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Zoom across the run's lifecycle.
 *
 * The brief listed the states a zoomed board has to survive: pausing, being
 * backgrounded, losing, restarting, and the simulation running underneath it.
 * None of these are gesture questions — they are questions about *where the
 * zoom lives*, and the answer this implementation gives is "on the view model,
 * outside the engine and outside the save".
 *
 * That choice is what these tests check. A zoom stored in the composable would
 * reset on every recomposition; one stored in the engine would reach the save
 * and be replicated to another device; one stored in the save would make a
 * resumed run open at whatever magnification it was abandoned at, which is
 * defensible but was not asked for and is not free.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ZoomLifecycleTest {

    private fun freshViewModel(): GameViewModel {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = GameViewModel(application, TestStores.isolatedRepository())
        shadowOf(Looper.getMainLooper()).idle()
        return viewModel
    }

    private fun GameViewModel.zoomIn(times: Int = 4) {
        val transform = WorldTransform(640f, 360f, viewport.zoom, viewport.panX, viewport.panY)
        repeat(times) { viewport.pinch(transform, Offset(320f, 180f), 1.3f) }
    }

    @Test
    fun `a match starts looking at the whole board`() {
        val viewModel = freshViewModel()
        viewModel.startNewGame()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(viewModel.viewport.isZoomed)
    }

    @Test
    fun `zoom survives pausing and resuming`() {
        val viewModel = freshViewModel()
        viewModel.startNewGame()
        shadowOf(Looper.getMainLooper()).idle()
        viewModel.zoomIn()
        val zoom = viewModel.viewport.zoom
        assertTrue(viewModel.viewport.isZoomed)

        viewModel.togglePause()
        viewModel.togglePause()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("pausing lost the zoom", zoom, viewModel.viewport.zoom, 0.0001f)
    }

    @Test
    fun `zoom survives the app being backgrounded and brought back`() {
        val viewModel = freshViewModel()
        viewModel.startNewGame()
        shadowOf(Looper.getMainLooper()).idle()
        viewModel.zoomIn()
        val zoom = viewModel.viewport.zoom

        viewModel.onAppPaused()
        shadowOf(Looper.getMainLooper()).idle()
        viewModel.onAppResumed()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(
            "coming back to a match should not snap the board back to fitted",
            zoom,
            viewModel.viewport.zoom,
            0.0001f
        )
    }

    @Test
    fun `the simulation is unaffected by zoom`() {
        // Zoom is a property of looking at the match, not of the match. If the
        // engine can see it at all, two players at different zooms are playing
        // different games.
        val a = freshViewModel()
        val b = freshViewModel()
        a.startNewGame()
        b.startNewGame()
        shadowOf(Looper.getMainLooper()).idle()
        b.zoomIn()

        repeat(600) {
            if (a.engine.canStartNextWave()) a.engine.startNextWave()
            if (b.engine.canStartNextWave()) b.engine.startNextWave()
            a.onFrame(1f / 60f)
            b.onFrame(1f / 60f)
        }

        assertEquals("zoom changed the wave count", a.engine.currentWave, b.engine.currentWave)
        assertEquals("zoom changed server integrity", a.engine.serverHp, b.engine.serverHp)
        assertEquals("zoom changed the economy", a.engine.crypto, b.engine.crypto)
    }

    @Test
    fun `restarting a run resets the view`() {
        val viewModel = freshViewModel()
        viewModel.startNewGame()
        shadowOf(Looper.getMainLooper()).idle()
        viewModel.zoomIn()
        assertTrue(viewModel.viewport.isZoomed)

        viewModel.startNewGame()
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse(
            "a new run opened zoomed into a corner of a map the player has not seen",
            viewModel.viewport.isZoomed
        )
        assertEquals(0f, viewModel.viewport.panX, 0.0001f)
        assertEquals(0f, viewModel.viewport.panY, 0.0001f)
    }

    @Test
    fun `losing while zoomed does not break the run ending`() {
        val viewModel = freshViewModel()
        viewModel.startNewGame()
        shadowOf(Looper.getMainLooper()).idle()
        viewModel.zoomIn()

        var frames = 0
        while (viewModel.engine.phase != RunPhase.GAME_OVER && frames < 200_000) {
            if (viewModel.engine.canStartNextWave()) viewModel.engine.startNextWave()
            viewModel.onFrame(1f / 30f)
            frames++
        }
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(RunPhase.GAME_OVER, viewModel.engine.phase)
        assertTrue("no summary after losing while zoomed", viewModel.gameOverSummary != null)
    }

    @Test
    fun `zoom is not written to the save`() {
        // It must not reach the saved run, because a run resumed on another
        // device has no business inheriting how far in someone was looking.
        val viewModel = freshViewModel()
        viewModel.startNewGame()
        shadowOf(Looper.getMainLooper()).idle()
        viewModel.zoomIn()

        val fields = com.cyopstd.game.save.SavedRun::class.java.declaredFields.map { it.name }
        for (name in fields) {
            // Prefix match, not substring: "Companion" contains "pan", and
            // the first version of this check failed on Kotlin's own
            // synthetic field rather than on anything in the save.
            assertFalse(
                "SavedRun carries a field that looks like view state: $name",
                name.startsWith("zoom", ignoreCase = true) ||
                    name.startsWith("pan", ignoreCase = true)
            )
        }
    }

    @Test
    fun `selection still resolves to the same node at every zoom`() {
        // "Units remain attached to their actual nodes when zoomed." The tap
        // is resolved in world space, so the answer cannot depend on zoom --
        // this is the assertion that says so out loud.
        val viewModel = freshViewModel()
        viewModel.startNewGame()
        shadowOf(Looper.getMainLooper()).idle()
        viewModel.skipTutorial()

        val node = viewModel.engine.map.nodes.first {
            it.x > 200f && it.y > 100f && it.y < WorldGeometry.HEIGHT - 100f
        }
        viewModel.choosePendingAgent(AgentType.FIREWALL)
        viewModel.onBattlefieldTap(Offset(node.x, node.y))
        assertTrue(
            "the agent was not placed, so this proves nothing",
            viewModel.engine.agentAt(node.id) != null
        )

        for (zoom in listOf(1f, 1.6f, 2.2f, WorldTransform.MAX_ZOOM)) {
            val t = WorldTransform(640f, 360f, zoom)
            // Where that node appears on screen at this zoom...
            val onScreen = Offset(t.toScreenX(node.x), t.toScreenY(node.y))
            // ...tapped, resolves back to that same node.
            viewModel.closeSelection()
            viewModel.onBattlefieldTap(t.toWorld(onScreen))
            assertEquals(
                "at ${zoom}x a tap on the node selected something else",
                node.id,
                viewModel.selection.selectedNodeId
            )
        }
    }
}
