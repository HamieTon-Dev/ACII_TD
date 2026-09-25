package com.cyopstd.game

import com.cyopstd.game.core.Maps
import com.cyopstd.game.engine.GameEngine
import com.cyopstd.game.engine.PlacementResult
import com.cyopstd.game.model.Agent
import com.cyopstd.game.model.AgentType
import com.cyopstd.game.model.BossPalette
import com.cyopstd.game.model.BossVariant
import com.cyopstd.game.model.Enemy
import com.cyopstd.game.model.EnemyType
import kotlin.math.max
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * E2 — the four opponents the gauntlet fields, and the rule that makes them
 * matter.
 *
 * HUGGING-FACE exists because wave 101 was unwinnable; the hats exist for the
 * same reason. Together they would have turned every boss on that map into the
 * same eight agents doing the same thing, so two of these four take one of
 * those answers away for half of every five seconds — and, critically, only
 * one of them. The owner's rule was exact: *"they both cannot do both, only
 * the type I specified."* Most of what is below is that one sentence, checked
 * from several directions, because it is the sentence the whole fight rests
 * on.
 */
class GauntletBossTest {

    private val eyes = listOf(BossVariant.WHITE_EYE, BossVariant.BLACK_EYE)
    private val heavies = listOf(BossVariant.BOUNTY, BossVariant.PAYOUT)
    private val gauntletBosses = eyes + heavies

    // ------------------------------------------------------------ the marks

    @Test
    fun `each boss draws the text the owner asked for`() {
        // Entered as escapes in the source so that an editor, a diff tool or a
        // copy-paste through something with a narrow charset cannot silently
        // turn one of these into a question mark. The literal is the spec.
        assertEquals("[○_○]", BossVariant.WHITE_EYE.glyph)
        assertEquals("[●_●]", BossVariant.BLACK_EYE.glyph)
        assertEquals("[₩₩₩]", BossVariant.BOUNTY.glyph)
        assertEquals("[¥¥¥]", BossVariant.PAYOUT.glyph)

        // The two eyes differ by exactly one thing: hollow versus filled.
        assertNotEquals(BossVariant.WHITE_EYE.glyph, BossVariant.BLACK_EYE.glyph)
    }

    @Test
    fun `no two bosses in the game share a mark or an id`() {
        val glyphs = BossVariant.entries.map { it.glyph }
        val ids = BossVariant.entries.map { it.id }
        assertEquals("duplicate glyphs: $glyphs", glyphs.size, glyphs.toSet().size)
        assertEquals("duplicate ids: $ids", ids.size, ids.toSet().size)
    }

    // ------------------------------------------------------------- the maps

    @Test
    fun `the four belong to the gauntlet and the map id is real`() {
        // BossVariant holds the id as a literal so the model layer does not
        // depend on the map catalog. This is the check that pays for that.
        for (boss in gauntletBosses) {
            assertEquals(
                "${boss.displayName} must belong to HUGGING-FACE",
                Maps.HUGGING_FACE.id,
                boss.mapId
            )
        }
        val everywhere = BossVariant.entries.filter { it.mapId == null }
        assertEquals(
            "the common roster should still be the original three",
            listOf(BossVariant.BREACH, BossVariant.GOOD_GAME, BossVariant.ZOMBIE),
            everywhere
        )
    }

    @Test
    fun `no other map can roll them`() {
        for (map in Maps.all.filter { it.id != Maps.HUGGING_FACE.id }) {
            for (cycle in 1..40) {
                val pool = BossVariant.poolFor(cycle, map.id)
                val trespassers = pool.filter { it in gauntletBosses }
                assertTrue(
                    "${map.displayName} cycle $cycle rolled $trespassers",
                    trespassers.isEmpty()
                )
            }
        }
    }

    @Test
    fun `the gauntlet fields its own and the common roster both`() {
        val late = BossVariant.poolFor(cycle = 12, mapId = Maps.HUGGING_FACE.id)
        assertEquals(
            "every boss in the game should be available on the gauntlet late on",
            BossVariant.entries.toSet(),
            late.toSet()
        )
        // Cycle 1 is a plain fight everywhere, including here.
        assertEquals(
            listOf(BossVariant.BREACH),
            BossVariant.poolFor(cycle = 1, mapId = Maps.HUGGING_FACE.id)
        )
    }

