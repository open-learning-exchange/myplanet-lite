/*
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2026-07-31
 */

package org.ole.planet.myplanet.lite

import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.ole.planet.myplanet.lite.util.BirthDateConstraints
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal fun SurveyWizardFragment.createBirthDateLayout(context: android.content.Context): Pair<TextInputLayout, TextInputEditText> {
    val birthDateLayout =
        TextInputLayout(context).apply {
            hint = getString(R.string.dashboard_survey_wizard_birth_date_label)
            layoutParams =
                LinearLayout.LayoutParams(
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
        setText(
            respondent.birthDate?.let { formatBirthDateDisplay(it) }
                ?: birthDateSelection?.let { formatBirthDateIso(it) }.orEmpty(),
        )
        setOnClickListener { showBirthDatePicker(this) }
        setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                showBirthDatePicker(this)
            }
        }
    }
    birthDateLayout.addView(birthDateInput)
    return birthDateLayout to birthDateInput
}

internal fun SurveyWizardFragment.renderBirthDateStep(): Pair<View, () -> Boolean> {
    val context = requireContext()
    val container =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams =
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
        }

    val (birthDateLayout, birthDateInput) = createBirthDateLayout(context)
    container.addView(birthDateLayout)

    val collector = {
        respondent.birthDate =
            birthDateSelection
                ?.takeUnless { BirthDateConstraints.isFuture(it) }
                ?.let { formatBirthDateIso(it) }
        true
    }
    return container to collector
}

internal fun SurveyWizardFragment.showBirthDatePicker(input: TextInputEditText) {
    if (childFragmentManager.findFragmentByTag(BIRTH_DATE_PICKER_TAG) != null) {
        return
    }

    val picker =
        MaterialDatePicker.Builder
            .datePicker()
            .setTitleText(getString(R.string.signup_birth_date_picker_title))
            .setCalendarConstraints(BirthDateConstraints.calendarConstraints())
            .apply {
                setSelection(BirthDateConstraints.coerceSelection(initialBirthDatePickerSelection()))
            }.build()

    picker.addOnPositiveButtonClickListener { selection ->
        if (BirthDateConstraints.isFuture(selection)) {
            return@addOnPositiveButtonClickListener
        }
        birthDateSelection = selection
        input.setText(formatBirthDateIso(selection))
    }

    picker.addOnDismissListener {
        input.clearFocus()
    }

    picker.show(childFragmentManager, BIRTH_DATE_PICKER_TAG)
}

internal fun SurveyWizardFragment.initialBirthDatePickerSelection(): Long? {
    birthDateSelection?.let { return it }
    val year = respondent.birthYear ?: return null
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).run {
        clear()
        set(year, Calendar.JANUARY, 1)
        timeInMillis
    }
}

internal fun SurveyWizardFragment.parseBirthDateIso(value: String?): Long? {
    if (value.isNullOrBlank()) {
        return null
    }
    val formatter =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    return try {
        formatter.parse(value)?.time
    } catch (_: ParseException) {
        null
    }
}

internal fun SurveyWizardFragment.formatBirthDateIso(selection: Long): String {
    val formatter =
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    return formatter.format(Date(selection))
}

internal fun SurveyWizardFragment.formatBirthDateDisplay(value: String): String {
    val parsed = parseBirthDateIso(value)
    return parsed?.let { formatBirthDateIso(it) } ?: value
}
