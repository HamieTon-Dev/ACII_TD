package com.cyopstd.game

import android.app.Application
import android.os.Looper
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.cyopstd.game.model.AgentType
import com.cyopstd.game.state.GameViewModel
import com.cyopstd.game.ui.game.DeployPanel
import com.cyopstd.game.ui.theme.CyOpsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Owner, 2026-10-02: *"gray these out when you have max units used on the
 * map"* — the agents with a deploy limit, once that many are on the board.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1600dp-h760dp-land-mdpi")
class MaxedAgentsTest {

    @get:Rule
    val compose = createComposeRule()

    private val everything = AgentType.entries.map { it.name }.toSet()

    @Test
    fun `the compact bar shows MAX for an agent at its limit, and still selects the rest`() {
        var picked: AgentType? = null
        compose.setContent {
            CyOpsTheme {
                Box(Modifier.fillMaxSize()) {
                    DeployPanel(
                        crypto = 1_000_000, unlockedAgents = everything, selected = null,
                        onSelect = { picked = it }, onClose = {}, compact = true,
                        maxedOut = setOf(AgentType.ANTI_DUCK)
                    )
                }
            }
        }
        compose.onNodeWithText("MAX").assertExists()
        // Its cost is gone (one fewer agent shows that price); the rest keep theirs.
        fun priced(cost: Int) = compose.onAllNodesWithText("$cost").fetchSemanticsNodes().size
        assertEquals(
            AgentType.catalog.count { it.cost == AgentType.ANTI_DUCK.cost } - 1,
            priced(AgentType.ANTI_DUCK.cost)
        )
        compose.onNodeWithTag("compact-agent-${AgentType.FIREWALL.name}").performClick()
        assertEquals(AgentType.FIREWALL, picked)
    }

    @Test
    fun `the full cards say MAX n DEPLOYED`() {
        compose.setContent {
            CyOpsTheme {
                Box(Modifier.fillMaxSize()) {
                    DeployPanel(
                        crypto = 1_000_000, unlockedAgents = everything, selected = null,
                        onSelect = {}, onClose = {}, maxedOut = setOf(AgentType.ANTI_DUCK)
                    )
                }
            }
        }
        // The row scrolls; the duck sits well along it.
        compose.onNode(androidx.compose.ui.test.hasScrollAction())
            .performScrollToNode(androidx.compose.ui.test.hasTestTag("agent-card-${AgentType.ANTI_DUCK.name}"))
        compose.onNodeWithText("MAX ${AgentType.ANTI_DUCK.maxDeployed} DEPLOYED").assertExists()
    }

    @Test
    fun `an agent counts as maxed exactly when its limit is on the board`() {
        val viewModel = GameViewModel(ApplicationProvider.getApplicationContext<Application>(), TestStores.isolatedRepository())
        shadowOf(Looper.getMainLooper()).idle()
        viewModel.startNewGame()
        shadowOf(Looper.getMainLooper()).idle()
        val engine = viewModel.engine
        engine.isAgentUnlocked = { true }
        engine.addCrypto(1_000_000, countAsEarned = false)
        val type = AgentType.ANTI_DUCK
        val nodes = engine.map.nodes.filter { !it.serverSlot }.map { it.id }

        assertFalse(type in viewModel.maxedOutAgents())
        engine.placeAgent(type, nodes[0])
        assertFalse("one of two is not the limit", type in viewModel.maxedOutAgents())
        engine.placeAgent(type, nodes[1])
        assertTrue("two of two is", type in viewModel.maxedOutAgents())
        assertFalse("an agent with no limit never maxes", AgentType.FIREWALL in viewModel.maxedOutAgents())

        // Selling one frees the slot again.
        engine.sellAgent(nodes[0])
        assertFalse(type in viewModel.maxedOutAgents())
    }
}
