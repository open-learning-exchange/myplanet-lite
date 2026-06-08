/**
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-12-12
 */

package org.ole.planet.myplanet.lite

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.CheckBox
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.BundleCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.squareup.moshi.Json
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.ole.planet.myplanet.lite.auth.AuthDependencies
import org.ole.planet.myplanet.lite.dashboard.DashboardServerPreferences
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveyStatusStore
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveySubmissionsRepository
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveySubmissionsRepository.SubmissionAnswer
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveySubmissionsRepository.SubmissionParent
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveySubmissionsRepository.SubmissionTeam
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveySubmissionsRepository.SurveySubmission
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyChoice
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyDocument
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyQuestion
import org.ole.planet.myplanet.lite.profile.GENDER_FEMALE
import org.ole.planet.myplanet.lite.profile.GENDER_MALE
import org.ole.planet.myplanet.lite.profile.GENDER_OTHER
import org.ole.planet.myplanet.lite.profile.LearningLevelTranslator
import org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore
import org.ole.planet.myplanet.lite.profile.StoredCredentials
import org.ole.planet.myplanet.lite.profile.UserProfile
import org.ole.planet.myplanet.lite.profile.UserProfileDatabase
import org.ole.planet.myplanet.lite.survey.DashboardLocalSurveyRepository
import org.ole.planet.myplanet.lite.surveys.SurveyTranslationManager
import org.ole.planet.myplanet.lite.surveys.SurveyTranslationManager.TranslatedQuestion
import org.ole.planet.myplanet.lite.util.NetworkUtils
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider
import androidx.core.graphics.toColorInt

internal fun SurveyWizardFragment.applyProfileDefaultsForCourseContent() {
    if (courseId.isNullOrBlank()) {
        return
    }
    val profile = UserProfileDatabase.getInstance(requireContext()).getProfile() ?: return
    var hasOptionalDetails = false
    fun assignIfEmpty(current: String?, incoming: String?): String? {
        return if (current.isNullOrBlank() && !incoming.isNullOrBlank()) {
            hasOptionalDetails = true
            incoming
        } else {
            current
        }
    }
    respondent.gender = assignIfEmpty(respondent.gender, profile.gender)
    respondent.firstName = assignIfEmpty(respondent.firstName, profile.firstName)
    respondent.middleName = assignIfEmpty(respondent.middleName, profile.middleName)
    respondent.lastName = assignIfEmpty(respondent.lastName, profile.lastName)
    respondent.email = assignIfEmpty(respondent.email, profile.email)
    respondent.phoneNumber = assignIfEmpty(respondent.phoneNumber, profile.phoneNumber)
    respondent.language = assignIfEmpty(respondent.language, profile.language)
    respondent.level = assignIfEmpty(respondent.level, profile.level)
    if (respondent.birthDate.isNullOrBlank() && !profile.birthDate.isNullOrBlank()) {
        respondent.birthDate = profile.birthDate
        birthDateSelection = parseBirthDateIso(profile.birthDate)
        hasOptionalDetails = true
    }
    val birthDateMillis = birthDateSelection
    if (birthDateMillis != null) {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = birthDateMillis
        }
        val year = calendar.get(Calendar.YEAR)
        if (respondent.birthYear == null) {
            respondent.birthYear = year
        }
        if (respondent.age == null) {
            val nowYear = Calendar.getInstance().get(Calendar.YEAR)
            respondent.age = nowYear - year
        }
    }
    if (hasOptionalDetails) {
        respondent.additionalInfo = true
    }
}

internal fun SurveyWizardFragment.createGenderGroup(context: android.content.Context): Pair<TextView, RadioGroup> {
    val genderLabel = TextView(context).apply {
        text = getString(R.string.dashboard_survey_wizard_gender_label)
    }
    val genderGroup = RadioGroup(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }
    val maleButton = RadioButton(context).apply {
        text = getString(R.string.signup_gender_option_male)
        tag = GENDER_MALE
        id = View.generateViewId()
    }
    val femaleButton = RadioButton(context).apply {
        text = getString(R.string.signup_gender_option_female)
        tag = GENDER_FEMALE
        id = View.generateViewId()
    }
    genderGroup.addView(maleButton)
    genderGroup.addView(femaleButton)
    when (respondent.gender) {
        GENDER_MALE -> maleButton.isChecked = true
        GENDER_FEMALE -> femaleButton.isChecked = true
    }
    return genderLabel to genderGroup
}

