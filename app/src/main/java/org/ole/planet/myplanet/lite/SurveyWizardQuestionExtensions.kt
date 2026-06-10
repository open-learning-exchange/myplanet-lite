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

internal fun SurveyWizardFragment.renderQuestion(
    question: SurveyQuestion,
    index: Int,
    translation: TranslatedQuestion?,
): Pair<View, () -> Boolean> {
    return when (question.type) {
        "input" -> renderTextInputQuestion(index, false)
        "textarea" -> renderTextInputQuestion(index, true)
        "select" -> renderSingleChoiceQuestion(question, index, translation)
        "selectMultiple" -> renderMultiChoiceQuestion(question, index, translation)
        "ratingScale" -> renderRatingQuestion(question, index)
        else -> renderTextInputQuestion(index, false)
    }
}

internal fun SurveyWizardFragment.renderTextInputQuestion(index: Int, multiline: Boolean): Pair<View, () -> Boolean> {
    val context = requireContext()
    val layout = TextInputLayout(context)
    layout.layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )
    val editText = TextInputEditText(context)
    editText.layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )
    editText.isSingleLine = !multiline
    if (multiline) {
        editText.minLines = 3
        editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
    } else {
        editText.inputType = InputType.TYPE_CLASS_TEXT
    }
    val savedText = (answers[index] as? SurveyAnswer.Text)?.value.orEmpty()
    if (savedText.isNotEmpty()) {
        editText.setText(savedText)
        editText.setSelection(savedText.length)
    }
    layout.addView(editText)
    val collector = {
        val response = editText.text?.toString()?.trim().orEmpty()
        if (response.isBlank()) {
            showValidationMessage(R.string.dashboard_survey_wizard_input_required)
            false
        } else {
            answers[index] = SurveyAnswer.Text(response)
            true
        }
    }
    return layout to collector
}

internal fun SurveyWizardFragment.renderSingleChoiceQuestion(
    question: SurveyQuestion,
    index: Int,
    translation: TranslatedQuestion?,
): Pair<View, () -> Boolean> {
    val context = requireContext()
    val container = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }
    val radioGroup = RadioGroup(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }

    val otherInputLayout = buildSingleChoiceOptions(
        context = context,
        question = question,
        translation = translation,
        radioGroup = radioGroup,
        container = container,
    )
    container.addView(radioGroup, 0)

    val savedSelection = (answers[index] as? SurveyAnswer.SingleChoice)?.choice
    restoreSingleChoiceSelection(radioGroup, otherInputLayout, savedSelection)

    val collector = {
        collectSingleChoiceAnswer(radioGroup, otherInputLayout, question, index)
    }
    return container to collector
}

internal fun SurveyWizardFragment.buildSingleChoiceOptions(
    context: android.content.Context,
    question: SurveyQuestion,
    translation: TranslatedQuestion?,
    radioGroup: RadioGroup,
    container: LinearLayout,
): TextInputLayout? {
    val choices = question.choices.orEmpty()
    choices.forEachIndexed { index, choice ->
        val button = RadioButton(context)
        val translatedLabel = translation?.choices?.getOrNull(index)
        button.text = translatedLabel?.takeIf { it.isNotBlank() } ?: choice.text.orEmpty()
        button.tag = choice
        radioGroup.addView(button)
    }
    var otherInputLayout: TextInputLayout? = null
    if (question.hasOtherOption) {
        val otherButton = RadioButton(context)
        otherButton.text = getString(R.string.dashboard_survey_wizard_other_option)
        otherButton.tag = OTHER_CHOICE_TAG
        radioGroup.addView(otherButton)
        otherInputLayout = buildOtherInputField(context)
        otherInputLayout.isVisible = false
        container.addView(otherInputLayout)
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val selectedButton = radioGroup.findViewById<RadioButton>(checkedId)
            val isOther = selectedButton?.tag == OTHER_CHOICE_TAG
            otherInputLayout.isVisible = isOther
        }
    }
    return otherInputLayout
}

