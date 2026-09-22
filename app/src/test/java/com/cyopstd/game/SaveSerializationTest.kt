package com.cyopstd.game

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
}