internal fun SurveyWizardFragment.createBirthYearLayout(context: android.content.Context): Pair<TextInputLayout, TextInputEditText> {
    val birthYearLayout = TextInputLayout(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }
    birthYearLayout.hint = getString(R.string.dashboard_survey_wizard_birth_year_label)
    val birthYearInput = TextInputEditText(context).apply {
        inputType = InputType.TYPE_CLASS_NUMBER
        setText(respondent.birthYear?.toString().orEmpty())
        setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                scrollFocusedFieldIntoView(view)
            } else {
                updateRespondentBirthYear(text?.toString())
            }
        }
        setOnClickListener { scrollFocusedFieldIntoView(this) }
        imeOptions = EditorInfo.IME_ACTION_DONE
        setOnEditorActionListener { _, actionId, event ->
            val isDoneAction = actionId == EditorInfo.IME_ACTION_DONE ||
                event?.keyCode == KeyEvent.KEYCODE_ENTER
            if (isDoneAction) {
                updateRespondentBirthYear(text?.toString())
            }
            false
        }
    }
    birthYearLayout.addView(birthYearInput)
    return birthYearLayout to birthYearInput
}

internal fun SurveyWizardFragment.scrollFocusedFieldIntoView(view: View) {
    questionScrollView.post {
        questionScrollView.postDelayed({
            val scrollChild = questionScrollView.getChildAt(0) as? ViewGroup ?: return@postDelayed
            val targetRect = Rect().also { view.getDrawingRect(it) }
            scrollChild.offsetDescendantRectToMyCoords(view, targetRect)
            targetRect.bottom += questionScrollView.height / 3
            questionScrollView.requestChildRectangleOnScreen(scrollChild, targetRect, true)
        }, 180)
    }
}

internal fun SurveyWizardFragment.updateRespondentBirthYear(value: String?) {
    val year = value?.trim().orEmpty().toIntOrNull()
    respondent.birthYear = year
    respondent.age = year?.let { Calendar.getInstance().get(Calendar.YEAR) - it }
}

internal fun SurveyWizardFragment.createAdditionalCheckBox(context: android.content.Context): CheckBox {
    return CheckBox(context).apply {
        text = getString(R.string.dashboard_survey_wizard_additional_info_label)
        isChecked = respondent.additionalInfo
    }
}

internal fun SurveyWizardFragment.renderBasicsStep(): Pair<View, () -> Boolean> {
    val context = requireContext()
    val container = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }

    val (genderLabel, genderGroup) = createGenderGroup(context)
    val (birthYearLayout, birthYearInput) = createBirthYearLayout(context)
    val additionalCheckBox = createAdditionalCheckBox(context)

    container.addView(genderLabel)
    container.addView(genderGroup)
    container.addView(birthYearLayout)
    container.addView(additionalCheckBox)

    val collector = {
        val checkedId = genderGroup.checkedRadioButtonId
        val yearText = birthYearInput.text?.toString()?.trim().orEmpty()
        val year = yearText.toIntOrNull()
        if (yearText.isNotEmpty() && year == null) {
            showValidationMessage(R.string.dashboard_survey_wizard_birth_year_required)
            false
        } else {
            val nowYear = Calendar.getInstance().get(Calendar.YEAR)
            respondent.gender = genderGroup.findViewById<RadioButton>(checkedId)?.tag as? String
            respondent.birthYear = year
            respondent.age = year?.let { nowYear - it }
            respondent.additionalInfo = additionalCheckBox.isChecked
            if (includeOptionalDetails != respondent.additionalInfo) {
                includeOptionalDetails = respondent.additionalInfo
                steps = buildSteps(includeOptionalDetails)
            }
            true
        }
    }
    return container to collector
}

