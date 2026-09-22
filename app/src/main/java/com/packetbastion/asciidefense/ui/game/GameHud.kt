package com.packetbastion.asciidefense.ui.game

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
import com.packetbastion.asciidefense.engine.RunPhase
import com.packetbastion.asciidefense.state.HudSnapshot
import com.packetbastion.asciidefense.ui.theme.Palette

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

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Palette.Surface)
            .border(1.dp, borderColor.copy(alpha = 0.7f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HudCell(
            label = "WAVE",
            value = if (hud.wave == 0) "--" else hud.wave.toString(),
            accent = if (hud.nextWaveIsBoss || alert) Palette.Red else Palette.Cyan,
            trailing = if (hud.wave > 0 && hud.wave % 5 == 0) "BOSS" else null
        )

        HudDivider()

        HudCell(
            label = "BEST",
            value = hud.bestWave.toString(),
            accent = Palette.Purple
        )

        HudDivider()

        // Server integrity: number, ASCII bar, and colour. Three redundant
        // signals so the state is never carried by colour alone.
        Column(Modifier.weight(1.4f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "CORE-SERVER",
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextMuted
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "HP ${hud.serverHp} / ${hud.serverMaxHp}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.healthColor(hud.serverFraction)
                )
            }
            IntegrityBar(hud.serverFraction)
        }

        HudDivider()

        HudCell(
            label = "◇ CRYPTO",
            value = hud.crypto.toString(),
            accent = Palette.Crypto
        )

        HudDivider()

        HudCell(
            label = "ENEMIES",
            value = if (hud.phase == RunPhase.PREPARING) "0" else hud.enemiesRemaining.toString(),
            accent = if (hud.enemiesRemaining > 0) Palette.Orange else Palette.TextMuted,
            trailing = if (hud.enemiesOnField > 0) "${hud.enemiesOnField} live" else null
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.HudCell(
    label: String,
    value: String,
    accent: Color,
    trailing: String? = null
) {
    Column(Modifier.weight(1f)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Palette.TextMuted
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = accent
            )
            if (trailing != null) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = trailing,
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextSecondary
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
            .height(34.dp)
            .background(Palette.Divider)
            .padding(horizontal = 4.dp)
    )
    Spacer(Modifier.width(10.dp))
}

/** `[########--]` integrity meter drawn as a real bar with an ASCII fallback feel. */
@Composable
private fun IntegrityBar(fraction: Float) {
    val clamped = fraction.coerceIn(0f, 1f)
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .background(Palette.SurfaceSunken, RoundedCornerShape(2.dp))
            .border(1.dp, Palette.Divider, RoundedCornerShape(2.dp))
    ) {
        Box(
            Modifier
                .fillMaxWidth(clamped)
                .height(8.dp)
                .background(Palette.healthColor(clamped), RoundedCornerShape(2.dp))
        )
    }
}
