package com.cyopstd.game

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.cyopstd.game.ui.splash.DeveloperSplashScreen
import com.cyopstd.game.ui.splash.SplashScreen
import com.cyopstd.game.ui.theme.CyOpsTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Not an assertion: the frame a human looks at. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w960dp-h440dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IdentPreview {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `developer ident`() {
        capture("developer-ident") { DeveloperSplashScreen(onFinished = {}) }
    }

    @Test
    fun `boot splash after the ident took the studio mark`() {
        capture("boot-splash") { SplashScreen(onFinished = {}) }
    }

    private fun capture(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CyOpsTheme { Box(Modifier.fillMaxSize()) { content() } }
        }
        compose.mainClock.advanceTimeBy(700)

        // Drawn out of the view hierarchy rather than through captureToImage,
        // which waits for a redraw that never comes while the test clock is
        // being advanced by hand. Same reason as MenuBackdropRenderTest.
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(
            view.width.coerceAtLeast(1),
            view.height.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        compose.runOnUiThread { view.draw(Canvas(bitmap)) }

        File("build/previews").mkdirs()
        File("build/previews/$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
