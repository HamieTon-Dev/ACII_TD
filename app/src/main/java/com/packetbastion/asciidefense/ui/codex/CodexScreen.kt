package com.packetbastion.asciidefense.ui.codex

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.dp
import com.packetbastion.asciidefense.ui.common.CompactButton
import com.packetbastion.asciidefense.ui.common.GlyphBadge
import com.packetbastion.asciidefense.ui.common.ScreenScaffold
import com.packetbastion.asciidefense.ui.theme.Palette

/**
 * The CODEX: a section rail on the left, entries on the right. Every enemy,
 * agent and boss modifier in the game has an entry, plus a short plain-language
 * glossary of the networking and security terms the game borrows.
 */
@Composable
fun CodexScreen(
    backgroundAnimation: Boolean,
    onBack: () -> Unit
) {
    var section by remember { mutableStateOf(CodexContent.Section.AGENTS) }
    val entries = remember(section) { CodexContent.entriesFor(section) }

    ScreenScaffold(
        title = "CODEX",
        subtitle = section.description,
        onBack = onBack,
        backgroundAnimation = backgroundAnimation
    ) {
        Row(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .width(200.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CodexContent.Section.entries.forEach { candidate ->
                    CompactButton(
                        text = candidate.title,
                        onClick = { section = candidate },
                        selected = section == candidate,
                        accent = Palette.Purple,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(entries, key = { "${section.name}:${it.title}" }) { entry ->
                    CodexCard(entry)
                }
            }
        }
    }
}

@Composable
private fun CodexCard(entry: CodexContent.Entry) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Palette.Surface.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
            .border(1.dp, Palette.Divider, RoundedCornerShape(6.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlyphBadge(glyph = entry.glyph, color = Palette.Cyan, sizeDp = 50)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Palette.TextPrimary
                )
                Text(
                    text = entry.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextMuted
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = entry.body,
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.TextSecondary
        )

        if (entry.footnote != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "> ${entry.footnote}",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.Green
            )
        }
    }
}
