package com.cyopstd.game.model

/**
 * Which kind of boss this is.
 *
 * Until 1.21.0 there was exactly one boss in the game — `EnemyType.BOSS`,
 * glyph `[!!!]` — and every boss anyone ever fought was that same thing with a
 * different set of [BossModifier]s rolled onto it. Modifiers make a boss
 * *harder*; they do not make it a different opponent, and a player who has
 * cleared wave 60 has seen `[!!!]` a dozen times.
 *
 * A variant is the opponent's identity: its own glyph, its own weighting of
 * health, armour and speed, and where it is allowed to show up. Everything
 * else — the modifier roll, the rewards, the routes — is unchanged and applies
 * on top, so a `[GG]` on cycle 6 is still a `[GG]` and still rolls its two
 * modifiers.
 *
 * Adding one is a row in this table. That is deliberate: the backlog has
 * several more waiting on the owner to choose, and two AI bosses that must
 * exist before the second map can.
 */
enum class BossVariant(
    /** Stable id, for saves and for the leaderboard. */
    val id: String,
    val displayName: String,
    /** Drawn in place of the type's glyph. */
    val glyph: String,
    val healthScale: Float,
    val armorBonus: Float,
    val speedScale: Float,
    /** One line for the dossier panel and the codex. */
    val signature: String,
    /** The earliest boss cycle this may appear on. */
    val firstCycle: Int
) {
    /**
     * The original, and still the one a player meets first.
     *
     * Cycle 1 is deliberately a plain fight with no modifiers, so it stays a
     * plain fight against a plain opponent too.
     */
    BREACH(
        id = "breach",
        displayName = "BREACH",
        glyph = "[!!!]",
        healthScale = 1f,
        armorBonus = 0f,
        speedScale = 1f,
        signature = "A coordinated breach attempt. No tricks, just weight.",
        firstCycle = 1
    ),

    /**
     * The wall.
     *
     * Slow, enormous, and armoured past what raw damage can chew through — it
     * is the fight that asks whether you built anything that ignores armour.
     */
    GOOD_GAME(
        id = "gg",
        displayName = "GOOD GAME",
        glyph = "[GG]",
        healthScale = 1.6f,
        armorBonus = 7f,
        speedScale = 0.78f,
        signature = "Enormous and heavily armoured, but slow. Bring something " +
            "that ignores armour.",
        firstCycle = 2
    ),

    /**
     * The one that gets back up.
     *
     * Comes back once at [ZOMBIE_REVIVE_FRACTION] of its health the first time
     * it is killed, which punishes a board that can only just manage a single
     * kill and rewards one with something left in reserve.
     */
    ZOMBIE(
        id = "zz",
        displayName = "ZOMBIE",
        glyph = "[ZZ]",
        healthScale = 0.85f,
        armorBonus = 0f,
        speedScale = 1.12f,
        signature = "Gets back up once, at 40% health. Killing it is not the " +
            "same as finishing it.",
        firstCycle = 3
    );

    companion object {
        /** How much health a ZOMBIE comes back with. */
        const val ZOMBIE_REVIVE_FRACTION = 0.4f

        fun fromIdSafe(id: String?): BossVariant =
            entries.firstOrNull { it.id == id } ?: BREACH

        /**
         * The variants a boss on [cycle] may roll.
         *
         * Introduced one at a time, like the modifiers are: a player meets a
         * new opponent on its own rather than three at once, and cycle 1 is
         * always the plain fight.
         */
        fun poolForCycle(cycle: Int): List<BossVariant> =
            entries.filter { cycle >= it.firstCycle }
    }
}
