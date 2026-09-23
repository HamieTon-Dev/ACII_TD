package com.cyopstd.game

import com.cyopstd.game.core.Balance
import com.cyopstd.game.core.WorldGeometry
import com.cyopstd.game.engine.GameEngine
import com.cyopstd.game.engine.PlacementResult
import com.cyopstd.game.engine.RunPhase
import com.cyopstd.game.engine.WaveGenerator
import com.cyopstd.game.model.AgentType
import com.cyopstd.game.model.TargetingMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * End-to-end simulation tests.
 *
 * The engine is pure Kotlin, so these drive a real match on the JVM: waves are
 * generated, packets walk the lanes, agents acquire and fire, damage resolves,
 * crypto is paid out and the server can actually fall. This is the game loop
 * under test, not a mock of it.
 */
class GameEngineTest {

    private fun newEngine(seed: Int = 7): GameEngine {
        val random = Random(seed)
        val engine = GameEngine(random, WaveGenerator(random))
        // Tests exercise the full roster; unlock gating has its own test.
        engine.isAgentUnlocked = { true }
        engine.startNewRun()
        return engine
    }

    /** Advance the simulation by [seconds] in fixed 20ms steps. */
    private fun GameEngine.runFor(seconds: Float, speed: Float = 1f) {
        var elapsed = 0f
        while (elapsed < seconds) {
            update(0.02f, speed)
            elapsed += 0.02f
        }
    }

    /**
     * Deploy a defence strong enough to survive being fast-forwarded. Tests that
     * need to *reach* a late wave use this so the run does not simply die on the
     * way there.
     */
    private fun GameEngine.deployStrongDefence() {
        addCrypto(1_000_000, countAsEarned = false)
        for (node in WorldGeometry.nodesByCoverage.take(14).map { it.id }) {
            placeAgent(AgentType.ANALYST, node)
            repeat(Balance.MAX_AGENT_LEVEL - 1) { upgradeAgent(node) }
        }
    }

    /**
     * Fast-forward to the start of [targetWave] without playing it.
     *
     * Fails loudly rather than looping forever if the run stalls or the server
     * falls on the way - a hanging test is far worse than a failing one.
     */
    private fun GameEngine.fastForwardTo(targetWave: Int) {
        var guard = 0
        while (currentWave < targetWave - 1) {
            val before = currentWave
            startNextWave()
            runWaveToCompletion(budgetSeconds = 200f)
            assertTrue(
                "run died before reaching wave $targetWave (stopped at $currentWave)",
                phase != RunPhase.GAME_OVER
            )
            assertTrue(
                "no progress toward wave $targetWave (stuck at $currentWave)",
                currentWave > before
            )
            guard++
            assertTrue("fast-forward guard tripped", guard < targetWave + 5)
        }
    }

    /** Run until the wave finishes or the budget expires. Returns true if cleared. */
    private fun GameEngine.runWaveToCompletion(budgetSeconds: Float = 240f): Boolean {
        var elapsed = 0f
        while (elapsed < budgetSeconds) {
            update(0.02f, 1f)
            elapsed += 0.02f
            if (phase == RunPhase.PREPARING || phase == RunPhase.GAME_OVER) return true
        }
        return false
    }

    // ---------------------------------------------------------------- startup

    @Test
    fun `a new run starts in preparation with full integrity and seed crypto`() {
        val engine = newEngine()
        assertEquals(RunPhase.PREPARING, engine.phase)
        assertEquals(0, engine.currentWave)
        assertEquals(Balance.SERVER_MAX_HP, engine.serverHp)
        assertEquals(Balance.STARTING_CRYPTO, engine.crypto)
        assertEquals(0, engine.activeEnemyCount())
        assertEquals(0, engine.activeAgentCount())
    }

    @Test
    fun `the starting crypto affords at least one opening agent`() {
        val engine = newEngine()
        assertTrue(
            "a player must be able to deploy before wave 1",
            engine.crypto >= AgentType.FIREWALL.cost
        )
    }

    // -------------------------------------------------------------- placement

    @Test
    fun `placing an agent deducts crypto and occupies the node`() {
        val engine = newEngine()
        val before = engine.crypto

        val result = engine.placeAgent(AgentType.FIREWALL, nodeId = 5)

        assertEquals(PlacementResult.SUCCESS, result)
        assertEquals(before - AgentType.FIREWALL.cost, engine.crypto)
        assertEquals(1, engine.activeAgentCount())

        val agent = engine.agentAt(5)
        assertNotNull(agent)
        assertEquals(AgentType.FIREWALL, agent!!.type)
        assertEquals(1, agent.level)
        assertEquals(WorldGeometry.node(5)!!.x, agent.x, 0.01f)
    }

    @Test
    fun `a node cannot be double-booked`() {
        val engine = newEngine()
        engine.placeAgent(AgentType.FIREWALL, 3)
        val second = engine.placeAgent(AgentType.IDS, 3)
        assertEquals(PlacementResult.NODE_OCCUPIED, second)
        assertEquals(1, engine.activeAgentCount())
    }

