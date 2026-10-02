package com.cyopstd.game.ui.game

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.cyopstd.game.model.AgentType
import com.cyopstd.game.ui.common.AsciiRule
import com.cyopstd.game.ui.common.CompactButton
import com.cyopstd.game.ui.theme.Palette

/**
 * The agent picker. Tap AGENTS, tap an agent, then tap a highlighted node.
 *
 * Locked agents stay visible behind a lock, because knowing what you are
 * working toward is half the reason to keep playing. Tapping one opens a short
 * note saying exactly how to unlock it (owner, 2026-09-26). Agents you cannot
 * currently afford are shown dimmed with the cost in red rather than hidden.
 */
@Composable
fun DeployPanel(
    crypto: Int,
    unlockedAgents: Set<String>,
    selected: AgentType?,
    onSelect: (AgentType) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /** The player's best wave, for the "your best" line in the lock note. */
    bestWave: Int = 0,
    /** Best wave on the beginner level, for agents only it unlocks. */
    bestWaveBeginner: Int = 0,
    /** Icons and costs only; see [GameSettings.compactAgentBar]. */
    compact: Boolean = false,
    /**
     * Agents already on the board as many times as they are allowed (owner,
     * 2026-10-02: *"gray these out when you have max units used on the map"*).
     */
    maxedOut: Set<AgentType> = emptySet()
) {
    var lockedInfo by remember { mutableStateOf<AgentType?>(null) }
    /** The agent whose full details the "i" on its card has opened. */
    var infoFor by remember { mutableStateOf<AgentType?>(null) }
    if (compact) {
        CompactDeployBar(
            crypto = crypto,
            unlockedAgents = unlockedAgents,
            selected = selected,
            lockedInfo = lockedInfo,
            onLockedInfo = { lockedInfo = it },
            onSelect = onSelect,
            bestWave = bestWave,
            bestWaveBeginner = bestWaveBeginner,
            modifier = modifier,
            maxedOut = maxedOut
        )
        return
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Palette.Surface.copy(alpha = LocalPanelOpacity.current), RoundedCornerShape(6.dp))
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

        lockedInfo?.let { type ->
            LockNote(
                type = type,
                bestWave = if (type.beginnerLevelOnly) bestWaveBeginner else bestWave,
                onDismiss = { lockedInfo = null }
            )
            Spacer(Modifier.height(8.dp))
        }

        infoFor?.let { type ->
            AgentInfoNote(
                type = type,
                unlocked = type.name in unlockedAgents,
                onSelect = {
                    infoFor = null
                    onSelect(type)
                },
                onDismiss = { infoFor = null }
            )
            Spacer(Modifier.height(8.dp))
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(AgentType.catalog, key = { it.name }) { type ->
                val unlocked = type.name in unlockedAgents
                val affordable = crypto >= type.cost
                AgentCard(
                    type = type,
                    unlocked = unlocked,
                    affordable = affordable,
                    maxed = unlocked && type in maxedOut,
                    selected = selected == type,
                    onClick = {
                        if (unlocked) {
                            lockedInfo = null
                            onSelect(type)
                        } else {
                            lockedInfo = type
                        }
                    },
                    onInfo = {
                        lockedInfo = null
                        infoFor = if (infoFor == type) null else type
                    },
                    infoOpen = infoFor == type
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
    onClick: () -> Unit,
    onInfo: () -> Unit,
    infoOpen: Boolean,
    /** At its deploy limit: greyed out, "MAX" in place of the cost. */
    maxed: Boolean = false
) {
    val accent = when {
        !unlocked || maxed -> Palette.TextMuted
        selected -> Palette.Green
        affordable -> Palette.Cyan
        else -> Palette.Orange
    }

    Column(
        modifier = Modifier
            .width(166.dp)
            .testTag("agent-card-${type.name}")
            .alpha(if (maxed) MAXED_ALPHA else 1f)
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
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
            Column(Modifier.weight(1f)) {
                Text(
                    text = type.shortName,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (unlocked && !maxed) Palette.TextPrimary else Palette.TextMuted,
                    maxLines = 1
                )
                Text(
                    text = when {
                        !unlocked -> "LOCKED"
                        maxed -> "MAX ${type.maxDeployed} DEPLOYED"
                        else -> "◇ ${type.cost}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        !unlocked || maxed -> Palette.TextMuted
                        affordable -> Palette.Crypto
                        else -> Palette.Red
                    }
                )
            }
            InfoButton(open = infoOpen, onClick = onInfo, tag = "agent-info-${type.name}")
        }

        Spacer(Modifier.height(5.dp))

        if (unlocked) {
            Text(
                text = if (type.healsServer) {
                    "+${com.cyopstd.game.core.Balance.ENGINEER_HEAL_AMOUNT} HP / " +
                        "${com.cyopstd.game.core.Balance.ENGINEER_HEAL_INTERVAL.toInt()}s"
                } else {
                    "DMG ${type.baseDamage.toInt()}  RATE ${format(type.baseFireRate)}/s"
                },
                style = MaterialTheme.typography.labelSmall,
                color = Palette.TextSecondary,
                maxLines = 1
            )
            Text(
                text = if (type.healsServer) "ON CORE" else "RNG ${type.baseRange.toInt()}",
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
                text = "\uD83D\uDD12 LOCKED",
                style = MaterialTheme.typography.labelMedium,
                color = Palette.Purple
            )
            Text(
                text = "TAP FOR DETAILS",
                style = MaterialTheme.typography.labelSmall,
                color = Palette.TextMuted
            )
        }
    }
}

/**
 * The small round "i" that opens an entry's full details (owner,
 * 2026-09-28). A separate target from the card itself, so reading about an
 * agent never selects it by accident.
 */
@Composable
internal fun InfoButton(open: Boolean, onClick: () -> Unit, tag: String, accent: androidx.compose.ui.graphics.Color = Palette.Cyan) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .testTag(tag)
            .size(24.dp)
            .background(accent.copy(alpha = if (open) 0.35f else 0.10f), CircleShape)
            .border(1.dp, accent.copy(alpha = 0.8f), CircleShape)
            .clickable(onClick = onClick)
    ) {
        Text(text = "i", style = MaterialTheme.typography.labelMedium, color = accent)
    }
}

