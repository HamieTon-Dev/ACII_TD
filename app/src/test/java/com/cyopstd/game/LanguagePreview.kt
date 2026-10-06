package com.cyopstd.game

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.cyopstd.game.i18n.Languages
import com.cyopstd.game.i18n.Tr
import com.cyopstd.game.save.GameSettings
import com.cyopstd.game.save.PlayerStats
import com.cyopstd.game.ui.menu.MainMenuScreen
import com.cyopstd.game.ui.settings.SettingsScreen
import com.cyopstd.game.ui.theme.CyOpsTheme
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the main menu and SETTINGS in one language, to look at:
 * `I18N_PREVIEW=es` writes `build/previews/lang-es-menu.png` and `-settings.png`.
 * Skipped unless the variable is set. Not an assertion suite.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w960dp-h440dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LanguagePreview {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun tagOrSkip(): String {
        val tag = System.getenv("I18N_PREVIEW")
        assumeTrue(!tag.isNullOrBlank())
        Tr.init(ApplicationProvider.getApplicationContext<Application>(), tag)
        return tag!!
    }

    private fun shoot(tag: String, name: String) {
        compose.mainClock.advanceTimeBy(700)
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { view.draw(Canvas(bitmap)) }
        File("build/previews").mkdirs()
        File("build/previews/lang-$tag-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        Tr.init(ApplicationProvider.getApplicationContext<Application>(), null)
    }

    @Test
    fun menu() {
        val tag = tagOrSkip()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CyOpsTheme {
                Box(Modifier.fillMaxSize()) {
                    MainMenuScreen(
                        hasSavedRun = true, stats = PlayerStats(highestWave = 64), budget = 12_500L,
                        firmwareLevel = 12, adsRemoved = false, backgroundAnimation = false,
                        onPlay = {}, onContinue = {}, onAgents = {}, onFirmware = {}, onCodex = {}, onStore = {},
                        onLoadout = {}, onPlayAccount = {}, onLeaderboard = {},
                        onStatistics = {}, onSettings = {}, onAbout = {}, onExit = {}
                    )
                }
            }
        }
        shoot(tag, "menu")
    }

    @Test
    fun settings() {
        val tag = tagOrSkip()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CyOpsTheme {
                Box(Modifier.fillMaxSize()) {
                    SettingsScreen(
                        settings = GameSettings(), backgroundAnimation = false, onUpdate = {},
                        onResetProgress = {}, onBack = {},
                        language = Languages.all.firstOrNull { it.tag == tag }
                    )
                }
            }
        }
        shoot(tag, "settings")
    }
}
