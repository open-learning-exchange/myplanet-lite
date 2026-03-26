package org.ole.planet.myplanet.lite.model

/**
 * Represents a language option in the application.
 *
 * @property languageTag The language tag (e.g., "en", "es").
 * @property labelRes The resource ID of the language name (e.g., R.string.language_name_english).
 * @property levelArrayRes Optional resource ID of the level options array for this language.
 */
data class LanguageOption(
    val languageTag: String,
    val labelRes: Int,
    val levelArrayRes: Int = 0
)
