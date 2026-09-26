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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
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

            // RESTART throws the run away, and it sits one button below
            // RESUME, so it asks first (owner, 2026-09-26). NO returns to
            // this menu with nothing changed.
            var confirmingRestart by remember { mutableStateOf(false) }
            if (confirmingRestart) {
                Text(
                    text = "Are you sure you want to restart your run?",
                    style = MaterialTheme.typography.titleMedium,
                    color = Palette.Orange
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Wave $wave and everything deployed will be lost.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextMuted
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(Modifier.weight(1f)) {
                        BastionButton(
                            "YES",
                            onRestart,
                            accent = Palette.Orange,
                            leadingGlyph = "[o]"
                        )
                    }
                    Box(Modifier.weight(1f)) {
                        BastionButton(
                            "NO",
                            { confirmingRestart = false },
                            accent = Palette.Green,
                            leadingGlyph = "[<]"
                        )
                    }
                }
            } else {
                BastionButton("RESUME", onResume, accent = Palette.Green, leadingGlyph = "[>]")
                Spacer(Modifier.height(8.dp))
                BastionButton(
                    "RESTART",
                    { confirmingRestart = true },
                    accent = Palette.Orange,
                    leadingGlyph = "[o]"
                )
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
    reviveIsFree: Boolean = false,
    /** The player answered NO to the revive. */
    onDeclineRevive: () -> Unit = {}
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

                // The owner's flow: the question comes first, and RETRY / MAIN
                // MENU only appear once it has been answered. YES is the
                // rewarded ad (or nothing, with the pack); NO is where the
                // lost-run ad may play.
                Text(
                    text = "WOULD YOU LIKE TO REVIVE?",
                    style = MaterialTheme.typography.titleMedium,
                    color = Palette.Crypto,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(Modifier.weight(1f)) {
                        BastionButton(
                            text = when {
                                reviveAdShowing -> "LOADING AD…"
                                // A player who bought the pack must not be told
                                // they are about to watch an ad. They are not.
                                reviveIsFree -> "YES"
                                else -> "YES — WATCH AD"
                            },
                            onClick = { if (!reviveAdShowing) onWatchAdToRevive() },
                            accent = Palette.Crypto,
                            leadingGlyph = "[+]"
                        )
                    }
                    Box(Modifier.weight(1f)) {
                        BastionButton(
                            text = "NO",
                            onClick = { if (!reviveAdShowing) onDeclineRevive() },
                            accent = Palette.Red,
                            leadingGlyph = "[X]"
                        )
                    }
                }
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

            if (onWatchAdToRevive == null) {
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
 * The guided first run.
 *
 * It never blocks the battlefield: it sits in a corner card and most steps
 * advance as the player actually performs the action, so it teaches by doing
 * rather than by reading. The script lives in [TutorialScript] — what each card
 * says, what its arrow points at, and how it is cleared — because the card, the
 * arrow and the SKIP button all have to agree about the current step and three
 * separate `when`s over the same integer is three places to forget.
 */
@Composable
fun TutorialOverlay(
    step: Int,
    onAdvance: () -> Unit,
    onBriefing: (wanted: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val content = TutorialScript.stepAt(step) ?: return
    val body = if (step == TutorialScript.BRIEFING) TutorialBriefing.body() else content.body

    Column(
        modifier = modifier
            .widthIn(max = 380.dp)
            .heightIn(max = 330.dp)
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
            text = body,
            style = if (step == TutorialScript.BRIEFING) {
                MaterialTheme.typography.bodySmall
            } else {
                MaterialTheme.typography.bodyMedium
            },
            color = Palette.TextPrimary,
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
        )

        // SKIP is not in here; it sits in a screen corner so it is reachable on
        // the steps that wait for a specific tap and show no buttons at all.
        when (content.gate) {
            TutorialGate.ACKNOWLEDGE -> {
                Spacer(Modifier.height(10.dp))
                CompactButton(
                    text = "CONTINUE",
                    onClick = onAdvance,
                    accent = Palette.Green,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            TutorialGate.ASK_BRIEFING -> {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(Modifier.weight(1f)) {
                        CompactButton(
                            text = "YES, BRIEF ME",
                            onClick = { onBriefing(true) },
                            accent = Palette.Green,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Box(Modifier.weight(1f)) {
                        CompactButton(
                            text = "NO, LET ME PLAY",
                            onClick = { onBriefing(false) },
                            accent = Palette.TextSecondary,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            TutorialGate.ACTION -> Unit
        }
    }
}

/**
 * The arrow from the tutorial card to a readout drawn inside the Canvas.
 *
 * This is the piece with teeth. `WAVE 1` and `◇ 120` are drawn by
 * [BattlefieldRenderer] in world units; the card is Compose, laid out in screen
 * pixels. The arrow has to cross that boundary, which it does by taking the
 * renderer's own [FieldStatusAnchors] through the same [WorldTransform] the
 * battlefield was drawn with — so the arrow lands on the readout at any screen
 * size, letterboxing included, and cannot drift to where the readout used to
 * be.
 *
 * Drawn as a full-size overlay rather than positioned, because the line is
 * between two points and neither of them is the box's corner.
 */
@Composable
fun TutorialPointer(
    target: WorldRect,
    transform: WorldTransform,
    modifier: Modifier = Modifier
) {
    val pulse by rememberInfiniteTransition(label = "pointer").animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pointerPulse"
    )

    val screen = target.toScreen(transform)
    Canvas(modifier = modifier.fillMaxSize()) {
        val box = Rect(
            left = screen.left - 6f,
            top = screen.top - 6f,
            right = screen.right + 6f,
            bottom = screen.bottom + 6f
        )
        val colour = Palette.Green.copy(alpha = pulse)

        drawRect(
            color = colour,
            topLeft = Offset(box.left, box.top),
            size = Size(box.width, box.height),
            style = Stroke(width = 3f)
        )

        // The arrow comes in from the left, which is where the card is, and
        // stops short of the box so it never sits on top of the number it is
        // pointing at.
        val tipX = box.left - 10f
        val tipY = box.center.y
        val tailX = (tipX - size.width * 0.16f).coerceAtLeast(8f)
        drawLine(
            color = colour,
            start = Offset(tailX, tipY),
            end = Offset(tipX, tipY),
            strokeWidth = 3f
        )
        val head = 14f
        drawLine(
            color = colour,
            start = Offset(tipX, tipY),
            end = Offset(tipX - head, tipY - head * 0.6f),
            strokeWidth = 3f
        )
        drawLine(
            color = colour,
            start = Offset(tipX, tipY),
            end = Offset(tipX - head, tipY + head * 0.6f),
            strokeWidth = 3f
        )
    }
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
            text = if (nextIsBoss) {
                "NEXT: WAVE ${wave + 1} \u2014 BOSS \u00B7 TAP NEXT BOSS FOR THE BRIEFING"
            } else {
                "NEXT: WAVE ${wave + 1}"
            },
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
