package org.ole.planet.myplanet.lite

import androidx.appcompat.app.AlertDialog
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveyDraftStore
import org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore

internal fun SurveyWizardFragment.isDraftSupported(): Boolean =
    courseId == null && !isExam && baseUrlOverride == null

internal fun SurveyWizardFragment.saveDraft() {
    if (!isDraftSupported() || draftPersistenceDisabled) return
    val survey = document ?: return
    val owner = ProfileCredentialsStore.getStoredCredentials(requireContext())?.username
    val key = draftKey ?: DashboardSurveyDraftStore.key(survey.id, teamId, owner)
    draftKey = key
    draftStore.save(
        DashboardSurveyDraftStore.DraftEntry(
            key = key,
            document = survey,
            teamId = teamId,
            teamName = teamName,
            owner = owner,
            currentIndex = currentIndex,
            answers = answers.toMap(),
            respondent = respondent.copy(),
            birthDateSelection = birthDateSelection,
            updatedAt = System.currentTimeMillis(),
        ),
    )
}

internal fun SurveyWizardFragment.restoreDraftIfAvailable() {
    val key = draftKey ?: return
    val draft = draftStore.get(key) ?: return
    answers.clear()
    answers.putAll(draft.answers)
    respondent.restoreFrom(draft.respondent)
    birthDateSelection = draft.birthDateSelection
    includeOptionalDetails = respondent.additionalInfo
    steps = buildSteps(includeOptionalDetails)
    currentIndex = draft.currentIndex.coerceIn(0, steps.lastIndex.coerceAtLeast(0))
}

private fun SurveyRespondent.restoreFrom(saved: SurveyRespondent) {
    gender = saved.gender
    birthYear = saved.birthYear
    age = saved.age
    additionalInfo = saved.additionalInfo
    firstName = saved.firstName
    middleName = saved.middleName
    lastName = saved.lastName
    birthDate = saved.birthDate
    email = saved.email
    phoneNumber = saved.phoneNumber
    language = saved.language
    level = saved.level
}

internal fun SurveyWizardFragment.deleteDraft() {
    draftPersistenceDisabled = true
    draftKey?.let(draftStore::delete)
}

internal fun SurveyWizardFragment.handleExitRequest(onExit: () -> Unit) {
    if (!isDraftSupported()) {
        onExit()
        return
    }
    AlertDialog.Builder(requireContext())
        .setTitle(R.string.dashboard_survey_draft_exit_title)
        .setMessage(R.string.dashboard_survey_draft_exit_message)
        .setPositiveButton(R.string.dashboard_survey_draft_keep) { _, _ ->
            saveDraft()
            onExit()
        }
        .setNegativeButton(R.string.dashboard_survey_draft_discard) { _, _ ->
            deleteDraft()
            onExit()
        }
        .setNeutralButton(android.R.string.cancel, null)
        .show()
}
