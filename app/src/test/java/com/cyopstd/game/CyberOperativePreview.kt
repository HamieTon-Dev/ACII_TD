package com.cyopstd.game

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * ♡3 look options for CYBER OPERATIVE (owner, 2026-10-01: *"need [it] to look
 * identical to the game's logo, use something new for this character if
 * necessary like an icon"*). Every option draws the real launcher vector
 * (`ic_launcher_foreground`), not a copy of it, so whichever is picked is the
 * logo pixel for pixel. Not an assertion suite; writes
 * `build/previews/cyber-operative-options.png`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CyberOperativePreview {

    private val context = ApplicationProvider.getApplicationContext<Application>()

    private fun logo(canvas: Canvas, cx: Float, cy: Float, size: Float, crop: Boolean) {
        // A: the launcher foreground itself. B-D: ic_cyber_operative, the same
        // paths cropped to the shield.
        val id = if (crop) R.drawable.ic_cyber_operative else R.drawable.ic_launcher_foreground
        val drawable = ResourcesCompat.getDrawable(context.resources, id, null)!!
        val halfW = if (crop) size * 64f / 74f / 2 else size / 2
        drawable.setBounds((cx - halfW).toInt(), (cy - size / 2).toInt(), (cx + halfW).toInt(), (cy + size / 2).toInt())
        drawable.draw(canvas)
    }

    @Test
    fun `render the options`() {
        val w = 1600
        val h = 700
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.parseColor("#070B14"))
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            color = Color.parseColor("#E6EEFA")
            textSize = 24f
        }
        val small = Paint(text).apply { textSize = 20f; color = Color.parseColor("#93A6C4") }
        val lane = Paint().apply { color = Color.parseColor("#12203A") }
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Color.parseColor("#6623C55E")
        }
        val node = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.parseColor("#1E2D47")
        }
        val options = listOf(
            "A" to "The whole logo",
            "B" to "Shield and face only",
            "C" to "B + anti-jam aura",
            "D" to "B in a node badge"
        )
        val colW = w / options.size.toFloat()
        options.forEachIndexed { i, (letter, label) ->
            val cx = colW * i + colW / 2
            canvas.drawText("$letter  $label", cx, 50f, text)
            // Large, to see it.
            when (letter) {
                "A" -> logo(canvas, cx, 230f, 280f, crop = false)
                else -> logo(canvas, cx, 230f, 220f, crop = true)
            }
            if (letter == "C") canvas.drawCircle(cx, 230f, 150f, ring)
            if (letter == "D") canvas.drawRoundRect(RectF(cx - 125, 105f, cx + 125, 355f), 18f, 18f, node)
            // At board size, beside a route, as it would be deployed.
            canvas.drawText("on the board", cx, 430f, small)
            canvas.drawRect(cx - colW / 2 + 20, 560f, cx + colW / 2 - 20, 600f, lane)
            when (letter) {
                "A" -> logo(canvas, cx, 510f, 76f, crop = false)
                else -> logo(canvas, cx, 510f, 54f, crop = true)
            }
            if (letter == "C") canvas.drawCircle(cx, 510f, 46f, ring)
            if (letter == "D") canvas.drawRoundRect(RectF(cx - 32, 478f, cx + 32, 542f), 8f, 8f, node)
            canvas.drawText("[>_<]  max 2  ·  TARPIT range", cx, 650f, small)
        }
        File("build/previews").mkdirs()
        File("build/previews/cyber-operative-options.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    /** The agents as deployed, through the real renderer: writes `build/previews/new-agents-on-board.png`. */
    @Test
    fun `render the new agents on a board`() {
        val random = kotlin.random.Random(1)
        val engine = com.cyopstd.game.engine.GameEngine(random, com.cyopstd.game.engine.WaveGenerator(random))
        engine.isAgentUnlocked = { true }
        engine.selectMap(com.cyopstd.game.core.Maps.HUGGING_FACE)
        engine.startNewRun()
        engine.addCrypto(1_000_000, countAsEarned = false)
        val nodes = com.cyopstd.game.core.Maps.HUGGING_FACE.nodesByCoverage
        val types = listOf(com.cyopstd.game.model.AgentType.CYBER_OPERATIVE, com.cyopstd.game.model.AgentType.ANTI_DUCK,
            com.cyopstd.game.model.AgentType.REDHAT, com.cyopstd.game.model.AgentType.FIREWALL)
        types.forEachIndexed { i, t -> engine.placeAgent(t, nodes[i].id) }
        val w = com.cyopstd.game.core.WorldGeometry.WIDTH.toInt()
        val h = com.cyopstd.game.core.WorldGeometry.HEIGHT.toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        com.cyopstd.game.ui.game.BattlefieldRenderer().draw(
            canvas = Canvas(bmp), engine = engine,
            transform = com.cyopstd.game.ui.game.WorldTransform(com.cyopstd.game.core.WorldGeometry.WIDTH, com.cyopstd.game.core.WorldGeometry.HEIGHT),
            options = com.cyopstd.game.ui.game.BattlefieldRenderOptions(backgroundAnimation = false),
            selection = com.cyopstd.game.ui.game.BattlefieldSelection(), time = 1f
        )
        File("build/previews").mkdirs()
        File("build/previews/new-agents-on-board.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("AGENTS " + types.mapIndexed { i, t -> "$t@${nodes[i].x.toInt()},${nodes[i].y.toInt()}" })
    }
}
