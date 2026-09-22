package com.packetbastion.asciidefense.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.packetbastion.asciidefense.core.Balance
import com.packetbastion.asciidefense.model.Agent
import com.packetbastion.asciidefense.ui.common.AsciiRule
import com.packetbastion.asciidefense.ui.common.CompactButton
import com.packetbastion.asciidefense.ui.common.StatRow
import com.packetbastion.asciidefense.ui.theme.Palette

/**
 * Tap a deployed agent and this opens: its live stats, what the next level buys,
 * and the UPGRADE / SELL / CLOSE actions.
 *
 * Stat rows show "current -> next" so an upgrade is a visible, informed decision
 * rather than a leap of faith.
 */
@Composable
fun AgentManagementPanel(
    agent: Agent,
    crypto: Int,
    onUpgrade: () -> Unit,
    onSell: () -> Unit,
    onCycleTargeting: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val type = agent.type
    val current = type.statsAtLevel(agent.level)
    val maxed = agent.level >= Balance.MAX_AGENT_LEVEL
    val next = if (maxed) null else type.statsAtLevel(agent.level + 1)
    val upgradeCost = if (maxed) 0 else type.upgradeCost(agent.level)
    val canAfford = !maxed && crypto >= upgradeCost

    Column(
        modifier = modifier
            .width(292.dp)
            .background(Palette.Surface.copy(alpha = 0.97f), RoundedCornerShape(6.dp))
            .border(1.dp, Palette.Cyan.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
            .padding(12.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = type.renderedGlyph(agent.level),
                style = MaterialTheme.typography.headlineMedium,
                color = Palette.Cyan
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = type.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = Palette.TextPrimary
                )
                Text(
                    text = if (maxed) "LEVEL ${agent.level} · MAX" else "LEVEL ${agent.level}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (maxed) Palette.Crypto else Palette.Green
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        LevelTrack(agent.level)
        AsciiRule(color = Palette.CyanDim)
        Spacer(Modifier.height(6.dp))

        UpgradeStatRow("DAMAGE", format(current.damage), next?.let { format(it.damage) })
        UpgradeStatRow("RATE", "${format(current.fireRate)}/sec", next?.let { "${format(it.fireRate)}/sec" })
        UpgradeStatRow("RANGE", current.range.toInt().toString(), next?.let { it.range.toInt().toString() })

        Spacer(Modifier.height(6.dp))
        AsciiRule(color = Palette.Divider)
        Spacer(Modifier.height(6.dp))

        Text(
            text = "SPECIAL — ${type.abilityName}",
            style = MaterialTheme.typography.labelMedium,
            color = Palette.Green
        )
        Text(
            text = type.abilitySummary,
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextSecondary
        )

        if (type.allowsTargetingModes) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "TARGETING",
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.TextMuted
                    )
                    Text(
                        text = agent.targetingMode.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = Palette.Purple
                    )
                }
                CompactButton(
                    text = "CHANGE",
                    onClick = onCycleTargeting,
                    accent = Palette.Purple
                )
            }
            Text(
                text = agent.targetingMode.description,
                style = MaterialTheme.typography.labelSmall,
                color = Palette.TextMuted
            )
        }

        Spacer(Modifier.height(8.dp))
        AsciiRule(color = Palette.Divider)
        Spacer(Modifier.height(6.dp))

        StatRow("PACKETS DESTROYED", agent.lifetimeKills.toString(), valueColor = Palette.Cyan)
        StatRow("DAMAGE DEALT", agent.lifetimeDamage.toInt().toString(), valueColor = Palette.Cyan)

        if (agent.damageBuff > 1f) {
            StatRow(
                "ARCHITECT UPLINK",
                "+${((agent.damageBuff - 1f) * 100).toInt()}% DMG",
                valueColor = Palette.Purple
            )
        }

        Spacer(Modifier.height(10.dp))

        if (maxed) {
            Text(
                text = "MAXIMUM LEVEL REACHED",
                style = MaterialTheme.typography.labelMedium,
                color = Palette.Crypto
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "UPGRADE COST",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextSecondary
                )
                Text(
                    text = "◇ $upgradeCost",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (canAfford) Palette.Crypto else Palette.Red
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactButton(
                text = if (maxed) "MAX" else "UPGRADE",
                onClick = onUpgrade,
                enabled = canAfford,
                accent = Palette.Green,
                modifier = Modifier.weight(1f)
            )
            CompactButton(
                text = "SELL\n◇ ${type.sellValue(agent.level)}",
                onClick = onSell,
                accent = Palette.Orange,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(8.dp))

        CompactButton(
            text = "CLOSE",
            onClick = onClose,
            accent = Palette.TextSecondary,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** `[####------]` level track so progress toward level 10 is always visible. */
@Composable
private fun LevelTrack(level: Int) {
    val filled = level.coerceIn(0, Balance.MAX_AGENT_LEVEL)
    Text(
        text = "[" + "#".repeat(filled) + "-".repeat(Balance.MAX_AGENT_LEVEL - filled) + "] $filled/${Balance.MAX_AGENT_LEVEL}",
        style = MaterialTheme.typography.bodyMedium,
        color = Palette.Green
    )
}

@Composable
private fun UpgradeStatRow(label: String, current: String, next: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.TextSecondary
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = current,
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextPrimary
            )
            if (next != null && next != current) {
                Text(
                    text = "  →  ",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextMuted
                )
                Text(
                    text = next,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Green
                )
            }
        }
    }
}
