package com.cyopstd.game

import com.cyopstd.game.ads.AdPolicy
import com.cyopstd.game.ads.NoAdGateway
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When the lost-run interstitial is allowed.
 *
 * The owner's rules (2026-09-26): only on a loss, only when the run lasted at
 * least three minutes of real play, never for someone who bought REMOVE ADS,
 * and no cooldown. When in the flow it is asked is the view model's job and is
 * covered in ReviveTest; this covers whether.
 */
class AdPolicyTest {

    private val policy = AdPolicy()

    @Test
    fun `paying to remove ads removes ads`() {
        assertFalse(policy.shouldShowOnRunLost(adsRemoved = true, ready = true, runSeconds = 3_600f))
    }

    @Test
    fun `a run shorter than three minutes gets no ad`() {
        assertFalse(policy.shouldShowOnRunLost(adsRemoved = false, ready = true, runSeconds = 0f))
        assertFalse(
            "one second short",
            policy.shouldShowOnRunLost(adsRemoved = false, ready = true, runSeconds = 179f)
        )
    }

    @Test
    fun `a run of three minutes or more gets one`() {
        assertTrue(policy.shouldShowOnRunLost(adsRemoved = false, ready = true, runSeconds = 180f))
        assertTrue(policy.shouldShowOnRunLost(adsRemoved = false, ready = true, runSeconds = 5_000f))
    }

    @Test
    fun `there is no cooldown between qualifying losses`() {
        // Two long losses in a row each get their ad; the rule is per loss.
        repeat(2) {
            assertTrue(policy.shouldShowOnRunLost(adsRemoved = false, ready = true, runSeconds = 200f))
        }
    }

    @Test
    fun `nothing loaded means nothing shown`() {
        assertFalse(policy.shouldShowOnRunLost(adsRemoved = false, ready = false, runSeconds = 600f))
    }

    @Test
    fun `the threshold is three minutes`() {
        assertEquals(180f, AdPolicy.MIN_RUN_SECONDS)
    }

    @Test
    fun `the shipped gateway never blocks the game`() {
        // Until AdMob is configured this is what runs. It must always finish,
        // because the game waits on that callback.
        val gateway = NoAdGateway()
        assertFalse(gateway.isReady)
        var finished = 0
        gateway.showInterstitial { finished++ }
        assertEquals("the continuation must run exactly once", 1, finished)
        gateway.preload()
    }
}
