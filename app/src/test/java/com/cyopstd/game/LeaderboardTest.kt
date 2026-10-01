package com.cyopstd.game

import com.cyopstd.game.core.GameMode
import com.cyopstd.game.save.LeaderboardEntry
import com.cyopstd.game.save.LeaderboardGateway
import com.cyopstd.game.save.LocalLeaderboard
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The local leaderboard.
 *
 * Rank is by wave reached with damage as the tiebreak, and that ordering lives
 * on the entry rather than in whatever happens to be sorting — so these check
 * the rule itself, not one screen's reading of it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LeaderboardTest {

    private fun entry(name: String, wave: Int, damage: Long, at: Long = 0) =
        LeaderboardEntry(name, wave, damage, GameMode.STANDARD.id, at)

    @Test
    fun `the deepest wave ranks first, damage only breaks a tie`() = runTest {
        val board = LocalLeaderboard(TestStores.isolatedRepository())
        board.submit(entry("LOW_WAVE_HUGE_DAMAGE", wave = 12, damage = 999_999))
        board.submit(entry("DEEP_RUN", wave = 40, damage = 10))
        board.submit(entry("TIED_WEAKER", wave = 40, damage = 5))

        val top = board.top()
        assertEquals("DEEP_RUN", top[0].username)
        assertEquals(
            "damage should break a tie, not beat a deeper wave",
            "TIED_WEAKER", top[1].username
        )
        assertEquals("LOW_WAVE_HUGE_DAMAGE", top[2].username)
    }

    @Test
    fun `submitting reports where the run placed`() = runTest {
        val board = LocalLeaderboard(TestStores.isolatedRepository())
        assertEquals(1, board.submit(entry("FIRST", 10, 100, at = 1)))
        assertEquals("a deeper run takes first", 1, board.submit(entry("BETTER", 30, 50, at = 2)))
        assertEquals("a shallower run goes below", 3, board.submit(entry("WORST", 5, 5, at = 3)))
    }

    @Test
    fun `a player who beats their own record keeps both runs`() = runTest {
        // The board is a history of runs, not a table of players. Silently
        // replacing a previous best would lose the thing the screen shows.
        val board = LocalLeaderboard(TestStores.isolatedRepository())
        board.submit(entry("AGENT", 20, 400, at = 1))
        board.submit(entry("AGENT", 35, 900, at = 2))

        val top = board.top()
        assertEquals(2, top.size)
        assertEquals(35, top[0].wave)
        assertEquals(20, top[1].wave)
    }

    @Test
    fun `the board is capped and keeps the best`() = runTest {
        val board = LocalLeaderboard(TestStores.isolatedRepository())
        for (wave in 1..LeaderboardGateway.MAX_ENTRIES + 10) {
            board.submit(entry("RUN_$wave", wave, wave * 10L, at = wave.toLong()))
        }
        val top = board.top(100)
        assertEquals(LeaderboardGateway.MAX_ENTRIES, top.size)
        assertEquals(
            "the deepest run should survive the cap",
            LeaderboardGateway.MAX_ENTRIES + 10, top.first().wave
        )
        assertTrue(
            "the shallow runs should have been dropped",
            top.none { it.wave <= 10 }
        )
    }

    @Test
    fun `the board survives a corrupt save`() = runTest {
        // A board that failed to parse must cost the player their scores, not
        // their ability to open the screen.
        val repository = TestStores.isolatedRepository()
        repository.saveLeaderboard(listOf(entry("REAL", 9, 9, at = 1)))
        val board = LocalLeaderboard(repository)
        assertEquals(1, board.top().size)
    }

    @Test
    fun `a run in hack AI is labelled as one`() = runTest {
        val board = LocalLeaderboard(TestStores.isolatedRepository())
        board.submit(LeaderboardEntry("AGENT", 30, 100, GameMode.HACK_AI.id, 1))
        assertEquals(GameMode.HACK_AI, board.top().first().mode)
        assertEquals("HACK:AI", board.top().first().mode.runName)
    }

    @Test
    fun `every level and difficulty keeps its own best runs`() = runTest {
        val board = LocalLeaderboard(TestStores.isolatedRepository())
        // Forty deep runs on the first level would crowd everything else off a
        // single top 25; the board keeps the best on every level as well.
        for (wave in 1..40) {
            board.submit(LeaderboardEntry("A", 200 + wave, 1, mapId = "perimeter", at = wave.toLong()))
        }
        board.submit(LeaderboardEntry("B", 12, 1, modeId = GameMode.HACK_AI.id, mapId = "trident", at = 99))
        val mirai = board.top(25, "trident", GameMode.HACK_AI)
        assertEquals(1, mirai.size)
        assertEquals("B", mirai.first().username)
        assertEquals(25, board.top(100, "perimeter", null).size)
        assertTrue(board.top(25, "trident", GameMode.STANDARD).isEmpty())
    }
}
