package com.cyopstd.game

import androidx.compose.ui.geometry.Offset
import com.cyopstd.game.core.Maps
import com.cyopstd.game.core.WorldGeometry
import com.cyopstd.game.ui.game.WorldTransform
import kotlin.math.min
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The battlefield half of the screen-size question.
 *
 * The menus are Compose and are checked by rendering them (`ScreenSizeTest`).
 * The battlefield is not: it is one native canvas pass drawing a fixed
 * 1600x760 world through [WorldTransform], so "does it fit" is arithmetic and
 * can be answered exactly, for every viewport, without rendering anything.
 *
 * That makes this the stronger of the two tests. It does not sample five
 * device sizes and hope; it sweeps aspect ratios from a square to a slot and
 * asserts the invariants hold at all of them.
 */
class WorldFitTest {

    /**
     * Viewports to sweep, in pixels.
     *
     * Deliberately past both ends of what any real phone reports: if the
     * arithmetic only holds for plausible sizes, it holds by luck.
     */
    private val viewports: List<Pair<Float, Float>> = buildList {
        for (width in listOf(320f, 568f, 640f, 800f, 900f, 1000f, 1280f, 1920f, 2400f, 3840f)) {
            for (aspect in listOf(1.0f, 1.33f, 1.6f, 1.78f, 2.0f, 2.16f, 2.4f, 3.0f)) {
                add(width to width / aspect)
            }
        }
    }

    @Test
    fun `the whole world lands inside the viewport, at every shape of screen`() {
        for ((viewWidth, viewHeight) in viewports) {
            val transform = WorldTransform(viewWidth, viewHeight)
            val left = transform.toScreenX(0f)
            val right = transform.toScreenX(WorldGeometry.WIDTH)
            val top = transform.toScreenY(0f)
            val bottom = transform.toScreenY(WorldGeometry.HEIGHT)

            val where = "${viewWidth.toInt()}x${viewHeight.toInt()}"
            assertTrue("world runs off the left at $where ($left)", left >= -0.01f)
            assertTrue("world runs off the top at $where ($top)", top >= -0.01f)
            assertTrue(
                "world runs off the right at $where ($right of $viewWidth)",
                right <= viewWidth + 0.01f
            )
            assertTrue(
                "world runs off the bottom at $where ($bottom of $viewHeight)",
                bottom <= viewHeight + 0.01f
            )
        }
    }

    @Test
    fun `it letterboxes rather than stretching, and centres the bars`() {
        for ((viewWidth, viewHeight) in viewports) {
            val transform = WorldTransform(viewWidth, viewHeight)
            val where = "${viewWidth.toInt()}x${viewHeight.toInt()}"

            // Aspect preserved: the drawn world has the world's own ratio, so
            // nothing is squashed. A single `scale` guarantees this by
            // construction -- this asserts the construction, because the
            // cheapest way to "fix" a clipped board is a second axis scale.
            val drawnWidth = transform.toScreenX(WorldGeometry.WIDTH) - transform.toScreenX(0f)
            val drawnHeight = transform.toScreenY(WorldGeometry.HEIGHT) - transform.toScreenY(0f)
            assertEquals(
                "aspect ratio is not preserved at $where",
                WorldGeometry.WIDTH / WorldGeometry.HEIGHT,
                drawnWidth / drawnHeight,
                0.001f
            )

            // Bars on one axis only, and equal on both sides of it. Slack on
            // *both* axes would mean the board is smaller than it needs to be.
            val slackX = viewWidth - drawnWidth
            val slackY = viewHeight - drawnHeight
            assertTrue(
                "the board is undersized at $where: ${slackX.toInt()}px spare " +
                    "across and ${slackY.toInt()}px down",
                slackX < 0.5f || slackY < 0.5f
            )
            assertEquals("left and right bars differ at $where", slackX / 2f, transform.offsetX, 0.01f)
            assertEquals("top and bottom bars differ at $where", slackY / 2f, transform.offsetY, 0.01f)
        }
    }

    @Test
    fun `a tap maps back to the point it was drawn from`() {
        // Every placement in the game is a screen tap turned into a world
        // point. If this drifts, agents land next to the node the player
        // aimed at, and only on the screen sizes where it drifts.
        val probes = listOf(
            0f to 0f,
            WorldGeometry.WIDTH to WorldGeometry.HEIGHT,
            WorldGeometry.SERVER_X to WorldGeometry.CORE_Y,
            800f to 380f
        )
        for ((viewWidth, viewHeight) in viewports) {
            val transform = WorldTransform(viewWidth, viewHeight)
            for ((worldX, worldY) in probes) {
                val back = transform.toWorld(
                    Offset(transform.toScreenX(worldX), transform.toScreenY(worldY))
                )
                val where = "${viewWidth.toInt()}x${viewHeight.toInt()}"
                assertEquals("x drifted at $where", worldX, back.x, 0.05f)
                assertEquals("y drifted at $where", worldY, back.y, 0.05f)
            }
        }
    }