    @Test
    fun `the eyes wait until the player can own a hat`() {
        // Jamming an agent that unlocks at wave 30 is meaningless before wave
        // 30, and a boss whose entire identity does nothing is just a reskin.
        val hatUnlockCycle = AgentType.REDHAT.unlockWave / 5
        for (eye in eyes) {
            assertEquals(
                "${eye.displayName} should arrive with the hats",
                hatUnlockCycle,
                eye.firstCycle
            )
        }
        val beforeHats = BossVariant.poolFor(hatUnlockCycle - 1, Maps.HUGGING_FACE.id)
        assertTrue("$beforeHats included an eye too early", eyes.none { it in beforeHats })
    }

    // ------------------------------------------------------- one hat, never two

    @Test
    fun `each eye jams exactly one hat and never the other`() {
        assertEquals("REDHAT", BossVariant.WHITE_EYE.jamsAgentType)
        assertEquals("BLUEHAT", BossVariant.BLACK_EYE.jamsAgentType)
        assertNotEquals(
            "the two eyes must not jam the same hat",
            BossVariant.WHITE_EYE.jamsAgentType,
            BossVariant.BLACK_EYE.jamsAgentType
        )
    }

    @Test
    fun `nothing else in the game jams by variant`() {
        val jammers = BossVariant.entries.filter { it.jamsAgentType != null }
        assertEquals("only the two eyes may jam: $jammers", eyes.toSet(), jammers.toSet())
        for (heavy in heavies) {
            assertEquals(
                "${heavy.displayName} was specified as no-jam",
                null,
                heavy.jamsAgentType
            )
        }
    }

    @Test
    fun `every named jam target is a real agent`() {
        // A typo here would be a boss with an ability that silently never
        // fires, which is the hardest kind of bug to notice in a tower defence.
        val names = AgentType.entries.map { it.name }.toSet()
        for (boss in BossVariant.entries) {
            val target = boss.jamsAgentType ?: continue
            assertTrue("${boss.displayName} jams unknown agent $target", target in names)
        }
    }

    // ------------------------------------------------------------ the heavies

    @Test
    fun `the heavies are exactly twenty per cent tougher and do nothing else`() {
        for (heavy in heavies) {
            assertEquals(
                "${heavy.displayName} health",
                1.2f,
                heavy.healthScale / BossVariant.BREACH.healthScale,
                0.0001f
            )
            assertEquals("${heavy.displayName} armour", 0f, heavy.armorBonus, 0.0001f)
            assertTrue(
                "${heavy.displayName} should not counter-scale against any agent",
                heavy.bonusDamageFrom.isEmpty()
            )
        }
    }

    // ------------------------------------------------------------- the theme

    @Test
    fun `the gauntlet four are lit cool and the originals are not`() {
        for (boss in gauntletBosses) {
            assertNotEquals(
                "${boss.displayName} should not be lit like an ordinary boss",
                BossPalette.HOSTILE,
                boss.palette
            )
        }
        for (boss in BossVariant.entries.filter { it.mapId == null }) {
            assertEquals(
                "${boss.displayName} must keep the colour players already know",
                BossPalette.HOSTILE,
                boss.palette
            )
        }
        // The pair that jams is the pair that strobes; the heavies are steady.
        assertTrue(eyes.all { it.palette == BossPalette.SPECTRUM })
        assertTrue(heavies.none { it.palette == BossPalette.SPECTRUM })
        assertNotEquals(
            "the two heavies should be told apart at a glance",
            BossVariant.BOUNTY.palette,
            BossVariant.PAYOUT.palette
        )
    }

    // ------------------------------------------------------------ in a fight

    /** An agent held still next to a boss that is also held still. */
    private class Post(val type: AgentType, val dx: Float, val dy: Float) {
        var agent: Agent? = null
        var peakJam = 0f
        var onsets = 0
        private var wasJammed = false

        fun sample() {
            val jam = agent?.disruptedFor ?: 0f
            peakJam = max(peakJam, jam)
            if (jam > 0f && !wasJammed) onsets++
            wasJammed = jam > 0f
        }
    }

