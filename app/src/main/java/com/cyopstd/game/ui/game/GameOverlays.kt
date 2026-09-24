package com.cyopstd.game.ui.game

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cyopstd.game.model.AgentType
import com.cyopstd.game.state.GameOverSummary
import com.cyopstd.game.ui.common.AsciiRule
import com.cyopstd.game.ui.common.BastionButton
import com.cyopstd.game.ui.common.CompactButton
import com.cyopstd.game.ui.common.StatRow
import com.cyopstd.game.ui.theme.Palette

/** Dim scrim shared by every modal overlay. */
@Composable
private fun Scrim(onDismiss: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.Background.copy(alpha = 0.88f))
            .then(
                if (onDismiss != null) Modifier.clickable(onClick = onDismiss) else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

// ------------------------------------------------------------------- pause

@Composable
fun PauseOverlay(
    wave: Int,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onSettings: () -> Unit,
    onMainMenu: () -> Unit
) {
    Scrim {
        Column(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .background(Palette.Surface, RoundedCornerShape(8.dp))
                .border(1.dp, Palette.Cyan.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "|| PAUSED",
                style = MaterialTheme.typography.headlineMedium,
                color = Palette.Cyan
            )
            Text(
                text = "WAVE $wave · SIMULATION HALTED",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextSecondary
            )
            AsciiRule(color = Palette.CyanDim)
            Spacer(Modifier.height(14.dp))

            BastionButton("RESUME", onResume, accent = Palette.Green, leadingGlyph = "[>]")
            Spacer(Modifier.height(8.dp))
            BastionButton("RESTART", onRestart, accent = Palette.Orange, leadingGlyph = "[o]")
            Spacer(Modifier.height(8.dp))
            BastionButton("SETTINGS", onSettings, leadingGlyph = "[*]")
            Spacer(Modifier.height(8.dp))
            BastionButton(
                text = "MAIN MENU",
                subtitle = "Progress is saved automatically",
                onClick = onMainMenu,
                accent = Palette.Red,
                leadingGlyph = "[X]"
            )
        }
    }
}

// --------------------------------------------------------------- game over

@Composable
fun GameOverOverlay(
    summary: GameOverSummary,
    onRetry: () -> Unit,
    onMainMenu: () -> Unit,
    /** Null when no revive can be offered; the button is then absent, not dead. */
    onWatchAdToRevive: (() -> Unit)? = null,
    revivesLeft: Int = 0,
    reviveAdShowing: Boolean = false,
    /** True when the REVIVE PACK has already paid for this; no ad is shown. */
    reviveIsFree: Boolean = false
) {
    val transition = rememberInfiniteTransition(label = "gameOver")
    val flash by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(760), RepeatMode.Reverse),
        label = "flash"
    )

    Scrim {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .background(Palette.Surface, RoundedCornerShape(8.dp))
                .border(2.dp, Palette.Red.copy(alpha = 0.75f), RoundedCornerShape(8.dp))
                .padding(22.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "NETWORK COMPROMISED",
                style = MaterialTheme.typography.headlineMedium,
                color = Palette.Red,
                modifier = Modifier.alpha(flash),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "CORE-SERVER INTEGRITY 0 · CONNECTION TERMINATED",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextMuted,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(12.dp))
            AsciiRule(color = Palette.RedDeep)
            Spacer(Modifier.height(10.dp))

            Column(Modifier.fillMaxWidth()) {
                StatRow(
                    "WAVE REACHED",
                    summary.waveReached.toString(),
                    valueColor = Palette.Crypto
                )
                StatRow(
                    "ATTACKS BLOCKED",
                    summary.attacksBlocked.toString(),
                    valueColor = Palette.Cyan
                )
                StatRow(
                    "CRYPTO EARNED",
                    "◇ ${summary.cryptoEarned}",
                    valueColor = Palette.Crypto
                )
                StatRow(
                    "BOSSES DEFEATED",
                    summary.bossesDefeated.toString(),
                    valueColor = Palette.Red
                )
                StatRow(
                    "BEST WAVE",
                    summary.bestWave.toString(),
                    valueColor = Palette.Green
                )
            }

            if (summary.isNewRecord) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = ">> NEW RECORD <<",
                    style = MaterialTheme.typography.titleMedium,
                    color = Palette.Green,
                    modifier = Modifier.alpha(flash)
                )
            }

            if (onWatchAdToRevive != null) {
                Spacer(Modifier.height(16.dp))
                AsciiRule(color = Palette.RedDeep)
                Spacer(Modifier.height(12.dp))

                BastionButton(
                    text = when {
                        reviveAdShowing -> "LOADING AD…"
                        // A player who bought the pack must not be told they
                        // are about to watch an ad. They are not.
                        reviveIsFree -> "CONTINUE"
                        else -> "WATCH AD TO CONTINUE"
                    },
                    onClick = { if (!reviveAdShowing) onWatchAdToRevive() },
                    accent = Palette.Crypto,
                    leadingGlyph = "[+]",
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    // Each clause is something a player would otherwise find
                    // out the hard way: what it costs, what it gives back, and
                    // how many are left.
                    text = "Resume this wave at half integrity. " +
                        (if (revivesLeft == 1) "One revive per run."
                        else "$revivesLeft revives left this run.") +
                        (if (reviveIsFree) " REVIVE PACK — no ad."
                        else " REMOVE ADS covers ads between runs; " +
                            "revive ads are separate."),
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextMuted,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(Modifier.weight(1f)) {
                    BastionButton("RETRY", onRetry, accent = Palette.Green, leadingGlyph = "[>]")
                }
                Box(Modifier.weight(1f)) {
                    BastionButton("MAIN MENU", onMainMenu, leadingGlyph = "[X]")
                }
            }
        }
    }
}

