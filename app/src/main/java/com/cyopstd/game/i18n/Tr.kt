package com.cyopstd.game.i18n

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale
import java.util.zip.CRC32

/**
 * The translation engine (owner, 2026-10-05: *"I need the game to be available
 * in 100+ languages ... an underlying engine with selector"*).
 *
 * Every line of text the player sees is written in the code as English and
 * wrapped in [tr]: `tr("PAUSED")`, `tr("{0} LIMIT — {1} MAX", name, max)`.
 * `I18nCatalogTest` collects every such English line into
 * `res/values/strings_i18n.xml`, each under a name derived from the text
 * itself (`t_` + its CRC-32), and the languages are the same names in
 * `res/values-<lang>/strings_i18n.xml`. At runtime [tr] looks the English up
 * by that name in the chosen language and falls back to the English when a
 * language has no line for it, so a missing translation is never a crash or a
 * blank, only English.
 *
 * Why English keys and not hand-named resources: there are well over a
 * thousand lines, most of them inside enums and objects that have no Context,
 * and naming each one by hand is a thousand chances to wire the wrong line to
 * the wrong place. The English *is* the key, so it cannot drift from itself.
 *
 * Placeholders are `{0}`, `{1}`… rather than `%1$s`, so a translator cannot
 * break formatting and Kotlin source needs no `\$` escaping.
 *
 * The language is fixed for the life of the process: enums and objects read
 * their text once, when first touched. Changing language therefore restarts
 * the game ([Languages.applyAndRestart]); that is what makes every line, not
 * just most of them, change.
 */
object Tr {
    @Volatile
    private var resources: Resources? = null

    @Volatile
    private var packageName: String = ""

    /** English line → resource id, or 0 for "not in the catalogue". */
    private val ids = HashMap<String, Int>()

    /** The locale the game is showing, for anything that formats numbers or dates. */
    @Volatile
    var locale: Locale = Locale.ENGLISH
        private set

    /**
     * Called once, from `CyOpsApplication.onCreate`, before anything reads text.
     * [languageTag] is the player's pick, or null to follow the phone.
     */
    fun init(context: Context, languageTag: String? = Languages.saved(context)) {
        val base = context.applicationContext ?: context
        val res = if (languageTag.isNullOrBlank()) {
            base.resources
        } else {
            val config = Configuration(base.resources.configuration)
            config.setLocale(Locale.forLanguageTag(languageTag))
            base.createConfigurationContext(config).resources
        }
        synchronized(ids) { ids.clear() }
        packageName = base.packageName
        locale = res.configuration.locales.get(0) ?: Locale.ENGLISH
        resources = res
    }

    /** The current language's line for [english], with `{0}`… filled from [args]. */
    fun t(english: String, vararg args: Any?): String {
        val template = lookup(english) ?: english
        return if (args.isEmpty()) template else fill(template, args)
    }

    private fun lookup(english: String): String? {
        val res = resources ?: return null
        val id = synchronized(ids) {
            ids.getOrPut(english) { res.getIdentifier(keyOf(english), "string", packageName) }
        }
        if (id == 0) return null
        return try {
            res.getString(id)
        } catch (_: Resources.NotFoundException) {
            null
        }
    }

    /** The resource name an English line is stored under. */
    fun keyOf(english: String): String {
        val crc = CRC32()
        crc.update(english.toByteArray(Charsets.UTF_8))
        return "t_" + java.lang.Long.toHexString(crc.value).padStart(8, '0')
    }

    /** Replaces `{0}`, `{1}`… with [args]; anything else in braces is left alone. */
    internal fun fill(template: String, args: Array<out Any?>): String {
        val out = StringBuilder(template.length + 16)
        var i = 0
        while (i < template.length) {
            val c = template[i]
            if (c == '{') {
                val close = template.indexOf('}', i + 1)
                val index = if (close > i + 1) template.substring(i + 1, close).toIntOrNull() else null
                if (index != null && index in args.indices) {
                    out.append(args[index])
                    i = close + 1
                    continue
                }
            }
            out.append(c)
            i++
        }
        return out.toString()
    }
}

/** Shorthand for [Tr.t]: the player's language for this English line. */
fun tr(english: String, vararg args: Any?): String = Tr.t(english, *args)
