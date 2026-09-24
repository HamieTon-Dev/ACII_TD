package com.cyopstd.game.ui.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cyopstd.game.BuildConfig
import com.cyopstd.game.ui.common.BastionButton
import com.cyopstd.game.ui.common.Caption
import com.cyopstd.game.ui.common.ScreenScaffold
import com.cyopstd.game.ui.common.StatRow
import com.cyopstd.game.ui.common.TerminalPanel
import com.cyopstd.game.ui.theme.Palette

/**
 * What this game is, who made it, and what it is careful not to claim.
 *
 * Two scrolling columns, because the content is now long enough to need it and
 * the game is landscape-locked. Everything textual lives in [AboutText] so that
 * the claims can be tested; this file is layout only.
 */
@Composable
fun AboutScreen(
    backgroundAnimation: Boolean,
    onBack: () -> Unit,
    /** True when this build actually serves ads. Reported, never assumed. */
    adsConfigured: Boolean = false,
    /** True when Play Billing is available in this build. */
    purchasesAvailable: Boolean = false,
    /**
     * Shown only when Google's consent SDK says a privacy entry point is
     * required, which is the same rule the settings screen uses.
     */
    privacyOptionsRequired: Boolean = false,
    onPrivacyOptions: () -> Unit = {}
) {
    ScreenScaffold(
        title = "ABOUT",
        subtitle = "${AboutText.GAME_TITLE} · ${AboutText.GAME_SUBTITLE}",
        onBack = onBack,
        backgroundAnimation = backgroundAnimation
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ---- left: what it is, and what it is built on -----------------
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
            ) {
                TerminalPanel(title = "BUILD", accent = Palette.Cyan) {
                    StatRow("TITLE", AboutText.GAME_TITLE, valueColor = Palette.Green)
                    StatRow("VERSION", BuildConfig.VERSION_NAME, valueColor = Palette.Green)
                    StatRow("PACKAGE", BuildConfig.APPLICATION_ID)
                    StatRow("BUILD TYPE", BuildConfig.BUILD_TYPE)
                    StatRow("DEVELOPER", AboutText.DEVELOPER, valueColor = Palette.Cyan)
                    StatRow("RENDERING", "Compose UI + native Canvas")
                    StatRow("PERSISTENCE", "Jetpack DataStore")
                }

                Spacer(Modifier.height(12.dp))

                TerminalPanel(title = "THE GAME", accent = Palette.Green) {
                    Body(AboutText.DEVELOPMENT_STATEMENT)
                    Spacer(Modifier.height(8.dp))
                    Body(
                        "Cyberattacks advance along network routes toward " +
                            "CORE-SERVER. You deploy Cyber Agents beside the " +
                            "lanes; they detect and destroy what comes past. " +
                            "Every attack you stop pays ◇ Crypto, which buys " +
                            "more agents and upgrades. Every fifth wave is a " +
                            "boss. There is no final wave — the only question " +
                            "is how far you get."
                    )
                }

                Spacer(Modifier.height(12.dp))

                TerminalPanel(title = "WHAT THIS GAME DOES NOT DO", accent = Palette.Red) {
                    for (line in AboutText.DOES_NOT_DO) {
                        Text(
                            text = "· $line",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Palette.TextSecondary
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Caption(AboutText.CRYPTO_DISCLAIMER)
                }

                Spacer(Modifier.height(12.dp))
            }

            // ---- right: the disclosures ------------------------------------
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
            ) {
                TerminalPanel(title = "ADVERTISING AND PURCHASES", accent = Palette.Orange) {
                    Body(
                        AboutText.monetisationSummary(
                            adsConfigured = adsConfigured,
                            purchasesAvailable = purchasesAvailable
                        )
                    )
                    if (privacyOptionsRequired) {
                        Spacer(Modifier.height(10.dp))
                        BastionButton(
                            text = "PRIVACY OPTIONS",
                            accent = Palette.Crypto,
                            leadingGlyph = "[i]",
                            onClick = onPrivacyOptions,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                TerminalPanel(title = AboutText.AI_DISCLOSURE_TITLE, accent = Palette.Purple) {
                    Body(AboutText.AI_DISCLOSURE)
                }

                Spacer(Modifier.height(12.dp))

                TerminalPanel(title = AboutText.TRADEMARK_TITLE, accent = Palette.Blue) {
                    Body(AboutText.TRADEMARK_NOTICE)
                }

                Spacer(Modifier.height(12.dp))

                TerminalPanel(title = "CREDITS", accent = Palette.Cyan) {
                    Caption(
                        "Original code, original artwork, original synthesized " +
                            "audio. Built with Kotlin, Jetpack Compose and the " +
                            "Android Canvas API.\n\n" +
                            "Third-party dependency licenses are listed in " +
                            "LICENSES.md in the project repository."
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = AboutText.copyright(),
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.TextMuted
                    )
                }

                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

/** Body copy, at the one size the About screen uses for prose. */
@Composable
private fun Body(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = Palette.TextSecondary
    )
}