    @Test
    fun `placement is refused without enough crypto and costs nothing`() {
        val engine = newEngine()
        // Drain the wallet on affordable agents first.
        var node = 0
        while (engine.crypto >= AgentType.FIREWALL.cost && node < WorldGeometry.nodes.size) {
            engine.placeAgent(AgentType.FIREWALL, node)
            node++
        }
        val cryptoBefore = engine.crypto
        val agentsBefore = engine.activeAgentCount()

        val result = engine.placeAgent(AgentType.ROOT_ADMIN, node)

        assertEquals(PlacementResult.INSUFFICIENT_CRYPTO, result)
        assertEquals(cryptoBefore, engine.crypto)
        assertEquals(agentsBefore, engine.activeAgentCount())
    }

    @Test
    fun `an invalid node id is rejected`() {
        val engine = newEngine()
        assertEquals(PlacementResult.NODE_INVALID, engine.placeAgent(AgentType.FIREWALL, 9999))
        assertEquals(PlacementResult.NODE_INVALID, engine.placeAgent(AgentType.FIREWALL, -1))
    }

    @Test
    fun `a locked agent cannot be deployed`() {
        val random = Random(1)
        val engine = GameEngine(random, WaveGenerator(random))
        engine.isAgentUnlocked = { it.unlockedByDefault }
        engine.startNewRun()

        assertEquals(
            PlacementResult.AGENT_LOCKED,
            engine.placeAgent(AgentType.ROOT_ADMIN, 0)
        )
        assertEquals(
            PlacementResult.SUCCESS,
            engine.placeAgent(AgentType.FIREWALL, 0)
        )
    }

    // ---------------------------------------------------------------- upgrade

    @Test
    fun `upgrading raises level and stats and costs crypto`() {
        val engine = newEngine()
        engine.placeAgent(AgentType.FIREWALL, 1)
        val agent = engine.agentAt(1)!!
        val statsBefore = agent.stats()
        val cost = agent.type.upgradeCost(agent.level)
        val cryptoBefore = engine.crypto

        assertTrue(engine.upgradeAgent(1))

        assertEquals(2, agent.level)
        assertEquals(cryptoBefore - cost, engine.crypto)
        val statsAfter = agent.stats()
        assertTrue(statsAfter.damage > statsBefore.damage)
        assertTrue(statsAfter.fireRate > statsBefore.fireRate)
        assertTrue(statsAfter.range > statsBefore.range)
    }

    @Test
    fun `upgrading stops at the level cap`() {
        val engine = newEngine()
        engine.placeAgent(AgentType.FIREWALL, 2)
        // Give the run enough crypto to max out without simulating a full game.
        engine.addCrypto(50_000, countAsEarned = false)

        repeat(Balance.MAX_AGENT_LEVEL + 5) { engine.upgradeAgent(2) }

        assertEquals(Balance.MAX_AGENT_LEVEL, engine.agentAt(2)!!.level)
        assertFalse("upgrading a maxed agent must fail", engine.upgradeAgent(2))
    }

    @Test
    fun `upgrading is refused without crypto`() {
        val engine = newEngine()
        engine.placeAgent(AgentType.FIREWALL, 4)
        engine.removeCrypto(engine.crypto)
        assertFalse(engine.upgradeAgent(4))
        assertEquals(1, engine.agentAt(4)!!.level)
    }

    // ------------------------------------------------------------------- sell

    @Test
    fun `selling frees the node and refunds part of the investment`() {
        val engine = newEngine()
        engine.placeAgent(AgentType.FIREWALL, 6)
        engine.upgradeAgent(6)
        val agent = engine.agentAt(6)!!
        val expectedRefund = agent.type.sellValue(agent.level)
        val cryptoBefore = engine.crypto

        assertTrue(engine.sellAgent(6))

        assertNull(engine.agentAt(6))
        assertEquals(cryptoBefore + expectedRefund, engine.crypto)
        assertEquals(0, engine.activeAgentCount())
    }

    @Test
    fun `selling an empty node does nothing`() {
        val engine = newEngine()
        val before = engine.crypto
        assertFalse(engine.sellAgent(9))
        assertEquals(before, engine.crypto)
    }

    // ------------------------------------------------------------ wave cycle

    @Test
    fun `starting a wave spawns packets that advance along their lane`() {
        val engine = newEngine()
        engine.startNextWave()
        assertEquals(1, engine.currentWave)
        assertEquals(RunPhase.IN_WAVE, engine.phase)

        engine.runFor(2.0f)
        assertTrue("packets should be on the field", engine.activeEnemyCount() > 0)

        val found = engine.enemies.items.firstOrNull { it.active }
        assertNotNull("wave 1 must spawn packets", found)
        val enemy = found!!
        val startX = enemy.x
        val lane = enemy.lane
        engine.runFor(1.0f)

        assertTrue("attacks must advance along their route", enemy.progress > 0f)
        assertEquals("attacks stay on their route", lane, enemy.lane)
        // Position is derived from route progress, so it must sit on the path.
        assertTrue(
            "attacks track their route",
            WorldGeometry.distanceToNearestLane(enemy.x, enemy.y) < 1f
        )
    }

