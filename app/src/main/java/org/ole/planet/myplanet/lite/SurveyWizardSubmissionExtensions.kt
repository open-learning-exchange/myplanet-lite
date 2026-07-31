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
import org.ole.planet.myplanet.lite.survey.SubmitOutcome
import org.ole.planet.myplanet.lite.profile.UserProfileDatabase
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider

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
    val outcome = localSurveyRepository.submitOrQueueSubmission(
        base = base,
        credentials = credentials,
        sessionCookie = sessionCookie,
        submission = submission,
        surveyId = survey.id,
        surveyName = survey.name,
        teamId = teamId,
        teamName = teamName,
        baseUrlOverride = baseUrlOverride,
        forceOfflineQueue = offlineMode,
    )

    setSubmitting(false)

    when (outcome) {
        SubmitOutcome.SUBMITTED_ONLINE -> {
            deleteDraft()
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
        }
        SubmitOutcome.QUEUED_OFFLINE -> {
            deleteDraft()
            Toast
                .makeText(
                    requireContext(),
                    getString(R.string.dashboard_survey_submission_saved_offline),
                    Toast.LENGTH_SHORT,
                ).show()
            finishWithResult()
        }
        SubmitOutcome.FAILED -> {
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

internal fun SurveyWizardFragment.resolveCustomDeviceName(): String? {
    val prefs = SecurePreferencesProvider.getServerPreferences(requireContext().applicationContext)
    return prefs.getString(KEY_DEVICE_CUSTOM_DEVICE_NAME, null)?.trim()?.takeIf { it.isNotBlank() }
}
