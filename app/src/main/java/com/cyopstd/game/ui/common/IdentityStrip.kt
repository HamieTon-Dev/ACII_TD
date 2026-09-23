package com.cyopstd.game.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyopstd.game.BuildConfig
import com.cyopstd.game.ui.theme.Palette

/**
 * The player's tag and the build identifier, on every screen.
 *
 * Small and dim enough to be ignorable, but deliberately **not** scaled down
 * past legibility: a build id exists to be read off a photograph of a bug
 * report, so it is set at a size and weight that survives a phone camera and a
 * crop, in the same monospace the rest of the game uses. It is drawn last, over
 * whatever screen is showing, so there is no screen it can be missing from.
 */
object BuildStamp {

    /**
     * e.g. `1.9.1 (13) · 8f3a21`.
     *
     * The suffix is derived from the compile-time stamp rather than stored, so
     * two builds of the same version string are still distinguishable — which
     * is the whole point of having it during testing.
     */
    val id: String by lazy {
        val short = BuildConfig.BUILD_STAMP.toLongOrNull()
            ?.let { java.lang.Long.toHexString(it).takeLast(6) }
            ?: "unknown"
        "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) · $short"
    }
}

@Composable
fun IdentityStrip(
    playerTag: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        if (playerTag.isNotBlank()) {
            Text(
                text = playerTag,
                color = Palette.TextMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                lineHeight = 11.sp,
                modifier = Modifier.align(Alignment.CenterStart)
            )
        }
        Text(
            text = BuildStamp.id,
            color = Palette.TextMuted,
            fontFamily = FontFamily.Monospace,
            // Slightly heavier than the tag: this is the string someone will
            // photograph and read back, so it takes the legibility budget.
            fontWeight = FontWeight.Medium,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
}