    @Test
    fun `an undefended wave damages the server`() {
        val engine = newEngine()
        engine.startNextWave()
        engine.runFor(60f)
        assertTrue(
            "packets that reach CORE-SERVER must hurt it",
            engine.serverHp < Balance.SERVER_MAX_HP
        )
        assertTrue(engine.runServerDamageTaken > 0)
    }

    @Test
    fun `a defended wave is cleared and pays out crypto`() {
        val engine = newEngine()
        // A modest but real opening: three firewalls covering the lanes.
        engine.placeAgent(AgentType.FIREWALL, 8)
        engine.placeAgent(AgentType.FIREWALL, 16)
        engine.placeAgent(AgentType.FIREWALL, 24)
        val cryptoAfterDeploying = engine.crypto

        engine.startNextWave()
        val finished = engine.runWaveToCompletion()

        assertTrue("wave 1 should finish", finished)
        assertEquals(RunPhase.PREPARING, engine.phase)
        assertTrue("packets should have been destroyed", engine.runAttacksBlocked > 0)
        assertTrue("kills must pay crypto", engine.crypto > cryptoAfterDeploying)
        assertTrue(engine.runCryptoEarned > 0)
        assertEquals(
            "a cleared wave leaves nothing on the field",
            0, engine.activeEnemyCount()
        )
    }

    @Test
    fun `agents fire and deal damage to packets in range`() {
        val engine = newEngine()
        engine.placeAgent(AgentType.IDS, 8)
        engine.startNextWave()

        var sawProjectile = false
        var sawDamage = false
        var elapsed = 0f
        while (elapsed < 30f && !(sawProjectile && sawDamage)) {
            engine.update(0.02f, 1f)
            elapsed += 0.02f
            if (engine.projectiles.activeCount() > 0) sawProjectile = true
            if (engine.enemies.items.any { it.active && it.health < it.maxHealth }) sawDamage = true
        }

        assertTrue("agents must actually fire", sawProjectile)
        assertTrue("shots must actually hurt packets", sawDamage)
        assertTrue("an agent should log damage dealt", engine.agentAt(8)!!.lifetimeDamage > 0f)
    }

    @Test
    fun `kills are credited to the agent that lands the finishing hit`() {
        val engine = newEngine()
        engine.addCrypto(1000, countAsEarned = false)
        engine.placeAgent(AgentType.IPS, 8)
        engine.startNextWave()

        var elapsed = 0f
        while (elapsed < 60f && engine.agentAt(8)!!.lifetimeKills == 0) {
            engine.update(0.02f, 1f)
            elapsed += 0.02f
            if (engine.phase == RunPhase.GAME_OVER) break
        }

        assertTrue(
            "the agent doing the shooting should be credited with the kill",
            engine.agentAt(8)!!.lifetimeKills > 0
        )
        assertTrue(engine.runAttacksBlocked > 0)
    }

    @Test
    fun `an agent out of range never fires`() {
        val engine = newEngine()
        // Node row 0 column 0 is far from lane 3; force everything into lane 3
        // by simply checking that a lone agent at the far left with a tiny range
        // cannot reach the server end of the map.
        engine.placeAgent(AgentType.FIREWALL, 0)
        val agent = engine.agentAt(0)!!

        // Park a stationary attack at the far end of the other route. Progress
        // along the route is authoritative: the mover recomputes x/y from it
        // every step, so setting coordinates directly would not survive a tick.
        val farAway = engine.enemies.obtain()!!
        farAway.reset()
        farAway.active = true
        farAway.lane = WorldGeometry.LANE_COUNT - 1
        farAway.progress = WorldGeometry.laneLength[WorldGeometry.LANE_COUNT - 1] - 40f
        farAway.baseSpeed = 0f
        farAway.health = 100f
        farAway.maxHealth = 100f
        engine.update(0.001f, 1f)

        val dx = farAway.x - agent.x
        val dy = farAway.y - agent.y
        assertTrue(
            "test setup: target must be outside range",
            dx * dx + dy * dy > agent.range() * agent.range()
        )

        engine.runFor(3f)
        assertEquals("no shot should be taken", 100f, farAway.health, 0.001f)
    }

    // ------------------------------------------------------------ boss waves

    @Test
    fun `wave five triggers the boss warning then spawns a boss`() {
        val engine = newEngine()
        engine.deployStrongDefence()
        engine.fastForwardTo(5)
        assertEquals(4, engine.currentWave)

        engine.startNextWave()
        assertEquals(5, engine.currentWave)
        assertEquals(
            "a boss wave must warn the player first",
            RunPhase.BOSS_WARNING, engine.phase
        )

        engine.runFor(Balance.BOSS_WARNING_SECONDS + 0.5f)
        assertEquals(RunPhase.IN_WAVE, engine.phase)

        // The defence is strong enough to delete a boss almost instantly, so
        // watch for one as the wave plays rather than sampling after the fact.
        var sawBoss = false
        var elapsed = 0f
        while (elapsed < 20f && !sawBoss) {
            engine.update(0.02f, 1f)
            elapsed += 0.02f
            if (engine.bossOnField()) sawBoss = true
        }
        assertTrue("a boss must reach the field", sawBoss)
    }

