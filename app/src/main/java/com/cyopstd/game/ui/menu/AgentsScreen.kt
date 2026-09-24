package com.cyopstd.game.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cyopstd.game.model.AgentType
import com.cyopstd.game.ui.common.GlyphBadge
import com.cyopstd.game.ui.common.ScreenScaffold
import com.cyopstd.game.ui.theme.Palette

/**
 * The permanent agent roster: what you have, what you do not, and exactly what
 * it takes to get the rest. Unlocks are account-wide and survive a lost run.
 */
@Composable
fun AgentsScreen(
    unlockedAgents: Set<String>,
    highestWave: Int,
    backgroundAnimation: Boolean,
    onBack: () -> Unit
) {
    ScreenScaffold(
        title = "CYBER AGENTS",
        subtitle = "${unlockedAgents.size} of ${AgentType.entries.size} unlocked · " +
            "best wave $highestWave",
        onBack = onBack,
        backgroundAnimation = backgroundAnimation
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 320.dp),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(AgentType.catalog, key = { it.name }) { type ->
                AgentRosterCard(
                    type = type,
                    unlocked = type.name in unlockedAgents,
                    highestWave = highestWave
                )
            }
        }
    }
}

@Composable
private fun AgentRosterCard(type: AgentType, unlocked: Boolean, highestWave: Int) {
    val accent = if (unlocked) Palette.Cyan else Palette.TextMuted

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Palette.Surface.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlyphBadge(glyph = "[${type.glyph}]", color = accent)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = type.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (unlocked) Palette.TextPrimary else Palette.TextMuted
                )
                Text(
                    text = if (unlocked) {
                        "AVAILABLE · ◇ ${type.cost}"
                    } else {
                        "LOCKED · REACH WAVE ${type.unlockWave}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (unlocked) Palette.Green else Palette.Orange
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            MiniStat("DMG", type.baseDamage.toInt().toString())
            MiniStat("RATE", "${trim(type.baseFireRate)}/s")
            MiniStat("RANGE", type.baseRange.toInt().toString())
        }

        Spacer(Modifier.height(8.dp))

        // Two halves, labelled, because the brief is that this screen should
        // teach the real concept as well as the fictional unit -- and because
        // the two used to be one paragraph in which a beginner had no way to
        // tell which sentence was true of the world and which was true only
        // here. REAL-WORLD comes first: it is the part worth knowing.
        IndexSection(
            label = "REAL-WORLD",
            accent = Palette.Cyan,
            body = type.realWorld
        )

        Spacer(Modifier.height(8.dp))

        IndexSection(
            label = "IN-GAME",
            accent = Palette.Green,
            body = type.inGame
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "${type.abilityName} — ${type.abilitySummary}",
            style = MaterialTheme.typography.labelMedium,
            color = Palette.Crypto
        )

        if (!unlocked) {
            Spacer(Modifier.height(8.dp))
            val progress = if (type.unlockWave <= 0) 1f
            else (highestWave.toFloat() / type.unlockWave).coerceIn(0f, 1f)
            val cells = 16
            val filled = (progress * cells).toInt()
            Text(
                text = "[" + "#".repeat(filled) + "-".repeat(cells - filled) + "] " +
                    "$highestWave / ${type.unlockWave}",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.Purple
            )
        }
    }
}

/** One labelled half of an index entry. */
@Composable
private fun IndexSection(label: String, accent: Color, body: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = accent
    )
    Text(
        text = body,
        style = MaterialTheme.typography.bodySmall,
        color = Palette.TextSecondary
    )
}

@Composable
private fun MiniStat(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Palette.TextMuted
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = Palette.Cyan
        )
    }
}

private fun trim(value: Float): String {
    val rounded = (value * 10f).toInt() / 10f
    return if (rounded == rounded.toInt().toFloat()) "${rounded.toInt()}" else "$rounded"
}
