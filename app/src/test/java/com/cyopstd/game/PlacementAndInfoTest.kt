package com.cyopstd.game

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.cyopstd.game.core.Maps
import com.cyopstd.game.core.WorldGeometry
import com.cyopstd.game.model.AgentType
import com.cyopstd.game.model.BossVariant
import com.cyopstd.game.save.GameSettings
import com.cyopstd.game.save.SavedRun
import com.cyopstd.game.state.GameViewModel
import com.cyopstd.game.ui.game.BattlefieldRenderOptions
import com.cyopstd.game.ui.game.BattlefieldRenderer
import com.cyopstd.game.ui.game.BattlefieldSelection
import com.cyopstd.game.ui.game.BossBriefing
import com.cyopstd.game.ui.game.BossWaveStrip
import com.cyopstd.game.ui.game.DeployPanel
import com.cyopstd.game.ui.game.WorldTransform
import com.cyopstd.game.ui.settings.SettingsScreen
import com.cyopstd.game.ui.theme.CyOpsTheme
import com.cyopstd.game.ui.theme.Palette
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Owner, 2026-09-28:
 *  Z1  an "i" on each full agent card that opens everything about it, and a
 *      list of the next boss wave's bosses in the bottom-left corner.
 *  Z2  a setting where placing takes two taps: the first shows the range on
 *      that spot, the second deploys.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w800dp-h360dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlacementAndInfoTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun waitFor(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline && !condition()) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
    }

    private fun snapshot(name: String) {
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { view.draw(Canvas(bitmap)) }
        File("build/previews").mkdirs()
        File("build/previews/$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    // ------------------------------------------------------------------ Z2

    private fun viewModelWith(confirm: Boolean): GameViewModel {
        val repository = TestStores.isolatedRepository()
        runBlocking {
            repository.saveRun(SavedRun(wave = 3, serverHp = 100, crypto = 5_000, mapId = Maps.PERIMETER.id))
            repository.updateSettings { it.copy(confirmPlacement = confirm) }
        }
        val viewModel = GameViewModel(ApplicationProvider.getApplicationContext<Application>(), repository)
        shadowOf(Looper.getMainLooper()).idle()
        var loaded = false
        viewModel.continueGame(onLoaded = { loaded = true })
        waitFor { loaded && viewModel.settings.confirmPlacement == confirm }
        return viewModel
    }

    @Test
    fun `tap twice to deploy is off by default and saved`() = runBlocking {
        assertFalse(GameSettings().confirmPlacement)
        val repository = TestStores.isolatedRepository()
        repository.updateSettings { it.copy(confirmPlacement = true) }
        assertTrue(repository.settings.first().confirmPlacement)
    }

    @Test
    fun `with it on, the first tap previews and the second deploys`() {
        val viewModel = viewModelWith(confirm = true)
        val engine = viewModel.engine
        val nodes = Maps.PERIMETER.nodesByCoverage
        val first = nodes[0]
        val second = nodes[1]
        val crypto = engine.crypto

        viewModel.choosePendingAgent(AgentType.FIREWALL)
        viewModel.onBattlefieldTap(Offset(first.x, first.y))
        assertNull("the first tap must not deploy", engine.agentAt(first.id))
        assertEquals(first.id, viewModel.selection.previewNodeId)
        assertEquals(crypto, engine.crypto)

        // Changing your mind moves the preview; still nothing placed.
        viewModel.onBattlefieldTap(Offset(second.x, second.y))
        assertNull(engine.agentAt(second.id))
        assertEquals(second.id, viewModel.selection.previewNodeId)

        viewModel.onBattlefieldTap(Offset(second.x + 3f, second.y - 2f))
        assertEquals(AgentType.FIREWALL, engine.agentAt(second.id)?.type)
        assertNull(engine.agentAt(first.id))
        assertNull(viewModel.selection.previewNodeId)
    }

    @Test
    fun `with it off, one tap deploys as before`() {
        val viewModel = viewModelWith(confirm = false)
        val node = Maps.PERIMETER.nodesByCoverage[0]
        viewModel.choosePendingAgent(AgentType.FIREWALL)
        viewModel.onBattlefieldTap(Offset(node.x, node.y))
        assertEquals(AgentType.FIREWALL, viewModel.engine.agentAt(node.id)?.type)
    }

    @Test
    fun `the preview draws the range ring even with SHOW AGENT RANGE off`() {
        val viewModel = viewModelWith(confirm = true)
        val engine = viewModel.engine
        val node = Maps.PERIMETER.nodesByCoverage[0]
        fun render(selection: BattlefieldSelection, name: String): Bitmap {
            val bitmap = Bitmap.createBitmap(
                WorldGeometry.WIDTH.toInt(), WorldGeometry.HEIGHT.toInt(), Bitmap.Config.ARGB_8888
            )
            BattlefieldRenderer().draw(
                canvas = Canvas(bitmap), engine = engine,
                transform = WorldTransform(WorldGeometry.WIDTH, WorldGeometry.HEIGHT),
                options = BattlefieldRenderOptions(backgroundAnimation = false, showAgentRange = false),
                selection = selection, time = 1f
            )
            File("build/previews").mkdirs()
            File("build/previews/$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            return bitmap
        }
        // Green on the ring, one range out to the right of the spot.
        fun ringGreen(bitmap: Bitmap): Int {
            val r = AgentType.FIREWALL.baseRange.toInt()
            var green = 0
            for (dx in r - 4..r + 4) for (dy in -30..30) {
                val x = node.x.toInt() + dx; val y = node.y.toInt() + dy
                if (x !in 0 until bitmap.width || y !in 0 until bitmap.height) continue
                val p = bitmap.getPixel(x, y)
                val red = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val b = p and 0xFF
                if (g > 120 && g > red + 50 && g > b) green++
            }
            return green
        }
        val placing = render(BattlefieldSelection(pendingAgent = AgentType.FIREWALL), "placing-no-preview")
        val preview = render(
            BattlefieldSelection(pendingAgent = AgentType.FIREWALL, previewNodeId = node.id), "placing-preview"
        )
        println("ring green: without preview ${ringGreen(placing)}, with ${ringGreen(preview)}")
        assertTrue("no range ring on the previewed spot", ringGreen(preview) > ringGreen(placing) + 20)
    }

    @Test
    fun `the setting is on the settings screen`() {
        compose.setContent {
            CyOpsTheme {
                SettingsScreen(settings = GameSettings(), backgroundAnimation = false,
                    onUpdate = {}, onResetProgress = {}, onBack = {})
            }
        }
        compose.onNodeWithText("TAP TWICE TO DEPLOY").assertExists()
    }

    // ------------------------------------------------------------------ Z1

    @Test
    fun `the i on a full agent card shows everything about it, without selecting it`() {
        var picked: AgentType? = null
        compose.setContent {
            CyOpsTheme {
                Box(Modifier.fillMaxSize().background(Palette.Background)) {
                    DeployPanel(
                        crypto = 300,
                        unlockedAgents = AgentType.catalog.map { it.name }.toSet(),
                        selected = null,
                        onSelect = { picked = it },
                        onClose = {},
                        modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp)
                    )
                }
            }
        }
        compose.onNodeWithTag("agent-info-${AgentType.FIREWALL.name}").performClick()
        compose.waitForIdle()
        snapshot("agent-info")
        assertNull("reading about it must not select it", picked)
        compose.onNodeWithText("IN-GAME").assertIsDisplayed()
        compose.onNodeWithText(AgentType.FIREWALL.inGame).assertExists()
        compose.onNodeWithText(AgentType.FIREWALL.realWorld).assertExists()

        compose.onNodeWithText("SELECT").performClick()
        assertEquals(AgentType.FIREWALL, picked)
    }

    @Test
    fun `the next boss wave's bosses are listed in the corner, and the i opens their briefing`() {
        val briefing = BossBriefing(
            wave = 30,
            variants = listOf(BossVariant.BREACH to 1, BossVariant.ZOMBIE to 2),
            bossCount = 3,
            modifiers = emptyList(),
            weakTo = emptyList(),
            warnings = emptyList()
        )
        var toggled = 0
        compose.setContent {
            CyOpsTheme {
                Box(Modifier.fillMaxSize().background(Palette.Background)) {
                    BossWaveStrip(
                        briefing = briefing,
                        open = false,
                        onToggle = { toggled++ },
                        modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
                    )
                }
            }
        }
        snapshot("boss-wave-strip")
        compose.onNodeWithText("BOSS WAVE 30", substring = true).assertIsDisplayed()
        compose.onNodeWithText(BossVariant.BREACH.displayName).assertIsDisplayed()
        compose.onNodeWithText("${BossVariant.ZOMBIE.displayName} ×2").assertIsDisplayed()
        compose.onNodeWithTag("boss-wave-info").performClick()
        assertEquals(1, toggled)
        // Bottom-left.
        val strip = compose.onNodeWithTag("boss-wave-strip").getBoundsInRoot()
        val root = compose.onRoot().getBoundsInRoot()
        assertTrue(strip.left < root.right * 0.2f && strip.bottom > root.bottom * 0.8f)
    }
}
