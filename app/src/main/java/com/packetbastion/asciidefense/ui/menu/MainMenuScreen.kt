package com.packetbastion.asciidefense.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.packetbastion.asciidefense.save.PlayerStats
import com.packetbastion.asciidefense.ui.common.AsciiBackdrop
import com.packetbastion.asciidefense.ui.common.AsciiRule
import com.packetbastion.asciidefense.ui.common.BastionButton
import com.packetbastion.asciidefense.ui.common.Caption
import com.packetbastion.asciidefense.ui.common.StatRow
import com.packetbastion.asciidefense.ui.common.TerminalPanel
import com.packetbastion.asciidefense.ui.theme.Palette

/**
 * The main menu. Landscape-first: title and status on the left, the action
 * column on the right, both independently scrollable so the layout survives
 * short screens and large system font scales.
 */
@Composable
fun MainMenuScreen(
    hasSavedRun: Boolean,
    stats: PlayerStats,
    backgroundAnimation: Boolean,
    onPlay: () -> Unit,
    onContinue: () -> Unit,
    onAgents: () -> Unit,
    onCodex: () -> Unit,
    onStatistics: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
    onExit: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.Background)
    ) {
        AsciiBackdrop(
            modifier = Modifier.fillMaxSize(),
            enabled = backgroundAnimation,
            density = 40
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            // ---- Left: identity + at-a-glance status -----------------------
            Column(
                modifier = Modifier
                    .weight(1.15f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "PACKET BASTION",
                    style = MaterialTheme.typography.displayMedium,
                    color = Palette.Cyan
                )
                Text(
                    text = "ASCII DEFENSE",
                    style = MaterialTheme.typography.titleLarge,
                    color = Palette.Green
                )

                Spacer(Modifier.height(10.dp))
                AsciiRule(color = Palette.CyanDim)
                Spacer(Modifier.height(14.dp))

                Text(
                    text = TITLE_ART,
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.CyanDim
                )

                Spacer(Modifier.height(16.dp))

                TerminalPanel(title = "NETWORK STATUS", accent = Palette.Green) {
                    StatRow(
                        "BEST WAVE",
                        stats.highestWave.toString(),
                        valueColor = Palette.Crypto
                    )
                    StatRow(
                        "PACKETS BLOCKED",
                        stats.totalPacketsBlocked.toString(),
                        valueColor = Palette.Cyan
                    )
                    StatRow(
                        "BOSSES DEFEATED",
                        stats.totalBossesDefeated.toString(),
                        valueColor = Palette.Red
                    )
                    StatRow(
                        "SAVED SESSION",
                        if (hasSavedRun) "PRESENT" else "NONE",
                        valueColor = if (hasSavedRun) Palette.Green else Palette.TextMuted
                    )
                }

                Spacer(Modifier.height(12.dp))
                Caption("OFFLINE · NO ACCOUNT · NO ADS · NO PURCHASES")
            }

            Spacer(Modifier.width(24.dp))

            // ---- Right: the actions ---------------------------------------
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center
            ) {
                BastionButton(
                    text = "PLAY",
                    subtitle = "Start a new defence run",
                    leadingGlyph = "[>]",
                    accent = Palette.Green,
                    onClick = onPlay
                )
                Spacer(Modifier.height(10.dp))
                BastionButton(
                    text = "CONTINUE",
                    subtitle = if (hasSavedRun) "Resume your saved session" else "No saved session",
                    leadingGlyph = "[=]",
                    enabled = hasSavedRun,
                    onClick = onContinue
                )
                Spacer(Modifier.height(10.dp))
                BastionButton(
                    text = "AGENTS",
                    subtitle = "Review your cyber agent roster",
                    leadingGlyph = "[@]",
                    onClick = onAgents
                )
                Spacer(Modifier.height(10.dp))
                BastionButton(
                    text = "CODEX",
                    subtitle = "Threats, agents and network terms",
                    leadingGlyph = "[?]",
                    accent = Palette.Purple,
                    onClick = onCodex
                )
                Spacer(Modifier.height(10.dp))
                BastionButton(
                    text = "STATISTICS",
                    subtitle = "Lifetime defence record",
                    leadingGlyph = "[#]",
                    onClick = onStatistics
                )
                Spacer(Modifier.height(10.dp))
                BastionButton(
                    text = "SETTINGS",
                    subtitle = "Audio, haptics, visuals",
                    leadingGlyph = "[*]",
                    onClick = onSettings
                )
                Spacer(Modifier.height(10.dp))
                BastionButton(
                    text = "ABOUT",
                    subtitle = "Version and credits",
                    leadingGlyph = "[i]",
                    onClick = onAbout
                )
                Spacer(Modifier.height(10.dp))
                BastionButton(
                    text = "EXIT",
                    subtitle = "Close the application",
                    leadingGlyph = "[X]",
                    accent = Palette.Red,
                    onClick = onExit
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

private val TITLE_ART = """
    >>> ---- LANE 1 ---------------->  +==============+
    >>> ---- LANE 2 ---------------->  |  CORE-SERVER |
    >>> ---- LANE 3 ---------------->  |  . . . . . . |
                                       +==============+
""".trimIndent()
