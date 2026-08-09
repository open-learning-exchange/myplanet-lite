/*
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2026-07-29
 */

package org.ole.planet.myplanet.lite

import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyQuestion
import kotlin.math.roundToInt

internal fun SurveyWizardFragment.isExamPassing(): Boolean {
    val survey = document ?: return true
    val questions = survey.questions.orEmpty()
    if (questions.isEmpty()) return true
    val passingPercentage = survey.passingPercentage ?: DEFAULT_EXAM_PASSING_PERCENTAGE
    val totalMarks = questions.sumOf { it.marks ?: 1 }
    if (totalMarks == 0) return true
    val earned =
        questions
            .mapIndexed { index, question ->
                if (isAnswerCorrect(question, answers[index])) {
                    question.marks ?: 1
                } else {
                    0
                }
            }.sum()
    val percentage = (earned * 100.0 / totalMarks).roundToInt()
    return percentage >= passingPercentage
}

internal fun SurveyWizardFragment.isAnswerCorrect(
    question: SurveyQuestion,
    answer: SurveyAnswer?,
): Boolean {
    val normalizedCorrect = normalizeCorrectChoice(question.correctChoice)
    val choiceIds =
        question.choices
            .orEmpty()
            .mapNotNull { it.id?.trim() }
            .filter { it.isNotBlank() }
            .toSet()
    val choiceTextToId =
        question.choices
            .orEmpty()
            .mapNotNull { choice ->
                val text = choice.text?.trim().orEmpty()
                val id = choice.id?.trim().orEmpty()
                if (text.isNotBlank() && id.isNotBlank()) text to id else null
            }.toMap()
    val correctIds =
        normalizedCorrect
            .map { it.trim() }
            .filter { it.isNotBlank() && choiceIds.contains(it) }
    val correctTexts =
        if (correctIds.isEmpty()) {
            normalizedCorrect.map { it.trim() }.filter { it.isNotBlank() }
        } else {
            emptyList()
        }
    return when (question.type) {
        "input", "textarea" -> isTextInputCorrect(normalizedCorrect, answer)
        "select" -> isSelectInputCorrect(correctIds, correctTexts, choiceTextToId, answer)
        "selectMultiple" -> isSelectMultipleInputCorrect(correctIds, answer)
        "ratingScale" -> isRatingScaleInputCorrect(normalizedCorrect, answer)
        else -> false
    }
}

internal fun SurveyWizardFragment.isTextInputCorrect(
    normalizedCorrect: List<String>,
    answer: SurveyAnswer?,
): Boolean {
    val correctText = normalizedCorrect.firstOrNull()?.trim().orEmpty()
    val response = (answer as? SurveyAnswer.Text)?.value?.trim().orEmpty()
    return if (correctText.isBlank()) {
        response.isNotBlank()
    } else {
        response.equals(correctText, ignoreCase = true)
    }
}

internal fun SurveyWizardFragment.isSelectInputCorrect(
    correctIds: List<String>,
    correctTexts: List<String>,
    choiceTextToId: Map<String, String>,
    answer: SurveyAnswer?,
): Boolean {
    val selectedChoice = (answer as? SurveyAnswer.SingleChoice)?.choice
    return if (correctIds.isNotEmpty()) {
        val selectedId = normalizeSelectedId(selectedChoice?.id)
        val resolvedId =
            selectedId?.takeIf { it.isNotBlank() }
                ?: selectedChoice?.text?.trim()?.let { choiceTextToId[it] }
        resolvedId != null && correctIds.contains(resolvedId)
    } else {
        val selectedText = selectedChoice?.text?.trim()
        selectedText != null && correctTexts.any { it.equals(selectedText, ignoreCase = true) }
    }
}

internal fun SurveyWizardFragment.isSelectMultipleInputCorrect(
    correctIds: List<String>,
    answer: SurveyAnswer?,
): Boolean {
    val selectedIds =
        (answer as? SurveyAnswer.MultipleChoice)
            ?.choices
            ?.filter { !it.isOther }
            ?.mapNotNull { normalizeSelectedId(it.id) }
            ?.filter { it.isNotBlank() }
            .orEmpty()
    return selectedIds.size == correctIds.size && selectedIds.toSet() == correctIds.toSet()
}

internal fun SurveyWizardFragment.isRatingScaleInputCorrect(
    normalizedCorrect: List<String>,
    answer: SurveyAnswer?,
): Boolean {
    val correctValue = normalizedCorrect.firstOrNull()?.trim().orEmpty()
    val selectedScore = (answer as? SurveyAnswer.Rating)?.score?.toString()
    return correctValue.isNotBlank() && selectedScore == correctValue
}

internal fun SurveyWizardFragment.normalizeCorrectChoice(correctChoice: Any?): List<String> =
    when (correctChoice) {
        is String -> listOf(correctChoice)
        is List<*> ->
            correctChoice.mapNotNull { item ->
                when (item) {
                    is Map<*, *> ->
                        item["id"]?.toString()
                            ?: item["_id"]?.toString()
                            ?: item["text"]?.toString()

                    else -> item?.toString()
                }
            }

        null -> emptyList()
        else -> listOf(correctChoice.toString())
    }

internal fun SurveyWizardFragment.normalizeSelectedId(rawId: String?): String? {
    val trimmed = rawId?.trim().orEmpty()
    if (trimmed.isBlank()) return null
    return trimmed.substringBefore("/").trim().takeIf { it.isNotBlank() }
}
