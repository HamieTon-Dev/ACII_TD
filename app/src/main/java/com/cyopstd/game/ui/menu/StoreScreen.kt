package com.cyopstd.game.ui.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cyopstd.game.store.BillingStatus
import com.cyopstd.game.store.Entitlements
import com.cyopstd.game.store.Sku
import com.cyopstd.game.store.SkuKind
import com.cyopstd.game.ui.common.AsciiRule
import com.cyopstd.game.ui.common.Caption
import com.cyopstd.game.ui.common.CompactButton
import com.cyopstd.game.ui.common.ScreenScaffold
import com.cyopstd.game.ui.common.StatRow
import com.cyopstd.game.ui.common.TerminalPanel
import androidx.compose.ui.graphics.Color
import com.cyopstd.game.ui.theme.Palette

/**
 * One headed group of products on the store's left-hand column.
 *
 * The sections are data rather than three inline loops so that a test can ask
 * the obvious question — *is every product in the catalog actually reachable?*
 * Adding a `Sku` and forgetting to list it produced a product that existed,
 * had a price, could be restored, and could never be bought, and nothing in
 * the build said so.
 */
data class StoreSection(val title: String, val accent: Color, val items: List<Sku>)

/** The left column, in order. */
val STORE_SECTIONS: List<StoreSection> = listOf(
    StoreSection(
        "BEST VALUE",
        Palette.Crypto,
        listOf(Sku.STARTER_PACK, Sku.CORE_SKIN_PACK, Sku.BG_PACK)
    ),
    StoreSection(
        "BUDGET",
        Palette.Cyan,
        listOf(Sku.BUDGET_SMALL, Sku.BUDGET_MEDIUM, Sku.BUDGET_LARGE)
    ),
    StoreSection(
        "CONVENIENCE",
        Palette.Green,
        // REVIVE_PACK sits next to NO_ADS on purpose: they are the two
        // products with "ads" in them, and a player comparing them side by
        // side is a player who does not later ask for a refund because they
        // bought the wrong one.
        listOf(Sku.NO_ADS, Sku.REVIVE_PACK, Sku.SPEED_5X)
    )
)

/**
 * The store.
 *
 * Driven entirely by the [Sku] catalog, so adding a product is a table entry
 * rather than a screen change. Two rules shape everything here:
 *
 * 1. **It must be honest about what it cannot do.** When billing is not
 *    available — no Play Services, no network, or a build with no Play
 *    integration configured — every button says so plainly instead of failing
 *    silently on tap.
 * 2. **Prices come from Play.** The catalog's own price strings are a fallback
 *    for before Play answers; the number the player is charged is in their
 *    currency and only Play knows it.
 */
@Composable
fun StoreScreen(
    entitlements: Entitlements,
    budget: Long,
    prices: Map<String, String>,
    status: BillingStatus,
    backgroundAnimation: Boolean,
    onBuy: (Sku) -> Unit,
    onRestore: () -> Unit,
    onBack: () -> Unit
) {
    val available = status == BillingStatus.READY

    ScreenScaffold(
        title = "STORE",
        subtitle = "€ $budget in the budget",
        onBack = onBack,
        backgroundAnimation = backgroundAnimation
    ) {
        if (!available) {
            TerminalPanel(title = "STORE UNAVAILABLE", accent = Palette.Orange) {
                Text(
                    text = when (status) {
                        BillingStatus.CONNECTING -> "Connecting to Google Play…"
                        BillingStatus.ERROR ->
                            "Google Play could not be reached. Anything already " +
                                "purchased is still yours — try RESTORE once you " +
                                "are back online."
                        else ->
                            "This build has no Google Play billing configured, so " +
                                "nothing can be bought here. Every part of the game " +
                                "that does not cost money works exactly as normal."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextSecondary
                )
            }
            Spacer(Modifier.height(12.dp))
        }

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                for ((index, section) in STORE_SECTIONS.withIndex()) {
                    if (index > 0) Spacer(Modifier.height(12.dp))
                    Section(section.title, section.accent) {
                        for (sku in section.items) {
                            ProductRow(sku, entitlements, prices, available, onBuy)
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                Section("CORE-SERVER SKINS", Palette.Purple) {
                    for (sku in Sku.coreSkins) {
                        ProductRow(sku, entitlements, prices, available, onBuy)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Section("LIVING BACKGROUNDS", Palette.Blue) {
                    for (sku in Sku.backgrounds) {
                        ProductRow(sku, entitlements, prices, available, onBuy)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Section("AGENT SKINS", Palette.Magenta) {
                    ProductRow(Sku.SKIN_AGENTS_SPECTRUM, entitlements, prices, available, onBuy)
                }

                Spacer(Modifier.height(12.dp))
                TerminalPanel(title = "ACCOUNT", accent = Palette.CyanDim) {
                    Caption(
                        "Purchases are tied to your Google account, not to this " +
                            "device. Reinstalling or changing phone does not lose them."
                    )
                    Spacer(Modifier.height(8.dp))
                    CompactButton(
                        text = "RESTORE PURCHASES",
                        onClick = onRestore,
                        enabled = status != BillingStatus.UNAVAILABLE,
                        accent = Palette.Cyan,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun Section(
    title: String,
    accent: androidx.compose.ui.graphics.Color,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    TerminalPanel(title = title, accent = accent, content = content)
}

@Composable
private fun ProductRow(
    sku: Sku,
    entitlements: Entitlements,
    prices: Map<String, String>,
    available: Boolean,
    onBuy: (Sku) -> Unit
) {
    val owned = sku.kind == SkuKind.PERMANENT && entitlements.owns(sku)
    // Play's localized price whenever we have it; the catalog's string only
    // stands in before Play answers.
    val price = prices[sku.id] ?: sku.fallbackPrice

    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = sku.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (owned) Palette.Green else Palette.TextPrimary
                )
                Caption(sku.summary)
            }
            Spacer(Modifier.height(0.dp))
            CompactButton(
                text = if (owned) "OWNED" else price,
                onClick = { onBuy(sku) },
                enabled = available && !owned,
                accent = if (owned) Palette.GreenDim else Palette.Crypto
            )
        }
        if (sku.grantsBudget > 0) {
            StatRow("INCLUDES", "€ ${sku.grantsBudget}", valueColor = Palette.Cyan)
        }
        AsciiRule(color = Palette.Divider)
    }
}