internal fun SurveyWizardFragment.restoreSingleChoiceSelection(
    radioGroup: RadioGroup,
    otherInputLayout: TextInputLayout?,
    savedSelection: SelectedOption?,
) {
    if (savedSelection != null) {
        val matchedButton = (0 until radioGroup.childCount)
            .mapNotNull { childIndex -> radioGroup.getChildAt(childIndex) as? RadioButton }
            .firstOrNull { button ->
                val taggedChoice = button.tag as? SurveyChoice
                if (savedSelection.isOther) {
                    button.tag == OTHER_CHOICE_TAG
                } else {
                    taggedChoice?.id == savedSelection.id ||
                        button.text?.toString() == savedSelection.text
                }
            }
        matchedButton?.isChecked = true
        if (savedSelection.isOther) {
            otherInputLayout?.isVisible = true
            otherInputLayout?.editText?.setText(savedSelection.text)
            otherInputLayout?.editText?.setSelection(savedSelection.text.length)
        }
    }
}

internal fun SurveyWizardFragment.collectSingleChoiceAnswer(
    radioGroup: RadioGroup,
    otherInputLayout: TextInputLayout?,
    question: SurveyQuestion,
    index: Int,
): Boolean {
    val selectedId = radioGroup.checkedRadioButtonId
    if (selectedId == -1) {
        showValidationMessage(R.string.dashboard_survey_wizard_choice_required)
        return false
    }
    val selectedButton = radioGroup.findViewById<RadioButton>(selectedId)
    val isOther = selectedButton.tag == OTHER_CHOICE_TAG
    val otherValue = otherInputLayout?.editText?.text?.toString()?.trim().orEmpty()
    if (isOther && otherValue.isBlank()) {
        showValidationMessage(R.string.dashboard_survey_wizard_input_required)
        return false
    }
    val choice = if (isOther) {
        SelectedOption(
            id = GENDER_OTHER,
            text = otherValue,
            isOther = true,
        )
    } else {
        val originalChoice = selectedButton.tag as? SurveyChoice
        val label = originalChoice?.text?.takeIf { it.isNotBlank() }
            ?: selectedButton.text?.toString()?.takeIf { it.isNotBlank() }
            ?: originalChoice?.id
            ?: ""
        SelectedOption(
            id = originalChoice?.id ?: label,
            text = label,
            isOther = false,
        )
    }
    val answer = SurveyAnswer.SingleChoice(choice = choice)
    if (isExam && !isAnswerCorrect(question, answer)) {
        showValidationMessage(R.string.dashboard_exam_incorrect_answers)
        return false
    }
    answers[index] = answer
    return true
}

internal fun SurveyWizardFragment.renderMultiChoiceQuestion(
    question: SurveyQuestion,
    index: Int,
    translation: TranslatedQuestion?,
): Pair<View, () -> Boolean> {
    val context = requireContext()
    val container = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }
    val checkboxes = mutableListOf<android.widget.CheckBox>()
    buildMultiChoiceCheckboxes(context, question, translation, container, checkboxes)

    val (otherBox, otherInputLayout) = buildMultiChoiceOtherOption(context, question, container)

    val savedSelections = (answers[index] as? SurveyAnswer.MultipleChoice)?.choices.orEmpty()
    restoreMultiChoiceSelections(checkboxes, otherBox, otherInputLayout, savedSelections)

    val collector = {
        collectMultiChoiceAnswer(checkboxes, otherBox, otherInputLayout, question, index)
    }
    return container to collector
}

internal fun SurveyWizardFragment.buildMultiChoiceCheckboxes(
    context: android.content.Context,
    question: SurveyQuestion,
    translation: TranslatedQuestion?,
    container: LinearLayout,
    checkboxes: MutableList<android.widget.CheckBox>,
) {
    question.choices.orEmpty().forEachIndexed { index, choice ->
        val box = android.widget.CheckBox(context)
        val translatedLabel = translation?.choices?.getOrNull(index)
        box.text = translatedLabel?.takeIf { it.isNotBlank() } ?: choice.text.orEmpty()
        box.tag = choice
        container.addView(box)
        checkboxes.add(box)
    }
}

