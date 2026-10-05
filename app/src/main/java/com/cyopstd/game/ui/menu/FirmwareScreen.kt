package com.cyopstd.game.ui.menu

import com.cyopstd.game.i18n.tr

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cyopstd.game.core.Balance
import com.cyopstd.game.ui.common.AsciiRule
import com.cyopstd.game.ui.common.FirmwareFormat
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
    onBack: () -> Unit,
    /** Show the first-visit explainer (owner, 2026-09-26). */
    showGuide: Boolean = false,
    onGuideDone: () -> Unit = {},
    /** AGENT FIRMWARE owned, and how to buy more (owner, 2026-09-28). */
    agentFirmware: Map<com.cyopstd.game.model.AgentType, com.cyopstd.game.model.AgentFirmware> = emptyMap(),
    unlockedAgents: Set<String> = emptySet(),
    onBuyAgent: (com.cyopstd.game.model.AgentType, com.cyopstd.game.model.FirmwareStat, Int) -> Unit = { _, _, _ -> }
) {
    var agentTab by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val nextCost = Balance.firmwareCost(firmwareLevel)
    val affordable = Balance.firmwareLevelsAffordable(firmwareLevel, budget)
    val maxed = firmwareLevel >= Balance.MAX_FIRMWARE_LEVEL
    val canAffordOne = !maxed && budget >= nextCost

    Box(Modifier.fillMaxSize()) {
    ScreenScaffold(
        title = tr("CORE FIRMWARE"),
        subtitle = tr("Permanent upgrades · every match from now on"),
        onBack = onBack,
        backgroundAnimation = backgroundAnimation
    ) {
        Column(Modifier.fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactButton(
                text = tr("CORE"),
                onClick = { agentTab = false },
                selected = !agentTab,
                accent = Palette.Crypto
            )
            CompactButton(
                text = tr("PER AGENT"),
                onClick = { agentTab = true },
                selected = agentTab,
                accent = Palette.Green,
                modifier = Modifier.then(Modifier)
            )
        }
        Spacer(Modifier.height(8.dp))
        if (agentTab) {
            AgentFirmwarePanel(
                budget = budget,
                agentFirmware = agentFirmware,
                unlockedAgents = unlockedAgents,
                onBuy = onBuyAgent
            )
        } else
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
                TerminalPanel(title = tr("€ BUDGET"), accent = Palette.Cyan) {
                    Text(
                        text = "€ $budget",
                        style = MaterialTheme.typography.displayMedium,
                        color = Palette.Cyan
                    )
                    Spacer(Modifier.height(4.dp))
                    Caption(
                        tr("Banked at every tenth wave. The deeper a run goes, the " +
                            "more it pays — the award grows with the square of the " +
                            "milestone, so one deep run beats five shallow ones.")
                    )
                    Spacer(Modifier.height(8.dp))
                    StatRow(
                        tr("LIFETIME EARNED"),
                        "€ $lifetimeBudgetEarned",
                        valueColor = Palette.TextSecondary
                    )
                }

                Spacer(Modifier.height(12.dp))

                TerminalPanel(title = tr("INSTALLED FIRMWARE"), accent = Palette.Green) {
                    StatRow(
                        tr("LEVEL"),
                        "$firmwareLevel / ${Balance.MAX_FIRMWARE_LEVEL}",
                        valueColor = Palette.Green
                    )
                    StatRow(
                        tr("AGENT DAMAGE"),
                        FirmwareFormat.multiplier(firmwareLevel),
                        valueColor = Palette.Crypto
                    )
                    StatRow(
                        tr("CRYPTO EARNED"),
                        "\u00D7%.2f".format(Balance.firmwareCryptoMultiplier(firmwareLevel)),
                        valueColor = Palette.Crypto
                    )
                    StatRow(
                        tr("PER LEVEL"),
                        FirmwareFormat.perLevel(),
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
                TerminalPanel(title = tr("INSTALL"), accent = Palette.Crypto) {
                    if (maxed) {
                        Text(
                            text = tr("FIRMWARE FULLY INSTALLED"),
                            style = MaterialTheme.typography.titleMedium,
                            color = Palette.Crypto
                        )
                    } else {
                        StatRow(
                            tr("NEXT LEVEL COSTS"),
                            "€ $nextCost",
                            valueColor = if (canAffordOne) Palette.Crypto else Palette.Red
                        )
                        StatRow(
                            tr("TAKES YOU TO"),
                            FirmwareFormat.multiplier(firmwareLevel + 1),
                            valueColor = if (canAffordOne) Palette.Crypto else Palette.TextMuted
                        )
                        StatRow(
                            tr("AFFORDABLE NOW"),
                            if (affordable > 0) {
                                (if (affordable == 1) tr("1 level") else tr("{0} levels", affordable)) +
                                    "  ${FirmwareFormat.gain(affordable)}"
                            } else {
                                tr("0 levels")
                            },
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
                            text = if (affordable > 0) tr("INSTALL MAX  (+{0})", affordable) else tr("INSTALL MAX"),
                            onClick = { onBuy(affordable) },
                            enabled = affordable > 0,
                            accent = Palette.Crypto,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(8.dp))
                        Caption(
                            tr("Each level costs slightly more than the last, so the " +
                                "multiplier keeps climbing but never runs away. " +
                                "There is no cap you will realistically reach.")
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                TerminalPanel(title = tr("WHAT THIS CHANGES"), accent = Palette.Purple) {
                    Text(
                        text = tr("Firmware multiplies the damage of every agent you " +
                            "deploy, in every match from now on. It is applied " +
                            "before armour and before the counter table, so it " +
                            "helps a Cryptographer against encryption exactly as " +
                            "much as it helps a Firewall against plain traffic."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.TextSecondary
                    )
                    Spacer(Modifier.height(8.dp))
                    Caption(
                        tr(
                            "It also raises the \u25C7 crypto every run pays out, " +
                                "+{0}% per level up to double. It does not affect enemy health " +
                                "or wave composition.",
                            "%.2f".format(Balance.FIRMWARE_CRYPTO_PER_LEVEL * 100)
                        )
                    )
                }

                Spacer(Modifier.height(12.dp))
            }
        }
        }
    }

    if (showGuide) {
        com.cyopstd.game.ui.common.GuideCard(
            steps = com.cyopstd.game.ui.common.FirmwareGuide.steps,
            onFinished = onGuideDone,
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.BottomEnd)
                .padding(16.dp)
        )
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
        0 -> tr("UNINSTALLED")
        1 -> tr("BASELINE")
        2 -> tr("HARDENED")
        3 -> tr("RESILIENT")
        4 -> tr("FORTIFIED")
        else -> tr("ABSOLUTE")
    }
    val cells = 5
    Text(
        text = "[" + "#".repeat(band) + "-".repeat(cells - band) + "]  $bandLabel",
        style = MaterialTheme.typography.bodyMedium,
        color = Palette.Purple,
        textAlign = TextAlign.Start
    )
}
