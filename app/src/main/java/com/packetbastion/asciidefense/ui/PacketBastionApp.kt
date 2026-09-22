package com.packetbastion.asciidefense.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.packetbastion.asciidefense.state.GameViewModel
import com.packetbastion.asciidefense.ui.codex.CodexScreen
import com.packetbastion.asciidefense.ui.game.GameScreen
import com.packetbastion.asciidefense.ui.menu.AboutScreen
import com.packetbastion.asciidefense.ui.menu.AgentsScreen
import com.packetbastion.asciidefense.ui.menu.MainMenuScreen
import com.packetbastion.asciidefense.ui.settings.SettingsScreen
import com.packetbastion.asciidefense.ui.splash.SplashScreen
import com.packetbastion.asciidefense.ui.stats.StatisticsScreen
import com.packetbastion.asciidefense.ui.theme.Palette

/**
 * Screen graph.
 *
 * A plain sealed hierarchy plus a small back stack, rather than Navigation
 * Compose: the game has eight destinations with no deep links and no arguments
 * to serialize, and the match screen must keep a live ViewModel across every
 * transition. A hand-rolled stack is smaller, faster and easier to follow here.
 */
sealed interface Screen {
    data object Splash : Screen
    data object MainMenu : Screen
    data object Game : Screen
    data object Agents : Screen
    data object Codex : Screen
    data object Statistics : Screen
    data object Settings : Screen
    data object About : Screen
}

@Composable
fun PacketBastionApp(
    viewModel: GameViewModel,
    onExitApp: () -> Unit,
    modifier: Modifier = Modifier
) {
    var screen by remember { mutableStateOf<Screen>(Screen.Splash) }
    // Where SETTINGS should return to: it is reachable from both the main menu
    // and the in-match pause menu.
    var settingsReturn by remember { mutableStateOf<Screen>(Screen.MainMenu) }

    Box(modifier.fillMaxSize().background(Palette.Background)) {
        when (screen) {
            Screen.Splash -> SplashScreen(onFinished = { screen = Screen.MainMenu })

            Screen.MainMenu -> {
                BackHandler(enabled = true) { onExitApp() }
                MainMenuScreen(
                    hasSavedRun = viewModel.hasSavedRun,
                    stats = viewModel.stats,
                    backgroundAnimation = viewModel.settings.backgroundAnimation,
                    onPlay = {
                        viewModel.playClick()
                        viewModel.startNewGame()
                        screen = Screen.Game
                    },
                    onContinue = {
                        viewModel.playClick()
                        viewModel.continueGame(
                            onFailed = { viewModel.showTransient("NO SAVED SESSION") }
                        )
                        screen = Screen.Game
                    },
                    onAgents = { viewModel.playClick(); screen = Screen.Agents },
                    onCodex = { viewModel.playClick(); screen = Screen.Codex },
                    onStatistics = { viewModel.playClick(); screen = Screen.Statistics },
                    onSettings = {
                        viewModel.playClick()
                        settingsReturn = Screen.MainMenu
                        screen = Screen.Settings
                    },
                    onAbout = { viewModel.playClick(); screen = Screen.About },
                    onExit = { viewModel.playClick(); onExitApp() }
                )
            }

            Screen.Game -> GameScreen(
                viewModel = viewModel,
                onExitToMenu = { screen = Screen.MainMenu },
                onOpenSettings = {
                    settingsReturn = Screen.Game
                    screen = Screen.Settings
                }
            )

            Screen.Agents -> {
                BackHandler { screen = Screen.MainMenu }
                AgentsScreen(
                    unlockedAgents = viewModel.unlockedAgents,
                    highestWave = viewModel.stats.highestWave,
                    backgroundAnimation = viewModel.settings.backgroundAnimation,
                    onBack = { viewModel.playClick(); screen = Screen.MainMenu }
                )
            }

            Screen.Codex -> {
                BackHandler { screen = Screen.MainMenu }
                CodexScreen(
                    backgroundAnimation = viewModel.settings.backgroundAnimation,
                    onBack = { viewModel.playClick(); screen = Screen.MainMenu }
                )
            }

            Screen.Statistics -> {
                BackHandler { screen = Screen.MainMenu }
                StatisticsScreen(
                    stats = viewModel.stats,
                    backgroundAnimation = viewModel.settings.backgroundAnimation,
                    onBack = { viewModel.playClick(); screen = Screen.MainMenu }
                )
            }

            Screen.Settings -> {
                BackHandler { screen = settingsReturn }
                SettingsScreen(
                    settings = viewModel.settings,
                    backgroundAnimation = viewModel.settings.backgroundAnimation,
                    onUpdate = viewModel::updateSettings,
                    onResetProgress = {
                        viewModel.resetAllProgress()
                        settingsReturn = Screen.MainMenu
                        screen = Screen.MainMenu
                    },
                    onBack = { viewModel.playClick(); screen = settingsReturn }
                )
            }

            Screen.About -> {
                BackHandler { screen = Screen.MainMenu }
                AboutScreen(
                    backgroundAnimation = viewModel.settings.backgroundAnimation,
                    onBack = { viewModel.playClick(); screen = Screen.MainMenu }
                )
            }
        }
    }
}
