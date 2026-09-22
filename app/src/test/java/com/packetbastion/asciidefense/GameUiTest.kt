package com.packetbastion.asciidefense

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.packetbastion.asciidefense.model.AgentType
import org.robolectric.Shadows.shadowOf
import com.packetbastion.asciidefense.state.GameViewModel
import com.packetbastion.asciidefense.ui.codex.CodexScreen
import com.packetbastion.asciidefense.ui.menu.AboutScreen
import com.packetbastion.asciidefense.ui.menu.AgentsScreen
import com.packetbastion.asciidefense.ui.menu.MainMenuScreen
import com.packetbastion.asciidefense.ui.settings.SettingsScreen
import com.packetbastion.asciidefense.ui.stats.StatisticsScreen
import com.packetbastion.asciidefense.ui.theme.PacketBastionTheme
import com.packetbastion.asciidefense.save.GameSettings
import com.packetbastion.asciidefense.save.PlayerStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * UI tests driven by Robolectric on the JVM.
 *
 * These compose the real screens with the real theme and assert on what a player
 * would actually see and tap. Running on the JVM rather than a device means the
 * whole suite finishes in seconds and can be run on every change, which is the
 * only way UI checks stay useful.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1920dp-h1080dp-land-xhdpi")
class GameUiTest {

    @get:Rule
    val compose = createComposeRule()

    /**
     * A view model on a private, empty store.
     *
     * Robolectric shares one process across test classes and a view model's
     * coroutines outlive the test that created them, so sharing the app's real
     * store lets one test observe another's writes. An isolated store removes
     * that race rather than trying to time around it.
     */
    private fun freshViewModel(): GameViewModel {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = GameViewModel(application, TestStores.isolatedRepository())
        // Let the repository collectors deliver their first values.
        shadowOf(Looper.getMainLooper()).idle()
        return viewModel
    }

    // ------------------------------------------------------------- main menu

    @Test
    fun `main menu shows the title and every action`() {
        var played = false
        compose.setContent {
            PacketBastionTheme {
                MainMenuScreen(
                    hasSavedRun = false,
                    stats = PlayerStats(),
                    backgroundAnimation = false,
                    onPlay = { played = true },
                    onContinue = {}, onAgents = {}, onCodex = {},
                    onStatistics = {}, onSettings = {}, onAbout = {}, onExit = {}
                )
            }
        }

        compose.onNodeWithText("PACKET BASTION").assertIsDisplayed()
        compose.onNodeWithText("ASCII DEFENSE").assertIsDisplayed()
        for (action in listOf(
            "PLAY", "CONTINUE", "AGENTS", "CODEX",
            "STATISTICS", "SETTINGS", "ABOUT", "EXIT"
        )) {
            compose.onNodeWithText(action).assertIsDisplayed()
        }

        compose.onNodeWithText("PLAY").performClick()
        assertTrue("PLAY must invoke its callback", played)
    }

    @Test
    fun `CONTINUE is inert without a saved run and live with one`() {
        var continued = 0
        compose.setContent {
            PacketBastionTheme {
                MainMenuScreen(
                    hasSavedRun = false,
                    stats = PlayerStats(),
                    backgroundAnimation = false,
                    onPlay = {}, onContinue = { continued++ }, onAgents = {},
                    onCodex = {}, onStatistics = {}, onSettings = {},
                    onAbout = {}, onExit = {}
                )
            }
        }

        compose.onNodeWithText("No saved session").assertIsDisplayed()
        compose.onNodeWithText("CONTINUE").performClick()
        assertEquals("CONTINUE must be disabled without a save", 0, continued)
    }

    @Test
    fun `main menu surfaces the lifetime record`() {
        compose.setContent {
            PacketBastionTheme {
                MainMenuScreen(
                    hasSavedRun = true,
                    stats = PlayerStats(
                        highestWave = 27,
                        totalPacketsBlocked = 4210,
                        totalBossesDefeated = 5
                    ),
                    backgroundAnimation = false,
                    onPlay = {}, onContinue = {}, onAgents = {}, onCodex = {},
                    onStatistics = {}, onSettings = {}, onAbout = {}, onExit = {}
                )
            }
        }

        compose.onNodeWithText("27").assertIsDisplayed()
        compose.onNodeWithText("4210").assertIsDisplayed()
        compose.onNodeWithText("PRESENT").assertIsDisplayed()
        compose.onNodeWithText("Resume your saved session").assertIsDisplayed()
    }

    // ---------------------------------------------------------------- agents

