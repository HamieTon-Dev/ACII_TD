package com.cyopstd.game

import androidx.compose.ui.geometry.Offset
import com.cyopstd.game.core.Maps
import com.cyopstd.game.core.WorldGeometry
import com.cyopstd.game.ui.game.BattlefieldViewport
import com.cyopstd.game.ui.game.WorldTransform
import kotlin.math.abs
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pinch-to-zoom: the arithmetic underneath it.
 *
 * The brief that asked for this listed a long tail of ways a zoomable board
 * goes wrong — phantom placements, coordinate drift, units detaching from their
 * nodes, the board lost off-screen. Almost all of them come from the same root
 * cause: drawing and hit-testing done by two different pieces of code that are
 * supposed to agree.
 *
 * This game does not have that problem to solve, because it never had two
 * pieces of code. Every coordinate goes through [WorldTransform] and always
 * has, so putting the zoom there means "where it is drawn" and "what a tap
 * hits" are not two answers that must be kept in step — they are one
 * expression evaluated twice. These tests are what proves that claim rather
 * than merely asserting it.
 */
class ZoomTest {

    /** The smallest window the game supports, at mdpi: one pixel is one dp. */
    private val smallPhone = 568f to 320f

    private fun transform(
        size: Pair<Float, Float> = smallPhone,
        zoom: Float = 1f,
        panX: Float = 0f,
        panY: Float = 0f
    ) = WorldTransform(size.first, size.second, zoom, panX, panY)

    // ------------------------------------------------------------ the bounds

    @Test
    fun `zoom cannot go below the fitted board`() {
        // At minimum zoom the whole battlefield must still be understandable,
        // which is satisfied exactly by making minimum zoom *be* the fit.
        val out = transform(zoom = 0.2f)
        assertEquals(
            "zooming out past the fit would shrink the board inside its own " +
                "letterbox for no benefit",
            transform(zoom = 1f).scale,
            out.scale,
            0.0001f
        )
    }

    @Test
    fun `zoom cannot exceed the maximum`() {
        val far = transform(zoom = 99f)
        assertEquals(transform(zoom = WorldTransform.MAX_ZOOM).scale, far.scale, 0.0001f)
    }

    @Test
    fun `the viewport clamps zoom on both sides`() {
        val viewport = BattlefieldViewport()
        repeat(40) { viewport.pinch(transform(zoom = viewport.zoom), Offset(284f, 160f), 1.3f) }
        assertTrue("zoom ran past the maximum", viewport.zoom <= WorldTransform.MAX_ZOOM + 0.001f)

        repeat(80) { viewport.pinch(transform(zoom = viewport.zoom), Offset(284f, 160f), 0.8f) }
        assertEquals("zoom ran below the fit", 1f, viewport.zoom, 0.001f)
    }

    // ------------------------------------------------- the reason it exists

    @Test
    fun `maximum zoom makes adjacent nodes a real touch target`() {
        // This is the whole point of the feature, and the number it has to
        // beat is Material's 48dp. Without zoom, neighbouring nodes on the
        // smallest supported phone are about 20dp apart, which no tap radius
        // can separate -- a wider radius only eats the empty-space tap that
        // clears a selection.
        var closest = Float.MAX_VALUE
        val nodes = Maps.PERIMETER.nodes
        for (i in nodes.indices) {
            for (j in i + 1 until nodes.size) {
                val dx = nodes[i].x - nodes[j].x
                val dy = nodes[i].y - nodes[j].y
                closest = minOf(closest, sqrt(dx * dx + dy * dy))
            }
        }

        val fitted = transform(zoom = 1f)
        val zoomed = transform(zoom = WorldTransform.MAX_ZOOM)

        val spacingFitted = closest * fitted.scale
        val spacingZoomed = closest * zoomed.scale

        assertTrue(
            "the problem should still be visible at minimum zoom " +
                "(${spacingFitted.toInt()}dp)",
            spacingFitted < 48f
        )
        assertTrue(
            "at maximum zoom adjacent nodes are only ${spacingZoomed.toInt()}dp " +
                "apart on a 568x320dp screen, which does not solve the problem " +
                "this feature exists for",
            spacingZoomed >= 48f
        )
    }

