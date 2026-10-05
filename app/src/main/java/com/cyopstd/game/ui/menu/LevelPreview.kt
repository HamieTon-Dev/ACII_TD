package com.cyopstd.game.ui.menu

import com.cyopstd.game.i18n.tr

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cyopstd.game.core.GameMap
import com.cyopstd.game.core.WorldGeometry
import com.cyopstd.game.model.BossVariant
import com.cyopstd.game.ui.common.Caption
import com.cyopstd.game.ui.common.CompactButton
import com.cyopstd.game.ui.game.BossIcon
import com.cyopstd.game.ui.game.bossUiColor
import com.cyopstd.game.ui.theme.Palette

/**
 * A level's preview (owner, 2026-10-02: "clicking and holding a level will
 * show a preview of what the map looks like, or a button that shows the map
 * and bosses info"). Opened by holding a level in the NEW RUN list or by its
 * info button: the map's routes and core in its own colours, and the bosses
 * that turn up there, its own first.
 */
@Composable
fun LevelPreviewDialog(
    map: GameMap,
    number: Int,
    unlocked: Boolean,
    onClose: () -> Unit
) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .widthIn(max = 760.dp)
                .background(Palette.Surface, RoundedCornerShape(8.dp))
                .border(1.dp, Palette.Cyan.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .padding(14.dp)
                .testTag("level-preview")
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "L$number · ${map.displayName}",
                    style = MaterialTheme.typography.titleMedium,
                    color = Palette.Cyan,
                    modifier = Modifier.weight(1f)
                )
                CompactButton(text = tr("CLOSE"), onClick = onClose, accent = Palette.TextSecondary)
            }
            Caption(if (unlocked) map.tagline else tr("LOCKED · {0}", map.unlockRequirement))
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                MapPicture(map, Modifier.weight(1.3f))
                BossList(map, Modifier.weight(1f))
            }
        }
    }
}

/** The routes and the core, drawn from the level's own waypoints and colours. */
@Composable
private fun MapPicture(map: GameMap, modifier: Modifier) {
    val theme = map.theme
    val backdrop = theme?.let { Color(it.backdrop) } ?: Palette.Background
    val laneFill = theme?.let { Color(it.laneFill) } ?: Color(0xFF173A2C)
    val laneBorder = theme?.let { Color(it.laneBorder) } ?: Color(0xFF2E7D5B)
    Canvas(
        modifier
            .aspectRatio(WorldGeometry.WIDTH / WorldGeometry.HEIGHT)
            .background(backdrop, RoundedCornerShape(4.dp))
            .border(1.dp, Palette.Divider, RoundedCornerShape(4.dp))
            .testTag("level-preview-map")
    ) {
        val scale = size.width / WorldGeometry.WIDTH
        fun at(x: Float, y: Float) = Offset(x.coerceAtLeast(0f) * scale, y * scale)
        for (lane in map.laneWaypoints) {
            val path = Path()
            lane.forEachIndexed { i, w ->
                val p = at(w.x, w.y)
                if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            drawPath(path, laneBorder, style = Stroke(CORRIDOR * scale + 4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(path, laneFill, style = Stroke(CORRIDOR * scale, cap = StrokeCap.Round, join = StrokeJoin.Round))
            // Where the attack comes in.
            val start = at(lane.first().x, lane.first().y)
            drawCircle(Palette.Red, 4.dp.toPx(), start)
        }
        // The core-server rack.
        val rackLeft = WorldGeometry.SERVER_X * scale
        drawRect(
            Palette.SurfaceRaised,
            Offset(rackLeft, RACK_TOP * scale),
            Size(size.width - rackLeft - 6f, (RACK_BOTTOM - RACK_TOP) * scale)
        )
        drawRect(
            Palette.Cyan,
            Offset(rackLeft, RACK_TOP * scale),
            Size(size.width - rackLeft - 6f, (RACK_BOTTOM - RACK_TOP) * scale),
            style = Stroke(2f)
        )
    }
}

/** The level's own boss first, then every other boss that can turn up there. */
@Composable
private fun BossList(map: GameMap, modifier: Modifier) {
    val own = BossVariant.entries.filter { it.mapId == map.id }
    val others = BossVariant.poolFor(LATE_CYCLE, map.id).filter { it !in own }
    Column(
        modifier
            .height(220.dp)
            .verticalScroll(rememberScrollState())
            .testTag("level-preview-bosses")
    ) {
        if (own.isNotEmpty()) {
            Text(tr("THIS LEVEL'S BOSSES"), style = MaterialTheme.typography.labelMedium, color = Palette.Red)
            Spacer(Modifier.height(4.dp))
            for (boss in own) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BossIcon(boss)
                    Spacer(Modifier.width(8.dp))
                    Text(boss.displayName, style = MaterialTheme.typography.titleSmall, color = bossUiColor(boss))
                }
                Caption(boss.signature)
                Spacer(Modifier.height(6.dp))
            }
        }
        Text(tr("ALSO TURNING UP HERE"), style = MaterialTheme.typography.labelMedium, color = Palette.TextSecondary)
        Spacer(Modifier.height(4.dp))
        for (boss in others) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                Box(Modifier.width(64.dp)) {
                    Text(boss.glyph, style = MaterialTheme.typography.labelMedium, color = bossUiColor(boss))
                }
                Text(boss.displayName, style = MaterialTheme.typography.labelMedium, color = Palette.TextPrimary)
            }
        }
    }
}

/** The lanes' corridor width, in world units. */
private const val CORRIDOR = 54f

/** The rack's top and bottom on the board, in world units. */
private const val RACK_TOP = 168f
private const val RACK_BOTTOM = 600f

/** Late enough that every boss a level can field is in its pool. */
private const val LATE_CYCLE = 40
