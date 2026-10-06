package com.cyopstd.game.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cyopstd.game.R
import com.cyopstd.game.i18n.Language
import com.cyopstd.game.i18n.tr
import com.cyopstd.game.ui.common.BastionButton
import com.cyopstd.game.ui.common.Caption
import com.cyopstd.game.ui.common.TerminalPanel
import com.cyopstd.game.ui.theme.Palette

/**
 * LANGUAGE (owner, 2026-10-05): every language the game ships in, each listed
 * in its own name so a player who cannot read the current one can still find
 * theirs, plus SYSTEM DEFAULT to follow the phone.
 *
 * Picking one asks first, then restarts the game in it (see
 * `Languages.applyAndRestart`). From inside a match it only explains where to
 * do it: a restart there would end the run.
 */
@Composable
fun LanguagePanel(
    current: Language?,
    languages: List<Language>,
    changeAllowed: Boolean,
    onApply: (Language?) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Pick?>(null) }
    val systemLabel = tr("SYSTEM DEFAULT (phone language)")

    TerminalPanel(title = tr("LANGUAGE"), accent = Palette.Cyan) {
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("language-picker")
                    .background(Palette.SurfaceRaised, RoundedCornerShape(4.dp))
                    .border(1.dp, Palette.Cyan.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                    .clickable(role = Role.DropdownList) { open = true }
                    .padding(horizontal = 10.dp, vertical = 9.dp)
            ) {
                Icon(
                    painterResource(R.drawable.ic_language), contentDescription = tr("LANGUAGE"),
                    tint = Palette.Cyan, modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = current?.let { label(it) } ?: systemLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text("▼", style = MaterialTheme.typography.labelMedium, color = Palette.Cyan)
            }
            DropdownMenu(
                expanded = open,
                onDismissRequest = { open = false },
                containerColor = Palette.SurfaceRaised,
                modifier = Modifier.heightIn(max = 320.dp)
            ) {
                DropdownMenuItem(
                    text = {
                        Text(
                            systemLabel, style = MaterialTheme.typography.labelMedium,
                            color = if (current == null) Palette.Green else Palette.TextPrimary
                        )
                    },
                    onClick = { open = false; if (current != null) pending = Pick(null) },
                    modifier = Modifier.testTag("language-system")
                )
                for (language in languages) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                label(language), style = MaterialTheme.typography.labelMedium,
                                color = if (language == current) Palette.Green else Palette.TextPrimary
                            )
                        },
                        onClick = { open = false; if (language != current) pending = Pick(language) },
                        modifier = Modifier.testTag("language-${language.tag}")
                    )
                }
            }
        }

        pending?.let { pick ->
            Spacer(Modifier.height(8.dp))
            if (changeAllowed) {
                Caption(
                    tr("The game restarts to switch to {0}. Your progress is kept.", pick.language?.nativeName ?: systemLabel),
                    color = Palette.Crypto
                )
                Spacer(Modifier.height(6.dp))
                Row {
                    BastionButton(
                        text = tr("RESTART"),
                        accent = Palette.Green,
                        onClick = { pending = null; onApply(pick.language) },
                        modifier = Modifier.weight(1f).testTag("language-restart")
                    )
                    Spacer(Modifier.width(8.dp))
                    BastionButton(
                        text = tr("CANCEL"),
                        accent = Palette.TextSecondary,
                        onClick = { pending = null },
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                Caption(
                    tr("Change the language from SETTINGS on the main menu: switching restarts the game, which would end this run."),
                    color = Palette.Orange,
                    modifier = Modifier.testTag("language-in-match-note")
                )
            }
        }
    }
}

/** A pick, wrapped so "follow the phone" (null) is a value too. */
private data class Pick(val language: Language?)

/** "Español · Spanish": its own name first, then English for anyone helping. */
private fun label(language: Language): String =
    if (language.nativeName == language.englishName) language.nativeName
    else "${language.nativeName} · ${language.englishName}"