    // ------------------------------------------------------- the coordinates

    @Test
    fun `a tap maps back to the point it was drawn from, at every zoom`() {
        // Coordinate drift, asserted away. If this holds, an agent cannot land
        // next to the node that was tapped, and a unit cannot detach from the
        // node it is standing on, because both are the same conversion.
        val probes = listOf(
            0f to 0f,
            WorldGeometry.WIDTH to WorldGeometry.HEIGHT,
            WorldGeometry.SERVER_X to WorldGeometry.CORE_Y,
            800f to 380f
        )
        for (size in listOf(568f to 320f, 640f to 360f, 1000f to 460f, 1280f to 800f)) {
            for (zoom in listOf(1f, 1.5f, 2f, 2.7f, WorldTransform.MAX_ZOOM)) {
                for (pan in listOf(0f to 0f, 120f to -80f, -400f to 300f)) {
                    val t = transform(size, zoom, pan.first, pan.second)
                    for ((wx, wy) in probes) {
                        val back = t.toWorld(Offset(t.toScreenX(wx), t.toScreenY(wy)))
                        val where = "${size.first.toInt()}x${size.second.toInt()} @${zoom}x"
                        assertEquals("x drifted at $where", wx, back.x, 0.05f)
                        assertEquals("y drifted at $where", wy, back.y, 0.05f)
                    }
                }
            }
        }
    }

    @Test
    fun `every node is reachable by a tap at some pan, when zoomed in`() {
        // "Stable coordinates regardless of zoom level" is not much use if
        // zooming in puts a node somewhere no pan can bring it. Each node must
        // land inside the viewport for some allowed pan.
        val size = smallPhone
        val zoom = WorldTransform.MAX_ZOOM
        for (node in Maps.PERIMETER.nodes) {
            // Pan far enough to try to centre this node, then let the clamp
            // decide what is actually allowed.
            val centring = WorldTransform(size.first, size.second, zoom)
            val wanted = centring.panKeeping(
                Offset(node.x, node.y),
                Offset(size.first / 2f, size.second / 2f)
            )
            val t = WorldTransform(size.first, size.second, zoom, wanted.x, wanted.y)
            val sx = t.toScreenX(node.x)
            val sy = t.toScreenY(node.y)
            assertTrue(
                "node #${node.id} at (${node.x.toInt()},${node.y.toInt()}) cannot be " +
                    "brought on screen at maximum zoom: lands at " +
                    "(${sx.toInt()},${sy.toInt()}) of ${size.first.toInt()}x${size.second.toInt()}",
                sx >= -1f && sx <= size.first + 1f && sy >= -1f && sy <= size.second + 1f
            )
        }
    }

    // -------------------------------------------------------------- the pan

    @Test
    fun `the board cannot be dragged off screen`() {
        // Clamped by geometry rather than by a rule: the slack is how much the
        // drawn board overflows the viewport, so there is nothing to pan to
        // when it does not overflow.
        for (zoom in listOf(1f, 1.5f, WorldTransform.MAX_ZOOM)) {
            for (push in listOf(-100_000f, -1_000f, 1_000f, 100_000f)) {
                val t = transform(zoom = zoom, panX = push, panY = push)
                val left = t.toScreenX(0f)
                val right = t.toScreenX(WorldGeometry.WIDTH)
                val top = t.toScreenY(0f)
                val bottom = t.toScreenY(WorldGeometry.HEIGHT)

                // Some part of the board is always on screen: the drawn board
                // either covers the viewport or is centred inside it.
                assertTrue(
                    "the board was lost off the side at ${zoom}x (pan $push)",
                    right > 0f && left < smallPhone.first
                )
                assertTrue(
                    "the board was lost off the bottom at ${zoom}x (pan $push)",
                    bottom > 0f && top < smallPhone.second
                )
            }
        }
    }

    @Test
    fun `there is nothing to pan at the fitted zoom`() {
        val t = transform(zoom = 1f, panX = 5_000f, panY = 5_000f)
        val centred = transform(zoom = 1f)
        assertEquals("a fitted board must stay centred", centred.offsetX, t.offsetX, 0.01f)
        assertEquals(centred.offsetY, t.offsetY, 0.01f)
    }

