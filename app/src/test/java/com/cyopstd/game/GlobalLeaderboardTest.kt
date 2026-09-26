package com.cyopstd.game

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.cyopstd.game.ads.PlayServices
import com.cyopstd.game.core.GameMode
import com.cyopstd.game.save.GlobalScoreTag
import com.cyopstd.game.save.LeaderboardEntry
import com.cyopstd.game.save.NoGlobalLeaderboard
import com.cyopstd.game.save.PlayerIdentity
import com.cyopstd.game.ui.menu.LeaderboardScreen
import com.cyopstd.game.ui.theme.CyOpsTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The global board on Play Games.
 *
 * The Play calls themselves need a signed-in device and cannot run here. What
 * can be checked is everything the game decides: what rides in the score tag
 * and that it comes back out, which builds have a board at all, and that the
 * screen offers GLOBAL only when there is one and says why it is empty.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1920dp-h1080dp-land-xhdpi")
class GlobalLeaderboardTest {

    @get:Rule
    val compose = createComposeRule()

    // ------------------------------------------------------------ the tag

    @Test
    fun `callsign and damage survive the round trip`() {
        val tag = GlobalScoreTag.encode("NEO-01", 123_456_789L)
        assertEquals("NEO-01" to 123_456_789L, GlobalScoreTag.decode(tag))
    }

    @Test
    fun `the tag is always one Play will accept`() {
        // 64 characters from the URL-safe set, whatever the inputs.
        val worst = GlobalScoreTag.encode("a very long name with spaces & symbols!!", Long.MAX_VALUE)
        assertTrue(worst.length <= GlobalScoreTag.MAX_LENGTH)
        assertTrue(worst, worst.all { it.isLetterOrDigit() || it in "-._~" })
    }

    @Test
    fun `an unregistered player posts with no callsign and falls back to Play's name`() {
        val tag = GlobalScoreTag.encode("", 900)
        assertEquals(null to 900L, GlobalScoreTag.decode(tag))
    }

    @Test
    fun `a missing or foreign tag reads as nothing rather than garbage`() {
        assertEquals(null to null, GlobalScoreTag.decode(null))
        assertEquals(null to null, GlobalScoreTag.decode(""))
        assertEquals(null to null, GlobalScoreTag.decode("no-separator"))
        assertEquals("ABC" to null, GlobalScoreTag.decode("ABC.notanumber"))
    }

    // ---------------------------------------------------------- which build

    @Test
    fun `no games project means no global board, whatever ids are set`() {
        val ids = mapOf(GameMode.STANDARD to "CgkIabc", GameMode.HACK_AI to "CgkIdef")
        assertTrue(PlayServices.leaderboardIds(gamesConfigured = false, raw = ids).isEmpty())
        assertEquals(ids, PlayServices.leaderboardIds(gamesConfigured = true, raw = ids))
    }

    @Test
    fun `a mode with no id keeps a local board only`() {
        val ids = PlayServices.leaderboardIds(
            gamesConfigured = true,
            raw = mapOf(GameMode.STANDARD to "CgkIabc", GameMode.HACK_AI to "  ")
        )
        assertEquals(setOf(GameMode.STANDARD), ids.keys)
    }

    @Test
    fun `the unconfigured gateway fails quietly`() = runBlocking {
        val none = NoGlobalLeaderboard()
        assertFalse(none.configured)
        assertFalse(none.submit(LeaderboardEntry("NEO", 10, 5)))
        assertNull(none.top(GameMode.STANDARD))
    }

    // ------------------------------------------------------------- the screen

    private fun show(
        globalModes: List<GameMode>,
        globalEntries: List<LeaderboardEntry>? = null,
        signedIn: Boolean = true,
        onShowGlobal: (GameMode) -> Unit = {}
    ) {
        compose.setContent {
            CyOpsTheme {
                LeaderboardScreen(
                    identity = PlayerIdentity(username = "NEO"),
                    entries = emptyList(),
                    backgroundAnimation = false,
                    onRegister = {},
                    onBack = {},
                    globalModes = globalModes,
                    globalEntries = globalEntries,
                    signedIn = signedIn,
                    onShowGlobal = onShowGlobal
                )
            }
        }
    }

    @Test
    fun `a build with no board offers no GLOBAL view`() {
        show(globalModes = emptyList())
        compose.onNodeWithText("THIS DEVICE").assertDoesNotExist()
        compose.onNodeWithText("GLOBAL · ${GameMode.STANDARD.runName}").assertDoesNotExist()
    }

    @Test
    fun `the global view lists callsigns and asks for the right mode`() {
        var asked: GameMode? = null
        show(
            globalModes = listOf(GameMode.STANDARD),
            globalEntries = listOf(LeaderboardEntry("TRINITY", 212, 9_000_000)),
            onShowGlobal = { asked = it }
        )
        compose.onNodeWithText("GLOBAL · ${GameMode.STANDARD.runName}").performClick()
        assertEquals(GameMode.STANDARD, asked)
        compose.onNodeWithText("TRINITY").assertExists()
        compose.onNodeWithText("212").assertExists()
    }

    @Test
    fun `a player who is not signed in is told how to join`() {
        show(globalModes = listOf(GameMode.STANDARD), signedIn = false)
        compose.onNodeWithText("GLOBAL · ${GameMode.STANDARD.runName}").performClick()
        compose.onNodeWithText("Link your Google account", substring = true).assertExists()
    }
}
