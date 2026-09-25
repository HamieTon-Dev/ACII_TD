package com.cyopstd.game

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.cyopstd.game.core.GameMode
import com.cyopstd.game.core.Maps
import com.cyopstd.game.state.GameViewModel
import com.cyopstd.game.save.PlayerStats
import com.cyopstd.game.store.CosmeticChoice
import com.cyopstd.game.store.Entitlements
import com.cyopstd.game.store.Sku
import com.cyopstd.game.ui.menu.LoadoutScreen
import com.cyopstd.game.ui.menu.MainMenuScreen
import com.cyopstd.game.ui.theme.CoreSkin
import com.cyopstd.game.ui.theme.CyOpsTheme
import com.cyopstd.game.ui.theme.LivingBackground
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Wearing what you bought, and choosing what you play.
 *
 * Both of these were plumbed end to end and had no control anywhere: the
 * renderer read a core skin the player could not set, and the engine accepted
 * a game mode nothing could select. This covers the two screens that close
 * that, and the rules that keep them honest — a locked item must be visible
 * but inert, and the free option must never be lockable.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1920dp-h1080dp-land-xhdpi")
class LoadoutTest {

    @get:Rule
    val compose = createComposeRule()

    private fun loadout(
        entitlements: Entitlements = Entitlements(),
        cosmetics: CosmeticChoice = CosmeticChoice(spectrumAgents = false),
        onCoreSkin: (String?) -> Unit = {},
        onBackground: (String?) -> Unit = {},
        onSpectrum: (Boolean) -> Unit = {},
        onStore: () -> Unit = {}
    ) {
        compose.setContent {
            CyOpsTheme {
                LoadoutScreen(
                    entitlements = entitlements,
                    cosmetics = cosmetics,
                    backgroundAnimation = false,
                    onChooseCoreSkin = onCoreSkin,
                    onChooseBackground = onBackground,
                    onSpectrumAgents = onSpectrum,
                    onOpenStore = onStore,
                    onBack = {}
                )
            }
        }
    }

    @Test
    fun `every skin and background is listed, owned or not`() {
        loadout(entitlements = Entitlements())
        // Hiding what is locked would leave a player unable to tell what the
        // store is even selling.
        for (skin in CoreSkin.entries) {
            compose.onAllNodesWithText(skin.displayName).assertCountEquals(1)
        }
        for (background in LivingBackground.entries) {
            compose.onAllNodesWithText(background.displayName).assertCountEquals(1)
        }
    }

    @Test
    fun `the free look is always equippable`() {
        var chosen: String? = "something"
        loadout(entitlements = Entitlements(), onCoreSkin = { chosen = it })

        compose.onNodeWithText(CoreSkin.DEFAULT.displayName).performClick()
        // null is "the default", and it must be reachable from a save that owns
        // nothing at all — there is always a way back to the plain game.
        assertNull(chosen)
    }

    @Test
    fun `an owned skin can be equipped`() {
        var chosen: String? = null
        loadout(
            entitlements = Entitlements().plus(Sku.CORE_SKIN_REACTOR),
            onCoreSkin = { chosen = it }
        )

        compose.onNodeWithText(CoreSkin.REACTOR.displayName).performClick()
        assertEquals("core_skin_reactor", chosen)
    }

    @Test
    fun `a locked skin does nothing when tapped`() {
        var taps = 0
        loadout(entitlements = Entitlements(), onCoreSkin = { taps++ })

        compose.onNodeWithText(CoreSkin.NEONGRID.displayName).performClick()
        // Not a checkout funnel: the row exists to inform, and the store button
        // is the way to buy.
        assertEquals(0, taps)
    }

    @Test
    fun `SPECTRUM is inert until it is owned, and works once it is`() {
        var value: Boolean? = null
        loadout(entitlements = Entitlements(), onSpectrum = { value = it })
        compose.onNodeWithText("SPECTRUM").performClick()
        assertNull("SPECTRUM must not be equippable unowned", value)
    }

    @Test
    fun `owning SPECTRUM makes it selectable`() {
        var value: Boolean? = null
        loadout(
            entitlements = Entitlements().plus(Sku.SKIN_AGENTS_SPECTRUM),
            onSpectrum = { value = it }
        )
        compose.onNodeWithText("SPECTRUM").performClick()
        assertEquals(true, value)
    }

    @Test
    fun `class colours can always be chosen back`() {
        var value: Boolean? = null
        loadout(
            entitlements = Entitlements().plus(Sku.SKIN_AGENTS_SPECTRUM),
            cosmetics = CosmeticChoice(spectrumAgents = true),
            onSpectrum = { value = it }
        )
        compose.onNodeWithText("CLASS COLOURS").performClick()
        assertEquals(false, value)
    }

