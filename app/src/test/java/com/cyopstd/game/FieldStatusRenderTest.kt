package com.cyopstd.game

import android.graphics.Bitmap
import android.graphics.Canvas
import com.cyopstd.game.core.WorldGeometry
import com.cyopstd.game.engine.GameEngine
import com.cyopstd.game.ui.game.BattlefieldRenderOptions
import com.cyopstd.game.ui.game.BattlefieldRenderer
import com.cyopstd.game.ui.game.BattlefieldSelection
import com.cyopstd.game.ui.game.WorldTransform
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Rasterizes the real battlefield renderer and inspects the pixels.
 *
 * The corner readouts are a purely visual change, and the failure mode for a
 * purely visual change is that it *looks* wrong rather than that it throws. So
 * this draws actual frames and locates the ink by diffing two frames that
 * differ only in the number being drawn. That pins down exactly where each
 * readout paints — and, more importantly, where it does not.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FieldStatusRenderTest {

    private val width = WorldGeometry.WIDTH.toInt()
    private val height = WorldGeometry.HEIGHT.toInt()

    /** The top edge of lane 1 — the first pixel the player needs to see. */
    private val laneTop = (WorldGeometry.A1 - WorldGeometry.LANE_HEIGHT / 2f).toInt()

    private fun frame(wave: Int, crypto: Int): Bitmap {
        val engine = GameEngine()
        engine.restore(
            wave = wave,
            serverHp = 100,
            crypto = crypto,
            placements = emptyList(),
            attacksBlocked = 0,
            cryptoEarned = 0,
            bossesDefeated = 0,
            serverDamageTaken = 0,
            agentsDeployed = 0,
            agentUpgrades = 0
        )

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        BattlefieldRenderer().draw(
            canvas = Canvas(bitmap),
            engine = engine,
            transform = WorldTransform(WorldGeometry.WIDTH, WorldGeometry.HEIGHT),
            options = BattlefieldRenderOptions(backgroundAnimation = false),
            selection = BattlefieldSelection(),
            time = 0f
        )
        return bitmap
    }

    private class Box(val minX: Int, val minY: Int, val maxX: Int, val maxY: Int, val pixels: Int)

    /** Where two frames differ. With one number changed, that is the readout. */
    private fun diff(a: Bitmap, b: Bitmap): Box {
        var minX = width; var maxX = -1
        var minY = height; var maxY = -1
        var pixels = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (a.getPixel(x, y) == b.getPixel(x, y)) continue
                pixels++
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
        return Box(minX, minY, maxX, maxY, pixels)
    }

    // Both waves sit in the same backdrop band, so the only thing that can
    // differ between the two frames is the wave readout itself.
    private val waveInk get() = diff(frame(1, 500), frame(4, 500))
    private val cryptoInk get() = diff(frame(1, 7), frame(1, 999_999))

    @Test
    fun `the wave number is drawn in the top-left corner`() {
        val ink = waveInk
        assertTrue("the wave number is not drawn at all", ink.pixels > 20)
        assertTrue("wave ink starts at x=${ink.minX}", ink.minX < width / 4)
        assertTrue("wave ink starts at y=${ink.minY}", ink.minY < 40)
    }

    @Test
    fun `the crypto number is drawn in the top-right corner`() {
        val ink = cryptoInk
        assertTrue("the crypto number is not drawn at all", ink.pixels > 20)
        assertTrue("crypto ink ends at x=${ink.maxX}", ink.maxX > width * 3 / 4)
        assertTrue("crypto ink starts at y=${ink.minY}", ink.minY < 40)
    }

    @Test
    fun `neither readout touches the lanes`() {
        // This is the whole point of putting them where they are. The top lane
        // is the one the player was complaining about not being able to see.
        for ((name, ink) in listOf("wave" to waveInk, "crypto" to cryptoInk)) {
            assertTrue(
                "$name ink reaches y=${ink.maxY}, and lane 1 starts at $laneTop",
                ink.maxY < laneTop
            )
        }
    }

    @Test
    fun `neither readout collides with the ATTACK ORIGIN label`() {
        // That label sits on its own line just below the status text.
        for ((name, ink) in listOf("wave" to waveInk, "crypto" to cryptoInk)) {
            assertTrue("$name ink descends to y=${ink.maxY}", ink.maxY < 38)
        }
    }

    @Test
    fun `the readouts change nothing else on the field`() {
        // A frame is 1,216,000 pixels. Changing the wave may repaint the wave
        // number and nothing else; the same for crypto. If either number ever
        // starts driving some other part of the render, this catches it.
        val wave = waveInk
        assertTrue("wave ink is ${wave.pixels} px wide-spread", wave.pixels < 2_000)
        assertTrue("wave ink spans to x=${wave.maxX}", wave.maxX < width / 4)

        val crypto = cryptoInk
        assertTrue("crypto ink is ${crypto.pixels} px wide-spread", crypto.pixels < 4_000)
        assertTrue("crypto ink spans from x=${crypto.minX}", crypto.minX > width * 3 / 4)
    }
}
