package com.cyopstd.game

import com.cyopstd.game.core.Balance
import com.cyopstd.game.engine.GameEngine
import com.cyopstd.game.model.EffectKind
import com.cyopstd.game.save.DEFAULT_PANEL_OPACITY
import com.cyopstd.game.save.MIN_PANEL_OPACITY
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The boss blast runs on real time whatever the game speed, and the in-game
 * panel opacity is a saved, clamped setting (owner, 2026-09-26).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BlastAndPanelTest {

    private fun blastProgressAfter(realSeconds: Float, speed: Float): Float {
        val engine = GameEngine()
        engine.startNewRun()
        engine.effectSystem().spawnShards(
            x = 800f, y = 380f,
            colorArgb = GameEngine.COLOR_HOSTILE,
            radius = Balance.BOSS_SHARD_RADIUS,
            lifetime = Balance.BOSS_SHARD_LIFETIME,
            toEdge = true
        )
        var elapsed = 0f
        while (elapsed < realSeconds) {
            engine.update(1f / 60f, speed)
            elapsed += 1f / 60f
        }
        return engine.effects.items.first { it.active && it.kind == EffectKind.SHARD_BURST }.progress
    }

    @Test
    fun `the boss blast takes the same real time at 1x and 4x`() {
        val slow = blastProgressAfter(0.5f, speed = 1f)
        val fast = blastProgressAfter(0.5f, speed = 4f)
        assertEquals("the blast sped up with the game", slow, fast, 0.02f)
    }

    private fun eliteProgressAfter(realSeconds: Float, speed: Float): Float {
        val engine = GameEngine()
        engine.startNewRun()
        engine.effectSystem().spawnShards(
            x = 800f, y = 380f, colorArgb = GameEngine.COLOR_ELITE,
            radius = Balance.ELITE_SHARD_RADIUS, lifetime = Balance.ELITE_SHARD_LIFETIME
        )
        val effect = engine.effects.items.first { it.active }
        var elapsed = 0f
        while (elapsed < realSeconds) {
            engine.update(1f / 60f, speed)
            elapsed += 1f / 60f
        }
        return effect.progress
    }

    @Test
    fun `other effects still follow game speed`() {
        val slow = eliteProgressAfter(0.1f, speed = 1f)
        val fast = eliteProgressAfter(0.1f, speed = 4f)
        assertTrue("an elite burst runs on game time: $slow at 1x, $fast at 4x", fast > slow * 3f)
    }

    @Test
    fun `panel opacity defaults to about a third see-through and is clamped`() = runTest {
        val repository = TestStores.isolatedRepository()
        assertEquals(DEFAULT_PANEL_OPACITY, repository.settings.first().panelOpacity, 0.001f)
        assertTrue(DEFAULT_PANEL_OPACITY in 0.5f..0.7f)

        repository.updateSettings { it.copy(panelOpacity = 0.05f) }
        assertEquals(MIN_PANEL_OPACITY, repository.settings.first().panelOpacity, 0.001f)

        repository.updateSettings { it.copy(panelOpacity = 0.8f) }
        assertEquals(0.8f, repository.settings.first().panelOpacity, 0.001f)
    }
}
