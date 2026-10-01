package com.cyopstd.game

import com.cyopstd.game.core.Balance
import com.cyopstd.game.core.GameMap
import com.cyopstd.game.core.Maps
import com.cyopstd.game.engine.GameEngine
import com.cyopstd.game.engine.SpawnOrder
import com.cyopstd.game.engine.WaveGenerator
import com.cyopstd.game.model.AgentType
import com.cyopstd.game.model.BossVariant
import com.cyopstd.game.model.Enemy
import com.cyopstd.game.model.EnemyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * ♡5: one boss per chapter-two level, the owner's picks. BOTMASTER (TRIDENT),
 * ROOTKIT (SPIRAL), WORM (ZIGZAG), SPOOFER (HELIX) and KERNEL PANIC (TRIPLE
 * BRAID), plus the balance probe the owner asked for on SPOOFER.
 */
class ChapterTwoBossTest {

    @Test
    fun `each chapter-two level has its own boss, and leads its boss waves with it`() {
        val own = mapOf(
            Maps.TRIDENT to BossVariant.BOTMASTER,
            Maps.SPIRAL to BossVariant.ROOTKIT,
            Maps.ZIGZAG to BossVariant.WORM,
            Maps.HELIX to BossVariant.SPOOFER,
            Maps.BRAID to BossVariant.KERNEL_PANIC
        )
        for ((map, variant) in own) {
            assertEquals(map.id, variant.mapId)
            assertEquals(variant, BossVariant.ownBossOf(map.id))
            assertFalse(variant.isChapterOne)
            val generator = WaveGenerator(Random(3)).also { it.mapId = map.id }
            val plan = generator.generate(20)
            assertEquals(variant, plan.orders.first { it.boss }.bossVariant)
        }
        // The first five levels keep their random pick.
        assertNull(BossVariant.ownBossOf(Maps.DDOS.id))
        // A level does not field a later level's boss.
        assertFalse(BossVariant.WORM in BossVariant.poolFor(40, Maps.SPIRAL.id))
        assertTrue(BossVariant.BOTMASTER in BossVariant.poolFor(40, Maps.BRAID.id))
    }

    private fun bossOf(map: GameMap, variant: BossVariant): Pair<GameEngine, Enemy> {
        val engine = GameEngine()
        engine.isAgentUnlocked = { true }
        engine.selectMap(map)
        engine.startNewRun()
        engine.enemySystem().spawn(
            SpawnOrder(0f, EnemyType.BOSS, 0, elite = false, boss = true, bossVariant = variant), 30
        )
        val boss = engine.enemies.items.first { it.active }
        boss.baseSpeed = 0f
        boss.progress = map.laneLength[0] * 0.4f
        return engine to boss
    }

    @Test
    fun `BOTMASTER drops BOTs that are not part of the wave`() {
        val (engine, _) = bossOf(Maps.TRIDENT, BossVariant.BOTMASTER)
        val remaining = engine.enemiesRemaining
        repeat(((BossVariant.BOTMASTER_INTERVAL + 0.1f) / 0.02f).toInt()) { engine.enemySystem().update(0.02f) }
        val bots = engine.enemies.items.filter { it.active && it.type == EnemyType.BOT }
        assertEquals(BossVariant.BOTMASTER_DROP, bots.size)
        assertTrue(bots.all { engine.enemySystem().isEscort(it) })
        assertEquals(remaining, engine.enemiesRemaining)
    }

    @Test
    fun `ROOTKIT cannot be targeted while hidden, but splash still reaches it`() {
        val (engine, boss) = bossOf(Maps.SPIRAL, BossVariant.ROOTKIT)
        val node = Maps.SPIRAL.nodesByCoverage.first()
        engine.addCrypto(100_000, countAsEarned = false)
        engine.placeAgent(AgentType.ROOT_ADMIN, node.id)
        val agent = engine.agentAt(node.id)!!
        boss.x = agent.x + 20f
        boss.y = agent.y

        var hiddenSeconds = 0f
        var t = 0f
        while (t < BossVariant.ROOTKIT_CYCLE) {
            engine.enemySystem().update(0.02f)
            boss.x = agent.x + 20f
            boss.y = agent.y
            if (boss.hidden) {
                hiddenSeconds += 0.02f
                assertNull(engine.combatSystem().selectTarget(agent))
            } else {
                assertEquals(boss, engine.combatSystem().selectTarget(agent))
            }
            t += 0.02f
        }
        assertEquals(BossVariant.ROOTKIT_HIDDEN, hiddenSeconds, 0.1f)

        // Damage still lands on it while hidden.
        boss.hidden = true
        val before = boss.health
        engine.projectileSystem().applyDamage(boss, 50f, AgentType.IPS, ignoresArmor = true, heavy = false)
        assertTrue(boss.health < before)
    }

