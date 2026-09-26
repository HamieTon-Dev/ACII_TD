package com.cyopstd.game

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasSetTextAction
import com.cyopstd.game.save.PlayerIdentity
import com.cyopstd.game.ui.menu.LeaderboardScreen
import com.cyopstd.game.ui.theme.CyOpsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The callsign is typed in a box pinned to the top of the screen, because in
 * landscape the keyboard covered the old in-panel field (owner, 2026-09-26).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w800dp-h400dp-land-xhdpi")
class CallsignEntryTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `entering a callsign opens a floating box, and SAVE registers it`() {
        val registered = mutableListOf<String>()
        compose.setContent {
            CyOpsTheme {
                LeaderboardScreen(
                    identity = PlayerIdentity(),
                    entries = emptyList(),
                    backgroundAnimation = false,
                    onRegister = { registered += it },
                    onBack = {}
                )
            }
        }

        compose.onNodeWithText("NOT SET").assertIsDisplayed()
        compose.onNodeWithText("ENTER CALLSIGN").performClick()

        compose.onNodeWithText("SAVE").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextInput("neo 01!")
        // Sanitised as typed: uppercase, no spaces or symbols.
        compose.onNodeWithText("NEO01").assertIsDisplayed()
        compose.onNodeWithText("SAVE").performClick()

        assertEquals(listOf("NEO01"), registered)
        compose.onNodeWithText("CANCEL").assertDoesNotExist()
    }

    @Test
    fun `CANCEL closes the box and registers nothing`() {
        val registered = mutableListOf<String>()
        compose.setContent {
            CyOpsTheme {
                LeaderboardScreen(
                    identity = PlayerIdentity(username = "TRINITY"),
                    entries = emptyList(),
                    backgroundAnimation = false,
                    onRegister = { registered += it },
                    onBack = {}
                )
            }
        }
        compose.onNodeWithText("EDIT CALLSIGN").performClick()
        compose.onNodeWithText("CANCEL").performClick()
        assertEquals(emptyList<String>(), registered)
    }
}
