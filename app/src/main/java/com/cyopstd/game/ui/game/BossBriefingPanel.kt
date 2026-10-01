package com.cyopstd.game.ui.game

import androidx.compose.foundation.background
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.unit.dp
import com.cyopstd.game.engine.ProjectileSystem
import com.cyopstd.game.engine.WavePlan
import com.cyopstd.game.model.AgentType
import com.cyopstd.game.model.BossModifier
import com.cyopstd.game.model.BossVariant
import com.cyopstd.game.ui.common.AsciiRule
import com.cyopstd.game.ui.common.Caption
import com.cyopstd.game.ui.common.CompactButton
import com.cyopstd.game.ui.theme.Palette

/**
 * What the next boss wave is bringing, and what beats it.
 *
 * The BOSS dossier (B1) only exists once a boss is on the field, which is too
 * late to build for it. This is the same information one break earlier, plus
 * the part the owner asked for: its weaknesses (2026-09-26).
 *
 * Every weakness listed here is read from the damage code's own tables and
 * constants, not written separately, so the briefing cannot promise a counter
 * the game does not apply.
 */
data class BossBriefing(
    val wave: Int,
    /** Each boss type in the wave and how many of it; a wave can mix types. */
    val variants: List<Pair<BossVariant, Int>>,
    val bossCount: Int,
    val modifiers: List<BossModifier>,
    /** Each line: an agent and why it is good here. */
    val weakTo: List<Pair<AgentType, String>>,
    /** Agents to keep away from it, or that it blunts. */
    val warnings: List<String>
) {
    companion object {
        fun from(plan: WavePlan): BossBriefing {
            val bosses = plan.orders.filter { it.boss }.map { it.bossVariant }
                .ifEmpty { listOf(plan.bossVariant) }
            val variants = bosses.distinct().map { v -> v to bosses.count { it == v } }
            val modifiers = plan.bossModifiers
            val weakTo = ArrayList<Pair<AgentType, String>>()

            for ((variant, _) in variants) {
                for ((name, multiplier) in variant.bonusDamageFrom) {
                    val agent = AgentType.fromNameSafe(name) ?: continue
                    weakTo += agent to "Deals \u00D7${formatMultiplier(multiplier)} damage to ${variant.displayName}."
                }
            }
            weakTo += AgentType.ANALYST to
                "Deals \u00D7${formatMultiplier(ProjectileSystem.ANALYST_VS_ELITE)} damage to every boss."
            if (variants.any { it.first.armorBonus > 0f }) {
                for (agent in listOf(AgentType.ZERO_DAY_HUNTER, AgentType.ROOT_ADMIN)) {
                    weakTo += agent to "Ignores its armour."
                }
            }
            if (variants.any { it.first == BossVariant.GRADIENT }) {
                weakTo += AgentType.ROOT_ADMIN to "Few, huge hits barely speed GRADIENT up."
                weakTo += AgentType.TARPIT to "Its field drags GRADIENT back down while it speeds up."
            }
            if (variants.any { it.first == BossVariant.MODEL_COLLAPSE }) {
                for (agent in listOf(AgentType.ROOT_ADMIN, AgentType.ZERO_DAY_HUNTER)) {
                    weakTo += agent to "One heavy hitter does not feed MODEL COLLAPSE."
                }
            }
            if (BossModifier.ENCRYPTION_SHIELD in modifiers) {
                weakTo += AgentType.CRYPTOGRAPHER to
                    "Breaks its encryption: \u00D7${formatMultiplier(ProjectileSystem.CRYPTOGRAPHER_VS_ENCRYPTED)} damage."
            }

            val warnings = ArrayList<String>()
            for ((variant, _) in variants) {
                val agent = variant.jamsAgentType?.let { AgentType.fromNameSafe(it) } ?: continue
                warnings += "${variant.displayName} jams ${agent.displayName} agents close to it. " +
                    "Place them back, at the edge of their range."
            }
            if (variants.any { it.first == BossVariant.MODEL_COLLAPSE }) {
                warnings += "MODEL COLLAPSE heals back part of every hit while " +
                    "${BossVariant.COLLAPSE_SWARM} or more agents hit it at once, more " +
                    "the bigger the crowd. Fewer, heavier hitters starve it."
            }
            if (variants.any { it.first == BossVariant.LICENSE }) {
                warnings += "LICENSE shrugs off any agent type that keeps hitting it. " +
                    "Mix your agent types."
            }
            if (variants.any { it.first == BossVariant.RANSOM }) {
                warnings += "RANSOM locks one agent's upgrades for " +
                    "${BossVariant.RANSOM_SECONDS.toInt()} seconds, every few seconds. " +
                    "Upgrade before it arrives."
            }
            if (variants.any { it.first == BossVariant.EXFIL }) {
                warnings += "EXFIL is fast. If it reaches the core it steals half " +
                    "your crypto instead of integrity. Spend first, or stop it."
            }
            if (variants.any { it.first == BossVariant.SYN_STORM }) {
                warnings += "SYN-STORM splits in two at half health, and the second " +
                    "half takes another route. Cover every route."
            }
            if (variants.any { it.first == BossVariant.GRADIENT }) {
                warnings += "GRADIENT speeds up with every hit, however small, and " +
                    "slows when left alone. Few big hits beat many small ones."
            }
            if (variants.any { it.first == BossVariant.BOTMASTER }) {
                warnings += "BOTMASTER drops BOTs behind itself every few seconds. " +
                    "Kill it fast, and keep something for the swarm."
            }
            if (variants.any { it.first == BossVariant.ROOTKIT }) {
                warnings += "ROOTKIT hides for 2 seconds of every 6 and nothing can " +
                    "target it. Splash and chains still reach it."
            }
            if (variants.any { it.first == BossVariant.WORM }) {
                warnings += "WORM breaks into three smaller worms when killed, and " +
                    "those break once more. Kill it early, with splash nearby."
            }
            if (variants.any { it.first == BossVariant.SPOOFER }) {
                warnings += "SPOOFER casts decoys of itself that soak up fire. " +
                    "ANALYST and ROOT ADMIN see through them."
            }
            if (variants.any { it.first == BossVariant.KERNEL_PANIC }) {
                warnings += "KERNEL PANIC jams every agent within reach for 3 " +
                    "seconds when it dies. FIREWALL stands in it."
            }
            if (BossModifier.FIREWALL_RESISTANCE in modifiers) {
                warnings += "Resists FIREWALL agents."
            }

            return BossBriefing(
                wave = plan.wave,
                variants = variants,
                bossCount = bosses.size,
                modifiers = modifiers,
                weakTo = weakTo.distinctBy { it.first },
                warnings = warnings
            )
        }

        private fun formatMultiplier(value: Float): String {
            val tenths = Math.round(value * 10f) / 10f
            return if (tenths == tenths.toInt().toFloat()) "${tenths.toInt()}" else "$tenths"
        }
    }
}

