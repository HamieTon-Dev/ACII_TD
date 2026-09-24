package com.cyopstd.game.ui.game

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cyopstd.game.core.Balance
import com.cyopstd.game.model.Agent
import com.cyopstd.game.ui.common.AsciiRule
import com.cyopstd.game.ui.common.CompactButton
import com.cyopstd.game.ui.common.StatRow
import com.cyopstd.game.ui.theme.Palette

/**
 * Tap a deployed agent and this opens: its live stats, what the next level buys,
 * and the UPGRADE / SELL / CLOSE actions.
 *
 * Stat rows show "current -> next" so an upgrade is a visible, informed decision
 * rather than a leap of faith.
 *
 * **Two columns, and the actions never scroll.** This was one tall scrolling
 * card, which put UPGRADE below the fold on a short screen with nothing to say
 * it was there: players could not find how to upgrade at all. Splitting it puts
 * everything you *do* on the right, always on screen, and everything you *read*
 * on the left. The reading column can still overflow on a small phone, so it
 * says so — a scroll hint appears at its bottom edge while there is more below,
 * because an invisible scroll is the same as no scroll.
 */
@Composable
fun AgentManagementPanel(
    agent: Agent,
    crypto: Int,
    /** How many levels the current balance could buy right now. */
    affordableLevels: Int,
    onUpgrade: (times: Int) -> Unit,
    onSell: () -> Unit,
    onCycleTargeting: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val type = agent.type
    val current = type.statsAtLevel(agent.level)
    val maxed = agent.level >= Balance.MAX_AGENT_LEVEL
    val next = if (maxed) null else type.statsAtLevel(agent.level + 1)
    val upgradeCost = if (maxed) 0 else type.upgradeCost(agent.level)
    val canAfford = !maxed && crypto >= upgradeCost

    val scroll = rememberScrollState()

    Row(
        modifier = modifier
            .width(430.dp)
            .heightIn(max = 300.dp)
            .background(Palette.Surface.copy(alpha = 0.97f), RoundedCornerShape(6.dp))
            .border(1.dp, Palette.Cyan.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
    // ---- left: what this agent is ----------------------------------------
    Box(modifier = Modifier.weight(1.25f)) {
    Column(modifier = Modifier.verticalScroll(scroll)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = type.renderedGlyph(agent.level),
                style = MaterialTheme.typography.headlineMedium,
                color = Palette.Cyan
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = type.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = Palette.TextPrimary
                )
                Text(
                    text = if (maxed) "LEVEL ${agent.level} · MAX" else "LEVEL ${agent.level}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (maxed) Palette.Crypto else Palette.Green
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        LevelTrack(agent.level)
        AsciiRule(color = Palette.CyanDim)
        Spacer(Modifier.height(6.dp))

        UpgradeStatRow("DAMAGE", format(current.damage), next?.let { format(it.damage) })
        UpgradeStatRow("RATE", "${format(current.fireRate)}/sec", next?.let { "${format(it.fireRate)}/sec" })
        UpgradeStatRow("RANGE", current.range.toInt().toString(), next?.let { it.range.toInt().toString() })

        Spacer(Modifier.height(6.dp))
        AsciiRule(color = Palette.Divider)
        Spacer(Modifier.height(6.dp))

        Text(
            text = "SPECIAL — ${type.abilityName}",
            style = MaterialTheme.typography.labelMedium,
            color = Palette.Green
        )
        Text(
            text = type.abilitySummary,
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextSecondary
        )

        if (type.allowsTargetingModes) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "TARGETING",
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.TextMuted
                    )
                    Text(
                        text = agent.targetingMode.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = Palette.Purple
                    )
                }
                CompactButton(
                    text = "CHANGE",
                    onClick = onCycleTargeting,
                    accent = Palette.Purple
                )
            }
            Text(
                text = agent.targetingMode.description,
                style = MaterialTheme.typography.labelSmall,
                color = Palette.TextMuted
            )
        }

        Spacer(Modifier.height(8.dp))
        AsciiRule(color = Palette.Divider)
        Spacer(Modifier.height(6.dp))

        StatRow("ATTACKS STOPPED", agent.lifetimeKills.toString(), valueColor = Palette.Cyan)
        StatRow("DAMAGE DEALT", agent.lifetimeDamage.toInt().toString(), valueColor = Palette.Cyan)

        if (agent.damageBuff > 1f) {
            StatRow(
                "ARCHITECT UPLINK",
                "+${((agent.damageBuff - 1f) * 100).toInt()}% DMG",
                valueColor = Palette.Purple
            )
        }
        // Clears the scroll hint, so the last row is never read through it.
        Spacer(Modifier.height(20.dp))
    }

    ScrollHint(scroll, Modifier.align(Alignment.BottomCenter))
    }

    // ---- right: what you can do about it ---------------------------------
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight(),
        verticalArrangement = Arrangement.Top
    ) {
        if (maxed) {
            Text(
                text = "MAXIMUM LEVEL REACHED",
                style = MaterialTheme.typography.labelMedium,
                color = Palette.Crypto
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "UPGRADE COST",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextSecondary
                )
                Text(
                    text = "◇ $upgradeCost",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (canAfford) Palette.Crypto else Palette.Red
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // Agents climb to level 100, so buying one at a time would be an ordeal.
        // +10 and MAX buy as many levels as the balance allows, then stop.
        if (!maxed) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CompactButton(
                    text = "+1",
                    onClick = { onUpgrade(1) },
                    enabled = canAfford,
                    accent = Palette.Green,
                    modifier = Modifier.weight(1f)
                )
                CompactButton(
                    text = "+10",
                    onClick = { onUpgrade(10) },
                    enabled = canAfford,
                    accent = Palette.Green,
                    modifier = Modifier.weight(1f)
                )
                CompactButton(
                    text = if (affordableLevels > 1) "MAX +$affordableLevels" else "MAX",
                    onClick = { onUpgrade(Balance.MAX_AGENT_LEVEL) },
                    enabled = canAfford,
                    accent = Palette.Crypto,
                    modifier = Modifier.weight(1.3f)
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        CompactButton(
            text = "SELL  ◇ ${type.sellValue(agent.level)}",
            onClick = onSell,
            accent = Palette.Orange,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        CompactButton(
            text = "CLOSE",
            onClick = onClose,
            accent = Palette.TextSecondary,
            modifier = Modifier.fillMaxWidth()
        )
    }
    }
}

/**
 * "MORE BELOW" at the bottom of a column that has more below.
 *
 * Shown only while the scroll can actually travel further, so it is never a
 * decoration: if it is there, there is something to see.
 */
@Composable
internal fun ScrollHint(scroll: ScrollState, modifier: Modifier = Modifier) {
    val more = scroll.value < scroll.maxValue - 2
    if (!more) return
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to Palette.Surface
                )
            )
            .padding(top = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "\u25BC  MORE BELOW",
            style = MaterialTheme.typography.labelSmall,
            color = Palette.Cyan
        )
    }
}

/**
 * `[####------] 42/100` level track.
 *
 * A hundred cells would not fit, so the bar is always [TRACK_CELLS] wide and
 * each cell stands for several levels.
 */
@Composable
private fun LevelTrack(level: Int) {
    val clamped = level.coerceIn(0, Balance.MAX_AGENT_LEVEL)
    val filled = (clamped * TRACK_CELLS) / Balance.MAX_AGENT_LEVEL
    Text(
        text = "[" + "#".repeat(filled) + "-".repeat(TRACK_CELLS - filled) +
            "] $clamped/${Balance.MAX_AGENT_LEVEL}",
        style = MaterialTheme.typography.bodyMedium,
        color = Palette.Green
    )
}

// Fourteen cells rather than twenty: the track shares its column with the
// stats now, and a bar that wraps onto a second line stops reading as a bar.
private const val TRACK_CELLS = 14

@Composable
private fun UpgradeStatRow(label: String, current: String, next: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.TextSecondary
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = current,
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextPrimary
            )
            if (next != null && next != current) {
                Text(
                    text = "  →  ",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextMuted
                )
                Text(
                    text = next,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Green
                )
            }
        }
    }
}
