package com.cyopstd.game.ui.menu

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.cyopstd.game.store.CosmeticChoice
import com.cyopstd.game.store.Entitlements
import com.cyopstd.game.ui.common.AsciiRule
import com.cyopstd.game.ui.common.Caption
import com.cyopstd.game.ui.common.MIN_TOUCH_HEIGHT_DP
import com.cyopstd.game.ui.common.ScreenScaffold
import com.cyopstd.game.ui.common.TerminalPanel
import com.cyopstd.game.ui.theme.CoreSkin
import com.cyopstd.game.ui.theme.LivingBackground
import com.cyopstd.game.ui.theme.Palette

/**
 * Where a player puts on what they own.
 *
 * This screen exists because the store sold six core skins, five backgrounds
 * and an agent palette, and there was nowhere to *wear* any of them: the
 * choice was stored, the renderer read it, and nothing could set it. Buying
 * something you cannot equip is the worst version of a store.
 *
 * Two rules:
 *
 * 1. **Everything is listed, owned or not.** A locked row shows what it is and
 *    says where to get it, because a grid with the locked items hidden cannot
 *    tell a player what the store is for. Tapping a locked row does nothing —
 *    it is not a checkout funnel.
 * 2. **The free option is never locked.** TERMINAL and STATIC are always
 *    selectable, so there is always a way back to the plain game.
 */
@Composable
fun LoadoutScreen(
    entitlements: Entitlements,
    cosmetics: CosmeticChoice,
    backgroundAnimation: Boolean,
    onChooseCoreSkin: (String?) -> Unit,
    onChooseBackground: (String?) -> Unit,
    onSpectrumAgents: (Boolean) -> Unit,
    onOpenStore: () -> Unit,
    onBack: () -> Unit
) {
    val ownedSkins = CoreSkin.entries.count { it.productId == null || entitlements.owns(it.productId) }
    val ownedBackgrounds =
        LivingBackground.entries.count { it.productId == null || entitlements.owns(it.productId) }

    ScreenScaffold(
        title = "LOADOUT",
        subtitle = "$ownedSkins core skins · $ownedBackgrounds backgrounds available",
        onBack = onBack,
        backgroundAnimation = backgroundAnimation
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                TerminalPanel(title = "CORE-SERVER SKIN", accent = Palette.Purple) {
                    Caption("The rack your core is built into. Seen on the battlefield.")
                    Spacer(Modifier.height(6.dp))
                    for (skin in CoreSkin.entries) {
                        val owned = skin.productId == null || entitlements.owns(skin.productId)
                        LoadoutRow(
                            title = skin.displayName,
                            subtitle = if (skin.productId == null) {
                                "The default rack."
                            } else {
                                skin.flourish.blurb
                            },
                            selected = cosmetics.coreSkinId == skin.productId,
                            owned = owned,
                            swatch = listOf(skin.accent, skin.chassis, skin.trim),
                            onClick = { onChooseCoreSkin(skin.productId) }
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                TerminalPanel(title = "LIVING BACKGROUND", accent = Palette.Blue) {
                    Caption("Drawn under the lanes, and behind every menu.")
                    Spacer(Modifier.height(6.dp))
                    for (background in LivingBackground.entries) {
                        val owned = background.productId == null ||
                            entitlements.owns(background.productId)
                        LoadoutRow(
                            title = background.displayName,
                            subtitle = background.description,
                            selected = cosmetics.backgroundId == background.productId,
                            owned = owned,
                            swatch = listOfNotNull(background.tint, background.laneTint),
                            onClick = { onChooseBackground(background.productId) }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                TerminalPanel(title = "AGENT COLOURS", accent = Palette.Magenta) {
                    LoadoutRow(
                        title = "CLASS COLOURS",
                        subtitle = "Each agent wears the colour of what it does.",
                        selected = !cosmetics.spectrumAgents,
                        owned = true,
                        swatch = listOf(Palette.Cyan, Palette.Green, Palette.Purple),
                        onClick = { onSpectrumAgents(false) }
                    )
                    LoadoutRow(
                        title = "SPECTRUM",
                        subtitle = "A slow colour cycle across the whole roster.",
                        selected = cosmetics.spectrumAgents && entitlements.spectrumAgents,
                        owned = entitlements.spectrumAgents,
                        swatch = listOf(Palette.Magenta, Palette.Crypto, Palette.Cyan),
                        onClick = { onSpectrumAgents(true) }
                    )
                }

                Spacer(Modifier.height(12.dp))

                TerminalPanel(title = "LOCKED ITEMS", accent = Palette.CyanDim) {
                    Caption(
                        "Anything marked LOCKED is in the store. Nothing here " +
                            "changes how the game plays — skins are looks, not " +
                            "advantages."
                    )
                    Spacer(Modifier.height(8.dp))
                    com.cyopstd.game.ui.common.CompactButton(
                        text = "OPEN STORE",
                        onClick = onOpenStore,
                        accent = Palette.Crypto,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/**
 * One choosable look.
 *
 * The swatch is the point: a name tells a player nothing about a colour scheme,
 * and this screen's whole job is letting them see what they are picking before
 * they pick it.
 */
@Composable
private fun LoadoutRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    owned: Boolean,
    swatch: List<Color>,
    onClick: () -> Unit
) {
    val accent = when {
        !owned -> Palette.TextMuted
        selected -> Palette.Green
        else -> Palette.CyanDim
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(
                if (selected) Palette.SurfaceRaised else Color.Transparent,
                RoundedCornerShape(4.dp)
            )
            .border(BorderStroke(1.dp, accent.copy(alpha = if (selected) 0.8f else 0.3f)),
                RoundedCornerShape(4.dp))
            // A locked row is inert rather than absent: it says what exists
            // without becoming a second checkout.
            .clickable(enabled = owned, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .height(MIN_TOUCH_HEIGHT_DP.dp - 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (selected) "[*]" else if (owned) "[ ]" else "[X]",
            style = MaterialTheme.typography.labelMedium,
            color = accent
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (owned) Palette.TextPrimary else Palette.TextMuted
            )
            Caption(if (owned) subtitle else "LOCKED · $subtitle")
        }
        for (colour in swatch.take(3)) {
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(
                        if (owned) colour else colour.copy(alpha = 0.25f),
                        RoundedCornerShape(2.dp)
                    )
                    .border(
                        BorderStroke(1.dp, Palette.Divider),
                        RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}
