package com.cyopstd.game

import com.cyopstd.game.core.GameMode
import com.cyopstd.game.core.Maps
import com.cyopstd.game.core.WorldGeometry
import com.cyopstd.game.model.MAP_PROGRESSION
import com.cyopstd.game.save.PlayerStats
import com.cyopstd.game.save.CloudSaveMerge
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Levels 6–10 (backlog ♡1) as the owner picked them, and the per-level,
 * per-mode records (♡4).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ChapterTwoTest {

    private val chapterTwo = listOf(Maps.TRIDENT, Maps.SPIRAL, Maps.ZIGZAG, Maps.HELIX, Maps.BRAID)

    @Test
    fun `ten levels, in the owner's order, each unlocked by wave 100 on the one before`() {
        assertEquals(10, Maps.all.size)
        assertEquals(chapterTwo, Maps.all.drop(5))
        assertEquals(Maps.all.map { it.id }, MAP_PROGRESSION)
        for ((before, level) in Maps.all.zipWithNext().drop(4)) {
            assertEquals(level.id, before.id, level.unlockMapId)
            assertEquals(100, level.unlockAtWave)
            assertTrue(level.unlockedBy(bestWaveOnMap = { if (it == before.id) 100 else 0 }) { 0 })
            assertFalse(level.unlockedBy(bestWaveOnMap = { if (it == before.id) 99 else 400 }) { 400 })
        }
    }

    @Test
    fun `the picked layouts and colours, with room to build`() {
        assertEquals(3, Maps.TRIDENT.laneCount)
        assertEquals(1, Maps.SPIRAL.laneCount)
        assertEquals(1, Maps.ZIGZAG.laneCount)
        assertEquals(2, Maps.HELIX.laneCount)
        assertEquals(3, Maps.BRAID.laneCount)
        for (map in chapterTwo) {
            assertTrue("${map.id} has a theme", map.theme != null)
            assertTrue("${map.id} has only ${map.fieldNodes.size} spots", map.fieldNodes.size >= 55)
            for (lane in map.laneWaypoints) {
                assertEquals("${map.id} route must end at the rack", WorldGeometry.SERVER_X, lane.last().x, 0.1f)
                assertEquals(WorldGeometry.CORE_Y, lane.last().y, 0.1f)
            }
        }
    }

    @Test
    fun `best wave is kept per level and per mode, and merges and seeds sensibly`() = runBlocking {
        val repository = TestStores.isolatedRepository()
        repository.updateHighestWave(40, GameMode.STANDARD.id, Maps.DDOS.id)
        repository.updateHighestWave(25, GameMode.HACK_AI.id, Maps.DDOS.id)
        repository.updateHighestWave(30, GameMode.STANDARD.id, Maps.DDOS.id)
        val stats = repository.stats.first()
        assertEquals(40, stats.bestWave(Maps.DDOS.id, GameMode.STANDARD.id))
        assertEquals(25, stats.bestWave(Maps.DDOS.id, GameMode.HACK_AI.id))
        assertEquals(0, stats.bestWave(Maps.TRIDENT.id, GameMode.STANDARD.id))

        val a = PlayerStats(highestWaveByMapMode = mapOf("ddos|hack_ai" to 50, "perimeter|standard" to 10))
        val b = PlayerStats(highestWaveByMapMode = mapOf("ddos|hack_ai" to 70))
        val save = repository.exportCloudSave("x")
        val merged = CloudSaveMerge.merge(save.copy(stats = a), save.copy(stats = b, savedAtMillis = 1))
        assertEquals(70, merged.stats.bestWave("ddos", "hack_ai"))
        assertEquals(10, merged.stats.bestWave("perimeter", "standard"))
    }
}
