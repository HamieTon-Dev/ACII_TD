package com.cyopstd.game.save

import com.cyopstd.game.store.CosmeticChoice
import kotlinx.serialization.Serializable

/**
 * A player's progress, in a form that can travel between devices.
 *
 * Two things are deliberately **not** in here.
 *
 * *Entitlements* — what the player has bought — are absent because Google Play
 * is the only honest source of truth for them. If ownership travelled inside a
 * save file, then anyone who could write a save file could grant themselves
 * every paid item, and a stale cloud save could silently revoke a purchase made
 * an hour ago. Play already carries purchases to a new device; the game asks it
 * on every launch, which is both safer and more accurate than copying them.
 *
 * *Settings* are absent because they are properties of a device, not of a
 * player: music volume, haptics and battery saver should not follow someone
 * from a phone to a tablet.
 */
@Serializable
data class CloudSave(
    /** Bumped only for a change old builds cannot read. */
    val schema: Int = SCHEMA,
    /** When this snapshot was taken, in wall-clock millis. */
    val savedAtMillis: Long = 0L,
    /** Free-text device label, shown when two saves disagree. */
    val device: String = "",
    val stats: PlayerStats = PlayerStats(),
    val progress: PlayerProgress = PlayerProgress(),
    val identity: PlayerIdentity = PlayerIdentity(),
    val cosmetics: CosmeticChoice = CosmeticChoice(),
    val run: SavedRun? = null,
    val leaderboard: List<LeaderboardEntry> = emptyList()
) {
    /**
     * How much play this save represents.
     *
     * Every term is a lifetime counter that only ever grows, so the sum only
     * ever grows too — which makes it comparable between two devices in a way
     * a wall clock is not. This is what decides which save is "further along",
     * and it exists because the obvious alternative is wrong twice over:
     *
     * - A save is stamped with the time it was *exported*, so a freshly
     *   installed device always looks newer than the account it is about to
     *   read from. Ordering by timestamp alone let an empty phone overwrite a
     *   player's € and their run in progress the moment they linked it.
     * - Device clocks disagree. A phone set to the wrong year would otherwise
     *   win every merge it ever took part in.
     *
     * It is also handed to Play Games as the snapshot's progress value, so
     * Google's own automatic conflict resolution agrees with ours instead of
     * quietly picking the other one.
     */
    val progressValue: Long
        get() = stats.highestWave.toLong() +
            stats.totalAttacksBlocked +
            stats.totalBossesDefeated +
            stats.totalCryptoEarned +
            stats.totalGamesPlayed +
            stats.totalAgentsDeployed +
            stats.totalAgentUpgrades +
            progress.lifetimeBudgetEarned

    companion object {
        const val SCHEMA = 1

        /** Play Games allows one named snapshot per save slot. This is ours. */
        const val SNAPSHOT_NAME = "cyops_td_progress"
    }
}

/**
 * Deciding what a player keeps when two devices disagree.
 *
 * This is the part of cloud save that can actually hurt someone, so the rules
 * are written down rather than left to whichever device syncs last:
 *
 * 1. **Lifetime counters take the maximum.** Waves reached, attacks blocked,
 *    bosses defeated and € *earned* only ever grow. A max can never take
 *    something away, and can never invent anything either — the number already
 *    happened on one of the two devices.
 *
 * 2. **Unlocks are unioned.** Reaching wave 40 on a tablet must not un-unlock
 *    an agent on the phone.
 *
 * 3. **The wallet moves as one piece.** Unspent € and the firmware level it was
 *    spent on are taken *together*, from whichever save is further along.
 *    Taking the max of each independently would mint currency: spend 500 € on
 *    firmware here, merge against a save from before the purchase, and a naive
 *    max would return both the firmware and the money. Spending is a ledger,
 *    and half a ledger is worse than an old one.
 *
 * 4. **The run in progress belongs to that same save.** Two half-finished runs
 *    cannot be combined into one; the other is the one the player walked away
 *    from.
 *
 * The result is the same whichever device performs the merge, which is what
 * stops two phones from ping-ponging different answers at each other.
 */
