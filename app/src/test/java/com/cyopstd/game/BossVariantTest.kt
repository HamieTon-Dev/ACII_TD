package com.cyopstd.game

import com.cyopstd.game.engine.GameEngine
import com.cyopstd.game.model.AgentType
import com.cyopstd.game.model.BossVariant
import com.cyopstd.game.model.EnemyType
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bosses with identities.
 *
 * Until now every boss in the game was the same opponent — `[!!!]` — with a
 * different set of modifiers rolled onto it. Modifiers make a boss harder;
 * they do not make it a different fight, and a player at wave 60 had met the
 * same one a dozen times.
 */
class BossVariantTest {

    @Test
    fun `every variant is distinguishable at a glance`() {
        val glyphs = BossVariant.entries.map { it.glyph }
        assertEquals("two bosses share a glyph", glyphs.size, glyphs.toSet().size)
        val ids = BossVariant.entries.map { it.id }
        assertEquals("two bosses share an id", ids.size, ids.toSet().size)
        for (variant in BossVariant.entries) {
            assertTrue("${variant.id} has no signature line", variant.signature.length > 20)
        }
    }

    @Test
    fun `the first boss anyone meets is the plain one`() {
        // Cycle 1 is a clean fight with no modifiers by design; it should be a
        // clean fight against a plain opponent too.
        assertEquals(listOf(BossVariant.BREACH), BossVariant.poolForCycle(1))
    }

    @Test
    fun `new opponents arrive one at a time`() {
        var previous = 0
        for (cycle in 1..8) {
            val size = BossVariant.poolForCycle(cycle).size
            assertTrue("the pool shrank at cycle $cycle", size >= previous)
            assertTrue("more than one new boss arrived at cycle $cycle", size - previous <= 1)
            previous = size
        }
    }

    @Test
    fun `a saved id survives a round trip, and an unknown one is not fatal`() {
        for (variant in BossVariant.entries) {
            assertEquals(variant, BossVariant.fromIdSafe(variant.id))
        }
        assertEquals(BossVariant.BREACH, BossVariant.fromIdSafe("a-boss-from-a-later-build"))
        assertEquals(BossVariant.BREACH, BossVariant.fromIdSafe(null))
    }

    // ------------------------------------------------------------ in a fight

    private fun bossOnWave(wave: Int, seed: Int): GameEngine {
        val engine = GameEngine(random = Random(seed))
        engine.startNewRun()
        engine.restore(
            wave = wave - 1, serverHp = 100, crypto = 0, placements = emptyList(),
            attacksBlocked = 0, cryptoEarned = 0, bossesDefeated = 0,
            serverDamageTaken = 0, agentsDeployed = 0, agentUpgrades = 0
        )
        engine.startNextWave()
        repeat(900) { engine.update(1f / 60f, 1f) }
        return engine
    }

    @Test
    fun `a boss wave brings a named opponent`() {
        val engine = bossOnWave(5, seed = 7)
        val boss = engine.enemies.items.firstOrNull { it.active && it.isBoss }
        assertTrue("no boss was spawned on a boss wave", boss != null)
        assertEquals(
            "the boss on the field should be the one the banner announced",
            engine.activeBossVariant,
            boss!!.variant
        )
    }

    @Test
    fun `the roster actually varies once it is unlocked`() {
        // Over enough seeds a later cycle must produce more than one opponent,
        // or the variants exist on paper only.
        val seen = (1..40).map { bossOnWave(20, seed = it).activeBossVariant }.toSet()
        assertTrue("wave 20 only ever produced $seen", seen.size > 1)
    }

    @Test
    fun `a boss draws its own mark, not the type's`() {
        val engine = bossOnWave(15, seed = 3)
        val boss = engine.enemies.items.first { it.active && it.isBoss }
        assertEquals(boss.variant.glyph, boss.renderedGlyph())
        // An ordinary threat is unaffected by any of this.
        val ordinary = engine.enemies.items.firstOrNull { it.active && !it.isBoss }
        if (ordinary != null) assertEquals(ordinary.type.glyph, ordinary.renderedGlyph())
    }

    // --------------------------------------------------------------- ZOMBIE

