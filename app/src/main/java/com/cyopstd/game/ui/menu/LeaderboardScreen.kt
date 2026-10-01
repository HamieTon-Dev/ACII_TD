package com.cyopstd.game.ui.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.platform.testTag
import com.cyopstd.game.core.GameMap
import com.cyopstd.game.core.GameMode
import com.cyopstd.game.core.Maps
import com.cyopstd.game.save.BoardKey
import com.cyopstd.game.save.LeaderboardGateway
import com.cyopstd.game.save.LeaderboardEntry
import com.cyopstd.game.save.PlayerIdentity
import com.cyopstd.game.ui.common.AsciiRule
import com.cyopstd.game.ui.common.Caption
import com.cyopstd.game.ui.common.CompactButton
import com.cyopstd.game.ui.common.ScreenScaffold
import com.cyopstd.game.ui.common.StatRow
import com.cyopstd.game.ui.common.TerminalPanel
import com.cyopstd.game.ui.theme.Palette

/**
 * The leaderboard, and where a player claims a name.
 *
 * Rank is by wave reached with damage as the tiebreak, which is the ordering
 * the entries themselves define — the screen sorts nothing, it displays what
 * the gateway returns, so the local board and a synced one can never disagree
 * about who is first.
 *
 * Two views. THIS DEVICE is every run played here and works offline.
 * WORLDWIDE is Google Play Games: each player's best, listed under the
 * callsign they registered here. Both follow the LEVEL and DIFFICULTY
 * drop-downs (owner, 2026-10-01: boards "all per level and difficulty").
 * WORLDWIDE is offered only when the build has at least one board.
 */
@Composable
fun LeaderboardScreen(
    identity: PlayerIdentity,
    entries: List<LeaderboardEntry>,
    backgroundAnimation: Boolean,
    onRegister: (String) -> Unit,
    onBack: () -> Unit,
    /** Whether a worldwide board exists for a difficulty on a level (null = all levels). */
    hasGlobalBoard: (BoardKey) -> Boolean = { false },
    /** False hides the WORLDWIDE view entirely. */
    anyGlobalBoard: Boolean = false,
    /** Null until read, or when it could not be. */
    globalEntries: List<LeaderboardEntry>? = null,
    globalLoading: Boolean = false,
    /** True when the player is signed into Play Games. */
    signedIn: Boolean = false,
    onShowGlobal: (BoardKey) -> Unit = {},
    onOpenNative: (BoardKey) -> Unit = {}
) {
    var worldwide by remember { mutableStateOf(false) }
    // Null is ALL LEVELS / ALL DIFFICULTIES.
    var level by remember { mutableStateOf<GameMap?>(null) }
    var mode by remember { mutableStateOf<GameMode?>(null) }
    val key = mode?.let { BoardKey(it, level?.id) }
    // Reads the state afresh: a pick changes it in the same click that refreshes.
    fun refresh() {
        val current = mode?.let { BoardKey(it, level?.id) } ?: return
        if (worldwide && hasGlobalBoard(current)) onShowGlobal(current)
    }
    ScreenScaffold(
        title = "LEADERBOARD",
        subtitle = if (identity.registered) "AGENT ${identity.username}" else "UNREGISTERED",
        onBack = onBack,
        backgroundAnimation = backgroundAnimation
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1.4f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Picker(
                        label = level?.displayName ?: "ALL LEVELS",
                        options = listOf<GameMap?>(null) + Maps.all,
                        text = { it?.let { m -> "L${Maps.all.indexOf(m) + 1} \u00B7 ${m.displayName}" } ?: "ALL LEVELS" },
                        tag = "board-level",
                        onPick = { level = it; refresh() },
                        modifier = Modifier.weight(1f)
                    )
                    Picker(
                        label = mode?.runName ?: "ALL DIFFICULTIES",
                        options = listOf<GameMode?>(null) + GameMode.entries,
                        text = { it?.runName ?: "ALL DIFFICULTIES" },
                        tag = "board-difficulty",
                        onPick = { mode = it; refresh() },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (anyGlobalBoard) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CompactButton(
                            text = "THIS DEVICE",
                            onClick = { worldwide = false },
                            accent = if (!worldwide) Palette.Crypto else Palette.TextMuted,
                            modifier = Modifier.weight(1f)
                        )
                        CompactButton(
                            text = "WORLDWIDE",
                            onClick = { worldwide = true; refresh() },
                            accent = if (worldwide) Palette.Green else Palette.TextMuted,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
                val boardName = (level?.displayName ?: "ALL LEVELS") + " \u00B7 " + (mode?.runName ?: "ALL DIFFICULTIES")
                if (worldwide) {
                    when {
                        key == null -> TerminalPanel(title = "WORLDWIDE", accent = Palette.Green) {
                            Caption("Pick a difficulty to see its worldwide board.")
                        }
                        !hasGlobalBoard(key) -> TerminalPanel(title = "WORLDWIDE", accent = Palette.Green) {
                            Caption("No worldwide board for $boardName yet. Runs there are recorded on this device.")
                        }
                        else -> GlobalPanel(
                            title = boardName,
                            identity = identity,
                            entries = globalEntries,
                            loading = globalLoading,
                            signedIn = signedIn,
                            onOpenNative = { onOpenNative(key) }
                        )
                    }
                } else TerminalPanel(title = boardName, accent = Palette.Crypto) {
                    val shown = entries.filter { it.matches(level?.id, mode) }
                        .sortedWith(LeaderboardEntry.ranking)
                        .take(LeaderboardGateway.MAX_ENTRIES)
                    if (shown.isEmpty()) {
                        Caption(
                            if (entries.isEmpty()) "No runs recorded yet. Finish a run and it will be " +
                                "listed here, ranked by the wave you reached."
                            else "No runs recorded on $boardName yet."
                        )
                    } else {
                        HeaderRow()
                        AsciiRule(color = Palette.Divider)
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            shown.forEachIndexed { index, entry ->
                                EntryRow(
                                    rank = index + 1,
                                    entry = entry,
                                    isYou = entry.username == identity.username
                                )
                            }
                        }
                    }
                }
            }

            Column(Modifier.weight(1f)) {
                RegistrationPanel(identity, onRegister)
                Spacer(Modifier.height(12.dp))
                TerminalPanel(title = "YOUR RECORD", accent = Palette.Cyan) {
                    StatRow("BEST WAVE", identity.highestWave.toString(), valueColor = Palette.Green)
                    StatRow(
                        "BEST DAMAGE",
                        identity.bestDamage.toString(),
                        valueColor = Palette.Crypto
                    )
                    AsciiRule(color = Palette.Divider)
                    Caption(
                        "Rank is set by the deepest wave you have reached. " +
                            "Damage dealt breaks a tie."
                    )
                }
            }
        }
    }
}

