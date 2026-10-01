package com.cyopstd.game

import com.cyopstd.game.core.Balance
import com.cyopstd.game.core.Maps
import com.cyopstd.game.engine.GameEngine
import com.cyopstd.game.engine.PlacementResult
import com.cyopstd.game.engine.SpawnOrder
import com.cyopstd.game.engine.WaveGenerator
import com.cyopstd.game.model.AgentType
import com.cyopstd.game.model.BossVariant
import com.cyopstd.game.model.EnemyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * ANTI DUCK USB `[<(o]` (backlog ♡2): earned by wave 100 on DUCK-USB, 500◇,
 * stronger than the hats, two maximum, ×3 against DUCK-USB's bosses.
 */
class AntiDuckTest {

    private val duck = AgentType.ANTI_DUCK

    @Test
    fun `matches the request`() {
        assertEquals("[<(o]", duck.renderedGlyph(1))
        assertEquals(500, duck.cost)
        assertEquals(100, duck.unlockWave)
        assertEquals(Maps.DUCK_USB.id, duck.unlockMapId)
        assertEquals("Unlock this agent by reaching wave 100 on DUCK-USB.", duck.unlockRequirement)
        assertEquals(2, duck.maxDeployed)
        assertFalse("the hats stay the only boss-first agents", duck.alwaysPrioritisesBosses)
        val glyphs = AgentType.entries.map { it.glyph }
        assertEquals(glyphs.size, glyphs.toSet().size)
    }

    @Test
    fun `is stronger than the hats`() {
        for (hat in listOf(AgentType.REDHAT, AgentType.BLUEHAT)) {
            assertTrue(duck.baseDamage * duck.baseFireRate > hat.baseDamage * hat.baseFireRate)
            assertTrue(duck.baseRange >= hat.baseRange)
        }
    }

    @Test
    fun `only wave 100 on DUCK-USB earns it`() {
        assertFalse(duck in AgentType.earnedBy(500, 500, mapOf("perimeter" to 500, "duck_usb" to 99)))
        assertTrue(duck in AgentType.earnedBy(0, 0, mapOf("duck_usb" to 100)))
    }

    @Test
    fun `hits SYN-STORM and GRADIENT three times as hard, and nothing else`() {
        val random = Random(1)
        val engine = GameEngine(random, WaveGenerator(random)).apply {
            isAgentUnlocked = { true }
            selectMap(Maps.DUCK_USB)
            startNewRun()
        }
        for (variant in BossVariant.entries) {
            engine.enemySystem().spawn(SpawnOrder(0f, EnemyType.BOSS, 0, false, true, bossVariant = variant), 50)
            val boss = engine.enemies.items.last { it.active }
            val ratio = engine.projectileSystem().damageMultiplier(boss, duck) /
                engine.projectileSystem().damageMultiplier(boss, AgentType.FIREWALL) *
                (variant.bonusDamageFrom[AgentType.FIREWALL.name] ?: 1f)
            val expected = if (variant == BossVariant.SYN_STORM || variant == BossVariant.GRADIENT) {
                Balance.ANTI_DUCK_MULTIPLIER
            } else {
                1f
            }
            assertEquals(variant.name, expected, ratio, 0.01f)
            boss.active = false
        }
    }

    @Test
    fun `no more than two on the board`() {
        val random = Random(1)
        val engine = GameEngine(random, WaveGenerator(random)).apply {
            isAgentUnlocked = { true }
            selectMap(Maps.DUCK_USB)
            startNewRun()
            addCrypto(1_000_000, countAsEarned = false)
        }
        val results = Maps.DUCK_USB.nodes.filter { !it.serverSlot }.take(3).map { engine.placeAgent(duck, it.id) }
        assertEquals(PlacementResult.SUCCESS, results[0])
        assertEquals(PlacementResult.SUCCESS, results[1])
        assertTrue(results[2] != PlacementResult.SUCCESS)
    }
}
