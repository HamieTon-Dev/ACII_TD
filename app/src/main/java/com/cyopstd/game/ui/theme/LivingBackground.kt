package com.cyopstd.game.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Animated backdrops for the battlefield and the menu.
 *
 * The governing constraint is that a background must never compete with the
 * game on top of it. Everything here is therefore built to the same budget:
 * cool, desaturated colours that cannot be mistaken for a threat, alpha in the
 * low tens, movement slow enough to read as ambience rather than motion, and
 * all of it drawn *under* the lanes so gameplay always paints over it.
 *
 * [NONE] is the free default. Everything else maps to a store product; a
 * background with a null [productId] is always available.
 */
enum class LivingBackground(
    val productId: String?,
    val displayName: String,
    val description: String,
    val tint: Color,
    /** Peak alpha, 0..255. Deliberately small. */
    val intensity: Int,
    /** Cycles per second of the slowest motion in the effect. */
    val speed: Float,
    /**
     * What the route corridors are tinted towards.
     *
     * A theme that recoloured everything *except* the lanes read as
     * half-applied — the corridors are the largest coloured shape on the
     * board. The tint is a pull towards this colour rather than a replacement,
     * so the corridor keeps its own value and stays distinguishable from the
     * backdrop, and every entry is a cool desaturated tone for the same reason
     * the backdrop bands are: a lane must never be mistakeable for a threat.
     */
    val laneTint: Color? = null
) {
    NONE(
        productId = null,
        displayName = "STATIC",
        description = "The plain grid. No motion.",
        tint = Color(0xFF12203A),
        intensity = 0,
        speed = 0f
    ),

    DRIFT(
        productId = "bg_drift",
        displayName = "DRIFT",
        description = "Slow diagonal data currents crossing the field.",
        tint = Color(0xFF2A6E8C),
        intensity = 34,
        speed = 0.055f,
        laneTint = Color(0xFF14384F)
    ),

    LATTICE(
        productId = "bg_lattice",
        displayName = "LATTICE",
        description = "A circuit lattice that breathes with the wave.",
        tint = Color(0xFF2E7D63),
        intensity = 40,
        speed = 0.18f,
        laneTint = Color(0xFF123E35)
    ),

    AURORA(
        productId = "bg_aurora",
        displayName = "AURORA",
        description = "Broad bands of cold light moving behind everything.",
        tint = Color(0xFF3C6BA8),
        intensity = 30,
        speed = 0.04f,
        laneTint = Color(0xFF1B3A66)
    ),

    RAINFALL(
        productId = "bg_rainfall",
        displayName = "RAINFALL",
        description = "Sparse columns of falling characters.",
        tint = Color(0xFF2F7F72),
        intensity = 36,
        speed = 0.5f,
        laneTint = Color(0xFF113B33)
    ),

    PULSE(
        productId = "bg_pulse",
        displayName = "PULSE",
        description = "Rings travelling outward from the core.",
        tint = Color(0xFF4A5FA8),
        intensity = 32,
        speed = 0.22f,
        laneTint = Color(0xFF26325F)
    );

    companion object {
        fun forProduct(id: String?): LivingBackground =
            entries.firstOrNull { it.productId != null && it.productId == id } ?: NONE

        val purchasable: List<LivingBackground> get() = entries.filter { it.productId != null }
    }
}