    @Test
    fun `WORM breaks into three, those break once more, and every piece joins the wave`() {
        val (engine, boss) = bossOf(Maps.ZIGZAG, BossVariant.WORM)
        engine.addToWave()
        val remaining = engine.enemiesRemaining
        engine.projectileSystem().applyDamage(boss, boss.maxHealth * 2f, AgentType.ROOT_ADMIN, true, false)
        val pieces = engine.enemies.items.filter { it.active && it.isBoss }
        assertEquals(BossVariant.WORM_PIECES, pieces.size)
        assertTrue(pieces.all { it.wormGeneration == 1 })
        assertEquals(remaining - 1 + 3, engine.enemiesRemaining)

        for (piece in pieces) {
            engine.projectileSystem().applyDamage(piece, piece.maxHealth * 2f, AgentType.ROOT_ADMIN, true, false)
        }
        val smallest = engine.enemies.items.filter { it.active && it.isBoss }
        assertEquals(9, smallest.size)
        assertTrue(smallest.all { it.wormGeneration == 2 })
        for (piece in smallest) {
            engine.projectileSystem().applyDamage(piece, piece.maxHealth * 2f, AgentType.ROOT_ADMIN, true, false)
        }
        assertEquals("the smallest do not break", 0, engine.enemies.items.count { it.active && it.isBoss })
        assertEquals(remaining - 1, engine.enemiesRemaining)
        assertEquals("one boss, however many pieces", 1, engine.runBossesDefeated)
    }

    @Test
    fun `SPOOFER casts decoys that draw fire, do no harm, and go with it`() {
        val (engine, boss) = bossOf(Maps.HELIX, BossVariant.SPOOFER)
        val remaining = engine.enemiesRemaining
        repeat((BossVariant.SPOOFER_INTERVAL * 3 / 0.02f).toInt()) { engine.enemySystem().update(0.02f) }
        val decoys = engine.enemies.items.filter { it.active && it.decoy }
        assertEquals(BossVariant.SPOOFER_MAX_DECOYS, decoys.size)
        assertEquals(remaining, engine.enemiesRemaining)
        assertTrue(decoys.all { it.serverDamage == 0 && it.glyphMatches(boss) })

        // An ordinary agent shoots the decoy ahead of the boss; ANALYST does not.
        engine.addCrypto(100_000, countAsEarned = false)
        val node = Maps.HELIX.nodesByCoverage.first()
        engine.placeAgent(AgentType.IDS, node.id)
        val agent = engine.agentAt(node.id)!!
        for (e in listOf(boss) + decoys) { e.x = agent.x + 10f; e.y = agent.y }
        assertTrue(engine.combatSystem().selectTarget(agent)!!.decoy)
        agent.type = AgentType.ANALYST
        assertEquals(boss, engine.combatSystem().selectTarget(agent))

        // Killing a decoy pays nothing and does not move the wave.
        val crypto = engine.crypto
        engine.projectileSystem().applyDamage(decoys[0], decoys[0].maxHealth * 2f, AgentType.IDS, true, false)
        assertEquals(crypto, engine.crypto)
        assertEquals(remaining, engine.enemiesRemaining)

        engine.projectileSystem().applyDamage(boss, boss.maxHealth * 2f, AgentType.ROOT_ADMIN, true, false)
        assertEquals("its decoys go with it", 0, engine.enemies.items.count { it.active })
    }

    private fun Enemy.glyphMatches(other: Enemy) = renderedGlyph() == other.renderedGlyph()

    @Test
    fun `KERNEL PANIC jams the agents near it when it dies, but not FIREWALL`() {
        val (engine, boss) = bossOf(Maps.BRAID, BossVariant.KERNEL_PANIC)
        engine.addCrypto(100_000, countAsEarned = false)
        val nodes = Maps.BRAID.nodes.filter { !it.serverSlot }.sortedBy {
            val dx = it.x - boss.x
            val dy = it.y - boss.y
            dx * dx + dy * dy
        }
        engine.placeAgent(AgentType.IDS, nodes[0].id)
        engine.placeAgent(AgentType.FIREWALL, nodes[1].id)
        val far = nodes.last()
        engine.placeAgent(AgentType.IDS, far.id)
        engine.projectileSystem().applyDamage(boss, boss.maxHealth * 2f, AgentType.ROOT_ADMIN, true, false)
        assertEquals(BossVariant.KERNEL_PANIC_SECONDS, engine.agentAt(nodes[0].id)!!.disruptedFor, 0.01f)
        assertEquals(0f, engine.agentAt(nodes[1].id)!!.disruptedFor, 0.01f)
        assertEquals(0f, engine.agentAt(far.id)!!.disruptedFor, 0.01f)
    }

    // ------------------------------------------------------------ balance

