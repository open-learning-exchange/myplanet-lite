/*
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-12-12
 */

package org.ole.planet.myplanet.lite

import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.ole.planet.myplanet.lite.dashboard.DashboardServerPreferences
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveyStatusStore
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveySubmissionsRepository
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveySubmissionsRepository.SubmissionAnswer
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveySubmissionsRepository.SubmissionParent
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveySubmissionsRepository.SubmissionTeam
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveySubmissionsRepository.SurveySubmission
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyDocument
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyQuestion
import org.ole.planet.myplanet.lite.profile.UserProfileDatabase
import org.ole.planet.myplanet.lite.util.NetworkUtils
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider
import kotlin.math.roundToInt

internal fun SurveyWizardFragment.buildSubmissionPayload(): Pair<List<SubmissionAnswer>, Int> {
    val answersPayload =
        questions.mapIndexed { index, question ->
            val answer = answers[index] ?: throw IllegalStateException("Missing answer for question $index")
            if (isExam) {
                val correct = isAnswerCorrect(question, answer)
                val grade = if (correct) question.marks ?: 1 else 0
                val mistakes = if (correct) 0 else 1
                val value =
                    when (answer) {
                        is SurveyAnswer.Text -> answer.value
                        is SurveyAnswer.SingleChoice -> answer.choice?.toSubmissionValue()
                        is SurveyAnswer.MultipleChoice -> answer.choices.map { it.toSubmissionValue() }
                        is SurveyAnswer.Rating -> answer.score.toString()
                    }
                SubmissionAnswer(
                    value = value,
                    mistakes = mistakes,
                    passed = correct,
                    grade = grade,
                )
            } else {
                when (answer) {
                    is SurveyAnswer.Text -> {
                        SubmissionAnswer(value = answer.value)
                    }

                    is SurveyAnswer.SingleChoice -> {
                        SubmissionAnswer(
                            value = answer.choice?.toSubmissionValue(),
                        )
                    }

                    is SurveyAnswer.MultipleChoice -> {
                        SubmissionAnswer(
                            value = answer.choices.map { it.toSubmissionValue() },
                        )
                    }

                    is SurveyAnswer.Rating -> {
                        SubmissionAnswer(value = answer.score.toString())
                    }
                }
            }
        }
    val totalGrade =
        if (isExam) {
            answersPayload.sumOf { it.grade }
        } else {
            0
        }
    return answersPayload to totalGrade
}

internal fun SurveyWizardFragment.submitSurvey() {
    if (isSubmitting) return
    val survey = document ?: return
    val base =
        baseUrl?.takeIf { it.isNotBlank() }
            ?: DashboardServerPreferences.getServerBaseUrl(requireContext().applicationContext)
    if (base.isNullOrBlank()) {
        showValidationMessage(R.string.dashboard_surveys_missing_server)
        return
    }

    val (answersPayload, totalGrade) =
        try {
            buildSubmissionPayload()
        } catch (_: IllegalStateException) {
            showValidationMessage(R.string.dashboard_survey_wizard_input_required)
            return
        }
    if (isExam && !isExamPassing()) {
        showValidationMessage(R.string.dashboard_exam_incorrect_answers)
        return
    }

    if (baseUrlOverride != null && !includeUserContext) {
        submitPublicSurvey(base, survey, answersPayload)
        return
    }

    val profile =
        if (includeUserContext) {
            UserProfileDatabase.getInstance(requireContext()).getProfile()
        } else {
            null
        }
    val username =
        (profile?.username ?: credentials?.username)
            ?.takeIf { includeUserContext }
    if (username.isNullOrBlank() && isExam) {
        showValidationMessage(R.string.dashboard_survey_wizard_submission_failed)
        return
    }
    val fullName =
        listOfNotNull(profile?.firstName, profile?.middleName, profile?.lastName)
            .joinToString(" ")
            .trim()
            .takeIf { it.isNotBlank() }
            ?: username
            ?: ANONYMOUS_RESPONDENT_NAME

    val parentId = buildSubmissionParentId(survey)
    if (parentId == null) {
        showValidationMessage(R.string.dashboard_survey_wizard_submission_failed)
        return
    }

    viewLifecycleOwner.lifecycleScope.launch {
        setSubmitting(true)
        val existingSubmission = fetchExistingSubmissionOrNull(base, username, parentId, survey)
        val submission =
            buildSurveySubmission(
                SurveySubmissionParams(
                    survey = survey,
                    existingSubmission = existingSubmission,
                    username = username,
                    fullName = fullName,
                    parentId = parentId,
                    answersPayload = answersPayload,
                    totalGrade = totalGrade,
                    profile = profile,
                ),
            )
        processSubmission(base, submission, survey, username)
    }
}

