package com.cyopstd.game

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import com.cyopstd.game.state.GameViewModel
import com.cyopstd.game.ui.game.GameScreen
import com.cyopstd.game.ui.theme.CyOpsTheme
import com.cyopstd.game.ui.theme.Palette
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The match screen as a whole: Compose strips above and below a native-Canvas
 * battlefield between them.
 *
 * This exists because of a defect that no other kind of test could see. The
 * renderer opens each frame with `canvas.drawColor`, which fills the whole
 * **clip** rather than the composable's box — and Compose does not clip a draw
 * to its layout bounds unless asked. So the battlefield painted straight over
 * the top status strip every frame. The HUD composed, laid out, and reported
 * perfectly correct bounds the entire time; only the pixels were missing, which
 * is why "is the top HUD strip actually visible on a device?" sat unanswered in
 * `DEVELOPMENT_STATUS.md` from 1.5.2 until 1.18.0.
 *
 * The lesson, and the reason this file is worth its runtime: a semantics
 * assertion proves a composable *exists*. Only pixels prove it is *seen*.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1600dp-h760dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MatchScreenRenderTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun frame(): Bitmap {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = GameViewModel(application, TestStores.isolatedRepository())
        shadowOf(Looper.getMainLooper()).idle()
        viewModel.startNewGame()
        viewModel.skipTutorial()
        shadowOf(Looper.getMainLooper()).idle()

        // The match screen drives the simulation from the frame clock and never
        // goes idle, so the clock is advanced by hand.
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CyOpsTheme {
                Box(Modifier.fillMaxSize().background(Palette.Background)) {
                    GameScreen(viewModel = viewModel, onExitToMenu = {}, onOpenSettings = {})
                }
            }
        }
        compose.mainClock.advanceTimeBy(600)

        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { view.draw(Canvas(bitmap)) }
        return bitmap
    }

    /** Where Compose says a piece of text ended up, in pixels. */
    private fun boundsOf(text: String): android.graphics.Rect? {
        val nodes = compose.onAllNodesWithText(text).fetchSemanticsNodes()
        val node = nodes.firstOrNull() ?: return null
        val r = node.boundsInRoot
        return android.graphics.Rect(
            r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt()
        )
    }

    /** How many pixels in [rect] are not the background the battlefield fills. */
    private fun inkIn(bitmap: Bitmap, rect: android.graphics.Rect): Int {
        var ink = 0
        for (y in rect.top until rect.bottom.coerceAtMost(bitmap.height)) {
            for (x in rect.left until rect.right.coerceAtMost(bitmap.width)) {
                val p = bitmap.getPixel(x, y)
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                // Anything appreciably brighter than the near-black field.
                if (r + g + b > 120) ink++
            }
        }
        return ink
    }

    @Test
    fun `the top status strip is actually drawn, not just laid out`() {
        val bitmap = frame()
        val bounds = boundsOf("CORE-SERVER")
        assertTrue("the HUD did not compose at all", bounds != null)

        val ink = inkIn(bitmap, bounds!!)
        assertTrue(
            "the HUD laid out at $bounds but only $ink pixels of it were drawn — " +
                "something is painting over it",
            ink > 30
        )
    }

    @Test
    fun `the control bar is drawn too`() {
        // The same failure could not have been caught at the bottom of the
        // screen, because the control bar is drawn *after* the battlefield.
        // Asserted anyway: it is the other half of the sandwich, and a future
        // change to the draw order would break this one first.
        val bitmap = frame()
        val bounds = boundsOf("AGENTS")
        assertTrue("the control bar did not compose", bounds != null)
        assertTrue("the control bar was not drawn", inkIn(bitmap, bounds!!) > 30)
    }

    @Test
    fun `the control bar is about half the height it was`() {
        frame()
        val bounds = boundsOf("AGENTS")!!
        // 46dp plus 8dp of row padding each side used to put this at ~62dp.
        // The owner asked for half, because every pixel of the bar is a pixel
        // the board does not get.
        assertTrue(
            "the control bar button is ${bounds.height()}px tall",
            bounds.height() <= 34
        )
    }
}
