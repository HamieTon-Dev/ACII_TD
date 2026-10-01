package com.cyopstd.game.ui.theme

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
    val CyanDim = Color(0xFF0A8FA3)
    val Green = Color(0xFF00FF9C)
    val GreenDim = Color(0xFF0C9A60)
    val Blue = Color(0xFF2E7BFF)
    val Purple = Color(0xFFA259FF)

    // Hostile --------------------------------------------------------------
    val Red = Color(0xFFFF2D55)
    val RedDeep = Color(0xFFC8102E)
    val Orange = Color(0xFFFF7A1A)
    val Magenta = Color(0xFFFF2EC4)

    // Information ----------------------------------------------------------
    val TextPrimary = Color(0xFFE6EEFA)
    val TextSecondary = Color(0xFF93A6C4)
    val TextMuted = Color(0xFF5C6E8C)
    val Crypto = Color(0xFFFFD426)

    /** ANTI DUCK USB's hologram (♡2): it shimmers between these two. */
    val HologramGreen = Color(0xFF5CFF7A)
    val HologramYellow = Color(0xFFF2F25A)

    // Semantic helpers -----------------------------------------------------
    val Danger = Red
    val Warning = Orange
    val Success = Green

    /** Corridor fill for each route. */
    val laneTints = arrayOf(
        Color(0xFF15304F),
        Color(0xFF14304A)
    )

    /**
     * The backdrop shifts to the next of these every five waves, so a long run
     * does not spend an hour on one shade of navy. Deliberately constrained:
     * every entry is a near-black cool tone (navy, pine, indigo, slate, ocean,
     * steel, moss, twilight) with no red, orange, magenta or purple in it, so
     * the shift can never be mistaken for an enemy's colour, and none is light
     * enough to wash out the ASCII drawn on top of it.
     */
    val backdropBands = arrayOf(
        Color(0xFF060A11),  // midnight navy   - where the game starts
        Color(0xFF07110F),  // deep pine
        Color(0xFF080D18),  // indigo slate
        Color(0xFF0A1111),  // graphite teal
        Color(0xFF05101A),  // deep ocean
        Color(0xFF0D1014),  // cool steel
        Color(0xFF08130C),  // moss
        Color(0xFF0A0C1A)   // twilight indigo
    )

    /** How many waves share one backdrop colour. */
    const val BACKDROP_BAND_WAVES = 5

    /** The backdrop colour for [wave], cycling once the bands run out. */
    fun backdropBand(wave: Int): Color {
        val band = (wave - 1).coerceAtLeast(0) / BACKDROP_BAND_WAVES
        return backdropBands[band % backdropBands.size]
    }

    /**
     * Rack indicator LEDs.
     *
     * Shared by every CORE-SERVER skin rather than tinted per skin. A skin
     * changes the *chassis* — its colour, its flourish, its frame — but the
     * indicator lights on a rack are green for link and power and amber for
     * activity and warnings whatever the box is painted. Tinting them violet
     * or gold to match a skin made them read as decoration instead of as
     * hardware.
     */
    val ServerLedGreen = Color(0xFF22F060)
    val ServerLedAmber = Color(0xFFFF9A1A)

    /** Server integrity bar colour, by remaining fraction. */
    fun healthColor(fraction: Float): Color = when {
        fraction > 0.6f -> Green
        fraction > 0.3f -> Orange
        else -> Red
    }
}
