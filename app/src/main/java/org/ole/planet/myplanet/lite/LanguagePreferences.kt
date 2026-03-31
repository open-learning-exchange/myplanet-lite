/**
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-12-27
 */

package org.ole.planet.myplanet.lite

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

object LanguagePreferences {

    private const val PREFS_NAME = "server_preferences"
    private const val KEY_SELECTED_LANGUAGE = "selected_language"
    private const val DEFAULT_LANGUAGE_TAG = "en"

    fun applySavedLocale(context: Context, supportedLanguageTags: List<String>) {
        val savedLanguage = getSelectedLanguage(context, supportedLanguageTags)
        val currentTags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        if (currentTags != savedLanguage) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(savedLanguage))
        }
    }

    fun getSelectedLanguage(context: Context, supportedLanguageTags: List<String>): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedLanguage = prefs.getString(KEY_SELECTED_LANGUAGE, null)?.takeIf { it.isNotBlank() }
        if (savedLanguage != null) {
            return savedLanguage
        }

        val deviceLanguageTag = Locale.getDefault().toLanguageTag()
        val exactMatch = supportedLanguageTags.firstOrNull { tag ->
            tag.equals(deviceLanguageTag, ignoreCase = true)
        }
        if (exactMatch != null) {
            return exactMatch
        }

        val deviceLanguage = Locale.getDefault().language
        val languageMatch = supportedLanguageTags.firstOrNull { tag ->
            Locale.forLanguageTag(tag).language.equals(deviceLanguage, ignoreCase = true)
        }

        return languageMatch ?: DEFAULT_LANGUAGE_TAG
    }

    fun setSelectedLanguage(
        context: Context,
        languageTag: String,
        supportedLanguageTags: List<String>
    ): Boolean {
        val sanitized = languageTag.ifBlank { DEFAULT_LANGUAGE_TAG }
        val current = getSelectedLanguage(context, supportedLanguageTags)
        if (sanitized.equals(current, ignoreCase = true)) {
            return false
        }
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_SELECTED_LANGUAGE, sanitized)
            .apply()
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(sanitized))
        return true
    }
}