    @Test
    fun `wave ten is also a boss wave and its boss is tougher`() {
        fun bossHealthOnWave(wave: Int): Float {
            val engine = newEngine()
            engine.deployStrongDefence()
            engine.fastForwardTo(wave)
            engine.startNextWave()
            assertTrue("wave $wave should be a boss wave", engine.isBossWave())

            // Sample the boss the moment it appears; a maxed defence can destroy
            // it between two checks otherwise.
            var bossHealth = 0f
            var elapsed = 0f
            while (elapsed < 30f && bossHealth == 0f) {
                engine.update(0.02f, 1f)
                elapsed += 0.02f
                engine.enemies.items.forEach {
                    if (it.active && it.isBoss && bossHealth == 0f) bossHealth = it.maxHealth
                }
            }
            assertTrue("wave $wave must spawn a boss", bossHealth > 0f)
            return bossHealth
        }

        val fiveHealth = bossHealthOnWave(5)
        val tenHealth = bossHealthOnWave(10)
        assertTrue(
            "wave 10 boss ($tenHealth) must out-scale wave 5 ($fiveHealth)",
            tenHealth > fiveHealth
        )
    }

    @Test
    fun `a boss reaching the server does far more damage than a packet`() {
        val engine = newEngine()
        val boss = engine.enemies.obtain()!!
        boss.reset()
        boss.active = true
        boss.isBoss = true
        boss.serverDamage = 12
        boss.lane = 0
        boss.health = 1f
        boss.maxHealth = 1f
        boss.baseSpeed = 5000f

        val before = engine.serverHp
        engine.runFor(1f)
        assertTrue("boss impact should be heavy", before - engine.serverHp >= 12)
    }

    // -------------------------------------------------------------- game over

    @Test
    fun `the server can be driven to zero and the run ends`() {
        val engine = newEngine()
        var gameOverFired = false
        engine.onGameOver = { gameOverFired = true }

        // Undefended, the server falls. Twelve waves is far more than enough.
        var wave = 0
        while (engine.phase != RunPhase.GAME_OVER && wave < 14) {
            engine.startNextWave()
            engine.runWaveToCompletion(budgetSeconds = 200f)
            wave++
        }

        assertEquals(RunPhase.GAME_OVER, engine.phase)
        assertEquals(0, engine.serverHp)
        assertTrue("the game over callback must fire", gameOverFired)
    }

    @Test
    fun `no simulation happens after game over`() {
        val engine = newEngine()
        while (engine.phase != RunPhase.GAME_OVER) {
            engine.startNextWave()
            engine.runWaveToCompletion(budgetSeconds = 200f)
        }
        val waveAtDeath = engine.currentWave
        val enemiesAtDeath = engine.activeEnemyCount()

        engine.runFor(5f)

        assertEquals(waveAtDeath, engine.currentWave)
        assertEquals(enemiesAtDeath, engine.activeEnemyCount())
        assertEquals(0, engine.serverHp)
    }

    // ------------------------------------------------------------- targeting

    @Test
    fun `FIRST targeting picks the packet closest to the server`() {
        val engine = newEngine()
        engine.addCrypto(1000, countAsEarned = false)
        engine.placeAgent(AgentType.ANALYST, 8)
        val agent = engine.agentAt(8)!!
        agent.targetingMode = TargetingMode.FIRST

        val near = spawnDummyNear(engine, agent.x + 40f, agent.y, health = 500f, progress = 900f)
        val far = spawnDummyNear(engine, agent.x - 40f, agent.y, health = 500f, progress = 200f)

        val target = engine.combatSystem().selectTarget(agent)
        assertTrue("FIRST should pick the more advanced packet", target === near)
        assertTrue(far.active)
    }

    @Test
    fun `LAST targeting picks the packet furthest from the server`() {
        val engine = newEngine()
        engine.addCrypto(1000, countAsEarned = false)
        engine.placeAgent(AgentType.ANALYST, 8)
        val agent = engine.agentAt(8)!!
        agent.targetingMode = TargetingMode.LAST

        val near = spawnDummyNear(engine, agent.x + 40f, agent.y, health = 500f, progress = 900f)
        val far = spawnDummyNear(engine, agent.x - 40f, agent.y, health = 500f, progress = 200f)

        val target = engine.combatSystem().selectTarget(agent)
        assertTrue("LAST should pick the less advanced packet", target === far)
        assertTrue(near.active)
    }

