package com.packetbastion.asciidefense.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.unit.dp
import com.packetbastion.asciidefense.save.GameSettings
import com.packetbastion.asciidefense.ui.common.AsciiRule
import com.packetbastion.asciidefense.ui.common.BastionButton
import com.packetbastion.asciidefense.ui.common.Caption
import com.packetbastion.asciidefense.ui.common.CompactButton
import com.packetbastion.asciidefense.ui.common.ScreenScaffold
import com.packetbastion.asciidefense.ui.common.SliderRow
import com.packetbastion.asciidefense.ui.common.TerminalPanel
import com.packetbastion.asciidefense.ui.common.ToggleRow
import com.packetbastion.asciidefense.ui.theme.Palette

/**
 * Settings. Two scrollable columns so everything is reachable in landscape on a
 * short phone, and every option takes effect immediately — nothing here needs
 * an "apply" step.
 */
@Composable
fun SettingsScreen(
    settings: GameSettings,
    backgroundAnimation: Boolean,
    onUpdate: ((GameSettings) -> GameSettings) -> Unit,
    onResetProgress: () -> Unit,
    onBack: () -> Unit
) {
    var confirmingReset by remember { mutableStateOf(false) }

    ScreenScaffold(
        title = "SETTINGS",
        subtitle = "Changes apply immediately and persist on this device",
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
                TerminalPanel(title = "AUDIO", accent = Palette.Cyan) {
                    SliderRow(
                        label = "MUSIC VOLUME",
                        value = settings.musicVolume,
                        onValueChange = { v -> onUpdate { it.copy(musicVolume = v) } }
                    )
                    SliderRow(
                        label = "SOUND EFFECTS VOLUME",
                        value = settings.sfxVolume,
                        onValueChange = { v -> onUpdate { it.copy(sfxVolume = v) } }
                    )
                    Caption(
                        "All audio is generated on-device at startup; the game " +
                            "ships no sound files."
                    )
                }

                Spacer(Modifier.height(12.dp))

                TerminalPanel(title = "FEEDBACK", accent = Palette.Green) {
                    ToggleRow(
                        label = "VIBRATION",
                        description = "Haptics on boss alerts, server hits and game over",
                        checked = settings.vibrationEnabled,
                        onCheckedChange = { v -> onUpdate { it.copy(vibrationEnabled = v) } }
                    )
                    ToggleRow(
                        label = "SCREEN SHAKE",
                        description = "Shake the battlefield when CORE-SERVER is hit",
                        checked = settings.screenShake,
                        onCheckedChange = { v -> onUpdate { it.copy(screenShake = v) } }
                    )
                }

                Spacer(Modifier.height(12.dp))

                TerminalPanel(title = "GAMEPLAY", accent = Palette.Purple) {
                    ToggleRow(
                        label = "AUTO START WAVES",
                        description = "Begin the next wave automatically after a short pause",
                        checked = settings.autoStartWaves,
                        onCheckedChange = { v -> onUpdate { it.copy(autoStartWaves = v) } }
                    )
                    ToggleRow(
                        label = "SHOW AGENT RANGE",
                        description = "Draw the scan radius of the selected agent",
                        checked = settings.showAgentRange,
                        onCheckedChange = { v -> onUpdate { it.copy(showAgentRange = v) } }
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
            ) {
                TerminalPanel(title = "VISUALS", accent = Palette.Cyan) {
                    ToggleRow(
                        label = "BACKGROUND ANIMATION",
                        description = "Drifting ASCII data behind the battlefield and menus",
                        checked = settings.backgroundAnimation,
                        onCheckedChange = { v -> onUpdate { it.copy(backgroundAnimation = v) } }
                    )
                    ToggleRow(
                        label = "DAMAGE NUMBERS",
                        description = "Show floating damage values on hits",
                        checked = settings.damageNumbers,
                        onCheckedChange = { v -> onUpdate { it.copy(damageNumbers = v) } }
                    )
                }

                Spacer(Modifier.height(12.dp))

                TerminalPanel(title = "PERFORMANCE", accent = Palette.Orange) {
                    ToggleRow(
                        label = "BATTERY SAVER",
                        description = "Halve the frame rate and drop decorative effects",
                        checked = settings.batterySaver,
                        onCheckedChange = { v -> onUpdate { it.copy(batterySaver = v) } }
                    )
                    Caption(
                        "Battery saver only changes what is drawn. Wave difficulty, " +
                            "damage and timing are completely unaffected."
                    )
                }

                Spacer(Modifier.height(12.dp))

                TerminalPanel(title = "DANGER ZONE", accent = Palette.Red) {
                    Caption(
                        "Deletes your saved run, unlocked agents, statistics and " +
                            "settings. This cannot be undone."
                    )
                    Spacer(Modifier.height(10.dp))
                    BastionButton(
                        text = "RESET PROGRESS",
                        accent = Palette.Red,
                        leadingGlyph = "[!]",
                        onClick = { confirmingReset = true }
                    )
                }

                Spacer(Modifier.height(12.dp))
            }
        }
    }

    if (confirmingReset) {
        ResetConfirmation(
            onCancel = { confirmingReset = false },
            onConfirm = {
                confirmingReset = false
                onResetProgress()
            }
        )
    }
}

@Composable
private fun ResetConfirmation(onCancel: () -> Unit, onConfirm: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.Background.copy(alpha = 0.9f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .background(Palette.Surface, RoundedCornerShape(8.dp))
                .border(2.dp, Palette.Red.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                .padding(22.dp)
        ) {
            Text(
                text = "DELETE ALL PROGRESS?",
                style = MaterialTheme.typography.headlineMedium,
                color = Palette.Red
            )
            AsciiRule(color = Palette.RedDeep)
            Spacer(Modifier.height(10.dp))
            Text(
                text = "This erases your saved run, every unlocked agent, all " +
                    "statistics and your settings.\n\nThis cannot be undone.",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextSecondary
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CompactButton(
                    text = "CANCEL",
                    onClick = onCancel,
                    accent = Palette.Cyan,
                    modifier = Modifier.weight(1f)
                )
                CompactButton(
                    text = "RESET",
                    onClick = onConfirm,
                    accent = Palette.Red,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
