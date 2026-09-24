package com.cyopstd.game.ui.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyopstd.game.ui.theme.Palette

/**
 * The studio ident, before anything else.
 *
 * A publisher card is a held beat, not a loading screen: it fades up, sits
 * still long enough to be read, and fades away. That is the whole design, and
 * it is why the timing lives in [developerIdentAlpha] as a plain function of
 * elapsed seconds rather than in an animation spec — a held beat that is a
 * frame short reads as a flicker, and a function can be asserted.
 *
 * It is drawn in the same monospace ASCII the rest of the game speaks rather
 * than shipped as an image, so it scales to any screen without an asset and
 * matches everything behind it.
 */
@Composable
fun DeveloperSplashScreen(onFinished: () -> Unit) {
    var elapsed by remember { mutableFloatStateOf(0f) }
    var dismissed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val startedAt = withFrameNanos { it }
        while (elapsed < IDENT_SECONDS) {
            withFrameNanos { nanos -> elapsed = (nanos - startedAt) / 1_000_000_000f }
        }
        if (!dismissed) {
            dismissed = true
            onFinished()
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.Background)
            // Tappable to skip. An ident is for the first launch; by the
            // twentieth it is between the player and the game.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (!dismissed) {
                    dismissed = true
                    onFinished()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Sized to the screen. The banner is BANNER_COLUMNS characters of
        // monospace and wrapping it would turn a logo into wreckage.
        val available = maxWidth.value - SIDE_GUTTER * 2f

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(horizontal = SIDE_GUTTER.dp)
                .alpha(developerIdentAlpha(elapsed))
        ) {
            BlockBanner(
                lines = DEVELOPER_BANNER.lines(),
                columns = BANNER_COLUMNS,
                availableWidth = available,
                color = Palette.Green,
                contentDescription = DEVELOPER_NAME
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = "P R E S E N T S",
                color = Palette.GreenDim,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleSmall
            )
        }
    }
}

/**
 * Block ASCII drawn row by row at measured positions.
 *
 * The first version of this was a single `Text` with a `lineHeight` tuned so
 * the `#` blocks would touch. It looked right in the test renderer and
 * **sheared into garbage on a real phone** — the leading was derived from the
 * ink height of `#` in Robolectric's substitute font, and a device's monospace
 * face has different metrics entirely, so the rows overlapped and slid.
 *
 * There is no line height that is correct on every device, because the number
 * depends on a font that is chosen at runtime. So nothing is guessed: the
 * glyph is measured with the paint that is about to draw it, and each row is
 * placed at an explicit baseline exactly one ink-height below the last. The
 * blocks meet on any font, on any device, because the spacing is derived from
 * the font actually in use rather than from one that was available when the
 * code was written.
 */
@Composable
private fun BlockBanner(
    lines: List<String>,
    columns: Int,
    availableWidth: Float,
    color: Color,
    contentDescription: String
) {
    val density = LocalDensity.current
    val paint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.MONOSPACE
            textAlign = Paint.Align.LEFT
        }
    }

    // Measured, not assumed: advance width and ink height both come from the
    // paint at the size it will actually draw at.
    val metrics = remember(availableWidth, columns, density) {
        with(density) {
            val widthPx = availableWidth.dp.toPx()
            // Binary search would be overkill -- the advance is linear in size
            // for a monospace face, so one measurement scales exactly.
            paint.textSize = 100f
            val advanceAt100 = paint.measureText("#")
            val size = (widthPx / columns) / (advanceAt100 / 100f)
            paint.textSize = size
            val bounds = android.graphics.Rect()
            paint.getTextBounds("#", 0, 1, bounds)
            BannerMetrics(
                textSize = size,
                rowHeight = bounds.height().toFloat(),
                // drawText places the baseline; the ink sits above it.
                baselineOffset = -bounds.top.toFloat(),
                width = paint.measureText("#") * columns
            )
        }
    }

    val heightDp = with(density) { (metrics.rowHeight * lines.size).toDp() }
    val widthDp = with(density) { metrics.width.toDp() }

    Canvas(
        Modifier
            .width(widthDp)
            .height(heightDp)
            .semantics { this.contentDescription = contentDescription }
    ) {
        drawIntoCanvas { canvas ->
            paint.textSize = metrics.textSize
            paint.color = color.toArgb()
            for ((row, line) in lines.withIndex()) {
                canvas.nativeCanvas.drawText(
                    line,
                    0f,
                    metrics.baselineOffset + row * metrics.rowHeight,
                    paint
                )
            }
        }
    }
}

private class BannerMetrics(
    val textSize: Float,
    val rowHeight: Float,
    val baselineOffset: Float,
    val width: Float
)

/**
 * Fade up, hold, fade down — as a function of elapsed seconds.
 *
 * Split out so the beat can be measured rather than eyeballed. The hold is the
 * part that matters: an ident that starts fading the instant it arrives never
 * gets read, and the failure looks like a flicker rather than like a timing
 * bug, which is exactly the kind of thing that survives to release.
 */
fun developerIdentAlpha(elapsed: Float): Float = when {
    elapsed <= 0f -> 0f
    elapsed < FADE_SECONDS -> elapsed / FADE_SECONDS
    elapsed < FADE_SECONDS + HOLD_SECONDS -> 1f
    elapsed < IDENT_SECONDS ->
        1f - (elapsed - FADE_SECONDS - HOLD_SECONDS) / FADE_SECONDS
    else -> 0f
}

const val FADE_SECONDS = 0.6f
const val HOLD_SECONDS = 0.8f
const val IDENT_SECONDS = FADE_SECONDS + HOLD_SECONDS + FADE_SECONDS

/** The studio, in plain text: what the block ASCII is a picture of. */
const val DEVELOPER_NAME = "HamieTon.dev"

/** Columns in [DEVELOPER_BANNER]; the type is scaled so they all fit. */
const val BANNER_COLUMNS = 70

private const val SIDE_GUTTER = 16f

/**
 * "HamieTon.dev" as block ASCII.
 *
 * Every line is padded to the same width: the theme is monospace throughout,
 * and a ragged-width block reads as a rendering fault rather than as a logo.
 */
val DEVELOPER_BANNER: String = """
    ##  ##                 ##       ######                  ##            
    ##  ##                            ##                    ##            
    ##  ##  ###   # ## ##  ##  ###    ##    ###  # ##     ####  ###  ## ##
    ###### #  ##  ## ## ## ## ## ##   ##   ## ## ## ##   ## ## ## ## ## ##
    ##  ##  ####  ## ## ## ## #####   ##   ## ## ## ##   #  ## ##### ## ##
    ##  ## ## ##  ## ## ## ## ##      ##   ## ## ## ##   #  ## ##     # # 
    ##  ## ## ##  ## ## ## ## ## ##   ##   ## ## ## ## # ## ## ## ##  ### 
    ##  ##  ## ## ## ## ## ##  ###    ##    ###  ## ## #  ####  ###    #  
""".trimIndent().trim('\n')
