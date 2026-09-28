package com.cyopstd.game.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.cyopstd.game.core.Balance
import com.cyopstd.game.model.AgentFirmware
import com.cyopstd.game.model.AgentType
import com.cyopstd.game.model.FirmwareStat
import com.cyopstd.game.ui.common.Caption
import com.cyopstd.game.ui.common.CompactButton
import com.cyopstd.game.ui.common.TerminalPanel
import com.cyopstd.game.ui.game.agentClassColor
import com.cyopstd.game.ui.theme.Palette

/**
 * AGENT FIRMWARE (owner, 2026-09-28): permanent upgrades for one agent type at
 * a time, bought with €. Damage, fire rate and range are separate tracks, each
 * up to [Balance.MAX_AGENT_FIRMWARE_LEVEL]. SERVER SYSTEMS ENGINEER is not
 * listed: it never needs a buff.
 */
@Composable
fun AgentFirmwarePanel(
    budget: Long,
    agentFirmware: Map<AgentType, AgentFirmware>,
    unlockedAgents: Set<String>,
    onBuy: (AgentType, FirmwareStat, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val agents = AgentFirmware.eligible
    var chosen by remember { mutableStateOf(agents.first()) }

    Row(modifier = modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(
            modifier = Modifier
                .weight(0.85f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            for (type in agents) {
                val firmware = agentFirmware[type] ?: AgentFirmware.NONE
                val color = agentClassColor(type)
                val selected = type == chosen
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("firmware-agent-${type.name}")
                        .background(
                            if (selected) color.copy(alpha = 0.18f) else Palette.Surface.copy(alpha = 0.7f),
                            RoundedCornerShape(4.dp)
                        )
                        .border(if (selected) 2.dp else 1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .clickable { chosen = type }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text("[${type.glyph}]", style = MaterialTheme.typography.titleMedium, color = color)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = type.displayName + if (type.name in unlockedAgents) "" else "  (LOCKED)",
                            style = MaterialTheme.typography.labelMedium,
                            color = Palette.TextPrimary,
                            maxLines = 1
                        )
                        Text(
                            text = "DMG ${firmware.damage} · RATE ${firmware.rate} · RNG ${firmware.range}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (firmware.isEmpty) Palette.TextMuted else Palette.Crypto,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1.15f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
        ) {
            val firmware = agentFirmware[chosen] ?: AgentFirmware.NONE
            TerminalPanel(title = "[${chosen.glyph}] ${chosen.displayName}", accent = agentClassColor(chosen)) {
                Text(
                    text = "€ $budget available",
                    style = MaterialTheme.typography.titleMedium,
                    color = Palette.Cyan
                )
                for (stat in FirmwareStat.entries) {
                    Spacer(Modifier.height(10.dp))
                    StatTrack(
                        type = chosen,
                        stat = stat,
                        level = firmware.level(stat),
                        budget = budget,
                        onBuy = onBuy
                    )
                }
                Spacer(Modifier.height(8.dp))
                Caption(
                    "Applies to every ${chosen.displayName} in every match from now on, " +
                        "on top of CORE FIRMWARE. Early levels are cheap; each one costs " +
                        "more than the last, and the top levels cost millions."
                )
            }
        }
    }
}

@Composable
private fun StatTrack(
    type: AgentType,
    stat: FirmwareStat,
    level: Int,
    budget: Long,
    onBuy: (AgentType, FirmwareStat, Int) -> Unit
) {
    val maxed = level >= Balance.MAX_AGENT_FIRMWARE_LEVEL
    val next = Balance.agentFirmwareCost(level)
    val affordable = Balance.agentFirmwareLevelsAffordable(level, budget)
    val now = Balance.agentFirmwareMultiplier(stat, level)
    val perLevel = Balance.agentFirmwareMultiplier(stat, 1) - 1f

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stat.label,
            style = MaterialTheme.typography.labelMedium,
            color = Palette.Green,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "LV $level  ×${"%.3f".format(now)}",
            style = MaterialTheme.typography.labelMedium,
            color = Palette.Crypto
        )
    }
    Text(
        text = if (maxed) "FULLY INSTALLED" else
            "NEXT € $next · +${"%.2f".format(perLevel * 100)}% ${stat.unit} a level" +
                if (affordable > 0) " · $affordable affordable" else "",
        style = MaterialTheme.typography.labelSmall,
        color = if (!maxed && budget >= next) Palette.TextSecondary else Palette.TextMuted
    )
    Spacer(Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for ((label, count) in listOf("+1" to 1, "+10" to 10, "+100" to 100)) {
            CompactButton(
                text = label,
                onClick = { onBuy(type, stat, count) },
                enabled = affordable >= 1,
                accent = Palette.Green,
                modifier = Modifier.weight(1f).testTag("firmware-${stat.name}-$label")
            )
        }
        CompactButton(
            text = "MAX",
            onClick = { onBuy(type, stat, affordable) },
            enabled = affordable >= 1,
            accent = Palette.Crypto,
            modifier = Modifier.weight(1f)
        )
    }
}
