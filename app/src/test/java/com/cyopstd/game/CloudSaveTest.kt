package com.cyopstd.game

import com.cyopstd.game.save.CloudSave
import com.cyopstd.game.save.CloudSaveGateway
import com.cyopstd.game.save.CloudSaveMerge
import com.cyopstd.game.save.CloudSaveStatus
import com.cyopstd.game.save.CloudSaveSync
import com.cyopstd.game.save.CloudSyncResult
import com.cyopstd.game.save.GameRepository
import com.cyopstd.game.save.LeaderboardEntry
import com.cyopstd.game.save.PlayerIdentity
import com.cyopstd.game.save.PlayerProgress
import com.cyopstd.game.save.PlayerStats
import com.cyopstd.game.save.SavedRun
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Carrying a player's progress between devices.
 *
 * The transport is not what is tested here — Play Games moves the bytes and
 * cannot be exercised in this container. What *can* be tested, and is the part
 * that can actually hurt someone, is the decision made when two devices
 * disagree. Every rule in [CloudSaveMerge] has a test, and the two that matter
 * most are stated as properties rather than examples: a merge must never lose
 * progress, and a merge must never *create* currency.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CloudSaveTest {

    // ------------------------------------------------------------- fixtures

    private fun save(
        at: Long,
        device: String = "phone",
        wave: Int = 0,
        budget: Long = 0,
        firmware: Int = 0,
        lifetimeEarned: Long = 0,
        agents: Set<String> = emptySet(),
        username: String = "",
        run: SavedRun? = null,
        board: List<LeaderboardEntry> = emptyList(),
        blocked: Long = 0
    ) = CloudSave(
        savedAtMillis = at,
        device = device,
        stats = PlayerStats(highestWave = wave, totalAttacksBlocked = blocked),
        progress = PlayerProgress(
            unlockedAgents = agents,
            budget = budget,
            firmwareLevel = firmware,
            lifetimeBudgetEarned = lifetimeEarned
        ),
        identity = PlayerIdentity(username = username, highestWave = wave),
        run = run,
        leaderboard = board
    )

    // ------------------------------------------------------ the merge rules

    @Test
    fun `lifetime counters take the best of both devices`() {
        val phone = save(at = 100, wave = 63, blocked = 8_000)
        val tablet = save(at = 200, wave = 41, blocked = 12_000)

        val merged = CloudSaveMerge.merge(phone, tablet)

        // Neither device's achievement is thrown away because the other one
        // synced more recently.
        assertEquals(63, merged.stats.highestWave)
        assertEquals(12_000L, merged.stats.totalAttacksBlocked)
    }

    @Test
    fun `unlocks are never taken away`() {
        val phone = save(at = 100, agents = setOf("FIREWALL", "ANALYST"))
        val tablet = save(at = 200, agents = setOf("FIREWALL", "TARPIT"))

        val merged = CloudSaveMerge.merge(phone, tablet)

        assertEquals(
            setOf("FIREWALL", "ANALYST", "TARPIT"),
            merged.progress.unlockedAgents
        )
    }

    @Test
    fun `merging cannot mint currency`() {
        // The scenario this rule exists for: the player had 500 EUR, spent it
        // all on firmware, and then the older save from before the purchase
        // turns up. Taking the max of each field independently would hand back
        // the money *and* keep the firmware.
        val beforeSpending = save(at = 100, budget = 500, firmware = 0, lifetimeEarned = 500)
        val afterSpending = save(at = 200, budget = 0, firmware = 10, lifetimeEarned = 500)

        val merged = CloudSaveMerge.merge(beforeSpending, afterSpending)

        assertEquals("the spent EUR must not come back", 0L, merged.progress.budget)
        assertEquals("the firmware that was bought stays", 10, merged.progress.firmwareLevel)
        // And the lifetime total, which is a record rather than a balance, is
        // untouched by the spending.
        assertEquals(500L, merged.progress.lifetimeBudgetEarned)
    }

    @Test
    fun `the wallet is never split across two saves`() {
        // A property, not an example: whichever pair of saves goes in, the
        // budget and firmware that come out must both have come from the same
        // save. Anything else is half a ledger.
        val pairs = listOf(
            save(at = 1, budget = 900, firmware = 0) to save(at = 2, budget = 0, firmware = 18),
            save(at = 2, budget = 40, firmware = 3) to save(at = 1, budget = 700, firmware = 1),
            save(at = 5, budget = 0, firmware = 0) to save(at = 5, budget = 250, firmware = 5)
        )
        for ((a, b) in pairs) {
            val merged = CloudSaveMerge.merge(a, b)
            val fromA = merged.progress.budget == a.progress.budget &&
                merged.progress.firmwareLevel == a.progress.firmwareLevel
            val fromB = merged.progress.budget == b.progress.budget &&
                merged.progress.firmwareLevel == b.progress.firmwareLevel
            assertTrue(
                "wallet was mixed: ${merged.progress.budget} EUR with firmware " +
                    "${merged.progress.firmwareLevel}, from ${a.progress} and ${b.progress}",
                fromA || fromB
            )
        }
    }

    @Test
    fun `the newer save wins the wallet and the run in progress`() {
        val older = save(at = 100, budget = 900, firmware = 2, run = SavedRun(wave = 5, serverHp = 90))
        val newer = save(at = 200, budget = 10, firmware = 9, run = SavedRun(wave = 31, serverHp = 40))

        val merged = CloudSaveMerge.merge(older, newer)

        assertEquals(10L, merged.progress.budget)
        assertEquals(9, merged.progress.firmwareLevel)
        assertEquals(31, merged.run?.wave)
    }

    @Test
    fun `a device with a wrong clock cannot win a merge`() {
        // Phones disagree about the time, and one set to the wrong year would
        // otherwise win every merge it ever took part in. Play, not the clock,
        // decides which save is further along.
        val played = save(at = 1_000, wave = 63, budget = 800, firmware = 9, blocked = 50_000)
        val barelyPlayedButFromTheFuture =
            save(at = 99_000_000_000, wave = 1, budget = 0, firmware = 0, blocked = 5)

        val merged = CloudSaveMerge.merge(played, barelyPlayedButFromTheFuture)

        assertEquals(800L, merged.progress.budget)
        assertEquals(9, merged.progress.firmwareLevel)
    }

    @Test
    fun `an untouched save never overwrites a played one`() {
        val fresh = save(at = 5_000)
        val played = save(at = 1_000, wave = 40, budget = 300, firmware = 6, blocked = 20_000)

        // Whichever way round, and regardless of which is newer.
        for (merged in listOf(
            CloudSaveMerge.merge(fresh, played),
            CloudSaveMerge.merge(played, fresh)
        )) {
            assertEquals(300L, merged.progress.budget)
            assertEquals(6, merged.progress.firmwareLevel)
            assertEquals(40, merged.stats.highestWave)
        }
    }

    @Test
    fun `a claimed callsign is never replaced by an empty one`() {
        val named = save(at = 100, username = "NULLSEC")
        val anonymous = save(at = 500, username = "")

        assertEquals("NULLSEC", CloudSaveMerge.merge(named, anonymous).identity.username)
        assertEquals("NULLSEC", CloudSaveMerge.merge(anonymous, named).identity.username)
    }

    @Test
    fun `both devices' run histories survive, without duplicates`() {
        val shared = LeaderboardEntry("NULLSEC", wave = 30, damage = 900, at = 10)
        val phoneOnly = LeaderboardEntry("NULLSEC", wave = 44, damage = 1_400, at = 20)
        val tabletOnly = LeaderboardEntry("NULLSEC", wave = 12, damage = 300, at = 30)

        val merged = CloudSaveMerge.merge(
            save(at = 100, board = listOf(shared, phoneOnly)),
            save(at = 200, board = listOf(shared, tabletOnly))
        )

        assertEquals(3, merged.leaderboard.size)
        assertEquals(44, merged.leaderboard.first().wave)
    }

    @Test
    fun `the merge does not depend on which device performs it`() {
        // Two phones that sync against each other must not ping-pong different
        // answers, so the result has to be the same from either side.
        val a = save(
            at = 100, device = "phone", wave = 63, budget = 200, firmware = 4,
            agents = setOf("FIREWALL"), username = "NULLSEC", blocked = 9_000
        )
        val b = save(
            at = 200, device = "tablet", wave = 20, budget = 40, firmware = 11,
            agents = setOf("TARPIT"), blocked = 400
        )

        val ab = CloudSaveMerge.merge(a, b)
        val ba = CloudSaveMerge.merge(b, a)

        assertEquals(ab.stats, ba.stats)
        assertEquals(ab.progress, ba.progress)
        assertEquals(ab.identity, ba.identity)
    }

    @Test
    fun `merging is stable once it has happened`() {
        val a = save(at = 100, wave = 63, budget = 200, firmware = 4, agents = setOf("FIREWALL"))
        val b = save(at = 200, wave = 20, budget = 40, firmware = 11, agents = setOf("TARPIT"))

        val once = CloudSaveMerge.merge(a, b)
        val twice = CloudSaveMerge.merge(once, b)
        val thrice = CloudSaveMerge.merge(twice, a)

        // Re-syncing an already merged save must be a no-op, or every launch
        // would shuffle the player's numbers.
        assertEquals(once.progress, twice.progress)
        assertEquals(once.progress, thrice.progress)
        assertEquals(once.stats, thrice.stats)
    }

    @Test
    fun `a save survives a round trip through JSON`() {
        // This is what actually crosses the wire, and a field that silently
        // fails to serialize is a field the player loses on a new phone.
        val original = save(
            at = 1_700_000_000_000, device = "Pixel", wave = 63, budget = 740,
            firmware = 14, lifetimeEarned = 3_000, agents = setOf("FIREWALL", "TARPIT"),
            username = "NULLSEC", run = SavedRun(wave = 12, serverHp = 80),
            board = listOf(LeaderboardEntry("NULLSEC", 63, 184_220, at = 5))
        )
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        val restored = json.decodeFromString(
            CloudSave.serializer(),
            json.encodeToString(CloudSave.serializer(), original)
        )

        assertEquals(original, restored)
    }

    @Test
    fun `entitlements are not part of a cloud save`() {
        // Deliberate: Play owns what the player has bought. If ownership
        // travelled in a save file, a save file could grant paid content.
        val descriptor = CloudSave.serializer().descriptor
        val fields = (0 until descriptor.elementsCount).map { descriptor.getElementName(it) }
        assertFalse(
            "a cloud save must not carry entitlements: $fields",
            fields.any { name -> name.contains("entitle", ignoreCase = true) }
        )
        assertFalse(
            "settings belong to a device, not to a player: $fields",
            fields.any { name -> name.contains("setting", ignoreCase = true) }
        )
    }

    // ------------------------------------------------------ the sync itself

    /** An in-memory stand-in for Play Games. */
    private class FakeCloud(
        var stored: CloudSave? = null,
        var writable: Boolean = true,
        var acceptsSignIn: Boolean = true,
        initial: CloudSaveStatus = CloudSaveStatus.NOT_LINKED
    ) : CloudSaveGateway {
        override val status = MutableStateFlow(initial)
        override val accountName = MutableStateFlow<String?>(null)
        var writes = 0

        override suspend fun signIn(): Boolean {
            if (!acceptsSignIn) return false
            status.value = CloudSaveStatus.LINKED
            accountName.value = "TEST PLAYER"
            return true
        }

        override suspend fun load(): CloudSave? = stored

        override suspend fun save(save: CloudSave): Boolean {
            if (!writable) return false
            writes++
            stored = save
            return true
        }

        override fun release() = Unit
    }

    private fun repository() = GameRepository(TestStores.isolatedStore())

    @Test
    fun `linking a new device adopts the progress already in the account`() = runTest {
        val repository = repository()
        // This device has never been played on; the account has a real history.
        val cloud = FakeCloud(
            stored = save(at = 9_000, device = "old phone", wave = 63, agents = setOf("TARPIT"))
        )
        val sync = CloudSaveSync(repository, cloud, "new phone")

        val result = sync.link()

        assertEquals(CloudSyncResult.MERGED, result)
        assertEquals(63, repository.stats.first().highestWave)
        assertTrue(repository.progress.first().unlockedAgents.contains("TARPIT"))
    }

    @Test
    fun `a freshly installed device never overwrites the account's wallet`() = runTest {
        // The case that matters most: new phone, game installed, nothing played
        // on it yet, account holds 900 EUR and a run in progress.
        val repository = repository()
        val cloud = FakeCloud(
            stored = save(
                at = 1_000, device = "old phone", wave = 63, budget = 900,
                firmware = 12, lifetimeEarned = 3_000, blocked = 40_000,
                run = SavedRun(wave = 58, serverHp = 70)
            )
        )
        val sync = CloudSaveSync(repository, cloud, "new phone")

        sync.link()

        assertEquals("the account's EUR must survive", 900L, repository.progress.first().budget)
        assertEquals(12, repository.progress.first().firmwareLevel)
        assertEquals(58, repository.savedRun.first()?.wave)
    }

    @Test
    fun `declining to link changes nothing`() = runTest {
        val repository = repository()
        repository.updateHighestWave(12)
        val cloud = FakeCloud(acceptsSignIn = false)
        val sync = CloudSaveSync(repository, cloud, "phone")

        assertEquals(CloudSyncResult.DECLINED, sync.link())
        assertEquals(0, cloud.writes)
        assertEquals(12, repository.stats.first().highestWave)
    }

    @Test
    fun `a failed upload never costs the player anything`() = runTest {
        val repository = repository()
        repository.updateHighestWave(40)
        val cloud = FakeCloud(writable = false, initial = CloudSaveStatus.LINKED)
        val sync = CloudSaveSync(repository, cloud, "phone")

        assertEquals(CloudSyncResult.FAILED, sync.sync())
        // The local save is the source of truth until the cloud confirms.
        assertEquals(40, repository.stats.first().highestWave)
    }

    @Test
    fun `a quiet sync never prompts an unlinked player`() = runTest {
        val cloud = FakeCloud(initial = CloudSaveStatus.NOT_LINKED)
        val sync = CloudSaveSync(repository(), cloud, "phone")

        assertEquals(CloudSyncResult.DECLINED, sync.syncQuietly())
        assertEquals(0, cloud.writes)
    }

    @Test
    fun `an unconfigured build reports cloud save as unavailable`() = runTest {
        val cloud = FakeCloud(initial = CloudSaveStatus.UNAVAILABLE)
        val sync = CloudSaveSync(repository(), cloud, "phone")

        assertEquals(CloudSyncResult.UNAVAILABLE, sync.link())
        assertEquals(CloudSyncResult.UNAVAILABLE, sync.sync())
    }

    @Test
    fun `two devices played apart converge on one save`() = runTest {
        val cloud = FakeCloud(initial = CloudSaveStatus.LINKED)

        // Phone: deep run, unlocked TARPIT, spent its money.
        val phone = repository()
        phone.updateHighestWave(63)
        phone.awardBudget(300)
        val phoneSync = CloudSaveSync(phone, cloud, "phone")
        assertEquals(CloudSyncResult.UPLOADED, phoneSync.sync())

        // Tablet: shallower, but blocked more attacks and has its own money.
        val tablet = repository()
        tablet.updateHighestWave(20)
        tablet.awardBudget(50)
        val tabletSync = CloudSaveSync(tablet, cloud, "tablet")
        assertEquals(CloudSyncResult.MERGED, tabletSync.sync())

        // The deepest wave survives on both, and syncing again settles.
        assertEquals(63, tablet.stats.first().highestWave)
        phoneSync.sync()
        assertEquals(63, phone.stats.first().highestWave)

        val cloudSave = cloud.stored
        assertNotNull(cloudSave)
        assertEquals(63, cloudSave!!.stats.highestWave)
    }
}
