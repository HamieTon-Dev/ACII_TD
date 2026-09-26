package com.cyopstd.game

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.cyopstd.game.core.Balance
import com.cyopstd.game.core.GameMode
import com.cyopstd.game.core.Maps
import com.cyopstd.game.engine.GameEngine
import com.cyopstd.game.engine.PlacementResult
import com.cyopstd.game.engine.RunPhase
import com.cyopstd.game.engine.WaveGenerator
import com.cyopstd.game.save.GameRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import com.cyopstd.game.model.AgentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random

/**
 * SERVER SYSTEMS ENGINEER [S] (backlog W1), as the owner specified it:
 * heals CORE-SERVER from anywhere, four maximum, 1 HP per 30 seconds at level
 * 1 counting only a running wave, a green "+ +" over the core on each repair,
 * and unlocked only by wave 100 on the beginner level.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ServerEngineerTest {

    private val engineer = AgentType.SERVER_SYSTEMS_ENGINEER

    private fun newEngine(): GameEngine {
        val random = Random(3)
        val engine = GameEngine(random, WaveGenerator(random))
        engine.isAgentUnlocked = { true }
        engine.startNewRun()
        engine.addCrypto(100_000, countAsEarned = false)
        return engine
    }

    private val nodes get() = Maps.PERIMETER.nodesByCoverage.map { it.id }

    /** Tick only the agents, so no packet walks in and muddies the integrity. */
    private fun GameEngine.tickAgents(seconds: Float) {
        var elapsed = 0f
        while (elapsed < seconds - 1e-4f) {
            combatSystem().update(0.02f)
            elapsed += 0.02f
        }
    }

    private fun GameEngine.greenPlusTexts(): Int =
        effects.items.count { it.active && it.text == "+ +" && it.colorArgb == GameEngine.COLOR_SUCCESS }

    // ------------------------------------------------------------ the spec

    @Test
    fun `matches the owner's specification`() {
        assertEquals("SERVER SYSTEMS ENGINEER", engineer.displayName)
        assertEquals("S", engineer.glyph)
        assertEquals("[S]", engineer.renderedGlyph(1))
        assertEquals(4, engineer.maxDeployed)
        assertEquals(100, engineer.unlockWave)
        assertTrue(engineer.healsServer)
        assertTrue(engineer.beginnerLevelOnly)
        assertEquals(30f, Balance.engineerHealInterval(1), 0.001f)
        assertEquals(1, Balance.ENGINEER_HEAL_AMOUNT)
        assertTrue("levels should shorten the timer", Balance.engineerHealInterval(100) < 11f)
        val glyphs = AgentType.entries.map { it.glyph }
        assertEquals("glyphs must stay unique", glyphs.size, glyphs.toSet().size)
    }

    @Test
    fun `the lock text names the beginner level`() {
        assertEquals(
            "Unlock this agent by reaching wave 100 on NETWORK PERIMETER in NETWORK DEFENCE mode.",
            engineer.unlockRequirement
        )
        assertEquals("Unlock this agent by reaching wave 30 on any level.", AgentType.REDHAT.unlockRequirement)
    }

    // ------------------------------------------------------------- healing

    @Test
    fun `repairs 1 HP every 30 seconds of a running wave, with a green plus over the core`() {
        val engine = newEngine()
        assertEquals(PlacementResult.SUCCESS, engine.placeAgent(engineer, nodes[0]))
        engine.startNextWave()
        assertEquals(RunPhase.IN_WAVE, engine.phase)
        engine.damageServer(10)
        val hurt = engine.serverHp

        engine.tickAgents(29.9f)
        assertEquals("no repair before 30 s", hurt, engine.serverHp)
        engine.tickAgents(0.2f)
        assertEquals("one repair at 30 s", hurt + 1, engine.serverHp)
        assertEquals(1, engine.greenPlusTexts())
    }

    @Test
    fun `the timer does not run in the break between waves or the boss warning`() {
        val engine = newEngine()
        engine.placeAgent(engineer, nodes[0])
        engine.damageServer(10)
        val hurt = engine.serverHp
        assertEquals(RunPhase.PREPARING, engine.phase)
        engine.tickAgents(120f)
        assertEquals("healed while the wave was waiting", hurt, engine.serverHp)

        // And the break did not bank time: a wave still needs a full 30 s.
        engine.startNextWave()
        engine.tickAgents(29.9f)
        assertEquals(hurt, engine.serverHp)
    }

    @Test
    fun `each engineer repairs on its own timer`() {
        val engine = newEngine()
        repeat(4) { assertEquals(PlacementResult.SUCCESS, engine.placeAgent(engineer, nodes[it])) }
        engine.startNextWave()
        engine.damageServer(20)
        val hurt = engine.serverHp
        engine.tickAgents(30.1f)
        assertEquals(hurt + 4, engine.serverHp)
    }

    @Test
    fun `never heals past full integrity and never fires`() {
        val engine = newEngine()
        engine.placeAgent(engineer, nodes[0])
        engine.startNextWave()
        engine.tickAgents(65f)
        assertEquals(engine.serverMaxHp, engine.serverHp)
        assertEquals("no plus at full integrity", 0, engine.greenPlusTexts())
        assertEquals(0, engine.projectiles.items.count { it.active })
    }

    @Test
    fun `no more than four may be deployed`() {
        val engine = newEngine()
        repeat(4) { assertEquals(PlacementResult.SUCCESS, engine.placeAgent(engineer, nodes[it])) }
        assertEquals(PlacementResult.TYPE_LIMIT_REACHED, engine.placeAgent(engineer, nodes[4]))
    }

    @Test
    fun `heals from the full game loop too`() {
        val engine = newEngine()
        assertEquals(PlacementResult.SUCCESS, engine.placeAgent(engineer, nodes[0]))
        // Hold the lanes so no packet reaches the core during the check.
        for (node in nodes.drop(1).take(12)) {
            engine.placeAgent(AgentType.ANALYST, node)
            repeat(40) { engine.upgradeAgent(node) }
        }
        engine.startNextWave()
        engine.damageServer(10)
        val hurt = engine.serverHp
        var inWave = 0f
        var guard = 0
        while (inWave < 31f && guard++ < 100_000) {
            if (engine.phase == RunPhase.PREPARING) engine.startNextWave()
            if (engine.phase == RunPhase.IN_WAVE) inWave += 0.02f
            engine.update(0.02f, 1f)
        }
        // Anything that leaked through is added back, so only the repairs remain.
        val leaked = engine.runServerDamageTaken - 10
        assertTrue(
            "expected a repair after 30 s of waves (hp ${engine.serverHp}, leaked $leaked)",
            engine.serverHp + leaked > hurt
        )
    }

    // ------------------------------------------------------------ unlocking

    @Test
    fun `only waves on the beginner level count toward it`() {
        assertFalse(engineer in AgentType.earnedBy(500))
        assertFalse(engineer in AgentType.earnedBy(500, 99))
        assertTrue(engineer in AgentType.earnedBy(0, 100))
        assertTrue(AgentType.isBeginnerLevel(Maps.PERIMETER.id, GameMode.STANDARD.id))
        assertFalse(AgentType.isBeginnerLevel(Maps.PERIMETER.id, GameMode.HACK_AI.id))
        assertFalse(AgentType.isBeginnerLevel(Maps.HUGGING_FACE.id, GameMode.STANDARD.id))
    }

    @Test
    fun `the engine unlocks it at wave 100 on the beginner level only`() {
        for ((map, mode, expected) in listOf(
            Triple(Maps.PERIMETER, GameMode.STANDARD, true),
            Triple(Maps.PERIMETER, GameMode.HACK_AI, false),
            Triple(Maps.HUGGING_FACE, GameMode.STANDARD, false)
        )) {
            val random = Random(5)
            val engine = GameEngine(random, WaveGenerator(random))
            val unlocked = mutableSetOf<AgentType>()
            engine.isAgentUnlocked = { it in unlocked }
            engine.onAgentUnlocked = { unlocked += it }
            engine.selectMode(mode)
            engine.selectMap(map)
            engine.startNewRun()
            engine.restore(
                wave = 99, serverHp = engine.serverMaxHp, crypto = 0, placements = emptyList(),
                attacksBlocked = 0, cryptoEarned = 0, bossesDefeated = 0,
                serverDamageTaken = 0, agentsDeployed = 0, agentUpgrades = 0
            )
            assertEquals("${map.id}/${mode.id}", expected, engineer in unlocked)
        }
    }

    @Test
    fun `the beginner record is kept apart from the others`() = runBlocking {
        val repository = TestStores.isolatedRepository()
        repository.updateHighestWave(100, GameMode.STANDARD.id, Maps.PERIMETER.id)
        repository.updateHighestWave(100, GameMode.HACK_AI.id, Maps.PERIMETER.id)
        repository.updateHighestWave(150, GameMode.HACK_AI.id, Maps.PERIMETER.id)
        repository.updateHighestWave(160, GameMode.STANDARD.id, Maps.HUGGING_FACE.id)
        var stats = repository.stats.first()
        assertEquals(100, stats.highestWaveBeginner)
        assertEquals(160, stats.highestWave)

        repository.recordRunResult(
            waveReached = 120, attacksBlocked = 0, bossesDefeated = 0, cryptoEarned = 0,
            serverDamageTaken = 0, agentsDeployed = 0, agentUpgrades = 0,
            deploymentsByType = emptyMap(), countAsGamePlayed = false,
            modeId = GameMode.STANDARD.id, mapId = Maps.PERIMETER.id
        )
        stats = repository.stats.first()
        assertEquals(120, stats.highestWaveBeginner)
    }

    @Test
    fun `an unknown level never counts as the beginner level`() = runBlocking {
        // Seeding aside: once the record exists, a run with no level must not raise it.
        val repository = TestStores.isolatedRepository()
        repository.updateHighestWave(5, GameMode.STANDARD.id, Maps.PERIMETER.id)
        repository.updateHighestWave(130, GameMode.STANDARD.id)
        assertEquals(5, repository.stats.first().highestWaveBeginner)
    }

    @Test
    fun `saves from before the record are seeded from what they prove`() = runBlocking {
        // Never played HACK:AI: every wave so far was on the beginner level.
        val store = TestStores.isolatedStore()
        store.edit { it[intPreferencesKey("highest_wave")] = 104 }
        assertEquals(104, GameRepository(store).stats.first().highestWaveBeginner)

        // Played HACK:AI, so wave 100 on the beginner level was cleared to open it.
        val veteran = TestStores.isolatedStore()
        veteran.edit {
            it[intPreferencesKey("highest_wave")] = 180
            it[intPreferencesKey("highest_wave_hack_ai")] = 180
        }
        assertEquals(100, GameRepository(veteran).stats.first().highestWaveBeginner)
    }
}