internal fun SurveyWizardFragment.submitPublicSurvey(
    base: String,
    survey: SurveyDocument,
    answersPayload: List<SubmissionAnswer>,
) {
    val publicTeamId = teamId ?: survey.teamId
    val publicSurveyId = survey.id
    if (publicTeamId.isNullOrBlank() || publicSurveyId.isNullOrBlank()) {
        showValidationMessage(R.string.dashboard_survey_wizard_submission_failed)
        return
    }
    viewLifecycleOwner.lifecycleScope.launch {
        setSubmitting(true)
        val result =
            submissionRepository.submitPublicSurvey(
                baseUrl = base,
                teamId = publicTeamId,
                surveyId = publicSurveyId,
                answers = answersPayload.map { it.value },
            )
        setSubmitting(false)
        if (result.isSuccess) {
            Toast
                .makeText(
                    requireContext(),
                    getString(R.string.dashboard_survey_wizard_completed),
                    Toast.LENGTH_SHORT,
                ).show()
            // After a public (deep-link) survey, route home: SplashScreen sends a logged-in
            // user to the dashboard and an anonymous respondent to the login screen.
            navigateHomeAfterPublicSurvey()
        } else {
            showValidationMessage(R.string.dashboard_survey_wizard_submission_failed)
        }
    }
}

internal suspend fun SurveyWizardFragment.fetchExistingSubmissionOrNull(
    base: String,
    username: String?,
    parentId: String,
    survey: SurveyDocument,
): DashboardSurveySubmissionsRepository.SubmissionLookup? {
    if (username.isNullOrBlank()) {
        return null
    }
    return courseId?.let {
        submissionRepository
            .fetchExistingSubmission(
                base,
                credentials,
                sessionCookie,
                parentId,
                userId = if (isExam) null else "org.couchdb.user:$username",
                userName = if (isExam) username else null,
                parentRev = if (isExam) survey.rev else null,
                type = if (isExam) "exam" else "survey",
            ).getOrNull()
    }
}

internal fun SurveyWizardFragment.buildSurveySubmission(params: SurveySubmissionParams): SurveySubmission {
    val survey = params.survey
    val existingSubmission = params.existingSubmission
    val username = params.username
    val fullName = params.fullName
    val parentId = params.parentId
    val answersPayload = params.answersPayload
    val totalGrade = params.totalGrade
    val profile = params.profile

    val rawProfile = parseProfileRawDocument(profile?.rawDocument)
    val fallbackUserId = username?.let { "org.couchdb.user:$it" }
    val resolvedUserId = rawProfile?.optString("_id").takeIf { !it.isNullOrBlank() } ?: fallbackUserId
    val resolvedUserName =
        rawProfile?.optString("name").takeIf { !it.isNullOrBlank() }
            ?: listOfNotNull(respondent.firstName, respondent.middleName, respondent.lastName)
                .joinToString(" ")
                .trim()
                .takeIf { it.isNotBlank() }
            ?: fullName
    val resolvedPlanetCode = rawProfile?.optString("planetCode").takeIf { !it.isNullOrBlank() } ?: serverCode
    val resolvedParentCode = rawProfile?.optString("parentCode").takeIf { !it.isNullOrBlank() } ?: parentCode
    return SurveySubmission(
        id = existingSubmission?.id,
        rev = existingSubmission?.rev,
        type = if (isExam) "exam" else "survey",
        parentId = parentId,
        parent =
            SubmissionParent(
                id = survey.id,
                rev = survey.rev,
                name = survey.name,
                type = if (isExam) "courses" else "surveys",
                passingPercentage = survey.passingPercentage,
                teamShareAllowed = if (isExam) null else false,
                questions = survey.questions,
                description = survey.description,
            ),
        user =
            DashboardSurveySubmissionsRepository.SubmissionUser(
                id = resolvedUserId,
                name = resolvedUserName,
                planetCode = resolvedPlanetCode,
                parentCode = resolvedParentCode,
                firstName = respondent.firstName ?: rawProfile?.optString("firstName").takeIf { !it.isNullOrBlank() },
                middleName = respondent.middleName ?: rawProfile?.optString("middleName").takeIf { !it.isNullOrBlank() },
                lastName = respondent.lastName ?: rawProfile?.optString("lastName").takeIf { !it.isNullOrBlank() },
                email = respondent.email ?: rawProfile?.optString("email").takeIf { !it.isNullOrBlank() },
                language = respondent.language ?: rawProfile?.optString("language").takeIf { !it.isNullOrBlank() },
                phoneNumber = respondent.phoneNumber ?: rawProfile?.optString("phoneNumber").takeIf { !it.isNullOrBlank() },
                birthDate = respondent.birthDate ?: rawProfile?.optString("birthDate").takeIf { !it.isNullOrBlank() },
                age = respondent.age ?: rawProfile.optIntOrNull("age"),
                gender = respondent.gender ?: rawProfile?.optString("gender").takeIf { !it.isNullOrBlank() },
                level = respondent.level ?: rawProfile?.optString("level").takeIf { !it.isNullOrBlank() },
            ),
        team =
            (teamId ?: survey.teamId)?.let { id ->
                SubmissionTeam(
                    id = id,
                    name = teamName,
                    type = "local",
                )
            },
        answers = answersPayload,
        grade = totalGrade,
        status = if (isExam) resolveExamStatus(survey) else "complete",
        startTime = startTimeMillis,
        lastUpdateTime = System.currentTimeMillis(),
        source = serverCode,
        parentCode = parentCode,
        deviceName =
            org.ole.planet.myplanet.lite.util.DeviceUtils
                .getDeviceName(),
        customDeviceName = resolveCustomDeviceName(),
    )
}

