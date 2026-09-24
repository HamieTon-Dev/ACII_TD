package com.cyopstd.game

import android.graphics.Bitmap
import android.graphics.Canvas
import com.cyopstd.game.core.WorldGeometry
import com.cyopstd.game.engine.GameEngine
import com.cyopstd.game.model.EnemyType
import com.cyopstd.game.ui.game.BattlefieldRenderOptions
import com.cyopstd.game.ui.game.BattlefieldRenderer
import com.cyopstd.game.ui.game.BattlefieldSelection
import com.cyopstd.game.ui.game.WorldTransform
import org.junit.Assert.assertFalse
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

    /** A frame of a fresh run, after [configure] has set the scene. */
    private fun scene(configure: (GameEngine) -> Unit): Bitmap {
        val engine = GameEngine()
        engine.startNewRun()
        configure(engine)
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
    fun `both readouts are stacked in the top-right corner`() {
        val wave = waveInk
        val crypto = cryptoInk
        assertTrue("the wave number is not drawn at all", wave.pixels > 20)
        assertTrue("the crypto number is not drawn at all", crypto.pixels > 20)

        for ((name, ink) in listOf("wave" to wave, "crypto" to crypto)) {
            assertTrue("$name ink ends at x=${ink.maxX}", ink.maxX > width * 3 / 4)
        }

        // The wave sits above the money: the two numbers a player checks most,
        // in one glance rather than at opposite ends of the board.
        assertTrue(
            "wave ink (y=${wave.minY}..${wave.maxY}) is not above crypto ink " +
                "(y=${crypto.minY}..${crypto.maxY})",
            wave.maxY < crypto.minY
        )
    }

    @Test
    fun `the stack fits in the empty block above the rack`() {
        // This corner was chosen because nothing else uses it: no deployment
        // node is placed past x=1240 and the core rack starts at y=168. If
        // either readout grows out of that block it lands on something.
        val nodeRight = WorldGeometry.nodes.maxOf { it.x } + WorldGeometry.NODE_RADIUS
        for ((name, ink) in listOf("wave" to waveInk, "crypto" to cryptoInk)) {
            assertTrue(
                "$name ink starts at x=${ink.minX}, and nodes reach $nodeRight",
                ink.minX > nodeRight
            )
            assertTrue(
                "$name ink reaches y=${ink.maxY}, and the rack starts at " +
                    "${WorldGeometry.SERVER_TOP}",
                ink.maxY < WorldGeometry.SERVER_TOP
            )
        }
    }

    @Test
    fun `neither readout touches the lanes`() {
        // The whole point of putting them where they are: a readout drawn over
        // a corridor hides the threats in it.
        //
        // Checked against the real route segments rather than against a single
        // y ceiling. The ceiling was a fair proxy while the readouts spanned
        // the full width of the board, but the stack now lives in the corner
        // past x=1240 where no corridor reaches, and a y-only test fails there
        // for a collision that cannot happen.
        val half = WorldGeometry.LANE_HEIGHT / 2f
        for ((name, ink) in listOf("wave" to waveInk, "crypto" to cryptoInk)) {
            for (lane in 0 until WorldGeometry.LANE_COUNT) {
                val points = WorldGeometry.laneWaypoints[lane]
                for (i in 0 until points.size - 1) {
                    val a = points[i]
                    val b = points[i + 1]
                    val left = minOf(a.x, b.x) - half
                    val right = maxOf(a.x, b.x) + half
                    val top = minOf(a.y, b.y) - half
                    val bottom = maxOf(a.y, b.y) + half
                    val overlaps = ink.maxX >= left && ink.minX <= right &&
                        ink.maxY >= top && ink.minY <= bottom
                    assertFalse(
                        "$name ink (x ${ink.minX}..${ink.maxX}, y ${ink.minY}..${ink.maxY}) " +
                            "lands on lane $lane segment $i " +
                            "(x $left..$right, y $top..$bottom)",
                        overlaps
                    )
                }
            }
        }
    }

    @Test
    fun `the readouts leave the ATTACK ORIGIN corner alone`() {
        // Both readouts moved to the right in 1.18.0. The top-left is crowded
        // -- ATTACK ORIGIN sits under it and lane 1 starts at y=73, which
        // leaves no room for a second line -- so the test that used to police
        // that overlap now polices the corner staying empty.
        for ((name, ink) in listOf("wave" to waveInk, "crypto" to cryptoInk)) {
            assertTrue(
                "$name ink starts at x=${ink.minX}, inside the ATTACK ORIGIN corner",
                ink.minX > width / 2
            )
        }
    }

    @Test
    fun `an overlapping threat chip occludes rather than smears`() {
        // The defect this exists for: a threat tag is up to sixty units wide,
        // and a fast archetype constantly catches a slow one. Drawn as bare
        // text, two overlapping tags composited into unreadable mush -- four
        // bots reading as "[BBBIB]". An opaque chip means the nearer one
        // simply covers the one behind it.
        //
        // The leading threat is spawned first in both scenes, so it takes the
        // same lane slot and lands on exactly the same pixels in each.
        var frontX = 0f
        var frontY = 0f
        val alone = scene { engine ->
            engine.enemySystem().spawnEscort(EnemyType.BOT, 0, 600f, 5)
            val front = engine.enemies.items.first { it.active }
            frontX = front.x
            frontY = front.y
        }
        val crowded = scene { engine ->
            engine.enemySystem().spawnEscort(EnemyType.BOT, 0, 600f, 5)
            engine.enemySystem().spawnEscort(EnemyType.SQL_INJECTION, 0, 578f, 5)
        }

        // A box well inside the leading chip, clear of its own border.
        val x0 = (frontX - 14f).toInt()
        val x1 = (frontX + 14f).toInt()
        val y0 = (frontY - 9f).toInt()
        val y1 = (frontY + 9f).toInt()

        var differing = 0
        var inspected = 0
        for (y in y0..y1) {
            for (x in x0..x1) {
                inspected++
                if (alone.getPixel(x, y) != crowded.getPixel(x, y)) differing++
            }
        }

        assertTrue("the chip interior was not sampled", inspected > 300)
        assertTrue(
            "$differing of $inspected pixels inside the leading chip bled " +
                "through from the threat behind it",
            differing == 0
        )
    }

    @Test
    fun `a threat entering the field is never drawn clipped`() {
        // Threats walk in from sixty units off the left edge. Before the fade
        // was measured from the chip's leading edge they slid in as a
        // hard-clipped half-chip sitting against the frame.
        val bare = scene { }
        val entering = scene { engine ->
            engine.enemySystem().spawnEscort(EnemyType.SQL_BLIND, 0, 0f, 5)
        }
        // Column zero must be untouched: nothing may be painted against the
        // edge of the field by a threat that has not finished arriving.
        var painted = 0
        for (y in 0 until height) {
            for (x in 0 until 3) {
                if (bare.getPixel(x, y) != entering.getPixel(x, y)) painted++
            }
        }
        assertTrue("$painted pixels of a clipped chip on the field edge", painted == 0)
    }

    @Test
    fun `the readouts change nothing else on the field`() {
        // A frame is 1,216,000 pixels. Changing the wave may repaint the wave
        // number and nothing else; the same for crypto. If either number ever
        // starts driving some other part of the render, this catches it.
        val wave = waveInk
        assertTrue("wave ink is ${wave.pixels} px wide-spread", wave.pixels < 2_500)
        assertTrue("wave ink spans from x=${wave.minX}", wave.minX > width * 3 / 4)

        val crypto = cryptoInk
        assertTrue("crypto ink is ${crypto.pixels} px wide-spread", crypto.pixels < 4_000)
        assertTrue("crypto ink spans from x=${crypto.minX}", crypto.minX > width * 3 / 4)
    }
}
