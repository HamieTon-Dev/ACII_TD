package com.packetbastion.asciidefense.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.packetbastion.asciidefense.model.AgentType
import com.packetbastion.asciidefense.save.PlayerStats
import com.packetbastion.asciidefense.ui.common.AsciiRule
import com.packetbastion.asciidefense.ui.common.Caption
import com.packetbastion.asciidefense.ui.common.ScreenScaffold
import com.packetbastion.asciidefense.ui.common.StatRow
import com.packetbastion.asciidefense.ui.common.TerminalPanel
import com.packetbastion.asciidefense.ui.theme.Palette

/** Lifetime record, kept on the device and nowhere else. */
@Composable
fun StatisticsScreen(
    stats: PlayerStats,
    backgroundAnimation: Boolean,
    onBack: () -> Unit
) {
    val favorite = stats.favoriteAgent?.let { AgentType.fromNameSafe(it) }

    ScreenScaffold(
        title = "STATISTICS",
        subtitle = "Stored locally on this device · nothing is uploaded",
        onBack = onBack,
        backgroundAnimation = backgroundAnimation
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
            ) {
                TerminalPanel(title = "DEFENCE RECORD", accent = Palette.Green) {
                    StatRow("HIGHEST WAVE", stats.highestWave.toString(), valueColor = Palette.Crypto)
                    StatRow("TOTAL GAMES PLAYED", stats.totalGamesPlayed.toString())
                    StatRow(
                        "TOTAL PACKETS BLOCKED",
                        stats.totalPacketsBlocked.toString(),
                        valueColor = Palette.Cyan
                    )
                    StatRow(
                        "TOTAL BOSSES DEFEATED",
                        stats.totalBossesDefeated.toString(),
                        valueColor = Palette.Red
                    )
                    StatRow(
                        "TOTAL SERVER DAMAGE TAKEN",
                        stats.totalServerDamageTaken.toString(),
                        valueColor = Palette.Orange
                    )
                }

                Spacer(Modifier.height(12.dp))

                TerminalPanel(title = "ECONOMY", accent = Palette.Crypto) {
                    StatRow(
                        "TOTAL CRYPTO EARNED",
                        "◇ ${stats.totalCryptoEarned}",
                        valueColor = Palette.Crypto
                    )
                    StatRow("TOTAL AGENTS DEPLOYED", stats.totalAgentsDeployed.toString())
                    StatRow("TOTAL AGENT UPGRADES", stats.totalAgentUpgrades.toString())
                    StatRow(
                        "FAVOURITE AGENT",
                        favorite?.displayName ?: "—",
                        valueColor = Palette.Cyan
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
            ) {
                TerminalPanel(title = "AGENT DEPLOYMENT BREAKDOWN", accent = Palette.Cyan) {
                    if (stats.deploymentsByAgent.isEmpty()) {
                        Caption("No deployments recorded yet. Play a run to populate this.")
                    } else {
                        val maxCount = stats.deploymentsByAgent.values.maxOrNull() ?: 1
                        stats.deploymentsByAgent
                            .entries
                            .sortedByDescending { it.value }
                            .forEach { (name, count) ->
                                val type = AgentType.fromNameSafe(name)
                                DeploymentBar(
                                    label = type?.displayName ?: name,
                                    glyph = type?.let { "[${it.glyph}]" } ?: "[?]",
                                    count = count,
                                    maxCount = maxCount
                                )
                            }
                    }
                }

                Spacer(Modifier.height(12.dp))

                TerminalPanel(title = "SESSION NOTES", accent = Palette.Purple) {
                    Caption(
                        "Statistics accumulate across every run, including runs you " +
                            "abandon from the pause menu. Resetting progress in " +
                            "SETTINGS clears all of it."
                    )
                }
            }
        }
    }
}

@Composable
private fun DeploymentBar(label: String, glyph: String, count: Int, maxCount: Int) {
    val cells = 18
    val filled = if (maxCount <= 0) 0 else (count.toFloat() / maxCount * cells).toInt().coerceIn(0, cells)
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "$glyph $label",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextPrimary
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.Cyan
            )
        }
        Text(
            text = "[" + "#".repeat(filled) + "-".repeat(cells - filled) + "]",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.CyanDim
        )
        Spacer(Modifier.height(4.dp))
    }
}