    @Test
    fun `STRONGEST and WEAKEST targeting honour remaining health`() {
        val engine = newEngine()
        engine.addCrypto(1000, countAsEarned = false)
        engine.placeAgent(AgentType.ANALYST, 8)
        val agent = engine.agentAt(8)!!

        val weak = spawnDummyNear(engine, agent.x + 20f, agent.y, health = 30f, progress = 500f)
        val strong = spawnDummyNear(engine, agent.x - 20f, agent.y, health = 900f, progress = 400f)

        agent.targetingMode = TargetingMode.STRONGEST
        assertTrue(engine.combatSystem().selectTarget(agent) === strong)

        agent.targetingMode = TargetingMode.WEAKEST
        assertTrue(engine.combatSystem().selectTarget(agent) === weak)
    }

    private fun spawnDummyNear(
        engine: GameEngine,
        x: Float,
        y: Float,
        health: Float,
        progress: Float
    ) = engine.enemies.obtain()!!.apply {
        reset()
        active = true
        this.x = x
        this.y = y
        this.health = health
        this.maxHealth = health
        this.progress = progress
        this.baseSpeed = 0f
        this.lane = 0
    }

    // ------------------------------------------------------------ save cycle

    @Test
    fun `a run round-trips through a save snapshot`() {
        val engine = newEngine()
        engine.addCrypto(1000, countAsEarned = false)
        engine.placeAgent(AgentType.FIREWALL, 2)
        engine.placeAgent(AgentType.IDS, 11)
        engine.upgradeAgent(2)
        engine.upgradeAgent(2)
        engine.setTargetingMode(11, TargetingMode.LAST)
        engine.startNextWave()
        engine.runWaveToCompletion()

        val placements = engine.snapshotPlacements()
        val savedWave = engine.currentWave
        val savedHp = engine.serverHp
        val savedCrypto = engine.crypto
        val savedBlocked = engine.runAttacksBlocked

        val restored = newEngine()
        restored.restore(
            wave = savedWave,
            serverHp = savedHp,
            crypto = savedCrypto,
            placements = placements,
            attacksBlocked = savedBlocked,
            cryptoEarned = engine.runCryptoEarned,
            bossesDefeated = engine.runBossesDefeated,
            serverDamageTaken = engine.runServerDamageTaken,
            agentsDeployed = engine.runAgentsDeployed,
            agentUpgrades = engine.runAgentUpgrades
        )

        assertEquals(savedWave, restored.currentWave)
        assertEquals(savedHp, restored.serverHp)
        assertEquals(savedCrypto, restored.crypto)
        assertEquals(savedBlocked, restored.runAttacksBlocked)
        assertEquals(RunPhase.PREPARING, restored.phase)
        assertEquals(2, restored.activeAgentCount())
        assertEquals(3, restored.agentAt(2)!!.level)
        assertEquals(AgentType.IDS, restored.agentAt(11)!!.type)
        assertEquals(TargetingMode.LAST, restored.agentAt(11)!!.targetingMode)
    }

    @Test
    fun `restore tolerates corrupt placement data`() {
        val engine = newEngine()
        engine.restore(
            wave = 3,
            serverHp = 50,
            crypto = 100,
            placements = listOf(
                GameEngine.SavedPlacement(0, "NOT_AN_AGENT", 3, 0),
                GameEngine.SavedPlacement(99999, "FIREWALL", 3, 0),
                GameEngine.SavedPlacement(4, "FIREWALL", 999, 99),
                GameEngine.SavedPlacement(4, "IDS", 2, 0)
            ),
            attacksBlocked = 0, cryptoEarned = 0, bossesDefeated = 0,
            serverDamageTaken = 0, agentsDeployed = 0, agentUpgrades = 0
        )

        // Only the one valid, non-duplicate placement survives, clamped.
        assertEquals(1, engine.activeAgentCount())
        val agent = engine.agentAt(4)!!
        assertEquals(AgentType.FIREWALL, agent.type)
        assertEquals(Balance.MAX_AGENT_LEVEL, agent.level)
        assertEquals(TargetingMode.FIRST, agent.targetingMode)
    }

    // -------------------------------------------------------------- unlocks

    @Test
    fun `reaching a milestone wave raises the unlock callback exactly once`() {
        val engine = newEngine()
        val unlocked = mutableListOf<AgentType>()
        engine.onAgentUnlocked = { unlocked += it }
        engine.deployStrongDefence()

        repeat(10) {
            if (engine.phase == RunPhase.GAME_OVER) return@repeat
            engine.startNextWave()
            engine.runWaveToCompletion()
        }

        assertTrue("IPS unlocks at wave 3", AgentType.IPS in unlocked)
        assertTrue("ANALYST unlocks at wave 5", AgentType.ANALYST in unlocked)
        assertEquals(
            "no duplicate unlock notifications",
            unlocked.size, unlocked.distinct().size
        )
    }

    // ----------------------------------------------------------- game speed

