package com.cyopstd.game.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.cyopstd.game.state.GameViewModel
import com.cyopstd.game.ui.codex.CodexScreen
import com.cyopstd.game.ui.game.GameScreen
import com.cyopstd.game.ui.menu.AboutScreen
import com.cyopstd.game.ui.menu.AgentsScreen
import com.cyopstd.game.ui.menu.FirmwareScreen
import com.cyopstd.game.ui.menu.LeaderboardScreen
import com.cyopstd.game.ui.menu.LoadoutScreen
import com.cyopstd.game.ui.menu.PlayAccountScreen
import com.cyopstd.game.ui.menu.StoreScreen
import com.cyopstd.game.ui.menu.MainMenuScreen
import com.cyopstd.game.ui.settings.SettingsScreen
import com.cyopstd.game.ui.splash.DeveloperSplashScreen
import com.cyopstd.game.ui.splash.SplashScreen
import com.cyopstd.game.ui.stats.StatisticsScreen
import androidx.compose.ui.Alignment
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import com.cyopstd.game.ads.PlayServices
import com.cyopstd.game.store.PlayLinks
import com.cyopstd.game.ui.common.IdentityStrip
import com.cyopstd.game.ui.common.LocalLivingBackground
import com.cyopstd.game.ui.theme.LivingBackground
import com.cyopstd.game.ui.theme.Palette

/**
 * Screen graph.
 *
 * A plain sealed hierarchy plus a small back stack, rather than Navigation
 * Compose: the game has eight destinations with no deep links and no arguments
 * to serialize, and the match screen must keep a live ViewModel across every
 * transition. A hand-rolled stack is smaller, faster and easier to follow here.
 */
sealed interface Screen {
    /** The studio ident, before anything else. */
    data object DeveloperIdent : Screen
    data object Splash : Screen
    data object MainMenu : Screen
    data object Game : Screen
    data object Agents : Screen
    data object Firmware : Screen
    data object Codex : Screen
    data object Store : Screen
    data object Loadout : Screen
    data object PlayAccount : Screen
    data object Leaderboard : Screen
    data object Statistics : Screen
    data object Settings : Screen
    data object About : Screen
}

