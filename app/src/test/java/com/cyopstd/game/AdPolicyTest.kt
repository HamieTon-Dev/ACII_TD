package com.cyopstd.game

import com.cyopstd.game.ads.AdPolicy
import com.cyopstd.game.ads.NoAdGateway
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When an interstitial is allowed.
 *
 * The ad itself needs AdMob and cannot be exercised here, but every rule
 * around it can — and these are the rules a player gets angry about if they
 * are wrong.
 */
class AdPolicyTest {

    private class Clock(var seconds: Long = 1_000_000L)

    private fun policy(clock: Clock, cooldown: Long = 180L) =
        AdPolicy(cooldownSeconds = cooldown, now = { clock.seconds })

    @Test
    fun `paying to remove ads removes ads`() {
        val clock = Clock()
        val policy = policy(clock)
        // No cooldown, an ad loaded and waiting, a lost run: still no.
        assertFalse(policy.shouldShowOnRunLost(adsRemoved = true, ready = true))

        // And it stays no however long passes.
        clock.seconds += 10_000
        assertFalse(policy.shouldShowOnRunLost(adsRemoved = true, ready = true))
    }

    @Test
    fun `an ad shows on a lost run when one is loaded`() {
        val policy = policy(Clock())
        assertTrue(policy.shouldShowOnRunLost(adsRemoved = false, ready = true))
    }

    @Test
    fun `nothing is shown when no ad is loaded`() {
        // The game must never stall waiting for an ad that does not exist.
        val policy = policy(Clock())
        assertFalse(policy.shouldShowOnRunLost(adsRemoved = false, ready = false))
    }

    @Test
    fun `losing repeatedly does not mean an ad every time`() {
        // The player losing over and over on an early wave is the one most
        // likely to uninstall. The cooldown exists for them.
        val clock = Clock()
        val policy = policy(clock, cooldown = 180L)

        assertTrue(policy.shouldShowOnRunLost(adsRemoved = false, ready = true))
        policy.recordShown()

        clock.seconds += 30
        assertFalse("30s later", policy.shouldShowOnRunLost(adsRemoved = false, ready = true))
        assertEquals(150L, policy.secondsUntilEligible())

        clock.seconds += 149
        assertFalse("one second short", policy.shouldShowOnRunLost(adsRemoved = false, ready = true))

        clock.seconds += 1
        assertTrue("cooldown elapsed", policy.shouldShowOnRunLost(adsRemoved = false, ready = true))
        assertEquals(0L, policy.secondsUntilEligible())
    }

    @Test
    fun `the first run of a session is eligible`() {
        // Nothing has been shown yet, so the cooldown must not be counted from
        // an unset timestamp and lock the player out.
        val policy = policy(Clock(seconds = 0))
        assertTrue(policy.shouldShowOnRunLost(adsRemoved = false, ready = true))
        assertEquals(0L, policy.secondsUntilEligible())
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