internal fun SurveyWizardFragment.parseProfileRawDocument(rawDocument: String?): JSONObject? {
    if (rawDocument.isNullOrBlank()) return null
    return runCatching { JSONObject(rawDocument) }.getOrNull()
}

internal fun JSONObject?.optIntOrNull(key: String): Int? {
    this ?: return null
    if (!has(key)) return null
    return when (val value = opt(key)) {
        null -> null
        is Number -> value.toInt()
        is String -> value.toIntOrNull()
        else -> null
    }
}

internal suspend fun SurveyWizardFragment.processSubmission(
    base: String,
    submission: SurveySubmission,
    survey: SurveyDocument,
    username: String?,
) {
    val isOnline = NetworkUtils.isDeviceOnline(requireContext())
    if (!isOnline) {
        setSubmitting(false)
        if (baseUrlOverride != null) {
            showValidationMessage(R.string.dashboard_survey_wizard_submission_failed)
            return
        }
        queueSubmissionForOutbox(submission, survey)
        return
    }

    val result = submissionRepository.submitSurvey(base, credentials, sessionCookie, submission)
    setSubmitting(false)
    if (result.isSuccess) {
        DashboardSurveyStatusStore(
            requireContext().applicationContext,
            username,
        ).markCompleted(survey.id)
        Toast
            .makeText(
                requireContext(),
                getString(
                    if (isExam) {
                        R.string.dashboard_exam_completed
                    } else {
                        R.string.dashboard_survey_wizard_completed
                    },
                ),
                Toast.LENGTH_SHORT,
            ).show()
        finishWithResult()
    } else {
        if (baseUrlOverride != null) {
            showValidationMessage(R.string.dashboard_survey_wizard_submission_failed)
            return
        }
        queueSubmissionForOutbox(submission, survey)
    }
}

internal fun SurveyWizardFragment.queueSubmissionForOutbox(
    submission: SurveySubmission,
    survey: SurveyDocument,
) {
    viewLifecycleOwner.lifecycleScope.launch {
        val saved =
            localSurveyRepository.saveSubmission(
                submission = submission,
                surveyId = survey.id,
                surveyName = survey.name,
                teamId = teamId,
                teamName = teamName,
            )
        if (saved) {
            Toast
                .makeText(
                    requireContext(),
                    getString(R.string.dashboard_survey_submission_saved_offline),
                    Toast.LENGTH_SHORT,
                ).show()
            finishWithResult()
        } else {
            showValidationMessage(R.string.dashboard_survey_wizard_submission_failed)
        }
    }
}

internal suspend fun SurveyWizardFragment.flushPendingSurveySubmissions() {
    localSurveyRepository.flushPendingSurveyOutbox()
}

internal fun SurveyWizardFragment.buildSubmissionParentId(survey: SurveyDocument): String? {
    val surveyId = survey.id?.takeIf { it.isNotBlank() } ?: return null
    val course = courseId?.takeIf { it.isNotBlank() }
    return if (course == null) {
        surveyId
    } else {
        "$surveyId@$course"
    }
}

internal fun SurveyWizardFragment.resolveExamStatus(survey: SurveyDocument): String {
    val requiresGrading =
        survey.questions.orEmpty().any { question ->
            question.type.equals("input", ignoreCase = true) ||
                question.type.equals("textarea", ignoreCase = true)
        }
    return if (requiresGrading) "requires grading" else "complete"
}

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
    val correctChoice = question.correctChoice
    val normalizedCorrect = normalizeCorrectChoice(correctChoice)
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
        is String -> {
            listOf(correctChoice)
        }

        is List<*> -> {
            correctChoice.mapNotNull { item ->
                when (item) {
                    is Map<*, *> -> {
                        item["id"]?.toString()
                            ?: item["_id"]?.toString()
                            ?: item["text"]?.toString()
                    }

                    else -> {
                        item?.toString()
                    }
                }
            }
        }

        null -> {
            emptyList()
        }

        else -> {
            listOf(correctChoice.toString())
        }
    }

internal fun SurveyWizardFragment.normalizeSelectedId(rawId: String?): String? {
    val trimmed = rawId?.trim().orEmpty()
    if (trimmed.isBlank()) return null
    return trimmed.substringBefore("/").trim().takeIf { it.isNotBlank() }
}

internal fun SurveyWizardFragment.resolveCustomDeviceName(): String? {
    val prefs = SecurePreferencesProvider.getServerPreferences(requireContext().applicationContext)
    return prefs.getString(KEY_DEVICE_CUSTOM_DEVICE_NAME, null)?.trim()?.takeIf { it.isNotBlank() }
}
