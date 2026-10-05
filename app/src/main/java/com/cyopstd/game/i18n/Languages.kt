package com.cyopstd.game.i18n

import android.app.Activity
import android.content.Context
import android.content.Intent

/**
 * One language the game ships in.
 *
 * [tag] is the BCP-47 tag handed to `Locale.forLanguageTag`; Android finds the
 * matching `res/values-*` folder itself (including the old `iw`/`in`/`ji`
 * folder names for Hebrew, Indonesian and Yiddish).
 */
data class Language(
    val tag: String,
    /** The language's name in itself, as the selector lists it. */
    val nativeName: String,
    /** Its name in English, under the native one. */
    val englishName: String
)

/**
 * The languages the selector offers and the player's pick.
 *
 * The pick lives in plain SharedPreferences rather than the settings
 * DataStore on purpose: it has to be readable synchronously in
 * `Application.onCreate`, before any text is read, and a DataStore read there
 * would either block on I/O or arrive too late.
 */
object Languages {

    private const val PREFS = "cyops_language"
    private const val KEY = "tag"

    /**
     * Every language there is a translation file for, English first and the
     * rest by English name. `LanguagesTest` checks each one has its
     * `strings_i18n.xml`, and that every translation file is listed here.
     */
    val all: List<Language> = listOf(
        Language("en", "English", "English")
    ) + LanguageCatalog.translated.sortedBy { it.englishName }

    fun saved(context: Context): String? =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)

    /** The saved pick as a listed language, or null for "follow the phone". */
    fun current(context: Context): Language? = saved(context)?.let { tag -> all.firstOrNull { it.tag == tag } }

    /**
     * Saves [language] (null: follow the phone) and restarts the game in it.
     *
     * A restart rather than a refresh: text that enums and objects read when
     * first touched would otherwise stay in the old language until the next
     * launch. [beforeRestart] is where the caller writes anything that must
     * not be lost (the run in progress); it runs before the process ends.
     */
    fun applyAndRestart(activity: Activity, language: Language?, beforeRestart: () -> Unit = {}) {
        val prefs = activity.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // commit(), not apply(): the process ends a moment from now.
        if (language == null) prefs.edit().remove(KEY).commit() else prefs.edit().putString(KEY, language.tag).commit()
        beforeRestart()
        val launch = activity.packageManager.getLaunchIntentForPackage(activity.packageName) ?: return
        val restart = Intent.makeRestartActivityTask(launch.component)
        activity.startActivity(restart)
        activity.finishAffinity()
        Runtime.getRuntime().exit(0)
    }
}
