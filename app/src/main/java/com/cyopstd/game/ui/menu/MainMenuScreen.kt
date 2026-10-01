package com.cyopstd.game.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cyopstd.game.core.GameMap
import com.cyopstd.game.core.GameMode
import com.cyopstd.game.core.Maps
import com.cyopstd.game.save.PlayerStats
import com.cyopstd.game.ui.common.AsciiBackdrop
import com.cyopstd.game.ui.common.AsciiRule
import com.cyopstd.game.ui.common.FirmwareFormat
import com.cyopstd.game.ui.common.BastionButton
import com.cyopstd.game.ui.common.Caption
import com.cyopstd.game.ui.common.StatRow
import com.cyopstd.game.ui.common.TerminalPanel
import com.cyopstd.game.ui.theme.Palette

/**
 * The main menu. Landscape-first: title and status on the left, the action
 * column on the right, both independently scrollable so the layout survives
 * short screens and large system font scales.
 */
@Composable
fun MainMenuScreen(
    hasSavedRun: Boolean,
    stats: PlayerStats,
    budget: Long,
    firmwareLevel: Int,
    adsRemoved: Boolean,
    backgroundAnimation: Boolean,
    /**
     * NEW RUN, after the "start a fresh run?" confirmation when there is a
     * save. Opens level and difficulty selection; the menu itself no longer
     * holds them (♡7).
     */
    onPlay: () -> Unit,
    onContinue: () -> Unit,
    onAgents: () -> Unit,
    onFirmware: () -> Unit,
    onCodex: () -> Unit,
    onStore: () -> Unit,
    onLoadout: () -> Unit,
    onPlayAccount: () -> Unit,
    onLeaderboard: () -> Unit,
    onStatistics: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
    onExit: () -> Unit,
    /** Show the one-time menu tour (owner, 2026-09-26). */
    showGuide: Boolean = false,
    onGuideDone: () -> Unit = {}
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

        var confirmNewRun by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

        // ♡7 (owner, 2026-09-30): less crowded. No description under every
        // button; level and difficulty are chosen after NEW RUN, not here;
        // everything scales with the width it is given.
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // ---- Left: identity + at-a-glance status -----------------------
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "CyOps TD",
                    style = MaterialTheme.typography.displayMedium,
                    color = Palette.Cyan
                )
                Text(
                    text = "ASCII CYBER DEFENSE",
                    style = MaterialTheme.typography.titleLarge,
                    color = Palette.Green
                )
                Spacer(Modifier.height(8.dp))
                AsciiRule(color = Palette.CyanDim)
                Spacer(Modifier.height(10.dp))

                // Never wraps: a wrapped line breaks the box apart. On a column
                // too narrow for it at the normal size, the text shrinks to fit.
                androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val base = MaterialTheme.typography.bodySmall
                    val widest = TITLE_ART.lines().maxOf { it.length }
                    val fitting = with(androidx.compose.ui.platform.LocalDensity.current) {
                        (maxWidth.toPx() / (widest * 0.62f)).toSp()
                    }
                    Text(
                        text = TITLE_ART,
                        style = base.copy(
                            fontSize = if (fitting < base.fontSize) fitting else base.fontSize,
                            lineHeight = if (fitting < base.fontSize) fitting * 1.3f else base.lineHeight
                        ),
                        color = Palette.CyanDim,
                        softWrap = false,
                        maxLines = TITLE_ART.lines().size
                    )
                }

                Spacer(Modifier.height(12.dp))

                TerminalPanel(title = "NETWORK STATUS", accent = Palette.Green) {
                    StatRow("BEST WAVE", stats.highestWave.toString(), valueColor = Palette.Crypto)
                    StatRow("BOSSES DEFEATED", stats.totalBossesDefeated.toString(), valueColor = Palette.Red)
                    StatRow("\u20AC BUDGET", budget.toString(), valueColor = Palette.Cyan)
                    StatRow(
                        "CORE FIRMWARE",
                        "LV $firmwareLevel  ${FirmwareFormat.multiplier(firmwareLevel)} DMG",
                        valueColor = Palette.Purple
                    )
                }
                Spacer(Modifier.height(8.dp))
                Caption(playsOfflineCaption(adsRemoved))
            }

            Spacer(Modifier.width(20.dp))

            // ---- Right: the actions ---------------------------------------
            androidx.compose.foundation.layout.BoxWithConstraints(
                modifier = Modifier
                    .weight(1.15f)
                    .fillMaxHeight()
            ) {
                val columns = if (maxWidth >= 330.dp) 4 else 3
                val gap = 8.dp
                val tile = (maxWidth - gap * (columns - 1)) / columns
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        MenuTile(
                            icon = "\u25B6",
                            label = "CONTINUE",
                            accent = Palette.Green,
                            enabled = hasSavedRun,
                            large = true,
                            onClick = onContinue,
                            modifier = Modifier.weight(1f)
                        )
                        MenuTile(
                            icon = "+",
                            label = "NEW RUN",
                            accent = if (hasSavedRun) Palette.Cyan else Palette.Green,
                            large = true,
                            onClick = { if (hasSavedRun) confirmNewRun = true else onPlay() },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(gap))
                    val tiles = listOf(
                        MenuEntry("[@]", "AGENTS", Palette.Cyan, onAgents),
                        MenuEntry("\u20AC", "FIRMWARE", Palette.Crypto, onFirmware),
                        MenuEntry("$", "STORE", Palette.Green, onStore),
                        MenuEntry("\u25C8", "LOADOUT", Palette.Purple, onLoadout),
                        MenuEntry("G", "GOOGLE PLAY", Palette.Blue, onPlayAccount),
                        MenuEntry("\u2261", "LEADERBOARD", Palette.Crypto, onLeaderboard),
                        MenuEntry("?", "CODEX", Palette.Purple, onCodex),
                        MenuEntry("#", "STATISTICS", Palette.Cyan, onStatistics),
                        MenuEntry("*", "SETTINGS", Palette.Cyan, onSettings),
                        MenuEntry("i", "ABOUT", Palette.Cyan, onAbout),
                        MenuEntry("X", "EXIT", Palette.Red, onExit)
                    )
                    for (row in tiles.chunked(columns)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                            for (entry in row) {
                                MenuTile(
                                    icon = entry.icon,
                                    label = entry.label,
                                    accent = entry.accent,
                                    onClick = entry.onClick,
                                    modifier = Modifier.width(tile)
                                )
                            }
                        }
                        Spacer(Modifier.height(gap))
                    }
                }
            }
        }

        if (confirmNewRun) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { confirmNewRun = false },
                containerColor = Palette.Surface,
                title = {
                    Text("NEW RUN", style = MaterialTheme.typography.titleMedium, color = Palette.Cyan)
                },
                text = {
                    Text(
                        "Are you sure you would like to start a fresh run? Your saved session will be replaced.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.TextPrimary
                    )
                },
                confirmButton = {
                    com.cyopstd.game.ui.common.CompactButton(
                        text = "START FRESH",
                        onClick = { confirmNewRun = false; onPlay() },
                        accent = Palette.Green
                    )
                },
                dismissButton = {
                    com.cyopstd.game.ui.common.CompactButton(
                        text = "KEEP MY SAVE",
                        onClick = { confirmNewRun = false },
                        accent = Palette.TextSecondary
                    )
                }
            )
        }

        if (showGuide) {
            com.cyopstd.game.ui.common.GuideCard(
                steps = com.cyopstd.game.ui.common.MenuGuide.steps,
                onFinished = onGuideDone,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            )
        }
    }
}

