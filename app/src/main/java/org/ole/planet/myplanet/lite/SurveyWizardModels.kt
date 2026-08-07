/*
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-12-12
 */

package org.ole.planet.myplanet.lite

import com.squareup.moshi.Json
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveySubmissionsRepository
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveySubmissionsRepository.SubmissionAnswer
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyDocument
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyQuestion
import org.ole.planet.myplanet.lite.profile.UserProfile

internal const val OTHER_CHOICE_TAG = "other_choice"
internal const val BIRTH_DATE_PICKER_TAG = "survey_birth_date_picker"
internal const val DEFAULT_RATING_SCALE_MAX = 9
internal const val MAX_SINGLE_ROW_RATING_SCALE = 5
internal const val DEFAULT_MULTI_ROW_COLUMN_COUNT = 3
internal const val DEFAULT_EXAM_PASSING_PERCENTAGE = 100
internal const val KEY_DEVICE_CUSTOM_DEVICE_NAME = "device_custom_device_name"
internal const val ANONYMOUS_RESPONDENT_NAME = "Anonymous"

internal sealed class WizardStep : java.io.Serializable {
    object Basics : WizardStep() {
        private fun readResolve(): Any = Basics
    }

    object Names : WizardStep() {
        private fun readResolve(): Any = Names
    }

    object BirthDate : WizardStep() {
        private fun readResolve(): Any = BirthDate
    }

    object Contact : WizardStep() {
        private fun readResolve(): Any = Contact
    }

    object LanguageLevel : WizardStep() {
        private fun readResolve(): Any = LanguageLevel
    }

    data class Question(
        val question: SurveyQuestion,
        val questionIndex: Int,
    ) : WizardStep()
}

internal data class SurveyRespondent(
    var gender: String? = null,
    var birthYear: Int? = null,
    var age: Int? = null,
    var additionalInfo: Boolean = false,
    var firstName: String? = null,
    var middleName: String? = null,
    var lastName: String? = null,
    var birthDate: String? = null,
    var email: String? = null,
    var phoneNumber: String? = null,
    var language: String? = null,
    var level: String? = null,
) : java.io.Serializable

internal sealed class SurveyAnswer : java.io.Serializable {
    data class Text(
        val value: String,
    ) : SurveyAnswer()

    data class SingleChoice(
        val choice: SelectedOption?,
    ) : SurveyAnswer()

    data class MultipleChoice(
        val choices: List<SelectedOption>,
    ) : SurveyAnswer()

    data class Rating(
        val score: Int,
    ) : SurveyAnswer()
}

internal data class SelectedOption(
    val id: String?,
    val text: String,
    val isOther: Boolean = false,
) : java.io.Serializable {
    fun toSubmissionValue(): SubmissionOptionValue =
        SubmissionOptionValue(
            id = id,
            text = text,
            isOther = isOther,
        )
}

internal data class SubmissionOptionValue(
    @param:Json(name = "id") val id: String?,
    @param:Json(name = "text") val text: String?,
    @param:Json(name = "isOther") val isOther: Boolean = false,
)

internal data class SurveySubmissionParams(
    val survey: SurveyDocument,
    val existingSubmission: DashboardSurveySubmissionsRepository.SubmissionLookup?,
    val username: String?,
    val fullName: String,
    val parentId: String,
    val answersPayload: List<SubmissionAnswer>,
    val totalGrade: Int,
    val profile: UserProfile?,
)
