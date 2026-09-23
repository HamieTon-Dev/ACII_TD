package com.cyopstd.game.store

/**
 * Everything the player can buy, in one table.
 *
 * The ids are the product ids that must be created in the Google Play Console.
 * They are declared here rather than in the billing layer so the whole catalog
 * — what a purchase grants, what it costs, what it unlocks — is one readable
 * list that can be tested without Play, without a network and without a device.
 *
 * Prices carry a local fallback string only so the store is legible before Play
 * answers. **The price shown to the player is always Play's**, because it is
 * the one in their currency and the one they will actually be charged.
 */
enum class SkuKind {
    /** Owned forever once bought. Restorable. */
    PERMANENT,

    /** Consumed on purchase; grants € and can be bought again. */
    CONSUMABLE
}

enum class Sku(
    val id: String,
    val kind: SkuKind,
    val title: String,
    val summary: String,
    /** Fallback display only. Play's localized price wins whenever available. */
    val fallbackPrice: String,
    /** € credited on purchase. */
    val grantsBudget: Int = 0,
    /** Other products this one also unlocks. */
    val alsoUnlocks: List<String> = emptyList()
) {
    // ----------------------------------------------------------- conveniences
    NO_ADS(
        id = "no_ads",
        kind = SkuKind.PERMANENT,
        title = "REMOVE ADS",
        summary = "No interstitial after a failed run. Ever.",
        fallbackPrice = "$4.99"
    ),

    SPEED_5X(
        id = "speed_5x",
        kind = SkuKind.PERMANENT,
        title = "5× SPEED",
        summary = "Adds a fifth simulation speed for clearing waves fast.",
        fallbackPrice = "$4.99"
    ),

    // ----------------------------------------------------------------- budget
    BUDGET_SMALL(
        id = "budget_small",
        kind = SkuKind.CONSUMABLE,
        title = "€150 BUDGET",
        summary = "Spend it on permanent CORE FIRMWARE damage.",
        fallbackPrice = "$0.99",
        grantsBudget = 150
    ),

    BUDGET_MEDIUM(
        id = "budget_medium",
        kind = SkuKind.CONSUMABLE,
        title = "€500 BUDGET",
        summary = "Better value per euro than the small pack.",
        fallbackPrice = "$2.99",
        grantsBudget = 500
    ),

    BUDGET_LARGE(
        id = "budget_large",
        kind = SkuKind.CONSUMABLE,
        title = "€900 BUDGET",
        summary = "The best value per euro.",
        fallbackPrice = "$4.99",
        grantsBudget = 900
    ),

    // ------------------------------------------------------------ agent skins
    SKIN_AGENTS_SPECTRUM(
        id = "skin_agents_spectrum",
        kind = SkuKind.PERMANENT,
        title = "SPECTRUM AGENTS",
        summary = "Agents drift slowly through the colour spectrum, each unit " +
            "offset from the next.",
        fallbackPrice = "$2.99"
    ),

    // ------------------------------------------------------- core-server skins
    CORE_SKIN_REACTOR(
        id = "core_skin_reactor",
        kind = SkuKind.PERMANENT,
        title = "CORE: REACTOR",
        summary = "Amber containment rings around a running reaction.",
        fallbackPrice = "$1.00"
    ),

    CORE_SKIN_MERIDIAN(
        id = "core_skin_meridian",
        kind = SkuKind.PERMANENT,
        title = "CORE: MERIDIAN",
        summary = "Gold traces over deep indigo.",
        fallbackPrice = "$1.00"
    ),

    CORE_SKIN_GLACIER(
        id = "core_skin_glacier",
        kind = SkuKind.PERMANENT,
        title = "CORE: GLACIER",
        summary = "Ice needles radiating from a frozen core.",
        fallbackPrice = "$1.00"
    ),

    CORE_SKIN_VOID(
        id = "core_skin_void",
        kind = SkuKind.PERMANENT,
        title = "CORE: VOID",
        summary = "A violet starfield behind the rack.",
        fallbackPrice = "$1.00"
    ),

    CORE_SKIN_MAINFRAME(
        id = "core_skin_mainframe",
        kind = SkuKind.PERMANENT,
        title = "CORE: MAINFRAME",
        summary = "CRT phosphor green, scanlines and a refresh sweep.",
        fallbackPrice = "$1.00"
    ),

    CORE_SKIN_CASCADE(
        id = "core_skin_cascade",
        kind = SkuKind.PERMANENT,
        title = "CORE: CASCADE",
        summary = "Code falling inside the rack itself.",
        fallbackPrice = "$1.00"
    ),

    CORE_SKIN_NEONGRID(
        id = "core_skin_neongrid",
        kind = SkuKind.PERMANENT,
        title = "CORE: NEONGRID",
        summary = "Neon-blue grid receding into the core, with a holographic " +
            "ring that skims blue to green.",
        fallbackPrice = "$1.00"
    ),

    CORE_SKIN_PACK(
        id = "core_skin_pack",
        kind = SkuKind.PERMANENT,
        title = "CORE SKIN PACK",
        // NEONGRID is deliberately not in here. It is the premium skin, sold
        // on its own, and folding it into a 2.50 bundle would give it away.
        summary = "Six CORE-SERVER skins, plus \u20AC200. NEONGRID sold separately.",
        fallbackPrice = "$2.50",
        grantsBudget = 200,
        alsoUnlocks = listOf(
            "core_skin_reactor", "core_skin_meridian", "core_skin_glacier",
            "core_skin_void", "core_skin_mainframe", "core_skin_cascade"
        )
    ),

    // ------------------------------------------------------ living backgrounds
    BG_DRIFT(
        id = "bg_drift",
        kind = SkuKind.PERMANENT,
        title = "LIVING: DRIFT",
        summary = "Slow data currents behind the menu and the board.",
        fallbackPrice = "$1.99"
    ),

    BG_LATTICE(
        id = "bg_lattice",
        kind = SkuKind.PERMANENT,
        title = "LIVING: LATTICE",
        summary = "A breathing circuit lattice that reacts to the wave.",
        fallbackPrice = "$2.99"
    ),

    BG_AURORA(
        id = "bg_aurora",
        kind = SkuKind.PERMANENT,
        title = "LIVING: AURORA",
        summary = "Cold light moving behind everything.",
        fallbackPrice = "$4.99"
    ),

    BG_RAINFALL(
        id = "bg_rainfall",
        kind = SkuKind.PERMANENT,
        title = "LIVING: RAINFALL",
        summary = "Sparse columns of falling characters behind the field.",
        fallbackPrice = "$1.99"
    ),

    BG_PULSE(
        id = "bg_pulse",
        kind = SkuKind.PERMANENT,
        title = "LIVING: PULSE",
        summary = "Rings travelling outward from the core.",
        fallbackPrice = "$2.99"
    ),

    BG_PACK(
        id = "bg_pack",
        kind = SkuKind.PERMANENT,
        title = "ALL LIVING BACKGROUNDS",
        // Every background, not most of them. RAINFALL and PULSE were added
        // after this pack was written and were not added to it, so a player
        // buying something called ALL LIVING BACKGROUNDS would have received
        // three of five.
        summary = "All five living backgrounds, plus \u20AC200.",
        fallbackPrice = "$4.99",
        grantsBudget = 200,
        alsoUnlocks = listOf(
            "bg_drift", "bg_lattice", "bg_aurora", "bg_rainfall", "bg_pulse"
        )
    ),

    // ------------------------------------------------------------ starter pack
    STARTER_PACK(
        id = "starter_pack",
        kind = SkuKind.PERMANENT,
        title = "STARTER PACK",
        summary = "No ads, the SPECTRUM agent skin, the DRIFT background, " +
            "and €200.",
        fallbackPrice = "$4.99",
        grantsBudget = 200,
        alsoUnlocks = listOf("no_ads", "skin_agents_spectrum", "bg_drift")
    );

    companion object {
        fun byId(id: String): Sku? = entries.firstOrNull { it.id == id }

        /** Everything a purchase of [sku] leaves the player owning. */
        fun unlockedBy(sku: Sku): Set<String> =
            buildSet {
                if (sku.kind == SkuKind.PERMANENT) add(sku.id)
                addAll(sku.alsoUnlocks)
            }

        val coreSkins: List<Sku> = listOf(
            CORE_SKIN_REACTOR, CORE_SKIN_MERIDIAN, CORE_SKIN_GLACIER,
            CORE_SKIN_VOID, CORE_SKIN_MAINFRAME, CORE_SKIN_CASCADE,
            CORE_SKIN_NEONGRID
        )

        val backgrounds: List<Sku> =
            listOf(BG_DRIFT, BG_LATTICE, BG_AURORA, BG_RAINFALL, BG_PULSE)
    }
}
