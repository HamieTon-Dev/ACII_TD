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
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyopstd.game.R
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
            // The wordmark is a drawable, not text.
            //
            // Two attempts at rendering it as monospace ASCII both looked
            // perfect in the test renderer and were unreadable on a phone: the
            // row spacing depends on the metrics of a font chosen at runtime,
            // and there is no constant that is right on every device. Centred
            // text also centres each line independently, which sheared the
            // whole logo into a diagonal.
            //
            // A vector has no font, no line height and no per-line centring.
            // Every cell is still a drawn '#', so it is the same mark -- it
            // just scales to any screen instead of depending on one.
            Image(
                painter = painterResource(R.drawable.hamieton_banner),
                contentDescription = DEVELOPER_NAME,
                colorFilter = ColorFilter.tint(Palette.Green),
                contentScale = ContentScale.Fit,
                // Width from the screen, height from the mark's own
                // proportions. Without the aspect ratio the image has no
                // height to fit *into* and collapses to its intrinsic size,
                // which is a logo a fifth of the width it should be.
                modifier = Modifier
                    .fillMaxWidth(BANNER_WIDTH_FRACTION)
                    .aspectRatio(BANNER_ASPECT)
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

/**
 * How wide the mark sits, as a fraction of the screen.
 *
 * Not the full width: a wordmark that touches both edges reads as a banner
 * rather than as a logo, and on a tall narrow phone it would be a thin strip.
 */
private const val BANNER_WIDTH_FRACTION = 0.78f

/**
 * The mark's proportions, derived from the grid it is drawn on rather than
 * typed in, so regenerating the drawable cannot leave this behind.
 */
private val BANNER_ASPECT: Float
    get() = BANNER_COLUMNS.toFloat() / DEVELOPER_BANNER.lines().size

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
