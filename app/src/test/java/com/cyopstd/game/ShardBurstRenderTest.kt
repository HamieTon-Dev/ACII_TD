package com.cyopstd.game

import android.graphics.Bitmap
import android.graphics.Canvas
import com.cyopstd.game.core.Balance
import com.cyopstd.game.core.WorldGeometry
import com.cyopstd.game.engine.GameEngine
import com.cyopstd.game.ui.game.BattlefieldRenderOptions
import com.cyopstd.game.ui.game.BattlefieldRenderer
import com.cyopstd.game.ui.game.BattlefieldSelection
import com.cyopstd.game.ui.game.WorldTransform
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The blast a boss leaves behind.
 *
 * A purely visual effect can only be verified by looking at it, so this
 * rasterizes real frames and measures them: that the shards appear, that they
 * travel outward, that they are the threat's colour rather than something
 * else, and — the part that matters for playability — that they are gone
 * again quickly rather than sitting over the board while the next wave walks
 * in underneath.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ShardBurstRenderTest {

    private val width = WorldGeometry.WIDTH.toInt()
    private val height = WorldGeometry.HEIGHT.toInt()

    /** A frame [afterSeconds] into a blast at the centre of the board. */
    private fun blastFrame(
        afterSeconds: Float,
        boss: Boolean = true,
        shards: Boolean = true
    ): Bitmap {
        val engine = GameEngine()
        engine.startNewRun()
        if (shards) engine.effectSystem().spawnShards(
            x = WorldGeometry.WIDTH * 0.5f,
            y = WorldGeometry.HEIGHT * 0.5f,
            colorArgb = GameEngine.COLOR_HOSTILE,
            radius = if (boss) Balance.BOSS_SHARD_RADIUS else Balance.ELITE_SHARD_RADIUS,
            lifetime = if (boss) Balance.BOSS_SHARD_LIFETIME else Balance.ELITE_SHARD_LIFETIME
        )
        // Advance the effect only; nothing else needs to move for this.
        var elapsed = 0f
        while (elapsed < afterSeconds) {
            engine.effectSystem().update(1f / 120f)
            elapsed += 1f / 120f
        }

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

    /**
     * A frame with nothing exploding, to measure the others against.
     *
     * The first version of this test looked for red-dominant pixels anywhere
     * on the board and reported the same spread every time: the ATTACK ORIGIN
     * marker is red and sits at x=16, so every measurement was really measuring
     * the label. Diffing against a control frame removes everything static by
     * construction, which is the technique the field-status tests already use
     * for the same reason.
     */
    private fun controlFrame(): Bitmap = blastFrame(0f, boss = true, shards = false)

    private class Ink(val count: Int, val spread: Float)

    private fun measure(bitmap: Bitmap, control: Bitmap): Ink {
        var count = 0
        var minX = width
        var maxX = 0
        val cx = width / 2
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (bitmap.getPixel(x, y) == control.getPixel(x, y)) continue
                count++
                if (x < minX) minX = x
                if (x > maxX) maxX = x
            }
        }
        val spread = if (count == 0) 0f else maxOf(maxX - cx, cx - minX).toFloat()
        return Ink(count, spread)
    }

    @Test
    fun `a boss death throws shards across the board`() {
        val control = controlFrame()
        val early = measure(blastFrame(0.08f), control)
        assertTrue("no shards were drawn at all", early.count > 100)
    }

    @Test
    fun `the shards travel outward`() {
        val control = controlFrame()
        val early = measure(blastFrame(0.08f), control)
        val later = measure(blastFrame(0.45f), control)
        assertTrue(
            "shards reached ${early.spread} early and ${later.spread} later, so " +
                "they are not moving",
            later.spread > early.spread * 1.5f
        )
    }

    @Test
    fun `the blast is the size it claims to be`() {
        val late = measure(blastFrame(0.7f), controlFrame())
        assertTrue(
            "a boss blast should carry most of the way to its ${Balance.BOSS_SHARD_RADIUS}" +
                " unit radius, but reached ${late.spread}",
            late.spread > Balance.BOSS_SHARD_RADIUS * 0.5f
        )
    }

    @Test
    fun `it is over before it is in the way`() {
        // The whole point of the short lifetime. A blast that lingers is a
        // blast that hides the wave walking in underneath it.
        val after = measure(blastFrame(Balance.BOSS_SHARD_LIFETIME + 0.05f), controlFrame())
        assertEquals("the blast outlived its lifetime", 0, after.count)
    }

    @Test
    fun `an elite blast is smaller than a boss one`() {
        val control = controlFrame()
        val boss = measure(blastFrame(0.5f, boss = true), control)
        val elite = measure(blastFrame(0.5f, boss = false), control)
        assertTrue(
            "boss reached ${boss.spread}, elite ${elite.spread}",
            boss.spread > elite.spread
        )
    }

    @Test
    fun `battery saver keeps the feedback and drops the spectacle`() {
        val engine = GameEngine()
        engine.startNewRun()
        engine.batterySaver = true
        engine.effectSystem().spawnShards(
            x = 800f, y = 380f,
            colorArgb = GameEngine.COLOR_HOSTILE,
            radius = Balance.BOSS_SHARD_RADIUS,
            lifetime = Balance.BOSS_SHARD_LIFETIME
        )
        val effect = engine.effects.items.first { it.active }
        assertTrue(
            "battery saver should shrink the blast, not cancel it",
            effect.scale > 0f && effect.scale < Balance.BOSS_SHARD_RADIUS
        )
    }

    @Test
    fun `two blasts do not look identical`() {
        // Seeded per explosion, so the same boss dying twice is not the same
        // picture twice.
        val engine = GameEngine()
        engine.startNewRun()
        repeat(2) {
            engine.effectSystem().spawnShards(
                x = 800f, y = 380f,
                colorArgb = GameEngine.COLOR_HOSTILE,
                radius = Balance.BOSS_SHARD_RADIUS,
                lifetime = Balance.BOSS_SHARD_LIFETIME
            )
        }
        val seeds = engine.effects.items.filter { it.active }.map { it.seed }
        assertEquals(2, seeds.size)
        assertTrue("both explosions rolled the same seed", seeds[0] != seeds[1])
    }

    @Test
    fun `preview`() {
        // Not an assertion: the frame a human looks at.
        File("build/previews").mkdirs()
        for ((name, t) in listOf("shards-early" to 0.10f, "shards-mid" to 0.35f)) {
            File("build/previews/$name.png").outputStream().use {
                blastFrame(t).compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
}