@Composable
fun BossBriefingPanel(
    briefing: BossBriefing,
    unlockedAgents: Set<String>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .widthIn(max = 460.dp)
            .heightIn(max = 340.dp)
            .background(Palette.Surface.copy(alpha = LocalPanelOpacity.current), RoundedCornerShape(6.dp))
            .border(1.dp, Palette.Red.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "NEXT: WAVE ${briefing.wave} \u00B7 BOSS",
                style = MaterialTheme.typography.titleMedium,
                color = Palette.Red,
                modifier = Modifier.weight(1f)
            )
            CompactButton(text = "CLOSE", onClick = onClose, accent = Palette.TextSecondary)
        }
        AsciiRule(color = Palette.RedDeep)
        Column(Modifier.verticalScroll(rememberScrollState())) {
            for ((variant, count) in briefing.variants) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BossIcon(variant)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = variant.displayName + if (count > 1) "  \u00D7$count" else "",
                        style = MaterialTheme.typography.titleMedium,
                        color = bossUiColor(variant),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = variant.signature,
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextSecondary
                )
                Text(
                    text = "HEALTH \u00D7${variant.healthScale} \u00B7 ARMOUR +${variant.armorBonus.toInt()} " +
                        "\u00B7 SPEED \u00D7${variant.speedScale}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextMuted
                )
            }

            if (briefing.modifiers.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Section("MODIFIERS", Palette.Orange)
                for (modifier in briefing.modifiers) {
                    Line("${modifier.displayName}: ${modifier.description}", Palette.TextSecondary)
                }
            }

            Spacer(Modifier.height(6.dp))
            Section("WEAK TO", Palette.Green)
            for ((agent, why) in briefing.weakTo) {
                val owned = agent.name in unlockedAgents
                Line(
                    "[${agent.glyph}] ${agent.displayName}${if (owned) "" else " (locked)"}: $why",
                    if (owned) Palette.Green else Palette.TextMuted
                )
            }

            if (briefing.warnings.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Section("WATCH OUT", Palette.Red)
                for (warning in briefing.warnings) Line(warning, Palette.TextSecondary)
            }

            Spacer(Modifier.height(6.dp))
            Caption("The countdown keeps running while this is open.")
        }
    }
}

/** A boss's colour on the board, for UI that names it. */
fun bossUiColor(variant: BossVariant): androidx.compose.ui.graphics.Color = when (variant.palette) {
    com.cyopstd.game.model.BossPalette.HOSTILE -> Palette.Red
    com.cyopstd.game.model.BossPalette.ICE -> Palette.Cyan
    com.cyopstd.game.model.BossPalette.VIOLET -> Palette.Purple
    // Cycles through the cool colours on the board; blue stands in for it.
    com.cyopstd.game.model.BossPalette.SPECTRUM -> Palette.Blue
}

/**
 * A boss's icon: its glyph in a small box in its board colour. Shown beside
 * each boss in the briefing (owner, 2026-09-28: the icon goes "in the menu
 * that pulls up when you tap boss", not in a box of its own on the board).
 */
@Composable
fun BossIcon(variant: BossVariant, modifier: Modifier = Modifier) {
    val color = bossUiColor(variant)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .testTag("boss-icon-${variant.name}")
            .heightIn(min = 32.dp)
            .widthIn(min = 54.dp)
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(7.dp))
            .border(1.dp, color.copy(alpha = 0.7f), RoundedCornerShape(7.dp))
            .padding(horizontal = 6.dp)
    ) {
        Text(
            text = variant.glyph,
            style = MaterialTheme.typography.titleMedium,
            color = color,
            maxLines = 1
        )
    }
}

@Composable
private fun Section(title: String, color: androidx.compose.ui.graphics.Color) {
    Text(text = title, style = MaterialTheme.typography.labelMedium, color = color)
}

@Composable
private fun Line(text: String, color: androidx.compose.ui.graphics.Color) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp)) {
        Text("\u00B7", color = color, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.width(6.dp))
        Text(text, color = color, style = MaterialTheme.typography.bodySmall)
    }
}
