package com.cyopstd.game

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.cyopstd.game.core.GameMode
import com.cyopstd.game.save.PlayerStats
import com.cyopstd.game.ui.menu.MainMenuScreen
import com.cyopstd.game.ui.menu.MenuBoot
import com.cyopstd.game.ui.theme.CyOpsTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Not an assertion: the frames a human looks at. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w960dp-h440dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MenuBootPreview {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun preview() {
        var elapsed by mutableFloatStateOf(0f)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CyOpsTheme {
                Box(Modifier.fillMaxSize()) {
                    MainMenuScreen(
                        hasSavedRun = true,
                        stats = PlayerStats(highestWave = 42),
                        budget = 9_000,
                        firmwareLevel = 12,
                        adsRemoved = false,
                        availableModes = GameMode.entries,
                        selectedMode = GameMode.STANDARD,
                        backgroundAnimation = false,
                        bootAlpha = { MenuBoot.elementAlpha(it, elapsed) },
                        ledGlow = MenuBoot.ledGlow(elapsed),
                        onSelectMode = {}, onPlay = {}, onContinue = {}, onAgents = {},
                        onFirmware = {}, onCodex = {}, onStore = {}, onLoadout = {},
                        onPlayAccount = {}, onLeaderboard = {}, onStatistics = {},
                        onSettings = {}, onAbout = {}, onExit = {}
                    )
                }
            }
        }

        File("build/previews").mkdirs()
        for (at in listOf(0.15f, 0.42f, 0.70f, 1.05f, 1.35f, MenuBoot.TOTAL_SECONDS)) {
            compose.runOnUiThread { elapsed = at }
            compose.mainClock.advanceTimeBy(16)
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(
                view.width.coerceAtLeast(1),
                view.height.coerceAtLeast(1),
                Bitmap.Config.ARGB_8888
            )
            compose.runOnUiThread { view.draw(Canvas(bitmap)) }
            val ms = (at * 1000).toInt()
            File("build/previews/menu-boot-$ms.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
}
