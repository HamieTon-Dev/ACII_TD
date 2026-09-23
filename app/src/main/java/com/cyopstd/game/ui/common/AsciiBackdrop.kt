package com.cyopstd.game.ui.common

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.staticCompositionLocalOf
import com.cyopstd.game.ui.theme.LivingBackground
import com.cyopstd.game.ui.theme.Palette
import kotlin.math.sin
import kotlin.random.Random

/**
 * The living background the menus should wear.
 *
 * A CompositionLocal rather than a parameter on every screen: the backdrop
 * sits behind roughly a dozen composables, all of which would otherwise have
 * to accept and forward a value none of them care about. Set once in
 * `CyOpsApp`, read once here.
 */
val LocalLivingBackground = staticCompositionLocalOf { LivingBackground.NONE }

/**
 * The slow drift of 0s, 1s and punctuation behind the menus.
 *
 * This is deliberately cheap: a fixed set of glyph columns is generated once and
 * then only translated, so no strings are built and no objects are allocated per
 * frame. When the player turns background animation off, or battery saver on,
 * the whole thing stops redrawing rather than merely drawing less.
 */
@Composable
fun AsciiBackdrop(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    density: Int = 46,
    tint: androidx.compose.ui.graphics.Color? = null,
    /** Seconds between backdrop updates. Decoration is throttled on purpose. */
    frameIntervalSeconds: Float = 0.05f
) {
    val density0 = LocalDensity.current
    var time by remember { mutableFloatStateOf(0f) }

    // The player's living background, if they own one. It reaches the menus by
    // changing this drift's colour and weight rather than by porting the
    // battlefield's Canvas effects: the menus are Compose, the effects are
    // native-Canvas, and one recognisable palette carries the theme without
    // maintaining two implementations of every backdrop.
    val living = LocalLivingBackground.current
    val resolvedTint = tint ?: living.laneTint ?: Palette.GridLine
    // A theme earns a denser, livelier drift; the free backdrop stays as it was.
    val columnCount = if (living == LivingBackground.NONE) density else (density * 1.35f).toInt()
    val liveliness = if (living == LivingBackground.NONE) 1f else 1f + living.speed * 1.6f

    // Keyed on liveliness as well as the count: every owned theme yields the
    // same column count, so keying on the count alone kept the first theme's
    // speeds when the player switched to another one.
    val columns = remember(columnCount, liveliness) {
        val random = Random(0xBA5710D)
        Array(columnCount) {
            BackdropColumn(
                xFraction = random.nextFloat(),
                speed = (8f + random.nextFloat() * 26f) * liveliness,
                offset = random.nextFloat() * 1000f,
                glyphs = CharArray(GLYPHS_PER_COLUMN) {
                    BACKDROP_CHARS[random.nextInt(BACKDROP_CHARS.size)]
                },
                alpha = 0.22f + random.nextFloat() * 0.5f
            )
        }
    }

    val paint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.MONOSPACE
            textAlign = Paint.Align.CENTER
        }
    }

    LaunchedEffect(enabled, frameIntervalSeconds) {
        if (!enabled) return@LaunchedEffect
        // Decoration does not deserve 60 FPS. Advancing the clock only every
        // ~50ms means the Canvas invalidates roughly 20 times a second instead
        // of 60, which matters because this composable is alive behind every
        // menu in the game.
        var lastEmitted = 0f
        while (true) {
            androidx.compose.runtime.withFrameNanos { nanos ->
                val now = (nanos / 1_000_000_000.0).toFloat()
                if (now - lastEmitted >= frameIntervalSeconds) {
                    lastEmitted = now
                    time = now
                }
            }
        }
    }

    Canvas(modifier = modifier) {
        if (!enabled) return@Canvas
        val w = size.width
        val h = size.height
        paint.textSize = with(density0) { 11.dp.toPx() }
        val lineHeight = paint.textSize * 1.5f
        val baseArgb = resolvedTint.toArgb()

        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            for (column in columns) {
                val x = column.xFraction * w
                val drift = (time * column.speed + column.offset) % (h + lineHeight * GLYPHS_PER_COLUMN)
                for (i in 0 until GLYPHS_PER_COLUMN) {
                    val y = drift - i * lineHeight
                    if (y < -lineHeight || y > h + lineHeight) continue
                    val fade = column.alpha * (1f - i.toFloat() / GLYPHS_PER_COLUMN)
                    val flicker = 0.75f + 0.25f * sin(time * 2.4f + column.offset + i)
                    paint.color = baseArgb
                    paint.alpha = (fade * flicker * 255f).toInt().coerceIn(0, 255)
                    native.drawText(column.glyphs, i, 1, x, y, paint)
                }
            }
        }
    }
}

private class BackdropColumn(
    val xFraction: Float,
    val speed: Float,
    val offset: Float,
    val glyphs: CharArray,
    val alpha: Float
)

private const val GLYPHS_PER_COLUMN = 9
private val BACKDROP_CHARS = charArrayOf(
    '0', '1', '0', '1', '.', ':', '/', '\\', '|', '-', '+', '>', '<', '#', '*'
)
