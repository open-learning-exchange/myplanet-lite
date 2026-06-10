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

internal fun SurveyWizardFragment.renderRatingQuestion(question: SurveyQuestion, index: Int): Pair<View, () -> Boolean> {
    val context = requireContext()
    val scaleMax = question.scaleMax?.takeIf { it > 0 } ?: DEFAULT_RATING_SCALE_MAX
    val gridLayout = createRatingGridLayout(context, scaleMax)

    val styles = getRatingStyles(context)
    val buttons = mutableListOf<MaterialButton>()
    var selectedValue: Int? = (answers[index] as? SurveyAnswer.Rating)?.score

    fun applySelection() {
        buttons.forEachIndexed { idx, button ->
            updateRatingButtonSelection(button, idx + 1, selectedValue == idx + 1, scaleMax, styles)
        }
    }

    (1..scaleMax).forEach { value ->
        val button = createRatingButton(context, value, styles.horizontalMargin, styles.bottomMargin) { selected ->
            selectedValue = selected
            applySelection()
        }
        buttons.add(button)
        gridLayout.addView(button)
    }

    applySelection()

    return gridLayout to createRatingCollector(index) { selectedValue }
}

internal fun SurveyWizardFragment.createRatingGridLayout(context: android.content.Context, scaleMax: Int): GridLayout {
    val columnCount = ratingScaleColumnCount(scaleMax)
    return GridLayout(context).apply {
        rowCount = ((scaleMax + columnCount - 1) / columnCount).coerceAtLeast(1)
        this.columnCount = columnCount
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }
}

internal data class RatingStyles(
    val basePalette: List<Int>,
    val selectedColor: Int,
    val selectedTextColor: Int,
    val defaultTextColor: Int,
    val horizontalMargin: Int,
    val bottomMargin: Int
)

internal fun SurveyWizardFragment.getRatingStyles(context: android.content.Context): RatingStyles {
    return RatingStyles(
        basePalette = listOf(
            "#F3C4E3".toColorInt(),
            "#F6CEDE".toColorInt(),
            "#F8D7DA".toColorInt(),
            "#FAE0D6".toColorInt(),
            "#FDEAD1".toColorInt(),
            "#FFF3CD".toColorInt(),
            "#F1F1D1".toColorInt(),
            "#E2EFD6".toColorInt(),
            "#D4EDDA".toColorInt(),
        ),
        selectedColor = ContextCompat.getColor(context, R.color.survey_rating_selected_background),
        selectedTextColor = ContextCompat.getColor(context, R.color.survey_rating_selected_text),
        defaultTextColor = ContextCompat.getColor(context, R.color.survey_rating_default_text),
        horizontalMargin = context.resources.getDimensionPixelSize(R.dimen.padding_small),
        bottomMargin = context.resources.getDimensionPixelSize(R.dimen.padding_small)
    )
}

internal fun SurveyWizardFragment.updateRatingButtonSelection(
    button: MaterialButton,
    value: Int,
    isSelected: Boolean,
    scaleMax: Int,
    styles: RatingStyles
) {
    val backgroundColor = if (isSelected) {
        styles.selectedColor
    } else {
        ratingBackgroundColor(value, scaleMax, styles.basePalette)
    }
    button.backgroundTintList = ColorStateList.valueOf(backgroundColor)
    button.setTextColor(if (isSelected) styles.selectedTextColor else styles.defaultTextColor)
    button.strokeWidth = 0
}

internal fun SurveyWizardFragment.createRatingCollector(index: Int, getSelectedValue: () -> Int?): () -> Boolean {
    return {
        val score = getSelectedValue()
        if (score == null) {
            showValidationMessage(R.string.dashboard_survey_wizard_rating_required)
            false
        } else {
            answers[index] = SurveyAnswer.Rating(score)
            true
        }
    }
}

internal fun SurveyWizardFragment.ratingScaleColumnCount(scaleMax: Int): Int {
    return when {
        scaleMax <= 5 -> scaleMax.coerceAtLeast(1)
        scaleMax <= DEFAULT_RATING_SCALE_MAX -> 3
        else -> kotlin.math.ceil(kotlin.math.sqrt(scaleMax.toDouble())).toInt().coerceAtLeast(1)
    }
}

internal fun SurveyWizardFragment.ratingBackgroundColor(value: Int, scaleMax: Int, palette: List<Int>): Int {
    if (palette.isEmpty()) return Color.TRANSPARENT
    if (scaleMax <= 1 || palette.size == 1) return palette.first()
    val normalizedPosition = (value - 1).coerceAtLeast(0).toFloat() / (scaleMax - 1)
    val palettePosition = normalizedPosition * (palette.size - 1)
    val lowerIndex = palettePosition.toInt().coerceIn(0, palette.lastIndex)
    val upperIndex = minOf(lowerIndex + 1, palette.lastIndex)
    val fraction = palettePosition - lowerIndex
    return blendColors(palette[lowerIndex], palette[upperIndex], fraction)
}

internal fun SurveyWizardFragment.blendColors(startColor: Int, endColor: Int, fraction: Float): Int {
    val red = (Color.red(startColor) + (Color.red(endColor) - Color.red(startColor)) * fraction).roundToInt()
    val green = (Color.green(startColor) + (Color.green(endColor) - Color.green(startColor)) * fraction).roundToInt()
    val blue = (Color.blue(startColor) + (Color.blue(endColor) - Color.blue(startColor)) * fraction).roundToInt()
    return Color.rgb(red, green, blue)
}

internal fun SurveyWizardFragment.createRatingButton(
    context: android.content.Context,
    value: Int,
    horizontalMargin: Int,
    bottomMargin: Int,
    onClick: (Int) -> Unit
): MaterialButton {
    return MaterialButton(context).apply {
        text = value.toString()
        isAllCaps = false
        textSize = 18f
        cornerRadius = resources.getDimensionPixelSize(R.dimen.padding_small)
        insetTop = 0
        insetBottom = 0
        layoutParams = GridLayout.LayoutParams().apply {
            width = 0
            height = ViewGroup.LayoutParams.WRAP_CONTENT
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            setMargins(horizontalMargin, 0, horizontalMargin, bottomMargin)
        }
        setOnClickListener {
            onClick(value)
        }
    }
}