    @Test
    fun `game speed multiplies progress without changing outcomes`() {
        fun distanceAfter(speed: Float, realSeconds: Float): Float {
            val engine = newEngine(seed = 3)
            engine.startNextWave()
            // Let one packet spawn, then measure.
            engine.update(0.02f, 1f)
            var elapsed = 0f
            while (engine.activeEnemyCount() == 0 && elapsed < 5f) {
                engine.update(0.02f, 1f)
                elapsed += 0.02f
            }
            val found = engine.enemies.items.firstOrNull { it.active }
            assertNotNull("a packet should have spawned within 5s", found)
            val enemy = found!!
            val start = enemy.progress
            var t = 0f
            while (t < realSeconds) {
                engine.update(0.02f, speed)
                t += 0.02f
            }
            return enemy.progress - start
        }

        val atOne = distanceAfter(1f, 1f)
        val atTwo = distanceAfter(2f, 1f)
        assertTrue("2x must cover more ground", atTwo > atOne * 1.7f)
        assertTrue("2x should not overshoot wildly", atTwo < atOne * 2.4f)
    }

    // --------------------------------------------------- difficulty increase

    @Test
    fun `later waves send tougher packets than earlier ones`() {
        fun peakHealthOnWave(wave: Int): Float {
            val engine = newEngine(seed = 11)
            engine.deployStrongDefence()
            engine.fastForwardTo(wave)
            engine.startNextWave()
            var peak = 0f
            var t = 0f
            while (t < 40f) {
                engine.update(0.02f, 1f)
                t += 0.02f
                engine.enemies.items.forEach { if (it.active && it.maxHealth > peak) peak = it.maxHealth }
            }
            return peak
        }

        val early = peakHealthOnWave(2)
        val later = peakHealthOnWave(12)
        assertTrue("wave 12 ($later) must out-scale wave 2 ($early)", later > early * 1.5f)
    }

    // ------------------------------------------------------------ pool limits

    @Test
    fun `entity pools are never exceeded during a long match`() {
        val engine = newEngine()
        engine.addCrypto(500_000, countAsEarned = false)
        for (node in WorldGeometry.nodesByCoverage.take(6).map { it.id }) {
            engine.placeAgent(AgentType.IPS, node)
        }

        repeat(16) {
            if (engine.phase == RunPhase.GAME_OVER) return@repeat
            engine.startNextWave()
            engine.runWaveToCompletion(budgetSeconds = 200f)
            assertTrue(engine.activeEnemyCount() <= GameEngine.MAX_ENEMIES)
            assertTrue(engine.projectiles.activeCount() <= GameEngine.MAX_PROJECTILES)
            assertTrue(engine.effects.activeCount() <= GameEngine.MAX_EFFECTS)
        }
    }

    /**
     * The deployment nodes worth building on, best first.
     *
     * Taking the first N nodes off the raw list gives a board crammed into one
     * corner. Ordering by how much route each node actually covers picks the
     * board a competent player would build, which is what makes a comparison
     * between compositions mean anything.
     */
    private fun spreadNodes(count: Int): List<com.cyopstd.game.core.NodePosition> =
        WorldGeometry.nodesByCoverage.take(count)

    /**
     * Fill [nodes] deployment nodes with [type] at [level].
     *
     * Defaults describe a board a real player could plausibly field around wave
     * 45, not a god board: filling all 32 nodes at level 100 takes damage from
     * nothing at all, which makes it useless for comparing compositions.
     */
    private fun GameEngine.deployMonoDefence(
        type: AgentType,
        nodes: Int = 10,
        level: Int = 20
    ) {
        addCrypto(10_000_000, countAsEarned = false)
        for (node in spreadNodes(nodes)) {
            placeAgent(type, node.id)
            upgradeAgent(node.id, level - 1)
        }
    }

    /**
     * Fill every deployment node with a mix built around the counter-play table:
     * IPS for swarms, ANALYST for elites, CRYPTOGRAPHER for encryption, TARPIT
     * for slows, IDS for fast packets, armour-ignoring agents for Trojans, and
     * an ARCHITECT to buff its neighbours.
     */
    private fun GameEngine.deployMixedDefence(nodes: Int = 10, level: Int = 20) {
        addCrypto(10_000_000, countAsEarned = false)
        val rotation = listOf(
            AgentType.IPS,
            AgentType.ANALYST,
            AgentType.CRYPTOGRAPHER,
            AgentType.TARPIT,
            AgentType.ZERO_DAY_HUNTER,
            AgentType.IDS,
            AgentType.ROOT_ADMIN,
            AgentType.NETWORK_ARCHITECT
        )
        spreadNodes(nodes).forEachIndexed { index, node ->
            placeAgent(rotation[index % rotation.size], node.id)
            upgradeAgent(node.id, level - 1)
        }
    }

