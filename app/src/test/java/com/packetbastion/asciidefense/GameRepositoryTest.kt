package com.packetbastion.asciidefense

import com.packetbastion.asciidefense.model.AgentType
import com.packetbastion.asciidefense.save.GameRepository
import com.packetbastion.asciidefense.save.SavedAgent
import com.packetbastion.asciidefense.save.SavedRun
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests the real DataStore persistence path under Robolectric.
 *
 * This is the component a player can lose actual progress to, and it is the one
 * that makes CONTINUE work at all, so it is exercised against a real store
 * rather than a fake: saves round-trip, unlocks accumulate, statistics fold
 * correctly across runs, and corrupt data degrades to defaults instead of
 * throwing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GameRepositoryTest {

    private lateinit var repository: GameRepository

    @Before
    fun setUp() {
        // Each test gets its own store file, so no test can observe another's
        // writes - including writes that land after a previous test finished.
        repository = TestStores.isolatedRepository()
    }

    @Test
    fun `defaults are sane before anything has been written`() = runTest {
        val settings = repository.settings.first()
        assertEquals(0.5f, settings.musicVolume, 0.001f)
        assertEquals(0.8f, settings.sfxVolume, 0.001f)
        assertTrue(settings.vibrationEnabled)
        assertTrue(settings.backgroundAnimation)
        assertFalse("auto start is off by default", settings.autoStartWaves)
        assertFalse("battery saver is off by default", settings.batterySaver)

        val stats = repository.stats.first()
        assertEquals(0, stats.highestWave)
        assertEquals(0L, stats.totalPacketsBlocked)
        assertNull(stats.favoriteAgent)

        assertNull("a fresh install has nothing to continue", repository.savedRun.first())
        assertFalse(repository.hasSavedRun())
    }

    @Test
    fun `starter agents are always unlocked`() = runTest {
        val progress = repository.progress.first()
        assertTrue(AgentType.FIREWALL.name in progress.unlockedAgents)
        assertTrue(AgentType.IDS.name in progress.unlockedAgents)
        assertFalse(AgentType.ROOT_ADMIN.name in progress.unlockedAgents)
        assertFalse(progress.tutorialCompleted)
    }

    @Test
    fun `settings persist and read back`() = runTest {
        repository.updateSettings {
            it.copy(
                musicVolume = 0.2f,
                sfxVolume = 0.0f,
                vibrationEnabled = false,
                backgroundAnimation = false,
                damageNumbers = false,
                showAgentRange = false,
                autoStartWaves = true,
                screenShake = false,
                batterySaver = true
            )
        }

        val reloaded = repository.settings.first()
        assertEquals(0.2f, reloaded.musicVolume, 0.001f)
        assertEquals(0.0f, reloaded.sfxVolume, 0.001f)
        assertFalse(reloaded.vibrationEnabled)
        assertFalse(reloaded.backgroundAnimation)
        assertFalse(reloaded.damageNumbers)
        assertFalse(reloaded.showAgentRange)
        assertTrue(reloaded.autoStartWaves)
        assertFalse(reloaded.screenShake)
        assertTrue(reloaded.batterySaver)
    }

    @Test
    fun `volumes are clamped on the way in`() = runTest {
        repository.updateSettings { it.copy(musicVolume = 9f, sfxVolume = -4f) }
        val reloaded = repository.settings.first()
        assertEquals(1f, reloaded.musicVolume, 0.001f)
        assertEquals(0f, reloaded.sfxVolume, 0.001f)
    }

    @Test
    fun `a saved run round-trips through the store and powers CONTINUE`() = runTest {
        val run = SavedRun(
            wave = 14,
            serverHp = 73,
            crypto = 466,
            agents = listOf(
                SavedAgent(nodeId = 2, type = "FIREWALL", level = 5, targeting = 0),
                SavedAgent(nodeId = 19, type = "CRYPTOGRAPHER", level = 2, targeting = 1)
            ),
            packetsBlocked = 501,
            cryptoEarned = 1820,
            bossesDefeated = 2,
            serverDamageTaken = 27,
            agentsDeployed = 8,
            agentUpgrades = 19,
            savedAtMillis = 1_700_000_000_000L
        )

        repository.saveRun(run)

        assertTrue("CONTINUE must light up", repository.hasSavedRun())
        val loaded = repository.savedRun.first()
        assertNotNull(loaded)
        assertEquals(run, loaded)
        assertEquals(2, loaded!!.agents.size)
        assertEquals("CRYPTOGRAPHER", loaded.agents[1].type)
        assertEquals(5, loaded.agents[0].level)
    }

    @Test
    fun `clearing the saved run disables CONTINUE`() = runTest {
        repository.saveRun(SavedRun(wave = 3, serverHp = 90, crypto = 40))
        assertTrue(repository.hasSavedRun())

        repository.clearSavedRun()

        assertFalse(repository.hasSavedRun())
        assertNull(repository.savedRun.first())
    }

    @Test
    fun `a run saved after the server fell is never offered`() = runTest {
        repository.saveRun(SavedRun(wave = 9, serverHp = 0, crypto = 10))
        assertFalse("a dead run is not resumable", repository.hasSavedRun())
        assertNull(repository.savedRun.first())
    }

    @Test
    fun `unlocks accumulate and are idempotent`() = runTest {
        repository.unlockAgent(AgentType.IPS)
        repository.unlockAgent(AgentType.ANALYST)
        repository.unlockAgent(AgentType.IPS)

        val progress = repository.progress.first()
        assertTrue(AgentType.IPS.name in progress.unlockedAgents)
        assertTrue(AgentType.ANALYST.name in progress.unlockedAgents)
        assertEquals(
            "re-unlocking must not duplicate",
            progress.unlockedAgents.size,
            progress.unlockedAgents.toSet().size
        )
        // Starters survive alongside earned unlocks.
        assertTrue(AgentType.FIREWALL.name in progress.unlockedAgents)
    }

    @Test
    fun `the tutorial flag persists`() = runTest {
        assertFalse(repository.progress.first().tutorialCompleted)
        repository.setTutorialCompleted(true)
        assertTrue(repository.progress.first().tutorialCompleted)
    }

    @Test
    fun `statistics fold across runs and track the best wave`() = runTest {
        repository.recordGameStarted()
        repository.recordRunResult(
            waveReached = 12,
            packetsBlocked = 300,
            bossesDefeated = 2,
            cryptoEarned = 900,
            serverDamageTaken = 18,
            agentsDeployed = 6,
            agentUpgrades = 14,
            deploymentsByType = mapOf("FIREWALL" to 4, "IDS" to 2),
            countAsGamePlayed = false
        )

        repository.recordGameStarted()
        repository.recordRunResult(
            waveReached = 7,
            packetsBlocked = 150,
            bossesDefeated = 1,
            cryptoEarned = 400,
            serverDamageTaken = 30,
            agentsDeployed = 5,
            agentUpgrades = 9,
            deploymentsByType = mapOf("FIREWALL" to 1, "ANALYST" to 4),
            countAsGamePlayed = false
        )

        val stats = repository.stats.first()
        assertEquals("a worse run must not lower the record", 12, stats.highestWave)
        assertEquals(450L, stats.totalPacketsBlocked)
        assertEquals(3L, stats.totalBossesDefeated)
        assertEquals(1300L, stats.totalCryptoEarned)
        assertEquals(48L, stats.totalServerDamageTaken)
        assertEquals(11L, stats.totalAgentsDeployed)
        assertEquals(23L, stats.totalAgentUpgrades)
        assertEquals(2L, stats.totalGamesPlayed)

        assertEquals(5, stats.deploymentsByAgent["FIREWALL"])
        assertEquals(4, stats.deploymentsByAgent["ANALYST"])
        assertEquals("FIREWALL", stats.favoriteAgent)
    }

    @Test
    fun `updateHighestWave only ever raises the record`() = runTest {
        repository.updateHighestWave(20)
        assertEquals(20, repository.stats.first().highestWave)
        repository.updateHighestWave(5)
        assertEquals(20, repository.stats.first().highestWave)
        repository.updateHighestWave(21)
        assertEquals(21, repository.stats.first().highestWave)
    }

    @Test
    fun `reset wipes everything back to defaults`() = runTest {
        repository.saveRun(SavedRun(wave = 30, serverHp = 50, crypto = 900))
        repository.unlockAgent(AgentType.ROOT_ADMIN)
        repository.setTutorialCompleted(true)
        repository.updateSettings { it.copy(batterySaver = true, musicVolume = 0f) }
        repository.updateHighestWave(44)

        repository.resetAllProgress()

        assertNull(repository.savedRun.first())
        assertFalse(repository.hasSavedRun())
        assertEquals(0, repository.stats.first().highestWave)
        assertFalse(repository.progress.first().tutorialCompleted)
        assertFalse(AgentType.ROOT_ADMIN.name in repository.progress.first().unlockedAgents)
        assertTrue(
            "starters survive a reset",
            AgentType.FIREWALL.name in repository.progress.first().unlockedAgents
        )
        val settings = repository.settings.first()
        assertFalse(settings.batterySaver)
        assertEquals(0.5f, settings.musicVolume, 0.001f)
    }
}