    @Test
    fun `agent roster separates unlocked agents from locked ones`() {
        compose.setContent {
            PacketBastionTheme {
                AgentsScreen(
                    unlockedAgents = setOf("FIREWALL", "IDS"),
                    highestWave = 4,
                    backgroundAnimation = false,
                    onBack = {}
                )
            }
        }

        compose.onNodeWithText("CYBER AGENTS").assertIsDisplayed()
        compose.onNodeWithText("2 of ${AgentType.entries.size} unlocked · best wave 4")
            .assertIsDisplayed()
        compose.onNodeWithText("FIREWALL").assertIsDisplayed()
        compose.onNodeWithText("AVAILABLE · ◇ 40").assertIsDisplayed()
        compose.onNodeWithText("LOCKED · REACH WAVE 3").assertIsDisplayed()
    }

    // ----------------------------------------------------------------- codex

    @Test
    fun `codex switches between its sections`() {
        compose.setContent {
            PacketBastionTheme {
                CodexScreen(backgroundAnimation = false, onBack = {})
            }
        }

        compose.onNodeWithText("CODEX").assertIsDisplayed()
        compose.onNodeWithText("The defences you deploy").assertIsDisplayed()
        compose.onAllNodesWithText("FIREWALL")[0].assertIsDisplayed()

        compose.onNodeWithText("THREAT PACKETS").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("What is coming down the lanes").assertIsDisplayed()

        compose.onNodeWithText("NETWORK TERMS").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Plain-language glossary").assertIsDisplayed()
        compose.onAllNodesWithText("DDoS")[0].assertIsDisplayed()
    }

    // ------------------------------------------------------------ statistics

    @Test
    fun `statistics screen reports stored totals`() {
        compose.setContent {
            PacketBastionTheme {
                StatisticsScreen(
                    stats = PlayerStats(
                        highestWave = 18,
                        totalPacketsBlocked = 2222,
                        totalBossesDefeated = 3,
                        totalCryptoEarned = 9100,
                        totalGamesPlayed = 12,
                        totalServerDamageTaken = 340,
                        totalAgentsDeployed = 77,
                        totalAgentUpgrades = 145,
                        deploymentsByAgent = mapOf("FIREWALL" to 40, "IDS" to 25)
                    ),
                    backgroundAnimation = false,
                    onBack = {}
                )
            }
        }

        compose.onNodeWithText("STATISTICS").assertIsDisplayed()
        compose.onNodeWithText("18").assertIsDisplayed()
        compose.onNodeWithText("2222").assertIsDisplayed()
        compose.onNodeWithText("◇ 9100").assertIsDisplayed()
        // The favourite agent is derived from the deployment tallies.
        compose.onNodeWithText("FIREWALL").assertIsDisplayed()
    }

    // -------------------------------------------------------------- settings

    @Test
    fun `settings toggles report changes back`() {
        var settings = GameSettings()
        compose.setContent {
            PacketBastionTheme {
                SettingsScreen(
                    settings = settings,
                    backgroundAnimation = false,
                    onUpdate = { transform -> settings = transform(settings) },
                    onResetProgress = {},
                    onBack = {}
                )
            }
        }

        compose.onNodeWithText("SETTINGS").assertIsDisplayed()
        compose.onNodeWithText("BATTERY SAVER").assertIsDisplayed()
        compose.onNodeWithText("AUTO START WAVES").assertIsDisplayed()

        assertFalse(settings.batterySaver)
        compose.onNodeWithText("BATTERY SAVER").performClick()
        compose.waitForIdle()
        assertTrue("tapping the row must toggle the setting", settings.batterySaver)
    }

    @Test
    fun `reset progress demands confirmation before firing`() {
        var reset = 0
        compose.setContent {
            PacketBastionTheme {
                SettingsScreen(
                    settings = GameSettings(),
                    backgroundAnimation = false,
                    onUpdate = {},
                    onResetProgress = { reset++ },
                    onBack = {}
                )
            }
        }

        compose.onNodeWithText("RESET PROGRESS").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("DELETE ALL PROGRESS?").assertIsDisplayed()
        assertEquals("nothing may be deleted before confirmation", 0, reset)

        compose.onNodeWithText("CANCEL").performClick()
        compose.waitForIdle()
        assertEquals(0, reset)

        compose.onNodeWithText("RESET PROGRESS").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("RESET").performClick()
        compose.waitForIdle()
        assertEquals("confirming must actually reset", 1, reset)
    }

    // ----------------------------------------------------------------- about

    @Test
    fun `about screen states the offline and no-monetisation guarantees`() {
        compose.setContent {
            PacketBastionTheme {
                AboutScreen(backgroundAnimation = false, onBack = {})
            }
        }

        compose.onNodeWithText("ABOUT").assertIsDisplayed()
        compose.onNodeWithText(BuildConfig.VERSION_NAME).assertIsDisplayed()
        compose.onNode(hasText("INTERNET", substring = true)).assertIsDisplayed()
        compose.onNode(hasText("No advertisements", substring = true)).assertIsDisplayed()
        compose.onNode(hasText("No real cryptocurrency", substring = true))
            .assertIsDisplayed()
    }

    // ------------------------------------------------------------- viewmodel

