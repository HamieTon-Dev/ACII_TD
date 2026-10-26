package com.cyopstd.game.i18n

import android.app.Activity
import android.content.Context
import android.content.Intent
import java.util.Locale

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

    /** The first-launch picker has been answered, or never applied (an update over an old install). */
    private const val CHOSEN = "chosen"

    /** One-shot: the restart that follows a first-launch pick skips the studio ident. */
    private const val SKIP_IDENT = "skip_ident"

    /**
     * Every language there is a translation file for, English first and the
     * rest by English name. `LanguagesTest` checks each one has its
     * `strings_i18n.xml`, and that every translation file is listed here.
     */
    val all: List<Language> = listOf(
        Language("en", "English", "English")
    ) + LanguageCatalog.translated.sortedBy { it.englishName }

    /** "Choose your language" in every language, for the first-launch picker's title. */
    val chooseTitle: Map<String, String> = mapOf(
        "en" to "CHOOSE YOUR LANGUAGE", "ar" to "اختر لغتك", "zh-CN" to "选择你的语言",
        "zh-TW" to "選擇你的語言", "cs" to "VYBERTE SI JAZYK", "da" to "VÆLG DIT SPROG",
        "nl" to "KIES JE TAAL", "fi" to "VALITSE KIELI", "fr" to "CHOISISSEZ VOTRE LANGUE",
        "de" to "WÄHLE DEINE SPRACHE", "hi" to "अपनी भाषा चुनें", "id" to "PILIH BAHASAMU",
        "it" to "SCEGLI LA TUA LINGUA", "ja" to "言語を選択", "ko" to "언어를 선택하세요",
        "nb" to "VELG SPRÅK", "pl" to "WYBIERZ JĘZYK", "pt" to "ESCOLHA SEU IDIOMA",
        "ru" to "ВЫБЕРИТЕ ЯЗЫК", "es" to "ELIGE TU IDIOMA", "es-419" to "ELIGE TU IDIOMA",
        "sv" to "VÄLJ SPRÅK", "th" to "เลือกภาษาของคุณ", "tr" to "DİLİNİ SEÇ",
        "uk" to "ОБЕРІТЬ МОВУ", "vi" to "CHỌN NGÔN NGỮ"
    )

    /** The listed language closest to [locale], English when none is. */
    fun match(locale: Locale): String {
        val lang = when (locale.language) {
            "in" -> "id"
            "no", "nn" -> "nb"
            else -> locale.language
        }
        return when (lang) {
            "zh" -> if (locale.script == "Hant" || locale.country in setOf("TW", "HK", "MO")) "zh-TW" else "zh-CN"
            "es" -> if (locale.country.isEmpty() || locale.country == "ES") "es" else "es-419"
            else -> all.firstOrNull { it.tag == lang }?.tag ?: "en"
        }
    }

    /**
     * Called once from `CyOpsApplication.onCreate`, before [Tr.init] (owner, 2026-10-26: a
     * language picker on first launch, "not if the game is updated"). Decided the first time
     * this version runs and stored: a fresh install shows the picker; an install that was
     * updated to this version (or already has a pick) never does, and keeps what it showed.
     */
    fun start(context: Context) {
        val prefs = prefs(context)
        if (!prefs.contains(CHOSEN)) {
            prefs.edit().putBoolean(CHOSEN, prefs.contains(KEY) || wasUpdated(context)).apply()
        }
    }

    /** True when the first-launch picker should show (fresh install, nothing picked yet). */
    fun needsPicker(context: Context): Boolean = !prefs(context).getBoolean(CHOSEN, true)

    /** Read once per launch: true when this start is the restart after a first-launch pick. */
    fun takeSkipIdent(context: Context): Boolean {
        val prefs = prefs(context)
        if (!prefs.getBoolean(SKIP_IDENT, false)) return false
        prefs.edit().remove(SKIP_IDENT).apply()
        return true
    }

    /**
     * The first-launch picker's choice. When it is the language already showing (the phone's),
     * it is saved and the game simply carries on; otherwise the game restarts in it, straight
     * to the boot screen (the studio ident has just played). Returns true when no restart is needed.
     */
    fun pickFirstLaunch(context: Context, language: Language): Boolean {
        val prefs = prefs(context)
        val activity = context as? Activity
        if (language.tag == match(Tr.locale) || activity == null) {
            // No restart: already showing, or nothing to restart from (it shows from the next start).
            prefs.edit().putString(KEY, language.tag).putBoolean(CHOSEN, true).commit()
            return true
        }
        prefs.edit().putBoolean(SKIP_IDENT, true).commit()
        applyAndRestart(activity, language)
        return false
    }

    /** First install and last update differ once the app has been updated at least once. */
    private fun wasUpdated(context: Context): Boolean = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.lastUpdateTime > info.firstInstallTime
    } catch (_: Exception) {
        true
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

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
        if (language == null) prefs.edit().remove(KEY).putBoolean(CHOSEN, true).commit()
        else prefs.edit().putString(KEY, language.tag).putBoolean(CHOSEN, true).commit()
        beforeRestart()
        val launch = activity.packageManager.getLaunchIntentForPackage(activity.packageName) ?: return
        val restart = Intent.makeRestartActivityTask(launch.component)
        activity.startActivity(restart)
        activity.finishAffinity()
        Runtime.getRuntime().exit(0)
    }
}
