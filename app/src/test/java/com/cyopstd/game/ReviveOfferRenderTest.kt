package com.cyopstd.game

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.unit.dp
import com.cyopstd.game.state.GameOverSummary
import com.cyopstd.game.ui.game.GameOverOverlay
import com.cyopstd.game.ui.theme.CyOpsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The revive offer, as the player actually sees it.
 *
 * Two things are being protected. The first is the rule from 1.16.0: a build
 * with no rewarded ad, or a run that has spent its revive, must show no button
 * at all rather than a dead one. The second is honesty about what REMOVE ADS
 * covers — a player who paid to remove ads and then meets an ad button with no
 * explanation has every right to feel cheated, so the explanation is asserted
 * rather than left to survive a future edit by luck.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1600dp-h760dp-land-mdpi")
class ReviveOfferRenderTest {

    @get:Rule
    val compose = createComposeRule()

    private val summary = GameOverSummary(
        waveReached = 12,
        attacksBlocked = 310,
        cryptoEarned = 940,
        bossesDefeated = 1,
        bestWave = 12,
        isNewRecord = false
    )

    private fun show(
        onRevive: (() -> Unit)? = {},
        revivesLeft: Int = 1,
        adShowing: Boolean = false
    ) {
        compose.setContent {
            CyOpsTheme {
                GameOverOverlay(
                    summary = summary,
                    onRetry = {},
                    onMainMenu = {},
                    onWatchAdToRevive = onRevive,
                    revivesLeft = revivesLeft,
                    reviveAdShowing = adShowing
                )
            }
        }
    }

    @Test
    fun `the offer states what it costs, what it gives back, and that there is one`() {
        show()
        compose.onNodeWithText("WOULD YOU LIKE TO REVIVE?").assertIsDisplayed()
        compose.onNodeWithText("YES — WATCH AD", substring = true).assertIsDisplayed()
        compose.onNodeWithText("NO", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("half integrity", substring = true).assertIsDisplayed()
        compose.onNodeWithText("One revive per run.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("revive ads are separate", substring = true).assertIsDisplayed()
    }

    @Test
    fun `the offer is drawn, not merely present`() {
        // The 1.22.0 lesson: a semantics assertion proves a composable exists,
        // and the boss dossier's signature line existed at a height of zero.
        show()
        compose.onNodeWithText("YES — WATCH AD", substring = true)
            .assertHeightIsAtLeast(20.dp)
    }

    @Test
    fun `the question is answered before the player can leave`() {
        // RETRY and MAIN MENU appear only once YES or NO has been chosen, so
        // the loss ad can only follow a NO.
        show()
        compose.onNodeWithText("RETRY", substring = true).assertDoesNotExist()
        compose.onNodeWithText("MAIN MENU", substring = true).assertDoesNotExist()
    }

    @Test
    fun `no button at all when there is no ad to pay for it`() {
        show(onRevive = null)
        compose.onNodeWithText("WOULD YOU LIKE TO REVIVE?").assertDoesNotExist()
        // The ordinary way out of a lost run is untouched.
        compose.onNodeWithText("RETRY", substring = true).assertIsDisplayed()
        compose.onNodeWithText("MAIN MENU", substring = true).assertIsDisplayed()
    }

    @Test
    fun `the button reports itself while the ad is on screen and cannot be pressed twice`() {
        var presses = 0
        show(onRevive = { presses++ }, adShowing = true)
        compose.onNodeWithText("LOADING AD", substring = true).performClick()
        assertEquals("a second press while the ad is up would queue a second revive", 0, presses)
    }

    @Test
    fun `it says how many are left when a pack has bought more`() {
        show(revivesLeft = 3)
        compose.onNodeWithText("3 revives left this run.", substring = true).assertIsDisplayed()
    }
}
