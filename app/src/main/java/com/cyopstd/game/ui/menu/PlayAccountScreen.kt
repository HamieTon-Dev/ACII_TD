package com.cyopstd.game.ui.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cyopstd.game.save.PlayerIdentity
import com.cyopstd.game.store.BillingStatus
import com.cyopstd.game.store.Entitlements
import com.cyopstd.game.store.Sku
import com.cyopstd.game.ui.common.AsciiRule
import com.cyopstd.game.ui.common.Caption
import com.cyopstd.game.ui.common.CompactButton
import com.cyopstd.game.ui.common.ScreenScaffold
import com.cyopstd.game.ui.common.StatRow
import com.cyopstd.game.ui.common.TerminalPanel
import com.cyopstd.game.ui.theme.Palette

/**
 * The Google Play account screen.
 *
 * Its job is to answer one question honestly: *where do my purchases live, and
 * what happens to them when I change phone?* Everything here follows from one
 * fact the screen refuses to obscure — **this game has no account of its own.**
 * There is no login, no password and no server holding your progress. Purchases
 * belong to the Google account already signed into the Play Store on the
 * device, which is why the only controls here are RESTORE and links into Play
 * itself.
 *
 * Saying that plainly is worth more than a reassuring "signed in" badge would
 * be. A player who believes there is a CyOps account will expect their waves
 * and their € to follow them to a new phone, and they will not, so the screen
 * says which of the two does.
 */
