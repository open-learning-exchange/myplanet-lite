/**
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-12-01
 */

package org.ole.planet.myplanet.lite.profile

import android.content.Context
import androidx.annotation.ArrayRes
import java.util.Locale
import org.ole.planet.myplanet.lite.R

object GenderTranslator {
    private val SUPPORTED_ARRAYS = intArrayOf(
        R.array.signup_gender_options_language_en,
        R.array.signup_gender_options_language_es,
        R.array.signup_gender_options_language_fr,
        R.array.signup_gender_options_language_pt,
        R.array.signup_gender_options_language_ar,
        R.array.signup_gender_options_language_so,
        R.array.signup_gender_options_language_ne,
        R.array.signup_gender_options_language_hi
    )

    fun toEnglish(context: Context, label: String?): String? {
        val trimmed = label?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            return null
        }

        val normalized = trimmed.lowercase(Locale.ROOT)
        val englishValues = context.resources.getStringArray(R.array.signup_gender_options_language_en)
        SUPPORTED_ARRAYS.forEach { arrayRes ->
            val localizedValues = context.resources.getStringArray(arrayRes)
            val index = localizedValues.indexOfFirst { option ->
                option.trim().lowercase(Locale.ROOT) == normalized
            }
            if (index >= 0) {
                return englishValues.getOrNull(index)
            }
        }

        return trimmed
    }

    fun toLocalized(
        context: Context,
        englishOrLocalizedLabel: String?,
        @ArrayRes targetArrayRes: Int
    ): String? {
        val englishValue = toEnglish(context, englishOrLocalizedLabel)?.trim()
        if (englishValue.isNullOrEmpty()) {
            return null
        }

        val englishValues = context.resources.getStringArray(R.array.signup_gender_options_language_en)
        val normalized = englishValue.lowercase(Locale.ROOT)
        val index = englishValues.indexOfFirst { option ->
            option.trim().lowercase(Locale.ROOT) == normalized
        }
        if (index == -1) {
            return englishValue
        }

        val localizedValues = context.resources.getStringArray(targetArrayRes)
        return localizedValues.getOrNull(index) ?: englishValue
    }
}
