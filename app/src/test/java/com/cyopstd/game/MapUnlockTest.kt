package com.cyopstd.game

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.cyopstd.game.core.GameMode
import com.cyopstd.game.core.Maps
import com.cyopstd.game.state.GameViewModel
import kotlinx.coroutines.flow.first
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
 * E1's last piece: choosing a level, and earning the right to.
 *
 * *"This level unlocks by reaching wave 100 of Hack AI level."* On which mode
 * is the whole of it — a lifetime best cannot answer that question, so the
 * record is kept per mode and this is what proves the two do not get confused.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MapUnlockTest {

    private fun repository() = TestStores.isolatedRepository()

    // ------------------------------------------------------------ the rule

    @Test
    fun `the first level is always open and the gauntlet is not`() {
        assertTrue(Maps.PERIMETER.unlockedBy { 0 })
        assertFalse(Maps.HUGGING_FACE.unlockedBy { 0 })
        assertEquals(GameMode.HACK_AI, Maps.HUGGING_FACE.unlockMode)
        assertEquals(100, Maps.HUGGING_FACE.unlockAtWave)
    }

    @Test
    fun `wave 100 on the standard mode does not open the gauntlet`() {
        // The trap this exists for: a player with a huge standard record and
        // no HACK:AI run at all must not be handed the second level.
        val bestByMode = mapOf(GameMode.STANDARD to 400, GameMode.HACK_AI to 12)
        assertFalse(Maps.HUGGING_FACE.unlockedBy { bestByMode.getValue(it) })
        assertTrue(Maps.HUGGING_FACE.unlockedBy { if (it == GameMode.HACK_AI) 100 else 0 })
    }

    // ------------------------------------------------------- what gets stored

    @Test
    fun `a HACK AI run raises the HACK AI record and a standard one does not`() = runBlocking {
        val repository = repository()
        repository.updateHighestWave(140, GameMode.STANDARD.id)
        assertEquals(140, repository.stats.first().highestWave)
        assertEquals(
            "a standard run wrote into the HACK:AI record",
            0,
            repository.stats.first().highestWaveHackAi
        )

        repository.updateHighestWave(100, GameMode.HACK_AI.id)
        val stats = repository.stats.first()
        assertEquals(100, stats.highestWaveHackAi)
        assertEquals("the lifetime best must not go backwards", 140, stats.highestWave)
    }

    @Test
    fun `a finished HACK AI run counts too`() = runBlocking {
        val repository = repository()
        repository.recordRunResult(
            waveReached = 101, attacksBlocked = 0, bossesDefeated = 0, cryptoEarned = 0,
            serverDamageTaken = 0, agentsDeployed = 0, agentUpgrades = 0,
            deploymentsByType = emptyMap(), countAsGamePlayed = true,
            modeId = GameMode.HACK_AI.id
        )
        assertEquals(101, repository.stats.first().highestWaveHackAi)
    }

    // ------------------------------------------------------------- the menu

    private fun viewModel(repository: com.cyopstd.game.save.GameRepository): GameViewModel {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = GameViewModel(application, repository)
        shadowOf(Looper.getMainLooper()).idle()
        return viewModel
    }

    @Test
    fun `a new player is offered one level and cannot choose the other`() {
        val viewModel = viewModel(repository())
        assertEquals(listOf(Maps.PERIMETER), viewModel.availableMaps)
        viewModel.selectMap(Maps.HUGGING_FACE)
        assertEquals(
            "a locked level was selectable",
            Maps.PERIMETER,
            viewModel.selectedMap
        )
    }

    @Test
    fun `clearing wave 100 on HACK AI offers the gauntlet, and it can be played`() {
        val repository = repository()
        runBlocking { repository.updateHighestWave(100, GameMode.HACK_AI.id) }
        val viewModel = viewModel(repository)

        assertTrue(
            "the gauntlet is still locked after the run that unlocks it",
            Maps.HUGGING_FACE in viewModel.availableMaps
        )
        viewModel.selectMap(Maps.HUGGING_FACE)
        assertEquals(Maps.HUGGING_FACE, viewModel.selectedMap)

        // And the run actually starts there. Choosing a level that the engine
        // then ignores is the failure this is really guarding against.
        viewModel.startNewGame()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(Maps.HUGGING_FACE.id, viewModel.engine.map.id)
    }
}