    /**
     * Play until the server falls, asserting nothing degenerates on the way.
     * Returns the wave the run died on.
     */
    private fun GameEngine.playUntilOverwhelmed(waveCap: Int): Int {
        for (wave in 1..waveCap) {
            if (phase == RunPhase.GAME_OVER) return currentWave
            startNextWave()

            var elapsed = 0f
            var resolved = false
            while (elapsed < 400f) {
                update(0.02f, 1f)
                elapsed += 0.02f
                assertTrue(
                    "enemy pool overflowed on wave $currentWave",
                    activeEnemyCount() <= GameEngine.MAX_ENEMIES
                )
                assertTrue(
                    "projectile pool overflowed on wave $currentWave",
                    projectiles.activeCount() <= GameEngine.MAX_PROJECTILES
                )
                assertTrue(
                    "effect pool overflowed on wave $currentWave",
                    effects.activeCount() <= GameEngine.MAX_EFFECTS
                )
                if (phase == RunPhase.PREPARING || phase == RunPhase.GAME_OVER) {
                    resolved = true
                    break
                }
            }
            assertTrue("wave $currentWave never resolved", resolved)
            assertTrue("crypto went out of range", crypto in 0..100_000_000)
        }
        return currentWave
    }

    @Test
    fun `the endless mode stays structurally sane until the run is overwhelmed`() {
        // "Endless" is a promise about wave 50 as much as wave 5. This plays a
        // fully built board to its death and checks that the run ends by being
        // overwhelmed - not by a wave that never resolves, a pool that
        // overflows, or an economy that runs away.
        val engine = newEngine(seed = 21)
        engine.deployMixedDefence(nodes = WorldGeometry.nodes.size, level = Balance.MAX_AGENT_LEVEL)

        val died = engine.playUntilOverwhelmed(waveCap = 120)

        assertTrue(
            "a fully built, maxed board should get well past the early game",
            died >= 30
        )
        assertTrue("60 waves of play should defeat many bosses", engine.runBossesDefeated >= 5)
        assertTrue(engine.runAttacksBlocked > 500)
    }

    @Test
    fun `difficulty keeps climbing far into the run`() {
        fun peakHealthOnWaveDeep(wave: Int): Float {
            val engine = newEngine(seed = 33)
            engine.deployMixedDefence(nodes = WorldGeometry.nodes.size, level = Balance.MAX_AGENT_LEVEL)
            engine.fastForwardTo(wave)
            engine.startNextWave()
            var peak = 0f
            var t = 0f
            while (t < 60f) {
                engine.update(0.02f, 1f)
                t += 0.02f
                val enemies = engine.enemies.items
                for (i in enemies.indices) {
                    val enemy = enemies[i]
                    if (enemy.active && enemy.maxHealth > peak) peak = enemy.maxHealth
                }
            }
            return peak
        }

        val at10 = peakHealthOnWaveDeep(10)
        val at30 = peakHealthOnWaveDeep(30)
        assertTrue("wave 30 ($at30) must out-scale wave 10 ($at10)", at30 > at10 * 1.5f)
        assertTrue("scaling must stay finite", at30 < 1_000_000f)
    }

    /** Spend a fixed crypto budget on a rotation of agent types, then upgrade. */
    private fun GameEngine.buildOnBudget(rotation: List<AgentType>, budget: Int) {
        addCrypto(budget, countAsEarned = false)
        val placed = ArrayList<Int>()
        var i = 0
        for (node in WorldGeometry.nodesByCoverage) {
            val type = rotation[i % rotation.size]
            if (crypto < type.cost) break
            if (placeAgent(type, node.id) == PlacementResult.SUCCESS) {
                placed += node.id
                i++
            }
        }
        // Round-robin upgrades so the budget is spread, not dumped on one agent.
        var spent = true
        while (spent) {
            spent = false
            for (id in placed) if (upgradeAgent(id, 1) > 0) spent = true
        }
    }

    private fun depthOnBudget(rotation: List<AgentType>, budget: Int): Int =
        (1..3).sumOf { seed ->
            val engine = newEngine(seed)
            engine.buildOnBudget(rotation, budget)
            engine.playUntilOverwhelmed(waveCap = 90)
        }

    @Test
    fun `what you spend the budget on matters more than how much you spend`() {
        // An earlier version of this test asserted that a mixed board beats
        // stacking one agent type. Measuring it properly - equal crypto rather
        // than equal agent count - showed that is simply not true: with the same
        // budget, focused ANALYST, focused ROOT ADMIN and a counter-led mix all
        // land within a few waves of each other.
        //
        // What is true, and what this guards, is that agent *choice* carries
        // real weight: the same budget spent on the cheap all-rounder goes
        // dramatically less far than spent on the right specialists. The
        // counter-play multipliers themselves are asserted directly in
        // ProjectileSystem's own tests.
        val budget = 1500

        val firewallOnly = depthOnBudget(listOf(AgentType.FIREWALL), budget)
        val specialists = depthOnBudget(
            listOf(
                AgentType.ANALYST,
                AgentType.ZERO_DAY_HUNTER,
                AgentType.CRYPTOGRAPHER,
                AgentType.IPS,
                AgentType.ANALYST,
                AgentType.IDS
            ),
            budget
        )

        assertTrue(
            "the same budget spent on specialists ($specialists waves across 3 " +
                "seeds) should clearly beat spending it all on FIREWALL " +
                "($firewallOnly)",
            specialists > firewallOnly * 1.2
        )
    }

