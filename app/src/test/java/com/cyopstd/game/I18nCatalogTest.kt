package com.cyopstd.game

import com.cyopstd.game.i18n.Languages
import com.cyopstd.game.i18n.Tr
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The translation catalogue (see `Tr`).
 *
 * Scans the source for every `tr("…")` and checks `res/values/strings_i18n.xml`
 * holds exactly those lines. Run with `I18N_WRITE=1` to (re)write that file
 * after adding or changing text. Also checks every language file against it:
 * no line the game no longer has, and every `{0}`-style placeholder kept.
 */
class I18nCatalogTest {

    private val sourceRoot = File("src/main/java")
    private val resRoot = File("src/main/res")
    private val english = File(resRoot, "values/strings_i18n.xml")

    @Test
    fun `the English catalogue holds exactly the lines the code asks for`() {
        val found = I18nScanner.scan(sourceRoot)
        if (found.problems.isNotEmpty()) {
            fail("tr() must be given plain string literals:\n" + found.problems.joinToString("\n"))
        }
        val xml = I18nXml.write(found.lines.associateBy { Tr.keyOf(it) })
        if (System.getenv("I18N_WRITE") == "1") {
            english.writeText(xml)
            return
        }
        assertTrue("missing ${english.path}: run the tests with I18N_WRITE=1", english.exists())
        assertEquals(
            "strings_i18n.xml is out of date: run the tests with I18N_WRITE=1",
            xml, english.readText()
        )
    }

    @Test
    fun `no two English lines share a key`() {
        val lines = I18nScanner.scan(sourceRoot).lines
        val byKey = lines.groupBy { Tr.keyOf(it) }.filterValues { it.size > 1 }
        assertTrue("CRC collision: $byKey", byKey.isEmpty())
    }

