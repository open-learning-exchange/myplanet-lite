/*
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2026-06-10
 */

package org.ole.planet.myplanet.lite

import android.widget.ArrayAdapter
import android.widget.Filter
import androidx.core.widget.doAfterTextChanged
import org.ole.planet.myplanet.lite.profile.LearningLevelTranslator
import java.util.Locale

internal fun SignupActivity.setupLanguageOptions() {
    languageOptions = buildLanguageOptionsList()
    setupLanguageInputAdapter()
    setupLanguageInputListeners()
    initializeLanguageSelection()
}

internal fun SignupActivity.buildLanguageOptionsList(): List<SignupLanguageOption> =
    listOf(
        SignupLanguageOption(
            languageTag = "en",
            labelRes = R.string.language_name_english,
            levelArrayRes = R.array.signup_level_options_language_en,
        ),
        SignupLanguageOption(
            languageTag = "es",
            labelRes = R.string.language_name_spanish,
            levelArrayRes = R.array.signup_level_options_language_es,
        ),
        SignupLanguageOption(
            languageTag = "fr",
            labelRes = R.string.language_name_french,
            levelArrayRes = R.array.signup_level_options_language_fr,
        ),
        SignupLanguageOption(
            languageTag = "pt",
            labelRes = R.string.language_name_portuguese,
            levelArrayRes = R.array.signup_level_options_language_pt,
        ),
        SignupLanguageOption(
            languageTag = "ar",
            labelRes = R.string.language_name_arabic,
            levelArrayRes = R.array.signup_level_options_language_ar,
        ),
        SignupLanguageOption(
            languageTag = "so",
            labelRes = R.string.language_name_somali,
            levelArrayRes = R.array.signup_level_options_language_so,
        ),
        SignupLanguageOption(
            languageTag = "ne",
            labelRes = R.string.language_name_nepali,
            levelArrayRes = R.array.signup_level_options_language_ne,
        ),
        SignupLanguageOption(
            languageTag = "hi",
            labelRes = R.string.language_name_hindi,
            levelArrayRes = R.array.signup_level_options_language_hi,
        ),
    )

internal fun SignupActivity.setupLanguageInputAdapter() {
    val languageLabels = languageOptions.map { getString(it.labelRes) }
    val languageAdapter = createNonFilteringAdapter(languageLabels)
    languageInput.setAdapter(languageAdapter)
}

internal fun SignupActivity.setupLanguageInputListeners() {
    languageInput.setOnItemClickListener { _, _, position, _ ->
        val option = languageOptions[position]
        languageLayout.error = null
        applySelectedLanguage(option, resetLevel = true)
    }

    languageInput.doAfterTextChanged { text ->
        languageLayout.error = null
        val label = text?.toString()?.trim().orEmpty()
        val option = languageOptions.firstOrNull { getString(it.labelRes) == label }
        if (option != null && option != selectedLanguageOption) {
            applySelectedLanguage(option, resetLevel = false)
        }
    }
}

internal fun SignupActivity.initializeLanguageSelection() {
    val existingLabel =
        languageInput.text
            ?.toString()
            ?.trim()
            .orEmpty()
    val matchedOption = languageOptions.firstOrNull { getString(it.labelRes) == existingLabel }

    if (matchedOption != null) {
        applySelectedLanguage(matchedOption, resetLevel = false)
        return
    }

    val defaultOption = findDefaultLanguageOption()
    languageInput.setText(getString(defaultOption.labelRes), false)
    applySelectedLanguage(defaultOption, resetLevel = true)
}

internal fun SignupActivity.findDefaultLanguageOption(): SignupLanguageOption {
    val systemLanguage = Locale.getDefault().language.lowercase(Locale.ROOT)
    return languageOptions.firstOrNull { it.languageTag == systemLanguage } ?: languageOptions.first()
}

internal fun SignupActivity.applySelectedLanguage(
    option: SignupLanguageOption,
    resetLevel: Boolean,
) {
    if (selectedLanguageOption == option && !resetLevel) {
        return
    }

    selectedLanguageOption = option

    val levelValues = getLocalizedLevelValues(option)
    val levelAdapter = createNonFilteringAdapter(levelValues)
    levelInput.setAdapter(levelAdapter)

    val localizedLevel =
        LearningLevelTranslator
            .toLocalized(this, levelInput.text?.toString(), option.levelArrayRes)
    val shouldClearLevel = resetLevel || localizedLevel.isNullOrBlank()
    if (shouldClearLevel) {
        levelInput.setText("", false)
        levelLayout.error = null
    } else {
        levelInput.setText(localizedLevel, false)
    }
}

internal fun SignupActivity.getLocalizedLevelValues(option: SignupLanguageOption): List<String> {
    val locale = Locale.forLanguageTag(option.languageTag)
    val baseConfig = resources.configuration
    val localizedConfig =
        android.content.res.Configuration(baseConfig).apply {
            setLocale(locale)
        }
    val localizedContext = createConfigurationContext(localizedConfig)
    return localizedContext.resources.getStringArray(option.levelArrayRes).toList()
}

internal fun SignupActivity.createNonFilteringAdapter(items: List<String>): ArrayAdapter<String> =
    object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, items) {
        internal val filter =
            object : Filter() {
                override fun performFiltering(constraint: CharSequence?): FilterResults =
                    FilterResults().apply {
                        values = items
                        count = items.size
                    }

                override fun publishResults(
                    constraint: CharSequence?,
                    results: FilterResults?,
                ) {
                    notifyDataSetChanged()
                }
            }

        override fun getFilter(): Filter = filter
    }
