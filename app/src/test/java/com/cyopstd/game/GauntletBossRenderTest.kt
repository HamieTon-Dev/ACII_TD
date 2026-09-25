package com.cyopstd.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import com.cyopstd.game.core.Maps
import com.cyopstd.game.core.WorldGeometry
import com.cyopstd.game.engine.GameEngine
import com.cyopstd.game.model.BossPalette
import com.cyopstd.game.model.BossVariant
import com.cyopstd.game.model.EnemyType
import com.cyopstd.game.ui.game.BattlefieldRenderOptions
import com.cyopstd.game.ui.game.BattlefieldRenderer
import com.cyopstd.game.ui.game.BattlefieldSelection
import com.cyopstd.game.ui.game.WorldTransform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What the gauntlet's four bosses actually look like on the board.
 *
 * Two things here cannot be checked by reading the table. The first is that
 * the marks the owner chose — `○`, `●`, `₩`, `¥` — exist in
 * the font the battlefield draws with; a missing glyph is not a compile error
 * and not a crash, it is a row of empty boxes that nobody sees until a player
 * does. The second is the colour: the model names a *theme* and only the
 * renderer knows what a theme looks like, so "give them a cool color theme"
 * is a claim about pixels and has to be measured in pixels.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GauntletBossRenderTest {

    private val width = WorldGeometry.WIDTH.toInt()
    private val height = WorldGeometry.HEIGHT.toInt()

    // --------------------------------------------------------- the font itself

    @Test
    fun `the monospace face has every mark these bosses are drawn with`() {
        // The same face the battlefield builds its text paints from.
        val paint = Paint().apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = 26f
        }
        for (boss in BossVariant.entries) {
            for (codePoint in boss.glyph.codePoints()) {
                val text = String(Character.toChars(codePoint))
                assertTrue(
                    "${boss.displayName} is drawn with U+%04X, which the " +
                        "battlefield font cannot render".format(codePoint),
                    paint.hasGlyph(text)
                )
            }
        }
    }

    @Test
    fun `a hollow eye and a filled eye are not the same picture`() {
        // hasGlyph can be satisfied by a fallback that maps both circles to
        // the same shape. If these two ever draw identically the player cannot
        // tell which hat is about to be jammed, which is the entire read.
        //
        // Measured as a diff between the two frames rather than against the
        // empty board: both bosses paint the same chassis over the same
        // pixels, so "differs from an empty board" counts the chassis twice
        // and the glyph not at all. The first version of this test did exactly
        // that and reported the two eyes identical to the pixel.
        val white = bossFrame(BossVariant.WHITE_EYE)
        val black = bossFrame(BossVariant.BLACK_EYE)
        assertTrue("WHITE EYE drew nothing at all", ink(white) > 0)
        assertTrue(
            "the two eyes rendered as the same picture",
            difference(white, black) > 60
        )

        // And the difference is the one a player would name: filled carries
        // more ink than hollow.
        val paint = Paint().apply {
            isAntiAlias = true
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textSize = 56f
            color = 0xFFFFFFFF.toInt()
        }
        assertTrue(
            "\u25CF should be heavier than \u25CB",
            glyphInk("\u25CF", paint) > glyphInk("\u25CB", paint)
        )
    }

    /** Pixels [text] puts down on its own, drawn with [paint]. */
    private fun glyphInk(text: String, paint: Paint): Int {
        val bitmap = Bitmap.createBitmap(160, 100, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawText(text, 10f, 70f, paint)
        var count = 0
        for (y in 0 until 100) {
            for (x in 0 until 160) if (bitmap.getPixel(x, y) != 0) count++
        }
        return count
    }

    // ------------------------------------------------------------- the colour

    @Test
    fun `the gauntlet bosses are lit cool and the originals stay red`() {
        for (boss in BossVariant.entries) {
            val tint = averageTint(bossFrame(boss))
            if (boss.palette == BossPalette.HOSTILE) {
                assertTrue(
                    "${boss.displayName} should still read as red, measured $tint",
                    tint.red > tint.blue
                )
            } else {
                assertTrue(
                    "${boss.displayName} was asked for a cool theme, measured $tint",
                    tint.blue > tint.red
                )
            }
        }
    }

    @Test
    fun `the strobing pair actually strobes`() {
        // SPECTRUM is a hue sweep, so two frames a fraction of a second apart
        // must be different hues. A constant is a much easier thing to ship by
        // accident than it looks.
        val first = dominantHue(bossFrame(BossVariant.WHITE_EYE, time = 0f))
        val later = dominantHue(bossFrame(BossVariant.WHITE_EYE, time = 0.25f))
        assertTrue(
            "the spectrum never moved: hue $first at 0s and $later at 0.25s",
            kotlin.math.abs(later - first) > 20f
        )
    }

    @Test
    fun `the steady pair does not strobe`() {
        // Measured as hue rather than as average colour, because every boss in
        // the game has always had a pulsing glow: an earlier version of this
        // test compared mean pixel values and failed on that pulse, which is
        // not the thing it is asking about.
        val first = dominantHue(bossFrame(BossVariant.BOUNTY, time = 0f))
        val later = dominantHue(bossFrame(BossVariant.BOUNTY, time = 0.25f))
        assertEquals("BOUNTY was specified steady, not cycling", first, later, 1.5f)
        assertNotEquals(
            "the two steady heavies should not be the same colour",
            dominantHue(bossFrame(BossVariant.BOUNTY)),
            dominantHue(bossFrame(BossVariant.PAYOUT))
        )
    }

    // ------------------------------------------------------------------ rig

    /** A rendered board, and where on it the boss ended up. */
    private class Frame(val bitmap: Bitmap, val cx: Int, val cy: Int)

    /** A single boss of [variant] parked mid-route, and nothing else moving. */
    private fun bossFrame(variant: BossVariant, time: Float = 0f): Frame {
        val engine = GameEngine()
        engine.selectMap(Maps.HUGGING_FACE)
        engine.startNewRun()
        val boss = engine.enemies.obtain()!!
        boss.reset()
        boss.active = true
        boss.type = EnemyType.BOSS
        boss.isBoss = true
        boss.isElite = true
        boss.variant = variant
        boss.lane = 0
        boss.progress = engine.map.laneLength[0] * 0.5f
        boss.maxHealth = 1_000f
        boss.health = 1_000f
        boss.baseSpeed = 0f
        // Zero phase, so the strobe's position is the time and nothing else.
        boss.phase = 0f
        engine.update(1f / 60f, 1f)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        BattlefieldRenderer().draw(
            canvas = Canvas(bitmap),
            engine = engine,
            transform = WorldTransform(WorldGeometry.WIDTH, WorldGeometry.HEIGHT),
            options = BattlefieldRenderOptions(backgroundAnimation = false),
            selection = BattlefieldSelection(),
            time = time
        )
        return Frame(bitmap, boss.x.toInt(), boss.y.toInt())
    }

    /** A frame with no boss at all, so the board itself is not what is measured. */
    private val control: Bitmap by lazy {
        val engine = GameEngine()
        engine.selectMap(Maps.HUGGING_FACE)
        engine.startNewRun()
        engine.update(1f / 60f, 1f)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        BattlefieldRenderer().draw(
            canvas = Canvas(bitmap),
            engine = engine,
            transform = WorldTransform(WorldGeometry.WIDTH, WorldGeometry.HEIGHT),
            options = BattlefieldRenderOptions(backgroundAnimation = false),
            selection = BattlefieldSelection(),
            time = 0f
        )
        bitmap
    }

    private data class Tint(val red: Int, val green: Int, val blue: Int)

    /**
     * The boss chassis, and only that.
     *
     * Deliberately stops short of the health bar above it. That bar is full
     * green at full health, which is the most saturated thing on the board by
     * some margin — an earlier version of these tests sampled the whole frame
     * and reported the same hue, 156 degrees, for all seven bosses. It was
     * measuring the health bar every time.
     */
    private inline fun forEachChassisPixel(frame: Frame, body: (Int) -> Unit) {
        val left = (frame.cx - CHASSIS_HALF_W).coerceAtLeast(0)
        val right = (frame.cx + CHASSIS_HALF_W).coerceAtMost(width - 1)
        val top = (frame.cy - CHASSIS_HALF_H).coerceAtLeast(0)
        val bottom = (frame.cy + CHASSIS_HALF_H).coerceAtMost(height - 1)
        for (y in top..bottom) {
            for (x in left..right) {
                val pixel = frame.bitmap.getPixel(x, y)
                if (pixel == control.getPixel(x, y)) continue
                body(pixel)
            }
        }
    }

    /** How many pixels of the chassis this frame has that the empty board does not. */
    private fun ink(frame: Frame): Int {
        var count = 0
        forEachChassisPixel(frame) { count++ }
        return count
    }

    /** Pixels where these two frames disagree, anywhere on the board. */
    private fun difference(a: Frame, b: Frame): Int {
        var count = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (a.bitmap.getPixel(x, y) != b.bitmap.getPixel(x, y)) count++
            }
        }
        return count
    }

    /** The mean colour of everything the boss added to its own chassis. */
    private fun averageTint(frame: Frame): Tint {
        var r = 0L
        var g = 0L
        var b = 0L
        var n = 0
        forEachChassisPixel(frame) { pixel ->
            r += android.graphics.Color.red(pixel)
            g += android.graphics.Color.green(pixel)
            b += android.graphics.Color.blue(pixel)
            n++
        }
        assertTrue("nothing was drawn to measure", n > 0)
        return Tint((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
    }

    /**
     * The hue of the most saturated thing on the chassis.
     *
     * The outline is drawn at near-full alpha in the accent colour, so the
     * most colourful pixel in the box is the accent itself. Hue survives the
     * alpha pulse that every boss glow has; mean brightness does not, which is
     * why this measures hue.
     */
    private fun dominantHue(frame: Frame): Float {
        var best = 0
        var bestChroma = -1
        forEachChassisPixel(frame) { pixel ->
            val r = android.graphics.Color.red(pixel)
            val g = android.graphics.Color.green(pixel)
            val b = android.graphics.Color.blue(pixel)
            val chroma = maxOf(r, g, b) - minOf(r, g, b)
            if (chroma > bestChroma) {
                bestChroma = chroma
                best = pixel
            }
        }
        assertTrue("nothing was drawn to measure", bestChroma > 0)
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(best, hsv)
        return hsv[0]
    }

    private companion object {
        /** Matches the boss chassis in BattlefieldRenderer.drawBoss. */
        const val CHASSIS_HALF_W = 74
        const val CHASSIS_HALF_H = 46
    }
}
