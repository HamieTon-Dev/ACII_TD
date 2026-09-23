package com.cyopstd.game

import com.cyopstd.game.core.Balance
import com.cyopstd.game.core.GameMode
import com.cyopstd.game.core.WorldGeometry
import com.cyopstd.game.engine.GameEngine
import com.cyopstd.game.model.AgentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * HACK:AI, the hard mode.
 *
 * It is built as multipliers over the shared curves rather than a second
 * balance table, so these check the two things that actually matter: that it
 * cannot be reached without earning it, and that it is genuinely harder in the
 * ways it claims rather than only in name.
 */
class GameModeTest {

    @Test
    fun `hack AI has to be earned`() {
        assertTrue(GameMode.STANDARD.isUnlockedByDefault)
        assertFalse(GameMode.HACK_AI.isUnlockedByDefault)
        assertEquals(100, GameMode.HACK_AI.unlockAtWave)

        assertFalse("unlocked at wave 99", GameMode.HACK_AI.unlockedBy(99))
        assertTrue("not unlocked at wave 100", GameMode.HACK_AI.unlockedBy(100))
        assertTrue(GameMode.HACK_AI.unlockedBy(140))
        // Standard is always there, including on a fresh install.
        assertTrue(GameMode.STANDARD.unlockedBy(0))
    }

    @Test
    fun `a run in hack AI starts with less integrity`() {
        val engine = GameEngine(random = Random(1))
        engine.selectMode(GameMode.HACK_AI)
        engine.startNewRun()
        assertEquals(GameMode.HACK_AI, engine.mode)
        assertEquals(GameMode.HACK_AI.serverHp, engine.serverMaxHp)
        assertEquals(engine.serverMaxHp, engine.serverHp)
        assertTrue(
            "hard mode should not start with more integrity than normal",
            engine.serverMaxHp < Balance.SERVER_MAX_HP
        )
    }

    @Test
    fun `threats in hack AI are tougher and arrive closer together`() {
        // Measured as the time taken to put ten threats on the board, not the
        // peak count: with no defences every threat in the wave ends up on the
        // field in both modes, so the peak is the wave size and says nothing
        // about pressure. The rate is the thing the mode actually changes.
        fun sample(mode: GameMode): Pair<Float, Float> {
            val engine = GameEngine(random = Random(9))
            engine.selectMode(mode)
            engine.startNewRun()
            engine.restore(
                wave = 11, serverHp = mode.serverHp, crypto = 0, placements = emptyList(),
                attacksBlocked = 0, cryptoEarned = 0, bossesDefeated = 0,
                serverDamageTaken = 0, agentsDeployed = 0, agentUpgrades = 0
            )
            engine.startNextWave()
            var toughest = 0f
            var tenthAt = -1f
            var elapsed = 0f
            repeat(1800) {
                engine.update(1f / 60f, 1f)
                elapsed += 1f / 60f
                var alive = 0
                for (enemy in engine.enemies.items) {
                    if (!enemy.active) continue
                    alive++
                    if (enemy.maxHealth > toughest) toughest = enemy.maxHealth
                }
                if (tenthAt < 0f && alive >= 10) tenthAt = elapsed
            }
            return toughest to tenthAt
        }

        val (normalHealth, normalTenth) = sample(GameMode.STANDARD)
        val (hardHealth, hardTenth) = sample(GameMode.HACK_AI)

        assertTrue(
            "hard mode threats have $hardHealth health against $normalHealth",
            hardHealth > normalHealth * 2f
        )
        assertTrue("ten threats never reached the board", normalTenth > 0f && hardTenth > 0f)
        assertTrue(
            "hard mode took ${hardTenth}s to land ten threats against ${normalTenth}s",
            hardTenth < normalTenth * 0.9f
        )
    }

    @Test
    fun `a harder run is not also a poorer one`() {
        fun earned(mode: GameMode): Int {
            val engine = GameEngine(random = Random(5))
            engine.selectMode(mode)
            engine.startNewRun()
            engine.isAgentUnlocked = { true }
            engine.restore(
                wave = 7, serverHp = 900, crypto = 50_000, placements = emptyList(),
                attacksBlocked = 0, cryptoEarned = 0, bossesDefeated = 0,
                serverDamageTaken = 0, agentsDeployed = 0, agentUpgrades = 0
            )
            var i = 0
            repeat(6) {
                while (i < WorldGeometry.nodesByCoverage.size) {
                    engine.placeAgent(AgentType.ROOT_ADMIN, WorldGeometry.nodesByCoverage[i].id)
                    val ok = engine.agentAt(WorldGeometry.nodesByCoverage[i].id) != null
                    i++
                    if (ok) break
                }
            }
            val before = engine.crypto
            engine.startNextWave()
            repeat(3000) { engine.update(1f / 60f, 1f) }
            return engine.crypto - before
        }

        val normal = earned(GameMode.STANDARD)
        val hard = earned(GameMode.HACK_AI)
        assertTrue("hard mode paid $hard against normal's $normal", hard > normal)
    }

    @Test
    fun `every mode has a name to show at the top of a run`() {
        for (mode in GameMode.entries) {
            assertTrue("${mode.id} has no run name", mode.runName.isNotBlank())
        }
        assertEquals("HACK:AI", GameMode.HACK_AI.runName)
        assertEquals(GameMode.STANDARD, GameMode.fromIdSafe("who_knows"))
        assertEquals(GameMode.HACK_AI, GameMode.fromIdSafe("hack_ai"))
    }
}