    @Test
    fun `a zero-sized viewport does not divide by zero`() {
        // It happens for one frame on rotation, and a NaN here would be drawn
        // as an empty board rather than crash, which is worse to diagnose.
        for (transform in listOf(WorldTransform(0f, 0f), WorldTransform(600f, 0f), WorldTransform(0f, 400f))) {
            assertTrue("scale is not a number", !transform.scale.isNaN())
            val back = transform.toWorld(Offset(10f, 10f))
            assertTrue("tap mapping is not a number", !back.x.isNaN() && !back.y.isNaN())
        }
    }

    /**
     * How big a deployment node actually is under a fingertip.
     *
     * This is where "fits on the screen" stops being the same as "usable on
     * the screen", and it is what this whole file was written to find. The
     * perimeter map generated sixteen pairs of nodes exactly 26 world units
     * apart — two circles of radius 26 drawn almost entirely on top of each
     * other. Nothing was clipped, nothing overflowed, every Compose check
     * passed, and on a 568x320dp phone those two nodes sat **9dp** apart,
     * which is a third of a fingertip.
     *
     * The rule now lives in the derivation, so this measures the outcome at
     * the smallest screen the game supports and fails if it regresses.
     */
    @Test
    fun `deployment nodes stay aimable on the smallest supported phone`() {
        // The smallest landscape window the game supports, at mdpi, so one
        // pixel here is one dp.
        val transform = WorldTransform(SMALLEST_WIDTH_DP, SMALLEST_HEIGHT_DP)

        for (map in Maps.all) {
            var closest = Float.MAX_VALUE
            var closestPair = ""
            for (i in map.nodes.indices) {
                for (j in i + 1 until map.nodes.size) {
                    val a = map.nodes[i]
                    val b = map.nodes[j]
                    val d = sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y))
                    if (d < closest) {
                        closest = d
                        closestPair = "#${a.id}(${a.x.toInt()},${a.y.toInt()}) and " +
                            "#${b.id}(${b.x.toInt()},${b.y.toInt()})"
                    }
                }
            }
            assertTrue("${map.id} has no nodes to measure", map.nodes.size > 4)

            // No two nodes may be drawn on top of each other, at any size.
            assertTrue(
                "${map.id}: $closestPair are ${closest.toInt()} units apart, " +
                    "closer than the ${WorldGeometry.MIN_NODE_SPACING.toInt()} " +
                    "units a node needs",
                closest >= WorldGeometry.MIN_NODE_SPACING - 0.01f
            )

            // And on the smallest phone they must still be far enough apart
            // to aim between.
            val spacingDp = closest * transform.scale
            assertTrue(
                "${map.id}: $closestPair are only ${spacingDp.toInt()}dp apart on a " +
                    "${SMALLEST_WIDTH_DP.toInt()}x${SMALLEST_HEIGHT_DP.toInt()}dp screen",
                spacingDp >= 18f
            )
        }
    }

    /**
     * There is always somewhere to tap that means "nothing".
     *
     * Enlarging the tap radius looks like the obvious fix for small-screen
     * aiming and is not one: nearest-node already wins every tap inside the
     * radius, so a bigger radius buys nothing except eating the empty-space
     * tap that closes the agent panel. The route corridors are the guaranteed
     * empty space — no node may sit within `NODE_CLEARANCE` of one — and this
     * fails if the radius ever grows enough to cover them.
     */
    @Test
    fun `tapping the route itself still clears the selection`() {
        val tapRadius = WorldGeometry.NODE_RADIUS * TAP_RADIUS_MULTIPLIER
        val step = FloatArray(3)
        for (map in Maps.all) {
            for (lane in 0 until map.laneCount) {
                var along = 0f
                while (along <= map.laneLength[lane]) {
                    map.positionAt(lane, along, step)
                    for (node in map.nodes) {
                        val d = sqrt(
                            (node.x - step[0]) * (node.x - step[0]) +
                                (node.y - step[1]) * (node.y - step[1])
                        )
                        assertTrue(
                            "${map.id}: a tap at (${step[0].toInt()},${step[1].toInt()}) on " +
                                "route $lane lands inside node #${node.id}, so the route " +
                                "can no longer be tapped to deselect",
                            d > tapRadius
                        )
                    }
                    along += 20f
                }
            }
        }
    }

    private companion object {
        const val SMALLEST_WIDTH_DP = 568f
        const val SMALLEST_HEIGHT_DP = 320f

        /** Mirrors `GameViewModel.TAP_RADIUS_MULTIPLIER`, which is private. */
        const val TAP_RADIUS_MULTIPLIER = 2.0f
    }
}
