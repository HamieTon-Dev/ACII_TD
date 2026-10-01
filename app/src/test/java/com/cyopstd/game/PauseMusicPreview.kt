package com.cyopstd.game

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.cyopstd.game.audio.MusicLibrary
import com.cyopstd.game.ui.game.MusicPlayerPanel
import com.cyopstd.game.ui.game.MusicPlayerState
import com.cyopstd.game.ui.game.PauseOverlay
import com.cyopstd.game.ui.theme.CyOpsTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The pause menu with its music player, for showing the owner. Not an
 * assertion suite; writes `build/previews/pause-music-*.png`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w800dp-h360dp-land-xhdpi")
class PauseMusicPreview {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun show(unlocked: Boolean, selected: Int?) {
        compose.setContent {
            CyOpsTheme {
                PauseOverlay(
                    wave = 42, onResume = {}, onRestart = {}, onSettings = {}, onMainMenu = {},
                    musicPlayer = { modifier ->
                        MusicPlayerPanel(
                            state = MusicPlayerState(unlocked, MusicLibrary.tracks.map { it.label }, selected, false),
                            onSelect = {}, onPlayPause = {}, onPrevious = {}, onNext = {}, modifier = modifier
                        )
                    }
                )
            }
        }
        compose.waitForIdle()
    }

    private fun snap(name: String) {
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { view.draw(Canvas(bitmap)) }
        File("build/previews").mkdirs()
        File("build/previews/pause-music-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun `locked, after a tap`() {
        show(unlocked = false, selected = null)
        compose.onNodeWithTag("music-play-pause").performClick()
        snap("locked")
    }

    @Test
    fun `restart asks first`() {
        show(unlocked = true, selected = null)
        compose.onNodeWithText("RESTART").performClick()
        snap("restart-confirm")
    }

    @Test
    fun `unlocked, a track picked`() {
        show(unlocked = true, selected = 7)
        snap("unlocked")
    }

    @Test
    fun `unlocked, list open`() {
        show(unlocked = true, selected = 7)
        compose.onNodeWithTag("music-track-dropdown").performClick()
        compose.waitForIdle()
        // The list is a popup in a window of its own; draw every window, in order.
        val global = Class.forName("android.view.WindowManagerGlobal")
        val instance = global.getMethod("getInstance").invoke(null)
        @Suppress("UNCHECKED_CAST")
        val views = global.getDeclaredField("mViews").apply { isAccessible = true }
            .get(instance) as List<android.view.View>
        val base = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(base.width, base.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        compose.runOnUiThread {
            for (v in views) {
                if (v.width == 0) continue
                val at = IntArray(2)
                v.getLocationOnScreen(at)
                canvas.save()
                canvas.translate(at[0].toFloat(), at[1].toFloat())
                v.draw(canvas)
                canvas.restore()
            }
        }
        File("build/previews").mkdirs()
        File("build/previews/pause-music-list.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
