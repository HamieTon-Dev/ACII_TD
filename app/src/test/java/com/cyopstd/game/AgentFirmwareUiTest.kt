package com.cyopstd.game

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.cyopstd.game.model.AgentFirmware
import com.cyopstd.game.model.AgentType
import com.cyopstd.game.model.FirmwareStat
import com.cyopstd.game.ui.menu.FirmwareScreen
import com.cyopstd.game.ui.theme.CyOpsTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The AGENT FIRMWARE tab on the FIRMWARE screen. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w800dp-h360dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AgentFirmwareUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `the agent tab lists every agent but the engineer and buys one stat`() {
        val bought = mutableListOf<Triple<AgentType, FirmwareStat, Int>>()
        compose.setContent {
            CyOpsTheme {
                FirmwareScreen(
                    budget = 30_000, firmwareLevel = 12, lifetimeBudgetEarned = 40_000,
                    backgroundAnimation = false, onBuy = {}, onBack = {},
                    agentFirmware = mapOf(AgentType.FIREWALL to AgentFirmware(damage = 40, rate = 5, range = 2)),
                    unlockedAgents = AgentType.catalog.map { it.name }.toSet(),
                    onBuyAgent = { t, s, n -> bought += Triple(t, s, n) }
                )
            }
        }
        compose.onNodeWithText("PER AGENT").performClick()
        compose.onNodeWithTag("firmware-agent-${AgentType.FIREWALL.name}").performClick()
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { view.draw(Canvas(bitmap)) }
        File("build/previews").mkdirs()
        File("build/previews/agent-firmware.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

        compose.onNodeWithTag("firmware-agent-${AgentType.SERVER_SYSTEMS_ENGINEER.name}").assertDoesNotExist()
        compose.onNodeWithText("LV 40", substring = true).assertExists()
        compose.onNodeWithTag("firmware-RANGE-+10").performScrollTo().performClick()
        assertEquals(listOf(Triple(AgentType.FIREWALL, FirmwareStat.RANGE, 10)), bought)
    }
}
