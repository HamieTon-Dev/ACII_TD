package com.cyopstd.game

import android.graphics.Bitmap
import android.graphics.Canvas
import com.cyopstd.game.core.Balance
import com.cyopstd.game.core.Maps
import com.cyopstd.game.core.WorldGeometry
import com.cyopstd.game.engine.GameEngine
import com.cyopstd.game.engine.PlacementResult
import com.cyopstd.game.engine.RunPhase
import com.cyopstd.game.engine.WaveGenerator
import com.cyopstd.game.model.AgentType
import com.cyopstd.game.ui.game.BattlefieldRenderOptions
import com.cyopstd.game.ui.game.BattlefieldRenderer
import com.cyopstd.game.ui.game.BattlefieldSelection
import com.cyopstd.game.ui.game.WorldTransform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.random.Random

/**
 * SERVER SYSTEMS ENGINEER played the way a player plays it: the real game
 * loop, many waves, boss warnings, fast-forward, a save and a restore, and
 * what it looks like on the board.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ServerEngineerScenarioTest {

    private val engineer = AgentType.SERVER_SYSTEMS_ENGINEER
    private val nodes = Maps.PERIMETER.nodesByCoverage.map { it.id }

    private fun defendedEngine(engineers: Int, engineerLevel: Int = 1): GameEngine {
        val random = Random(11)
        val engine = GameEngine(random, WaveGenerator(random))
        engine.isAgentUnlocked = { true }
        engine.startNewRun()
        engine.addCrypto(50_000_000, countAsEarned = false)
        repeat(engineers) {
            assertEquals(PlacementResult.SUCCESS, engine.placeAgent(engineer, nodes[it]))
            engine.upgradeAgent(nodes[it], engineerLevel - 1)
        }
        for (node in nodes.drop(engineers).take(12)) {
            engine.placeAgent(AgentType.ANALYST, node)
            engine.upgradeAgent(node, Balance.MAX_AGENT_LEVEL - 1)
        }
        return engine
    }

    private class Tally {
        var heals = 0
        var healsOutsideWave = 0
        var inWaveSeconds = 0f
        var bossWarnings = 0
    }

    /** Play waves in the real loop until [waves] have been cleared, tallying every repair. */
    private fun GameEngine.play(waves: Int, speed: Float = 1f): Tally {
        val tally = Tally()
        val target = currentWave + waves
        var guard = 0
        var sawWarning = false
        while (currentWave < target || phase != RunPhase.PREPARING) {
            check(guard++ < 2_000_000) { "run stalled at wave $currentWave in $phase" }
            if (phase == RunPhase.GAME_OVER) error("server fell at wave $currentWave")
            if (phase == RunPhase.PREPARING) startNextWave()
            if (phase == RunPhase.BOSS_WARNING && !sawWarning) { tally.bossWarnings++; sawWarning = true }
            if (phase != RunPhase.BOSS_WARNING) sawWarning = false
            val before = serverHp
            val phaseBefore = phase
            update(0.02f, speed)
            if (phaseBefore == RunPhase.IN_WAVE && phase == RunPhase.IN_WAVE) tally.inWaveSeconds += 0.02f * speed
            if (serverHp > before) {
                tally.heals += serverHp - before
                if (phaseBefore != RunPhase.IN_WAVE) tally.healsOutsideWave++
            }
        }
        return tally
    }

    @Test
    fun `four engineers heal at the promised rate across real waves, bosses included`() {
        val engine = defendedEngine(engineers = 4)
        engine.damageServer(80)
        val tally = engine.play(waves = 15)
        println("4 x [S] L1 over 15 waves: ${tally.inWaveSeconds}s in-wave, ${tally.heals} HP repaired, " +
            "${tally.bossWarnings} boss warnings, hp ${engine.serverHp}/${engine.serverMaxHp}")
        assertTrue("expected boss waves in the sample", tally.bossWarnings >= 2)
        assertEquals("repaired outside a running wave", 0, tally.healsOutsideWave)
        val expected = 4 * (tally.inWaveSeconds / 30f).toInt()
        // Each unit's timer is continuous across waves, so the count is exact
        // to within one tick per unit at the very end.
        assertTrue("repaired ${tally.heals}, expected about $expected",
            tally.heals in (expected - 4)..(expected + 4))
    }

    @Test
    fun `fast forward repairs by game time, like everything else`() {
        val engine = defendedEngine(engineers = 1)
        engine.damageServer(80)
        val tally = engine.play(waves = 6, speed = 3f)
        val expected = (tally.inWaveSeconds / 30f).toInt()
        println("1 x [S] at 3x: ${tally.inWaveSeconds}s in-wave (game time), ${tally.heals} HP repaired")
        assertTrue("repaired ${tally.heals}, expected about $expected", tally.heals in (expected - 1)..(expected + 1))
    }

    @Test
    fun `a level 100 engineer repairs about three times as often`() {
        val engine = defendedEngine(engineers = 1, engineerLevel = 100)
        assertEquals(100, engine.agentAt(nodes[0])!!.level)
        engine.damageServer(80)
        val tally = engine.play(waves = 6)
        val interval = Balance.engineerHealInterval(100)
        val expected = (tally.inWaveSeconds / interval).toInt()
        println("1 x [S] L100 (every ${"%.2f".format(interval)}s): ${tally.inWaveSeconds}s in-wave, ${tally.heals} HP")
        assertTrue("repaired ${tally.heals}, expected about $expected", tally.heals in (expected - 1)..(expected + 1))
    }

    @Test
    fun `engineers survive a save and restore, level and all`() {
        val engine = defendedEngine(engineers = 4, engineerLevel = 20)
        val saved = engine.snapshotPlacements()
        val random = Random(2)
        val restored = GameEngine(random, WaveGenerator(random))
        restored.isAgentUnlocked = { true }
        restored.restore(
            wave = 12, serverHp = 50, crypto = 0, placements = saved,
            attacksBlocked = 0, cryptoEarned = 0, bossesDefeated = 0,
            serverDamageTaken = 0, agentsDeployed = 0, agentUpgrades = 0
        )
        val back = restored.agents.items.filter { it.active && it.type == engineer }
        assertEquals(4, back.size)
        assertTrue(back.all { it.level == 20 })
        val tally = restored.play(waves = 2)
        assertTrue("restored engineers did not repair", tally.heals > 0)
    }

    @Test
    fun `selling one refunds and frees a slot under the cap`() {
        val engine = defendedEngine(engineers = 4)
        assertEquals(PlacementResult.TYPE_LIMIT_REACHED, engine.placeAgent(engineer, nodes[20]))
        assertTrue(engine.sellAgent(nodes[0]))
        assertEquals(PlacementResult.SUCCESS, engine.placeAgent(engineer, nodes[20]))
    }

    @Test
    fun `renders on the board with the link to the core and the green plus`() {
        val engine = defendedEngine(engineers = 4)
        engine.startNextWave()
        engine.damageServer(10)
        // Run the agents to the first repair so the "+ +" is in the frame.
        var t = 0f
        while (t < 30.2f) { engine.combatSystem().update(0.02f); t += 0.02f }
        engine.update(0.3f, 1f)

        val width = WorldGeometry.WIDTH.toInt()
        val height = WorldGeometry.HEIGHT.toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        BattlefieldRenderer().draw(
            canvas = Canvas(bitmap),
            engine = engine,
            transform = WorldTransform(WorldGeometry.WIDTH, WorldGeometry.HEIGHT),
            options = BattlefieldRenderOptions(backgroundAnimation = false),
            selection = BattlefieldSelection(selectedNodeId = nodes[0]),
            time = 1f
        )
        val out = File("build/engineer").apply { mkdirs() }
        File(out, "engineer_board.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

        // Green ink above the core, where the "+ +" floats.
        val cx = (WorldGeometry.SERVER_X + WorldGeometry.SERVER_WIDTH / 2f).toInt()
        var green = 0
        for (x in cx - 40..cx + 40) for (y in (WorldGeometry.SERVER_TOP - 60f).toInt()..WorldGeometry.SERVER_TOP.toInt()) {
            val p = bitmap.getPixel(x, y)
            val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val b = p and 0xFF
            if (g > 180 && r < 90 && b < 200) green++
        }
        assertTrue("no green \"+ +\" above the core ($green px)", green > 10)
    }
}
