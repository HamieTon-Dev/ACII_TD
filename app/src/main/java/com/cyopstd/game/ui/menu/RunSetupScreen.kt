package com.cyopstd.game.ui.menu

import com.cyopstd.game.i18n.tr

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.cyopstd.game.R
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
    // The level whose preview is open (owner, 2026-10-02), or null.
    var previewing by remember { mutableStateOf<GameMap?>(null) }
    previewing?.let { map ->
        LevelPreviewDialog(
            map = map,
            number = Maps.all.indexOf(map) + 1,
            unlocked = map in availableMaps,
            onClose = { previewing = null }
        )
    }
    ScreenScaffold(
        title = tr("NEW RUN"),
        subtitle = tr("Choose a level and a difficulty"),
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
                TerminalPanel(title = tr("LEVEL"), accent = Palette.Cyan) {
                    for ((index, map) in Maps.all.withIndex()) {
                        val unlocked = map in availableMaps
                        MapRow(
                            map = map,
                            number = index + 1,
                            selected = map == selectedMap,
                            unlocked = unlocked,
                            best = map.bestTowardUnlock(
                                bestWaveOnMode = { mode -> stats.bestInMode(mode) },
                                bestWaveOnMap = { id -> stats.highestWaveByMap[id] ?: 0 }
                            ).let { if (it == Int.MAX_VALUE) 0 else it },
                            onClick = {
                                if (unlocked) {
                                    lockedNote = null
                                    onSelectMap(map)
                                } else {
                                    lockedNote = tr("{0} is locked: {1}.", map.displayName, map.unlockRequirement)
                                }
                            },
                            onPreview = { previewing = map }
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
                TerminalPanel(title = tr("DIFFICULTY"), accent = Palette.Red) {
                    DifficultyDropdown(
                        selected = selectedMode,
                        available = availableModes,
                        stats = stats,
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
                    tr("{0} · {1}" +
                        "  ·  best {2}", selectedMap.displayName, selectedMode.runName, stats.bestWave(selectedMap.id, selectedMode.id))
                )
                Spacer(Modifier.height(8.dp))
                BastionButton(
                    text = tr("START"),
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
    stats: PlayerStats,
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
                                else tr("LOCKED · {0} (best: {1})", mode.unlockRequirement, bestToward(mode, stats)),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (unlocked) Palette.TextSecondary else Palette.TextMuted
                            )
                        }
                    },
                    onClick = {
                        open = false
                        if (unlocked) onSelect(mode)
                        else onLocked(tr("{0} is locked: {1} to unlock it.", mode.runName, mode.unlockRequirement))
                    }
                )
            }
        }
    }
}

private fun bestToward(mode: GameMode, stats: PlayerStats): Int =
    mode.bestTowardUnlock(stats.highestWave) { map, m -> stats.bestWave(map, m) }

private fun modeSummary(mode: GameMode): String = when (mode) {
    GameMode.STANDARD -> tr("The standard curve.")
    GameMode.HACK_AI -> tr("Tougher threats, closer together, less integrity. Richer rewards.")
    GameMode.KERNEL_MODE -> tr("HACK:AI pushed past its limits. Half the integrity, double the rewards.")
}

/**
 * One level in the list. Shown locked rather than hidden: a player aims at
 * what they can see. An open level leads with its number; a locked one has a
 * lock in that place and the whole row is greyed (owner, 2026-10-01).
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun MapRow(
    map: GameMap,
    number: Int,
    selected: Boolean,
    unlocked: Boolean,
    best: Int,
    onClick: () -> Unit,
    /** Hold the row, or tap its info button: the map and its bosses. */
    onPreview: () -> Unit
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
            .alpha(if (unlocked) 1f else LOCKED_ROW_ALPHA)
            .background(if (selected) Palette.SurfaceRaised else Color.Transparent, RoundedCornerShape(4.dp))
            .border(BorderStroke(1.dp, accent.copy(alpha = if (selected) 0.8f else 0.3f)), RoundedCornerShape(4.dp))
            .combinedClickable(role = Role.RadioButton, onClick = onClick, onLongClick = onPreview)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The badge: the level's number, or a lock where the number would be.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(30.dp)
                .border(1.dp, (if (unlocked) accent else Palette.TextSecondary).copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                .background(if (selected) Palette.Green.copy(alpha = 0.15f) else Color.Transparent, RoundedCornerShape(4.dp))
        ) {
            if (unlocked) {
                Text(
                    text = "$number",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected) Palette.Green else Palette.TextPrimary,
                    modifier = Modifier.testTag("level-number-${map.id}")
                )
            } else {
                androidx.compose.material3.Icon(
                    painter = androidx.compose.ui.res.painterResource(R.drawable.ic_lock),
                    contentDescription = tr("Locked"),
                    // Brighter than the greyed row, so the lock still reads.
                    tint = Palette.TextPrimary,
                    modifier = Modifier
                        .size(16.dp)
                        .testTag("level-lock-${map.id}")
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = map.displayName,
                style = MaterialTheme.typography.titleSmall,
                color = if (unlocked) Palette.TextPrimary else Palette.TextMuted
            )
            Caption(if (unlocked) map.tagline else tr("LOCKED · {0} (best: {1})", map.unlockRequirement, best))
        }
        Spacer(Modifier.width(8.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(30.dp)
                .testTag("level-info-${map.id}")
                .border(1.dp, Palette.CyanDim.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                .clickable(role = Role.Button, onClick = onPreview)
        ) {
            androidx.compose.material3.Icon(
                painter = androidx.compose.ui.res.painterResource(R.drawable.ic_menu_about),
                contentDescription = tr("Preview {0}", map.displayName),
                tint = Palette.Cyan,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/** How strongly a locked level row is greyed out. */
private const val LOCKED_ROW_ALPHA = 0.55f