    /**
     * A boss of [variant] parked on the gauntlet with agents posted around it.
     *
     * Both the boss and the agents are pinned in place on purpose. A boss
     * walking its route at full speed leaves a 220-unit radius inside five
     * seconds, so a moving rig would measure the route rather than the
     * mechanic, and it would measure it differently every time the map is
     * edited.
     */
    private fun run(variant: BossVariant, seconds: Float, posts: List<Post>): Enemy {
        val engine = GameEngine(random = Random(11))
        engine.isAgentUnlocked = { true }
        engine.selectMap(Maps.HUGGING_FACE)
        engine.startNewRun()
        engine.restore(
            wave = 30, serverHp = 100, crypto = 99_999, placements = emptyList(),
            attacksBlocked = 0, cryptoEarned = 0, bossesDefeated = 0,
            serverDamageTaken = 0, agentsDeployed = 0, agentUpgrades = 0
        )

        val nodes = engine.map.nodes
        var next = 0
        for (post in posts) {
            while (next < nodes.size) {
                val node = nodes[next++]
                if (engine.placeAgent(post.type, node.id) != PlacementResult.SUCCESS) continue
                post.agent = engine.agents.items.first { it.active && it.nodeId == node.id }
                break
            }
            checkNotNull(post.agent) { "could not post a ${post.type.displayName}" }
        }

        val boss = engine.enemies.obtain()!!
        boss.reset()
        boss.active = true
        boss.type = EnemyType.BOSS
        boss.isBoss = true
        boss.isElite = true
        boss.variant = variant
        boss.lane = 0
        boss.progress = engine.map.laneLength[0] * 0.5f
        // Unkillable and immobile: this test is about the jam, not about
        // whether eight agents can out-damage one boss in five seconds.
        boss.maxHealth = 1_000_000f
        boss.health = 1_000_000f
        boss.baseSpeed = 0f
        boss.variantJamTimer = BossVariant.VARIANT_JAM_INTERVAL

        val dt = 1f / 60f
        // One step to let the path place it, then post the agents around
        // wherever that turned out to be.
        engine.update(dt, 1f)
        for (post in posts) {
            post.agent!!.x = boss.x + post.dx
            post.agent!!.y = boss.y + post.dy
        }

        repeat((seconds / dt).toInt()) {
            engine.update(dt, 1f)
            for (post in posts) post.sample()
        }
        return boss
    }

    @Test
    fun `WHITE EYE jams a nearby RED HAT and nothing else`() {
        val near = Post(AgentType.REDHAT, 60f, 0f)
        val far = Post(AgentType.REDHAT, BossVariant.VARIANT_JAM_RADIUS + 80f, 0f)
        val blue = Post(AgentType.BLUEHAT, 0f, 60f)
        val firewall = Post(AgentType.FIREWALL, -60f, 0f)
        run(BossVariant.WHITE_EYE, seconds = 6f, posts = listOf(near, far, blue, firewall))

        assertTrue("the RED HAT beside it was never jammed", near.peakJam > 0f)
        assertEquals("a RED HAT out of reach must be untouched", 0f, far.peakJam, 0.0001f)
        assertEquals("WHITE EYE must not touch BLUE HAT", 0f, blue.peakJam, 0.0001f)
        assertEquals("FIREWALL cannot be jammed by anything", 0f, firewall.peakJam, 0.0001f)
    }

    @Test
    fun `BLACK EYE is the same fight with the colours swapped`() {
        val red = Post(AgentType.REDHAT, 60f, 0f)
        val blue = Post(AgentType.BLUEHAT, 0f, 60f)
        run(BossVariant.BLACK_EYE, seconds = 6f, posts = listOf(red, blue))

        assertTrue("the BLUE HAT beside it was never jammed", blue.peakJam > 0f)
        assertEquals("BLACK EYE must not touch RED HAT", 0f, red.peakJam, 0.0001f)
    }

    @Test
    fun `a heavy jams nobody`() {
        val red = Post(AgentType.REDHAT, 60f, 0f)
        val blue = Post(AgentType.BLUEHAT, 0f, 60f)
        run(BossVariant.BOUNTY, seconds = 12f, posts = listOf(red, blue))
        assertEquals(0f, red.peakJam, 0.0001f)
        assertEquals(0f, blue.peakJam, 0.0001f)
    }

