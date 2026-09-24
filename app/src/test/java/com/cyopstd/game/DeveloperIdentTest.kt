package com.cyopstd.game

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.cyopstd.game.ui.splash.BANNER_COLUMNS
import com.cyopstd.game.ui.splash.DEVELOPER_BANNER
import com.cyopstd.game.ui.splash.DEVELOPER_NAME
import com.cyopstd.game.ui.splash.DeveloperSplashScreen
import com.cyopstd.game.ui.splash.FADE_SECONDS
import com.cyopstd.game.ui.splash.HOLD_SECONDS
import com.cyopstd.game.ui.splash.IDENT_SECONDS
import com.cyopstd.game.ui.splash.developerIdentAlpha
import com.cyopstd.game.ui.theme.CyOpsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The studio ident that runs before the boot screen.
 *
 * Two different things are checked. The **banner** is a block of ASCII whose
 * every line has to be the same width — the theme is monospace, and a
 * ragged-width block reads as a rendering fault rather than as a logo, which is
 * the sort of thing nobody notices until it is on a store listing.
 *
 * The **beat** is fade up, hold, fade down, and the hold is the part that
 * matters: an ident that starts fading the instant it arrives never gets read,
 * and the symptom is a flicker rather than anything that looks like a timing
 * bug. That is why the curve is a plain function of elapsed seconds instead of
 * an animation spec — it can be asserted at the moments that matter.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1600dp-h760dp-land-mdpi")
class DeveloperIdentTest {

    @get:Rule
    val compose = createComposeRule()

    // ----------------------------------------------------------- the banner

    @Test
    fun `it spells the studio's name`() {
        // Block ASCII cannot be read back, so the name is carried alongside it
        // as the banner's accessibility label -- which a screen reader needs
        // anyway, since the raw text is a mouthful of pipes and underscores.
        // Asserting on the label is the only check here that would actually
        // notice the banner being regenerated from the wrong string.
        assertEquals("HamieTon.dev", DEVELOPER_NAME)
        assertTrue("the banner is empty", DEVELOPER_BANNER.isNotBlank())
        assertEquals("an eight-row block font", 8, DEVELOPER_BANNER.lines().size)
    }

    @Test
    fun `a screen reader is told the name, not the pipes and underscores`() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CyOpsTheme {
                Box(Modifier.fillMaxSize()) { DeveloperSplashScreen(onFinished = {}) }
            }
        }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithContentDescription(DEVELOPER_NAME).assertExists()
    }

    @Test
    fun `every line is the same width`() {
        val lines = DEVELOPER_BANNER.lines()
        val widths = lines.map { it.length }.distinct()
        assertEquals(
            "a monospace block with ragged lines reads as a rendering fault, " +
                "not a logo; widths were $widths",
            1,
            widths.size
        )
        assertEquals(
            "the declared column count is what the type is scaled against, so " +
                "it has to be the real one",
            BANNER_COLUMNS,
            widths.single()
        )
    }

    @Test
    fun `the banner carries no tab or control characters`() {
        // A tab in a monospace block is a column shift that renders
        // differently everywhere and looks like corruption.
        val bad = DEVELOPER_BANNER.filter { it != '\n' && it.code < 32 }
        assertTrue("control characters in the banner: ${bad.map { it.code }}", bad.isEmpty())
    }

    // ------------------------------------------------------------- the beat

    @Test
    fun `it fades in`() {
        assertEquals(0f, developerIdentAlpha(0f), 0.001f)
        assertEquals(0.5f, developerIdentAlpha(FADE_SECONDS / 2f), 0.01f)
        assertEquals(1f, developerIdentAlpha(FADE_SECONDS), 0.01f)
    }

    @Test
    fun `it holds still long enough to be read`() {
        // The whole point of an ident. Sampled across the hold rather than at
        // one instant, because a hold that is really a plateau of one frame
        // would pass a single-point check.
        var sample = 0f
        while (sample <= HOLD_SECONDS) {
            assertEquals(
                "the ident dipped ${FADE_SECONDS + sample}s in, during the hold",
                1f,
                developerIdentAlpha(FADE_SECONDS + sample),
                0.001f
            )
            sample += 0.05f
        }
    }

    @Test
    fun `it fades out and ends at nothing`() {
        val fadeStart = FADE_SECONDS + HOLD_SECONDS
        assertEquals(0.5f, developerIdentAlpha(fadeStart + FADE_SECONDS / 2f), 0.01f)
        assertEquals(0f, developerIdentAlpha(IDENT_SECONDS), 0.01f)
        assertEquals(
            "it must not come back after it is over",
            0f,
            developerIdentAlpha(IDENT_SECONDS + 5f),
            0.001f
        )
    }

    @Test
    fun `it is never invisible while it is supposed to be on screen`() {
        // A fade that crosses zero in the middle is a blink. Walked frame by
        // frame at 60fps across the whole beat.
        var t = 1f / 60f
        while (t < IDENT_SECONDS - 1f / 60f) {
            assertTrue(
                "the ident was invisible ${t}s in, which reads as a blink",
                developerIdentAlpha(t) > 0f
            )
            t += 1f / 60f
        }
    }

    @Test
    fun `it is short enough to sit in front of the boot screen`() {
        // It runs before the existing 1.9s boot splash on every single launch.
        // A publisher card the player cannot get past is the first thing they
        // resent about the game.
        assertTrue(
            "the ident runs for $IDENT_SECONDS seconds before anything else",
            IDENT_SECONDS <= 2.5f
        )
    }

    // ----------------------------------------------------------- on screen

    @Test
    fun `it draws the banner and hands over when it is done`() {
        var finished = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CyOpsTheme {
                Box(Modifier.fillMaxSize()) {
                    DeveloperSplashScreen(onFinished = { finished++ })
                }
            }
        }
        compose.mainClock.advanceTimeBy(100)

        // Found by its label rather than its text: the banner is drawn glyph
        // by glyph onto a Canvas now, because a Text centred each row
        // independently and sheared the whole logo into a diagonal on a real
        // device while looking perfect in this renderer.
        compose.onNodeWithContentDescription(DEVELOPER_NAME).assertIsDisplayed()
        compose.onNodeWithText("P R E S E N T S").assertIsDisplayed()
        assertEquals("it handed over before it had been read", 0, finished)

        compose.mainClock.advanceTimeBy((IDENT_SECONDS * 1000).toLong() + 200)
        assertEquals("the ident never finished, so the game never starts", 1, finished)
    }

    @Test
    fun `tapping it skips ahead, once`() {
        var finished = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CyOpsTheme {
                Box(Modifier.fillMaxSize()) {
                    DeveloperSplashScreen(onFinished = { finished++ })
                }
            }
        }
        compose.mainClock.advanceTimeBy(100)

        compose.onNodeWithContentDescription(DEVELOPER_NAME).performClick()
        assertEquals(1, finished)

        // Letting the timer run out must not hand over a second time: two
        // navigations would push the menu onto the stack twice.
        compose.mainClock.advanceTimeBy((IDENT_SECONDS * 1000).toLong() + 200)
        assertEquals("the ident handed over twice", 1, finished)
    }
}
