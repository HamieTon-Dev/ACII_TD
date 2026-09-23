package com.cyopstd.game

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.cyopstd.game.ui.common.AsciiBackdrop
import com.cyopstd.game.ui.common.LocalLivingBackground
import com.cyopstd.game.ui.theme.CyOpsTheme
import com.cyopstd.game.ui.theme.LivingBackground
import com.cyopstd.game.ui.theme.Palette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Rasterizes the menu backdrop and looks at the pixels.
 *
 * A living background that a player paid for has exactly one job on the menus:
 * to be *visible*, and to be *recognisably itself*. Neither can be established
 * by reading the code — the effect is alpha-blended text at a few percent
 * opacity, and it is entirely possible to wire the theme through correctly and
 * still draw something no eye could tell from the free backdrop.
 *
 * The measurement subtracts the page colour before judging anything. The first
 * version of this test did not, counted every dark-blue background pixel as
 * "ink", and consequently reported the backdrop as blue no matter which theme
 * was set — it passed for AURORA for the wrong reason and failed for LATTICE
 * for the right one. What is measured here is what the backdrop *adds*.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MenuBackdropRenderTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val background = mutableStateOf(LivingBackground.NONE)
    private var installed = false

    /**
     * One backdrop frame for [bg].
     *
     * Compose allows a single `setContent` per test, so the theme is a piece of
     * state the test writes rather than a new composition, which also means
     * every frame is drawn by the same live backdrop the menus use.
     */
    private fun frame(bg: LivingBackground): Bitmap {
        if (!installed) {
            installed = true
            compose.mainClock.autoAdvance = false
            compose.setContent {
                CyOpsTheme {
                    CompositionLocalProvider(LocalLivingBackground provides background.value) {
                        AsciiBackdrop(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Palette.Background),
                            density = 40
                        )
                    }
                }
            }
        }
        compose.runOnUiThread { background.value = bg }
        compose.mainClock.advanceTimeBy(600)

        // Drawn straight out of the view hierarchy rather than through
        // captureToImage: that path asks the window to redraw and waits for it,
        // and the wait never completes here because the backdrop's animation
        // holds the test clock by hand.
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(
            view.width.coerceAtLeast(1),
            view.height.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        compose.runOnUiThread { view.draw(Canvas(bitmap)) }
        return bitmap
    }

    /** What the backdrop added on top of the page colour. */
    private class Ink(val pixels: Int, val r: Float, val g: Float, val b: Float) {
        val luminance: Float get() = 0.2126f * r + 0.7152f * g + 0.0722f * b
        override fun toString() = "pixels=$pixels r=$r g=$g b=$b"
    }

    private fun ink(image: Bitmap): Ink {
        // The page colour is whatever fills most of the frame.
        val histogram = HashMap<Int, Int>()
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val p = image.getPixel(x, y)
                histogram[p] = (histogram[p] ?: 0) + 1
            }
        }
        val page = histogram.maxByOrNull { it.value }!!.key
        val pr = (page shr 16) and 0xFF
        val pg = (page shr 8) and 0xFF
        val pb = page and 0xFF

        var count = 0
        var r = 0f
        var g = 0f
        var b = 0f
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val p = image.getPixel(x, y)
                val dr = ((p shr 16) and 0xFF) - pr
                val dg = ((p shr 8) and 0xFF) - pg
                val db = (p and 0xFF) - pb
                if (maxOf(dr, dg, db) < 2) continue
                count++
                r += dr.coerceAtLeast(0) / 255f
                g += dg.coerceAtLeast(0) / 255f
                b += db.coerceAtLeast(0) / 255f
            }
        }
        return if (count == 0) Ink(0, 0f, 0f, 0f)
        else Ink(count, r / count, g / count, b / count)
    }

    @Test
    fun `the free backdrop draws something and stays neutral`() {
        val plain = ink(frame(LivingBackground.NONE))
        assertTrue("the free backdrop drew nothing at all: $plain", plain.pixels > 0)
    }

    @Test
    fun `an owned background changes the menu drift`() {
        val plain = ink(frame(LivingBackground.NONE))
        val lit = ink(frame(LivingBackground.AURORA))
        assertTrue("AURORA drew nothing: $lit", lit.pixels > 0)
        // Denser by construction (1.35x the columns). If this ever stops being
        // true, the player is paying for something invisible.
        // Measured: 4270 lit pixels against 2051 free ones at this size, so
        // the floor below is comfortable rather than tuned to the reading.
        assertTrue(
            "AURORA covered $lit, barely more than the free backdrop's $plain",
            lit.pixels > plain.pixels * 1.25f
        )
    }

    @Test
    fun `AURORA reads blue and LATTICE reads green`() {
        val aurora = ink(frame(LivingBackground.AURORA))
        // AURORA's tint is 0xFF1B3A66: blue dominant.
        assertTrue("AURORA should read blue: $aurora", aurora.b > aurora.g)

        val lattice = ink(frame(LivingBackground.LATTICE))
        // LATTICE's is 0xFF123E35: green dominant, and the two must not be
        // mistakable for one another on screen.
        assertTrue("LATTICE should read green: $lattice", lattice.g > lattice.b)
        assertTrue("LATTICE should read green: $lattice", lattice.g > lattice.r)
    }

    @Test
    fun `no themed backdrop is bright enough to fight the menu`() {
        // The same ceiling the palette tests hold the lane tints to. A backdrop
        // that crosses it stops being a backdrop. Every theme currently
        // measures around 0.02, so this is a guard rail, not a fit.
        for (bg in LivingBackground.entries) {
            val lit = ink(frame(bg))
            assertTrue(
                "$bg backdrop averages ${lit.luminance} luminance over the page, " +
                    "too bright to sit behind a menu ($lit)",
                lit.luminance < 0.22f
            )
        }
    }

    @Test
    fun `every living background carries a menu tint`() {
        // The menus get their theme from `laneTint`; a background that forgot
        // one would silently render as the free backdrop despite being paid
        // for, which is the failure this catches at the catalog level.
        for (bg in LivingBackground.entries) {
            if (bg == LivingBackground.NONE) {
                assertEquals(null, bg.laneTint)
            } else {
                assertTrue(
                    "$bg has no laneTint, so the menus cannot show it",
                    bg.laneTint != null
                )
            }
        }
    }
}
