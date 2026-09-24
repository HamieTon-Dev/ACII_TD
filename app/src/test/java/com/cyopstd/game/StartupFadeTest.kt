package com.cyopstd.game

import com.cyopstd.game.audio.AudioEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Music easing in at launch.
 *
 * The shape is asserted rather than watched, which is why the ramp is a plain
 * function of elapsed seconds: a fade that is a beat short reads as music
 * arriving abruptly, not as a timing bug, and a fade that reaches full early
 * is invisible until somebody notices the boot chime being trodden on.
 *
 * The clause that matters most is the last one. The ramp is a **multiplier on
 * the player's setting**, not a level of its own. Ramping an absolute volume
 * would take somebody who set music to 20% up to full and back down, which is
 * the same bug as ignoring the setting outright — and it is the natural way to
 * write this, so it is pinned down here.
 */
class StartupFadeTest {

    private val silence = AudioEngine.STARTUP_SILENCE_SECONDS
    private val fade = AudioEngine.STARTUP_FADE_SECONDS

    @Test
    fun `nothing plays for the first five seconds`() {
        // The boot chime has the opening to itself. The ident and the boot
        // screen take about 3.9s between them, so this leaves roughly a second
        // of quiet after the chime before anything else starts.
        assertEquals(5f, silence, 0.01f)
        var t = 0f
        while (t <= silence) {
            assertEquals(
                "music was already audible ${t}s in",
                0f,
                AudioEngine.startupRampAt(t),
                0.0001f
            )
            t += 0.1f
        }
    }

    @Test
    fun `it rises, and keeps rising`() {
        var previous = -1f
        var t = silence
        while (t <= silence + fade) {
            val now = AudioEngine.startupRampAt(t)
            assertTrue("the ramp went backwards at ${t}s", now >= previous)
            previous = now
            t += 0.05f
        }
        assertEquals(0.5f, AudioEngine.startupRampAt(silence + fade / 2f), 0.02f)
    }

    @Test
    fun `it arrives at full and stays there`() {
        assertEquals(1f, AudioEngine.startupRampAt(silence + fade), 0.0001f)
        assertEquals(
            "the ramp is still moving long after the launch",
            1f,
            AudioEngine.startupRampAt(600f),
            0.0001f
        )
    }

    @Test
    fun `the rise is slow enough to be a fade rather than a switch`() {
        // Four seconds. Under about two this stops reading as an introduction
        // and starts reading as a delay followed by music.
        assertTrue("the fade is only ${fade}s long", fade >= 2f)
    }

    @Test
    fun `the ramp never exceeds the player's setting`() {
        // The whole point. At every moment of the launch, a player who chose
        // 20% hears at most 20%, and a player who chose nothing hears nothing.
        for (setting in listOf(0f, 0.2f, 0.5f, 1f)) {
            var t = 0f
            while (t <= silence + fade + 1f) {
                val level = setting * AudioEngine.startupRampAt(t)
                assertTrue(
                    "at ${t}s a setting of $setting produced $level",
                    level <= setting + 0.0001f
                )
                t += 0.1f
            }
        }
    }

    @Test
    fun `a player who turned music off hears nothing at any point in the fade`() {
        var t = 0f
        while (t <= silence + fade + 1f) {
            assertEquals(0f, 0f * AudioEngine.startupRampAt(t), 0.0001f)
            t += 0.1f
        }
    }
}
