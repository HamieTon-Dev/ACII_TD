package com.cyopstd.game

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.cyopstd.game.model.AgentType
import com.cyopstd.game.state.GameViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * A save that lost the wave-30 unlocks is repaired on load.
 *
 * Before 1.39.2 three agents unlocking together at wave 30 raced each other
 * into the store and only the last survived. Players who hit that have a best
 * wave past 30 and a stored set missing QUANTUM and RED HAT. They must get them
 * back without replaying wave 30.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class UnlockRepairTest {

    @Test
    fun `a best wave past 30 unlocks every wave-30 agent, whatever the stored set says`() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val repository = TestStores.isolatedRepository()
        runBlocking {
            // The damaged save: best wave 35, only BLUE HAT recorded.
            repository.updateHighestWave(35)
            repository.unlockAgent(AgentType.BLUEHAT)
        }

        val viewModel = GameViewModel(application, repository)
        val wanted = listOf(AgentType.QUANTUM_DEFENDER, AgentType.REDHAT, AgentType.BLUEHAT)
            .map { it.name }
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline &&
            !viewModel.unlockedAgents.containsAll(wanted)
        ) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }

        for (name in wanted) {
            assertTrue("$name is still locked at best wave 35", name in viewModel.unlockedAgents)
        }
        assertTrue(
            "nothing past the best wave unlocks early",
            AgentType.NETWORK_ARCHITECT.name !in viewModel.unlockedAgents
        )
    }
}