@Composable
private fun GlobalPanel(
    title: String,
    identity: PlayerIdentity,
    entries: List<LeaderboardEntry>?,
    loading: Boolean,
    signedIn: Boolean,
    onOpenNative: () -> Unit
) {
    TerminalPanel(title = "WORLDWIDE \u00B7 $title", accent = Palette.Green) {
        when {
            !signedIn -> Caption(
                "Link your Google account on the GOOGLE PLAY screen to see the " +
                    "global board and post to it. Your runs are still recorded " +
                    "on this device."
            )
            loading -> Caption("Asking Google Play Games…")
            entries == null -> Caption(
                "Google Play Games could not be reached. Your runs are recorded " +
                    "on this device and your best is posted next time a run ends " +
                    "while you are online."
            )
            entries.isEmpty() -> Caption("No scores posted yet. Finish a run to be first.")
            else -> {
                HeaderRow()
                AsciiRule(color = Palette.Divider)
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    entries.forEachIndexed { index, entry ->
                        EntryRow(
                            rank = index + 1,
                            entry = entry,
                            isYou = identity.registered && entry.username == identity.username
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        AsciiRule(color = Palette.Divider)
        Caption(
            "Each player's best wave, worldwide, listed by callsign. Register one " +
                "on the right, or Google shows your Play Games name instead."
        )
        Spacer(Modifier.height(8.dp))
        CompactButton(
            text = "OPEN IN GOOGLE PLAY GAMES",
            onClick = onOpenNative,
            enabled = signedIn,
            accent = Palette.Green,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun RegistrationPanel(identity: PlayerIdentity, onRegister: (String) -> Unit) {
    var editing by remember { mutableStateOf(false) }

    TerminalPanel(
        title = if (identity.registered) "CHANGE CALLSIGN" else "REGISTER CALLSIGN",
        accent = Palette.Green
    ) {
        Text(
            text = if (identity.registered) identity.username else "NOT SET",
            style = TextStyle(
                color = if (identity.registered) Palette.TextPrimary else Palette.TextMuted,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            ),
            modifier = Modifier.padding(vertical = 8.dp)
        )
        AsciiRule(color = Palette.Divider)
        Caption(
            "${PlayerIdentity.MIN_LENGTH}-${PlayerIdentity.MAX_LENGTH} characters. " +
                "A-Z, 0-9, dash and underscore."
        )
        Spacer(Modifier.height(8.dp))
        CompactButton(
            text = if (identity.registered) "EDIT CALLSIGN" else "ENTER CALLSIGN",
            onClick = { editing = true },
            accent = Palette.Green,
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (editing) {
        CallsignEntry(
            identity = identity,
            onSave = { name ->
                onRegister(name)
                editing = false
            },
            onCancel = { editing = false }
        )
    }
}

/**
 * Typing a callsign, in a box pinned to the top of the screen.
 *
 * The field used to sit in the right-hand panel, and in landscape the keyboard
 * covers most of the screen, so players typed into a field they could not see
 * (owner, 2026-09-26). This floats above everything at the top, where the
 * keyboard never reaches, and opens the keyboard itself.
 */
@Composable
internal fun CallsignEntry(
    identity: PlayerIdentity,
    onSave: (String) -> Unit,
    onCancel: () -> Unit
) {
    var typed by remember(identity.username) { mutableStateOf(identity.username) }
    val cleaned = PlayerIdentity.sanitize(typed)
    val valid = PlayerIdentity.isValid(typed) && cleaned != identity.username
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onCancel,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .background(Palette.Surface, RoundedCornerShape(6.dp))
                    .border(1.dp, Palette.Green.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = if (identity.registered) "CHANGE CALLSIGN" else "REGISTER CALLSIGN",
                    style = MaterialTheme.typography.titleSmall,
                    color = Palette.Green
                )
                // Sanitised as it is typed rather than on submit, so what is on
                // screen is exactly what will be stored.
                BasicTextField(
                    value = typed,
                    onValueChange = { typed = PlayerIdentity.sanitize(it) },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Palette.TextPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    ),
                    cursorBrush = SolidColor(Palette.Cyan),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Characters,
                        autoCorrectEnabled = false,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onDone = { if (valid) onSave(cleaned) }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .focusRequester(focus)
                )
                // Open the keyboard once the box is on screen. Asking any
                // earlier -- before the dialog's own window is attached --
                // throws, so it waits a frame and never fails the screen.
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    androidx.compose.runtime.withFrameNanos { }
                    runCatching { focus.requestFocus() }
                }
                AsciiRule(color = Palette.Divider)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompactButton(
                        text = "CANCEL",
                        onClick = onCancel,
                        accent = Palette.TextSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    CompactButton(
                        text = "SAVE",
                        onClick = { onSave(cleaned) },
                        enabled = valid,
                        accent = Palette.Green,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderRow() {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Cell("#", 44.dp, Palette.TextMuted)
        Cell("CALLSIGN", 170.dp, Palette.TextMuted)
        Cell("LVL", 52.dp, Palette.TextMuted)
        Cell("WAVE", 78.dp, Palette.TextMuted)
        Cell("DAMAGE", 120.dp, Palette.TextMuted)
        Cell("MODE", 120.dp, Palette.TextMuted)
    }
}

@Composable
private fun EntryRow(rank: Int, entry: LeaderboardEntry, isYou: Boolean) {
    val accent = when {
        isYou -> Palette.Cyan
        rank == 1 -> Palette.Crypto
        else -> Palette.TextSecondary
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Cell(rank.toString(), 44.dp, if (rank <= 3) Palette.Crypto else Palette.TextMuted)
        Cell(entry.username.ifBlank { "UNREGISTERED" }, 170.dp, accent)
        Cell(
            entry.mapId?.let { id -> Maps.all.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { "L${it + 1}" } } ?: "\u2014",
            52.dp, Palette.TextSecondary
        )
        Cell(entry.wave.toString(), 78.dp, Palette.Green)
        Cell(entry.damage.toString(), 120.dp, Palette.TextSecondary)
        Cell(
            entry.mode.runName,
            120.dp,
            if (entry.mode == GameMode.STANDARD) Palette.TextMuted else Palette.Orange
        )
    }
}

@Composable
private fun Cell(text: String, width: androidx.compose.ui.unit.Dp, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.bodyMedium,
        fontFamily = FontFamily.Monospace,
        maxLines = 1,
        modifier = Modifier.width(width)
    )
}

/** A small drop-down for the board's LEVEL and DIFFICULTY. */
@Composable
private fun <T> Picker(
    label: String,
    options: List<T>,
    text: (T) -> String,
    tag: String,
    onPick: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(tag)
                .background(Palette.SurfaceRaised, RoundedCornerShape(4.dp))
                .border(1.dp, Palette.Crypto.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                .clickable(role = androidx.compose.ui.semantics.Role.DropdownList) { open = true }
                .padding(horizontal = 10.dp, vertical = 9.dp)
        ) {
            Text(
                label, style = MaterialTheme.typography.labelMedium, color = Palette.TextPrimary,
                maxLines = 1, modifier = Modifier.weight(1f)
            )
            Text("\u25BC", style = MaterialTheme.typography.labelMedium, color = Palette.Crypto)
        }
        androidx.compose.material3.DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = Palette.SurfaceRaised,
            modifier = Modifier.heightIn(max = 300.dp)
        ) {
            for (option in options) {
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(text(option), style = MaterialTheme.typography.labelMedium, color = Palette.TextPrimary) },
                    onClick = { open = false; onPick(option) }
                )
            }
        }
    }
}