    @Test
    fun `the wave five boss can be killed by a modest board`() {
        // The bug this guards: bosses move slower than their own escorts, so
        // under FIRST targeting every piece of trash in the wave outranked the
        // boss and the boss walked the whole route untouched. Combined with boss
        // health tuned for coverage a small board does not have, wave 5 was
        // unwinnable no matter how much the player upgraded.
        //
        // Three level-5 FIREWALLs is deliberately modest - roughly what a first
        // run can afford by wave 5.
        for (agentCount in intArrayOf(3, 4, 5)) {
            val engine = newEngine(seed = 7)
            engine.addCrypto(50_000, countAsEarned = false)
            for (node in WorldGeometry.nodesByCoverage.take(agentCount)) {
                engine.placeAgent(AgentType.FIREWALL, node.id)
                engine.upgradeAgent(node.id, 4)
            }

            engine.fastForwardTo(5)
            engine.startNextWave()
            assertTrue("wave 5 must be a boss wave", engine.isBossWave())
            engine.runWaveToCompletion(budgetSeconds = 400f)

            assertEquals(
                "$agentCount agents should clear the first boss wave",
                RunPhase.PREPARING, engine.phase
            )
            assertTrue(
                "$agentCount agents should destroy the wave-5 boss, not let it " +
                    "walk through (server on ${engine.serverHp} hp)",
                engine.runBossesDefeated >= 1
            )
        }
    }

    @Test
    fun `a boss is targeted ahead of the trash escorting it`() {
        val engine = newEngine()
        engine.addCrypto(5_000, countAsEarned = false)
        val node = WorldGeometry.nodesByCoverage.first()
        engine.placeAgent(AgentType.FIREWALL, node.id)
        val agent = engine.agentAt(node.id)!!

        // Both in range. The boss is FURTHER along than the escort, which is
        // what makes this discriminating: FIRST would pick the boss anyway on
        // progress alone, so the escort is placed behind it to give LAST a
        // different answer than FIRST.
        val boss = engine.enemies.obtain()!!.apply {
            reset(); active = true; isBoss = true; lane = 0
            progress = 900f; baseSpeed = 0f; health = 500f; maxHealth = 500f
            x = agent.x; y = agent.y
        }
        val escort = engine.enemies.obtain()!!.apply {
            reset(); active = true; lane = 0
            progress = 600f; baseSpeed = 0f; health = 20f; maxHealth = 20f
            x = agent.x + 10f; y = agent.y
        }

        assertTrue(
            "FIRST targeting must engage the boss",
            engine.combatSystem().selectTarget(agent) === boss
        )

        // And the real case the fix exists for: a boss trailing its escorts.
        boss.progress = 300f
        escort.progress = 1200f
        assertTrue(
            "FIRST must still prefer the boss when the escort has outrun it - " +
                "that is the situation that made bosses unkillable",
            engine.combatSystem().selectTarget(agent) === boss
        )

        agent.targetingMode = TargetingMode.LAST
        assertTrue(
            "LAST is how a player assigns an agent to escort clean-up, so it " +
                "must not be hijacked by boss priority",
            engine.combatSystem().selectTarget(agent) === boss
        )

        boss.progress = 1200f
        escort.progress = 300f
        assertTrue(
            "with the boss ahead, LAST must fall back to the trailing escort",
            engine.combatSystem().selectTarget(agent) === escort
        )
    }

    @Test
    fun `a well defended run survives past wave five`() {
        // The stated balance target: a new player should get beyond the first
        // boss with a reasonable opening. This plays that opening automatically.
        val engine = newEngine(seed = 5)
        engine.placeAgent(AgentType.FIREWALL, 8)
        engine.placeAgent(AgentType.FIREWALL, 17)

        var wave = 0
        while (wave < 6 && engine.phase != RunPhase.GAME_OVER) {
            // Spend on upgrades and a new agent whenever the run can afford it.
            for (node in WorldGeometry.nodesByCoverage.take(6).map { it.id }) {
                if (engine.agentAt(node) == null && engine.crypto >= AgentType.FIREWALL.cost * 2) {
                    engine.placeAgent(AgentType.FIREWALL, node)
                }
            }
            for (node in WorldGeometry.nodesByCoverage.take(4).map { it.id }) {
                val agent = engine.agentAt(node) ?: continue
                if (engine.crypto >= agent.type.upgradeCost(agent.level) * 2) {
                    engine.upgradeAgent(node)
                }
            }
            engine.startNextWave()
            engine.runWaveToCompletion(budgetSeconds = 200f)
            wave++
        }

        assertTrue(
            "a sensible opening should clear the first boss (reached wave ${engine.currentWave}, " +
                "hp ${engine.serverHp})",
            engine.currentWave >= 6 && engine.phase != RunPhase.GAME_OVER
        )
    }
}