internal fun SurveyWizardFragment.renderNamesStep(): Pair<View, () -> Boolean> {
    val context = requireContext()
    val container = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }

    val firstLayout = TextInputLayout(context).apply {
        hint = getString(R.string.dashboard_survey_wizard_first_name_label)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }
    val firstInput = TextInputEditText(context)
    firstInput.setText(respondent.firstName.orEmpty())
    firstLayout.addView(firstInput)

    val middleLayout = TextInputLayout(context).apply {
        hint = getString(R.string.dashboard_survey_wizard_middle_name_label)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }
    val middleInput = TextInputEditText(context)
    middleInput.setText(respondent.middleName.orEmpty())
    middleLayout.addView(middleInput)

    val lastLayout = TextInputLayout(context).apply {
        hint = getString(R.string.dashboard_survey_wizard_last_name_label)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }
    val lastInput = TextInputEditText(context)
    lastInput.setText(respondent.lastName.orEmpty())
    lastLayout.addView(lastInput)

    container.addView(firstLayout)
    container.addView(middleLayout)
    container.addView(lastLayout)

    val collector = {
        respondent.firstName = firstInput.text?.toString()?.trim().orEmpty().takeIf { it.isNotBlank() }
        respondent.middleName = middleInput.text?.toString()?.trim().orEmpty().takeIf { it.isNotBlank() }
        respondent.lastName = lastInput.text?.toString()?.trim().orEmpty().takeIf { it.isNotBlank() }
        true
    }
    return container to collector
}

internal fun SurveyWizardFragment.renderBirthDateStep(): Pair<View, () -> Boolean> {
    val context = requireContext()
    val container = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }

    val birthDateLayout = TextInputLayout(context).apply {
        hint = getString(R.string.dashboard_survey_wizard_birth_date_label)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }
    val birthDateInput = TextInputEditText(context)
    birthDateInput.apply {
        inputType = InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_DATE
        keyListener = null
        isFocusable = false
        isClickable = true
        setText(respondent.birthDate?.let { formatBirthDateDisplay(it) }
            ?: birthDateSelection?.let { formatBirthDateIso(it) }.orEmpty())
        setOnClickListener { showBirthDatePicker(this) }
        setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                showBirthDatePicker(this)
            }
        }
    }
    birthDateLayout.addView(birthDateInput)
    container.addView(birthDateLayout)

    val collector = {
        respondent.birthDate = birthDateSelection?.let { formatBirthDateIso(it) }
        true
    }
    return container to collector
}

internal fun SurveyWizardFragment.showBirthDatePicker(input: TextInputEditText) {
    if (childFragmentManager.findFragmentByTag(BIRTH_DATE_PICKER_TAG) != null) {
        return
    }

    val picker = MaterialDatePicker.Builder.datePicker()
        .setTitleText(getString(R.string.signup_birth_date_picker_title))
        .apply {
            birthDateSelection?.let { setSelection(it) }
        }
        .build()

    picker.addOnPositiveButtonClickListener { selection ->
        birthDateSelection = selection
        input.setText(formatBirthDateIso(selection))
    }

    picker.addOnDismissListener {
        input.clearFocus()
    }

    picker.show(childFragmentManager, BIRTH_DATE_PICKER_TAG)
}

internal fun SurveyWizardFragment.parseBirthDateIso(value: String?): Long? {
    if (value.isNullOrBlank()) {
        return null
    }
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    return try {
        formatter.parse(value)?.time
    } catch (_: ParseException) {
        null
    }
}

internal fun SurveyWizardFragment.formatBirthDateIso(selection: Long): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    return formatter.format(Date(selection))
}

internal fun SurveyWizardFragment.formatBirthDateDisplay(value: String): String {
    val parsed = parseBirthDateIso(value)
    return parsed?.let { formatBirthDateIso(it) } ?: value
}

internal fun SurveyWizardFragment.renderContactStep(): Pair<View, () -> Boolean> {
    val context = requireContext()
    val container = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }

    val emailLayout = TextInputLayout(context).apply {
        hint = getString(R.string.dashboard_survey_wizard_email_label)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }
    val emailInput = TextInputEditText(context).apply {
        inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        setText(respondent.email.orEmpty())
    }
    emailLayout.addView(emailInput)

    val phoneLayout = TextInputLayout(context).apply {
        hint = getString(R.string.dashboard_survey_wizard_phone_label)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }
    val phoneInput = TextInputEditText(context).apply {
        inputType = InputType.TYPE_CLASS_PHONE
        setText(respondent.phoneNumber.orEmpty())
    }
    phoneLayout.addView(phoneInput)

    container.addView(emailLayout)
    container.addView(phoneLayout)

    val collector = {
        respondent.email = emailInput.text?.toString()?.trim().orEmpty().takeIf { it.isNotBlank() }
        respondent.phoneNumber = phoneInput.text?.toString()?.trim().orEmpty().takeIf { it.isNotBlank() }
        true
    }
    return container to collector
}