// ------------------------------------------------------------------ unlock

@Composable
fun UnlockBanner(type: AgentType, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "unlock")
    val glow by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(620), RepeatMode.Reverse),
        label = "glow"
    )

    Column(
        modifier = modifier
            .background(Palette.Surface.copy(alpha = 0.96f), RoundedCornerShape(6.dp))
            .border(2.dp, Palette.Green.copy(alpha = glow), RoundedCornerShape(6.dp))
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "NEW CYBER AGENT UNLOCKED",
            style = MaterialTheme.typography.labelMedium,
            color = Palette.Green
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = type.displayName,
            style = MaterialTheme.typography.titleLarge,
            color = Palette.Cyan
        )
        Text(
            text = "[${type.glyph}]",
            style = MaterialTheme.typography.headlineMedium,
            color = Palette.Cyan
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = type.abilitySummary,
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

// ---------------------------------------------------------------- tutorial

/**
 * A four-step, skippable first-play tutorial. It never blocks the battlefield:
 * it sits in a corner card and advances as the player actually performs each
 * action, so it teaches by doing rather than by reading.
 */
@Composable
fun TutorialOverlay(
    step: Int,
    onAdvance: () -> Unit,
    modifier: Modifier = Modifier
) {
    val content = tutorialContentFor(step) ?: return

    Column(
        modifier = modifier
            .widthIn(max = 380.dp)
            .background(Palette.SurfaceRaised.copy(alpha = 0.97f), RoundedCornerShape(6.dp))
            .border(1.dp, Palette.Green.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
            .padding(14.dp)
    ) {
        Text(
            text = content.title,
            style = MaterialTheme.typography.titleMedium,
            color = Palette.Green
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = content.body,
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.TextPrimary
        )
        // SKIP is not in here; it sits in the screen's top-right corner so it
        // is reachable on the steps that wait for a specific tap and show no
        // buttons at all. That leaves CONTINUE the full width of the card.
        if (content.showContinue) {
            Spacer(Modifier.height(10.dp))
            CompactButton(
                text = "CONTINUE",
                onClick = onAdvance,
                accent = Palette.Green,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private data class TutorialContent(
    val title: String,
    val body: String,
    val showContinue: Boolean
)

private fun tutorialContentFor(step: Int): TutorialContent? = when (step) {
    0 -> TutorialContent(
        title = "WELCOME TO CyOps TD",
        body = "Cyberattacks are inbound on CORE-SERVER. " +
            "Deploy Cyber Agents beside the routes to stop them before they land.",
        showContinue = true
    )
    1 -> TutorialContent(
        title = "STEP 1 — OPEN THE ROSTER",
        body = "Tap the AGENTS button in the control bar below.",
        showContinue = false
    )
    2 -> TutorialContent(
        title = "STEP 2 — PICK AN AGENT",
        body = "Select FIREWALL. It is cheap, reliable, and has no weaknesses.",
        showContinue = false
    )
    3 -> TutorialContent(
        title = "STEP 3 — DEPLOY",
        body = "Tap one of the highlighted deployment nodes beside a route. " +
            "Crypto is deducted when the agent lands.",
        showContinue = false
    )
    4 -> TutorialContent(
        title = "STEP 4 — START THE WAVE",
        body = "Tap NEXT WAVE. Your agents fire automatically. Every attack you " +
            "stop pays out ◇ Crypto, which buys more agents and upgrades.",
        showContinue = false
    )
    else -> null
}

// ------------------------------------------------------------- transient msg

@Composable
fun TransientMessage(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Palette.Surface.copy(alpha = 0.95f), RoundedCornerShape(4.dp))
            .border(1.dp, Palette.Red.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            color = Palette.Red
        )
    }
}

/**
 * Between-waves prompt.
 *
 * Deliberately one line tall. It floats over the battlefield, and the earlier
 * three-line version covered the top lane's deployment nodes — which is exactly
 * where you need to see to place an agent. The same information also lives
 * permanently in the control bar, so this only has to catch the eye, not
 * explain itself.
 */
@Composable
fun PreparationBanner(
    wave: Int,
    nextIsBoss: Boolean,
    autoStartIn: Int,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = if (nextIsBoss) Palette.Red else Palette.Cyan

    Row(
        modifier = modifier
            .background(Palette.Surface.copy(alpha = 0.92f), RoundedCornerShape(4.dp))
            .border(1.dp, accent.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
            .clickable(onClick = onDismiss)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (wave == 0) "PERIMETER READY" else "WAVE $wave SECURED",
            style = MaterialTheme.typography.labelMedium,
            color = Palette.Green,
            maxLines = 1
        )
        Text(
            text = "  \u00B7  ",
            style = MaterialTheme.typography.labelSmall,
            color = Palette.TextMuted
        )
        Text(
            text = if (nextIsBoss) "NEXT: WAVE ${wave + 1} \u2014 MAJOR BREACH" else "NEXT: WAVE ${wave + 1}",
            style = MaterialTheme.typography.labelMedium,
            color = if (nextIsBoss) Palette.Red else Palette.TextSecondary,
            maxLines = 1
        )
        if (autoStartIn > 0) {
            Text(
                text = "  \u00B7  AUTO $autoStartIn",
                style = MaterialTheme.typography.labelSmall,
                color = Palette.Crypto,
                maxLines = 1
            )
        }
    }
}
