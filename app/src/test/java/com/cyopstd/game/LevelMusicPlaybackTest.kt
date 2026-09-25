package com.cyopstd.game

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.cyopstd.game.audio.AudioEngine
import com.cyopstd.game.audio.ChiptuneComposer
import com.cyopstd.game.audio.LevelMusic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The wiring around the level playlists.
 *
 * Deliberately measured through [AudioEngine.musicWanted] — "is the game
 * asking for noise right now" — rather than through MediaPlayer. Whether a
 * file decodes is the device's business and it is handled by falling back;
 * *which* player has been asked to play is this game's business, and it is
 * where every music bug so far has actually lived.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LevelMusicPlaybackTest {

    private fun audio(musicVolume: Float = 0.8f): AudioEngine {
        val context = ApplicationProvider.getApplicationContext<Application>()
        return AudioEngine(context).apply { applyVolumes(music = musicVolume, sfx = 0.5f) }
    }

    @Test
    fun `starting a match on a level asks that level's music to play`() {
        val engine = audio()
        assertFalse("nothing should be playing before a screen is chosen", engine.musicWanted)
        engine.setInMatch(true, LevelMusic.LEVEL_TWO, ChiptuneComposer.Track.GAME)
        assertTrue("the gauntlet started in silence", engine.musicWanted)
    }

    @Test
    fun `changing level does not leave two tracks running`() {
        // The failure this prevents is audible and unmistakable: two pieces of
        // music at once. It has happened once already, when a HACK:AI run was
        // followed by a standard one.
        val engine = audio()
        engine.setInMatch(true, LevelMusic.LEVEL_ONE, ChiptuneComposer.Track.GAME)
        engine.setInMatch(false)
        engine.setInMatch(true, LevelMusic.LEVEL_TWO, ChiptuneComposer.Track.BOTTLE)
        assertEquals(
            "exactly one match track should want to play",
            1,
            engine.matchTracksWanting
        )
    }

    @Test
    fun `leaving a match hands the screen back to the menu`() {
        val engine = audio()
        engine.setInMatch(true, LevelMusic.LEVEL_ONE, ChiptuneComposer.Track.GAME)
        engine.setInMatch(false)
        assertEquals("a level track is still running in the menu", 0, engine.matchTracksWanting)
        assertTrue("the menu came back silent", engine.musicWanted)
    }

    @Test
    fun `backgrounding silences the level music too`() {
        val engine = audio()
        engine.setInMatch(true, LevelMusic.LEVEL_ONE, ChiptuneComposer.Track.GAME)
        engine.stopMusic()
        assertFalse("a backgrounded game is still asking to play", engine.musicWanted)
    }

    @Test
    fun `coming back resumes the level rather than the menu`() {
        val engine = audio()
        engine.setInMatch(true, LevelMusic.LEVEL_TWO, ChiptuneComposer.Track.GAME)
        engine.stopMusic()
        engine.startMusic()
        assertEquals("the level music did not come back", 1, engine.matchTracksWanting)
    }

    @Test
    fun `a player who turned music off gets no level music either`() {
        val engine = audio(musicVolume = 0f)
        engine.setInMatch(true, LevelMusic.LEVEL_ONE, ChiptuneComposer.Track.GAME)
        engine.startMusic()
        assertFalse("music is off and something still wants to play", engine.musicWanted)
    }

    @Test
    fun `a level with no music of its own still gets a track`() {
        // The third map is not built; a fourth might land before its music
        // does. Passing null must mean "play the generated one", not "play
        // nothing".
        val engine = audio()
        engine.setInMatch(true, null, ChiptuneComposer.Track.GAME)
        assertEquals("a level with no files of its own went silent", 1, engine.matchTracksWanting)
    }
}