internal fun SurveyWizardFragment.createDropdownLayout(context: android.content.Context, hintText: String): Pair<TextInputLayout, AutoCompleteTextView> {
    val layout = TextInputLayout(context).apply {
        hint = hintText
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }
    val input = AutoCompleteTextView(context).apply {
        inputType = InputType.TYPE_NULL
        keyListener = null
        setOnClickListener { showDropDown() }
        setOnFocusChangeListener { _, hasFocus -> if (hasFocus) showDropDown() }
    }
    layout.addView(input)
    return layout to input
}

internal fun SurveyWizardFragment.levelArrayForLanguage(languageLabel: String?): Int {
    val normalized = languageLabel?.trim()?.lowercase(Locale.ROOT)
    return when (normalized) {
        getString(R.string.language_name_spanish).lowercase(Locale.ROOT) -> R.array.signup_level_options_language_es
        getString(R.string.language_name_french).lowercase(Locale.ROOT) -> R.array.signup_level_options_language_fr
        getString(R.string.language_name_portuguese).lowercase(Locale.ROOT) -> R.array.signup_level_options_language_pt
        getString(R.string.language_name_arabic).lowercase(Locale.ROOT) -> R.array.signup_level_options_language_ar
        getString(R.string.language_name_somali).lowercase(Locale.ROOT) -> R.array.signup_level_options_language_so
        getString(R.string.language_name_nepali).lowercase(Locale.ROOT) -> R.array.signup_level_options_language_ne
        getString(R.string.language_name_hindi).lowercase(Locale.ROOT) -> R.array.signup_level_options_language_hi
        else -> R.array.signup_level_options_language_en
    }
}

internal fun SurveyWizardFragment.renderLanguageLevelStep(): Pair<View, () -> Boolean> {
    val context = requireContext()
    val container = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }

    val (languageLayout, languageInput) = createDropdownLayout(
        context,
        getString(R.string.dashboard_survey_wizard_language_label)
    )
    val (levelLayout, levelInput) = createDropdownLayout(
        context,
        getString(R.string.dashboard_survey_wizard_level_label)
    )

    val languages = resources.getStringArray(R.array.signup_language_options).toList()
    val languageAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, languages)
    languageInput.setAdapter(languageAdapter)
    respondent.language?.let { languageInput.setText(it, false) }

    var currentLevelOptions: List<String> = emptyList()

    fun updateLevelOptions(languageLabel: String?) {
        val arrayRes = levelArrayForLanguage(languageLabel)
        val options = resources.getStringArray(arrayRes).toList()
        currentLevelOptions = options
        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, options)
        levelInput.setAdapter(adapter)
        val localizedLevel = LearningLevelTranslator.toLocalized(context, respondent.level, arrayRes)
        if (!localizedLevel.isNullOrBlank() && options.contains(localizedLevel)) {
            levelInput.setText(localizedLevel, false)
        } else {
            levelInput.setText("", false)
        }
    }

    languageInput.setOnItemClickListener { _, _, position, _ ->
        val selected = languageAdapter.getItem(position)
        updateLevelOptions(selected)
    }

    updateLevelOptions(respondent.language)

    container.addView(languageLayout)
    container.addView(levelLayout)

    val collector = {
        val selectedLanguage = languageInput.text?.toString()?.trim().orEmpty()
        respondent.language = selectedLanguage.takeIf { languages.contains(it) }

        val levelText = levelInput.text?.toString()?.trim().orEmpty()
        respondent.level = if (currentLevelOptions.contains(levelText)) {
            LearningLevelTranslator.toEnglish(context, levelText)
        } else {
            null
        }
        true
    }
    return container to collector
}
