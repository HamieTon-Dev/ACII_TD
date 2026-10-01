package com.cyopstd.game

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import com.cyopstd.game.core.GameMode
import com.cyopstd.game.core.Maps
import com.cyopstd.game.save.PlayerStats
import com.cyopstd.game.ui.menu.MainMenuScreen
import com.cyopstd.game.ui.menu.RunSetupScreen
import com.cyopstd.game.ui.theme.CyOpsTheme
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * ♡7 (owner, 2026-09-30): the main menu, smaller and uncluttered, legible on
 * every phone; level and difficulty on their own screen. Renders both on a
 * tiny and a normal phone and fails if any two labels overlap.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class MenuRedesignTestBase(private val name: String) {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun text(n: SemanticsNode) =
        n.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text } ?: ""

    private fun snapshotAndCheck(file: String) {
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { view.draw(Canvas(bitmap)) }
        File("build/previews").mkdirs()
        File("build/previews/$file-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val all = mutableListOf<SemanticsNode>()
        fun walk(n: SemanticsNode) { all += n; n.children.forEach(::walk) }
        walk(compose.onRoot().fetchSemanticsNode())
        val labels = all.filter { text(it).isNotBlank() && it.boundsInRoot.width > 0f && it.boundsInRoot.height > 0f }
        for (i in labels.indices) for (j in i + 1 until labels.size) {
            val a = labels[i].boundsInRoot; val b = labels[j].boundsInRoot
            val overlap = a.left < b.right - 1 && b.left < a.right - 1 && a.top < b.bottom - 1 && b.top < a.bottom - 1
            assertTrue("'${text(labels[i])}' overlaps '${text(labels[j])}' on $name", !overlap)
        }
    }

    @Test
    fun `the main menu fits without overlap`() {
        compose.setContent {
            CyOpsTheme {
                MainMenuScreen(
                    hasSavedRun = true, stats = PlayerStats(highestWave = 132, totalBossesDefeated = 40),
                    budget = 12_500, firmwareLevel = 40, adsRemoved = false, backgroundAnimation = false,
                    onPlay = {}, onContinue = {}, onAgents = {}, onFirmware = {}, onCodex = {}, onStore = {},
                    onLoadout = {}, onPlayAccount = {}, onLeaderboard = {}, onStatistics = {}, onSettings = {},
                    onAbout = {}, onExit = {}
                )
            }
        }
        snapshotAndCheck("main-menu")
    }

    @Test
    fun `the run setup fits without overlap`() {
        compose.setContent {
            CyOpsTheme {
                RunSetupScreen(
                    stats = PlayerStats(highestWave = 132), availableModes = GameMode.entries.toList(),
                    selectedMode = GameMode.STANDARD, availableMaps = Maps.all.take(5),
                    selectedMap = Maps.DDOS, backgroundAnimation = false,
                    onSelectMode = {}, onSelectMap = {}, onStart = {}, onBack = {}
                )
            }
        }
        snapshotAndCheck("run-setup")
    }
}

@Config(sdk = [33], qualifiers = "w568dp-h320dp-land-mdpi")
class MenuRedesignTinyPhoneTest : MenuRedesignTestBase("tiny-568x320")

@Config(sdk = [33], qualifiers = "w800dp-h360dp-land-xhdpi")
class MenuRedesignPhoneTest : MenuRedesignTestBase("phone-800x360")
