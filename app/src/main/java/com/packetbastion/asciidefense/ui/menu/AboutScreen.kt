package com.packetbastion.asciidefense.ui.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.packetbastion.asciidefense.BuildConfig
import com.packetbastion.asciidefense.ui.common.Caption
import com.packetbastion.asciidefense.ui.common.ScreenScaffold
import com.packetbastion.asciidefense.ui.common.StatRow
import com.packetbastion.asciidefense.ui.common.TerminalPanel
import com.packetbastion.asciidefense.ui.theme.Palette

/** Version, scope, and an explicit statement of what this game does not do. */
@Composable
fun AboutScreen(
    backgroundAnimation: Boolean,
    onBack: () -> Unit
) {
    ScreenScaffold(
        title = "ABOUT",
        subtitle = "CyOps TD \u00B7 ASCII Cyber Defense",
        onBack = onBack,
        backgroundAnimation = backgroundAnimation
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
            ) {
                TerminalPanel(title = "BUILD", accent = Palette.Cyan) {
                    StatRow("VERSION", BuildConfig.VERSION_NAME, valueColor = Palette.Green)
                    StatRow("PACKAGE", BuildConfig.APPLICATION_ID)
                    StatRow("BUILD TYPE", BuildConfig.BUILD_TYPE)
                    StatRow("RENDERING", "Compose UI + native Canvas")
                    StatRow("PERSISTENCE", "Jetpack DataStore")
                }

                Spacer(Modifier.height(12.dp))

                TerminalPanel(title = "THE GAME", accent = Palette.Green) {
                    Text(
                        text = "Hostile packets advance down three network lanes " +
                            "toward CORE-SERVER. You deploy cyber agents beside the " +
                            "lanes; they detect and destroy what comes past. " +
                            "Destroyed packets pay ◇ Crypto, which buys more " +
                            "agents and upgrades. Every fifth wave is a boss. " +
                            "There is no final wave — the only question is how " +
                            "far you get.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.TextSecondary
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
            ) {
                TerminalPanel(title = "WHAT THIS GAME DOES NOT DO", accent = Palette.Red) {
                    listOf(
                        "No internet connection — the app has no INTERNET permission",
                        "No account, no login, no cloud save",
                        "No advertisements",
                        "No in-app purchases or subscriptions",
                        "No analytics or telemetry",
                        "No real cryptocurrency, blockchain, wallet, mining or NFTs",
                        "No gambling mechanics"
                    ).forEach { line ->
                        Text(
                            text = "· $line",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Palette.TextSecondary
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    Caption(
                        "◇ Crypto is a fictional in-game resource. It has no " +
                            "value, cannot be bought, and cannot leave the device."
                    )
                }

                Spacer(Modifier.height(12.dp))

                TerminalPanel(title = "CREDITS", accent = Palette.Purple) {
                    Caption(
                        "Original code, original artwork, original synthesized " +
                            "audio. Built with Kotlin, Jetpack Compose and the " +
                            "Android Canvas API.\n\n" +
                            "Third-party dependency licenses are listed in " +
                            "LICENSES.md in the project repository."
                    )
                }

                Spacer(Modifier.height(12.dp))
            }
        }
    }
}