    @Test
    fun `a zombie gets back up exactly once`() {
        val engine = GameEngine()
        engine.startNewRun()
        val zombie = engine.enemies.obtain()!!
        zombie.reset()
        zombie.active = true
        zombie.isBoss = true
        zombie.variant = BossVariant.ZOMBIE
        zombie.maxHealth = 1_000f
        zombie.health = 1f

        // First killing blow: cancelled.
        engine.projectileSystem().applyDamage(
            enemy = zombie,
            rawDamage = 5_000f,
            sourceType = AgentType.FIREWALL,
            ignoresArmor = true,
            heavy = false
        )
        assertTrue("a zombie should come back", zombie.health > 0f)
        assertEquals(
            1_000f * BossVariant.ZOMBIE_REVIVE_FRACTION,
            zombie.health,
            1f
        )

        // Second: it stays down.
        engine.projectileSystem().applyDamage(
            enemy = zombie,
            rawDamage = 5_000f,
            sourceType = AgentType.FIREWALL,
            ignoresArmor = true,
            heavy = false
        )
        assertEquals("a zombie only comes back once", 0f, zombie.health, 0.001f)
    }

    @Test
    fun `only a zombie comes back`() {
        for (variant in BossVariant.entries) {
            val engine = GameEngine()
            engine.startNewRun()
            val boss = engine.enemies.obtain()!!
            boss.reset()
            boss.active = true
            boss.isBoss = true
            boss.variant = variant
            boss.maxHealth = 100f
            boss.health = 100f

            val revived = boss.tryRevive()
            assertEquals(
                "${variant.id} should ${if (variant == BossVariant.ZOMBIE) "" else "not "}revive",
                variant == BossVariant.ZOMBIE,
                revived
            )
        }
    }

    @Test
    fun `an ordinary threat never revives`() {
        val engine = GameEngine()
        engine.startNewRun()
        engine.enemySystem().spawnEscort(EnemyType.BOT, 0, 100f, 3)
        val bot = engine.enemies.items.first { it.active }
        // Even handed the flag, a thing that is not a boss stays dead.
        bot.variant = BossVariant.ZOMBIE
        assertFalse("only bosses come back", bot.tryRevive())
    }

    // ----------------------------------------------------------- the weights

    @Test
    fun `GG is the wall and ZZ is the quick one`() {
        // The identities have to be legible in the numbers, or they are only
        // glyphs.
        assertTrue(BossVariant.GOOD_GAME.healthScale > BossVariant.BREACH.healthScale)
        assertTrue(BossVariant.GOOD_GAME.armorBonus > BossVariant.BREACH.armorBonus)
        assertTrue(BossVariant.GOOD_GAME.speedScale < BossVariant.BREACH.speedScale)

        assertTrue(BossVariant.ZOMBIE.speedScale > BossVariant.BREACH.speedScale)
        assertTrue(
            "a boss that comes back should not also be the toughest",
            BossVariant.ZOMBIE.healthScale < BossVariant.GOOD_GAME.healthScale
        )
        assertNotEquals(BossVariant.GOOD_GAME.glyph, BossVariant.ZOMBIE.glyph)
    }

    @Test
    fun `a variant's weighting reaches the boss on the field`() {
        // Found by playing real boss waves rather than by a test-only spawn
        // hook: what matters is that the weighting survives the path the game
        // actually takes, configure() included.
        val bosses = HashMap<BossVariant, Float>()
        val armour = HashMap<BossVariant, Float>()
        val speed = HashMap<BossVariant, Float>()
        for (seed in 1..60) {
            val engine = bossOnWave(20, seed)
            val boss = engine.enemies.items.firstOrNull { it.active && it.isBoss } ?: continue
            bosses.putIfAbsent(boss.variant, boss.maxHealth)
            armour.putIfAbsent(boss.variant, boss.armor)
            speed.putIfAbsent(boss.variant, boss.baseSpeed)
        }

        val wall = bosses[BossVariant.GOOD_GAME]
        val plain = bosses[BossVariant.BREACH]
        assertTrue("never saw a GG or a BREACH in 60 seeds: ${bosses.keys}", wall != null && plain != null)
        assertTrue("GG ($wall) should outweigh BREACH ($plain)", wall!! > plain!!)
        assertTrue(armour[BossVariant.GOOD_GAME]!! > armour[BossVariant.BREACH]!!)
        assertTrue(speed[BossVariant.GOOD_GAME]!! < speed[BossVariant.BREACH]!!)
    }
}
