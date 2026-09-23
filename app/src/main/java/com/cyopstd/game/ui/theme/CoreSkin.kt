package com.cyopstd.game.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Looks for the CORE-SERVER rack.
 *
 * A skin is pure data — four colours and a signature flourish — so a new one is
 * a table entry rather than a branch in the renderer, and so every option can
 * be rendered side by side and *looked at* before any of them is chosen.
 *
 * [DEFAULT] is not for sale. Everything else maps to a product id in the store
 * catalog; a skin with a null [productId] is always available.
 */
enum class CoreSkin(
    val productId: String?,
    val displayName: String,
    /** Rack fill. */
    val chassis: Color,
    /** Border, rules and the identity line. */
    val accent: Color,
    /** The activity LED grid. */
    val led: Color,
    /** Secondary text and the quieter details. */
    val trim: Color,
    val flourish: Flourish
) {
    DEFAULT(
        productId = null,
        displayName = "TERMINAL",
        chassis = Color(0xFF0C1322),
        accent = Color(0xFF00E5FF),
        led = Color(0xFF00FF9C),
        trim = Color(0xFF93A6C4),
        flourish = Flourish.NONE
    ),

    REACTOR(
        productId = "core_skin_reactor",
        displayName = "REACTOR",
        chassis = Color(0xFF17100A),
        accent = Color(0xFFFFB13D),
        led = Color(0xFFFFD98A),
        trim = Color(0xFFB48A5C),
        flourish = Flourish.RING
    ),

    MERIDIAN(
        productId = "core_skin_meridian",
        displayName = "MERIDIAN",
        chassis = Color(0xFF0B0A1E),
        accent = Color(0xFFE8C877),
        led = Color(0xFFF2E0A8),
        trim = Color(0xFF8E85C0),
        flourish = Flourish.TRACES
    ),

    GLACIER(
        productId = "core_skin_glacier",
        displayName = "GLACIER",
        chassis = Color(0xFF071620),
        accent = Color(0xFFAEE7FF),
        led = Color(0xFFE6F8FF),
        trim = Color(0xFF6FA8C0),
        flourish = Flourish.FROST
    ),

    MAINFRAME(
        productId = "core_skin_mainframe",
        displayName = "MAINFRAME",
        chassis = Color(0xFF031008),
        accent = Color(0xFF3BFF7A),
        led = Color(0xFF9CFFC2),
        trim = Color(0xFF3F8F5C),
        flourish = Flourish.SCANLINES
    ),

    CASCADE(
        productId = "core_skin_cascade",
        displayName = "CASCADE",
        chassis = Color(0xFF020C06),
        accent = Color(0xFF27F58C),
        led = Color(0xFF7BFFC0),
        trim = Color(0xFF2E7F58),
        flourish = Flourish.CASCADE
    ),

    NEONGRID(
        productId = "core_skin_neongrid",
        displayName = "NEONGRID",
        chassis = Color(0xFF04121F),
        accent = Color(0xFF4AD9FF),
        led = Color(0xFFBDEEFF),
        trim = Color(0xFF4093C4),
        flourish = Flourish.GRID
    ),

    VOID(
        productId = "core_skin_void",
        displayName = "VOID",
        chassis = Color(0xFF0D0716),
        accent = Color(0xFFC77BFF),
        led = Color(0xFFE9C6FF),
        trim = Color(0xFF7D6296),
        flourish = Flourish.STARFIELD
    );

    /** The extra mark that makes a skin recognisable at a glance. */
    enum class Flourish {
        NONE, RING, TRACES, FROST, STARFIELD,
        /** CRT phosphor scanlines. */
        SCANLINES,
        /** Falling code columns inside the rack. */
        CASCADE,
        /** A receding perspective grid. */
        GRID
    }

    companion object {
        /** The skin for a chosen product id, falling back to the free one. */
        fun forProduct(id: String?): CoreSkin =
            entries.firstOrNull { it.productId != null && it.productId == id } ?: DEFAULT

        /** Everything that can be sold. */
        val purchasable: List<CoreSkin> get() = entries.filter { it.productId != null }
    }
}
