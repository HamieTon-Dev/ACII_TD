package com.cyopstd.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.cyopstd.game.core.GameMap
import com.cyopstd.game.core.LevelTheme
import com.cyopstd.game.core.Waypoint
import com.cyopstd.game.core.WorldGeometry
import com.cyopstd.game.engine.GameEngine
import com.cyopstd.game.ui.game.BattlefieldRenderOptions
import com.cyopstd.game.ui.game.BattlefieldRenderer
import com.cyopstd.game.ui.game.BattlefieldSelection
import com.cyopstd.game.ui.game.WorldTransform
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Layout and colour candidates for levels 6–10 (backlog ♡1), for the owner to
 * choose from. Real [GameMap]s, the real renderer, real build spots. Not an
 * assertion suite.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChapterTwoPreview {

    private val s = WorldGeometry.SPAWN_X
    private val r = WorldGeometry.SERVER_X
    private val c = WorldGeometry.CORE_Y
    private fun w(x: Float, y: Float) = Waypoint(x, y)
    private val rows = FloatArray(21) { 30f + it * 35f }

    private fun map(id: String, name: String, tagline: String, vararg lanes: Array<Waypoint>,
                    extra: List<Waypoint> = emptyList(), theme: LevelTheme? = null) =
        GameMap(id = id, displayName = name, tagline = tagline, laneWaypoints = arrayOf(*lanes),
            candidateRows = rows, extraNodes = extra, theme = theme)

    // ------------------------------------------------------------ level 6

    private fun l6a(theme: LevelTheme? = null) = map("l6a", "6A · SPIRAL",
        "One long route drops in from the top and spirals outward to the rack.",
        arrayOf(w(560f, -60f), w(560f, c), w(840f, c), w(840f, 220f), w(290f, 220f), w(290f, 550f),
            w(1060f, 550f), w(1060f, 80f), w(1190f, 80f), w(1190f, c), w(r, c)), theme = theme)

    private fun l6b() = map("l6b", "6B · TRIDENT",
        "Three routes: a straight shot down the middle, two that zigzag above and below it.",
        arrayOf(w(s, 90f), w(480f, 90f), w(480f, 230f), w(880f, 230f), w(880f, 90f), w(1190f, 90f), w(1190f, c), w(r, c)),
        arrayOf(w(s, c), w(r, c)),
        arrayOf(w(s, 670f), w(480f, 670f), w(480f, 530f), w(880f, 530f), w(880f, 670f), w(1190f, 670f), w(1190f, c), w(r, c)))

    private fun l6c() = map("l6c", "6C · LADDER",
        "Two routes swap between the top and bottom rails, crossing on every rung.",
        arrayOf(w(s, 110f), w(380f, 110f), w(380f, 650f), w(780f, 650f), w(780f, 110f), w(1190f, 110f), w(1190f, c), w(r, c)),
        arrayOf(w(s, 650f), w(230f, 650f), w(230f, 270f), w(580f, 270f), w(580f, 490f), w(980f, 490f), w(980f, 270f), w(1120f, 270f), w(1120f, c), w(r, c)))

    // ------------------------------------------------------------ level 7

    private fun l7a() = map("l7a", "7A · MAZE",
        "One very long route snakes top to bottom eight times. Every tower sees it twice.",
        arrayOf(w(s, 80f), *(0 until 8).flatMap { i ->
            val x = 110f + i * 150f
            if (i % 2 == 0) listOf(w(x, 80f), w(x, 680f)) else listOf(w(x, 680f), w(x, 80f))
        }.toTypedArray(), w(1190f, 80f), w(1190f, c), w(r, c)),
        extra = (0 until 7).flatMap { i -> listOf(170f, 240f, 310f, 380f, 450f, 520f, 590f).map { y -> w(185f + i * 150f, y) } })

    private fun l7b() = map("l7b", "7B · PINCER",
        "Routes from the top, the bottom and the left close on one road to the rack.",
        arrayOf(w(400f, -60f), w(400f, 150f), w(1100f, 150f), w(1100f, c), w(r, c)),
        arrayOf(w(s, c), w(1100f, c), w(r, c)),
        arrayOf(w(400f, 820f), w(400f, 610f), w(1100f, 610f), w(1100f, c), w(r, c)))

    private fun l7c() = map("l7c", "7C · STAIRCASE",
        "Two routes step down and up toward each other and meet just before the rack.",
        arrayOf(w(s, 70f), w(300f, 70f), w(300f, 190f), w(600f, 190f), w(600f, 300f), w(1190f, 300f), w(1190f, c), w(r, c)),
        arrayOf(w(s, 690f), w(300f, 690f), w(300f, 570f), w(600f, 570f), w(600f, 460f), w(1190f, 460f), w(1190f, c), w(r, c)))

    // ------------------------------------------------------------ level 8

    private fun l8a() = map("l8a", "8A · RING ROAD",
        "Two routes split at the gate and circle the whole board, one over, one under.",
        arrayOf(w(s, c), w(200f, c), w(200f, 80f), w(1150f, 80f), w(1150f, c), w(r, c)),
        arrayOf(w(s, c), w(200f, c), w(200f, 680f), w(1150f, 680f), w(1150f, c), w(r, c)),
        arrayOf(w(680f, -60f), w(680f, c), w(1150f, c), w(r, c)))

    private fun l8b() = map("l8b", "8B · FOUR GATES",
        "Four routes enter; they pair up at the middle and become one road at the end.",
        arrayOf(w(s, 70f), w(400f, 70f), w(600f, 190f), w(850f, 190f), w(1050f, c), w(r, c)),
        arrayOf(w(s, 310f), w(400f, 310f), w(600f, 190f), w(850f, 190f), w(1050f, c), w(r, c)),
        arrayOf(w(s, 450f), w(400f, 450f), w(600f, 570f), w(850f, 570f), w(1050f, c), w(r, c)),
        arrayOf(w(s, 690f), w(400f, 690f), w(600f, 570f), w(850f, 570f), w(1050f, c), w(r, c)))

    private fun l8c() = map("l8c", "8C · ZIGZAG",
        "One route slashes corner to corner across the board five times.",
        arrayOf(w(s, 90f), w(100f, 90f), w(320f, 670f), w(540f, 90f), w(760f, 670f), w(980f, 90f),
            w(1190f, 670f), w(1190f, c), w(r, c)))

    // ------------------------------------------------------------ level 9

    private fun l9a() = map("l9a", "9A · STARBURST",
        "A long straight road from the left, and two more that dive in from the top and bottom.",
        arrayOf(w(s, c), w(r, c)),
        arrayOf(w(300f, -60f), w(300f, 160f), w(1000f, 160f), w(1000f, c), w(r, c)),
        arrayOf(w(300f, 820f), w(300f, 600f), w(1000f, 600f), w(1000f, c), w(r, c)))

    private fun l9b() = map("l9b", "9B · TRIPLE BRAID",
        "Three routes braid through each other twice: every lane changes side.",
        arrayOf(w(s, 120f), w(300f, 120f), w(500f, c), w(700f, c), w(900f, 640f), w(1150f, 640f), w(1190f, c), w(r, c)),
        arrayOf(w(s, c), w(300f, c), w(500f, 640f), w(700f, 640f), w(900f, 120f), w(1150f, 120f), w(1190f, c), w(r, c)),
        arrayOf(w(s, 640f), w(300f, 640f), w(500f, 120f), w(700f, 120f), w(900f, c), w(1190f, c), w(r, c)))

    private fun l9c() = map("l9c", "9C · CHECKPOINT",
        "Three routes funnel into one short checkpoint mid-board, then fan out again.",
        arrayOf(w(s, 90f), w(350f, 90f), w(600f, c), w(800f, c), w(1050f, 90f), w(1190f, 90f), w(1190f, c), w(r, c)),
        arrayOf(w(s, c), w(r, c)),
        arrayOf(w(s, 670f), w(350f, 670f), w(600f, c), w(800f, c), w(1050f, 670f), w(1190f, 670f), w(1190f, c), w(r, c)))

    // ----------------------------------------------------------- level 10

    private fun l10a() = map("l10a", "10A · FIVE FRONTS",
        "Five short routes from three edges. The hardest board in the game.",
        arrayOf(w(s, 150f), w(600f, 150f), w(600f, c), w(r, c)),
        arrayOf(w(s, 610f), w(600f, 610f), w(600f, c), w(r, c)),
        arrayOf(w(820f, -60f), w(820f, 220f), w(1000f, 220f), w(1000f, c), w(r, c)),
        arrayOf(w(820f, 820f), w(820f, 540f), w(1000f, 540f), w(1000f, c), w(r, c)),
        arrayOf(w(1200f, -60f), w(1200f, c), w(r, c)))

    private fun l10b() = map("l10b", "10B · LABYRINTH",
        "Two routes each wind back and forth three times across their half of the board.",
        arrayOf(w(s, 50f), w(1150f, 50f), w(1150f, 210f), w(200f, 210f), w(200f, 300f), w(1190f, 300f), w(1190f, c), w(r, c)),
        arrayOf(w(s, 710f), w(1150f, 710f), w(1150f, 550f), w(200f, 550f), w(200f, 460f), w(1190f, 460f), w(1190f, c), w(r, c)))

    private fun l10c() = map("l10c", "10C · GRIDLOCK",
        "Two roads across, two down: four routes that cut through each other.",
        arrayOf(w(s, 200f), w(1190f, 200f), w(1190f, c), w(r, c)),
        arrayOf(w(s, 560f), w(1190f, 560f), w(1190f, c), w(r, c)),
        arrayOf(w(500f, -60f), w(500f, c), w(r, c)),
        arrayOf(w(900f, 820f), w(900f, c), w(r, c)))

    @Test
    fun layouts() {
        for ((name, m) in listOf(
            "6A" to l6a(), "6B" to l6b(), "6C" to l6c(),
            "7A" to l7a(), "7B" to l7b(), "7C" to l7c(),
            "8A" to l8a(), "8B" to l8b(), "8C" to l8c(),
            "9A" to l9a(), "9B" to l9b(), "9C" to l9c(),
            "10A" to l10a(), "10B" to l10b(), "10C" to l10c()
        )) render(name, m)
    }

    /** Colour options, each drawn on the same layout so only the colour changes. */
    @Test
    fun colours() {
        fun t(b: Long, g: Long, f: Long, e: Long, m: Long) =
            LevelTheme(b.toInt(), g.toInt(), f.toInt(), e.toInt(), m.toInt())
        val options = listOf(
            "C1 VIOLET" to t(0xFF0B0514, 0xFF24123A, 0xFF2A1440, 0xFF5A2E8A, 0xFFA070E0),
            "C2 MIDNIGHT BLUE" to t(0xFF040816, 0xFF0F1E44, 0xFF10224E, 0xFF2A4C9A, 0xFF5F8FE8),
            "C3 MAGENTA" to t(0xFF12040E, 0xFF3A0F2E, 0xFF3A0F30, 0xFF7A1E62, 0xFFD04CA8),
            "C4 TEAL" to t(0xFF031010, 0xFF0E3434, 0xFF0F3838, 0xFF1F6E6E, 0xFF3FC0B8),
            "C5 STEEL" to t(0xFF080A0E, 0xFF20262E, 0xFF232A34, 0xFF4A5666, 0xFFA8B8CC),
            "C6 SYNTHWAVE" to t(0xFF0A0418, 0xFF2A0F4A, 0xFF1A0F3A, 0xFFB0268A, 0xFF26C6DA),
            "C7 TOXIC LIME" to t(0xFF070C02, 0xFF1E3308, 0xFF22380A, 0xFF4E8A14, 0xFFA8E040),
            "C8 COPPER" to t(0xFF0C0605, 0xFF2E1A14, 0xFF32190F, 0xFF7A4028, 0xFFD08A5C)
        )
        for ((name, theme) in options) render(name.substringBefore(' '), l6a(theme).let {
            GameMap(id = it.id, displayName = name, tagline = "Colour option, shown on layout 6A.",
                laneWaypoints = it.laneWaypoints, candidateRows = rows, theme = theme)
        })
    }

    private fun render(name: String, map: GameMap) {
        val engine = GameEngine()
        engine.selectMap(map)
        engine.startNewRun()
        engine.update(1f / 60f, 1f)
        val header = 70
        val w = WorldGeometry.WIDTH.toInt()
        val h = WorldGeometry.HEIGHT.toInt()
        val bmp = Bitmap.createBitmap(w, h + header, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        cv.drawColor(Color.parseColor("#070B14"))
        cv.save()
        cv.translate(0f, header.toFloat())
        BattlefieldRenderer().draw(
            canvas = cv, engine = engine,
            transform = WorldTransform(WorldGeometry.WIDTH, WorldGeometry.HEIGHT),
            options = BattlefieldRenderOptions(backgroundAnimation = false),
            selection = BattlefieldSelection(), time = 1f
        )
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 2f; color = Color.parseColor("#6600FF9C")
        }
        for (node in map.nodes) if (!node.serverSlot) cv.drawCircle(node.x, node.y, WorldGeometry.NODE_RADIUS, dot)
        cv.restore()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.parseColor("#E6EEFA")
        paint.textSize = 32f
        cv.drawText("${map.displayName}   ·   ${map.fieldNodes.size} agent spots   ·   ${map.laneCount} routes   ·   " +
            "route length ${map.laneLength.min().toInt()}–${map.laneLength.max().toInt()}", 16f, 34f, paint)
        paint.textSize = 22f
        paint.color = Color.parseColor("#93A6C4")
        cv.drawText(map.tagline, 16f, 62f, paint)
        val out = File("build/chapter2").apply { mkdirs() }
        File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("CH2 $name spots=${map.fieldNodes.size} lengths=${map.laneLength.map { it.toInt() }}")
    }
}
