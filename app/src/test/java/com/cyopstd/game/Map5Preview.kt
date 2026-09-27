package com.cyopstd.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.cyopstd.game.core.GameMap
import com.cyopstd.game.core.Waypoint
import com.cyopstd.game.core.WorldGeometry
import com.cyopstd.game.engine.GameEngine
import com.cyopstd.game.ui.game.BattlefieldRenderOptions
import com.cyopstd.game.ui.game.BattlefieldRenderer
import com.cyopstd.game.ui.game.BattlefieldSelection
import com.cyopstd.game.ui.game.WorldTransform
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Layout candidates for map 5 (backlog R2), for the owner to pick from, drawn
 * the same way as [NeuralMeshPreview]: real [GameMap]s, real renderer, real
 * build spots. Not an assertion suite.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Map5Preview {

    private val spawn = WorldGeometry.SPAWN_X
    private val rack = WorldGeometry.SERVER_X
    private val core = WorldGeometry.CORE_Y
    private fun w(x: Float, y: Float) = Waypoint(x, y)

    /** A — HELIX: two routes weave through each other three times, like a strand of DNA. */
    @Test
    fun `A helix`() = render("A-helix", GameMap(
        id = "m5_helix", displayName = "A · HELIX",
        tagline = "Two routes weave through each other three times before the rack.",
        laneWaypoints = arrayOf(
            arrayOf(w(spawn, 150f), w(150f, 150f), w(330f, 610f), w(510f, 610f), w(690f, 150f),
                w(870f, 150f), w(1050f, 610f), w(1190f, 610f), w(1190f, core), w(rack, core)),
            arrayOf(w(spawn, 610f), w(150f, 610f), w(330f, 150f), w(510f, 150f), w(690f, 610f),
                w(870f, 610f), w(1050f, 150f), w(1190f, 150f), w(1190f, core), w(rack, core))
        ),
        candidateRows = floatArrayOf(60f, 250f, 380f, 510f, 700f)
    ))

    /** B — HOURGLASS: two routes squeeze through one short choke mid-board, then part again. */
    @Test
    fun `B hourglass`() = render("B-hourglass", GameMap(
        id = "m5_hourglass", displayName = "B · HOURGLASS",
        tagline = "Two routes squeeze through one choke mid-board, part, and meet again at the rack.",
        laneWaypoints = arrayOf(
            arrayOf(w(spawn, 100f), w(350f, 100f), w(620f, core), w(780f, core), w(1050f, 100f),
                w(1190f, 100f), w(1190f, core), w(rack, core)),
            arrayOf(w(spawn, 660f), w(350f, 660f), w(620f, core), w(780f, core), w(1050f, 660f),
                w(1190f, 660f), w(1190f, core), w(rack, core))
        ),
        candidateRows = floatArrayOf(30f, 190f, 290f, 470f, 570f, 730f)
    ))

    /** C — CROSSFIRE: four short routes from three edges. The hardest board of the five. */
    @Test
    fun `C crossfire`() = render("C-crossfire", GameMap(
        id = "m5_crossfire", displayName = "C · CROSSFIRE",
        tagline = "Four short routes from three edges. Little time, many fronts.",
        laneWaypoints = arrayOf(
            arrayOf(w(spawn, 220f), w(620f, 220f), w(620f, core), w(rack, core)),
            arrayOf(w(spawn, 540f), w(620f, 540f), w(620f, core), w(rack, core)),
            arrayOf(w(900f, -60f), w(900f, 200f), w(1110f, 200f), w(1110f, core), w(rack, core)),
            arrayOf(w(900f, 820f), w(900f, 560f), w(1110f, 560f), w(1110f, core), w(rack, core))
        ),
        candidateRows = floatArrayOf(90f, 300f, 460f, 670f)
    ))

    /** D — TWIN COMB: each route combs its own half of the board; the gap between is prime ground. */
    @Test
    fun `D twin comb`() = render("D-twin-comb", GameMap(
        id = "m5_twincomb", displayName = "D · TWIN COMB",
        tagline = "Each route combs its own half. Towers in the middle gap reach both.",
        laneWaypoints = arrayOf(
            arrayOf(w(spawn, 60f), w(160f, 60f), w(160f, 300f), w(340f, 300f), w(340f, 60f),
                w(520f, 60f), w(520f, 300f), w(700f, 300f), w(700f, 60f), w(880f, 60f),
                w(880f, 300f), w(1060f, 300f), w(1060f, 60f), w(1200f, 60f), w(1200f, core), w(rack, core)),
            arrayOf(w(spawn, 700f), w(160f, 700f), w(160f, 460f), w(340f, 460f), w(340f, 700f),
                w(520f, 700f), w(520f, 460f), w(700f, 460f), w(700f, 700f), w(880f, 700f),
                w(880f, 460f), w(1060f, 460f), w(1060f, 700f), w(1200f, 700f), w(1200f, core), w(rack, core))
        ),
        candidateRows = floatArrayOf(180f, 380f, 580f),
        extraNodes = listOf(250f, 430f, 610f, 790f, 970f).flatMap { x -> listOf(w(x, 150f), w(x, 610f)) }
    ))

    /** The shipped level. */
    @Test
    fun `shipped`() = render("shipped-ddos", com.cyopstd.game.core.Maps.DDOS)

    private fun render(name: String, map: GameMap, setup: (GameEngine) -> Unit = {}) {
        val engine = GameEngine()
        engine.selectMap(map)
        engine.startNewRun()
        setup(engine)
        engine.update(1f / 60f, 1f)

        val header = 70
        val w = WorldGeometry.WIDTH.toInt()
        val h = WorldGeometry.HEIGHT.toInt()
        val bmp = Bitmap.createBitmap(w, h + header, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.parseColor("#070B14"))
        c.save()
        c.translate(0f, header.toFloat())
        BattlefieldRenderer().draw(
            canvas = c, engine = engine,
            transform = WorldTransform(WorldGeometry.WIDTH, WorldGeometry.HEIGHT),
            options = BattlefieldRenderOptions(backgroundAnimation = false),
            selection = BattlefieldSelection(), time = 1f
        )
        // Mark each build spot plainly; in game an empty node is only a faint bracket.
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 2f; color = Color.parseColor("#6600FF9C")
        }
        for (node in map.nodes) c.drawCircle(node.x, node.y, WorldGeometry.NODE_RADIUS, dot)
        c.restore()

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.parseColor("#E6EEFA")
        paint.textSize = 30f
        c.drawText(
            "${map.displayName}   ·   ${map.nodes.size} nodes   ·   ${map.laneCount} routes   ·   " +
                "route length ${map.laneLength.min().toInt()}–${map.laneLength.max().toInt()}",
            16f, 32f, paint
        )
        paint.textSize = 20f
        paint.color = Color.parseColor("#93A6C4")
        c.drawText(map.tagline, 16f, 60f, paint)

        val out = File("build/map5").apply { mkdirs() }
        val f = File(out, "$name.png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(f.length() > 2_000)
        println("M5 $name nodes=${map.nodes.size} lengths=${map.laneLength.map { it.toInt() }}")
    }


}