    @Test
    fun `panning against the edge does not bank slack that has to be unwound`() {
        // Without settle(), dragging hard into an edge stores a pan far past
        // what the clamp allows, and the next drag the other way does nothing
        // for a while. That reads as a stuck board.
        val viewport = BattlefieldViewport()
        viewport.pinch(transform(), Offset(284f, 160f), 2f)
        repeat(50) { viewport.pan(500f, 0f) }
        viewport.settle(transform(zoom = viewport.zoom, panX = viewport.panX, panY = viewport.panY))

        val before = viewport.panX
        viewport.pan(-30f, 0f)
        assertTrue(
            "the board did not respond to a drag away from the edge",
            viewport.panX < before - 1f
        )
    }

    // ------------------------------------------------------- pinch anchoring

    @Test
    fun `a pinch keeps the point between the fingers between the fingers`() {
        val viewport = BattlefieldViewport()
        val centroid = Offset(200f, 120f)
        val before = transform(zoom = viewport.zoom)
        val anchorWorld = before.toWorld(centroid)

        viewport.pinch(before, centroid, 1.8f)

        val after = transform(zoom = viewport.zoom, panX = viewport.panX, panY = viewport.panY)
        val landed = Offset(after.toScreenX(anchorWorld.x), after.toScreenY(anchorWorld.y))

        // Exact where the clamp allows it; the clamp is the only thing that may
        // move it, and only at an edge.
        assertTrue(
            "the pinch anchor slid to $landed from $centroid",
            abs(landed.x - centroid.x) < 2f && abs(landed.y - centroid.y) < 2f
        )
    }

    @Test
    fun `pinching most of the way out snaps exactly to the fitted board`() {
        val viewport = BattlefieldViewport()
        viewport.pinch(transform(), Offset(284f, 160f), 2.5f)
        assertTrue(viewport.isZoomed)

        // Land just above the fit rather than exactly on it.
        viewport.pinch(
            transform(zoom = viewport.zoom, panX = viewport.panX, panY = viewport.panY),
            Offset(284f, 160f),
            1f / 2.45f
        )

        assertEquals("zoom should snap to the fit", 1f, viewport.zoom, 0.0001f)
        assertEquals("...and drop the pan with it", 0f, viewport.panX, 0.0001f)
        assertEquals(0f, viewport.panY, 0.0001f)
        assertTrue(!viewport.isZoomed)
    }

    // ------------------------------------------------------ the HUD does not

    @Test
    fun `zoom changes the board and nothing about the viewport it is drawn in`() {
        // The HUD and the agent menu are separate composables outside the
        // battlefield's box, so the structural guarantee is that the transform
        // has no way to reach them. What can be asserted here is the other
        // half: the viewport's own size is untouched by zoom, so the box the
        // board is clipped to never grows into the strips above and below it.
        val fitted = transform(zoom = 1f)
        val zoomed = transform(zoom = WorldTransform.MAX_ZOOM)
        assertEquals(fitted.viewWidth, zoomed.viewWidth, 0.0001f)
        assertEquals(fitted.viewHeight, zoomed.viewHeight, 0.0001f)
        assertNotEquals(fitted.scale, zoomed.scale)
    }

    @Test
    fun `a zero-sized viewport still does not divide by zero`() {
        for (t in listOf(
            WorldTransform(0f, 0f, 2f),
            WorldTransform(600f, 0f, 3f),
            WorldTransform(0f, 400f, 1.5f)
        )) {
            assertTrue(!t.scale.isNaN())
            val back = t.toWorld(Offset(10f, 10f))
            assertTrue(!back.x.isNaN() && !back.y.isNaN())
            assertTrue(!t.panX.isNaN() && !t.panY.isNaN())
        }
    }

    @Test
    fun `a new viewport is fitted and centred`() {
        val viewport = BattlefieldViewport()
        assertEquals(1f, viewport.zoom, 0.0001f)
        assertEquals(0f, viewport.panX, 0.0001f)
        assertEquals(0f, viewport.panY, 0.0001f)
        assertTrue(!viewport.isZoomed)
    }
}
