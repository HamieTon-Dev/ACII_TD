package com.cyopstd.game

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.cyopstd.game.engine.RunPhase
import com.cyopstd.game.model.AgentType
import com.cyopstd.game.save.SavedRun
import com.cyopstd.game.state.GameViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The wave-30 agents through the real game, end to end (owner, 2026-09-26:
 * "Quantum is still locked at wave 30").
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class Wave30UnlockFlowTest {

    private val wave30 = listOf(AgentType.QUANTUM_DEFENDER, AgentType.REDHAT, AgentType.BLUEHAT)
        .map { it.name }

    private fun waitFor(viewModel: GameViewModel, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline && !condition()) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
    }

    @Test
    fun `the wave-30 agents unlock in the break before wave 30, and stay unlocked`() {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val repository = TestStores.isolatedRepository()
        // Wave 29 is done: the break before wave 30.
        runBlocking { repository.saveRun(SavedRun(wave = 29, serverHp = 100, crypto = 500)) }

        val viewModel = GameViewModel(application, repository)
        shadowOf(Looper.getMainLooper()).idle()
        var loaded = false
        viewModel.continueGame(onLoaded = { loaded = true })
        waitFor(viewModel) { loaded }
        assertEquals(RunPhase.PREPARING, viewModel.engine.phase)
        assertEquals(29, viewModel.engine.currentWave)

        // Unlocked in the break, while there is time to deploy them for wave 30.
        waitFor(viewModel) { viewModel.unlockedAgents.containsAll(wave30) }
        for (name in wave30) {
            assertTrue("$name is still locked before wave 30", name in viewModel.unlockedAgents)
        }

        // And saved: a fresh view model on the same store still has them.
        val reloaded = GameViewModel(application, repository)
        waitFor(reloaded) { reloaded.unlockedAgents.containsAll(wave30) }
        for (name in wave30) {
            assertTrue("$name was not saved", name in reloaded.unlockedAgents)
        }
    }
}
