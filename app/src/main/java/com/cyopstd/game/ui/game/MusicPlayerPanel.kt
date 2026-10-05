package com.cyopstd.game.ui.game

import com.cyopstd.game.i18n.tr

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cyopstd.game.R
import com.cyopstd.game.ui.theme.Palette

/** What the pause-menu music player shows. */
data class MusicPlayerState(
    /** False until level 5 is completed; every control then explains how to unlock it. */
    val unlocked: Boolean,
    /** Every track's label, in level order. */
    val tracks: List<String>,
    /** The chosen track, or null for the level's own music. */
    val selected: Int?,
    /** The music player's pause (music only). */
    val paused: Boolean
)

/** Shown when a locked music player is touched. */
val MUSIC_PLAYER_LOCKED_NOTE =
    tr("LOCKED · Complete level 5 (clear wave 100 on DDoS) to unlock the music player " +
        "and the track list.")

/** The label for following the level's own music. */
val LEVEL_MUSIC_LABEL = tr("LEVEL MUSIC (AUTO)")

/**
 * The pause menu's music player (owner, 2026-10-01): previous, play/pause and
 * next, and a drop-down of every level's tracks, in CoreUI icons. Locked until
 * level 5 is completed; while locked it is shown greyed and any touch says what
 * unlocks it.
 */
@Composable
fun MusicPlayerPanel(
    state: MusicPlayerState,
    onSelect: (Int?) -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    var note by remember { mutableStateOf<String?>(null) }
    var open by remember { mutableStateOf(false) }
    val accent = if (state.unlocked) Palette.Purple else Palette.TextMuted

    fun guarded(action: () -> Unit) {
        if (state.unlocked) {
            note = null
            action()
        } else {
            note = MUSIC_PLAYER_LOCKED_NOTE
        }
    }

    Column(
        modifier = modifier
            .testTag("music-player")
            .background(Palette.SurfaceRaised, RoundedCornerShape(6.dp))
            .border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(R.drawable.ic_music_note), contentDescription = null,
                tint = accent, modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                tr("MUSIC PLAYER"), style = MaterialTheme.typography.titleSmall,
                color = if (state.unlocked) Palette.TextPrimary else Palette.TextMuted,
                modifier = Modifier.weight(1f)
            )
            if (!state.unlocked) {
                Icon(
                    painterResource(R.drawable.ic_lock), contentDescription = tr("Locked"),
                    tint = Palette.TextPrimary,
                    modifier = Modifier
                        .size(16.dp)
                        .testTag("music-player-lock")
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        val dim = if (state.unlocked) 1f else 0.5f
        // The track list.
        Box(Modifier.alpha(dim)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("music-track-dropdown")
                    .background(Palette.Surface, RoundedCornerShape(4.dp))
                    .border(1.dp, accent.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                    .clickable(role = Role.DropdownList) { guarded { open = true } }
                    .padding(horizontal = 10.dp, vertical = 9.dp)
            ) {
                Text(
                    text = state.selected?.let { state.tracks.getOrNull(it) } ?: LEVEL_MUSIC_LABEL,
                    style = MaterialTheme.typography.labelMedium,
                    color = Palette.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text("▼", style = MaterialTheme.typography.labelMedium, color = accent)
            }
            DropdownMenu(
                expanded = open,
                onDismissRequest = { open = false },
                containerColor = Palette.SurfaceRaised,
                modifier = Modifier
                    .background(Palette.SurfaceRaised)
                    .border(1.dp, Palette.Purple.copy(alpha = 0.7f))
                    .heightIn(max = 300.dp)
            ) {
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(
                            painterResource(R.drawable.ic_media_level_music), contentDescription = null,
                            tint = Palette.Cyan, modifier = Modifier.size(16.dp)
                        )
                    },
                    text = {
                        Text(
                            LEVEL_MUSIC_LABEL, style = MaterialTheme.typography.labelMedium,
                            color = if (state.selected == null) Palette.Green else Palette.TextPrimary
                        )
                    },
                    onClick = { open = false; onSelect(null) }
                )
                state.tracks.forEachIndexed { index, label ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                label, style = MaterialTheme.typography.labelMedium,
                                color = if (state.selected == index) Palette.Green else Palette.TextPrimary
                            )
                        },
                        onClick = { open = false; onSelect(index) },
                        modifier = Modifier.testTag("music-track-$index")
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        // The transport.
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .alpha(dim)
        ) {
            TransportButton(R.drawable.ic_media_previous, tr("Previous track"), tr("music-previous"), accent,
                Modifier.weight(1f)) { guarded(onPrevious) }
            TransportButton(
                if (state.paused) R.drawable.ic_media_play else R.drawable.ic_media_pause,
                if (state.paused) tr("Play music") else tr("Pause music"), tr("music-play-pause"),
                if (state.unlocked) Palette.Green else Palette.TextMuted, Modifier.weight(1f)
            ) { guarded(onPlayPause) }
            TransportButton(R.drawable.ic_media_next, tr("Next track"), tr("music-next"), accent,
                Modifier.weight(1f)) { guarded(onNext) }
        }

        note?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                it, style = MaterialTheme.typography.bodySmall, color = Palette.Orange,
                modifier = Modifier.testTag("music-player-locked-note")
            )
        }
    }
}

@Composable
private fun TransportButton(
    @DrawableRes icon: Int,
    description: String,
    tag: String,
    tint: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(40.dp)
            .testTag(tag)
            .background(Palette.Surface, RoundedCornerShape(4.dp))
            .border(1.dp, tint.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
            .clickable(role = Role.Button, onClick = onClick)
    ) {
        Icon(painterResource(icon), contentDescription = description, tint = tint, modifier = Modifier.size(20.dp))
    }
}
