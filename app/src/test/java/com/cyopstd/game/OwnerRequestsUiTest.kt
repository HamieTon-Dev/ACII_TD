package com.cyopstd.game

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.cyopstd.game.model.EnemyType
import com.cyopstd.game.engine.GameEngine
import com.cyopstd.game.engine.RunPhase
import com.cyopstd.game.engine.SpawnOrder
import com.cyopstd.game.engine.WaveGenerator
import com.cyopstd.game.engine.WavePlan
import com.cyopstd.game.model.AgentType
import com.cyopstd.game.model.BossModifier
import com.cyopstd.game.model.BossVariant
import com.cyopstd.game.ui.game.BossBriefing
import com.cyopstd.game.ui.game.BossBriefingPanel
import com.cyopstd.game.ui.game.DeployPanel
import com.cyopstd.game.ui.theme.CyOpsTheme
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The owner's 2026-09-26 requests: U1, a lock and an unlock note on locked
 * agents; U3, a briefing on the next boss before its wave starts.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1920dp-h1080dp-land-xhdpi")
class OwnerRequestsUiTest {

    @get:Rule
    val compose = createComposeRule()

    // ------------------------------------------------------------------- U1

    @Test
    fun `a locked agent shows a lock, and tapping it explains how to unlock it`() {
        val selected = mutableListOf<AgentType>()
        compose.setContent {
            CyOpsTheme {
                DeployPanel(
                    crypto = 1_000,
                    unlockedAgents = AgentType.starters.map { it.name }.toSet(),
                    selected = null,
                    onSelect = { selected += it },
                    onClose = {},
                    bestWave = 2
                )
            }
        }

        // No bare wave number on a locked card any more.
        compose.onAllNodesWithText("UNLOCKS AT").assertCountEquals(0)
        assertTrue(compose.onAllNodesWithText("🔒 LOCKED").fetchSemanticsNodes().isNotEmpty())

        compose.onNodeWithText("IPS").performClick()
        assertTrue("a locked agent must not be selected", selected.isEmpty())
        compose.onNodeWithText(AgentType.IPS.unlockRequirement).assertIsDisplayed()
        compose.onNodeWithText("Your best: wave 2").assertIsDisplayed()

        compose.onNodeWithText("OK").performClick()
        compose.onNodeWithText(AgentType.IPS.unlockRequirement).assertDoesNotExist()
    }

    @Test
    fun `the unlock requirement says the wave in words`() {
        assertEquals(
            "Unlock this agent by reaching wave 30 on any level.",
            AgentType.REDHAT.unlockRequirement
        )
    }

    // ------------------------------------------------------------------- U3

    private fun bossPlan(variant: BossVariant, modifiers: List<BossModifier> = emptyList()) = WavePlan(
        wave = 10,
        isBossWave = true,
        orders = List(2) {
            SpawnOrder(
                time = it.toFloat(), type = EnemyType.BOSS, lane = 0, elite = false,
                boss = true, bossModifiers = modifiers, bossVariant = variant
            )
        },
        bossModifiers = modifiers,
        bossVariant = variant
    )

    @Test
    fun `the briefing names each boss's real counters`() {
        val gg = BossBriefing.from(bossPlan(BossVariant.GOOD_GAME, listOf(BossModifier.ENCRYPTION_SHIELD)))
        val counters = gg.weakTo.map { it.first }
        assertEquals(2, gg.bossCount)
        assertTrue("RED HAT is GOOD GAME's counter", AgentType.REDHAT in counters)
        assertTrue("armoured: armour-ignoring agents", AgentType.ROOT_ADMIN in counters)
        assertTrue("encrypted: CRYPTOGRAPHER", AgentType.CRYPTOGRAPHER in counters)

        val whiteEye = BossBriefing.from(bossPlan(BossVariant.WHITE_EYE))
        assertTrue(
            "WHITE EYE jams RED HAT, and the briefing must say so",
            whiteEye.warnings.any { it.contains(AgentType.REDHAT.displayName) }
        )
    }

