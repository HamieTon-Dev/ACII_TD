package com.cyopstd.game.model

import com.cyopstd.game.core.Balance
import kotlinx.serialization.Serializable

/** The three stats AGENT FIRMWARE can raise, each bought separately. */
enum class FirmwareStat(val label: String, val unit: String) {
    DAMAGE("DAMAGE", "damage"),
    RATE("FIRE RATE", "fire rate"),
    RANGE("RANGE", "range")
}

/**
 * Permanent upgrades for one agent type, bought with € BUDGET on the FIRMWARE
 * screen (owner, 2026-09-28). Each stat has its own level, 0 to
 * [Balance.MAX_AGENT_FIRMWARE_LEVEL]. They apply to every agent of that type in
 * every future match.
 */
@Serializable
data class AgentFirmware(
    val damage: Int = 0,
    val rate: Int = 0,
    val range: Int = 0
) {
    fun level(stat: FirmwareStat): Int = when (stat) {
        FirmwareStat.DAMAGE -> damage
        FirmwareStat.RATE -> rate
        FirmwareStat.RANGE -> range
    }

    fun with(stat: FirmwareStat, level: Int): AgentFirmware = when (stat) {
        FirmwareStat.DAMAGE -> copy(damage = level)
        FirmwareStat.RATE -> copy(rate = level)
        FirmwareStat.RANGE -> copy(range = level)
    }

    val damageMultiplier: Float get() = Balance.agentFirmwareMultiplier(FirmwareStat.DAMAGE, damage)
    val rateMultiplier: Float get() = Balance.agentFirmwareMultiplier(FirmwareStat.RATE, rate)
    val rangeMultiplier: Float get() = Balance.agentFirmwareMultiplier(FirmwareStat.RANGE, range)

    val isEmpty: Boolean get() = damage == 0 && rate == 0 && range == 0

    companion object {
        val NONE = AgentFirmware()

        /**
         * Which agents AGENT FIRMWARE is sold for. SERVER SYSTEMS ENGINEER is
         * left out on purpose: it never needs a buff (owner, 2026-09-28).
         */
        val eligible: List<AgentType> get() = AgentType.catalog.filter { !it.healsServer }

        fun isEligible(type: AgentType): Boolean = !type.healsServer
    }
}
