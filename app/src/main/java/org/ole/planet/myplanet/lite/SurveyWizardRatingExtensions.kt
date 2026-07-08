/*
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-12-12
 */

package org.ole.planet.myplanet.lite

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import com.google.android.material.button.MaterialButton
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyQuestion
import kotlin.math.roundToInt

internal fun SurveyWizardFragment.renderRatingQuestion(
    question: SurveyQuestion,
    index: Int,
): Pair<View, () -> Boolean> {
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
        val button =
            createRatingButton(context, value, styles.horizontalMargin, styles.bottomMargin) { selected ->
                selectedValue = selected
                applySelection()
            }
        buttons.add(button)
        gridLayout.addView(button)
    }

    applySelection()

    return gridLayout to createRatingCollector(index) { selectedValue }
}

internal fun SurveyWizardFragment.createRatingGridLayout(
    context: android.content.Context,
    scaleMax: Int,
): GridLayout {
    val columnCount = ratingScaleColumnCount(scaleMax)
    return GridLayout(context).apply {
        rowCount = ((scaleMax + columnCount - 1) / columnCount).coerceAtLeast(1)
        this.columnCount = columnCount
        layoutParams =
            LinearLayout.LayoutParams(
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
    val bottomMargin: Int,
)

internal fun SurveyWizardFragment.getRatingStyles(context: android.content.Context): RatingStyles =
    RatingStyles(
        basePalette =
            listOf(
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
        bottomMargin = context.resources.getDimensionPixelSize(R.dimen.padding_small),
    )

internal fun SurveyWizardFragment.updateRatingButtonSelection(
    button: MaterialButton,
    value: Int,
    isSelected: Boolean,
    scaleMax: Int,
    styles: RatingStyles,
) {
    val backgroundColor =
        if (isSelected) {
            styles.selectedColor
        } else {
            ratingBackgroundColor(value, scaleMax, styles.basePalette)
        }
    button.backgroundTintList = ColorStateList.valueOf(backgroundColor)
    button.setTextColor(if (isSelected) styles.selectedTextColor else styles.defaultTextColor)
    button.strokeWidth = 0
}

internal fun SurveyWizardFragment.createRatingCollector(
    index: Int,
    getSelectedValue: () -> Int?,
): () -> Boolean =
    {
        val score = getSelectedValue()
        if (score == null) {
            showValidationMessage(R.string.dashboard_survey_wizard_rating_required)
            false
        } else {
            answers[index] = SurveyAnswer.Rating(score)
            true
        }
    }

internal fun SurveyWizardFragment.ratingScaleColumnCount(scaleMax: Int): Int =
    when {
        scaleMax <= MAX_SINGLE_ROW_RATING_SCALE -> {
            scaleMax.coerceAtLeast(1)
        }

        scaleMax <= DEFAULT_RATING_SCALE_MAX -> {
            DEFAULT_MULTI_ROW_COLUMN_COUNT
        }

        else -> {
            kotlin.math
                .ceil(kotlin.math.sqrt(scaleMax.toDouble()))
                .toInt()
                .coerceAtLeast(1)
        }
    }

internal fun SurveyWizardFragment.ratingBackgroundColor(
    value: Int,
    scaleMax: Int,
    palette: List<Int>,
): Int {
    if (palette.isEmpty()) return Color.TRANSPARENT
    if (scaleMax <= 1 || palette.size == 1) return palette.first()
    val normalizedPosition = (value - 1).coerceAtLeast(0).toFloat() / (scaleMax - 1)
    val palettePosition = normalizedPosition * (palette.size - 1)
    val lowerIndex = palettePosition.toInt().coerceIn(0, palette.lastIndex)
    val upperIndex = minOf(lowerIndex + 1, palette.lastIndex)
    val fraction = palettePosition - lowerIndex
    return blendColors(palette[lowerIndex], palette[upperIndex], fraction)
}

internal fun SurveyWizardFragment.blendColors(
    startColor: Int,
    endColor: Int,
    fraction: Float,
): Int {
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
    onClick: (Int) -> Unit,
): MaterialButton =
    MaterialButton(context).apply {
        text = value.toString()
        isAllCaps = false
        textSize = 18f
        cornerRadius = resources.getDimensionPixelSize(R.dimen.padding_small)
        insetTop = 0
        insetBottom = 0
        layoutParams =
            GridLayout.LayoutParams().apply {
                width = 0
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(horizontalMargin, 0, horizontalMargin, bottomMargin)
            }
        setOnClickListener {
            onClick(value)
        }
    }