object CloudSaveMerge {

    fun merge(local: CloudSave, remote: CloudSave): CloudSave {
        // Which save is "further along" — see CloudSave.progressValue for why
        // this is not simply the newer timestamp. The clock is only a tiebreak,
        // for the case where two saves represent the same amount of play and
        // differ in how that play was spent; and local wins a total tie,
        // because a device merging against an identical save has no reason to
        // adopt the other one's wallet.
        val base = when {
            remote.progressValue > local.progressValue -> remote
            local.progressValue > remote.progressValue -> local
            remote.savedAtMillis > local.savedAtMillis -> remote
            else -> local
        }
        val other = if (base === local) remote else local

        return base.copy(
            schema = CloudSave.SCHEMA,
            savedAtMillis = maxOf(local.savedAtMillis, remote.savedAtMillis),
            stats = mergeStats(base.stats, other.stats),
            progress = base.progress.copy(
                // Rule 2: unions.
                unlockedAgents = base.progress.unlockedAgents + other.progress.unlockedAgents,
                tutorialCompleted =
                    base.progress.tutorialCompleted || other.progress.tutorialCompleted,
                // Rule 1: a lifetime total.
                lifetimeBudgetEarned = maxOf(
                    base.progress.lifetimeBudgetEarned,
                    other.progress.lifetimeBudgetEarned
                )
                // budget and firmwareLevel are left exactly as `base` has them.
                // See rule 3 — they are one ledger and are never mixed.
            ),
            identity = mergeIdentity(base.identity, other.identity),
            // Rule 4.
            run = base.run,
            leaderboard = mergeBoards(base.leaderboard, other.leaderboard)
        )
    }

    private fun mergeStats(base: PlayerStats, other: PlayerStats) = PlayerStats(
        highestWave = maxOf(base.highestWave, other.highestWave),
        totalAttacksBlocked = maxOf(base.totalAttacksBlocked, other.totalAttacksBlocked),
        totalBossesDefeated = maxOf(base.totalBossesDefeated, other.totalBossesDefeated),
        totalCryptoEarned = maxOf(base.totalCryptoEarned, other.totalCryptoEarned),
        totalGamesPlayed = maxOf(base.totalGamesPlayed, other.totalGamesPlayed),
        totalServerDamageTaken = maxOf(base.totalServerDamageTaken, other.totalServerDamageTaken),
        totalAgentsDeployed = maxOf(base.totalAgentsDeployed, other.totalAgentsDeployed),
        totalAgentUpgrades = maxOf(base.totalAgentUpgrades, other.totalAgentUpgrades),
        // Per-agent deployment counts are lifetime totals too, so the same rule
        // applies key by key rather than to the map as a whole.
        deploymentsByAgent = (base.deploymentsByAgent.keys + other.deploymentsByAgent.keys)
            .associateWith { key ->
                maxOf(
                    base.deploymentsByAgent[key] ?: 0,
                    other.deploymentsByAgent[key] ?: 0
                )
            }
    )

    private fun mergeIdentity(base: PlayerIdentity, other: PlayerIdentity) = PlayerIdentity(
        // A claimed callsign is never silently replaced by an empty one.
        username = base.username.ifBlank { other.username },
        highestWave = maxOf(base.highestWave, other.highestWave),
        bestDamage = maxOf(base.bestDamage, other.bestDamage)
    )

    /**
     * Both devices' run histories, best first.
     *
     * Identical runs recorded on both sides collapse into one — the same run
     * synced twice is one run, and showing it twice would misreport a board
     * that is meant to be a personal history.
     */
    private fun mergeBoards(
        base: List<LeaderboardEntry>,
        other: List<LeaderboardEntry>
    ): List<LeaderboardEntry> = (base + other)
        .distinctBy { listOf(it.username, it.wave, it.damage, it.at, it.modeId) }
        .sortedWith(LeaderboardEntry.ranking)
        .take(LeaderboardGateway.MAX_ENTRIES)
}
