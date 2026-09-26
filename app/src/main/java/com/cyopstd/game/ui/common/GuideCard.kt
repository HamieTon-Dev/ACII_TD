package com.cyopstd.game.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cyopstd.game.core.Balance
import com.cyopstd.game.ui.theme.Palette

/** One card of a screen guide. */
data class GuideStep(val title: String, val body: String)

/**
 * A short guided tour of a menu screen, one card at a time.
 *
 * The same shape as the in-game tutorial card (H1): a small panel in a
 * corner that never covers the whole screen, with NEXT and SKIP. It keeps its
 * own place in the script; [onFinished] fires once, on DONE or SKIP, and the
 * caller records that it has been seen.
 */
@Composable
fun GuideCard(
    steps: List<GuideStep>,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (steps.isEmpty()) return
    var index by remember(steps) { mutableIntStateOf(0) }
    val step = steps[index.coerceIn(0, steps.lastIndex)]
    val last = index >= steps.lastIndex

    Column(
        modifier = modifier
            .widthIn(max = 380.dp)
            .background(Palette.SurfaceRaised.copy(alpha = 0.97f), RoundedCornerShape(6.dp))
            .border(1.dp, Palette.Green.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
            .padding(14.dp)
    ) {
        Text(
            text = "${index + 1}/${steps.size} · ${step.title}",
            style = MaterialTheme.typography.titleSmall,
            color = Palette.Green
        )
        Spacer(Modifier.height(6.dp))
        Text(text = step.body, style = MaterialTheme.typography.bodyMedium, color = Palette.TextPrimary)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!last) {
                CompactButton(text = "SKIP", onClick = onFinished, accent = Palette.TextSecondary)
            }
            CompactButton(
                text = if (last) "DONE" else "NEXT",
                onClick = { if (last) onFinished() else index++ },
                accent = Palette.Green
            )
        }
    }
}

/** The main-menu tour (owner, 2026-09-26: "this is what Firmware does… scroll down to see more"). */
object MenuGuide {
    val steps: List<GuideStep> = listOf(
        GuideStep(
            "MAIN MENU",
            "A quick tour of the menu. NEXT to go on, SKIP to close it. You can " +
                "replay it any time from SETTINGS."
        ),
        GuideStep(
            "PLAY · CONTINUE",
            "PLAY starts a run on the level and run mode picked on the left. " +
                "When a run is saved, CONTINUE picks it up and NEW RUN starts over."
        ),
        GuideStep(
            "AGENTS",
            "Every cyber agent, what it does, and the wave that unlocks it."
        ),
        GuideStep(
            "FIRMWARE",
            "Spend € BUDGET — banked at every tenth wave of a run and kept " +
                "afterwards — on a permanent damage boost for every agent."
        ),
        GuideStep(
            "SCROLL FOR MORE",
            "The buttons on the right scroll. Further down: STORE, LOADOUT " +
                "(equip skins and backgrounds), GOOGLE PLAY (purchases and cloud " +
                "save), LEADERBOARD, CODEX (threats and agents explained), " +
                "STATISTICS, SETTINGS and ABOUT."
        )
    )
}

/** The FIRMWARE screen's first-visit explainer. */
object FirmwareGuide {
    private val percentPerLevel: String
        get() {
            val pct = Balance.FIRMWARE_DAMAGE_PER_LEVEL * 100f
            return if (pct == pct.toInt().toFloat()) "${pct.toInt()}" else "$pct"
        }

    val steps: List<GuideStep>
        get() = listOf(
            GuideStep(
                "€ BUDGET",
                "The money on the left. It is banked at every tenth wave of a " +
                    "run and kept when the run ends. Deeper runs pay far more."
            ),
            GuideStep(
                "INSTALLED FIRMWARE",
                "Each firmware level adds +$percentPerLevel% damage to every agent " +
                    "and +${"%.2f".format(Balance.FIRMWARE_CRYPTO_PER_LEVEL * 100)}% " +
                    "\u25C7 crypto earned in runs (up to double), in every future " +
                    "match. It never goes away."
            ),
            GuideStep(
                "INSTALL",
                "+1, +10 and +100 buy that many levels. INSTALL MAX spends as " +
                    "much as you can afford. Each level costs a little more."
            ),
            GuideStep(
                "WHY IT MATTERS",
                "Stuck at a wave? Bank budget, install firmware, and the same " +
                    "board hits harder next run."
            )
        )
}
