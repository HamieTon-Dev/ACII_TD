package com.cyopstd.game

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.cyopstd.game.i18n.Languages
import com.cyopstd.game.i18n.Tr
import com.cyopstd.game.i18n.tr
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The translation engine at runtime: a pick changes the lines, English is the fallback. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LanguagesTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()

    @After
    fun backToEnglish() = Tr.init(app, null)

    @Test
    fun `with no pick the game reads English`() {
        Tr.init(app, null)
        assertEquals("SETTINGS", tr("SETTINGS"))
        assertEquals("WAVE 12 SECURED", tr("WAVE {0} SECURED", 12))
    }

    @Test
    fun `every shipped language actually changes the text, placeholders filled`() {
        for (language in Languages.all.filter { it.tag != "en" }) {
            Tr.init(app, language.tag)
            assertNotEquals("${language.tag}: SETTINGS untranslated", "SETTINGS", tr("SETTINGS"))
            val line = tr("WAVE {0} SECURED", 12)
            assertTrue("${language.tag}: placeholder not filled in '$line'", "12" in line && "{0}" !in line)
        }
    }

    @Test
    fun `a line no language has falls back to English`() {
        Tr.init(app, "es")
        assertEquals("Not a catalogued line", tr("Not a catalogued line"))
        assertEquals("AJUSTES", tr("SETTINGS"))
    }

    @Test
    fun `placeholders fill in any order and leave other braces alone`() {
        assertEquals("b a {x} {9}", Tr.fill("{1} {0} {x} {9}", arrayOf("a", "b")))
    }
}
