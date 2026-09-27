package com.cyopstd.game

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.cyopstd.game.core.Maps
import com.cyopstd.game.engine.PlacementResult
import com.cyopstd.game.engine.RunPhase
import com.cyopstd.game.model.AgentType
import com.cyopstd.game.save.SavedRun
import com.cyopstd.game.state.GameViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * SERVER SYSTEMS ENGINEER [S] through the real app, not just the engine:
 * earned in the break before wave 100 on the beginner level, saved, placeable
 * from the view model, and repairing the core in a running wave.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ServerEngineerAppFlowTest {

    private val engineer = AgentType.SERVER_SYSTEMS_ENGINEER.name

    private fun waitFor(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline && !condition()) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
    }

    private fun resume(wave: Int, mapId: String): Pair<GameViewModel, com.cyopstd.game.save.GameRepository> {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val repository = TestStores.isolatedRepository()
        runBlocking { repository.saveRun(SavedRun(wave = wave, serverHp = 100, crypto = 5_000, mapId = mapId)) }
        val viewModel = GameViewModel(application, repository)
        shadowOf(Looper.getMainLooper()).idle()
        var loaded = false
        viewModel.continueGame(onLoaded = { loaded = true })
        waitFor { loaded }
        return viewModel to repository
    }

    @Test
    fun `earned before wave 100 on the beginner level, placed, and repairing`() {
        val (viewModel, repository) = resume(99, Maps.PERIMETER.id)
        assertEquals(RunPhase.PREPARING, viewModel.engine.phase)
        waitFor { engineer in viewModel.unlockedAgents }
        assertTrue("[S] should unlock in the break before wave 100", engineer in viewModel.unlockedAgents)

        // Deployable through the same unlock check the deploy panel uses.
        val engine = viewModel.engine
        val node = Maps.PERIMETER.nodesByCoverage.first { engine.agentAt(it.id) == null }.id
        assertEquals(PlacementResult.SUCCESS, engine.placeAgent(AgentType.SERVER_SYSTEMS_ENGINEER, node))

        // Repairs once a wave is running.
        engine.startNextWave()
        repeat(200) { if (engine.phase != RunPhase.IN_WAVE) engine.update(0.05f, 1f) }
        assertEquals(RunPhase.IN_WAVE, engine.phase)
        engine.damageServer(10)
        val hurt = engine.serverHp
        var t = 0f
        while (t < 30.2f) { engine.combatSystem().update(0.02f); t += 0.02f }
        assertEquals(hurt + 1, engine.serverHp)

        // And saved: a new session on the same store still has it.
        val reloaded = GameViewModel(ApplicationProvider.getApplicationContext(), repository)
        waitFor { engineer in reloaded.unlockedAgents }
        assertTrue("[S] was not saved", engineer in reloaded.unlockedAgents)
    }

    @Test
    fun `wave 99 on another level does not earn it`() {
        val (viewModel, _) = resume(99, Maps.HUGGING_FACE.id)
        waitFor { viewModel.engine.currentWave == 99 }
        repeat(20) { shadowOf(Looper.getMainLooper()).idle(); Thread.sleep(20) }
        assertFalse(engineer in viewModel.unlockedAgents)
    }
}
