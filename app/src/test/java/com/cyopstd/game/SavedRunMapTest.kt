package com.cyopstd.game

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.cyopstd.game.core.GameMode
import com.cyopstd.game.core.Maps
import com.cyopstd.game.save.GameRepository
import com.cyopstd.game.save.SavedAgent
import com.cyopstd.game.save.SavedRun
import com.cyopstd.game.state.GameViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * A saved run remembers which level and which mode it was played on.
 *
 * This is the trap the backlog flagged before the second map existed, and it
 * is worth restating because the failure is silent. Agents are saved **by node
 * id**, and a node id is a position in a per-map array — node 17 is one patch
 * of ground on one level and a completely different one on another. Restore a
 * run onto the wrong map and the player's entire board is scattered somewhere
 * else, with no error and nothing in the UI to say what happened.
 *
 * The mode has the same shape of bug with a different symptom: a HACK:AI run
 * resuming as a standard one gets the wrong integrity, the wrong health curve
 * and the wrong spawn pressure.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SavedRunMapTest {

    private lateinit var repository: GameRepository

    /**
     * Writes a save without blocking the main thread.
     *
     * `runBlocking { repository.saveRun(...) }` deadlocks here: Robolectric
     * runs the test *on* the main looper, and blocking that thread stops the
     * very one the write has to hand back through. The write goes to IO and
     * the looper is pumped while it lands, which is what production does
     * anyway.
     */
    private fun saveRunAndWait(run: SavedRun) {
        var done = false
        CoroutineScope(Dispatchers.IO).launch {
            repository.saveRun(run)
            done = true
        }
        pumpUntil { done }
        assertTrue("the save never landed", done)
    }

    private fun readSavedRun(): SavedRun? {
        var result: SavedRun? = null
        var done = false
        CoroutineScope(Dispatchers.IO).launch {
            result = repository.savedRun.first()
            done = true
        }
        pumpUntil { done }
        return result
    }

    /**
     * Pumps for a fixed stretch regardless of any condition.
     *
     * Needed where the thing being waited for is a write that has not been
     * *issued* yet. `pumpUntil { readSavedRun() == null }` looked like it
     * waited for startNewGame's clear to land, and on a fresh store it
     * returned instantly because there was never a save to clear -- so the
     * clear arrived after the persist below and wiped the run under test.
     * A condition that is already true is not a wait.
     */
    private fun settle(millis: Long = 1_500) {
        val deadline = System.currentTimeMillis() + millis
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
    }

    private fun pumpUntil(ready: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 15_000
        while (!ready() && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun freshViewModel(): GameViewModel {
        val application = ApplicationProvider.getApplicationContext<Application>()
        repository = TestStores.isolatedRepository()
        val viewModel = GameViewModel(application, repository)
        shadowOf(Looper.getMainLooper()).idle()
        return viewModel
    }

    @Test
    fun `the two maps really do disagree about what a node id means`() {
        // The premise. If this ever stops being true the rest of this file is
        // guarding against nothing.
        val a = Maps.PERIMETER.node(17)!!
        val b = Maps.HUGGING_FACE.node(17)!!
        assertNotEquals(
            "node 17 is the same place on both maps, so nothing here is at risk",
            a.x to a.y,
            b.x to b.y
        )
    }

    @Test
    fun `a saved run records its level and its mode`() {
        val viewModel = freshViewModel()
        viewModel.startNewGame()
        // startNewGame clears any previous save on its own coroutine, and
        // that clear has to land before the persist below or it wipes the run
        // this test is about to read.
        settle()

        viewModel.leaveMatch()
        // leaveMatch persists on its own scope, so the read has to wait for
        // the write rather than assume one frame of the looper covered it.
        var saved: SavedRun? = null
        pumpUntil {
            saved = readSavedRun()
            saved != null
        }
        assertEquals(Maps.PERIMETER.id, saved?.mapId)
        assertEquals(GameMode.STANDARD.id, saved?.modeId)
    }

    @Test
    fun `resuming restores the level the run was played on`() {
        val viewModel = freshViewModel()
        saveRunAndWait(
                SavedRun(
                    wave = 4,
                    serverHp = 60,
                    crypto = 200,
                    agents = listOf(SavedAgent(3, "FIREWALL", 1, 0)),
                    mapId = Maps.HUGGING_FACE.id,
                    modeId = GameMode.HACK_AI.id
                )
            )

        var loaded = false
        viewModel.continueGame(onLoaded = { loaded = true })
        pumpUntil { loaded }

        assertTrue("the run did not load at all", loaded)
        assertEquals(
            "a run saved on the second map came back on the first",
            Maps.HUGGING_FACE.id,
            viewModel.engine.map.id
        )
        assertEquals(
            "a HACK:AI run came back as a standard one",
            GameMode.HACK_AI,
            viewModel.engine.mode
        )
    }

    @Test
    fun `an agent comes back on the ground it was left on`() {
        // The actual harm, stated as the player would experience it.
        val viewModel = freshViewModel()
        val nodeId = 3
        saveRunAndWait(
                SavedRun(
                    wave = 2,
                    serverHp = 100,
                    crypto = 0,
                    agents = listOf(SavedAgent(nodeId, "FIREWALL", 1, 0)),
                    mapId = Maps.HUGGING_FACE.id
                )
            )
        var loaded = false
        viewModel.continueGame(onLoaded = { loaded = true })
        pumpUntil { loaded }

        val agent = viewModel.engine.agentAt(nodeId)
        val expected = Maps.HUGGING_FACE.node(nodeId)!!
        assertEquals("the agent is not on the node it was saved at", expected.x, agent?.x)
        assertEquals(expected.y, agent?.y)
    }

    @Test
    fun `a save written before maps existed resumes on the original one`() {
        // Every save already on a player's phone has no mapId. It has to come
        // back on the level it was actually played on, which is the only one
        // that existed when it was written.
        val viewModel = freshViewModel()
        val legacy = SavedRun(wave = 7, serverHp = 80, crypto = 50)
        assertEquals("the default is not the original map", Maps.PERIMETER.id, legacy.mapId)

        saveRunAndWait(legacy)
        var loaded = false
        viewModel.continueGame(onLoaded = { loaded = true })
        pumpUntil { loaded }
        assertEquals(Maps.PERIMETER.id, viewModel.engine.map.id)
    }

    @Test
    fun `an unknown level does not crash or land somewhere arbitrary`() {
        // A save from a newer build, or a corrupted one.
        val viewModel = freshViewModel()
        saveRunAndWait(SavedRun(wave = 3, serverHp = 90, mapId = "atlantis"))
        var loaded = false
        viewModel.continueGame(onLoaded = { loaded = true })
        pumpUntil { loaded }
        assertEquals(Maps.PERIMETER.id, viewModel.engine.map.id)
    }
}
