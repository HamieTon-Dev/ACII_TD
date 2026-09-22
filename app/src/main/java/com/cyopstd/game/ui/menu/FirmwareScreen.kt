package com.cyopstd.game.ui.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cyopstd.game.core.Balance
import com.cyopstd.game.ui.common.AsciiRule
import com.cyopstd.game.ui.common.Caption
import com.cyopstd.game.ui.common.CompactButton
import com.cyopstd.game.ui.common.ScreenScaffold
import com.cyopstd.game.ui.common.StatRow
import com.cyopstd.game.ui.common.TerminalPanel
import com.cyopstd.game.ui.theme.Palette

/**
 * CORE FIRMWARE — the between-runs progression screen.
 *
 * € BUDGET is banked at every tenth wave and survives the run that earned it.
 * Spending it here raises a permanent damage multiplier that applies to every
 * agent in every future match, which is what lets a player who keeps dying at
 * wave 30 eventually stop dying at wave 30.
 */
@Composable
fun FirmwareScreen(
    budget: Long,
    firmwareLevel: Int,
    lifetimeBudgetEarned: Long,
    backgroundAnimation: Boolean,
    onBuy: (levels: Int) -> Unit,
    onBack: () -> Unit
) {
    val multiplier = Balance.firmwareDamageMultiplier(firmwareLevel)
    val nextCost = Balance.firmwareCost(firmwareLevel)
    val affordable = Balance.firmwareLevelsAffordable(firmwareLevel, budget)
    val maxed = firmwareLevel >= Balance.MAX_FIRMWARE_LEVEL
    val canAffordOne = !maxed && budget >= nextCost

    ScreenScaffold(
        title = "CORE FIRMWARE",
        subtitle = "Permanent damage upgrades · applies to every agent, every match",
        onBack = onBack,
        backgroundAnimation = backgroundAnimation
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ---- Left: current state -----------------------------------
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
            ) {
                TerminalPanel(title = "€ BUDGET", accent = Palette.Cyan) {
                    Text(
                        text = "€ $budget",
                        style = MaterialTheme.typography.displayMedium,
                        color = Palette.Cyan
                    )
                    Spacer(Modifier.height(4.dp))
                    Caption(
                        "Banked at every tenth wave. The deeper a run goes, the " +
                            "more it pays — the award grows with the square of the " +
                            "milestone, so one deep run beats five shallow ones."
                    )
                    Spacer(Modifier.height(8.dp))
                    StatRow(
                        "LIFETIME EARNED",
                        "€ $lifetimeBudgetEarned",
                        valueColor = Palette.TextSecondary
                    )
                }

                Spacer(Modifier.height(12.dp))

                TerminalPanel(title = "INSTALLED FIRMWARE", accent = Palette.Green) {
                    StatRow(
                        "LEVEL",
                        "$firmwareLevel / ${Balance.MAX_FIRMWARE_LEVEL}",
                        valueColor = Palette.Green
                    )
                    StatRow(
                        "AGENT DAMAGE",
                        "×${"%.2f".format(multiplier)}",
                        valueColor = Palette.Crypto
                    )
                    StatRow(
                        "PER LEVEL",
                        "+${(Balance.FIRMWARE_DAMAGE_PER_LEVEL * 100).toInt()}%",
                        valueColor = Palette.TextSecondary
                    )
                    AsciiRule(color = Palette.Divider)
                    Spacer(Modifier.height(6.dp))
                    FirmwareBar(firmwareLevel)
                }
            }

            // ---- Right: spend it ---------------------------------------
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
            ) {
                TerminalPanel(title = "INSTALL", accent = Palette.Crypto) {
                    if (maxed) {
                        Text(
                            text = "FIRMWARE FULLY INSTALLED",
                            style = MaterialTheme.typography.titleMedium,
                            color = Palette.Crypto
                        )
                    } else {
                        StatRow(
                            "NEXT LEVEL COSTS",
                            "€ $nextCost",
                            valueColor = if (canAffordOne) Palette.Crypto else Palette.Red
                        )
                        StatRow(
                            "AFFORDABLE NOW",
                            "$affordable level${if (affordable == 1) "" else "s"}",
                            valueColor = if (affordable > 0) Palette.Green else Palette.TextMuted
                        )

                        Spacer(Modifier.height(12.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CompactButton(
                                text = "+1",
                                onClick = { onBuy(1) },
                                enabled = canAffordOne,
                                accent = Palette.Green,
                                modifier = Modifier.weight(1f)
                            )
                            CompactButton(
                                text = "+10",
                                onClick = { onBuy(10) },
                                enabled = affordable >= 1,
                                accent = Palette.Green,
                                modifier = Modifier.weight(1f)
                            )
                            CompactButton(
                                text = "+100",
                                onClick = { onBuy(100) },
                                enabled = affordable >= 1,
                                accent = Palette.Green,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        CompactButton(
                            text = if (affordable > 0) "INSTALL MAX  (+$affordable)" else "INSTALL MAX",
                            onClick = { onBuy(affordable) },
                            enabled = affordable > 0,
                            accent = Palette.Crypto,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(8.dp))
                        Caption(
                            "Each level costs slightly more than the last, so the " +
                                "multiplier keeps climbing but never runs away. " +
                                "There is no cap you will realistically reach."
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                TerminalPanel(title = "WHAT THIS CHANGES", accent = Palette.Purple) {
                    Text(
                        text = "Firmware multiplies the damage of every agent you " +
                            "deploy, in every match from now on. It is applied " +
                            "before armour and before the counter table, so it " +
                            "helps a Cryptographer against encryption exactly as " +
                            "much as it helps a Firewall against plain traffic.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.TextSecondary
                    )
                    Spacer(Modifier.height(8.dp))
                    Caption(
                        "It does not affect enemy health, rewards or wave " +
                            "composition — only your side of the fight."
                    )
                }

                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

/** A coarse progress bar; the real range is far too large to draw literally. */
@Composable
private fun FirmwareBar(level: Int) {
    // Log-ish banding: each band is a tenfold jump, so early progress is visible.
    val band = when {
        level >= 10_000 -> 5
        level >= 1_000 -> 4
        level >= 100 -> 3
        level >= 10 -> 2
        level >= 1 -> 1
        else -> 0
    }
    val bandLabel = when (band) {
        0 -> "UNINSTALLED"
        1 -> "BASELINE"
        2 -> "HARDENED"
        3 -> "RESILIENT"
        4 -> "FORTIFIED"
        else -> "ABSOLUTE"
    }
    val cells = 5
    Text(
        text = "[" + "#".repeat(band) + "-".repeat(cells - band) + "]  $bandLabel",
        style = MaterialTheme.typography.bodyMedium,
        color = Palette.Purple,
        textAlign = TextAlign.Start
    )
}
