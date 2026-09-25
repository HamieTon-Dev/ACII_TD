package com.cyopstd.game

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.cyopstd.game.ads.AdGateway
import com.cyopstd.game.core.Balance
import com.cyopstd.game.engine.RunPhase
import com.cyopstd.game.save.GameRepository
import com.cyopstd.game.state.GameViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
 * The rewarded-revive state machine, every branch of it.
 *
 * `ReviveTest` covers what a revive *does* — the integrity restored, the wave
 * replayed, the run recorded exactly once. This covers the states around it,
 * which is where a rewarded-ad integration actually goes wrong: an ad that
 * never loads, one that fails to show, one the player closes early, one that
 * reports its reward twice, and a process killed while the ad's own Activity
 * is in front of the game.
 *
 * Every one of those has the same required outcome — **the player is never
 * stuck and never silently charged** — and a different way of being got wrong.
 * The rule underneath all of it: a revive is granted from the reward callback
 * and from nothing else.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RewardedReviveStateMachineTest {

    /**
     * A gateway that can be put into any state the real one can reach.
     *
     * Scripted rather than random: each test names the branch it is exercising
     * in the constructor, so a failure says which branch broke.
     */
    private class ScriptedAds(
        /** False when nothing is loaded — the offer must not appear. */
        var rewardedLoaded: Boolean = true,
        /** What `show` does. */
        var outcome: Outcome = Outcome.REWARD_THEN_DISMISS
    ) : AdGateway {
        enum class Outcome {
            /** The happy path: reward callback, then dismissal. */
            REWARD_THEN_DISMISS,

            /** Player closed it early. Dismissal, no reward. */
            DISMISS_WITHOUT_REWARD,

            /** The SDK could not present it at all. */
            FAIL_TO_SHOW,

            /**
             * The SDK delivers a reward and then *also* reports a failure,
             * which it is entitled to do. One revive, not two.
             */
            REWARD_THEN_DOUBLE_CALLBACK
        }

        var showCalls = 0
        var preloadRewardedCalls = 0

        override val isRewardedReady: Boolean get() = rewardedLoaded

        override fun showRewarded(onResult: (earned: Boolean) -> Unit) {
            showCalls++
            when (outcome) {
                Outcome.REWARD_THEN_DISMISS -> onResult(true)
                Outcome.DISMISS_WITHOUT_REWARD -> onResult(false)
                Outcome.FAIL_TO_SHOW -> onResult(false)
                Outcome.REWARD_THEN_DOUBLE_CALLBACK -> {
                    onResult(true)
                    // The real gateway latches this behind a `finished` flag.
                    // If that ever comes out, this second call reaches the view
                    // model and this test is what notices.
                    onResult(true)
                }
            }
        }

        override fun preloadRewarded() {
            preloadRewardedCalls++
        }
    }

    private lateinit var repository: GameRepository

    /**
     * Reads the saved run without blocking the main looper.
     *
     * `runBlocking { repository.savedRun.first() }` deadlocks here: Robolectric
     * runs the test on the main looper, and DataStore needs that looper to
     * finish the read. Launching on IO and pumping is the pattern
     * `SavedRunMapTest` established after that cost ten minutes of a hung
     * build and a `jstack` to diagnose.
     */
    private fun readSavedRun(): com.cyopstd.game.save.SavedRun? {
        var result: com.cyopstd.game.save.SavedRun? = null
        var done = false
        CoroutineScope(Dispatchers.IO).launch {
            result = repository.savedRun.first()
            done = true
        }
        val deadline = System.currentTimeMillis() + 15_000
        while (!done && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        shadowOf(Looper.getMainLooper()).idle()
        return result
    }

    /** Pumps for a fixed stretch, for a write that may not have been issued yet. */
    private fun settle(millis: Long = 1_500) {
        val deadline = System.currentTimeMillis() + millis
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
    }

    private fun freshViewModel(ads: AdGateway): GameViewModel {
        val application = ApplicationProvider.getApplicationContext<Application>()
        repository = TestStores.isolatedRepository()
        val viewModel = GameViewModel(application, repository, adsOverride = ads)
        shadowOf(Looper.getMainLooper()).idle()
        return viewModel
    }

    private fun GameViewModel.loseARun() {
        startNewGame()
        shadowOf(Looper.getMainLooper()).idle()
        var frames = 0
        while (engine.phase != RunPhase.GAME_OVER && frames < 200_000) {
            if (engine.canStartNextWave()) engine.startNextWave()
            onFrame(1f / 30f)
            frames++
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("the run never actually ended", RunPhase.GAME_OVER, engine.phase)
    }

    // ------------------------------------------------- when the offer appears

    @Test
    fun `no offer when no ad is loaded`() {
        // The whole reason the ad is preloaded the moment a match starts. An
        // offer the game cannot honour is worse than no offer: the player
        // taps it at the worst possible moment and nothing happens.
        val ads = ScriptedAds(rewardedLoaded = false)
        val viewModel = freshViewModel(ads)
        viewModel.loseARun()

        assertFalse("a revive was offered with no ad loaded", viewModel.canReviveNow)
    }

    @Test
    fun `the offer appears on a lost run when an ad is ready`() {
        val viewModel = freshViewModel(ScriptedAds())
        viewModel.loseARun()

        assertTrue("no revive offered on a lost run", viewModel.canReviveNow)
        assertNotNull("no summary to offer it on", viewModel.gameOverSummary)
    }

    @Test
    fun `the offer never appears mid-run`() {
        val viewModel = freshViewModel(ScriptedAds())
        viewModel.startNewGame()
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse("a revive was offered during a live run", viewModel.canReviveNow)
    }

    // ------------------------------------------------- what each outcome does

    @Test
    fun `a completed ad revives the run exactly once`() {
        val ads = ScriptedAds(outcome = ScriptedAds.Outcome.REWARD_THEN_DISMISS)
        val viewModel = freshViewModel(ads)
        viewModel.loseARun()
        val wave = viewModel.engine.currentWave

        viewModel.watchAdToRevive()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("the ad was not shown", 1, ads.showCalls)
        assertEquals("one revive should have been spent", 1, viewModel.revivesUsed)
        assertFalse("the run is still over", viewModel.engine.phase == RunPhase.GAME_OVER)
        assertEquals(
            "the same wave should be replayed, not the next one",
            (wave - 1).coerceAtLeast(0),
            viewModel.engine.currentWave
        )
        assertEquals(
            "the core should come back at half integrity",
            Math.ceil(
                viewModel.engine.serverMaxHp * Balance.REVIVE_INTEGRITY_FRACTION.toDouble()
            ).toInt(),
            viewModel.engine.serverHp
        )
    }

    @Test
    fun `closing the ad early grants nothing and leaves the offer standing`() {
        // The most important branch. An interstitial calls back on dismissal,
        // so a revive hung off "the ad closed" is a revive for watching two
        // seconds of it. Nothing here may be spent.
        val ads = ScriptedAds(outcome = ScriptedAds.Outcome.DISMISS_WITHOUT_REWARD)
        val viewModel = freshViewModel(ads)
        viewModel.loseARun()

        viewModel.watchAdToRevive()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(0, viewModel.revivesUsed)
        assertEquals("the run should still be over", RunPhase.GAME_OVER, viewModel.engine.phase)
        assertTrue("the player should be able to try again", viewModel.canReviveNow)
        assertFalse("the game is stuck waiting on the ad", viewModel.showingReviveAd)
    }

    @Test
    fun `an ad that fails to show does not soft-lock the game`() {
        val ads = ScriptedAds(outcome = ScriptedAds.Outcome.FAIL_TO_SHOW)
        val viewModel = freshViewModel(ads)
        viewModel.loseARun()

        viewModel.watchAdToRevive()
        shadowOf(Looper.getMainLooper()).idle()

        assertFalse("the game is stuck on a spinner", viewModel.showingReviveAd)
        assertEquals(0, viewModel.revivesUsed)

        // ...and the ordinary end-of-run path is still available, which is the
        // actual definition of "not soft-locked".
        viewModel.finishRun()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(RunPhase.GAME_OVER, viewModel.engine.phase)
    }

    @Test
    fun `a doubled reward callback still grants one revive`() {
        val ads = ScriptedAds(outcome = ScriptedAds.Outcome.REWARD_THEN_DOUBLE_CALLBACK)
        val viewModel = freshViewModel(ads)
        viewModel.loseARun()

        viewModel.watchAdToRevive()
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("a doubled callback bought two revives", 1, viewModel.revivesUsed)
    }

    // ------------------------------------------------------- one per run

    @Test
    fun `a second revive is refused on the same run`() {
        val ads = ScriptedAds()
        val viewModel = freshViewModel(ads)
        viewModel.loseARun()

        viewModel.watchAdToRevive()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, viewModel.revivesUsed)

        // Lose it again. The allowance is spent.
        var frames = 0
        while (viewModel.engine.phase != RunPhase.GAME_OVER && frames < 200_000) {
            if (viewModel.engine.canStartNextWave()) viewModel.engine.startNextWave()
            viewModel.onFrame(1f / 30f)
            frames++
        }
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(Balance.REVIVES_PER_RUN, viewModel.revivesAllowed)
        assertFalse("a second revive was offered on the same run", viewModel.canReviveNow)

        viewModel.watchAdToRevive()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("a second ad was shown after the allowance ran out", 1, ads.showCalls)
        assertEquals(1, viewModel.revivesUsed)
    }

    @Test
    fun `a genuinely new run gets its allowance back`() {
        val viewModel = freshViewModel(ScriptedAds())
        viewModel.loseARun()
        viewModel.watchAdToRevive()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, viewModel.revivesUsed)

        viewModel.startNewGame()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("a new run should start with a full allowance", 0, viewModel.revivesUsed)
    }

    @Test
    fun `a revive spent stays spent across a process restart`() {
        // The counter used to live only in the ViewModel, which dies with the
        // process. A rewarded ad puts *its own Activity* in front of the game,
        // which is exactly when Android is most willing to kill what is
        // behind it -- so "one per run" quietly became "one per process".
        val ads = ScriptedAds()
        val viewModel = freshViewModel(ads)
        viewModel.loseARun()
        viewModel.watchAdToRevive()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, viewModel.revivesUsed)

        // The revive persists the run itself; wait for that write to land.
        // Read off the IO dispatcher and pumped, never `runBlocking`: this
        // test runs *on* the main looper, and blocking it while DataStore
        // needs it to complete is a ten-minute hang, not a failure.
        settle()
        val saved = readSavedRun()
        assertNotNull("the revive was never written to the save", saved)
        assertEquals(1, saved!!.revivesUsed)

        // A fresh ViewModel over the same store is what a restarted process
        // sees.
        val application = ApplicationProvider.getApplicationContext<Application>()
        val restarted = GameViewModel(application, repository, adsOverride = ScriptedAds())
        shadowOf(Looper.getMainLooper()).idle()
        restarted.continueGame()
        // continueGame reads the store on its own scope, so idling the looper
        // starts that read rather than waiting for it.
        settle()

        assertEquals(
            "the spent revive was forgotten when the process restarted",
            1,
            restarted.revivesUsed
        )
    }

    // ------------------------------------------------------ nothing is stuck

    @Test
    fun `the ad overlay is never left up, whatever the outcome`() {
        for (outcome in ScriptedAds.Outcome.entries) {
            val viewModel = freshViewModel(ScriptedAds(outcome = outcome))
            viewModel.loseARun()
            viewModel.watchAdToRevive()
            shadowOf(Looper.getMainLooper()).idle()
            assertFalse(
                "the game was left waiting on the ad after $outcome",
                viewModel.showingReviveAd
            )
        }
    }
}
