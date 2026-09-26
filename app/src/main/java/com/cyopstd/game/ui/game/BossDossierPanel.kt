package com.cyopstd.game.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cyopstd.game.model.BossModifier
import com.cyopstd.game.model.BossVariant
import com.cyopstd.game.ui.common.AsciiRule
import com.cyopstd.game.ui.common.Caption
import com.cyopstd.game.ui.common.CompactButton
import com.cyopstd.game.ui.common.asciiMeter
import com.cyopstd.game.ui.theme.Palette

/**
 * Everything the game knows about the boss currently on the field.
 *
 * All of this existed and none of it was visible. A boss arrived carrying up to
 * four modifiers — each with a name, a tag and a written description — and the
 * player's only clue was a banner that flashed past before the fight and a
 * health bar with no numbers on it. "Why is this one not dying?" had an answer
 * the game simply never gave.
 *
 * Read live rather than snapshotted: the numbers move while it is open, which
 * is the point of opening it mid-fight.
 */
data class BossDossier(
    val variant: BossVariant,
    val health: Float,
    val maxHealth: Float,
    val armor: Float,
    val speed: Float,
    val modifiers: List<BossModifier>,
    val revived: Boolean,
    val distanceToCore: Float
) {
    val fraction: Float get() = if (maxHealth <= 0f) 0f else (health / maxHealth).coerceIn(0f, 1f)
}

/**
 * Tall enough for everything in it, with room to spare.
 *
 * The first draft stacked INTEGRITY, ARMOUR, SPEED and DISTANCE as four full
 * rows inside a 272dp panel, and the bottom two simply ran off the end of it
 * with nothing to say so. Asserting the text existed did not catch that —
 * it existed, laid out, at a height of zero. Fetching the laid-out bounds did:
 * `DISTANCE TO CORE`'s value measured 6 x 0 and the signature line measured
 * 78 x 0, which is what "present but never seen" looks like from the outside.
 *
 * Three independent things now stop it coming back: the three secondary
 * numbers share one strip instead of costing four rows, the panel is sized
 * past what its content measures rather than up to it, and the column scrolls
 * with the usual MORE BELOW hint — so a longer signature or a bigger system
 * font on a real phone degrades into a scroll rather than into silence.
 */
private val PANEL_HEIGHT = 330.dp

@Composable
fun BossDossierPanel(
    dossier: BossDossier,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scroll = rememberScrollState()

    Row(
        modifier = modifier
            .width(430.dp)
            // A fixed height, not a maximum: the modifier list below uses a
            // vertical weight to keep CLOSE pinned under it, and a weight
            // inside a column of unbounded height collapses to nothing.
            .height(PANEL_HEIGHT)
            .background(Palette.Surface.copy(alpha = LocalPanelOpacity.current), RoundedCornerShape(6.dp))
            .border(1.dp, Palette.Red.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ---- who it is, and how it is doing ------------------------------
        Box(Modifier.weight(1.25f)) {
            Column(Modifier.verticalScroll(scroll)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = dossier.variant.glyph,
                        style = MaterialTheme.typography.titleLarge,
                        color = Palette.Red
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = dossier.variant.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            color = Palette.TextPrimary
                        )
                        Caption(
                            if (dossier.revived) "REANIMATED — it will not come back again"
                            else "ON THE FIELD"
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))
                Text(
                    text = asciiMeter(dossier.fraction, cells = 14),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.healthColor(dossier.fraction)
                )
                Text(
                    text = "${dossier.health.toInt()} / ${dossier.maxHealth.toInt()}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Palette.healthColor(dossier.fraction)
                )

                Spacer(Modifier.height(4.dp))
                AsciiRule(color = Palette.Divider)
                Spacer(Modifier.height(4.dp))

                // Three numbers that are each one word wide. Stacked as full
                // rows they cost four times the height they need.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatCell("ARMOUR", dossier.armor.toInt().toString(), Palette.Orange)
                    StatCell("SPEED", "${dossier.speed.toInt()} u/s", Palette.Cyan)
                    StatCell(
                        "TO CORE",
                        "${dossier.distanceToCore.toInt()} u",
                        if (dossier.distanceToCore < 300f) Palette.Red else Palette.TextPrimary
                    )
                }

                Spacer(Modifier.height(6.dp))
                Caption(dossier.variant.signature)
            }

            ScrollHint(scroll, Modifier.align(Alignment.BottomCenter))
        }

        // ---- what it is carrying -----------------------------------------
        Column(Modifier.weight(1f)) {
            Text(
                text = "MODIFIERS",
                style = MaterialTheme.typography.titleSmall,
                color = Palette.Crypto
            )
            AsciiRule(color = Palette.Divider)

            Box(Modifier.weight(1f)) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (dossier.modifiers.isEmpty()) {
                        Caption("None. This one is a straight fight.")
                    }
                    for (modifier in dossier.modifiers) {
                        Text(
                            text = "[${modifier.tag}]",
                            style = MaterialTheme.typography.labelMedium,
                            color = Palette.Orange
                        )
                        Caption(modifier.description)
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }

            CompactButton(
                text = "CLOSE",
                onClick = onClose,
                accent = Palette.TextSecondary,
                modifier = Modifier.fillMaxWidth(),
                dense = true
            )
        }
    }
}

/** One label-over-value number, as narrow as the number it holds. */
@Composable
private fun StatCell(label: String, value: String, valueColor: Color) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Palette.TextMuted
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}
