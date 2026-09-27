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
 * Layout candidates for map 4 (backlog R2), for the owner to pick from, drawn
 * the same way as [NeuralMeshPreview]: real [GameMap]s, real renderer, real
 * build spots. Not an assertion suite.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class Map4Preview {

    private val spawn = WorldGeometry.SPAWN_X
    private val rack = WorldGeometry.SERVER_X
    private val core = WorldGeometry.CORE_Y
    private fun w(x: Float, y: Float) = Waypoint(x, y)

    /** A — BRAID: three routes that swap places twice before the rack. */
    @Test
    fun `A braid`() = render("A-braid", GameMap(
        id = "m4_braid", displayName = "A · BRAID",
        tagline = "Three routes swap places twice. Every crossing is a spot that hits two.",
        laneWaypoints = arrayOf(
            arrayOf(w(spawn, 110f), w(250f, 110f), w(450f, core), w(650f, core), w(850f, 650f),
                w(1190f, 650f), w(1190f, core), w(rack, core)),
            arrayOf(w(spawn, core), w(250f, core), w(450f, 110f), w(1190f, 110f),
                w(1190f, core), w(rack, core)),
            arrayOf(w(spawn, 650f), w(650f, 650f), w(850f, core), w(rack, core))
        ),
        candidateRows = floatArrayOf(36f, 200f, 290f, 470f, 560f, 724f)
    ))

    /** B — ROUTER: two routes split around three blocks and meet at every choke. */
    @Test
    fun `B router`() = render("B-router", GameMap(
        id = "m4_router", displayName = "B · ROUTER",
        tagline = "Two routes split round three blocks and share every choke between them.",
        laneWaypoints = arrayOf(
            arrayOf(w(spawn, core), w(240f, core), w(240f, 130f), w(500f, 130f), w(500f, core),
                w(580f, core), w(580f, 130f), w(840f, 130f), w(840f, core),
                w(920f, core), w(920f, 130f), w(1180f, 130f), w(1180f, core), w(rack, core)),
            arrayOf(w(spawn, core), w(240f, core), w(240f, 630f), w(500f, 630f), w(500f, core),
                w(580f, core), w(580f, 630f), w(840f, 630f), w(840f, core),
                w(920f, core), w(920f, 630f), w(1180f, 630f), w(1180f, core), w(rack, core))
        ),
        candidateRows = floatArrayOf(60f, 255f, 505f, 700f),
        // Inside each block: the ground a branch passes on three sides.
        extraNodes = listOf(w(370f, core), w(710f, core), w(1050f, core))
    ))

    /** C — COMB: one route, six full-height passes. The longest map in the game. */
    @Test
    fun `C comb`() = render("C-comb", GameMap(
        id = "m4_comb", displayName = "C · COMB",
        tagline = "One route, up and down the whole board six times.",
        laneWaypoints = arrayOf(
            arrayOf(w(spawn, 70f), w(110f, 70f), w(110f, 690f), w(320f, 690f), w(320f, 70f),
                w(530f, 70f), w(530f, 690f), w(740f, 690f), w(740f, 70f),
                w(950f, 70f), w(950f, 690f), w(1160f, 690f), w(1160f, core), w(rack, core))
        ),
        candidateRows = floatArrayOf(150f, 260f, 370f, 480f, 590f),
        extraNodes = listOf(215f, 425f, 635f, 845f, 1055f).flatMap { x ->
            listOf(150f, 260f, 370f, 480f, 590f).map { y -> w(x, y) }
        }
    ))

    /** D — THREE FRONTS: attacks from the left, the top and the bottom edges. */
    @Test
    fun `D three fronts`() = render("D-three-fronts", GameMap(
        id = "m4_fronts", displayName = "D · THREE FRONTS",
        tagline = "Attacks from three edges. They only meet in front of the rack.",
        laneWaypoints = arrayOf(
            arrayOf(w(spawn, core), w(300f, core), w(300f, 560f), w(700f, 560f), w(700f, core),
                w(rack, core)),
            arrayOf(w(420f, -60f), w(420f, 170f), w(1000f, 170f), w(1000f, core), w(rack, core)),
            arrayOf(w(900f, 820f), w(900f, 660f), w(1180f, 660f), w(1180f, core), w(rack, core))
        ),
        candidateRows = floatArrayOf(60f, 270f, 470f, 660f, 730f)
    ))

    /** The shipped level with its two bosses walking it. */
    @Test
    fun `shipped with its bosses`() = render("shipped-bosses", com.cyopstd.game.core.Maps.DUCK_USB) { engine ->
        for ((i, variant) in listOf(
            com.cyopstd.game.model.BossVariant.SYN_STORM,
            com.cyopstd.game.model.BossVariant.GRADIENT
        ).withIndex()) {
            engine.enemySystem().spawn(
                com.cyopstd.game.engine.SpawnOrder(
                    0f, com.cyopstd.game.model.EnemyType.BOSS, i * 2,
                    elite = false, boss = true, bossVariant = variant
                ), 40
            )
            val boss = engine.enemies.items.last { it.active }
            boss.progress = 520f + i * 200f
            boss.baseSpeed = 0f
        }
    }

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

        val out = File("build/map4").apply { mkdirs() }
        val f = File(out, "$name.png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(f.length() > 2_000)
        println("M4 $name nodes=${map.nodes.size} lengths=${map.laneLength.map { it.toInt() }}")
    }

}
