package com.cyopstd.game

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.cyopstd.game.audio.AudioEngine
import com.cyopstd.game.audio.ChiptuneComposer
import com.cyopstd.game.audio.LevelMusic
import com.cyopstd.game.audio.MusicLibrary
import com.cyopstd.game.core.Maps
import com.cyopstd.game.ui.game.MUSIC_PLAYER_LOCKED_NOTE
import com.cyopstd.game.ui.game.MusicPlayerPanel
import com.cyopstd.game.ui.game.MusicPlayerState
import com.cyopstd.game.ui.theme.CyOpsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The pause-menu music player (owner, 2026-10-01): every level's tracks in a
 * drop-down, previous / play-pause / next, unlocked by completing level 5.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class MusicPlayerTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `the library lists both tracks of every level with music, in level order`() {
        val tracks = MusicLibrary.tracks
        assertEquals(10, tracks.size)
        assertEquals((1..5).flatMap { listOf(it, it) }, tracks.map { it.level })
        assertEquals("L1 · NETWORK PERIMETER · 1", tracks[0].label)
        assertEquals("L5 · DDoS · Cassette Noir 2", tracks[9].label)
        assertEquals(2, MusicLibrary.firstTrackOf(Maps.HUGGING_FACE))
        assertNull("levels 6-10 have no tracks yet", MusicLibrary.firstTrackOf(Maps.TRIDENT))
    }

    private var selected: Int? = -1
    private var playPauses = 0
    private var nexts = 0
    private var previouses = 0

    private fun show(unlocked: Boolean, picked: Int? = null) {
        compose.setContent {
            CyOpsTheme {
                MusicPlayerPanel(
                    state = MusicPlayerState(unlocked, MusicLibrary.tracks.map { it.label }, picked, paused = false),
                    onSelect = { selected = it },
                    onPlayPause = { playPauses++ },
                    onPrevious = { previouses++ },
                    onNext = { nexts++ }
                )
            }
        }
    }

    @Test
    fun `before level 5 every control explains how to unlock it and does nothing`() {
        show(unlocked = false)
        compose.onNodeWithTag("music-player-lock", useUnmergedTree = true).assertExists()
        for (tag in listOf("music-play-pause", "music-next", "music-previous", "music-track-dropdown")) {
            compose.onNodeWithTag(tag).performClick()
            compose.onNodeWithText(MUSIC_PLAYER_LOCKED_NOTE).assertIsDisplayed()
        }
        assertEquals(0, playPauses + nexts + previouses)
        assertEquals(-1, selected)
        compose.onNodeWithTag("music-track-3").assertDoesNotExist()
    }

    @Test
    fun `once unlocked, any track can be picked, and the transport works`() {
        show(unlocked = true)
        compose.onNodeWithTag("music-player-lock", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag("music-track-dropdown").performClick()
        compose.onNodeWithTag("music-track-7").performScrollTo().performClick()
        assertEquals(7, selected)
        compose.onNodeWithTag("music-play-pause").performClick()
        compose.onNodeWithTag("music-next").performClick()
        compose.onNodeWithTag("music-previous").performClick()
        assertEquals(1, playPauses)
        assertEquals(1, nexts)
        assertEquals(1, previouses)
        compose.onNodeWithText(MUSIC_PLAYER_LOCKED_NOTE).assertDoesNotExist()
    }

    @Test
    fun `the drop-down can go back to the level's own music`() {
        show(unlocked = true, picked = 4)
        compose.onNodeWithText(MusicLibrary.tracks[4].label).assertIsDisplayed()
        compose.onNodeWithTag("music-track-dropdown").performClick()
        compose.onNodeWithText("LEVEL MUSIC (AUTO)").performClick()
        assertNull(selected)
    }

    private fun audio(): AudioEngine =
        AudioEngine(ApplicationProvider.getApplicationContext<Application>())
            .apply { applyVolumes(music = 0.8f, sfx = 0.5f) }

    @Test
    fun `a picked track replaces the level music, never plays beside it, and pauses alone`() {
        val engine = audio()
        engine.setInMatch(true, LevelMusic.LEVEL_ONE, ChiptuneComposer.Track.GAME)
        engine.playLibraryTrack(6)
        assertEquals(1, engine.matchTracksWanting)

        engine.setMusicPausedByPlayer(true)
        assertFalse(engine.musicWanted)
        engine.setMusicPausedByPlayer(false)
        assertEquals(1, engine.matchTracksWanting)

        engine.followLevelMusic()
        assertEquals(1, engine.matchTracksWanting)

        // Leaving the match hands back to the menu with no match track left.
        engine.playLibraryTrack(2)
        engine.setInMatch(false)
        assertEquals(0, engine.matchTracksWanting)
        assertTrue(engine.musicWanted)
        engine.release()
    }
}