    @Test
    fun `the jam lands once every five seconds, not continuously`() {
        val near = Post(AgentType.REDHAT, 60f, 0f)
        run(BossVariant.WHITE_EYE, seconds = 21f, posts = listOf(near))

        // Four beats fit in twenty-one seconds at a five-second cadence; the
        // first lands at five, not at zero.
        assertEquals("jam onsets over 21s", 4, near.onsets)
        assertTrue(
            "the jam should last about ${BossVariant.VARIANT_JAM_SECONDS}s",
            near.peakJam <= BossVariant.VARIANT_JAM_SECONDS + 0.05f
        )
        assertFalse(
            "a jam that never lifts is a disabled agent, not a boss ability",
            BossVariant.VARIANT_JAM_SECONDS >= BossVariant.VARIANT_JAM_INTERVAL
        )
    }

    @Test
    fun `the jam is the numbers the owner asked for`() {
        assertEquals("once every 5 seconds", 5f, BossVariant.VARIANT_JAM_INTERVAL, 0.001f)
        assertEquals("for 2 seconds", 2f, BossVariant.VARIANT_JAM_SECONDS, 0.001f)
        assertEquals("within range of 100", 100f, BossVariant.VARIANT_JAM_RADIUS, 0.001f)
    }

    @Test
    fun `the jam cannot reach as far as the hat it is jamming`() {
        // The whole counterplay. If the radius ever grows past a hat's range
        // there is no placement that escapes it, and the mechanic stops being
        // a decision.
        assertTrue(
            "jam radius ${BossVariant.VARIANT_JAM_RADIUS} vs hat range " +
                "${AgentType.REDHAT.baseRange}",
            BossVariant.VARIANT_JAM_RADIUS < AgentType.REDHAT.baseRange
        )
        assertEquals(AgentType.REDHAT.baseRange, AgentType.BLUEHAT.baseRange, 0.001f)
    }

    @Test
    fun `a hat can be jammed at all`() {
        // D2 as first asked had the hats immune to JAM, and E2 asks for bosses
        // whose whole job is to jam them. If both had shipped the two rules
        // would have cancelled silently and these bosses would do nothing.
        // The decided D2 table dropped the immunity; this is the check that it
        // stays dropped.
        assertFalse("RED HAT cannot be jammed, so WHITE EYE does nothing", AgentType.REDHAT.immuneToJam)
        assertFalse("BLUE HAT cannot be jammed, so BLACK EYE does nothing", AgentType.BLUEHAT.immuneToJam)
        assertTrue("FIREWALL's immunity is its identity", AgentType.FIREWALL.immuneToJam)
    }

    // --------------------------------------------------------- through a run

    @Test
    fun `a real gauntlet run eventually meets its own bosses`() {
        val seen = (1..60).map { seed ->
            val engine = GameEngine(random = Random(seed))
            engine.selectMap(Maps.HUGGING_FACE)
            engine.startNewRun()
            engine.restore(
                wave = 39, serverHp = 100, crypto = 0, placements = emptyList(),
                attacksBlocked = 0, cryptoEarned = 0, bossesDefeated = 0,
                serverDamageTaken = 0, agentsDeployed = 0, agentUpgrades = 0
            )
            engine.startNextWave()
            engine.activeBossVariant
        }.toSet()

        assertTrue(
            "wave 40 on the gauntlet only ever produced $seen",
            seen.any { it in gauntletBosses }
        )
    }

    @Test
    fun `the first level never meets them`() {
        val seen = (1..60).map { seed ->
            val engine = GameEngine(random = Random(seed))
            engine.selectMap(Maps.PERIMETER)
            engine.startNewRun()
            engine.restore(
                wave = 39, serverHp = 100, crypto = 0, placements = emptyList(),
                attacksBlocked = 0, cryptoEarned = 0, bossesDefeated = 0,
                serverDamageTaken = 0, agentsDeployed = 0, agentUpgrades = 0
            )
            engine.startNextWave()
            engine.activeBossVariant
        }.toSet()

        assertTrue("the perimeter rolled $seen", seen.none { it in gauntletBosses })
    }
}
