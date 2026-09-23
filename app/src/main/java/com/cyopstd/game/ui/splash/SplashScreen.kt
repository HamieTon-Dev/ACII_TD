package com.cyopstd.game.ui.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyopstd.game.ui.common.AsciiBackdrop
import com.cyopstd.game.ui.theme.Palette

/**
 * Boot screen. It masks the normal cold-start work rather than adding to it —
 * the progress bar is driven by a real elapsed timer with a short floor, and the
 * screen dismisses itself the moment that floor is reached.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    var progress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        val startedAt = withFrameNanos { it }
        while (progress < 1f) {
            withFrameNanos { nanos ->
                val elapsed = (nanos - startedAt) / 1_000_000_000f
                progress = (elapsed / SPLASH_SECONDS).coerceIn(0f, 1f)
            }
        }
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.Background),
        contentAlignment = Alignment.Center
    ) {
        AsciiBackdrop(modifier = Modifier.fillMaxSize(), density = 34)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            // The publisher mark comes first and on its own: the studio
            // signs the game, it is not part of the title.
            Text(
                text = HAMIETON_MARK,
                style = MaterialTheme.typography.bodySmall,
                color = Palette.Green,
                textAlign = TextAlign.Center,
                lineHeight = 13.sp,
                fontSize = 11.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "HAMIETON-DEV",
                style = MaterialTheme.typography.titleMedium,
                color = Palette.GreenDim,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(22.dp))

            Text(
                text = "CyOps TD",
                style = MaterialTheme.typography.displayMedium,
                color = Palette.Cyan,
                textAlign = TextAlign.Center
            )
            Text(
                text = "ASCII CYBER DEFENSE",
                style = MaterialTheme.typography.titleLarge,
                color = Palette.Green,
                textAlign = TextAlign.Center
            )

            Box(Modifier.height(36.dp))

            Text(
                text = "Initializing Network...",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextSecondary
            )

            Box(Modifier.height(10.dp))

            val filled = (progress * BAR_CELLS).toInt().coerceIn(0, BAR_CELLS)
            Text(
                text = "[" + "#".repeat(filled) + "-".repeat(BAR_CELLS - filled) + "]",
                style = MaterialTheme.typography.titleMedium,
                color = Palette.Cyan
            )

            Box(Modifier.height(8.dp))

            Text(
                text = bootLineFor(progress),
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextMuted
            )
        }
    }
}

private fun bootLineFor(progress: Float): String = when {
    progress < 0.2f -> "> mounting lane topology"
    progress < 0.4f -> "> loading threat signatures"
    progress < 0.6f -> "> arming cyber agents"
    progress < 0.8f -> "> syncing CORE-SERVER"
    else -> "> perimeter online"
}

private const val SPLASH_SECONDS = 1.9f
private const val BAR_CELLS = 20

/**
 * The publisher mark, drawn in the same monospace ASCII the rest of the game
 * speaks rather than shipped as an image. It is the launcher icon's shield and
 * `>_<` glyph, rendered in text.
 *
 * Every line is padded to the same width on purpose: in a proportional font
 * this would still be a mess, but the theme sets monospace throughout, and a
 * ragged-width block reads as a rendering fault rather than as a logo.
 */
private val HAMIETON_MARK = """
    /\===============/\
   ||               ||
   ||     >_<       ||
    \\             //
     \\===========//
       \\=======//
         \\===//
           \_/
""".trimIndent()
