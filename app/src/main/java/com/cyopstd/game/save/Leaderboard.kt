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
    val at: Long = 0,
    /**
     * The level, by `GameMap.id`; null for runs recorded before levels were
     * (owner, 2026-10-01), which then only show under ALL LEVELS.
     */
    val mapId: String? = null
) {
    val mode: GameMode get() = GameMode.fromIdSafe(modeId)

    /** Whether this run belongs on the board for [mapId] (null = every level) and [mode] (null = every mode). */
    fun matches(mapId: String?, mode: GameMode?): Boolean =
        (mapId == null || this.mapId == mapId) && (mode == null || this.mode == mode)

    /** The worldwide boards this run is posted to: its difficulty overall, and its level in that difficulty. */
    val boardKeys: List<BoardKey>
        get() = listOfNotNull(BoardKey(mode), mapId?.let { BoardKey(mode, it) })

    companion object {
        /** Best first: deepest wave, then most damage, then most recent. */
        val ranking: Comparator<LeaderboardEntry> = compareByDescending<LeaderboardEntry> { it.wave }
            .thenByDescending { it.damage }
            .thenByDescending { it.at }
    }
}

/**
 * One worldwide board: a difficulty, on one level or (null) all of them
 * (owner, 2026-10-01: boards "all per level and difficulty").
 */
data class BoardKey(val mode: GameMode, val mapId: String? = null) {
    /** "mapId|modeId", how a per-level board's id is looked up. */
    val key: String get() = "${mapId ?: "all"}|${mode.id}"
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

    /** The best [limit] runs on [mapId] (null = every level) in [mode] (null = every mode). */
    suspend fun top(limit: Int, mapId: String?, mode: GameMode?): List<LeaderboardEntry> =
        repository.leaderboard().filter { it.matches(mapId, mode) }
            .sortedWith(LeaderboardEntry.ranking).take(limit)

    override suspend fun submit(entry: LeaderboardEntry): Int {
        // The best runs overall, and the best on every level in every mode,
        // so a filtered board is never empty because the others crowded it out.
        val all = (repository.leaderboard() + entry).sortedWith(LeaderboardEntry.ranking)
        val keep = HashSet<LeaderboardEntry>()
        keep += all.take(LeaderboardGateway.MAX_ENTRIES)
        all.groupBy { it.mapId to it.modeId }.values
            .forEach { keep += it.take(LeaderboardGateway.MAX_ENTRIES) }
        val kept = all.filter { it in keep }
        repository.saveLeaderboard(kept)
        val index = kept.indexOfFirst {
            it.at == entry.at && it.wave == entry.wave && it.damage == entry.damage
        }
        return if (index < 0) -1 else index + 1
    }
}
