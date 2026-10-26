package com.cyopstd.game

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.test.core.app.ApplicationProvider
import com.cyopstd.game.i18n.Language
import com.cyopstd.game.i18n.Languages
import com.cyopstd.game.i18n.Tr
import com.cyopstd.game.ui.splash.FirstLaunchLanguageScreen
import com.cyopstd.game.ui.splash.TITLE_CYCLE_MS
import com.cyopstd.game.ui.theme.CyOpsTheme
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The first-launch language picker (owner, 2026-10-26): it follows the studio ident on a fresh
 * install only. An update must never show it, or every existing player would be asked a question
 * they already answered in Settings (or never needed to).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1600dp-h760dp-land-mdpi")
class FirstLaunchLanguageTest {

    @get:Rule
    val compose = createComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val prefs get() = app.getSharedPreferences("cyops_language", Context.MODE_PRIVATE)

    @Before
    fun freshInstall() {
        prefs.edit().clear().commit()
        setInstallTimes(first = 1_000L, last = 1_000L)
        Tr.init(app, null)
    }

    @After
    fun backToEnglish() {
        prefs.edit().clear().commit()
        Tr.init(app, null)
    }

    private fun setInstallTimes(first: Long, last: Long) {
        val info = shadowOf(app.packageManager).getInternalMutablePackageInfo(app.packageName)
        info.firstInstallTime = first
        info.lastUpdateTime = last
    }

    // ------------------------------------------------------------ when it shows

    @Test
    fun `a fresh install shows the picker`() {
        Languages.start(app)
        assertTrue(Languages.needsPicker(app))
    }

    @Test
    fun `an update over an existing install never shows it`() {
        setInstallTimes(first = 1_000L, last = 9_000L)
        Languages.start(app)
        assertFalse(Languages.needsPicker(app))
    }

    @Test
    fun `an install that already picked a language in Settings never shows it`() {
        prefs.edit().putString("tag", "fr").commit()
        Languages.start(app)
        assertFalse(Languages.needsPicker(app))
    }

    @Test
    fun `the decision is made once, so a later update cannot change it`() {
        setInstallTimes(first = 1_000L, last = 9_000L)
        Languages.start(app)
        setInstallTimes(first = 1_000L, last = 1_000L)
        Languages.start(app)
        assertFalse(Languages.needsPicker(app))
    }

    @Test
    fun `picking the language already showing saves it and needs no restart`() {
        Languages.start(app)
        val english = Languages.all.first { it.tag == "en" }
        assertTrue(Languages.pickFirstLaunch(app, english))
        assertFalse(Languages.needsPicker(app))
        assertEquals("en", Languages.saved(app))
    }

    @Test
    fun `the restart flag is read once`() {
        prefs.edit().putBoolean("skip_ident", true).commit()
        assertTrue(Languages.takeSkipIdent(app))
        assertFalse(Languages.takeSkipIdent(app))
    }

    @Test
    fun `phone languages map onto the listed ones`() {
        assertEquals("zh-TW", Languages.match(Locale.forLanguageTag("zh-HK")))
        assertEquals("zh-CN", Languages.match(Locale.forLanguageTag("zh")))
        assertEquals("es-419", Languages.match(Locale.forLanguageTag("es-MX")))
        assertEquals("es", Languages.match(Locale.forLanguageTag("es-ES")))
        assertEquals("nb", Languages.match(Locale.forLanguageTag("nn-NO")))
        assertEquals("id", Languages.match(Locale("in", "ID")))
        assertEquals("en", Languages.match(Locale.US))
        assertEquals("en", Languages.match(Locale.forLanguageTag("eu")))
    }

    @Test
    fun `every language has a title in its own words`() {
        assertEquals(Languages.all.map { it.tag }.toSet(), Languages.chooseTitle.keys)
    }

    // ------------------------------------------------------------ the screen

    @Test
    fun `it lists every language and hands back the one tapped`() {
        var picked: Language? = null
        // The title cycles and the backdrop drifts forever, so Compose is never idle on its own.
        compose.mainClock.autoAdvance = false
        compose.setContent { CyOpsTheme { FirstLaunchLanguageScreen(deviceTag = "de") { picked = it } } }
        compose.mainClock.advanceTimeBy(100)

        compose.onNodeWithText("WÄHLE DEINE SPRACHE").assertIsDisplayed()
        val grid = compose.onNode(hasScrollToNodeAction())
        for (language in Languages.all) {
            grid.performScrollToNode(hasTestTag("first-language-${language.tag}"))
            compose.mainClock.advanceTimeBy(32)
            compose.onNodeWithTag("first-language-${language.tag}").assertIsDisplayed()
        }
        grid.performScrollToNode(hasTestTag("first-language-ja"))
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("first-language-ja").performClick()
        compose.mainClock.advanceTimeBy(32)
        assertEquals("ja", picked?.tag)
    }

    @Test
    fun `the title cycles through the languages, the phone's first`() {
        compose.mainClock.autoAdvance = false
        compose.setContent { CyOpsTheme { FirstLaunchLanguageScreen(deviceTag = "fr") { } } }
        compose.onNodeWithText("CHOISISSEZ VOTRE LANGUE").assertExists()
        compose.mainClock.advanceTimeBy(TITLE_CYCLE_MS + 50)
        compose.onNodeWithText("CHOOSE YOUR LANGUAGE").assertExists()
    }
}
