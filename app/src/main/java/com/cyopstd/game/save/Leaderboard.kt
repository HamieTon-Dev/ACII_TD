package com.cyopstd.game.save

import com.cyopstd.game.core.GameMode
import kotlinx.serialization.Serializable

/**
 * One finished run, as it appears on the board.
 *
 * Rank is by **wave reached**, with damage as the tiebreak — that ordering is
 * the product decision, so it lives here next to the data rather than being
 * re-implemented by whatever happens to be sorting.
 */
@Serializable
data class LeaderboardEntry(
    val username: String,
    val wave: Int,
    val damage: Long,
    val modeId: String = GameMode.STANDARD.id,
    /** Epoch seconds. Only used to break a total tie and to show recency. */
    val at: Long = 0
) {
    val mode: GameMode get() = GameMode.fromIdSafe(modeId)

    companion object {
        /** Best first: deepest wave, then most damage, then most recent. */
        val ranking: Comparator<LeaderboardEntry> = compareByDescending<LeaderboardEntry> { it.wave }
            .thenByDescending { it.damage }
            .thenByDescending { it.at }
    }
}

/**
 * The local record of finished runs.
 *
 * Every run on this device, offline. The worldwide board is a separate thing
 * beside it — Play Games, each player's best, see [GlobalLeaderboardGateway] —
 * and a finished run is posted to both.
 */
interface LeaderboardGateway {
    suspend fun top(limit: Int = MAX_ENTRIES): List<LeaderboardEntry>

    /** Records a finished run. Returns its rank, or -1 if it did not place. */
    suspend fun submit(entry: LeaderboardEntry): Int

    companion object {
        const val MAX_ENTRIES = 25
    }
}

/**
 * Keeps the best [LeaderboardGateway.MAX_ENTRIES] runs in the save.
 *
 * One entry per run, not per player: a player who beats their own record keeps
 * both, because a board that silently replaced your previous bests would lose
 * the history the screen exists to show.
 */
class LocalLeaderboard(private val repository: GameRepository) : LeaderboardGateway {

    override suspend fun top(limit: Int): List<LeaderboardEntry> =
        repository.leaderboard().sortedWith(LeaderboardEntry.ranking).take(limit)

    override suspend fun submit(entry: LeaderboardEntry): Int {
        val kept = (repository.leaderboard() + entry)
            .sortedWith(LeaderboardEntry.ranking)
            .take(LeaderboardGateway.MAX_ENTRIES)
        repository.saveLeaderboard(kept)
        val index = kept.indexOfFirst {
            it.at == entry.at && it.wave == entry.wave && it.damage == entry.damage
        }
        return if (index < 0) -1 else index + 1
    }
}
