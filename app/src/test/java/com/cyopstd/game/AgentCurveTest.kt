package com.cyopstd.game

import com.cyopstd.game.core.WorldGeometry
import com.cyopstd.game.engine.GameEngine
import com.cyopstd.game.model.AgentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The shape of the agent roster: what more money is supposed to buy you.
 *
 * These exist because it used to buy you almost nothing. A ROOT ADMIN costs
 * eight times a FIREWALL and delivered *less* damage per crypto (0.232 against
 * 0.259); the CRYPTOGRAPHER at 105 had half the base damage of the ANALYST at
 * 95; and the longest range in the game belonged to a 55-crypto unit. Saving up
 * was a worse plan than buying starters, which is the opposite of what a tower
 * defence should reward.
 */
class AgentCurveTest {

    /** Damage a lone agent of this type puts out per second, before upgrades. */
    private val AgentType.dps: Float get() = baseDamage * baseFireRate

    /**
     * The agents whose job is damage. The TARPIT (a slow field), the SANDBOX
     * (a slow) and the NETWORK ARCHITECT (a buff aura) are priced for what they
     * do to other things, so they are judged elsewhere.
     */
    private val damageDealers = listOf(
        AgentType.FIREWALL, AgentType.IPS, AgentType.ANALYST,
        AgentType.CRYPTOGRAPHER, AgentType.ZERO_DAY_HUNTER,
        AgentType.QUANTUM_DEFENDER, AgentType.ROOT_ADMIN
    )

    @Test
    fun `paying more buys more damage per crypto, not less`() {
        val starter = AgentType.FIREWALL.dps / AgentType.FIREWALL.cost
        for (type in damageDealers) {
            val value = type.dps / type.cost
            assertTrue(
                "${type.name} costs ${type.cost} and returns $value damage per " +
                    "crypto against the 40-crypto FIREWALL's $starter",
                value >= starter * 0.92f
            )
        }

        // And the top of the roster must be clearly better than the bottom,
        // or there is no reason to ever save up.
        val top = AgentType.ROOT_ADMIN.dps / AgentType.ROOT_ADMIN.cost
        assertTrue(
            "the 320-crypto ROOT ADMIN returns $top per crypto against the " +
                "40-crypto FIREWALL's $starter",
            top > starter * 1.25f
        )
    }

    @Test
    fun `every step up the roster puts out more damage`() {
        // Output, not per-shot damage: the IPS is deliberately a fast, light
        // burst unit and hits for less per shot than the FIREWALL below it
        // while putting out 70% more. What must rise with price is what the
        // agent actually delivers. The CRYPTOGRAPHER used to cost more than
        // the ANALYST and deliver a fifth less.
        val byPrice = damageDealers.sortedBy { it.cost }
        for (i in 1 until byPrice.size) {
            val cheaper = byPrice[i - 1]
            val dearer = byPrice[i]
            assertTrue(
                "${dearer.name} (${dearer.cost}) puts out ${dearer.dps} where " +
                    "${cheaper.name} (${cheaper.cost}) puts out ${cheaper.dps}",
                dearer.dps > cheaper.dps
            )
        }
    }

    @Test
    fun `reach grows with price`() {
        // The IDS is the declared exception: a cheap unit whose whole identity
        // is seeing further than anything else.
        val byPrice = (damageDealers + AgentType.SANDBOX + AgentType.NETWORK_ARCHITECT)
            .sortedBy { it.cost }
        for (i in 1 until byPrice.size) {
            assertTrue(
                "${byPrice[i].name} reaches ${byPrice[i].baseRange} where the " +
                    "cheaper ${byPrice[i - 1].name} reaches ${byPrice[i - 1].baseRange}",
                byPrice[i].baseRange > byPrice[i - 1].baseRange
            )
        }
        assertTrue(
            "the IDS should still see furthest of all",
            AgentType.entries.all { it == AgentType.IDS || it.baseRange <= AgentType.IDS.baseRange }
        )
    }

    @Test
    fun `no agent's damage depends on a dice roll`() {
        // The ZERO-DAY HUNTER used to roll a 25% chance of a 3x critical, so
        // the same tower against the same threat could deal wildly different
        // damage. Every shot is now identical. Proven by running the same
        // scenario under different generators and comparing what was dealt.
        fun damageDealt(seed: Int): List<Float> {
            val engine = GameEngine(random = Random(seed))
            engine.startNewRun()
            engine.restore(
                wave = 20, serverHp = 900, crypto = 100_000, placements = emptyList(),
                attacksBlocked = 0, cryptoEarned = 0, bossesDefeated = 0,
                serverDamageTaken = 0, agentsDeployed = 0, agentUpgrades = 0
            )
            engine.isAgentUnlocked = { true }
            val node = WorldGeometry.nodesByCoverage.first().id
            engine.placeAgent(AgentType.ZERO_DAY_HUNTER, node)
            assertTrue("the hunter was not placed", engine.activeAgentCount() == 1)
            engine.startNextWave()

            val seen = LinkedHashSet<Float>()
            repeat(2000) {
                engine.update(1f / 60f, 1f)
                for (projectile in engine.projectiles.items) {
                    if (projectile.active) seen += projectile.damage
                }
            }
            return seen.toList()
        }

        val first = damageDealt(1)
        assertTrue("the hunter never fired", first.isNotEmpty())
        assertEquals(
            "the hunter dealt ${first.size} different damage values in one run",
            1, first.size
        )
        for (seed in 2..5) {
            assertEquals(
                "a different generator changed what the hunter deals",
                first, damageDealt(seed)
            )
        }
    }

    @Test
    fun `the map offers no spot beyond every agent's reach`() {
        // Node eligibility is judged at the longest range in the roster. If an
        // agent's range moves and that constant does not follow, spots appear
        // that literally nothing can use.
        val longest = AgentType.entries.maxOf { it.baseRange }
        for (node in WorldGeometry.nodes) {
            assertTrue(
                "node ${node.id} is ${node.laneDistance} from a route and the " +
                    "furthest-seeing agent reaches $longest",
                node.laneDistance <= longest
            )
        }
    }
}