/** Everything about one agent, opened from the "i" on its card. */
@Composable
private fun AgentInfoNote(
    type: AgentType,
    unlocked: Boolean,
    onSelect: () -> Unit,
    onDismiss: () -> Unit
) {
    val color = agentClassColor(type)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Palette.SurfaceRaised.copy(alpha = 0.9f), RoundedCornerShape(4.dp))
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
            .padding(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "[${type.glyph}] ${type.displayName}  \u00B7  \u25C7 ${type.cost}",
                style = MaterialTheme.typography.titleSmall,
                color = color,
                modifier = Modifier.weight(1f)
            )
            if (unlocked) {
                CompactButton(text = "SELECT", onClick = onSelect, accent = Palette.Green)
                Spacer(Modifier.width(6.dp))
            }
            CompactButton(text = "CLOSE", onClick = onDismiss, accent = Palette.TextSecondary)
        }
        Column(
            Modifier
                .heightIn(max = 130.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = if (type.healsServer) {
                    "REPAIR +${com.cyopstd.game.core.Balance.ENGINEER_HEAL_AMOUNT} HP " +
                        "\u00B7 EVERY ${com.cyopstd.game.core.Balance.ENGINEER_HEAL_INTERVAL.toInt()}s " +
                        "\u00B7 SLOT CORE-SERVER \u00B7 MAX ${type.maxDeployed}"
                } else {
                    "DMG ${type.baseDamage.toInt()} \u00B7 RATE ${format(type.baseFireRate)}/s " +
                        "\u00B7 RANGE ${type.baseRange.toInt()}" +
                        if (type.maxDeployed > 0) " \u00B7 MAX ${type.maxDeployed}" else ""
                },
                style = MaterialTheme.typography.labelMedium,
                color = Palette.Cyan
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${type.abilityName} \u2014 ${type.abilitySummary}",
                style = MaterialTheme.typography.labelMedium,
                color = Palette.Crypto
            )
            Spacer(Modifier.height(4.dp))
            Text(text = "IN-GAME", style = MaterialTheme.typography.labelSmall, color = Palette.Green)
            Text(text = type.inGame, style = MaterialTheme.typography.bodySmall, color = Palette.TextSecondary)
            Spacer(Modifier.height(4.dp))
            Text(text = "REAL-WORLD", style = MaterialTheme.typography.labelSmall, color = Palette.Cyan)
            Text(text = type.realWorld, style = MaterialTheme.typography.bodySmall, color = Palette.TextSecondary)
        }
    }
}

/**
 * The compact deploy bar (owner, 2026-09-27): each agent as a small icon in
 * its board colour with its cost under it in yellow, and nothing else.
 * Selecting, locking and affordability work exactly as on the full cards.
 */
