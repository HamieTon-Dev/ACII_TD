package com.packetbastion.asciidefense.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.packetbastion.asciidefense.model.AgentType
import com.packetbastion.asciidefense.ui.common.AsciiRule
import com.packetbastion.asciidefense.ui.common.CompactButton
import com.packetbastion.asciidefense.ui.theme.Palette

/**
 * The agent picker. Tap AGENTS, tap an agent, then tap a highlighted node.
 *
 * Locked agents stay visible with their unlock wave shown, because knowing what
 * you are working toward is half the reason to keep playing. Agents you cannot
 * currently afford are shown dimmed with the cost in red rather than hidden.
 */
@Composable
fun DeployPanel(
    crypto: Int,
    unlockedAgents: Set<String>,
    selected: AgentType?,
    onSelect: (AgentType) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Palette.Surface.copy(alpha = 0.97f), RoundedCornerShape(6.dp))
            .border(1.dp, Palette.Cyan.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "DEPLOY CYBER AGENT",
                style = MaterialTheme.typography.titleMedium,
                color = Palette.Cyan
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "◇ $crypto",
                    style = MaterialTheme.typography.titleMedium,
                    color = Palette.Crypto
                )
                Spacer(Modifier.width(12.dp))
                CompactButton(text = "CLOSE", onClick = onClose, accent = Palette.TextSecondary)
            }
        }

        AsciiRule(color = Palette.CyanDim)
        Spacer(Modifier.height(8.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(AgentType.catalog, key = { it.name }) { type ->
                val unlocked = type.name in unlockedAgents
                val affordable = crypto >= type.cost
                AgentCard(
                    type = type,
                    unlocked = unlocked,
                    affordable = affordable,
                    selected = selected == type,
                    onClick = { onSelect(type) }
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = if (selected == null) {
                "Select an agent, then tap a highlighted deployment node."
            } else {
                "${selected.displayName} selected — tap a highlighted node to deploy."
            },
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextSecondary
        )
    }
}

@Composable
private fun AgentCard(
    type: AgentType,
    unlocked: Boolean,
    affordable: Boolean,
    selected: Boolean,
    onClick: () -> Unit
) {
    val accent = when {
        !unlocked -> Palette.TextMuted
        selected -> Palette.Green
        affordable -> Palette.Cyan
        else -> Palette.Orange
    }

    Column(
        modifier = Modifier
            .width(148.dp)
            .background(
                if (selected) accent.copy(alpha = 0.16f) else Palette.SurfaceRaised.copy(alpha = 0.6f),
                RoundedCornerShape(4.dp)
            )
            .border(
                if (selected) 2.dp else 1.dp,
                accent.copy(alpha = 0.65f),
                RoundedCornerShape(4.dp)
            )
            .clickable(enabled = true, onClick = onClick)
            .padding(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .background(accent.copy(alpha = 0.14f), RoundedCornerShape(3.dp))
                    .border(1.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(3.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "[${type.glyph}]",
                    style = MaterialTheme.typography.titleMedium,
                    color = accent
                )
            }
            Spacer(Modifier.width(7.dp))
            Column {
                Text(
                    text = type.shortName,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (unlocked) Palette.TextPrimary else Palette.TextMuted,
                    maxLines = 1
                )
                Text(
                    text = if (unlocked) "◇ ${type.cost}" else "LOCKED",
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        !unlocked -> Palette.TextMuted
                        affordable -> Palette.Crypto
                        else -> Palette.Red
                    }
                )
            }
        }

        Spacer(Modifier.height(5.dp))

        if (unlocked) {
            Text(
                text = "DMG ${type.baseDamage.toInt()}  RATE ${format(type.baseFireRate)}/s",
                style = MaterialTheme.typography.labelSmall,
                color = Palette.TextSecondary,
                maxLines = 1
            )
            Text(
                text = "RNG ${type.baseRange.toInt()}",
                style = MaterialTheme.typography.labelSmall,
                color = Palette.TextSecondary,
                maxLines = 1
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = type.abilityName,
                style = MaterialTheme.typography.labelSmall,
                color = Palette.Green,
                maxLines = 1
            )
        } else {
            Text(
                text = "UNLOCKS AT",
                style = MaterialTheme.typography.labelSmall,
                color = Palette.TextMuted
            )
            Text(
                text = "WAVE ${type.unlockWave}",
                style = MaterialTheme.typography.labelMedium,
                color = Palette.Purple
            )
        }
    }
}

internal fun format(value: Float): String {
    val rounded = (value * 10f).toInt() / 10f
    return if (rounded == rounded.toInt().toFloat()) "${rounded.toInt()}" else "$rounded"
}