    // -------------------------------------------------- the paid fifth speed

    @Test
    fun `a locked fifth speed is refused, not quietly swapped`() {
        val viewModel = GameViewModel(
            ApplicationProvider.getApplicationContext(),
            TestStores.isolatedRepository()
        )
        viewModel.fifthSpeedUnlocked = false

        viewModel.applySpeedIndex(3)

        // It used to coerce into range, which selected 3x and looked like the
        // button had worked. A control that silently does something else is
        // worse than one that does nothing.
        assertEquals(0, viewModel.speedIndex)
        assertTrue(
            "the player must be told where the speed is",
            viewModel.transientMessage?.contains("STORE") == true
        )
    }

    @Test
    fun `owning the fifth speed makes it selectable`() {
        val viewModel = GameViewModel(
            ApplicationProvider.getApplicationContext(),
            TestStores.isolatedRepository()
        )
        viewModel.fifthSpeedUnlocked = true

        viewModel.applySpeedIndex(3)

        assertEquals(3, viewModel.speedIndex)
        assertEquals(5f, viewModel.currentSpeed, 0.001f)
    }

    // ------------------------------------------------------------ run mode

    private fun menu(
        highestWave: Int,
        modes: List<GameMode>,
        selected: GameMode = GameMode.STANDARD,
        onSelectMode: (GameMode) -> Unit = {}
    ) {
        compose.setContent {
            CyOpsTheme {
                MainMenuScreen(
                    hasSavedRun = false,
                    stats = PlayerStats(highestWave = highestWave),
                    budget = 0L,
                    firmwareLevel = 0,
                    adsRemoved = false,
                    availableModes = modes,
                    selectedMode = selected,
                    availableMaps = listOf(Maps.PERIMETER),
                    selectedMap = Maps.PERIMETER,
                    backgroundAnimation = false,
                    onSelectMode = onSelectMode,
                    onSelectMap = {},
                    onPlay = {}, onContinue = {}, onAgents = {}, onFirmware = {},
                    onCodex = {}, onStore = {}, onLoadout = {}, onPlayAccount = {},
                    onLeaderboard = {}, onStatistics = {}, onSettings = {},
                    onAbout = {}, onExit = {}
                )
            }
        }
    }

    @Test
    fun `HACK AI is visible but locked before wave 100`() {
        var picked: GameMode? = null
        menu(highestWave = 40, modes = listOf(GameMode.STANDARD), onSelectMode = { picked = it })

        compose.onNodeWithText(GameMode.HACK_AI.runName).assertIsDisplayed()
        // Shown so there is something to aim at; inert until it is earned.
        compose.onNodeWithText(GameMode.HACK_AI.runName).performClick()
        assertNull(picked)
        compose.onNodeWithText("LOCKED · clear wave 100 (best: 40)").assertIsDisplayed()
    }

    @Test
    fun `HACK AI can be selected once it is earned`() {
        var picked: GameMode? = null
        menu(
            highestWave = 100,
            modes = GameMode.entries.toList(),
            onSelectMode = { picked = it }
        )

        compose.onNodeWithText(GameMode.HACK_AI.runName).performClick()
        assertEquals(GameMode.HACK_AI, picked)
    }

    @Test
    fun `the PLAY button names the mode and the level it will start`() {
        menu(highestWave = 100, modes = GameMode.entries.toList(), selected = GameMode.HACK_AI)
        // Choosing a hard mode or a hard level and forgetting is a wasted run,
        // so the button names both.
        compose.onNodeWithText(
            "${GameMode.HACK_AI.runName} on ${Maps.PERIMETER.displayName}"
        ).assertIsDisplayed()
    }

    @Test
    fun `the PLAY button names the level on the standard mode too`() {
        menu(highestWave = 0, modes = listOf(GameMode.STANDARD), selected = GameMode.STANDARD)
        compose.onNodeWithText("Start a run on ${Maps.PERIMETER.displayName}").assertIsDisplayed()
    }

    @Test
    fun `every mode the engine has is offered by the menu`() {
        // A mode added to GameMode but left out of the menu would be
        // unreachable, which is how HACK:AI shipped in the first place.
        menu(highestWave = 100, modes = GameMode.entries.toList())
        for (mode in GameMode.entries) {
            assertTrue(
                "${mode.runName} is not on the menu",
                compose.onAllNodesWithText(mode.runName).fetchSemanticsNodes().isNotEmpty()
            )
        }
    }
}