private class MenuEntry(
    val icon: String,
    val label: String,
    val accent: androidx.compose.ui.graphics.Color,
    val onClick: () -> Unit
)

/**
 * A main-menu button: an icon with a short label under it, and nothing else
 * (owner, 2026-09-30: the descriptions under every button were the clutter).
 * The icons are placeholders in the game's own glyph style until the owner
 * confirms an icon set (♡7).
 */
@Composable
private fun MenuTile(
    icon: String,
    label: String,
    accent: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    large: Boolean = false
) {
    val color = if (enabled) accent else Palette.TextMuted
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .height(if (large) 76.dp else 58.dp)
            .background(Palette.Surface.copy(alpha = 0.85f), androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
            .border(1.dp, color.copy(alpha = if (enabled) 0.7f else 0.3f), androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .padding(4.dp)
    ) {
        Text(
            text = icon,
            style = if (large) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
            color = color,
            maxLines = 1
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = if (large) 11.sp else 9.sp),
            color = if (enabled) Palette.TextPrimary else Palette.TextMuted,
            maxLines = 1,
            softWrap = false,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

/**
 * The one-line promise under the status panel.
 *
 * It only ever claims what is still true for *this* player: the game runs with
 * no network and asks for no account either way, and ads are mentioned only
 * when there are ads to mention.
 */
private fun playsOfflineCaption(adsRemoved: Boolean): String = buildString {
    append("PLAYS OFFLINE \u00B7 NO LOGIN REQUIRED")
    if (adsRemoved) append(" \u00B7 AD-FREE")
}

/**
 * The lanes feeding the server, drawn in text.
 *
 * Each lane arrow lands on a row *inside* the box, and every row inside is
 * centred and exactly as wide as the border, so the right-hand edge lines up
 * (owner, 2026-09-26: the core-server art was misaligned). `MainMenuArtTest`
 * checks both, so an edit that breaks the box fails a test.
 */
internal val TITLE_ART = listOf(
    "                                  +===============+",
    ">>> ---- LANE 1 ----------------> |  CORE-SERVER  |",
    ">>> ---- LANE 2 ----------------> |  . . . . . .  |",
    ">>> ---- LANE 3 ----------------> |  ## ## ## ##  |",
    "                                  +===============+"
).joinToString("\n")