    @Test
    fun `every language file matches the catalogue and keeps its placeholders`() {
        val source = I18nXml.read(english)
        val problems = ArrayList<String>()
        for (file in translationFiles()) {
            val lang = file.parentFile.name
            for ((key, text) in I18nXml.read(file)) {
                val en = source[key]
                if (en == null) {
                    problems += "$lang: $key is not in the English catalogue"
                    continue
                }
                if (placeholders(en) != placeholders(text)) {
                    problems += "$lang: $key placeholders ${placeholders(text)} != English ${placeholders(en)}"
                }
            }
        }
        assertTrue(problems.take(40).joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun `the selector lists exactly the languages there are files for`() {
        val folders = translationFiles().map { it.parentFile.name }.toSet()
        val listed = Languages.all.filter { it.tag != "en" }.map { folderFor(it.tag) }.toSet()
        assertEquals("listed but no file", emptySet<String>(), listed - folders)
        assertEquals("file but not listed", emptySet<String>(), folders - listed)
        assertEquals("a language listed twice", Languages.all.size, Languages.all.map { it.tag }.toSet().size)
    }

    private fun translationFiles(): List<File> =
        resRoot.listFiles().orEmpty()
            .filter { it.isDirectory && it.name.startsWith("values-") }
            .map { File(it, "strings_i18n.xml") }
            .filter { it.exists() }

    private fun placeholders(text: String): List<String> =
        Regex("\\{\\d+}").findAll(text).map { it.value }.sorted().toList()

    companion object {
        /** The `res/values-*` folder Android reads for [tag]. */
        fun folderFor(tag: String): String {
            val locale = Locale.forLanguageTag(tag)
            val legacy = mapOf("he" to "iw", "id" to "in", "yi" to "ji")
            val lang = legacy[locale.language] ?: locale.language
            val simple = lang.length == 2 && locale.script.isEmpty() &&
                (locale.country.isEmpty() || locale.country.length == 2)
            return if (simple) {
                "values-$lang" + if (locale.country.isNotEmpty()) "-r${locale.country}" else ""
            } else {
                "values-b+" + tag.replace('-', '+')
            }
        }
    }
}

/** Finds every `tr("…")` in the Kotlin source. */
object I18nScanner {
    data class Result(val lines: Set<String>, val problems: List<String>)

    private val call = Regex("(?<![A-Za-z0-9_.])tr\\(")

    fun scan(root: File): Result {
        val lines = LinkedHashSet<String>()
        val problems = ArrayList<String>()
        root.walkTopDown().filter { it.extension == "kt" }.sortedBy { it.path }.forEach { file ->
            val src = file.readText()
            if (file.name == "Tr.kt") return@forEach
            for (m in call.findAll(src)) {
                val where = "${file.path}:${src.substring(0, m.range.first).count { it == '\n' } + 1}"
                val parsed = parseLiterals(src, m.range.last + 1)
                if (parsed == null) problems += "$where: not a plain string literal"
                else lines += parsed
            }
        }
        return Result(lines, problems)
    }

    /** One literal, or several joined with `+`, starting at [start]; null if anything else. */
    private fun parseLiterals(src: String, start: Int): String? {
        var i = skipSpace(src, start)
        val out = StringBuilder()
        while (true) {
            if (i >= src.length || src[i] != '"' || src.startsWith("\"\"\"", i)) return null
            i++
            while (true) {
                if (i >= src.length) return null
                val c = src[i]
                when {
                    c == '"' -> { i++; break }
                    c == '\n' -> return null
                    c == '$' && i + 1 < src.length && (src[i + 1] == '{' || src[i + 1].isLetter()) -> return null
                    c == '\\' -> {
                        val e = src[i + 1]
                        when (e) {
                            'n' -> out.append('\n'); 't' -> out.append('\t'); 'r' -> out.append('\r')
                            'b' -> out.append('\b')
                            '"', '\'', '\\', '$' -> out.append(e)
                            'u' -> { out.append(src.substring(i + 2, i + 6).toInt(16).toChar()); i += 4 }
                            else -> return null
                        }
                        i += 2
                    }
                    else -> { out.append(c); i++ }
                }
            }
            val j = skipSpace(src, i)
            if (j < src.length && src[j] == '+') {
                i = skipSpace(src, j + 1)
                continue
            }
            return out.toString()
        }
    }

    private fun skipSpace(src: String, from: Int): Int {
        var i = from
        while (i < src.length && src[i].isWhitespace()) i++
        return i
    }
}

/** Reads and writes `strings_i18n.xml` in the escaping Android expects. */
object I18nXml {
    fun write(lines: Map<String, String>): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
        sb.append("<!-- GENERATED by I18nCatalogTest (I18N_WRITE=1) from every tr(\"…\") in the code. Do not edit. -->\n")
        sb.append("<resources>\n")
        for ((key, text) in lines.toSortedMap()) {
            sb.append("    <string name=\"").append(key).append("\" formatted=\"false\">")
                .append(escape(text)).append("</string>\n")
        }
        sb.append("</resources>\n")
        return sb.toString()
    }

    /** Wrapped in quotes so Android keeps spaces exactly; quotes, backslashes and markup escaped. */
    fun escape(text: String): String {
        val sb = StringBuilder("\"")
        for (c in text) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\t' -> sb.append("\\t")
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                else -> sb.append(c)
            }
        }
        return sb.append('"').toString()
    }

    private val entry = Regex("<string name=\"(t_[0-9a-f]{8})\"[^>]*>(.*?)</string>", RegexOption.DOT_MATCHES_ALL)

    /** key → text, unescaped. */
    fun read(file: File): Map<String, String> =
        entry.findAll(file.readText()).associate { it.groupValues[1] to unescape(it.groupValues[2]) }

    fun unescape(raw: String): String {
        var s = raw.trim()
        if (s.length >= 2 && s.startsWith("\"") && s.endsWith("\"")) s = s.substring(1, s.length - 1)
        s = s.replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val e = s[i + 1]) {
                    'n' -> sb.append('\n'); 't' -> sb.append('\t')
                    else -> sb.append(e)
                }
                i += 2
            } else {
                sb.append(c); i++
            }
        }
        return sb.toString()
    }
}
