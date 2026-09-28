package com.cyopstd.game

import com.cyopstd.game.core.Balance
import com.cyopstd.game.core.Maps
import com.cyopstd.game.engine.GameEngine
import com.cyopstd.game.engine.WaveGenerator
import com.cyopstd.game.model.AgentFirmware
import com.cyopstd.game.model.AgentType
import com.cyopstd.game.model.FirmwareStat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random

/**
 * AGENT FIRMWARE (owner, 2026-09-28): permanent per-agent upgrades to damage,
 * fire rate and range, each bought separately with €, cheap at first and
 * growing to very expensive by level 10,000. Never for SERVER SYSTEMS ENGINEER.
 *
 * (Asked for as HP, damage and range. Agents have no HP in this game --
 * nothing ever damages them -- so the third track is fire rate.)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AgentFirmwareTest {

    @Test
    fun `the cost curve starts cheap and ends very expensive`() {
        assertEquals(20L, Balance.agentFirmwareCost(0))
        for (l in 1 until Balance.MAX_AGENT_FIRMWARE_LEVEL) {
            assertTrue("level $l is not dearer than ${l - 1}",
                Balance.agentFirmwareCost(l) >= Balance.agentFirmwareCost(l - 1))
        }
        val first100 = Balance.agentFirmwareCostFor(0, 100)
        val first1000 = Balance.agentFirmwareCostFor(0, 1_000)
        val all = Balance.agentFirmwareCostFor(0, Balance.MAX_AGENT_FIRMWARE_LEVEL)
        val last = Balance.agentFirmwareCost(Balance.MAX_AGENT_FIRMWARE_LEVEL - 1)
        println("agent firmware: first 100 = $first100 €, first 1000 = $first1000 €, " +
            "level 5000 = ${Balance.agentFirmwareCost(4_999)} €, level 10000 = $last €, all = $all €")
        // A run to wave 100 pays about 19 thousand €: a real first step.
        assertTrue(first100 in 20_000L..40_000L)
        // A thousand levels is a long-term goal...
        assertTrue(first1000 in 2_000_000L..5_000_000L)
        // ...and the top is out of reach: one level costs millions.
        assertTrue(last > 2_000_000L)
        assertTrue(all > 1_000_000_000L)
        assertTrue("exponential, not linear", last > Balance.agentFirmwareCost(0) * 100_000)
    }

    @Test
    fun `each stat is its own track, and the engineer is never sold it`() {
        assertFalse(AgentType.SERVER_SYSTEMS_ENGINEER in AgentFirmware.eligible)
        assertEquals(AgentType.catalog.size - 1, AgentFirmware.eligible.size)
        val f = AgentFirmware().with(FirmwareStat.DAMAGE, 100).with(FirmwareStat.RANGE, 50)
        assertEquals(100, f.level(FirmwareStat.DAMAGE))
        assertEquals(0, f.level(FirmwareStat.RATE))
        assertEquals(50, f.level(FirmwareStat.RANGE))
        assertEquals(1.5f, f.damageMultiplier, 1e-4f)
        assertEquals(1.0f, f.rateMultiplier, 1e-4f)
        assertEquals(1.01f, f.rangeMultiplier, 1e-4f)
    }

    @Test
    fun `buying spends the right amount and raises only that stat of that agent`() = runBlocking {
        val repository = TestStores.isolatedRepository()
        repository.awardBudget(100_000)
        val before = repository.progress.first().budget
        assertEquals(10, repository.buyAgentFirmware(AgentType.FIREWALL, FirmwareStat.DAMAGE, 10))
        assertEquals(3, repository.buyAgentFirmware(AgentType.FIREWALL, FirmwareStat.RANGE, 3))
        val progress = repository.progress.first()
        assertEquals(
            before - Balance.agentFirmwareCostFor(0, 10) - Balance.agentFirmwareCostFor(0, 3),
            progress.budget
        )
        val firewall = progress.agentFirmware.getValue(AgentType.FIREWALL.name)
        assertEquals(AgentFirmware(damage = 10, rate = 0, range = 3), firewall)
        assertFalse(AgentType.IDS.name in progress.agentFirmware)

        // Never for [S]; never more than the € allows.
        assertEquals(0, repository.buyAgentFirmware(AgentType.SERVER_SYSTEMS_ENGINEER, FirmwareStat.DAMAGE, 5))
        val left = repository.progress.first().budget
        val bought = repository.buyAgentFirmware(AgentType.IDS, FirmwareStat.RATE, 100_000)
        assertEquals(Balance.agentFirmwareLevelsAffordable(0, left), bought)
        assertTrue(repository.progress.first().budget >= 0)
    }

    @Test
    fun `agents on the board use their type's firmware, and a purchase applies at once`() {
        val engine = GameEngine(Random(1), WaveGenerator(Random(1)))
        engine.isAgentUnlocked = { true }
        engine.startNewRun()
        engine.addCrypto(10_000, countAsEarned = false)
        val node = Maps.PERIMETER.nodesByCoverage[0].id
        engine.placeAgent(AgentType.FIREWALL, node)
        val agent = engine.agentAt(node)!!
        val damage = agent.effectiveDamage()
        val cooldown = agent.effectiveCooldown()
        val range = agent.range()

        engine.agentFirmware = mapOf(
            AgentType.FIREWALL to AgentFirmware(damage = 200, rate = 400, range = 500),
            AgentType.SERVER_SYSTEMS_ENGINEER to AgentFirmware(damage = 9_999)
        )
        assertEquals(damage * 2f, agent.effectiveDamage(), 1e-3f)
        assertEquals(cooldown / 1.2f, agent.effectiveCooldown(), 1e-4f)
        assertEquals(range * 1.1f, agent.range(), 1e-3f)

        // A new one gets it too, and an engineer never does.
        val second = Maps.PERIMETER.nodesByCoverage[1].id
        engine.placeAgent(AgentType.FIREWALL, second)
        assertEquals(damage * 2f, engine.agentAt(second)!!.effectiveDamage(), 1e-3f)
        val slot = Maps.PERIMETER.serverSlots[0].id
        engine.placeAgent(AgentType.SERVER_SYSTEMS_ENGINEER, slot)
        assertEquals(1f, engine.agentAt(slot)!!.firmwareDamage, 0f)
    }

    @Test
    fun `it survives a cloud restore with the rest of the wallet`() = runBlocking {
        val source = TestStores.isolatedRepository()
        source.awardBudget(50_000)
        source.buyAgentFirmware(AgentType.ANALYST, FirmwareStat.DAMAGE, 7)
        val save = source.exportCloudSave("test")
        assertEquals(7, save.progress.agentFirmware.getValue(AgentType.ANALYST.name).damage)

        val target = TestStores.isolatedRepository()
        target.importCloudSave(save)
        assertEquals(7, target.progress.first().agentFirmware.getValue(AgentType.ANALYST.name).damage)
        assertEquals(save.progress.budget, target.progress.first().budget)
    }
}