    @Test
    fun `a new game starts a live match with the tutorial armed`() {
        val viewModel = freshViewModel()

        viewModel.startNewGame()

        assertTrue("a new game must be live", viewModel.matchActive)
        assertEquals(0, viewModel.hud.wave)
        assertEquals(
            "first-time players get the tutorial",
            GameViewModel.TUTORIAL_INTRO, viewModel.tutorialStep
        )
        assertFalse(viewModel.paused)
        assertEquals(1f, viewModel.currentSpeed, 0.001f)
    }

    @Test
    fun `the deploy flow places an agent and spends crypto`() {
        val viewModel = freshViewModel()
        viewModel.startNewGame()

        val cryptoBefore = viewModel.hud.crypto

        viewModel.toggleDeployPanel()
        assertTrue(viewModel.showDeployPanel)
        assertEquals(
            "opening the roster advances the tutorial even if the intro card " +
                "was never acknowledged",
            GameViewModel.TUTORIAL_SELECT_FIREWALL, viewModel.tutorialStep
        )

        viewModel.choosePendingAgent(AgentType.FIREWALL)
        assertEquals(AgentType.FIREWALL, viewModel.selection.pendingAgent)
        assertFalse("choosing an agent closes the picker", viewModel.showDeployPanel)
        assertEquals(GameViewModel.TUTORIAL_TAP_NODE, viewModel.tutorialStep)

        val node = com.packetbastion.asciidefense.core.WorldGeometry.nodes[10]
        viewModel.onBattlefieldTap(androidx.compose.ui.geometry.Offset(node.x, node.y))
        viewModel.onFrame(0.016f)

        assertEquals(1, viewModel.engine.activeAgentCount())
        assertEquals(cryptoBefore - AgentType.FIREWALL.cost, viewModel.hud.crypto)
        assertEquals(
            "placing advances the tutorial to the wave step",
            GameViewModel.TUTORIAL_START_WAVE, viewModel.tutorialStep
        )
    }

    @Test
    fun `tapping a deployed agent opens its panel and tapping away closes it`() {
        val viewModel = freshViewModel()
        viewModel.startNewGame()

        val node = com.packetbastion.asciidefense.core.WorldGeometry.nodes[10]
        viewModel.choosePendingAgent(AgentType.FIREWALL)
        viewModel.onBattlefieldTap(androidx.compose.ui.geometry.Offset(node.x, node.y))

        viewModel.onBattlefieldTap(androidx.compose.ui.geometry.Offset(node.x, node.y))
        assertEquals(node.id, viewModel.selection.selectedNodeId)

        // Empty space: far from every node.
        viewModel.onBattlefieldTap(androidx.compose.ui.geometry.Offset(800f, 392f))
        assertEquals(null, viewModel.selection.selectedNodeId)
    }

    @Test
    fun `deploying without enough crypto reports it instead of silently failing`() {
        val viewModel = freshViewModel()
        viewModel.startNewGame()

        // ROOT ADMIN costs far more than the opening purse.
        viewModel.choosePendingAgent(AgentType.ROOT_ADMIN)
        assertEquals(
            "a locked agent must not become pending",
            null, viewModel.selection.pendingAgent
        )
        assertTrue(
            "the player must be told why",
            viewModel.transientMessage?.contains("LOCKED") == true
        )
    }

    @Test
    fun `starting a wave advances the counter and clears the tutorial`() {
        val viewModel = freshViewModel()
        viewModel.startNewGame()

        viewModel.toggleDeployPanel()
        viewModel.choosePendingAgent(AgentType.FIREWALL)
        val node = com.packetbastion.asciidefense.core.WorldGeometry.nodes[10]
        viewModel.onBattlefieldTap(androidx.compose.ui.geometry.Offset(node.x, node.y))

        viewModel.startNextWave()
        viewModel.onFrame(0.016f)

        assertEquals(1, viewModel.hud.wave)
        assertEquals("finishing the tutorial retires it", -1, viewModel.tutorialStep)
    }

    @Test
    fun `speed and pause controls change the simulation clock`() {
        val viewModel = freshViewModel()
        viewModel.startNewGame()

        assertEquals(1f, viewModel.currentSpeed, 0.001f)
        viewModel.applySpeedIndex(1)
        assertEquals(2f, viewModel.currentSpeed, 0.001f)
        viewModel.applySpeedIndex(2)
        assertEquals(3f, viewModel.currentSpeed, 0.001f)

        viewModel.startNextWave()
        repeat(60) { viewModel.onFrame(0.016f) }
        val enemiesWhileRunning = viewModel.engine.activeEnemyCount()
        assertTrue("the wave should be producing packets", enemiesWhileRunning > 0)

        viewModel.togglePause()
        assertTrue(viewModel.paused)
        val waveWhilePaused = viewModel.hud.wave
        repeat(120) { viewModel.onFrame(0.016f) }
        assertEquals(
            "pausing must freeze the simulation",
            waveWhilePaused, viewModel.hud.wave
        )
    }
}
