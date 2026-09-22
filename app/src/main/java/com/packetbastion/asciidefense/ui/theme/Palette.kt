package com.packetbastion.asciidefense.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The whole game speaks in this palette. Colour is used to carry information —
 * friendly vs hostile, safe vs alarmed, spendable vs locked — never as
 * decoration, and never as the *only* signal: every entity also has a distinct
 * ASCII glyph so the game stays readable without colour discrimination.
 */
object Palette {

    // Surfaces -------------------------------------------------------------
    val Background = Color(0xFF070B14)
    val Surface = Color(0xFF0C1322)
    val SurfaceRaised = Color(0xFF121C30)
    val SurfaceSunken = Color(0xFF060A11)
    val Divider = Color(0xFF1E2D47)
    val GridLine = Color(0xFF12203A)

    // Friendly / defence ---------------------------------------------------
    val Cyan = Color(0xFF00E5FF)
    val CyanDim = Color(0xFF0E7C8C)
    val Green = Color(0xFF00FF9C)
    val GreenDim = Color(0xFF12855A)
    val Blue = Color(0xFF4D8DFF)
    val Purple = Color(0xFFB07BFF)

    // Hostile --------------------------------------------------------------
    val Red = Color(0xFFFF4D6A)
    val RedDeep = Color(0xFFB4122F)
    val Orange = Color(0xFFFF8A3D)
    val Magenta = Color(0xFFFF5BD1)

    // Information ----------------------------------------------------------
    val TextPrimary = Color(0xFFE6EEFA)
    val TextSecondary = Color(0xFF93A6C4)
    val TextMuted = Color(0xFF5C6E8C)
    val Crypto = Color(0xFFFFC94D)

    // Semantic helpers -----------------------------------------------------
    val Danger = Red
    val Warning = Orange
    val Success = Green

    /** Colour used to draw a given lane's corridor. */
    val laneTints = arrayOf(
        Color(0xFF15304F),
        Color(0xFF16344F),
        Color(0xFF14304A)
    )

    /** Server integrity bar colour, by remaining fraction. */
    fun healthColor(fraction: Float): Color = when {
        fraction > 0.6f -> Green
        fraction > 0.3f -> Orange
        else -> Red
    }
}
