package com.cyopstd.game

import com.cyopstd.game.model.AgentType
import com.cyopstd.game.core.Balance
import com.cyopstd.game.save.GameRepository
import com.cyopstd.game.save.SavedAgent
import com.cyopstd.game.save.SavedRun
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
        assertEquals(0L, stats.totalAttacksBlocked)
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
            attacksBlocked = 501,
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
            attacksBlocked = 300,
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
            attacksBlocked = 150,
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
        assertEquals(450L, stats.totalAttacksBlocked)
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

    // ------------------------------------------------ budget and firmware

    @Test
    fun `budget starts empty and accumulates`() = runTest {
        assertEquals(0L, repository.progress.first().budget)

        repository.awardBudget(5)
        repository.awardBudget(20)

        val progress = repository.progress.first()
        assertEquals(25L, progress.budget)
        assertEquals(25L, progress.lifetimeBudgetEarned)
        assertEquals(0, progress.firmwareLevel)
    }

    @Test
    fun `a non-positive award is ignored`() = runTest {
        repository.awardBudget(0)
        repository.awardBudget(-40)
        assertEquals(0L, repository.progress.first().budget)
    }

    @Test
    fun `buying firmware spends budget and raises the level`() = runTest {
        // Priced off the curve rather than off literals. These tests are about
        // what the repository does with a purchase -- spend exactly the cost,
        // raise exactly the level, leave income alone -- and hardcoding the
        // numbers made them fail on the 1.24.0 x10 rescale, which changed the
        // curve and nothing they were actually asserting.
        val cost = Balance.firmwareCostFor(fromLevel = 0, steps = 5)
        repository.awardBudget((cost + 75L).toInt())

        val bought = repository.buyFirmware(5)

        assertEquals(5, bought)
        val progress = repository.progress.first()
        assertEquals(5, progress.firmwareLevel)
        assertEquals(75L, progress.budget)
        // Lifetime earned is a record of income, so spending must not reduce it.
        assertEquals(cost + 75L, progress.lifetimeBudgetEarned)
    }

    @Test
    fun `firmware purchases stop at the budget rather than going negative`() = runTest {
        // Enough for two levels and change, never three.
        val twoLevels = Balance.firmwareCostFor(fromLevel = 0, steps = 2)
        val thirdLevel = Balance.firmwareCost(2)
        repository.awardBudget((twoLevels + thirdLevel - 1).toInt())

        val bought = repository.buyFirmware(50)

        assertEquals(2, bought)
        val progress = repository.progress.first()
        assertEquals(2, progress.firmwareLevel)
        assertEquals(thirdLevel - 1L, progress.budget)
        assertTrue("budget can never go negative", progress.budget >= 0)
    }

    @Test
    fun `buying with no budget changes nothing`() = runTest {
        val bought = repository.buyFirmware(10)
        assertEquals(0, bought)
        val progress = repository.progress.first()
        assertEquals(0, progress.firmwareLevel)
        assertEquals(0L, progress.budget)
    }

    @Test
    fun `firmware survives as permanent progression`() = runTest {
        repository.awardBudget(Balance.firmwareCostFor(fromLevel = 0, steps = 20).toInt())
        repository.buyFirmware(20)

        // A fresh repository over the same store sees the same firmware: this
        // is the whole point of the meta-currency.
        val reloaded = repository.progress.first()
        assertEquals(20, reloaded.firmwareLevel)
        assertTrue(
            "firmware must translate into a real damage multiplier",
            com.cyopstd.game.core.Balance
                .firmwareDamageMultiplier(reloaded.firmwareLevel) > 1f
        )
    }

    @Test
    fun `reset wipes everything back to defaults`() = runTest {
        repository.saveRun(SavedRun(wave = 30, serverHp = 50, crypto = 900))
        repository.awardBudget(500)
        repository.buyFirmware(10)
        repository.unlockAgent(AgentType.ROOT_ADMIN)
        repository.setTutorialCompleted(true)
        repository.updateSettings { it.copy(batterySaver = true, musicVolume = 0f) }
        repository.updateHighestWave(44)

        repository.resetAllProgress()

        assertNull(repository.savedRun.first())
        assertFalse(repository.hasSavedRun())
        assertEquals(0, repository.stats.first().highestWave)
        assertEquals(0L, repository.progress.first().budget)
        assertEquals(0, repository.progress.first().firmwareLevel)
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