@Composable
fun CyOpsApp(
    viewModel: GameViewModel,
    onExitApp: () -> Unit,
    modifier: Modifier = Modifier
) {
    var screen by remember { mutableStateOf<Screen>(Screen.DeveloperIdent) }
    // Where SETTINGS should return to: it is reachable from both the main menu
    // and the in-match pause menu.
    var settingsReturn by remember { mutableStateOf<Screen>(Screen.MainMenu) }
    // The Play account screen links out to Google Play; opening a URI needs a
    // Context, and this is the one place in the graph that already has one.
    val context = LocalContext.current

    // Set once, read by every menu backdrop beneath it.
    val living = LivingBackground.forProduct(viewModel.cosmetics.backgroundId)
    CompositionLocalProvider(LocalLivingBackground provides living) {
    Box(modifier.fillMaxSize().background(Palette.Background)) {
        when (screen) {
            Screen.DeveloperIdent -> {
                // The machine coming up, over the studio card.
                LaunchedEffect(Unit) { viewModel.playBootChime() }
                DeveloperSplashScreen(onFinished = { screen = Screen.Splash })
            }

            Screen.Splash -> SplashScreen(onFinished = { screen = Screen.MainMenu })

            Screen.MainMenu -> {
                BackHandler(enabled = true) { onExitApp() }
                // Every arrival at the menu, including the first. Nothing used
                // to do this, so menu music never played on a fresh launch.
                LaunchedEffect(Unit) { viewModel.onMenuShown() }

                MainMenuScreen(
                    hasSavedRun = viewModel.hasSavedRun,
                    stats = viewModel.stats,
                    budget = viewModel.budget,
                    firmwareLevel = viewModel.firmwareLevel,
                    adsRemoved = viewModel.entitlements.adsRemoved,
                    availableModes = viewModel.availableModes,
                    selectedMode = viewModel.selectedMode,
                    backgroundAnimation = viewModel.settings.backgroundAnimation,
                    onSelectMode = { viewModel.selectMode(it) },
                    onPlay = {
                        viewModel.playClick()
                        viewModel.startNewGame()
                        screen = Screen.Game
                    },
                    onContinue = {
                        viewModel.playClick()
                        viewModel.continueGame(
                            onLoaded = { screen = Screen.Game },
                            onFailed = { viewModel.showTransient("NO SAVED SESSION") }
                        )
                    },
                    onAgents = { viewModel.playClick(); screen = Screen.Agents },
                    onFirmware = { viewModel.playClick(); screen = Screen.Firmware },
                    onCodex = { viewModel.playClick(); screen = Screen.Codex },
                    onStore = { viewModel.playClick(); screen = Screen.Store },
                    onLoadout = { viewModel.playClick(); screen = Screen.Loadout },
                    onPlayAccount = { viewModel.playClick(); screen = Screen.PlayAccount },
                    onLeaderboard = {
                        viewModel.playClick()
                        viewModel.refreshLeaderboard()
                        screen = Screen.Leaderboard
                    },
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

            Screen.Firmware -> {
                BackHandler { screen = Screen.MainMenu }
                FirmwareScreen(
                    budget = viewModel.budget,
                    firmwareLevel = viewModel.firmwareLevel,
                    lifetimeBudgetEarned = viewModel.lifetimeBudgetEarned,
                    backgroundAnimation = viewModel.settings.backgroundAnimation,
                    onBuy = { levels -> viewModel.buyFirmware(levels) },
                    onBack = { viewModel.playClick(); screen = Screen.MainMenu }
                )
            }

            Screen.Store -> {
                BackHandler { screen = Screen.MainMenu }
                StoreScreen(
                    entitlements = viewModel.entitlements,
                    budget = viewModel.budget,
                    prices = viewModel.billingPrices,
                    status = viewModel.billingStatus,
                    backgroundAnimation = viewModel.settings.backgroundAnimation,
                    onBuy = { sku -> viewModel.buy(sku) },
                    onRestore = { viewModel.restorePurchases() },
                    onBack = { viewModel.playClick(); screen = Screen.MainMenu }
                )
            }

            Screen.Loadout -> {
                BackHandler { screen = Screen.MainMenu }
                LoadoutScreen(
                    entitlements = viewModel.entitlements,
                    cosmetics = viewModel.cosmetics,
                    backgroundAnimation = viewModel.settings.backgroundAnimation,
                    onChooseCoreSkin = { viewModel.chooseCoreSkin(it) },
                    onChooseBackground = { viewModel.chooseBackground(it) },
                    onSpectrumAgents = { viewModel.setSpectrumAgents(it) },
                    onOpenStore = { viewModel.playClick(); screen = Screen.Store },
                    onBack = { viewModel.playClick(); screen = Screen.MainMenu }
                )
            }

            Screen.PlayAccount -> {
                BackHandler { screen = Screen.MainMenu }
                PlayAccountScreen(
                    entitlements = viewModel.entitlements,
                    identity = viewModel.identity,
                    budget = viewModel.budget,
                    status = viewModel.billingStatus,
                    adsConfigured = PlayServices.adsConfigured,
                    reviveAdsConfigured = PlayServices.rewardedConfigured,
                    cloudStatus = viewModel.cloudStatus,
                    cloudAccount = viewModel.cloudAccount,
                    lastCloudSync = viewModel.lastCloudSync,
                    cloudBusy = viewModel.cloudBusy,
                    backgroundAnimation = viewModel.settings.backgroundAnimation,
                    onRestore = { viewModel.restorePurchases() },
                    onLinkCloud = { viewModel.linkCloudSave() },
                    onSyncCloud = { viewModel.syncCloudSave() },
                    onOpenOrders = {
                        viewModel.playClick()
                        if (!PlayLinks.open(context, PlayLinks.orderHistoryUris())) {
                            viewModel.showTransient("NO APP CAN OPEN GOOGLE PLAY")
                        }
                    },
                    onOpenListing = {
                        viewModel.playClick()
                        val uris = PlayLinks.listingUris(context.packageName)
                        if (!PlayLinks.open(context, uris)) {
                            viewModel.showTransient("NO APP CAN OPEN GOOGLE PLAY")
                        }
                    },
                    onCallsign = {
                        viewModel.playClick()
                        viewModel.refreshLeaderboard()
                        screen = Screen.Leaderboard
                    },
                    onBack = { viewModel.playClick(); screen = Screen.MainMenu }
                )
            }

            Screen.Leaderboard -> {
                BackHandler { screen = Screen.MainMenu }
                LeaderboardScreen(
                    identity = viewModel.identity,
                    entries = viewModel.leaderboardEntries,
                    backgroundAnimation = viewModel.settings.backgroundAnimation,
                    onRegister = { viewModel.registerUsername(it) },
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
                    budget = viewModel.budget,
                    firmwareLevel = viewModel.firmwareLevel,
                    lifetimeBudgetEarned = viewModel.lifetimeBudgetEarned,
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
                    onBack = { viewModel.playClick(); screen = settingsReturn },
                    privacyOptionsRequired = viewModel.privacyOptionsRequired,
                    onPrivacyOptions = {
                        viewModel.playClick()
                        // Google's form needs a real Activity. The composition
                        // is inside one; anything else means the SDK is not in
                        // a position to show it anyway.
                        (context as? android.app.Activity)?.let(viewModel::showPrivacyOptions)
                    }
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

        // Drawn last, over whatever screen is showing, so there is no screen
        // the build identifier can be missing from.
        IdentityStrip(
            playerTag = viewModel.playerTag,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
    }
}
