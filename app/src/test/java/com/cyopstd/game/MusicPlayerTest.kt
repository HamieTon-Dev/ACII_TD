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
        assertEquals(22, tracks.size)
        assertEquals(listOf(0, 0) + (1..10).flatMap { listOf(it, it) }, tracks.map { it.level })
        // The owner's naming: "CyOps TD - Level X (Y)", the menu's first.
        assertEquals("CyOps TD - Main Menu (1)", tracks[0].title)
        assertEquals("CyOps TD - Main Menu (1)", tracks[0].label)
        assertEquals("CyOps TD - Level 1 (1)", tracks[2].title)
        assertEquals("CyOps TD - Level 10 (2)", tracks[21].title)
        assertEquals("CyOps TD - Level 10 (2).mp3", tracks[21].fileName)
        assertEquals("CyOps TD - Level 1 (1) \u00B7 NETWORK PERIMETER", tracks[2].label)
        assertEquals(4, MusicLibrary.firstTrackOf(Maps.HUGGING_FACE))
        assertEquals(12, MusicLibrary.firstTrackOf(Maps.TRIDENT))
    }

    @Test
    fun `a saved pick is found again by a key that survives new builds`() {
        val track = MusicLibrary.tracks[9]
        assertEquals("duck_usb|2", track.key)
        assertEquals(9, MusicLibrary.indexOfKey("duck_usb|2"))
        assertEquals(0, MusicLibrary.indexOfKey("menu|1"))
        assertNull(MusicLibrary.indexOfKey(null))
        assertNull("a track that no longer exists is ignored", MusicLibrary.indexOfKey("gone|9"))
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

    private fun store(owned: Boolean, onSave: () -> Unit = {}, onBuy: (com.cyopstd.game.store.Sku) -> Unit = {}) {
        compose.setContent {
            CyOpsTheme {
                com.cyopstd.game.ui.menu.StoreScreen(
                    entitlements = if (owned) com.cyopstd.game.store.Entitlements().plus(com.cyopstd.game.store.Sku.SOUNDTRACK)
                    else com.cyopstd.game.store.Entitlements(),
                    budget = 0, prices = emptyMap(), status = com.cyopstd.game.store.BillingStatus.READY,
                    backgroundAnimation = false, onBuy = onBuy, onRestore = {}, onBack = {},
                    onSaveSoundtrack = onSave
                )
            }
        }
    }

    @Test
    fun `the store sells the soundtrack, then offers to save it to the phone`() {
        var bought: com.cyopstd.game.store.Sku? = null
        store(owned = false, onBuy = { bought = it })
        compose.onNodeWithText("BUY CyOps TD SOUNDTRACK \u00B7 \$4.99").performScrollTo().performClick()
        assertEquals(com.cyopstd.game.store.Sku.SOUNDTRACK, bought)
        compose.onNodeWithText("SAVE SOUNDTRACK TO PHONE").assertDoesNotExist()
    }

    @Test
    fun `once owned, the soundtrack can be saved to the phone`() {
        var saves = 0
        store(owned = true, onSave = { saves++ })
        compose.onNodeWithText("SAVE SOUNDTRACK TO PHONE").performScrollTo().performClick()
        assertEquals(1, saves)
    }
}
