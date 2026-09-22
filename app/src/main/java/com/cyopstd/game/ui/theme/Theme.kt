package com.cyopstd.game.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * CyOps TD is a terminal. It is dark, it is monospace, and it does not
 * follow the system light/dark setting — a cyan-on-black SOC display in "light
 * mode" would be a different game.
 */

/**
 * Android's bundled monospace face. Using the platform font instead of bundling
 * JetBrains Mono keeps several hundred kilobytes out of the APK and guarantees
 * the glyph coverage we need for the box-drawing and ASCII art.
 */
val TerminalFont: FontFamily = FontFamily.Monospace

private val BastionTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = TerminalFont, fontWeight = FontWeight.Bold,
        fontSize = 44.sp, letterSpacing = 6.sp
    ),
    displayMedium = TextStyle(
        fontFamily = TerminalFont, fontWeight = FontWeight.Bold,
        fontSize = 30.sp, letterSpacing = 4.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = TerminalFont, fontWeight = FontWeight.Bold,
        fontSize = 22.sp, letterSpacing = 2.sp
    ),
    titleLarge = TextStyle(
        fontFamily = TerminalFont, fontWeight = FontWeight.Bold,
        fontSize = 18.sp, letterSpacing = 1.5.sp
    ),
    titleMedium = TextStyle(
        fontFamily = TerminalFont, fontWeight = FontWeight.Bold,
        fontSize = 15.sp, letterSpacing = 1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = TerminalFont, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, letterSpacing = 0.4.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = TerminalFont, fontWeight = FontWeight.Normal,
        fontSize = 13.sp, letterSpacing = 0.4.sp
    ),
    bodySmall = TextStyle(
        fontFamily = TerminalFont, fontWeight = FontWeight.Normal,
        fontSize = 11.5.sp, letterSpacing = 0.3.sp
    ),
    labelLarge = TextStyle(
        fontFamily = TerminalFont, fontWeight = FontWeight.Bold,
        fontSize = 14.sp, letterSpacing = 1.2.sp
    ),
    labelMedium = TextStyle(
        fontFamily = TerminalFont, fontWeight = FontWeight.Bold,
        fontSize = 12.sp, letterSpacing = 1.sp
    ),
    labelSmall = TextStyle(
        fontFamily = TerminalFont, fontWeight = FontWeight.Normal,
        fontSize = 10.5.sp, letterSpacing = 0.8.sp
    )
)

private val BastionColors = darkColorScheme(
    primary = Palette.Cyan,
    onPrimary = Palette.Background,
    secondary = Palette.Green,
    onSecondary = Palette.Background,
    tertiary = Palette.Purple,
    background = Palette.Background,
    onBackground = Palette.TextPrimary,
    surface = Palette.Surface,
    onSurface = Palette.TextPrimary,
    surfaceVariant = Palette.SurfaceRaised,
    onSurfaceVariant = Palette.TextSecondary,
    error = Palette.Red,
    onError = Palette.TextPrimary,
    outline = Palette.Divider
)

@Composable
fun CyOpsTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = BastionColors,
        typography = BastionTypography,
        content = content
    )
}