    /** Whether the boss (every piece of it) died, how long that took, and the integrity lost meanwhile. */
    private data class Fight(val killed: Boolean, val seconds: Float, val hpLost: Int)

    /** One boss walking its level's route into a fixed board, as DuckUsbBossBalanceTest does. */
    private fun fight(map: GameMap, variant: BossVariant, board: List<AgentType>, level: Int, wave: Int = 100): Fight {
        val random = Random(9)
        val engine = GameEngine(random, WaveGenerator(random))
        engine.isAgentUnlocked = { true }
        engine.selectMap(map)
        engine.startNewRun()
        engine.addCrypto(100_000_000, countAsEarned = false)
        val nodes = map.nodesByCoverage.map { it.id }
        board.forEachIndexed { i, type ->
            engine.placeAgent(type, nodes[i])
            engine.upgradeAgent(nodes[i], level - 1)
        }
        engine.enemySystem().spawn(
            SpawnOrder(0f, EnemyType.BOSS, 0, elite = false, boss = true, bossVariant = variant), wave
        )
        engine.enemies.items.first { it.active }.let { boss ->
            boss.maxHealth /= Balance.pressureMultiplier(wave).toFloat()
            boss.health = boss.maxHealth
        }
        val startHp = engine.serverHp
        var bossesLeaked = 0
        var t = 0f
        while (t < 300f) {
            bossesLeaked += engine.enemies.items.count {
                it.active && it.isBoss && !it.decoy &&
                    it.progress + it.currentSpeed() * 0.04f >= engine.map.laneLength[it.lane]
            }
            engine.update(0.02f, 1f)
            t += 0.02f
            if (engine.enemies.items.none { it.active && it.isBoss && !it.decoy }) {
                return Fight(bossesLeaked == 0, t, startHp - engine.serverHp)
            }
        }
        return Fight(false, t, startHp - engine.serverHp)
    }

    private val mixed = listOf(
        AgentType.ROOT_ADMIN, AgentType.ZERO_DAY_HUNTER, AgentType.ANALYST, AgentType.QUANTUM_DEFENDER,
        AgentType.REDHAT, AgentType.BLUEHAT, AgentType.CRYPTOGRAPHER, AgentType.AI_SENTINEL,
        AgentType.IDS, AgentType.FIREWALL, AgentType.NETWORK_ARCHITECT, AgentType.IPS
    )

    /** No ANALYST and no ROOT ADMIN: the board SPOOFER fools completely. */
    private val fooled = listOf(
        AgentType.ZERO_DAY_HUNTER, AgentType.ZERO_DAY_HUNTER, AgentType.QUANTUM_DEFENDER, AgentType.QUANTUM_DEFENDER,
        AgentType.REDHAT, AgentType.BLUEHAT, AgentType.CRYPTOGRAPHER, AgentType.AI_SENTINEL,
        AgentType.IDS, AgentType.FIREWALL, AgentType.NETWORK_ARCHITECT, AgentType.IPS
    )

    @Test
    fun `every chapter-two boss is a real fight and none is impossible`() {
        val bosses = listOf(
            Maps.TRIDENT to BossVariant.BOTMASTER,
            Maps.SPIRAL to BossVariant.ROOTKIT,
            Maps.ZIGZAG to BossVariant.WORM,
            Maps.HELIX to BossVariant.SPOOFER,
            Maps.BRAID to BossVariant.KERNEL_PANIC
        )
        val report = StringBuilder()
        for ((map, variant) in bosses) {
            val breach = fight(map, BossVariant.BREACH, mixed, 24)
            val own = fight(map, variant, mixed, 24)
            report.append("${map.displayName}: BREACH $breach, ${variant.displayName} $own\n")
            assertTrue("$report${variant.displayName} must be beatable by a mixed board", own.killed)
            assertTrue("$report${variant.displayName} must not wreck the core", own.hpLost <= MAX_HP_LOST)
            assertTrue("$report${variant.displayName} should take longer than BREACH", own.seconds > breach.seconds)
        }
        // The owner: "test balance on this, it sounds impossible". It is not —
        // even a board with nothing that sees through it wins, just slower.
        val fooledBreach = fight(Maps.HELIX, BossVariant.BREACH, fooled, 24)
        val fooledSpoofer = fight(Maps.HELIX, BossVariant.SPOOFER, fooled, 24)
        report.append("HELIX, no ANALYST/ROOT ADMIN: BREACH $fooledBreach, SPOOFER $fooledSpoofer\n")
        println(report)
        assertTrue("$report", fooledSpoofer.killed)
        assertTrue("$report SPOOFER should not take more than twice as long as BREACH",
            fooledSpoofer.seconds < fooledBreach.seconds * 2f)
        assertNotNull(report)
    }

    private companion object {
        /** A boss fight may cost a little integrity to its escorts, never the run. */
        const val MAX_HP_LOST = 10
    }
}
