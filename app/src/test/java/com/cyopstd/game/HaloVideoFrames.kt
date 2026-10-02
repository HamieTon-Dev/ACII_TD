package com.cyopstd.game

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.cyopstd.game.ui.common.HaloBackdrop
import com.cyopstd.game.ui.theme.CyOpsTheme
import com.cyopstd.game.ui.theme.Palette
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Frames of the menu backdrop for a preview video, only when asked for:
 * the HALO_FRAMES environment variable. Writes `build/halo-frames/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "w800dp-h360dp-land-mdpi")
class HaloVideoFrames {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `write frames`() {
        if (System.getenv("HALO_FRAMES") == null) return
        val seconds = (System.getenv("HALO_SECONDS") ?: "12").toFloat()
        val fps = 20
        val speed = (System.getenv("HALO_SPEED") ?: "1").toFloat()
        val moment = mutableStateOf(0f)
        compose.setContent {
            CyOpsTheme {
                Box(Modifier.fillMaxSize().background(Palette.Background)) {
                    HaloBackdrop(Modifier.fillMaxSize(), stillTime = moment.value)
                }
            }
        }
        val out = File("build/halo-frames").apply { deleteRecursively(); mkdirs() }
        val frames = (seconds * fps).toInt()
        for (i in 0 until frames) {
            moment.value = 5f + i * speed / fps
            compose.waitForIdle()
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            compose.runOnUiThread { view.draw(Canvas(bitmap)) }
            File(out, "f%04d.png".format(i)).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
