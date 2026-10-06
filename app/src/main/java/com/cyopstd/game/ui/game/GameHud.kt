package com.cyopstd.game.ui.game

import com.cyopstd.game.i18n.tr

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.cyopstd.game.engine.RunPhase
import com.cyopstd.game.state.HudSnapshot
import com.cyopstd.game.ui.theme.Palette

/**
 * The top status strip: wave, integrity, crypto, packets. Compose owns this —
 * it is a handful of text nodes that change a few times a second, exactly the
 * workload Compose is good at, and it stays out of the Canvas hot path.
 */
@Composable
fun GameHud(
    hud: HudSnapshot,
    modifier: Modifier = Modifier
) {
    val alert = hud.bossOnField || hud.phase == RunPhase.BOSS_WARNING
    val borderColor = when {
        alert -> Palette.Red
        hud.serverFraction <= 0.3f -> Palette.Orange
        else -> Palette.Divider
    }

    // ☆2 (owner, 2026-09-28): smaller throughout, to make room for € earned
    // this run, and every value on one line so nothing can wrap into its
    // neighbour on a narrow phone.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Palette.Surface)
            .border(1.dp, borderColor.copy(alpha = 0.7f))
            .padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HudCell(
            label = tr("WAVE"),
            value = if (hud.wave == 0) "--" else hud.wave.toString(),
            accent = if (hud.nextWaveIsBoss || alert) Palette.Red else Palette.Cyan,
            trailing = if (hud.wave > 0 && hud.wave % 5 == 0) tr("BOSS") else null
        )

        HudDivider()

        HudCell(
            label = tr("BEST"),
            value = hud.bestWave.toString(),
            accent = Palette.Purple
        )

        HudDivider()

        // Server integrity: number, bar, and colour. Three redundant signals
        // so the state is never carried by colour alone.
        Column(Modifier.weight(1.6f)) {
            Text(
                text = "CORE-SERVER",
                style = HudLabel,
                color = Palette.TextMuted,
                maxLines = 1
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    // Integrity, not "HP": every other surface calls it that.
                    text = "${hud.serverHp}/${hud.serverMaxHp}",
                    style = HudValue,
                    color = Palette.healthColor(hud.serverFraction),
                    maxLines = 1
                )
                Spacer(Modifier.width(6.dp))
                Box(Modifier.weight(1f)) { IntegrityBar(hud.serverFraction) }
            }
        }

        HudDivider()

        HudCell(
            label = tr("\u25C7 CRYPTO"),
            value = hud.crypto.toString(),
            accent = Palette.Crypto
        )

        HudDivider()

        HudCell(
            label = tr("\u20AC THIS RUN"),
            value = "\u20AC${hud.budgetEarned}",
            accent = Palette.Cyan,
            tag = "hud-run-budget"
        )

        HudDivider()

        HudCell(
            label = tr("ENEMIES"),
            value = if (hud.phase == RunPhase.PREPARING) "0" else hud.enemiesRemaining.toString(),
            accent = if (hud.enemiesRemaining > 0) Palette.Orange else Palette.TextMuted,
            trailing = if (hud.enemiesOnField > 0) tr("{0} live", hud.enemiesOnField) else null
        )
    }
}

private val HudLabel = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 8.5.sp,
    letterSpacing = 0.6.sp,
    lineHeight = 10.sp
)

private val HudValue = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Bold,
    fontSize = 13.sp,
    letterSpacing = 0.6.sp,
    lineHeight = 15.sp
)

@Composable
private fun androidx.compose.foundation.layout.RowScope.HudCell(
    label: String,
    value: String,
    accent: Color,
    trailing: String? = null,
    tag: String? = null
) {
    Column(Modifier.weight(1f).then(if (tag != null) Modifier.testTag(tag) else Modifier)) {
        Text(
            text = label,
            style = HudLabel,
            color = Palette.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                style = HudValue,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (trailing != null) {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = trailing,
                    style = HudLabel,
                    color = Palette.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun HudDivider() {
    Box(
        Modifier
            .width(1.dp)
            .height(24.dp)
            .background(Palette.Divider)
    )
    Spacer(Modifier.width(8.dp))
}

/** Integrity meter drawn as a real bar. */
@Composable
private fun IntegrityBar(fraction: Float) {
    val clamped = fraction.coerceIn(0f, 1f)
    Box(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .background(Palette.SurfaceSunken, RoundedCornerShape(2.dp))
            .border(1.dp, Palette.Divider, RoundedCornerShape(2.dp))
    ) {
        Box(
            Modifier
                .fillMaxWidth(clamped)
                .height(6.dp)
                .background(Palette.healthColor(clamped), RoundedCornerShape(2.dp))
        )
    }
}
