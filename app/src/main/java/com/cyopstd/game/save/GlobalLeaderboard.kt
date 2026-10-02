package com.cyopstd.game.save

import android.app.Activity
import android.util.Log
import com.cyopstd.game.core.GameMode
import com.google.android.gms.games.PlayGames
import com.google.android.gms.games.leaderboard.LeaderboardVariant
import com.google.android.gms.tasks.Task
import java.lang.ref.WeakReference
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * The global board, on Play Games Services leaderboards.
 *
 * Sits beside the local board rather than replacing it. The local board is
 * every run on this device and works offline; the global board is each
 * player's best, worldwide, and needs the player signed into Play Games —
 * the same sign-in the cloud save uses, so a linked player is already there.
 *
 * One Play Console leaderboard per [GameMode] across all levels, because a
 * HACK:AI wave and a standard wave are not the same achievement, and (owner,
 * 2026-10-01) one per level in each mode as well: a [BoardKey].
 */
interface GlobalLeaderboardGateway {
    /** True when this build has at least one leaderboard id configured. */
    val configured: Boolean

    /** True when [key] has a board of its own. */
    fun hasBoard(key: BoardKey): Boolean

    /** True when [mode] has an all-levels board. */
    fun hasBoard(mode: GameMode): Boolean = hasBoard(BoardKey(mode))

    /**
     * Posts a finished run. False on any failure — not signed in, offline, no
     * board for the mode — and never throws: the local record is already
     * written, and a global board that is down must cost the player nothing.
     */
    suspend fun submit(entry: LeaderboardEntry): Boolean

    /** The top of [key]'s board, or null when it could not be read. */
    suspend fun top(key: BoardKey, limit: Int = LeaderboardGateway.MAX_ENTRIES): List<LeaderboardEntry>?

    /** The top of [mode]'s all-levels board. */
    suspend fun top(mode: GameMode, limit: Int = LeaderboardGateway.MAX_ENTRIES): List<LeaderboardEntry>? =
        top(BoardKey(mode), limit)

    /** Opens Google's own leaderboard screen for [key]. */
    suspend fun openNative(key: BoardKey): Boolean
}

/** The gateway for a build with no leaderboard ids: nothing is global. */
class NoGlobalLeaderboard : GlobalLeaderboardGateway {
    override val configured = false
    override fun hasBoard(key: BoardKey) = false
    override suspend fun submit(entry: LeaderboardEntry) = false
    override suspend fun top(key: BoardKey, limit: Int): List<LeaderboardEntry>? = null
    override suspend fun openNative(key: BoardKey) = false
}

/**
 * What rides along with a score.
 *
 * Play ranks on one number, so the score is the **wave reached** — the same
 * thing the local board ranks on. The run's damage and the player's in-game
 * callsign go in the score tag, which Play stores with the score and returns
 * with it. That is how the global list can show callsigns rather than Play
 * profile names, and damage beside the wave.
 *
 * Play allows 64 URL-safe characters in a tag. A callsign is at most 16 of
 * `A-Z 0-9 - _` (see [PlayerIdentity.sanitize]), all URL-safe, and the
 * separator is a dot, which a callsign cannot contain — so the tag is always
 * legal and always splits back unambiguously.
 */
object GlobalScoreTag {
    private const val SEPARATOR = '.'
    const val MAX_LENGTH = 64

    fun encode(username: String, damage: Long): String {
        val name = PlayerIdentity.sanitize(username)
        return "$name$SEPARATOR${damage.coerceAtLeast(0)}".take(MAX_LENGTH)
    }

    /** The callsign and damage in [tag], each null when absent or unreadable. */
    fun decode(tag: String?): Pair<String?, Long?> {
        if (tag.isNullOrBlank()) return null to null
        val cut = tag.lastIndexOf(SEPARATOR)
        if (cut < 0) return null to null
        val name = PlayerIdentity.sanitize(tag.substring(0, cut)).ifBlank { null }
        val damage = tag.substring(cut + 1).toLongOrNull()?.takeIf { it >= 0 }
        return name to damage
    }
}

