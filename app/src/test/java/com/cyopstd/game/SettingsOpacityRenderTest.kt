package com.cyopstd.game

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.cyopstd.game.save.GameSettings
import com.cyopstd.game.ui.settings.SettingsScreen
import com.cyopstd.game.ui.theme.CyOpsTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The owner could not find the in-game panel opacity slider (2026-09-26).
 * On a landscape phone it must be on SETTINGS without scrolling.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w800dp-h360dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsOpacityRenderTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `the opacity slider is on screen on a phone`() {
        compose.setContent {
            CyOpsTheme {
                Box(Modifier.fillMaxSize()) {
                    SettingsScreen(
                        settings = GameSettings(),
                        backgroundAnimation = false,
                        onUpdate = {},
                        onResetProgress = {},
                        onBack = {}
                    )
                }
            }
        }
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { view.draw(Canvas(bitmap)) }
        File("build/previews").mkdirs()
        File("build/previews/settings-phone.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithText("IN-GAME PANEL OPACITY").assertIsDisplayed()
    }
}
