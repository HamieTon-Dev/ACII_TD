package com.cyopstd.game.i18n

/**
 * The languages with a translation file, besides English. Every one is in
 * `res/` under the folder Android reads for its tag (`I18nCatalogTest` checks
 * the two match). Native name first, so a player can find their own.
 */
internal object LanguageCatalog {
    val translated: List<Language> = listOf(
        Language("es", "Español (España)", "Spanish (Spain)"),
        Language("es-419", "Español (Latinoamérica)", "Spanish (Latin America)"),
        Language("pt", "Português (Brasil)", "Portuguese (Brazil)"),
        Language("fr", "Français", "French"),
        Language("de", "Deutsch", "German")
    )
}
