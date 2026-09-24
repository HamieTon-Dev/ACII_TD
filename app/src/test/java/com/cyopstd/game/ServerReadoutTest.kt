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
 * What the server rack says about integrity.
 *
 * The figures used to be printed here as well as in the HUD strip. Removing
 * them is easy; proving the *bar* survived is the part worth a test, because
 * the two sit a few pixels apart and deleting one by mistake would look like a
 * successful change.
 *
 * Measured by diffing frames that differ only in integrity: whatever pixels
 * move between 100/100 and 40/100 are the integrity display by construction.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ServerReadoutTest {

    private val width = WorldGeometry.WIDTH.toInt()
    private val height = WorldGeometry.HEIGHT.toInt()

    private fun frame(serverHp: Int): Bitmap {
        val engine = GameEngine()
        engine.startNewRun()
        engine.restore(
            wave = 3, serverHp = serverHp, crypto = 120, placements = emptyList(),
            attacksBlocked = 0, cryptoEarned = 0, bossesDefeated = 0,
            serverDamageTaken = 0, agentsDeployed = 0, agentUpgrades = 0
        )
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        BattlefieldRenderer().draw(
            canvas = Canvas(bitmap),
            engine = engine,
            transform = WorldTransform(WorldGeometry.WIDTH, WorldGeometry.HEIGHT),
            options = BattlefieldRenderOptions(backgroundAnimation = false),
            selection = BattlefieldSelection(),
            time = 1f
        )
        return bitmap
    }

    /** Every pixel that moves when integrity changes. */
    private fun integrityPixels(): List<Pair<Int, Int>> {
        val full = frame(100)
        val hurt = frame(40)
        val changed = ArrayList<Pair<Int, Int>>()
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (full.getPixel(x, y) != hurt.getPixel(x, y)) changed += x to y
            }
        }
        return changed
    }

    @Test
    fun `the rack still shows integrity somewhere`() {
        assertTrue(
            "nothing on the rack responds to integrity at all -- the bar went " +
                "out with the numbers",
            integrityPixels().isNotEmpty()
        )
    }

    @Test
    fun `it is a bar, not a line of digits`() {
        // A bar is one solid horizontal block: a handful of rows, each of them
        // wide. Digits are the opposite -- many rows, each narrow and broken
        // into separate runs. Measuring the shape distinguishes them without
        // needing to read anything.
        val pixels = integrityPixels()
        val rows = pixels.groupBy { it.second }
        val widest = rows.values.maxOf { row ->
            val xs = row.map { it.first }
            xs.max() - xs.min()
        }
        assertTrue(
            "the widest changing row is only $widest wide, which is a number, " +
                "not a bar",
            widest > 120
        )
    }

    @Test
    fun `the figures are gone`() {
        // The numbers sat below the bar, spanning a tall block of rows. A bar
        // occupies few rows; digits at 27pt occupy around thirty. If the
        // changing pixels span much more than a bar's height, the text is back.
        val pixels = integrityPixels()
        val rows = pixels.map { it.second }.distinct().sorted()
        val span = rows.last() - rows.first()
        assertTrue(
            "integrity is drawn across $span rows of the rack; a bar alone is " +
                "about 26, so the figures are still being printed",
            span < 40
        )
    }
}