@Composable
fun PlayAccountScreen(
    entitlements: Entitlements,
    identity: PlayerIdentity,
    budget: Long,
    status: BillingStatus,
    adsConfigured: Boolean,
    backgroundAnimation: Boolean,
    onRestore: () -> Unit,
    onOpenOrders: () -> Unit,
    onOpenListing: () -> Unit,
    onCallsign: () -> Unit,
    onBack: () -> Unit
) {
    ScreenScaffold(
        title = "GOOGLE PLAY",
        subtitle = "Purchases, restores and what leaves this device",
        onBack = onBack,
        backgroundAnimation = backgroundAnimation
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---- Left: the connection and what it means -------------------
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                TerminalPanel(title = "CONNECTION", accent = statusAccent(status)) {
                    StatRow(
                        "GOOGLE PLAY BILLING",
                        statusLabel(status),
                        valueColor = statusAccent(status)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = statusExplanation(status),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.TextSecondary
                    )
                    Spacer(Modifier.height(10.dp))
                    CompactButton(
                        text = "RESTORE PURCHASES",
                        onClick = onRestore,
                        enabled = status != BillingStatus.UNAVAILABLE,
                        accent = Palette.Cyan,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    Caption(
                        "Restore re-asks Play what this Google account owns. It " +
                            "is safe to press at any time and never charges you."
                    )
                }

                Spacer(Modifier.height(12.dp))

                TerminalPanel(title = "HOW SIGN-IN WORKS", accent = Palette.Green) {
                    Text(
                        text = "There is no CyOps account and nothing to log into. " +
                            "Purchases follow the Google account signed into the " +
                            "Play Store on this device, so reinstalling the game " +
                            "or moving to a new phone keeps them — sign into Play " +
                            "with the same account and press RESTORE.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.TextSecondary
                    )
                    Spacer(Modifier.height(8.dp))
                    AsciiRule(color = Palette.Divider)
                    Spacer(Modifier.height(8.dp))
                    StatRow(
                        "PURCHASES",
                        "YOUR GOOGLE ACCOUNT",
                        valueColor = Palette.Green
                    )
                    StatRow(
                        "WAVES, € AND FIRMWARE",
                        "THIS DEVICE ONLY",
                        valueColor = Palette.Orange
                    )
                    Spacer(Modifier.height(6.dp))
                    Caption(
                        "Run progress is stored on the device, not in the cloud. A " +
                            "factory reset or an uninstall clears it; purchases " +
                            "survive both."
                    )
                }

                Spacer(Modifier.height(12.dp))

                TerminalPanel(title = "WHAT LEAVES THIS DEVICE", accent = Palette.CyanDim) {
                    StatRow(
                        "PURCHASE CHECKS",
                        "GOOGLE PLAY",
                        valueColor = Palette.Cyan
                    )
                    StatRow(
                        "ADVERTISING",
                        when {
                            entitlements.adsRemoved -> "REMOVED · NONE"
                            adsConfigured -> "ONE AD AFTER A LOST RUN"
                            else -> "NOT IN THIS BUILD"
                        },
                        valueColor = if (entitlements.adsRemoved) Palette.Green else Palette.TextPrimary
                    )
                    StatRow("LEADERBOARD", "ON THIS DEVICE", valueColor = Palette.TextPrimary)
                    StatRow("ANALYTICS", "NONE", valueColor = Palette.Green)
                    Spacer(Modifier.height(6.dp))
                    Caption(
                        "Nothing else is sent anywhere. The game is playable with " +
                            "no network at all."
                    )
                }
            }

            // ---- Right: what this account owns ----------------------------
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                TerminalPanel(title = "THIS ACCOUNT OWNS", accent = Palette.Crypto) {
                    StatRow(
                        "REMOVE ADS",
                        owningLabel(entitlements.adsRemoved),
                        valueColor = ownedColor(entitlements.adsRemoved)
                    )
                    StatRow(
                        "5× SPEED",
                        owningLabel(entitlements.fifthSpeedUnlocked),
                        valueColor = ownedColor(entitlements.fifthSpeedUnlocked)
                    )
                    StatRow(
                        "SPECTRUM AGENTS",
                        owningLabel(entitlements.spectrumAgents),
                        valueColor = ownedColor(entitlements.spectrumAgents)
                    )
                    StatRow(
                        "CORE-SERVER SKINS",
                        "${entitlements.coreSkins.size} / ${Sku.coreSkins.size}",
                        valueColor = ownedColor(entitlements.coreSkins.isNotEmpty())
                    )
                    StatRow(
                        "LIVING BACKGROUNDS",
                        "${entitlements.backgrounds.size} / ${Sku.backgrounds.size}",
                        valueColor = ownedColor(entitlements.backgrounds.isNotEmpty())
                    )
                    AsciiRule(color = Palette.Divider)
                    StatRow("€ BUDGET", budget.toString(), valueColor = Palette.Cyan)
                    Spacer(Modifier.height(6.dp))
                    Caption(
                        if (entitlements.ownedIds.isEmpty()) {
                            "Nothing purchased. Everything above can also be earned " +
                                "or simply played without."
                        } else {
                            "€ packs are spent, not owned: they are credited " +
                                "once and RESTORE does not hand them out again."
                        }
                    )
                }

                Spacer(Modifier.height(12.dp))

                TerminalPanel(title = "CALLSIGN", accent = Palette.Purple) {
                    StatRow(
                        "REGISTERED AS",
                        if (identity.registered) identity.username else "UNREGISTERED",
                        valueColor = if (identity.registered) Palette.Green else Palette.TextMuted
                    )
                    StatRow(
                        "BEST WAVE",
                        identity.highestWave.toString(),
                        valueColor = Palette.Crypto
                    )
                    Spacer(Modifier.height(6.dp))
                    Caption(
                        "The callsign is local to this device and is not a Google " +
                            "account. It names your runs on the leaderboard."
                    )
                    Spacer(Modifier.height(8.dp))
                    CompactButton(
                        text = if (identity.registered) "CHANGE CALLSIGN" else "REGISTER CALLSIGN",
                        onClick = onCallsign,
                        accent = Palette.Purple,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(12.dp))

                TerminalPanel(title = "MANAGE ON GOOGLE PLAY", accent = Palette.Blue) {
                    Caption(
                        "Refunds, payment methods and receipts are handled by " +
                            "Google, not by this game."
                    )
                    Spacer(Modifier.height(8.dp))
                    CompactButton(
                        text = "ORDER HISTORY & REFUNDS",
                        onClick = onOpenOrders,
                        accent = Palette.Blue,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    CompactButton(
                        text = "OPEN STORE LISTING",
                        onClick = onOpenListing,
                        accent = Palette.CyanDim,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

private fun statusLabel(status: BillingStatus): String = when (status) {
    BillingStatus.READY -> "CONNECTED"
    BillingStatus.CONNECTING -> "CONNECTING"
    BillingStatus.ERROR -> "UNREACHABLE"
    BillingStatus.UNAVAILABLE -> "NOT AVAILABLE"
}

private fun statusAccent(status: BillingStatus): Color = when (status) {
    BillingStatus.READY -> Palette.Green
    BillingStatus.CONNECTING -> Palette.Cyan
    BillingStatus.ERROR -> Palette.Orange
    BillingStatus.UNAVAILABLE -> Palette.TextMuted
}

private fun statusExplanation(status: BillingStatus): String = when (status) {
    BillingStatus.READY ->
        "Google Play answered. Purchases and restores work normally."
    BillingStatus.CONNECTING ->
        "Asking Google Play what this account owns. This usually takes a moment."
    BillingStatus.ERROR ->
        "Google Play could not be reached — usually no network. Anything " +
            "already bought is still yours; press RESTORE once you are back " +
            "online and it will reappear."
    BillingStatus.UNAVAILABLE ->
        "This build has no Google Play billing configured, or this device has " +
            "no Play Store. Nothing can be bought, and nothing that costs " +
            "money is required to play."
}

private fun owningLabel(owned: Boolean): String = if (owned) "OWNED" else "NOT OWNED"

private fun ownedColor(owned: Boolean): Color =
    if (owned) Palette.Green else Palette.TextMuted