@Composable
private fun CompactDeployBar(
    crypto: Int,
    unlockedAgents: Set<String>,
    selected: AgentType?,
    lockedInfo: AgentType?,
    onLockedInfo: (AgentType?) -> Unit,
    onSelect: (AgentType) -> Unit,
    bestWave: Int,
    bestWaveBeginner: Int,
    modifier: Modifier,
    maxedOut: Set<AgentType> = emptySet()
) {
    Column(
        modifier = modifier
            .background(Palette.Surface.copy(alpha = LocalPanelOpacity.current), RoundedCornerShape(6.dp))
            .border(1.dp, Palette.Cyan.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        lockedInfo?.let { type ->
            LockNote(
                type = type,
                bestWave = if (type.beginnerLevelOnly) bestWaveBeginner else bestWave,
                onDismiss = { onLockedInfo(null) }
            )
            Spacer(Modifier.height(6.dp))
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(COMPACT_GAP)) {
            items(AgentType.catalog, key = { it.name }) { type ->
                val unlocked = type.name in unlockedAgents
                CompactAgentIcon(
                    type = type,
                    unlocked = unlocked,
                    affordable = crypto >= type.cost,
                    maxed = unlocked && type in maxedOut,
                    selected = selected == type,
                    onClick = {
                        if (unlocked) {
                            onLockedInfo(null)
                            onSelect(type)
                        } else {
                            onLockedInfo(type)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun CompactAgentIcon(
    type: AgentType,
    unlocked: Boolean,
    affordable: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    /** At its deploy limit: grey, faded, "MAX" under it in place of the cost. */
    maxed: Boolean = false
) {
    val color = if (unlocked && !maxed) agentClassColor(type) else Palette.TextMuted
    // Too dear right now: still there, but faded, like the full cards.
    val strength = when {
        maxed -> MAXED_ALPHA
        !unlocked || affordable -> 1f
        else -> 0.45f
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .testTag("compact-agent-${type.name}")
            .clickable(onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(width = COMPACT_ICON_WIDTH, height = COMPACT_ICON_HEIGHT)
                .background(
                    color.copy(alpha = (if (selected) 0.34f else 0.16f) * strength),
                    RoundedCornerShape(7.dp)
                )
                .border(
                    if (selected) 2.dp else 1.dp,
                    if (selected) Palette.TextPrimary else color.copy(alpha = 0.7f * strength),
                    RoundedCornerShape(7.dp)
                )
        ) {
            Text(
                text = "[${type.glyph}]",
                style = MaterialTheme.typography.titleMedium,
                color = color.copy(alpha = strength),
                maxLines = 1
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = when {
                !unlocked -> "\uD83D\uDD12"
                maxed -> "MAX"
                else -> "${type.cost}"
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (unlocked && !maxed) Palette.Crypto.copy(alpha = strength) else Palette.TextMuted,
            maxLines = 1
        )
    }
}

/** How faded an agent at its deploy limit is drawn. */
private const val MAXED_ALPHA = 0.4f

private val COMPACT_ICON_WIDTH = 54.dp
private val COMPACT_ICON_HEIGHT = 32.dp
private val COMPACT_GAP = 12.dp

/** What a locked agent needs, shown when its card is tapped. */
@Composable
private fun LockNote(type: AgentType, bestWave: Int, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Palette.SurfaceRaised, RoundedCornerShape(4.dp))
            .border(1.dp, Palette.Purple.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "\uD83D\uDD12 [${type.glyph}] ${type.displayName} \u00B7 LOCKED",
                style = MaterialTheme.typography.labelMedium,
                color = Palette.Purple
            )
            Text(
                text = type.unlockRequirement,
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextPrimary
            )
            Text(
                text = if (type.beginnerLevelOnly) {
                    "Your best on ${AgentType.BEGINNER_LEVEL_NAME}: wave $bestWave"
                } else {
                    "Your best: wave $bestWave"
                },
                style = MaterialTheme.typography.labelSmall,
                color = Palette.TextMuted
            )
        }
        Spacer(Modifier.width(10.dp))
        CompactButton(text = "OK", onClick = onDismiss, accent = Palette.Purple)
    }
}

internal fun format(value: Float): String {
    val rounded = (value * 10f).toInt() / 10f
    return if (rounded == rounded.toInt().toFloat()) "${rounded.toInt()}" else "$rounded"
}
