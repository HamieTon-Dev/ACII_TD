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
 * Layout candidates for map 3, NEURAL-MESH (backlog R2), for the owner to
 * pick from. Not an assertion suite. Each candidate is a real [GameMap] drawn
 * by the real battlefield renderer, so the nodes shown are the ones the game
 * would actually offer.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NeuralMeshPreview {

    private val spawn = WorldGeometry.SPAWN_X
    private val rack = WorldGeometry.SERVER_X
    private val core = WorldGeometry.CORE_Y

    private fun rows(vararg y: Float) = y

    /** 1 — SYNAPSE: two routes that cross each other twice, then meet at the rack. */
    @Test
    fun `1 synapse`() = render("1-synapse", GameMap(
        id = "nm_synapse", displayName = "1 · SYNAPSE",
        tagline = "Two routes cross twice. Build on the crossings to hit both.",
        laneWaypoints = arrayOf(
            arrayOf(
                Waypoint(spawn, 110f), Waypoint(250f, 110f), Waypoint(560f, 650f),
                Waypoint(820f, 650f), Waypoint(1110f, 110f), Waypoint(1190f, 110f),
                Waypoint(1190f, core), Waypoint(rack, core)
            ),
            arrayOf(
                Waypoint(spawn, 650f), Waypoint(250f, 650f), Waypoint(560f, 110f),
                Waypoint(820f, 110f), Waypoint(1110f, 650f), Waypoint(1190f, 650f),
                Waypoint(1190f, core), Waypoint(rack, core)
            )
        ),
        candidateRows = rows(40f, 200f, 300f, 380f, 460f, 560f, 720f)
    ))

    /** 2 — LAYERS: three routes step through the network's layers; the middle one is fast. */
    @Test
    fun `2 layers`() = render("2-layers", GameMap(
        id = "nm_layers", displayName = "2 · LAYERS",
        tagline = "Three routes. The outer two step through every layer; the middle one is fast.",
        laneWaypoints = arrayOf(
            arrayOf(
                Waypoint(spawn, 96f), Waypoint(300f, 96f), Waypoint(300f, 250f),
                Waypoint(640f, 250f), Waypoint(640f, 96f), Waypoint(980f, 96f),
                Waypoint(980f, 250f), Waypoint(1170f, 250f), Waypoint(1170f, core), Waypoint(rack, core)
            ),
            arrayOf(Waypoint(spawn, core), Waypoint(rack, core)),
            arrayOf(
                Waypoint(spawn, 664f), Waypoint(300f, 664f), Waypoint(300f, 510f),
                Waypoint(640f, 510f), Waypoint(640f, 664f), Waypoint(980f, 664f),
                Waypoint(980f, 510f), Waypoint(1170f, 510f), Waypoint(1170f, core), Waypoint(rack, core)
            )
        ),
        candidateRows = rows(34f, 173f, 315f, 445f, 587f, 726f)
    ))

    /** 3 — ATTENTION: four heads fan in, pair up, then merge before the rack. */
    @Test
    fun `3 attention`() = render("3-attention", GameMap(
        id = "nm_attention", displayName = "3 · ATTENTION",
        tagline = "Four entry points pair up, then merge. Many fronts early, one late.",
        laneWaypoints = arrayOf(
            arrayOf(
                Waypoint(spawn, 60f), Waypoint(360f, 60f), Waypoint(600f, 180f),
                Waypoint(900f, 180f), Waypoint(900f, 60f), Waypoint(1160f, 60f),
                Waypoint(1160f, core), Waypoint(rack, core)
            ),
            arrayOf(
                Waypoint(spawn, 300f), Waypoint(360f, 300f), Waypoint(600f, 180f),
                Waypoint(900f, 180f), Waypoint(900f, 60f), Waypoint(1160f, 60f),
                Waypoint(1160f, core), Waypoint(rack, core)
            ),
            arrayOf(
                Waypoint(spawn, 460f), Waypoint(360f, 460f), Waypoint(600f, 580f),
                Waypoint(900f, 580f), Waypoint(900f, 700f), Waypoint(1160f, 700f),
                Waypoint(1160f, core), Waypoint(rack, core)
            ),
            arrayOf(
                Waypoint(spawn, 700f), Waypoint(360f, 700f), Waypoint(600f, 580f),
                Waypoint(900f, 580f), Waypoint(900f, 700f), Waypoint(1160f, 700f),
                Waypoint(1160f, core), Waypoint(rack, core)
            )
        ),
        candidateRows = rows(120f, 240f, 380f, 520f, 640f)
    ))

    /** 4 — BACKPROP: each route runs out, comes back, and the two merge into a long shared return. */
    @Test
    fun `4 backprop`() = render("4-backprop", GameMap(
        id = "nm_backprop", displayName = "4 · BACKPROP",
        tagline = "Out, back, and a long shared return to the rack.",
        laneWaypoints = arrayOf(
            arrayOf(
                Waypoint(spawn, 96f), Waypoint(1160f, 96f), Waypoint(1160f, 250f),
                Waypoint(160f, 250f), Waypoint(160f, core), Waypoint(rack, core)
            ),
            arrayOf(
                Waypoint(spawn, 664f), Waypoint(1160f, 664f), Waypoint(1160f, 510f),
                Waypoint(160f, 510f), Waypoint(160f, core), Waypoint(rack, core)
            )
        ),
        candidateRows = rows(34f, 173f, 315f, 445f, 587f, 726f)
    ))

    /** The shipped level with its two bosses walking it. */
    @Test
    fun `shipped with its bosses`() = render("shipped-bosses", com.cyopstd.game.core.Maps.NEURAL_MESH) { engine ->
        for ((i, variant) in listOf(
            com.cyopstd.game.model.BossVariant.MODEL_COLLAPSE,
            com.cyopstd.game.model.BossVariant.LICENSE
        ).withIndex()) {
            engine.enemySystem().spawn(
                com.cyopstd.game.engine.SpawnOrder(
                    0f, com.cyopstd.game.model.EnemyType.BOSS, i,
                    elite = false, boss = true, bossVariant = variant
                ), 40
            )
            val boss = engine.enemies.items.last { it.active }
            boss.progress = 700f + i * 300f
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

        val out = File("build/neural-mesh").apply { mkdirs() }
        val f = File(out, "$name.png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(f.length() > 2_000)
        println("NM $name nodes=${map.nodes.size} lengths=${map.laneLength.map { it.toInt() }}")
    }
}
