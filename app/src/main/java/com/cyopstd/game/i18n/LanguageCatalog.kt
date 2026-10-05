package com.cyopstd.game.i18n

/**
 * The languages with a translation file, besides English. Added to as each
 * language's `strings_i18n.xml` in its `res/values-` folder lands.
 */
internal object LanguageCatalog {
    val translated: List<Language> = listOf(
        Language("es", "Español (España)", "Spanish (Spain)")
    )
}
