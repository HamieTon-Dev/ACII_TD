package com.cyopstd.game

import com.cyopstd.game.core.Maps
import com.cyopstd.game.engine.GameEngine
import com.cyopstd.game.engine.RunPhase
import com.cyopstd.game.engine.WaveGenerator
import com.cyopstd.game.model.AgentType
import com.cyopstd.game.save.SavedRun
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random

/**
 * The owner's game-breaking bug (2026-10-02): a save kept only the wave
 * number, the crypto and the board, and CONTINUE replayed the wave from its
 * start with the crypto earned in it kept, so leaving for the menu and
 * continuing farmed one wave's money forever. A wave in progress is now saved
 * as it stands and resumes from there.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MidWaveSaveTest {

    private fun engine(seed: Int = 7): GameEngine {
        val random = Random(seed)
        return GameEngine(random, WaveGenerator(random)).apply {
            isAgentUnlocked = { true }
            selectMap(Maps.HUGGING_FACE)
            startNewRun()
            addCrypto(5_000, countAsEarned = false)
        }
    }

    /** A run part-way into wave 3, with enemies on the board and some killed. */
    private fun midWave(): GameEngine {
        val e = engine()
        val nodes = Maps.HUGGING_FACE.nodesByCoverage
        e.placeAgent(AgentType.IDS, nodes[0].id)
        e.placeAgent(AgentType.FIREWALL, nodes[1].id)
        repeat(2) {
            e.startNextWave()
            var guard = 0
            while (e.phase != RunPhase.PREPARING && guard++ < 100_000) e.update(0.05f, 1f)
        }
        e.startNextWave()
        repeat(160) { e.update(0.05f, 1f) }
        check(e.phase == RunPhase.IN_WAVE) { "wave 3 ended too soon for the test" }
        check(e.enemies.activeCount() > 0) { "no enemies on the board to save" }
        return e
    }

    private fun restored(from: GameEngine): GameEngine {
        val snapshot = from.snapshotWave()!!
        val to = engine(seed = 99)
        to.restore(
            wave = from.currentWave, serverHp = from.serverHp, crypto = from.crypto,
            placements = from.snapshotPlacements(), attacksBlocked = from.runAttacksBlocked,
            cryptoEarned = from.runCryptoEarned, bossesDefeated = from.runBossesDefeated,
            serverDamageTaken = from.runServerDamageTaken, agentsDeployed = from.runAgentsDeployed,
            agentUpgrades = from.runAgentUpgrades
        )
        to.restoreWave(snapshot)
        return to
    }

    @Test
    fun `a wave in progress comes back exactly as it was left`() {
        val before = midWave()
        val after = restored(before)
        assertEquals(RunPhase.IN_WAVE, after.phase)
        assertEquals(before.currentWave, after.currentWave)
        assertEquals(before.crypto, after.crypto)
        assertEquals(before.enemiesRemaining, after.enemiesRemaining)
        val was = before.enemies.items.filter { it.active }
        val now = after.enemies.items.filter { it.active }
        assertEquals(was.size, now.size)
        for ((a, b) in was.zip(now)) {
            assertEquals(a.type, b.type)
            assertEquals(a.lane, b.lane)
            assertEquals(a.progress, b.progress, 0.001f)
            assertEquals(a.x, b.x, 0.01f)
            assertEquals(a.y, b.y, 0.01f)
            assertEquals(a.health, b.health, 0.001f)
            assertEquals(a.reward, b.reward)
        }
        // The same snapshot taken again is the same snapshot.
        assertEquals(before.snapshotWave()!!.copy(), after.snapshotWave()!!.copy())
    }

    @Test
    fun `continuing does not replay the wave, so its money cannot be earned twice`() {
        val before = midWave()
        val snapshot = before.snapshotWave()!!
        // What is left of the wave is only what had not yet been spawned, what
        // waits at the gate, and what is on the board. Nothing already killed
        // comes back to be killed (and paid for) again.
        val planEnemiesOnBoard = snapshot.enemies.count { !it.escort && !it.decoy }
        assertEquals(snapshot.enemiesRemaining, snapshot.pending.size + snapshot.held.size + planEnemiesOnBoard)
        val after = restored(before)
        assertEquals(before.enemiesRemaining, after.enemiesRemaining)

        // Leaving and continuing over and over gains nothing: the crypto and
        // what is left of the wave are unchanged every time.
        var e = after
        repeat(3) {
            val again = restored(e)
            assertEquals(after.crypto, again.crypto)
            assertEquals(after.enemiesRemaining, again.enemiesRemaining)
            e = again
        }
    }

    @Test
    fun `between waves there is nothing to save, and nothing changes`() {
        val e = engine()
        assertNull(e.snapshotWave())
    }

    @Test
    fun `the snapshot survives the save file`() = runTest {
        val before = midWave()
        val repository = TestStores.isolatedRepository()
        val snapshot = before.snapshotWave()
        repository.saveRun(SavedRun(wave = before.currentWave, crypto = before.crypto, midWave = snapshot))
        val loaded = repository.savedRun.first()
        assertNotNull(loaded?.midWave)
        assertEquals(snapshot, loaded!!.midWave)
    }
}
