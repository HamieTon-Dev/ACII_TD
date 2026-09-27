package com.cyopstd.game

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.cyopstd.game.model.AgentType
import com.cyopstd.game.save.GameSettings
import com.cyopstd.game.ui.game.DeployPanel
import com.cyopstd.game.ui.game.agentClassColor
import com.cyopstd.game.ui.settings.SettingsScreen
import com.cyopstd.game.ui.theme.CyOpsTheme
import com.cyopstd.game.ui.theme.Palette
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The compact agents menu (owner, 2026-09-27): a settings switch that turns
 * the deploy bar into small icons, each in the agent's board colour, with its
 * cost in yellow below and nothing else.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w800dp-h360dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CompactAgentBarTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /** Near the start of the row, so it is on screen without scrolling. */
    private val locked = AgentType.catalog[2]
    private val unlocked = (AgentType.catalog.map { it.name } - locked.name).toSet()

    private fun snapshot(name: String) {
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { view.draw(Canvas(bitmap)) }
        File("build/previews").mkdirs()
        File("build/previews/$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun show(compact: Boolean, onSelect: (AgentType) -> Unit = {}) {
        compose.setContent {
            CyOpsTheme {
                Box(Modifier.fillMaxSize().background(Palette.Background)) {
                    var selected by remember { mutableStateOf<AgentType?>(null) }
                    DeployPanel(
                        crypto = 300,
                        unlockedAgents = unlocked,
                        selected = selected,
                        onSelect = { selected = it; onSelect(it) },
                        onClose = {},
                        compact = compact,
                        modifier = Modifier
                            .align(if (compact) Alignment.BottomStart else Alignment.BottomCenter)
                            .padding(8.dp)
                    )
                }
            }
        }
    }

    @Test
    fun `off by default, so new players keep the full cards`() {
        assertFalse(GameSettings().compactAgentBar)
    }

    @Test
    fun `the switch is saved`() = runBlocking {
        val repository = TestStores.isolatedRepository()
        repository.updateSettings { it.copy(compactAgentBar = true) }
        assertTrue(repository.settings.first().compactAgentBar)
        repository.updateSettings { it.copy(compactAgentBar = false) }
        assertFalse(repository.settings.first().compactAgentBar)
    }

    @Test
    fun `compact shows only small icons and yellow costs, and still deploys`() {
        var picked: AgentType? = null
        show(compact = true) { picked = it }
        snapshot("compact-agents")

        // Just the icon and the cost: no names, stats or header.
        compose.onNodeWithText("DEPLOY CYBER AGENT").assertDoesNotExist()
        compose.onNodeWithText(AgentType.FIREWALL.shortName).assertDoesNotExist()
        compose.onNodeWithText("[${AgentType.FIREWALL.glyph}]").assertIsDisplayed()
        compose.onNodeWithText("${AgentType.FIREWALL.cost}").assertIsDisplayed()

        // Small, like the owner's example.
        val bounds = compose.onNodeWithTag("compact-agent-${AgentType.FIREWALL.name}").getBoundsInRoot()
        assertTrue("icon is ${bounds.right - bounds.left} wide", (bounds.right - bounds.left).value <= 60f)
        assertTrue("icon is ${bounds.bottom - bounds.top} tall", (bounds.bottom - bounds.top).value <= 56f)

        compose.onNodeWithTag("compact-agent-${AgentType.FIREWALL.name}").performClick()
        assertEquals(AgentType.FIREWALL, picked)
    }

    @Test
    fun `a locked agent still explains itself`() {
        show(compact = true)
        compose.onNodeWithTag("compact-agent-${locked.name}").performClick()
        compose.onNodeWithText(locked.unlockRequirement, substring = true).assertIsDisplayed()
    }

    @Test
    fun `icons use the agent's board colour`() {
        assertEquals(Palette.Green, agentClassColor(AgentType.FIREWALL))
        assertEquals(Palette.Red, agentClassColor(AgentType.REDHAT))
        assertEquals(Palette.Blue, agentClassColor(AgentType.BLUEHAT))
    }

    @Test
    fun `the switch is on the settings screen`() {
        compose.setContent {
            CyOpsTheme {
                SettingsScreen(
                    settings = GameSettings(),
                    backgroundAnimation = false,
                    onUpdate = {},
                    onResetProgress = {},
                    onBack = {}
                )
            }
        }
        compose.onNodeWithText("COMPACT AGENTS MENU").assertExists()
    }

    @Test
    fun `full preview for comparison`() {
        show(compact = false)
        snapshot("full-agents")
    }
}
