package com.cyopstd.game

import android.app.Application
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.cyopstd.game.i18n.Tr
import com.cyopstd.game.save.CloudSaveStatus
import com.cyopstd.game.save.GameSettings
import com.cyopstd.game.save.LeaderboardEntry
import com.cyopstd.game.save.PlayerIdentity
import com.cyopstd.game.save.PlayerStats
import com.cyopstd.game.state.GameOverSummary
import com.cyopstd.game.state.GameViewModel
import com.cyopstd.game.store.BillingStatus
import com.cyopstd.game.store.CosmeticChoice
import com.cyopstd.game.store.Entitlements
import com.cyopstd.game.ui.codex.CodexScreen
import com.cyopstd.game.ui.game.GameOverOverlay
import com.cyopstd.game.ui.game.GameScreen
import com.cyopstd.game.ui.game.PauseOverlay
import com.cyopstd.game.ui.menu.AboutScreen
import com.cyopstd.game.ui.menu.AgentsScreen
import com.cyopstd.game.ui.menu.FirmwareScreen
import com.cyopstd.game.ui.menu.LeaderboardScreen
import com.cyopstd.game.ui.menu.LoadoutScreen
import com.cyopstd.game.ui.menu.MainMenuScreen
import com.cyopstd.game.ui.menu.PlayAccountScreen
import com.cyopstd.game.ui.menu.RunSetupScreen
import com.cyopstd.game.ui.menu.StoreScreen
import com.cyopstd.game.ui.settings.SettingsScreen
import com.cyopstd.game.ui.stats.StatisticsScreen
import com.cyopstd.game.ui.theme.CyOpsTheme
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Every screen a player can reach, rendered in a translated language, with
 * nothing on it that is still the English of a line that has a translation.
 *
 * The other i18n tests prove the files are complete; this proves the screens
 * actually *use* them. Every visible text is also written to
 * `build/i18n-audit/<lang>-<screen>.txt` for a read-through.
 *
 * sdk 35 rather than the usual 33 gives this class its own Robolectric
 * sandbox, so the model enums (whose descriptions are read once, as they are
 * after the app's language restart) are built in this language, not left over
 * in English from another test class. One language only, for the same reason:
 * a second one in this sandbox would see the first one's enum text. Japanese,
 * because no English word survives into it by coincidence.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h440dp-land-mdpi")
class TranslationCoverageTest(private val lang: String) {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun langs(): List<Array<Any>> = listOf(arrayOf<Any>("ja"))
    }

    @get:Rule
    val compose = createComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    /** English lines whose translation differs: none of these may be on screen. */
    private fun forbidden(): Set<String> {
        val english = I18nXml.read(File("src/main/res/values/strings_i18n.xml")).values
        return english.filter { Tr.t(it) != it && it.any(Char::isLetter) }
            .flatMap { listOf(it, it.uppercase()) }.toSet()
    }

    private fun check(screen: String, content: @Composable () -> Unit) {
        Tr.init(app, lang)
        compose.mainClock.autoAdvance = false
        compose.setContent { CyOpsTheme { Box(Modifier.fillMaxSize()) { content() } } }
        compose.mainClock.advanceTimeBy(300)
        val texts = mutableListOf<String>()
        fun walk(node: SemanticsNode) {
            node.config.getOrNull(SemanticsProperties.Text)?.forEach { texts += it.text }
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.let { texts += it }
            node.children.forEach(::walk)
        }
        walk(compose.onRoot(useUnmergedTree = true).fetchSemanticsNode())
        File("build/i18n-audit").mkdirs()
        File("build/i18n-audit/$lang-$screen.txt").writeText(texts.joinToString("\n"))
        val bad = forbidden()
        val leaks = texts.map { it.trim() }.filter { it in bad }.distinct()
        Tr.init(app, null)
        assertTrue("$screen in $lang still shows English: $leaks", leaks.isEmpty())
    }

    @Test fun mainMenu() = check("menu") {
        MainMenuScreen(
            hasSavedRun = true, stats = PlayerStats(highestWave = 64), budget = 12_500L,
            firmwareLevel = 12, adsRemoved = false, backgroundAnimation = false,
            onPlay = {}, onContinue = {}, onAgents = {}, onFirmware = {}, onCodex = {}, onStore = {},
            onLoadout = {}, onPlayAccount = {}, onLeaderboard = {},
            onStatistics = {}, onSettings = {}, onAbout = {}, onExit = {}
        )
    }

    @Test fun about() = check("about") { AboutScreen(backgroundAnimation = false, onBack = {}) }

    @Test fun firmware() = check("firmware") {
        FirmwareScreen(
            budget = 99_000, firmwareLevel = 24, lifetimeBudgetEarned = 500_000,
            backgroundAnimation = false, onBuy = {}, onBack = {}
        )
    }

    @Test fun agents() = check("agents") {
        AgentsScreen(unlockedAgents = emptySet(), highestWave = 40, backgroundAnimation = false, onBack = {})
    }

    @Test fun store() = check("store") {
        StoreScreen(
            entitlements = Entitlements(), budget = 99_000, prices = emptyMap(),
            status = BillingStatus.READY, backgroundAnimation = false,
            onBuy = {}, onRestore = {}, onBack = {}
        )
    }

    @Test fun loadout() = check("loadout") {
        LoadoutScreen(
            entitlements = Entitlements(), cosmetics = CosmeticChoice(), backgroundAnimation = false,
            onChooseCoreSkin = {}, onChooseBackground = {}, onSpectrumAgents = {}, onOpenStore = {}, onBack = {}
        )
    }

    @Test fun settings() = check("settings") {
        SettingsScreen(settings = GameSettings(), backgroundAnimation = false, onUpdate = {}, onResetProgress = {}, onBack = {})
    }

    @Test fun codex() = check("codex") { CodexScreen(backgroundAnimation = false, onBack = {}) }

    @Test fun statistics() = check("statistics") {
        StatisticsScreen(
            stats = PlayerStats(highestWave = 120, totalGamesPlayed = 240, deploymentsByAgent = mapOf("FIREWALL" to 9)),
            budget = 99_000, firmwareLevel = 24, lifetimeBudgetEarned = 500_000,
            backgroundAnimation = false, onBack = {}
        )
    }

    @Test fun leaderboard() = check("leaderboard") {
        LeaderboardScreen(
            identity = PlayerIdentity(username = "OPERATOR", highestWave = 120),
            entries = List(3) { LeaderboardEntry(username = "OP_$it", wave = 120 - it, damage = 9_000L) },
            backgroundAnimation = false, onRegister = {}, onBack = {}
        )
    }

    @Test fun playAccount() = check("google-play") {
        PlayAccountScreen(
            entitlements = Entitlements(), identity = PlayerIdentity(), budget = 250,
            status = BillingStatus.READY, adsConfigured = true,
            cloudStatus = CloudSaveStatus.NOT_LINKED, cloudAccount = null, lastCloudSync = null,
            cloudBusy = false, backgroundAnimation = false,
            onRestore = {}, onLinkCloud = {}, onSyncCloud = {}, onOpenOrders = {}, onOpenListing = {},
            onCallsign = {}, onBack = {}
        )
    }

    @Test fun pause() = check("pause") {
        PauseOverlay(wave = 37, onResume = {}, onRestart = {}, onSettings = {}, onMainMenu = {})
    }

    @Test fun gameOver() = check("game-over") {
        GameOverOverlay(
            summary = GameOverSummary(
                waveReached = 121, attacksBlocked = 4_820, cryptoEarned = 96_400,
                bossesDefeated = 30, bestWave = 121, isNewRecord = true
            ),
            onRetry = {}, onMainMenu = {}, onWatchAdToRevive = {}, revivesLeft = 1
        )
    }

    @Test fun runSetup() {
        Tr.init(app, lang)
        val vm = GameViewModel(app, TestStores.isolatedRepository())
        shadowOf(Looper.getMainLooper()).idle()
        check("run-setup") {
            RunSetupScreen(
                stats = vm.stats, availableModes = vm.availableModes, selectedMode = vm.selectedMode,
                availableMaps = vm.availableMaps, selectedMap = vm.selectedMap, backgroundAnimation = false,
                onSelectMode = {}, onSelectMap = {}, onStart = {}, onBack = {}
            )
        }
    }

    @Test fun matchWithRosterOpen() {
        Tr.init(app, lang)
        val vm = GameViewModel(app, TestStores.isolatedRepository())
        shadowOf(Looper.getMainLooper()).idle()
        vm.startNewGame()
        shadowOf(Looper.getMainLooper()).idle()
        vm.toggleDeployPanel()
        check("match") { GameScreen(viewModel = vm, onExitToMenu = {}, onOpenSettings = {}) }
    }
}
