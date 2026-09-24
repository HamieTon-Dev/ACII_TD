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
        // Sized to the screen rather than set in sp. The banner is
        // BANNER_COLUMNS characters of monospace and wrapping it would turn a
        // logo into wreckage, so the type is scaled to fit the width it has.
        val available = maxWidth.value - SIDE_GUTTER * 2f
        val fitted = (available / (BANNER_COLUMNS * MONOSPACE_ADVANCE))
            .coerceIn(MIN_BANNER_SP, MAX_BANNER_SP)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(horizontal = SIDE_GUTTER.dp)
                .alpha(developerIdentAlpha(elapsed))
        ) {
            Text(
                text = DEVELOPER_BANNER,
                color = Palette.Green,
                textAlign = TextAlign.Center,
                fontSize = fitted.sp,
                lineHeight = (fitted * LINE_HEIGHT_RATIO).sp,
                // Block ASCII only forms letters when its rows touch
                // exactly. Compose's default leading -- font padding plus
                // whatever the style's lineHeight adds -- pushes them apart
                // into scattered punctuation, or overlaps them into a smear.
                // Turning both off makes lineHeight mean what it says, so the
                // ratio below is the real cell height and nothing else.
                style = MaterialTheme.typography.bodySmall.copy(
                    // Zero letter spacing. The theme gives body text 0.3sp for
                    // legibility, which is right for prose and fatal here: a
                    // block of ASCII is a grid, and a third of a point added
                    // to every cell is what turns letterforms into a smear.
                    letterSpacing = 0.sp,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.Both
                    )
                ),
                // Block ASCII is a picture of a word. A screen reader given
                // the raw text reads out pipes and underscores, so it is told
                // the name instead -- which is also the only way a test can
                // check that the banner still says what it is meant to say.
                modifier = Modifier.semantics { contentDescription = DEVELOPER_NAME }
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

/** The studio, in plain text: what the block ASCII is a picture of. */
const val DEVELOPER_NAME = "HamieTon.dev"

/** Columns in [DEVELOPER_BANNER]; the type is scaled so they all fit. */
const val BANNER_COLUMNS = 70

/** Width of a monospace character as a fraction of its point size. */
private const val MONOSPACE_ADVANCE = 0.6f

/**
 * Leading, as a fraction of the font size.
 *
 * Not a taste call: the banner is built from `#` blocks, and they only form
 * solid letters when consecutive rows touch. Measured rather than guessed --
 * a `#` inks exactly 0.71 of its point size in the platform monospace face, so
 * that is the line height that makes the blocks meet. The font's natural
 * leading (1.172) leaves visible gaps and the letters read as dot matrix.
 */
private const val LINE_HEIGHT_RATIO = 0.71f
private const val SIDE_GUTTER = 16f
private const val MIN_BANNER_SP = 6f
private const val MAX_BANNER_SP = 30f

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