    @Test
    fun `the boss briefed is the boss that arrives`() {
        val random = Random(11)
        val engine = GameEngine(random, WaveGenerator(random))
        engine.isAgentUnlocked = { true }
        engine.startNewRun()
        // Walk to the break before the first boss wave.
        while (!engine.nextWaveIsBoss()) {
            engine.startNextWave()
            var elapsed = 0f
            while (engine.phase != RunPhase.PREPARING && elapsed < 300f) {
                engine.update(0.05f, 1f)
                elapsed += 0.05f
            }
        }
        assertEquals(RunPhase.PREPARING, engine.phase)
        val preview = engine.upcomingPlan()
        assertNotNull(preview)
        preview!!
        assertTrue("asking twice gives the same plan", preview === engine.upcomingPlan())
        assertTrue(preview.isBossWave)

        engine.startNextWave()
        assertEquals(preview.bossVariant, engine.activeBossVariant)
        assertEquals(preview.bossModifiers, engine.activeBossModifiers)
        assertNull("no preview once the wave has started", engine.upcomingPlan())
    }

    @Test
    fun `the briefing panel shows the boss, its weaknesses and a close button`() {
        val briefing = BossBriefing.from(bossPlan(BossVariant.GOOD_GAME))
        var closed = 0
        compose.setContent {
            CyOpsTheme {
                BossBriefingPanel(
                    briefing = briefing,
                    unlockedAgents = setOf(AgentType.REDHAT.name),
                    onClose = { closed++ }
                )
            }
        }
        compose.onNodeWithText("NEXT: WAVE 10 · BOSS").assertIsDisplayed()
        compose.onNodeWithText("WEAK TO").assertIsDisplayed()
        compose.onNodeWithText("RED HAT", substring = true).assertIsDisplayed()
        compose.onNodeWithText("CLOSE").performClick()
        assertEquals(1, closed)
    }

    // ------------------------------------------------------------ +10 button

    @Test
    fun `+10 is only lit when all ten levels are affordable`() {
        assertTrue(com.cyopstd.game.ui.game.plusTenAffordable(affordableLevels = 10, level = 1))
        assertTrue(!com.cyopstd.game.ui.game.plusTenAffordable(affordableLevels = 9, level = 1))
        assertTrue("one level affordable is not ten", !com.cyopstd.game.ui.game.plusTenAffordable(1, 1))
        // Near the cap, "all that are left" is enough.
        val cap = com.cyopstd.game.core.Balance.MAX_AGENT_LEVEL
        assertTrue(com.cyopstd.game.ui.game.plusTenAffordable(affordableLevels = 3, level = cap - 3))
        assertTrue(!com.cyopstd.game.ui.game.plusTenAffordable(affordableLevels = 0, level = cap))
    }

    // --------------------------------------------- boss waves: mix and wait

    @Test
    fun `a wave with several bosses fields different types`() {
        val generator = WaveGenerator(Random(3))
        var checked = 0
        for (wave in listOf(20, 25, 30, 35, 40)) {
            val plan = generator.generate(wave)
            val bosses = plan.orders.filter { it.boss }
            if (bosses.size < 2) continue
            val pool = BossVariant.poolForCycle(com.cyopstd.game.core.Balance.bossCycle(wave))
            val expectedDistinct = minOf(bosses.size, pool.size)
            assertEquals(
                "wave $wave repeats a boss type while others were available",
                expectedDistinct,
                bosses.map { it.bossVariant }.distinct().size
            )
            checked++
        }
        assertTrue("no multi-boss wave was generated to check", checked > 0)
    }

    private fun engineBefore(wave: Int, bossAuto: Boolean): GameEngine {
        val random = Random(5)
        val engine = GameEngine(random, WaveGenerator(random))
        engine.isAgentUnlocked = { true }
        engine.restore(
            wave = wave, serverHp = 100, crypto = 0, placements = emptyList(),
            attacksBlocked = 0, cryptoEarned = 0, bossesDefeated = 0,
            serverDamageTaken = 0, agentsDeployed = 0, agentUpgrades = 0
        )
        engine.autoStartWaves = true
        engine.autoStartBossWaves = bossAuto
        return engine
    }

    /** Plays the current wave out, then a few seconds of the break after it. */
    private fun GameEngine.clearWaveThenWait() {
        startNextWave()
        var elapsed = 0f
        while (phase != RunPhase.PREPARING && phase != RunPhase.GAME_OVER && elapsed < 300f) {
            update(0.05f, 1f); elapsed += 0.05f
        }
        repeat(40) { update(0.05f, 1f) }
    }

