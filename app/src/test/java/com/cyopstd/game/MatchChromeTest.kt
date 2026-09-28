package com.cyopstd.game

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.cyopstd.game.state.GameViewModel
import com.cyopstd.game.ui.common.BuildStamp
import com.cyopstd.game.ui.game.GameScreen
import com.cyopstd.game.ui.theme.CyOpsTheme
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * ☆1 and ☆2 (owner, 2026-09-28): in a match, the build id and the player's
 * tag sit under the status line instead of on top of the buttons; the top
 * strip is smaller and shows € earned this run; nothing overlaps.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
abstract class MatchChromeTestBase(private val name: String) {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun textOf(node: SemanticsNode): String =
        node.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text } ?: ""

    private fun Rect.overlaps(other: Rect): Boolean =
        left < other.right - 0.5f && other.left < right - 0.5f &&
            top < other.bottom - 0.5f && other.top < bottom - 0.5f

    @Test
    fun `nothing in the top strip or the control bar overlaps`() {
        val viewModel = GameViewModel(ApplicationProvider.getApplicationContext<Application>(), TestStores.isolatedRepository())
        shadowOf(Looper.getMainLooper()).idle()
        viewModel.startNewGame()
        shadowOf(Looper.getMainLooper()).idle()
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CyOpsTheme { GameScreen(viewModel = viewModel, onExitToMenu = {}, onOpenSettings = {}) }
        }
        compose.mainClock.advanceTimeBy(64)

        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        compose.runOnUiThread { view.draw(Canvas(bitmap)) }
        File("build/previews").mkdirs()
        File("build/previews/match-chrome-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

        // The identity line exists, carries the build id, and sits in the
        // control bar clear of every button.
        val identity = compose.onNodeWithTag("match-identity").fetchSemanticsNode()
        assertTrue(textOf(identity).contains(BuildStamp.id))
        val root = compose.onRoot().fetchSemanticsNode()
        val all = mutableListOf<SemanticsNode>()
        fun walk(n: SemanticsNode) { all += n; n.children.forEach(::walk) }
        walk(root)
        val buttons = all.filter { it.config.contains(SemanticsProperties.Role) || it.config.contains(androidx.compose.ui.semantics.SemanticsActions.OnClick) }
            .filter { textOf(it).isNotBlank() || it.children.any { c -> textOf(c).isNotBlank() } }
        for (b in buttons) {
            assertTrue("identity overlaps ${textOf(b)}${b.children.joinToString { textOf(it) }}",
                !identity.boundsInRoot.overlaps(b.boundsInRoot))
        }

        // Text in the top strip never runs into other text in it.
        val hudBottom = compose.onNodeWithTag("hud-run-budget").fetchSemanticsNode().boundsInRoot.bottom + 12f
        val texts = all.filter { textOf(it).isNotBlank() && it.boundsInRoot.bottom <= hudBottom && it.boundsInRoot.width > 0f }
        for (i in texts.indices) for (j in i + 1 until texts.size) {
            val a = texts[i]; val b = texts[j]
            if (a.boundsInRoot.overlaps(b.boundsInRoot) && !isAncestor(a, b) && !isAncestor(b, a)) {
                throw AssertionError("'${textOf(a)}' overlaps '${textOf(b)}' in the top strip on $name")
            }
        }
        compose.onNodeWithText("\u20AC THIS RUN").fetchSemanticsNode()
        assertTrue(compose.onAllNodesWithTag("hud-run-budget").fetchSemanticsNodes().size == 1)
    }

    private fun isAncestor(a: SemanticsNode, b: SemanticsNode): Boolean {
        var p = b.parent
        while (p != null) { if (p.id == a.id) return true; p = p.parent }
        return false
    }
}

@Config(sdk = [33], qualifiers = "w568dp-h320dp-land-mdpi")
class MatchChromeTinyPhoneTest : MatchChromeTestBase("tiny-568x320")

@Config(sdk = [33], qualifiers = "w800dp-h360dp-land-xhdpi")
class MatchChromePhoneTest : MatchChromeTestBase("phone-800x360")
