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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The owner's pick (option F, 2026-10-02): two spectrum spiral arms winding
 * out from the middle of the screen over faint radiating rings, slowly
 * turning and slowly moving through the whole colour spectrum. It sits
 * behind the main menu, the pause menu, the store and every other menu
 * screen, under the drifting ASCII. Part of the game, not a store item.
 *
 * Kept faint, so the screen behind the text is never bright enough to fight
 * it. With background animation off (or battery saver on) it is drawn once,
 * still. [stillTime] draws one fixed moment, for previews.
 */
@Composable
fun HaloBackdrop(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    stillTime: Float? = null
) {
    var time by remember { mutableFloatStateOf(stillTime ?: 0f) }
    LaunchedEffect(enabled, stillTime) {
        if (!enabled || stillTime != null) return@LaunchedEffect
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
        spiral(stillTime ?: time)
    }
}

// ----------------------------------------------------------------- shared

private fun DrawScope.center(): Offset = Offset(size.width / 2f, size.height / 2f)
private fun DrawScope.maxRadius(): Float = hypot(size.width / 2f, size.height / 2f)

/** Faint near the centre and the edge, strongest between. */
private fun ringAlpha(fraction: Float): Float =
    (MAX_ALPHA * (1f - fraction) * (fraction * 4f).coerceAtMost(1f)).coerceIn(0f, 1f)

private fun hue(t: Float, offset: Float = 0f): Float =
    ((t * HUE_DEGREES_PER_SECOND + offset) % 360f + 360f) % 360f

private fun spectrum(t: Float, offset: Float, alpha: Float): Color =
    Color.hsv(hue(t, offset), 0.75f, 1f).copy(alpha = alpha.coerceIn(0f, 1f))

/** Each ring's radius as the set travels outward, ring by ring. */
private inline fun DrawScope.forEachRing(t: Float, block: (ring: Int, radius: Float, fraction: Float) -> Unit) {
    val maxRadius = maxRadius()
    val spacing = maxRadius / RINGS
    val travel = (t * OUTWARD_SPEED) % 1f
    for (ring in 0 until RINGS) {
        val radius = (ring + travel) * spacing
        if (radius < spacing * 0.3f) continue
        block(ring, radius, radius / maxRadius)
    }
}

// ----------------------------------------------------------------- styles

/** Two spectrum spiral arms winding outward, turning slowly, over faint rings. */
private fun DrawScope.spiral(t: Float) {
    val c = center()
    val maxRadius = maxRadius()
    val width = 2.dp.toPx()
    for (arm in 0 until 2) {
        var previous: Offset? = null
        val steps = 220
        for (i in 0..steps) {
            val f = i / steps.toFloat()
            val radius = f * maxRadius
            val deg = f * 900f + arm * 180f + t * ROTATION_DEGREES_PER_SECOND * 2f
            val a = deg * (PI / 180.0)
            val p = Offset(c.x + radius * cos(a).toFloat(), c.y + radius * sin(a).toFloat())
            previous?.let { drawLine(spectrum(t, f * 360f, ringAlpha(f) * 1.2f), it, p, width) }
            previous = p
        }
    }
    val thin = Stroke(width = 1.dp.toPx())
    forEachRing(t) { ring, radius, fraction ->
        drawCircle(spectrum(t, ring * HUE_STEP, ringAlpha(fraction) * 0.5f), radius, c, style = thin)
    }
}

private const val RINGS = 9
private const val MAX_ALPHA = 0.28f
private const val OUTWARD_SPEED = 0.08f
private const val ROTATION_DEGREES_PER_SECOND = 4f
private const val HUE_DEGREES_PER_SECOND = 6f
private const val HUE_STEP = 24f
private const val FRAME_SECONDS = 1f / 30f
