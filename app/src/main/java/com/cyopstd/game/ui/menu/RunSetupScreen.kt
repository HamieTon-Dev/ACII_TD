package com.cyopstd.game.ui.menu

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.cyopstd.game.core.GameMap
import com.cyopstd.game.core.GameMode
import com.cyopstd.game.core.Maps
import com.cyopstd.game.save.PlayerStats
import com.cyopstd.game.ui.common.BastionButton
import com.cyopstd.game.ui.common.Caption
import com.cyopstd.game.ui.common.ScreenScaffold
import com.cyopstd.game.ui.common.TerminalPanel
import com.cyopstd.game.ui.theme.Palette

/**
 * Level and difficulty, chosen after NEW RUN (♡7, owner, 2026-09-30): they
 * used to crowd the main menu. A level list, a difficulty drop-down, START.
 * Locked levels and difficulties are shown greyed and say what unlocks them
 * when tapped, like every other locked thing in the game.
 */
@Composable
fun RunSetupScreen(
    stats: PlayerStats,
    availableModes: List<GameMode>,
    selectedMode: GameMode,
    availableMaps: List<GameMap>,
    selectedMap: GameMap,
    backgroundAnimation: Boolean,
    onSelectMode: (GameMode) -> Unit,
    onSelectMap: (GameMap) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit
) {
    var lockedNote by remember { mutableStateOf<String?>(null) }
    ScreenScaffold(
        title = "NEW RUN",
        subtitle = "Choose a level and a difficulty",
        onBack = onBack,
        backgroundAnimation = backgroundAnimation
    ) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(
                Modifier
                    .weight(1.3f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
            ) {
                TerminalPanel(title = "LEVEL", accent = Palette.Cyan) {
                    for (map in Maps.all) {
                        val unlocked = map in availableMaps
                        MapRow(
                            map = map,
                            selected = map == selectedMap,
                            unlocked = unlocked,
                            best = map.bestTowardUnlock(
                                bestWaveOnMode = { mode ->
                                    when (mode) {
                                        GameMode.HACK_AI -> stats.highestWaveHackAi
                                        GameMode.STANDARD -> stats.highestWave
                                    }
                                },
                                bestWaveOnMap = { id -> stats.highestWaveByMap[id] ?: 0 }
                            ).let { if (it == Int.MAX_VALUE) 0 else it },
                            onClick = {
                                if (unlocked) {
                                    lockedNote = null
                                    onSelectMap(map)
                                } else {
                                    lockedNote = "${map.displayName} is locked: ${map.unlockRequirement}."
                                }
                            }
                        )
                    }
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
            ) {
                TerminalPanel(title = "DIFFICULTY", accent = Palette.Red) {
                    DifficultyDropdown(
                        selected = selectedMode,
                        available = availableModes,
                        highestWave = stats.highestWave,
                        onSelect = { lockedNote = null; onSelectMode(it) },
                        onLocked = { lockedNote = it }
                    )
                }
                Spacer(Modifier.height(10.dp))
                lockedNote?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.Orange,
                        modifier = Modifier.testTag("locked-note"))
                    Spacer(Modifier.height(10.dp))
                }
                Caption(
                    "${selectedMap.displayName} · ${selectedMode.runName}" +
                        "  ·  best ${stats.bestWave(selectedMap.id, selectedMode.id)}"
                )
                Spacer(Modifier.height(8.dp))
                BastionButton(
                    text = "START",
                    leadingGlyph = "▶",
                    accent = Palette.Green,
                    onClick = onStart
                )
            }
        }
    }
}

@Composable
private fun DifficultyDropdown(
    selected: GameMode,
    available: List<GameMode>,
    highestWave: Int,
    onSelect: (GameMode) -> Unit,
    onLocked: (String) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("difficulty-dropdown")
                .background(Palette.SurfaceRaised, RoundedCornerShape(4.dp))
                .border(1.dp, Palette.Green.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                .clickable(role = Role.DropdownList) { open = true }
                .padding(horizontal = 10.dp, vertical = 10.dp)
        ) {
            Text(selected.runName, style = MaterialTheme.typography.titleSmall,
                color = Palette.TextPrimary, modifier = Modifier.weight(1f))
            Text("▼", style = MaterialTheme.typography.labelMedium, color = Palette.Green)
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.background(Palette.Surface)
        ) {
            for (mode in GameMode.entries) {
                val unlocked = mode in available
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                mode.runName,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (unlocked) Palette.TextPrimary else Palette.TextMuted
                            )
                            Text(
                                if (unlocked) modeSummary(mode)
                                else "LOCKED · clear wave ${mode.unlockAtWave} (best: $highestWave)",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (unlocked) Palette.TextSecondary else Palette.TextMuted
                            )
                        }
                    },
                    onClick = {
                        open = false
                        if (unlocked) onSelect(mode)
                        else onLocked("${mode.runName} is locked: clear wave ${mode.unlockAtWave} to unlock it.")
                    }
                )
            }
        }
    }
}

private fun modeSummary(mode: GameMode): String = when (mode) {
    GameMode.STANDARD -> "The standard curve."
    else -> "Tougher threats, closer together, less integrity. Richer rewards."
}

/**
 * One level in the list. Shown locked rather than hidden: a player aims at
 * what they can see.
 */
@Composable
private fun MapRow(
    map: GameMap,
    selected: Boolean,
    unlocked: Boolean,
    best: Int,
    onClick: () -> Unit
) {
    val accent = when {
        !unlocked -> Palette.TextMuted
        selected -> Palette.Green
        else -> Palette.CyanDim
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .testTag("level-${map.id}")
            .background(if (selected) Palette.SurfaceRaised else Color.Transparent, RoundedCornerShape(4.dp))
            .border(BorderStroke(1.dp, accent.copy(alpha = if (selected) 0.8f else 0.3f)), RoundedCornerShape(4.dp))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (selected) "[*]" else if (unlocked) "[ ]" else "[X]",
            style = MaterialTheme.typography.labelMedium,
            color = accent
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = map.displayName,
                style = MaterialTheme.typography.titleSmall,
                color = if (unlocked) Palette.TextPrimary else Palette.TextMuted
            )
            Caption(if (unlocked) map.tagline else "LOCKED · ${map.unlockRequirement} (best: $best)")
        }
    }
}