/**
 * Play Games leaderboards.
 *
 * Holds the Activity weakly, like every other Play gateway here: the Games
 * clients are Activity-scoped, and a strong reference would leak a rotated
 * window. The SDK itself is initialised by [PlayGamesCloudSave.attach], which
 * shares the games project id; this class only makes calls once it has been
 * attached after that.
 */
class PlayGamesLeaderboard(
    modeBoardIds: Map<GameMode, String>,
    /** Per level and mode (owner, 2026-10-01). */
    levelBoardIds: Map<BoardKey, String> = emptyMap()
) : GlobalLeaderboardGateway {

    private val boardIds: Map<BoardKey, String> =
        modeBoardIds.mapKeys { BoardKey(it.key) } + levelBoardIds

    private var activityRef = WeakReference<Activity?>(null)

    fun attach(activity: Activity) {
        activityRef = WeakReference(activity)
    }

    override val configured: Boolean get() = boardIds.isNotEmpty()

    override fun hasBoard(key: BoardKey): Boolean = boardIds[key] != null

    /** Posted to its difficulty's all-levels board and to its level's board, each that exists. */
    override suspend fun submit(entry: LeaderboardEntry): Boolean {
        var any = false
        for (key in entry.boardKeys) {
            val id = boardIds[key] ?: continue
            if (submitTo(id, entry)) any = true
        }
        return any
    }

    private suspend fun submitTo(id: String, entry: LeaderboardEntry): Boolean {
        val activity = activityRef.get() ?: return false
        return runCatching {
            PlayGames.getLeaderboardsClient(activity)
                .submitScoreImmediate(
                    id,
                    entry.wave.toLong(),
                    GlobalScoreTag.encode(entry.username, entry.damage)
                )
                .await() != null
        }.getOrElse {
            Log.i(TAG, "Global score not submitted", it)
            false
        }
    }

    override suspend fun top(key: BoardKey, limit: Int): List<LeaderboardEntry>? {
        val mode = key.mode
        val id = boardIds[key] ?: return null
        val activity = activityRef.get() ?: return null
        return runCatching {
            val data = PlayGames.getLeaderboardsClient(activity)
                .loadTopScores(
                    id,
                    LeaderboardVariant.TIME_SPAN_ALL_TIME,
                    LeaderboardVariant.COLLECTION_PUBLIC,
                    limit.coerceIn(1, MAX_PAGE)
                )
                .await() ?: return null
            val scores = data.get() ?: return null
            try {
                scores.scores.map { score ->
                    val (callsign, damage) = GlobalScoreTag.decode(score.scoreTag)
                    LeaderboardEntry(
                        username = callsign ?: score.scoreHolderDisplayName.orEmpty(),
                        wave = score.rawScore.coerceIn(0, Int.MAX_VALUE.toLong()).toInt(),
                        damage = damage ?: 0,
                        modeId = mode.id,
                        at = score.timestampMillis / 1000,
                        mapId = key.mapId
                    )
                }
            } finally {
                // A score buffer is a cursor over shared memory and must be
                // released, or it leaks for the life of the process.
                scores.release()
            }
        }.getOrElse {
            Log.i(TAG, "Global board not read", it)
            null
        }
    }

    override suspend fun openNative(key: BoardKey): Boolean {
        val id = boardIds[key] ?: return false
        val activity = activityRef.get() ?: return false
        return runCatching {
            val intent = PlayGames.getLeaderboardsClient(activity)
                .getLeaderboardIntent(id)
                .await() ?: return false
            activity.startActivityForResult(intent, REQUEST_CODE)
            true
        }.getOrElse {
            Log.i(TAG, "Google's leaderboard screen would not open", it)
            false
        }
    }

    /** As in [PlayGamesCloudSave]: resumed exactly once, null on failure. */
    private suspend fun <T> Task<T>.await(): T? = suspendCancellableCoroutine { cont ->
        var resumed = false
        addOnCompleteListener { task ->
            if (resumed) return@addOnCompleteListener
            resumed = true
            cont.resume(if (task.isSuccessful) task.result else null)
        }
    }

    private companion object {
        const val TAG = "CyOpsBoard"
        const val REQUEST_CODE = 9_004
        /** Play's own page limit for loadTopScores. */
        const val MAX_PAGE = 25
    }
}
