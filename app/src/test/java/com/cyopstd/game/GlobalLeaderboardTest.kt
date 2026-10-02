package com.cyopstd.game

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import com.cyopstd.game.ads.PlayServices
import com.cyopstd.game.core.GameMode
import com.cyopstd.game.core.Maps
import com.cyopstd.game.save.BoardKey
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
        boards: Set<BoardKey>,
        globalEntries: List<LeaderboardEntry>? = null,
        signedIn: Boolean = true,
        entries: List<LeaderboardEntry> = emptyList(),
        onShowGlobal: (BoardKey) -> Unit = {}
    ) {
        compose.setContent {
            CyOpsTheme {
                LeaderboardScreen(
                    identity = PlayerIdentity(username = "NEO"),
                    entries = entries,
                    backgroundAnimation = false,
                    onRegister = {},
                    onBack = {},
                    hasGlobalBoard = { it in boards },
                    anyGlobalBoard = boards.isNotEmpty(),
                    globalEntries = globalEntries,
                    signedIn = signedIn,
                    onShowGlobal = onShowGlobal
                )
            }
        }
    }

    private fun pick(tag: String, option: String) {
        compose.onNodeWithTag(tag).performClick()
        compose.onNodeWithText(option).performScrollTo().performClick()
    }

    @Test
    fun `a build with no board offers no WORLDWIDE view`() {
        show(boards = emptySet())
        compose.onNodeWithText("THIS DEVICE").assertDoesNotExist()
        compose.onNodeWithText("WORLDWIDE").assertDoesNotExist()
    }

    @Test
    fun `the worldwide view follows the level and difficulty picked`() {
        val mirai = BoardKey(GameMode.HACK_AI, Maps.TRIDENT.id)
        val asked = mutableListOf<BoardKey>()
        show(
            boards = setOf(BoardKey(GameMode.STANDARD), mirai),
            globalEntries = listOf(LeaderboardEntry("TRINITY", 212, 9_000_000)),
            onShowGlobal = { asked += it }
        )
        compose.onNodeWithText("WORLDWIDE").performClick()
        compose.onNodeWithText("Pick a difficulty", substring = true).assertExists()

        pick("board-difficulty", GameMode.STANDARD.runName)
        assertEquals(BoardKey(GameMode.STANDARD), asked.last())
        compose.onNodeWithText("TRINITY").assertExists()
        compose.onNodeWithText("212").assertExists()

        pick("board-level", "L6 \u00B7 MIRAI")
        compose.onNodeWithText("No worldwide board for MIRAI", substring = true).assertExists()
        pick("board-difficulty", GameMode.HACK_AI.runName)
        assertEquals(mirai, asked.last())
    }

    @Test
    fun `a player who is not signed in is told how to join`() {
        show(boards = setOf(BoardKey(GameMode.STANDARD)), signedIn = false)
        compose.onNodeWithText("WORLDWIDE").performClick()
        pick("board-difficulty", GameMode.STANDARD.runName)
        compose.onNodeWithText("Link your Google account", substring = true).assertExists()
    }

    @Test
    fun `the device board filters by level and difficulty`() {
        show(
            boards = emptySet(),
            entries = listOf(
                LeaderboardEntry("ALPHA", 150, 1, modeId = GameMode.STANDARD.id, mapId = Maps.PERIMETER.id),
                LeaderboardEntry("BRAVO", 90, 1, modeId = GameMode.HACK_AI.id, mapId = Maps.TRIDENT.id),
                LeaderboardEntry("OLDRUN", 300, 1)
            )
        )
        compose.onNodeWithText("ALPHA").assertExists()
        compose.onNodeWithText("OLDRUN").assertExists()
        pick("board-level", "L6 \u00B7 MIRAI")
        compose.onNodeWithText("BRAVO").assertExists()
        compose.onNodeWithText("ALPHA").assertDoesNotExist()
        compose.onNodeWithText("OLDRUN").assertDoesNotExist()
        pick("board-difficulty", GameMode.STANDARD.runName)
        compose.onNodeWithText("No runs recorded on MIRAI", substring = true).assertExists()
    }

    // ----------------------------------------------------------- per level

    @Test
    fun `there is a board slot for every level in every difficulty`() {
        val keys = com.cyopstd.game.ads.LevelLeaderboards.ids.keys
        assertEquals(Maps.all.size * GameMode.entries.size, keys.size)
        for (map in Maps.all) for (mode in GameMode.entries) assertTrue("${map.id}|${mode.id}" in keys)
        assertEquals("MIRAI \u00B7 HACK:AI", com.cyopstd.game.ads.LevelLeaderboards.boardName(Maps.TRIDENT, GameMode.HACK_AI))
        assertEquals("DUCK-USB \u00B7 KERNEL MODE", com.cyopstd.game.ads.LevelLeaderboards.boardName(Maps.DUCK_USB, GameMode.KERNEL_MODE))
    }

    @Test
    fun `only filled-in per-level ids become boards, and only with a games project`() {
        val raw = mapOf("trident|hack_ai" to " CgkIxyz ", "spiral|standard" to "")
        assertTrue(com.cyopstd.game.ads.LevelLeaderboards.boards(gamesConfigured = false, raw = raw).isEmpty())
        assertEquals(
            mapOf(BoardKey(GameMode.HACK_AI, Maps.TRIDENT.id) to "CgkIxyz"),
            com.cyopstd.game.ads.LevelLeaderboards.boards(gamesConfigured = true, raw = raw)
        )
    }

    @Test
    fun `a run is posted to its difficulty overall and to its level`() {
        val entry = LeaderboardEntry("NEO", 120, 5, modeId = GameMode.HACK_AI.id, mapId = Maps.HELIX.id)
        assertEquals(listOf(BoardKey(GameMode.HACK_AI), BoardKey(GameMode.HACK_AI, Maps.HELIX.id)), entry.boardKeys)
        assertEquals(listOf(BoardKey(GameMode.STANDARD)), LeaderboardEntry("NEO", 1, 1).boardKeys)
    }
}
