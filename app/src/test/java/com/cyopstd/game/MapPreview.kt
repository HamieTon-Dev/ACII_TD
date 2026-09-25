package com.cyopstd.game

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.cyopstd.game.core.GameMap
import com.cyopstd.game.core.Maps
import com.cyopstd.game.core.Waypoint
import com.cyopstd.game.core.WorldGeometry
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Draws candidate second-map layouts so a human can choose between them.
 *
 * Not an assertion suite — an exporter, like `IconExport`. It exists because
 * "pick a map layout" is not a question anybody can answer from a list of
 * waypoint coordinates, and because the thing that actually decides whether a
 * layout is any good is **where the deployment nodes land**, which is derived
 * rather than authored. A sketch would show the routes and hide the part that
 * matters.
 *
 * So each candidate is a real [GameMap], built through the real constructor,
 * and the nodes drawn are the ones the game would actually offer.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MapPreview {

    private val out = File("build/maps")

    // Shared band positions, matching the existing map's vocabulary.
    private val top = 118f
    private val upperMid = 250f
    private val centre = WorldGeometry.CORE_Y
    private val lowerMid = 510f
    private val bottom = 642f
    private val spawn = WorldGeometry.SPAWN_X
    private val rack = WorldGeometry.SERVER_X

    private fun rows() = floatArrayOf(
        top - 62f,
        (top + upperMid) / 2f,
        (upperMid + centre) / 2f,
        (centre + lowerMid) / 2f,
        (lowerMid + bottom) / 2f,
        bottom + 62f
    )

    @Test
    fun `A current - one choke`() {
        render("A-one-choke", Maps.HUGGING_FACE)
    }

    /**
     * B — PINCER. Two long outer routes that wrap the board and come at the
     * rack from above and below, plus one short straight centre.
     *
     * The centre is much the fastest, so it is the one that punishes you for
     * ignoring it; the outer two take long enough that a single well-placed
     * cluster can work both of them. Rewards spreading out rather than
     * stacking one choke.
     */
    @Test
    fun `B pincer`() {
        render("B-pincer", GameMap(
            id = "preview_b",
            displayName = "PINCER",
            tagline = "Two long flanks and a fast middle.",
            laneWaypoints = arrayOf(
                arrayOf(
                    Waypoint(spawn, upperMid), Waypoint(200f, upperMid),
                    Waypoint(200f, top), Waypoint(900f, top),
                    Waypoint(900f, upperMid), Waypoint(1150f, upperMid),
                    Waypoint(rack, centre)
                ),
                arrayOf(
                    Waypoint(spawn, centre), Waypoint(1150f, centre), Waypoint(rack, centre)
                ),
                arrayOf(
                    Waypoint(spawn, lowerMid), Waypoint(200f, lowerMid),
                    Waypoint(200f, bottom), Waypoint(900f, bottom),
                    Waypoint(900f, lowerMid), Waypoint(1150f, lowerMid),
                    Waypoint(rack, centre)
                )
            ),
            candidateRows = rows()
        ))
    }

    /**
     * C — SWITCHBACK. Two routes that double back on themselves three times
     * before converging late.
     *
     * The longest total path of the three, so a single agent covers far more
     * route than it would elsewhere — which makes range matter more than rate
     * and favours IDS and ANALYST over FIREWALL walls. The late convergence
     * means the last stretch is shared, so a boss-killer parked near the rack
     * always has work.
     */
    @Test
    fun `C switchback`() {
        render("C-switchback", GameMap(
            id = "preview_c",
            displayName = "SWITCHBACK",
            tagline = "Long doubled-back routes. Range beats rate.",
            laneWaypoints = arrayOf(
                arrayOf(
                    Waypoint(spawn, top), Waypoint(420f, top),
                    Waypoint(420f, upperMid), Waypoint(150f, upperMid),
                    Waypoint(150f, centre), Waypoint(700f, centre),
                    Waypoint(700f, upperMid), Waypoint(1000f, upperMid),
                    Waypoint(1000f, centre), Waypoint(rack, centre)
                ),
                arrayOf(
                    Waypoint(spawn, bottom), Waypoint(420f, bottom),
                    Waypoint(420f, lowerMid), Waypoint(150f, lowerMid),
                    Waypoint(150f, centre), Waypoint(700f, centre),
                    Waypoint(700f, lowerMid), Waypoint(1000f, lowerMid),
                    Waypoint(1000f, centre), Waypoint(rack, centre)
                )
            ),
            candidateRows = rows()
        ))
    }

    // ------------------------------------------------------------ machinery

    private fun render(name: String, map: GameMap) {
        val scale = 0.78f
        val header = 64
        val w = (WorldGeometry.WIDTH * scale).toInt()
        val h = (WorldGeometry.HEIGHT * scale).toInt() + header
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.parseColor("#070B14"))
        // A header band, so the caption never sits on top of the board. The
        // first version drew the text over the top row of nodes, which is a
        // poor way to present something somebody has to read to decide.
        c.save()
        c.translate(0f, header.toFloat())

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Routes, drawn at lane width so the corridor reads as a corridor.
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = WorldGeometry.LANE_HEIGHT * scale
        paint.color = Color.parseColor("#12203A")
        for (lane in 0 until map.laneCount) {
            val pts = map.laneWaypoints[lane]
            for (i in 0 until pts.size - 1) {
                c.drawLine(
                    pts[i].x * scale, pts[i].y * scale,
                    pts[i + 1].x * scale, pts[i + 1].y * scale, paint
                )
            }
        }
        paint.strokeWidth = 2f
        paint.color = Color.parseColor("#00E5FF")
        for (lane in 0 until map.laneCount) {
            val pts = map.laneWaypoints[lane]
            for (i in 0 until pts.size - 1) {
                c.drawLine(
                    pts[i].x * scale, pts[i].y * scale,
                    pts[i + 1].x * scale, pts[i + 1].y * scale, paint
                )
            }
        }

        // The server rack.
        paint.style = Paint.Style.FILL
        paint.color = Color.parseColor("#1E2D47")
        c.drawRect(
            WorldGeometry.SERVER_X * scale, WorldGeometry.SERVER_TOP * scale,
            (WorldGeometry.SERVER_X + WorldGeometry.SERVER_WIDTH) * scale,
            (WorldGeometry.SERVER_TOP + WorldGeometry.SERVER_HEIGHT) * scale, paint
        )

        // Deployment nodes -- the part that actually decides the map.
        paint.color = Color.parseColor("#00FF9C")
        for (node in map.nodes) {
            c.drawCircle(node.x * scale, node.y * scale, WorldGeometry.NODE_RADIUS * scale, paint)
        }

        c.restore()
        paint.color = Color.parseColor("#E6EEFA")
        paint.textSize = 26f
        c.drawText(
            "${map.displayName}   ·   ${map.nodes.size} nodes   ·   " +
                "${map.laneCount} routes   ·   longest ${map.laneLength.max().toInt()}u",
            16f, 28f, paint
        )
        paint.textSize = 17f
        paint.color = Color.parseColor("#93A6C4")
        c.drawText(map.tagline, 16f, 52f, paint)

        out.mkdirs()
        val f = File(out, "$name.png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue("$name.png did not render", f.length() > 2_000)
        println("MAP ${f.absolutePath} nodes=${map.nodes.size} routes=${map.laneCount} " +
            "lengths=${map.laneLength.map { it.toInt() }}")
    }
}
