package com.cyopstd.game.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.MaterialTheme
import com.cyopstd.game.ui.theme.Palette

/**
 * Shared chrome for every screen. All controls here are sized for thumbs, not
 * cursors: the minimum interactive height is 52dp, comfortably above the 48dp
 * Android touch-target guideline.
 */

const val MIN_TOUCH_HEIGHT_DP = 52

/** A bordered terminal panel with an optional title rendered in its top rule. */
@Composable
fun TerminalPanel(
    modifier: Modifier = Modifier,
    title: String? = null,
    accent: Color = Palette.Cyan,
    contentPadding: androidx.compose.foundation.layout.PaddingValues =
        androidx.compose.foundation.layout.PaddingValues(14.dp),
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .background(Palette.Surface, RoundedCornerShape(6.dp))
            .border(BorderStroke(1.dp, accent.copy(alpha = 0.45f)), RoundedCornerShape(6.dp))
            .padding(contentPadding)
    ) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = accent
            )
            AsciiRule(color = accent.copy(alpha = 0.4f))
            Spacer(Modifier.height(8.dp))
        }
        content()
    }
}

/** A dashed ASCII horizontal rule used instead of a plain divider line. */
@Composable
fun AsciiRule(
    modifier: Modifier = Modifier,
    color: Color = Palette.Divider,
    pattern: String = "·"
) {
    Text(
        text = pattern.repeat(120),
        maxLines = 1,
        color = color,
        fontSize = 10.sp,
        modifier = modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodySmall
    )
}

/**
 * The game's primary button: a wide, monospace, bracketed label that glows when
 * pressed. Disabled buttons stay visible but dim, so the player can see that
 * CONTINUE exists before they have a save to continue.
 */
@Composable
fun BastionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Color = Palette.Cyan,
    subtitle: String? = null,
    leadingGlyph: String? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val glow by animateFloatAsState(
        targetValue = if (pressed && enabled) 1f else 0f,
        label = "buttonGlow"
    )

    val effectiveAccent = if (enabled) accent else Palette.TextMuted
    val background = if (enabled) {
        Palette.SurfaceRaised.copy(alpha = 0.55f + glow * 0.35f)
    } else {
        Palette.Surface.copy(alpha = 0.4f)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = MIN_TOUCH_HEIGHT_DP.dp)
            .background(background, RoundedCornerShape(4.dp))
            .border(
                BorderStroke(
                    if (pressed && enabled) 2.dp else 1.dp,
                    effectiveAccent.copy(alpha = if (enabled) 0.55f + glow * 0.45f else 0.3f)
                ),
                RoundedCornerShape(4.dp)
            )
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (pressed && enabled) ">" else " ",
            color = effectiveAccent,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.width(8.dp))
        if (leadingGlyph != null) {
            Text(
                text = leadingGlyph,
                color = effectiveAccent,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.width(44.dp)
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = text,
                color = if (enabled) Palette.TextPrimary else Palette.TextMuted,
                style = MaterialTheme.typography.labelLarge
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = if (enabled) Palette.TextSecondary else Palette.TextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

/** A compact action button for in-game control bars. */
@Composable
fun CompactButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    accent: Color = Palette.Cyan
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val effectiveAccent = when {
        !enabled -> Palette.TextMuted
        selected -> accent
        else -> accent.copy(alpha = 0.7f)
    }

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 46.dp, minWidth = 62.dp)
            .background(
                when {
                    !enabled -> Palette.Surface.copy(alpha = 0.35f)
                    selected -> accent.copy(alpha = 0.22f)
                    pressed -> Palette.SurfaceRaised
                    else -> Palette.SurfaceRaised.copy(alpha = 0.6f)
                },
                RoundedCornerShape(4.dp)
            )
            .border(
                BorderStroke(if (selected) 2.dp else 1.dp, effectiveAccent.copy(alpha = 0.6f)),
                RoundedCornerShape(4.dp)
            )
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (enabled) Palette.TextPrimary else Palette.TextMuted,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center
        )
    }
}

/** Label / value pair, monospace-aligned. */
@Composable
fun StatRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = Palette.TextPrimary
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Palette.TextSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = value,
            color = valueColor,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Settings toggle with a large tap area covering the whole row. */
@Composable
fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = MIN_TOUCH_HEIGHT_DP.dp)
            .clickable(role = Role.Switch) { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                color = Palette.TextPrimary,
                style = MaterialTheme.typography.bodyLarge
            )
            if (description != null) {
                Text(
                    text = description,
                    color = Palette.TextMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        // Text state alongside the switch: never rely on the switch colour alone.
        Text(
            text = if (checked) "ON " else "OFF",
            color = if (checked) Palette.Green else Palette.TextMuted,
            style = MaterialTheme.typography.labelMedium
        )
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Palette.Green,
                checkedTrackColor = Palette.GreenDim.copy(alpha = 0.6f),
                uncheckedThumbColor = Palette.TextMuted,
                uncheckedTrackColor = Palette.Surface
            )
        )
    }
}

/** Volume-style slider with an ASCII bar readout. */
@Composable
fun SliderRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                color = Palette.TextPrimary,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = asciiMeter(value),
                color = Palette.Cyan,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = Palette.Cyan,
                activeTrackColor = Palette.Cyan.copy(alpha = 0.8f),
                inactiveTrackColor = Palette.Divider
            ),
            modifier = Modifier.height(40.dp)
        )
    }
}

/** Renders a 0..1 value as `[####------] 40%`. */
fun asciiMeter(value: Float, cells: Int = 10): String {
    val clamped = value.coerceIn(0f, 1f)
    val filled = (clamped * cells).toInt().coerceIn(0, cells)
    val bar = "#".repeat(filled) + "-".repeat(cells - filled)
    val percent = (clamped * 100).toInt()
    return "[$bar] ${percent.toString().padStart(3)}%"
}

/** Small glyph badge used for agent/enemy symbols in lists. */
@Composable
fun GlyphBadge(
    glyph: String,
    color: Color,
    modifier: Modifier = Modifier,
    sizeDp: Int = 44
) {
    Box(
        modifier = modifier
            .size(sizeDp.dp)
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
            .border(BorderStroke(1.dp, color.copy(alpha = 0.6f)), RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = glyph,
            color = color,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1
        )
    }
}

/** Dim, non-interactive caption text. */
@Composable
fun Caption(text: String, modifier: Modifier = Modifier, color: Color = Palette.TextMuted) {
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.bodySmall,
        modifier = modifier.alpha(0.95f)
    )
}
