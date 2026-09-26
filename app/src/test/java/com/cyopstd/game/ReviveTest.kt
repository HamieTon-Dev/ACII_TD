package com.cyopstd.game

import android.app.Application
import android.os.Looper
import com.cyopstd.game.ads.AdGateway
import com.cyopstd.game.core.Balance
import com.cyopstd.game.engine.GameEngine
import com.cyopstd.game.engine.RunPhase
import com.cyopstd.game.state.GameViewModel
import androidx.test.core.app.ApplicationProvider
import com.cyopstd.game.save.GameRepository
import com.cyopstd.game.store.Sku
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Watching an ad to continue a lost run.
 *
 * Two separate things are being checked here and they fail in different ways.
 *
 * The **engine** half is about what the player gets back: the right integrity,
 * an empty board rather than the swarm that just killed them, and the same
 * wave rather than a skipped one.
 *
 * The **view model** half is about what the game writes down. Before this
 * feature the run was recorded, submitted to the leaderboard and cleared from
 * the save the instant the core fell. A revive after that would have
 * double-counted the entire run — two leaderboard entries for one run, and its
 * kills and crypto landing twice in lifetime stats. That reordering is the real
 * work in this feature, and it is the thing most likely to break silently, so
 * it is asserted directly rather than inferred.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ReviveTest {

    // ------------------------------------------------------------ the engine

    /** A run with no agents on the board, played until the core falls. */
    private fun lostRun(): GameEngine {
        val engine = GameEngine()
        engine.startNewRun()
        engine.playUntilLost()
        return engine
    }

    private fun GameEngine.playUntilLost() {
        var frames = 0
        while (phase != RunPhase.GAME_OVER && frames < 200_000) {
            if (canStartNextWave()) startNextWave()
            update(1f / 30f, 4f)
            frames++
        }
        assertEquals("the run never actually ended", RunPhase.GAME_OVER, phase)
    }

    @Test
    fun `a revive restores half the mode's integrity, rounded up`() {
        val engine = lostRun()
        assertEquals(RunPhase.GAME_OVER, engine.phase)
        assertEquals(0, engine.serverHp)

        assertTrue("a lost run should be revivable", engine.reviveRun())
        assertEquals(
            "half of the mode's maximum, not half of what was left",
            Math.ceil(engine.serverMaxHp * Balance.REVIVE_INTEGRITY_FRACTION.toDouble()).toInt(),
            engine.serverHp
        )
    }

    @Test
    fun `a revive clears the board`() {
        val engine = lostRun()
        engine.reviveRun()
        assertEquals(
            "reviving into the swarm that just killed the player is not a " +
                "revive -- they would lose again inside a second",
            0,
            engine.enemies.activeCount()
        )
    }

    @Test
    fun `a revive replays the wave it died on rather than skipping it`() {
        val engine = lostRun()
        val died = engine.currentWave
        engine.reviveRun()
        assertEquals(RunPhase.PREPARING, engine.phase)

        engine.startNextWave()
        assertEquals(
            "the wave the player died on should be the one that comes back",
            died,
            engine.currentWave
        )
    }

    @Test
    fun `a revive keeps the agents, their levels and the crypto`() {
        val engine = lostRun()
        val placements = engine.snapshotPlacements()
        val crypto = engine.crypto
        engine.reviveRun()

        assertEquals("the run continues, it does not restart", crypto, engine.crypto)
        assertEquals(placements.size, engine.snapshotPlacements().size)
    }

    @Test
    fun `a run that is not over cannot be revived`() {
        val engine = GameEngine()
        engine.startNewRun()
        engine.startNextWave()
        assertFalse(
            "a revive outside game over would be a free board wipe mid-wave",
            engine.reviveRun()
        )
    }

    // -------------------------------------------------------- the view model

    /** A gateway that always has a rewarded ad, and grants what it is told to. */
    private class FakeAds(private val grants: Boolean) : AdGateway {
        var rewardedShown = 0
        var interstitialsShown = 0
        override val isReady = true
        override val isRewardedReady = true
        override fun showInterstitial(onFinished: () -> Unit) {
            interstitialsShown++
            onFinished()
        }
        override fun showRewarded(onResult: (Boolean) -> Unit) {
            rewardedShown++
            onResult(grants)
        }
        override fun preloadRewarded() = Unit
    }

    /** The store the view model under test is writing into. */
    private lateinit var repository: GameRepository

    private fun freshViewModel(ads: AdGateway? = null): GameViewModel {
        val application = ApplicationProvider.getApplicationContext<Application>()
        repository = TestStores.isolatedRepository()
        val viewModel = GameViewModel(application, repository, adsOverride = ads)
        shadowOf(Looper.getMainLooper()).idle()
        return viewModel
    }

    /**
     * How many runs have reached the board.
     *
     * Read from the store rather than from `leaderboardEntries`, and polled
     * rather than read once: the view model writes on its own scope and
     * DataStore commits on an IO dispatcher, so idling the main looper starts
     * the write but does not wait for it. Polling to a deadline is honest about
     * that -- a count that never arrives fails on the assertion, not on a
     * timeout with no explanation.
     */
    private fun entriesOnBoard(): Int = runBlocking {
        var latest = 0
        withTimeoutOrNull(5_000) {
            while (true) {
                shadowOf(Looper.getMainLooper()).idle()
                latest = repository.leaderboard().size
                if (latest > 0) break
                delay(10)
            }
        }
        latest
    }

    private fun GameViewModel.loseARun() {
        startNewGame()
        shadowOf(Looper.getMainLooper()).idle()
        playUntilLost()
    }

    /** Plays the live match out with an empty board until the core falls. */
    private fun GameViewModel.playUntilLost() {
        var frames = 0
        while (engine.phase != RunPhase.GAME_OVER && frames < 200_000) {
            if (engine.canStartNextWave()) engine.startNextWave()
            onFrame(1f / 30f)
            frames++
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("the run never actually ended", RunPhase.GAME_OVER, engine.phase)
    }

    @Test
    fun `nothing is written down while a revive is still on offer`() {
        val viewModel = freshViewModel(FakeAds(grants = true))
        viewModel.loseARun()

        assertEquals(RunPhase.GAME_OVER, viewModel.engine.phase)
        assertNotNull("the player still needs to see how the run went", viewModel.gameOverSummary)
        assertTrue("the offer should be standing", viewModel.canReviveNow)
        assertEquals(
            "a run with a revive on offer is not finished, so nothing should " +
                "have reached the leaderboard yet",
            0,
            runBlocking { repository.leaderboard().size }
        )
    }

    @Test
    fun `a revived run produces exactly one leaderboard entry`() {
        val viewModel = freshViewModel(FakeAds(grants = true))
        viewModel.loseARun()

        viewModel.watchAdToRevive()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("the run should be live again", viewModel.matchActive)

        // Lose it a second time, for real this time.
        viewModel.playUntilLost()

        viewModel.abandonMatch()

        assertEquals(
            "one run, one entry -- recording at the moment of death and again " +
                "at the end would post two",
            1,
            entriesOnBoard()
        )
    }

    @Test
    fun `an ad the player skipped costs them nothing and grants nothing`() {
        val ads = FakeAds(grants = false)
        val viewModel = freshViewModel(ads)
        viewModel.loseARun()

        viewModel.watchAdToRevive()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1, ads.rewardedShown)
        assertEquals("the entitlement was spent on nothing", 0, viewModel.revivesUsed)
        assertEquals(RunPhase.GAME_OVER, viewModel.engine.phase)
        assertTrue("the offer should still stand", viewModel.canReviveNow)
    }

    @Test
    fun `one revive per run, and the second is not offered`() {
        val viewModel = freshViewModel(FakeAds(grants = true))
        viewModel.loseARun()

        viewModel.watchAdToRevive()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, viewModel.revivesUsed)

        viewModel.playUntilLost()

        assertEquals(Balance.REVIVES_PER_RUN, viewModel.revivesAllowed)
        assertFalse(
            "the run's one revive is spent, so no second offer",
            viewModel.canReviveNow
        )
    }

    @Test
    fun `a revived run is not also charged the loss interstitial`() {
        val ads = FakeAds(grants = true)
        val viewModel = freshViewModel(ads)
        viewModel.loseARun()

        viewModel.watchAdToRevive()
        shadowOf(Looper.getMainLooper()).idle()

        viewModel.playUntilLost()
        viewModel.abandonMatch()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("a rewarded ad already spent this run's ad budget", 0, ads.interstitialsShown)
        assertEquals(1, ads.rewardedShown)
    }

    @Test
    fun `a lost run with no revive taken shows the loss interstitial`() {
        val ads = FakeAds(grants = true)
        val viewModel = freshViewModel(ads)
        viewModel.loseARun()

        // Declining the offer is walking away from a *lost* run, not quitting
        // a live one, so the run carries its one loss ad.
        viewModel.finishRun()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(1, ads.interstitialsShown)
    }

    @Test
    fun `owning REMOVE ADS removes the loss interstitial`() {
        val ads = FakeAds(grants = true)
        val application = ApplicationProvider.getApplicationContext<Application>()
        repository = TestStores.isolatedRepository()
        runBlocking { repository.applyPurchase(Sku.NO_ADS, "order-no-ads-loss") }
        val viewModel = GameViewModel(application, repository, adsOverride = ads)
        shadowOf(Looper.getMainLooper()).idle()
        viewModel.loseARun()
        viewModel.finishRun()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(0, ads.interstitialsShown)
    }

    @Test
    fun `backgrounding the app on the offer still records the run`() {
        val viewModel = freshViewModel(FakeAds(grants = true))
        viewModel.loseARun()
        assertTrue(viewModel.canReviveNow)

        viewModel.onAppPaused()

        assertEquals(
            "walking away from the offer must not be a way to erase a bad run",
            1,
            entriesOnBoard()
        )
    }

    // ------------------------------------------------------- the revive pack

    @Test
    fun `the pack grants three revives and shows no ad for any of them`() {
        val ads = FakeAds(grants = true)
        val application = ApplicationProvider.getApplicationContext<Application>()
        repository = TestStores.isolatedRepository()
        runBlocking { repository.applyPurchase(Sku.REVIVE_PACK, "order-revive-pack") }

        val viewModel = GameViewModel(application, repository, adsOverride = ads)
        shadowOf(Looper.getMainLooper()).idle()
        viewModel.loseARun()

        assertEquals(3, viewModel.revivesAllowed)
        assertTrue("the pack pays for the revive", viewModel.reviveIsFree)

        repeat(3) { attempt ->
            assertTrue("revive ${attempt + 1} should be on offer", viewModel.canReviveNow)
            viewModel.watchAdToRevive()
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue("the run should be live again", viewModel.matchActive)
            viewModel.playUntilLost()
        }

        assertEquals(3, viewModel.revivesUsed)
        assertEquals(
            "the pack sells the ad away; showing one anyway is the refund " +
                "request this product exists to avoid",
            0,
            ads.rewardedShown
        )
        assertFalse("three is three, not four", viewModel.canReviveNow)
    }

    @Test
    fun `REMOVE ADS alone does not pay for the revive`() {
        val ads = FakeAds(grants = true)
        val application = ApplicationProvider.getApplicationContext<Application>()
        repository = TestStores.isolatedRepository()
        runBlocking { repository.applyPurchase(Sku.NO_ADS, "order-no-ads") }

        val viewModel = GameViewModel(application, repository, adsOverride = ads)
        shadowOf(Looper.getMainLooper()).idle()
        viewModel.loseARun()

        assertFalse(
            "REMOVE ADS covers ads between runs, not the one the player asked for",
            viewModel.reviveIsFree
        )
        assertEquals(Balance.REVIVES_PER_RUN, viewModel.revivesAllowed)

        viewModel.watchAdToRevive()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("the revive still costs an ad", 1, ads.rewardedShown)
    }

    @Test
    fun `a build with no rewarded ad never offers a revive`() {
        val viewModel = freshViewModel()   // the shipping NoAdGateway
        viewModel.loseARun()
        assertFalse(
            "a control that cannot act must not be offered",
            viewModel.canReviveNow
        )
        assertEquals(
            "and the run is recorded straight away, as it always was",
            1,
            entriesOnBoard()
        )
    }
}
