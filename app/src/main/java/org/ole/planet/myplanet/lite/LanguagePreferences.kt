/*
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-12-27
 */

package org.ole.planet.myplanet.lite

import android.content.Context
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import org.ole.planet.myplanet.lite.model.LanguageOption
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider
import java.util.Locale

object LanguagePreferences {
    private const val KEY_SELECTED_LANGUAGE = "selected_language"
    private const val DEFAULT_LANGUAGE_TAG = "en"

    fun applySavedLocale(context: Context) {
        val savedLanguage = getSelectedLanguage(context)
        val currentTags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        if (currentTags != savedLanguage) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(savedLanguage))
        }
    }

    fun getSelectedLanguage(context: Context): String {
        val prefs = SecurePreferencesProvider.getServerPreferences(context.applicationContext)
        val savedLanguage = prefs.getString(KEY_SELECTED_LANGUAGE, null)?.takeIf { it.isNotBlank() }
        if (savedLanguage != null) {
            return savedLanguage
        }

        val deviceLanguageTag = Locale.getDefault().toLanguageTag()
        val exactMatch =
            SUPPORTED_LANGUAGES.firstOrNull { tag ->
                tag.languageTag.equals(deviceLanguageTag, ignoreCase = true)
            }
        if (exactMatch != null) {
            return exactMatch.languageTag
        }

        val deviceLanguage = Locale.getDefault().language
        val languageMatch =
            SUPPORTED_LANGUAGES.firstOrNull { tag ->
                Locale.forLanguageTag(tag.languageTag).language.equals(deviceLanguage, ignoreCase = true)
            }

        return languageMatch?.languageTag ?: DEFAULT_LANGUAGE_TAG
    }

    fun setSelectedLanguage(
        context: Context,
        languageTag: String,
    ): Boolean {
        val sanitized = languageTag.ifBlank { DEFAULT_LANGUAGE_TAG }
        val current = getSelectedLanguage(context)
        if (sanitized.equals(current, ignoreCase = true)) {
            return false
        }
        val prefs = SecurePreferencesProvider.getServerPreferences(context.applicationContext)
        prefs
            .edit()
            .putString(KEY_SELECTED_LANGUAGE, sanitized)
            .apply()
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(sanitized))
        return true
    }

    val SUPPORTED_LANGUAGES =
        listOf(
            LanguageOption("en", R.string.language_name_english),
            LanguageOption("es", R.string.language_name_spanish),
            LanguageOption("fr", R.string.language_name_french),
            LanguageOption("pt", R.string.language_name_portuguese),
            LanguageOption("ne", R.string.language_name_nepali),
            LanguageOption("ar", R.string.language_name_arabic),
            LanguageOption("so", R.string.language_name_somali),
            LanguageOption("hi", R.string.language_name_hindi),
        )

    private const val LANGUAGE_TRANSITION_DURATION_MS = 250L

    fun showLanguageSelectionDialog(activity: AppCompatActivity) {
        val options = SUPPORTED_LANGUAGES
        val optionLabels = options.map { activity.getString(it.labelRes) }.toTypedArray()
        val currentLanguage = getSelectedLanguage(activity)
        val currentIndex =
            options
                .indexOfFirst { it.languageTag.equals(currentLanguage, ignoreCase = true) }
                .takeIf { it >= 0 } ?: 0

        AlertDialog
            .Builder(activity)
            .setTitle(R.string.language_selector_title)
            .setSingleChoiceItems(optionLabels, currentIndex) { dialog, which ->
                val selectedOption = options[which]
                val changed = setSelectedLanguage(activity, selectedOption.languageTag)
                dialog.dismiss()
                if (changed) {
                    restartWithLanguageAnimation(activity)
                }
            }.setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun restartWithLanguageAnimation(activity: AppCompatActivity) {
        val contentRoot: android.view.View? = activity.findViewById(android.R.id.content)
        if (contentRoot == null || activity.isFinishing || activity.isDestroyed) {
            activity.recreate()
            return
        }
        contentRoot.animate().cancel()
        contentRoot
            .animate()
            .alpha(0f)
            .setDuration(LANGUAGE_TRANSITION_DURATION_MS)
            .setInterpolator(FastOutSlowInInterpolator())
            .withEndAction { activity.recreate() }
            .start()
    }
}
