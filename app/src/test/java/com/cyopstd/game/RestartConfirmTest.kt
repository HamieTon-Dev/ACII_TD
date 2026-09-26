package com.cyopstd.game

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.cyopstd.game.ui.game.PauseOverlay
import com.cyopstd.game.ui.theme.CyOpsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** RESTART throws a run away, so it asks first (owner, 2026-09-26). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1920dp-h1080dp-land-xhdpi")
class RestartConfirmTest {

    @get:Rule
    val compose = createComposeRule()

    private var restarts = 0

    private fun show() {
        compose.setContent {
            CyOpsTheme {
                PauseOverlay(
                    wave = 42,
                    onResume = {},
                    onRestart = { restarts++ },
                    onSettings = {},
                    onMainMenu = {}
                )
            }
        }
    }

    @Test
    fun `RESTART asks before it restarts`() {
        show()
        compose.onNodeWithText("RESTART").performClick()
        assertEquals("one tap must not restart", 0, restarts)
        compose.onNodeWithText("Are you sure you want to restart your run?").assertIsDisplayed()
        compose.onNodeWithText("Wave 42", substring = true).assertIsDisplayed()

        compose.onNodeWithText("YES").performClick()
        assertEquals(1, restarts)
    }

    @Test
    fun `NO goes back to the pause menu and restarts nothing`() {
        show()
        compose.onNodeWithText("RESTART").performClick()
        compose.onNodeWithText("NO").performClick()

        assertEquals(0, restarts)
        compose.onNodeWithText("RESUME").assertIsDisplayed()
        compose.onNodeWithText("Are you sure you want to restart your run?").assertDoesNotExist()
    }
}
