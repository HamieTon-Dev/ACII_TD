package com.cyopstd.game

import android.graphics.Bitmap
import android.graphics.Canvas
import com.cyopstd.game.core.WorldGeometry
import com.cyopstd.game.engine.GameEngine
import com.cyopstd.game.ui.game.BattlefieldRenderOptions
import com.cyopstd.game.ui.game.BattlefieldRenderer
import com.cyopstd.game.ui.game.BattlefieldSelection
import com.cyopstd.game.ui.game.WorldRect
import com.cyopstd.game.ui.game.WorldTransform
import com.cyopstd.game.ui.game.toScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The boxes the tutorial's arrows point at.
 *
 * A coordinate typed into the tutorial by hand is a coordinate that goes stale
 * the first time the readout moves — which it did in 1.18.0, when the two lines
 * were restacked. So the renderer draws from these rects and the tutorial aims
 * at them, and this proves the rects actually contain the pixels.
 *
 * Proved by diffing frames rather than by looking for coloured pixels: change
 * only the crypto number and every pixel that moves belongs to the crypto
 * readout by construction. That is the technique that found the shard-burst
 * measurement measuring the ATTACK ORIGIN label instead.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FieldStatusAnchorTest {

    private val width = WorldGeometry.WIDTH.toInt()
    private val height = WorldGeometry.HEIGHT.toInt()
    private val renderer = BattlefieldRenderer()

    private fun frame(wave: Int, crypto: Int): Bitmap {
        val engine = GameEngine()
        engine.restore(
            wave = wave, serverHp = 100, crypto = crypto, placements = emptyList(),
            attacksBlocked = 0, cryptoEarned = 0, bossesDefeated = 0,
            serverDamageTaken = 0, agentsDeployed = 0, agentUpgrades = 0
        )

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        renderer.draw(
            canvas = Canvas(bitmap),
            engine = engine,
            transform = WorldTransform(WorldGeometry.WIDTH, WorldGeometry.HEIGHT),
            options = BattlefieldRenderOptions(backgroundAnimation = false),
            selection = BattlefieldSelection(),
            time = 1f
        )
        return bitmap
    }

    /** Every pixel that differs between two frames. */
    private fun changedPixels(a: Bitmap, b: Bitmap): List<Pair<Int, Int>> {
        val changed = ArrayList<Pair<Int, Int>>()
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (a.getPixel(x, y) != b.getPixel(x, y)) changed += x to y
            }
        }
        return changed
    }

    private fun assertAllInside(changed: List<Pair<Int, Int>>, box: WorldRect, what: String) {
        assertTrue("$what did not change any pixels at all", changed.isNotEmpty())
        val stray = changed.filterNot { (x, y) -> box.contains(x.toFloat(), y.toFloat()) }
        assertEquals(
            "$what drew ${stray.size} pixels outside the box the tutorial points " +
                "at, e.g. ${stray.take(4)}; box is $box",
            0,
            stray.size
        )
    }

    @Test
    fun `the wave box contains the wave readout`() {
        val changed = changedPixels(frame(wave = 7, crypto = 120), frame(wave = 8, crypto = 120))
        assertAllInside(changed, renderer.fieldStatusAnchors.wave, "the wave readout")
    }

    @Test
    fun `the crypto box contains the crypto readout`() {
        val changed = changedPixels(frame(wave = 7, crypto = 120), frame(wave = 7, crypto = 999))
        assertAllInside(changed, renderer.fieldStatusAnchors.crypto, "the crypto readout")
    }

    @Test
    fun `the two boxes do not overlap`() {
        // An arrow that points at both is an arrow that points at neither.
        val wave = renderer.fieldStatusAnchors.wave
        val crypto = renderer.fieldStatusAnchors.crypto
        assertTrue(
            "wave ($wave) and crypto ($crypto) overlap vertically",
            wave.bottom <= crypto.top
        )
    }

    @Test
    fun `both boxes are inside the plate that backs them`() {
        val plate = renderer.fieldStatusAnchors.plate
        for ((name, box) in listOf(
            "wave" to renderer.fieldStatusAnchors.wave,
            "crypto" to renderer.fieldStatusAnchors.crypto
        )) {
            assertTrue(
                "$name ($box) is not inside the plate ($plate)",
                box.left >= plate.left && box.right <= plate.right &&
                    box.top >= plate.top && box.bottom <= plate.bottom
            )
        }
    }

    @Test
    fun `the box survives the letterbox onto a real screen`() {
        // The world is 1600x760; a phone is not. The arrow is drawn in screen
        // pixels, so the conversion is part of what has to be right.
        val transform = WorldTransform(2400f, 1000f)
        val screen = renderer.fieldStatusAnchors.crypto.toScreen(transform)

        val worldBox = renderer.fieldStatusAnchors.crypto
        assertEquals(transform.toScreenX(worldBox.left), screen.left, 0.01f)
        assertEquals(transform.toScreenY(worldBox.top), screen.top, 0.01f)
        assertTrue("the box vanished in the conversion", screen.width > 0f)
        assertTrue(
            "the box landed off the right of a 2400px screen at ${screen.right}",
            screen.right <= 2400f
        )
    }

    @Test
    fun `a fresh run never reads WAVE 0`() {
        // The first thing a new player sees. The HUD shows "--" and the
        // preparation banner says PERIMETER READY; the readout was the only
        // thing claiming a wave zero existed, and the tutorial has to be able
        // to point at the words "WAVE 1".
        val fresh = frame(wave = 0, crypto = 120)
        val one = frame(wave = 1, crypto = 120)
        assertEquals(
            "wave 0 and wave 1 should render identically, both reading WAVE 1",
            0,
            changedPixels(fresh, one).size
        )
    }
}
