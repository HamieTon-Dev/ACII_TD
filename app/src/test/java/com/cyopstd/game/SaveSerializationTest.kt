package com.cyopstd.game

import com.cyopstd.game.core.WorldGeometry
import com.cyopstd.game.engine.GameEngine
import com.cyopstd.game.model.AgentType
import com.cyopstd.game.save.SavedAgent
import com.cyopstd.game.save.SavedRun
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The save format is the one thing a player can lose real progress to, so it is
 * tested directly: it must round-trip exactly, tolerate fields it has never seen
 * (forward compatibility), and fill in fields that are missing (backward
 * compatibility).
 */
class SaveSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    @Test
    fun `a saved run round-trips without loss`() {
        val original = SavedRun(
            wave = 23,
            serverHp = 61,
            crypto = 742,
            agents = listOf(
                SavedAgent(nodeId = 3, type = "FIREWALL", level = 7, targeting = 0),
                SavedAgent(nodeId = 18, type = "AI_SENTINEL", level = 4, targeting = 2)
            ),
            attacksBlocked = 812,
            cryptoEarned = 3310,
            bossesDefeated = 4,
            serverDamageTaken = 39,
            agentsDeployed = 11,
            agentUpgrades = 27,
            savedAtMillis = 1_700_000_000_000L
        )

        val decoded = json.decodeFromString(
            SavedRun.serializer(),
            json.encodeToString(SavedRun.serializer(), original)
        )

        assertEquals(original, decoded)
        assertEquals(2, decoded.agents.size)
        assertEquals("AI_SENTINEL", decoded.agents[1].type)
        assertEquals(2, decoded.agents[1].targeting)
    }

    @Test
    fun `a save written by an older build still decodes`() {
        // Every field has a default, so an older payload missing newer keys is
        // not an error - it simply takes the defaults.
        val legacy = """{"wave":4,"serverHp":88,"crypto":120}"""
        val decoded = json.decodeFromString(SavedRun.serializer(), legacy)

        assertEquals(4, decoded.wave)
        assertEquals(88, decoded.serverHp)
        assertEquals(120, decoded.crypto)
        assertTrue(decoded.agents.isEmpty())
        assertEquals(0, decoded.bossesDefeated)
        assertTrue(decoded.isResumable)
    }

    @Test
    fun `a save written by a newer build still decodes`() {
        val futuristic = """
            {"wave":9,"serverHp":40,"crypto":55,"mapId":"branching-v2","weather":"rain"}
        """.trimIndent()
        val decoded = json.decodeFromString(SavedRun.serializer(), futuristic)

        assertEquals(9, decoded.wave)
        assertEquals(40, decoded.serverHp)
    }

    @Test
    fun `a dead run is not offered as resumable`() {
        assertFalse(SavedRun(wave = 12, serverHp = 0).isResumable)
        assertTrue(SavedRun(wave = 12, serverHp = 1).isResumable)
    }

    @Test
    fun `malformed json is rejected rather than silently accepted`() {
        val broken = """{"wave":"not a number"""
        var threw = false
        try {
            json.decodeFromString(SavedRun.serializer(), broken)
        } catch (expected: Exception) {
            threw = true
        }
        assertTrue(
            "corrupt payloads must throw so the repository can discard them",
            threw
        )
    }
    @Test
    fun `a save naming an agent that no longer exists still loads`() {
        // The SANDBOX was removed in 1.9.1. Anyone mid-run with one deployed
        // has it written into their save by name, and a save that referred to
        // a agent the build no longer has must not take the run down with it.
        val engine = GameEngine()
        engine.startNewRun()
        engine.restore(
            wave = 9,
            serverHp = 72,
            crypto = 400,
            placements = listOf(
                GameEngine.SavedPlacement(
                    nodeId = WorldGeometry.nodesByCoverage[0].id,
                    agentTypeName = "FIREWALL",
                    level = 4,
                    targetingOrdinal = 0
                ),
                GameEngine.SavedPlacement(
                    nodeId = WorldGeometry.nodesByCoverage[1].id,
                    agentTypeName = "SANDBOX",
                    level = 7,
                    targetingOrdinal = 0
                ),
                GameEngine.SavedPlacement(
                    nodeId = WorldGeometry.nodesByCoverage[2].id,
                    agentTypeName = "TARPIT",
                    level = 2,
                    targetingOrdinal = 0
                )
            ),
            attacksBlocked = 10,
            cryptoEarned = 50,
            bossesDefeated = 1,
            serverDamageTaken = 28,
            agentsDeployed = 3,
            agentUpgrades = 11
        )

        // The run survives, the wave and integrity are intact, and the towers
        // that still exist are placed. The retired one is simply dropped.
        assertEquals(9, engine.currentWave)
        assertEquals(72, engine.serverHp)
        assertEquals(
            "the removed agent should be skipped, not placed or crashed on",
            2, engine.activeAgentCount()
        )
        assertEquals(
            AgentType.FIREWALL,
            engine.agentAt(WorldGeometry.nodesByCoverage[0].id)?.type
        )
        assertEquals(
            "the spot the removed agent held should be free",
            null, engine.agentAt(WorldGeometry.nodesByCoverage[1].id)
        )
        assertEquals(
            AgentType.TARPIT,
            engine.agentAt(WorldGeometry.nodesByCoverage[2].id)?.type
        )
    }

    @Test
    fun `an unlock list naming a removed agent is harmless`() {
        // Unlocks are stored by name too. A stale entry must neither crash nor
        // unlock something else by shifting an index.
        val stale = setOf("FIREWALL", "SANDBOX", "IPS")
        val resolved = stale.mapNotNull { AgentType.fromNameSafe(it) }
        assertEquals(2, resolved.size)
        assertTrue(AgentType.FIREWALL in resolved)
        assertTrue(AgentType.IPS in resolved)
    }

}
