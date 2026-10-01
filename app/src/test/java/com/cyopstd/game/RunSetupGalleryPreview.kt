package com.cyopstd.game

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.cyopstd.game.core.GameMode
import com.cyopstd.game.core.Maps
import com.cyopstd.game.save.PlayerStats
import com.cyopstd.game.ui.menu.RunSetupScreen
import com.cyopstd.game.ui.theme.CyOpsTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The NEW RUN screen tall enough to show all ten levels at once, for showing
 * the owner the locked rows. Not an assertion suite; writes
 * `build/previews/run-setup-all-levels.png`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w800dp-h1180dp-land-mdpi")
class RunSetupGalleryPreview {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `render all ten levels, five open`() {
        compose.setContent {
            CyOpsTheme {
                RunSetupScreen(
                    stats = PlayerStats(highestWave = 132, highestWaveHackAi = 104,
                        highestWaveByMap = mapOf("ddos" to 64)),
                    availableModes = listOf(GameMode.STANDARD, GameMode.HACK_AI),
                    selectedMode = GameMode.HACK_AI,
                    availableMaps = Maps.all.take(5),
                    selectedMap = Maps.DUCK_USB,
                    backgroundAnimation = false,
                    onSelectMode = {}, onSelectMap = {}, onStart = {}, onBack = {}
                )
            }
        }
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { view.draw(Canvas(bitmap)) }
        File("build/previews").mkdirs()
        File("build/previews/run-setup-all-levels.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
