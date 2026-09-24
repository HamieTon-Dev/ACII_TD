package com.cyopstd.game

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import com.cyopstd.game.state.GameViewModel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Music when the app is not on screen.
 *
 * A game that keeps playing over whatever you opened instead gets reported as
 * a bug, correctly. What shipped was worse than that: backgrounding the app
 * called `setInMatch(false)`, which does not stop anything — it *starts the
 * menu music*. Background the game mid-run and it swapped to the menu track
 * and played on.
 *
 * Nothing in the suite could see it, because nothing asked the obvious
 * question: is the game making noise right now. So that is now a property
 * rather than something buried in two MediaPlayers.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BackgroundAudioTest {

    private fun freshViewModel(musicVolume: Float = 0.5f): GameViewModel {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val repository = TestStores.isolatedRepository()
        // Written before the view model reads it, rather than changed
        // afterwards: settings are persisted asynchronously, so a change made
        // after construction is briefly overwritten by the stored value coming
        // back, and the test would be measuring that race rather than the fix.
        runBlocking { repository.updateSettings { it.copy(musicVolume = musicVolume) } }
        val viewModel = GameViewModel(application, repository)
        shadowOf(Looper.getMainLooper()).idle()
        return viewModel
    }

    @Test
    fun `backgrounding the app silences it`() {
        val viewModel = freshViewModel()
        viewModel.onMenuShown()
        assertTrue("the menu should be playing something", viewModel.musicWanted)

        viewModel.onAppPaused()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(
            "the game is still asking to play music while it is not on screen",
            viewModel.musicWanted
        )
    }

    @Test
    fun `backgrounding a match does not switch it to the menu track`() {
        // The exact shape of the bug: pausing called setInMatch(false), which
        // starts the menu music rather than stopping anything.
        val viewModel = freshViewModel()
        viewModel.startNewGame()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(viewModel.musicWanted)

        viewModel.onAppPaused()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse("backgrounding a run left music playing", viewModel.musicWanted)
    }

    @Test
    fun `coming back resumes the screen the player left`() {
        val viewModel = freshViewModel()
        viewModel.onMenuShown()
        viewModel.onAppPaused()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(viewModel.musicWanted)

        viewModel.onAppResumed()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(
            "returning to the menu left it silent; resume used to only restart " +
                "music when a match was running",
            viewModel.musicWanted
        )
    }

    @Test
    fun `a player who turned music off stays silent through the whole cycle`() {
        val viewModel = freshViewModel(musicVolume = 0f)

        viewModel.onMenuShown()
        assertFalse("music is off", viewModel.musicWanted)
        viewModel.onAppPaused()
        viewModel.onAppResumed()
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse("resuming turned the music back on", viewModel.musicWanted)
    }
}
