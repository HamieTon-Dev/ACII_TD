package com.cyopstd.game.ui.common

import com.cyopstd.game.core.Balance

/**
 * How the CORE FIRMWARE multiplier is written on screen.
 *
 * This exists because the obvious formatting was wrong in a way that made a
 * working system look broken. One firmware level is +0.5% damage, so at two
 * decimals level 1 (×1.005) printed as "×1.00" and level 2 as "×1.01": the
 * player spent € and watched a number not move. Worse, the per-level figure was
 * `(0.005 * 100).toInt()`, which truncates to zero and told them outright that
 * an upgrade does nothing.
 *
 * Three decimals is the right precision for this multiplier specifically: the
 * step is 0.005, so **every single level changes the last digit**, and no
 * purchase is ever invisible. Both screens go through here so they cannot drift
 * apart again.
 */
object FirmwareFormat {

    /** e.g. "×1.005". Always shows the effect of the most recent level. */
    fun multiplier(level: Int): String =
        "×%.3f".format(Balance.firmwareDamageMultiplier(level))

    /** e.g. "+0.5%". Never rounds a real increase down to nothing. */
    fun perLevel(): String = "+%s%%".format(
        trimTrailingZero(Balance.FIRMWARE_DAMAGE_PER_LEVEL * 100f)
    )

    /** e.g. "+2.5%" for five levels — what a purchase is actually buying. */
    fun gain(levels: Int): String = "+%s%%".format(
        trimTrailingZero(levels * Balance.FIRMWARE_DAMAGE_PER_LEVEL * 100f)
    )

    /** "0.5" rather than "0.50", and "3" rather than "3.0". */
    private fun trimTrailingZero(value: Float): String {
        val text = "%.1f".format(value)
        return if (text.endsWith(".0")) text.dropLast(2) else text
    }
}
