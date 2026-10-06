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
        Language("de", "Deutsch", "German"),
        Language("it", "Italiano", "Italian"),
        Language("ru", "Русский", "Russian"),
        Language("ja", "日本語", "Japanese"),
        Language("ko", "한국어", "Korean"),
        Language("zh-CN", "简体中文", "Chinese (Simplified)"),
        Language("zh-TW", "繁體中文", "Chinese (Traditional)"),
        Language("ar", "العربية", "Arabic"),
        Language("hi", "हिन्दी", "Hindi"),
        Language("tr", "Türkçe", "Turkish"),
        Language("pl", "Polski", "Polish"),
        Language("nl", "Nederlands", "Dutch"),
        Language("id", "Bahasa Indonesia", "Indonesian"),
        Language("vi", "Tiếng Việt", "Vietnamese"),
        Language("th", "ไทย", "Thai"),
        Language("uk", "Українська", "Ukrainian"),
        Language("sv", "Svenska", "Swedish"),
        Language("da", "Dansk", "Danish"),
        Language("nb", "Norsk bokmål", "Norwegian"),
        Language("fi", "Suomi", "Finnish"),
        Language("cs", "Čeština", "Czech")
    )
}