internal fun SurveyWizardFragment.buildMultiChoiceOtherOption(
    context: android.content.Context,
    question: SurveyQuestion,
    container: LinearLayout,
): Pair<android.widget.CheckBox?, TextInputLayout?> {
    if (!question.hasOtherOption) return null to null
    val otherBox = android.widget.CheckBox(context)
    otherBox.text = getString(R.string.dashboard_survey_wizard_other_option)
    otherBox.tag = OTHER_CHOICE_TAG
    container.addView(otherBox)
    val otherInputLayout = buildOtherInputField(context)
    otherInputLayout.isVisible = false
    container.addView(otherInputLayout)
    otherBox.setOnCheckedChangeListener { _, isChecked ->
        otherInputLayout.isVisible = isChecked
    }
    return otherBox to otherInputLayout
}

internal fun SurveyWizardFragment.restoreMultiChoiceSelections(
    checkboxes: List<android.widget.CheckBox>,
    otherBox: android.widget.CheckBox?,
    otherInputLayout: TextInputLayout?,
    savedSelections: List<SelectedOption>,
) {
    checkboxes.forEach { box ->
        val choice = box.tag as? SurveyChoice
        val isSelected = savedSelections.any { saved ->
            saved.id == choice?.id || saved.text == box.text?.toString()
        }
        box.isChecked = isSelected
    }
    val savedOther = savedSelections.firstOrNull { it.isOther }
    if (savedOther != null) {
        otherBox?.isChecked = true
        otherInputLayout?.isVisible = true
        otherInputLayout?.editText?.setText(savedOther.text)
        otherInputLayout?.editText?.setSelection(savedOther.text.length)
    }
}

internal fun SurveyWizardFragment.collectMultiChoiceAnswer(
    checkboxes: List<android.widget.CheckBox>,
    otherBox: android.widget.CheckBox?,
    otherInputLayout: TextInputLayout?,
    question: SurveyQuestion,
    index: Int,
): Boolean {
    val selectedChoices = checkboxes.filter { it.isChecked }
        .map { checkbox ->
            val choice = checkbox.tag as? SurveyChoice
            val label = choice?.text?.takeIf { it.isNotBlank() }
                ?: checkbox.text?.toString()?.takeIf { it.isNotBlank() }
                ?: choice?.id
                ?: ""
            SelectedOption(
                id = choice?.id ?: label,
                text = label,
                isOther = false,
            )
        }
    val otherChecked = otherBox?.isChecked == true
    val otherText = otherInputLayout?.editText?.text?.toString()?.trim().orEmpty()
    if (selectedChoices.isEmpty() && !otherChecked) {
        showValidationMessage(R.string.dashboard_survey_wizard_choice_required)
        return false
    } else if (otherChecked && otherText.isBlank()) {
        showValidationMessage(R.string.dashboard_survey_wizard_input_required)
        return false
    }
    val combined = mutableListOf<SelectedOption>()
    combined.addAll(selectedChoices)
    if (otherChecked && otherText.isNotBlank()) {
        combined.add(
            SelectedOption(
                id = GENDER_OTHER,
                text = otherText,
                isOther = true,
            ),
        )
    }
    val answer = SurveyAnswer.MultipleChoice(choices = combined)
    if (isExam && !isAnswerCorrect(question, answer)) {
        showValidationMessage(R.string.dashboard_exam_incorrect_answers)
        return false
    }
    answers[index] = answer
    return true
}

internal fun SurveyWizardFragment.buildOtherInputField(context: android.content.Context): TextInputLayout {
    val layout = TextInputLayout(context)
    layout.layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )
    val editText = TextInputEditText(context)
    editText.hint = getString(R.string.dashboard_survey_wizard_other_hint)
    editText.layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    )
    layout.addView(editText)
    return layout
}