    @Test
    fun `auto-start waits before a boss wave unless boss auto-start is on`() {
        // Wave 4 cleared: wave 5 is a boss wave.
        val waits = engineBefore(wave = 3, bossAuto = false)
        waits.clearWaveThenWait()
        assertEquals(RunPhase.PREPARING, waits.phase)
        assertTrue(waits.nextWaveIsBoss())
        assertEquals("no countdown before a boss wave", 0, waits.autoStartRemaining.toInt())

        val goes = engineBefore(wave = 3, bossAuto = true)
        goes.startNextWave()
        var elapsed = 0f
        while (goes.phase != RunPhase.PREPARING && elapsed < 300f) { goes.update(0.05f, 1f); elapsed += 0.05f }
        assertTrue("boss auto-start on: the countdown runs", goes.autoStartRemaining > 0f)
    }

    @Test
    fun `ordinary waves still auto-start`() {
        // Wave 2 cleared: wave 3 is not a boss wave.
        val engine = engineBefore(wave = 1, bossAuto = false)
        engine.startNextWave()
        var elapsed = 0f
        while (engine.phase != RunPhase.PREPARING && elapsed < 300f) { engine.update(0.05f, 1f); elapsed += 0.05f }
        assertTrue(engine.autoStartRemaining > 0f)
    }

    // --------------------------------------------------- U2 and the tutorial

    @Test
    fun `the guide card steps through to DONE and reports once`() {
        var finished = 0
        val steps = com.cyopstd.game.ui.common.MenuGuide.steps
        compose.setContent {
            CyOpsTheme {
                com.cyopstd.game.ui.common.GuideCard(steps = steps, onFinished = { finished++ })
            }
        }
        compose.onNodeWithText("1/${steps.size}", substring = true).assertIsDisplayed()
        repeat(steps.size - 1) { compose.onNodeWithText("NEXT").performClick() }
        compose.onNodeWithText("DONE").performClick()
        assertEquals(1, finished)
    }

    @Test
    fun `the menu tour mentions scrolling for more, and SKIP closes it`() {
        assertTrue(
            com.cyopstd.game.ui.common.MenuGuide.steps.any { it.title.contains("SCROLL") }
        )
        var finished = 0
        compose.setContent {
            CyOpsTheme {
                com.cyopstd.game.ui.common.GuideCard(
                    steps = com.cyopstd.game.ui.common.MenuGuide.steps,
                    onFinished = { finished++ }
                )
            }
        }
        compose.onNodeWithText("SKIP").performClick()
        assertEquals(1, finished)
    }

    @Test
    fun `the firmware explainer states the real damage per level`() {
        val pct = com.cyopstd.game.core.Balance.FIRMWARE_DAMAGE_PER_LEVEL * 100f
        val body = com.cyopstd.game.ui.common.FirmwareGuide.steps.joinToString(" ") { it.body }
        assertTrue(body, body.contains("+$pct%") || body.contains("+${pct.toInt()}%"))
    }

    @Test
    fun `the firmware screen shows its explainer when asked`() {
        var done = 0
        compose.setContent {
            CyOpsTheme {
                com.cyopstd.game.ui.menu.FirmwareScreen(
                    budget = 0, firmwareLevel = 0, lifetimeBudgetEarned = 0,
                    backgroundAnimation = false, onBuy = {}, onBack = {},
                    showGuide = true, onGuideDone = { done++ }
                )
            }
        }
        compose.onNodeWithText("1/", substring = true).assertIsDisplayed()
        compose.onNodeWithText("SKIP").performClick()
        assertEquals(1, done)
    }

    @Test
    fun `the first-run tutorial teaches pinch-to-zoom before the first placement`() {
        val script = com.cyopstd.game.ui.game.TutorialScript
        val zoom = script.stepAt(script.PINCH_ZOOM)
        assertNotNull(zoom)
        assertTrue(zoom!!.body.contains("Pinch"))
        assertTrue(script.PINCH_ZOOM < script.PLACE_FIREWALLS)
    }
}
