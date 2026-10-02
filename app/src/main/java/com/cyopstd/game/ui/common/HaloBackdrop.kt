package com.cyopstd.game.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlin.math.hypot

/**
 * Circles radiating from the middle of the screen, slowly turning and slowly
 * moving through the whole colour spectrum (owner, 2026-10-01). It sits
 * behind the main menu, the pause menu, the store and every other menu
 * screen, under the drifting ASCII.
 *
 * Each ring is a broken circle (arcs with gaps), so the slow rotation can be
 * seen; rings are born at the centre, grow outward and fade as they go, so
 * the screen behind the text is never bright enough to fight it. With
 * background animation off (or battery saver on) it is drawn once, still.
 */
@Composable
fun HaloBackdrop(
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(enabled) {
        if (!enabled) return@LaunchedEffect
        // Decoration is throttled, as AsciiBackdrop is: the clock only moves
        // about 30 times a second.
        var lastEmitted = 0f
        while (true) {
            withFrameNanos { nanos ->
                val now = (nanos / 1_000_000_000.0).toFloat()
                if (now - lastEmitted >= FRAME_SECONDS) {
                    lastEmitted = now
                    time = now
                }
            }
        }
    }

    Canvas(modifier.testTag("halo-backdrop")) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxRadius = hypot(size.width / 2f, size.height / 2f)
        val spacing = maxRadius / RINGS
        // How far the rings have travelled outward, in ring spacings.
        val travel = (time * OUTWARD_SPEED) % 1f
        val stroke = Stroke(width = 2.dp.toPx())
        val turn = time * ROTATION_DEGREES_PER_SECOND

        for (ring in 0 until RINGS) {
            val radius = (ring + travel) * spacing
            if (radius < spacing * 0.3f) continue
            val fraction = radius / maxRadius
            // Fades in near the centre and out towards the edge.
            val alpha = (MAX_ALPHA * (1f - fraction) * (fraction * 4f).coerceAtMost(1f)).coerceIn(0f, 1f)
            // The spectrum turns slowly over time, and each ring sits a step on.
            val hue = ((time * HUE_DEGREES_PER_SECOND) + ring * HUE_STEP) % 360f
            val color = Color.hsv(hue, 0.75f, 1f).copy(alpha = alpha)
            val direction = if (ring % 2 == 0) 1f else -1f
            rotate(turn * direction + ring * 17f, center) {
                val arc = 360f / SEGMENTS
                for (segment in 0 until SEGMENTS) {
                    drawArc(
                        color = color,
                        startAngle = segment * arc,
                        sweepAngle = arc * 0.72f,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2f, radius * 2f),
                        style = stroke
                    )
                }
            }
        }
    }
}

private const val RINGS = 9
private const val SEGMENTS = 6
private const val MAX_ALPHA = 0.28f
private const val OUTWARD_SPEED = 0.08f
private const val ROTATION_DEGREES_PER_SECOND = 4f
private const val HUE_DEGREES_PER_SECOND = 6f
private const val HUE_STEP = 24f
private const val FRAME_SECONDS = 1f / 30f
