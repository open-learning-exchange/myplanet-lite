/*
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-12-12
 */

package org.ole.planet.myplanet.lite

import android.app.Activity
import android.content.Intent
import android.view.View
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import org.ole.planet.myplanet.lite.auth.AuthDependencies
import org.ole.planet.myplanet.lite.dashboard.DashboardServerPreferences
import org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore
import java.util.Locale

internal fun SurveyWizardFragment.setupNavigationButtons() {
    previousButton.setOnClickListener {
        activeCollector?.invoke()
        if (currentIndex > 0) {
            currentIndex -= 1
            showStep(currentIndex)
        }
    }

    nextButton.setOnClickListener {
        val collector = activeCollector ?: return@setOnClickListener
        if (collector()) {
            if (currentIndex < steps.lastIndex) {
                currentIndex += 1
                showStep(currentIndex)
            } else {
                submitSurvey()
            }
        }
    }
}

internal fun SurveyWizardFragment.setupInsets(view: View) {
    val contentView: View = view.findViewById(R.id.surveyWizardContent)
    val initialPaddingStart = contentView.paddingStart
    val initialPaddingTop = contentView.paddingTop
    val initialPaddingEnd = contentView.paddingEnd
    val initialPaddingBottom = contentView.paddingBottom

    ViewCompat.setOnApplyWindowInsetsListener(contentView) { content, insets ->
        val systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
        val bottomInset = maxOf(systemInsets.bottom, imeInsets.bottom)
        content.setPadding(
            initialPaddingStart,
            initialPaddingTop,
            initialPaddingEnd,
            initialPaddingBottom + bottomInset,
        )
        insets
    }
}

internal fun SurveyWizardFragment.bindViews(view: View) {
    titleView = view.findViewById(R.id.surveyWizardTitle)
    descriptionView = view.findViewById(R.id.surveyWizardDescription)
    counterView = view.findViewById(R.id.surveyWizardCounter)
    progressBar = view.findViewById(R.id.surveyWizardProgress)
    translationOverlay = view.findViewById(R.id.surveyWizardTranslationOverlay)
    translationProgressBar = view.findViewById(R.id.surveyWizardTranslationProgress)
    translationNoticeView = view.findViewById(R.id.surveyWizardTranslationNotice)
    questionBodyView = view.findViewById(R.id.surveyWizardQuestionBody)
    questionScrollView = view.findViewById(R.id.surveyWizardQuestionScrollView)
    questionContainer = view.findViewById(R.id.surveyWizardQuestionContainer)
    previousButton = view.findViewById(R.id.surveyWizardPreviousButton)
    nextButton = view.findViewById(R.id.surveyWizardNextButton)
}

internal suspend fun SurveyWizardFragment.initializeSession() {
    val context = requireContext().applicationContext
    baseUrl = baseUrlOverride ?: DashboardServerPreferences.getServerBaseUrl(context)
    credentials =
        if (includeUserContext) {
            ProfileCredentialsStore.getStoredCredentials(context)
        } else {
            null
        }
    serverCode = if (includeUserContext) DashboardServerPreferences.getServerCode(context) else null
    parentCode = if (includeUserContext) DashboardServerPreferences.getServerParentCode(context) else null
    baseUrl?.takeIf { includeUserContext }?.let { base ->
        val authService = AuthDependencies.provideAuthService(context, base)
        sessionCookie = authService.getStoredToken()
    }
}

internal suspend fun SurveyWizardFragment.attemptSurveyTranslation() {
    val survey = document ?: return
    if (!checkTranslationAllowed()) return
    val base = getTranslationBaseUrl() ?: return
    val targetLanguage = determineTargetLanguage()
    targetSurveyLanguage = targetLanguage
    val detectedLanguage = translationManager.detectSurveyLanguage(survey)
    detectedSurveyLanguage = detectedLanguage
    val shouldShowTranslationProgress =
        detectedLanguage != null &&
            !detectedLanguage.equals(targetLanguage, ignoreCase = true)
    setTranslationInProgress(shouldShowTranslationProgress)
    val result =
        translationManager.translateSurvey(
            base,
            survey,
            targetLanguage,
            detectedLanguage,
        )
    setTranslationInProgress(false)
    handleTranslationResult(result)
}

private fun SurveyWizardFragment.checkTranslationAllowed(): Boolean {
    val translationPreferenceEnabled = DashboardActivity.isSurveyTranslationEnabled(requireContext())
    val translationConsentAccepted = DashboardActivity.isSurveyTranslationConsentAccepted(requireContext())
    val translationAllowed = translationPreferenceEnabled && translationConsentAccepted
    if (!translationAllowed) {
        translatedTitle = null
        translatedDescription = null
        questionTranslations = emptyMap()
        translationApplied = false
        updateTranslationNotice(
            showConsentNotice = translationPreferenceEnabled,
            showAppliedNotice = false,
        )
        setTranslationInProgress(false)
    }
    return translationAllowed
}

private fun SurveyWizardFragment.getTranslationBaseUrl(): String? =
    baseUrl?.takeIf { it.isNotBlank() }
        ?: DashboardServerPreferences.getServerBaseUrl(requireContext().applicationContext)

private fun SurveyWizardFragment.determineTargetLanguage(): String {
    val appLocales = AppCompatDelegate.getApplicationLocales()
    return appLocales[0]
        ?.language
        ?.takeIf { it.isNotBlank() }
        ?: Locale.getDefault().language
}

private fun SurveyWizardFragment.handleTranslationResult(
    result: org.ole.planet.myplanet.lite.surveys.SurveyTranslationManager.SurveyTranslationResult
) {
    translatedTitle = result.titleTranslation?.takeIf { it.isNotBlank() }
    translatedDescription = result.descriptionTranslation?.takeIf { it.isNotBlank() }
    applySurveyTranslations()
    val translations = result.translations.filterValues { it.hasTranslation }
    if (translations.isNotEmpty()) {
        questionTranslations = translations
        if (isAdded) {
            val currentStepValue = steps.getOrNull(currentIndex)
            if (currentStepValue is WizardStep.Question) {
                showStep(currentIndex)
            }
        }
    }
    translationApplied = translatedTitle != null || translatedDescription != null ||
        translations.isNotEmpty() || (
            detectedSurveyLanguage != null &&
                targetSurveyLanguage != null &&
                !detectedSurveyLanguage.equals(targetSurveyLanguage, ignoreCase = true)
        )
    updateTranslationNotice(
        showConsentNotice = false,
        showAppliedNotice = translationApplied,
    )
}

internal fun SurveyWizardFragment.applySurveyTranslations() {
    val survey = document ?: return
    val titleText =
        translatedTitle?.takeIf { it.isNotBlank() }
            ?: survey.name.orEmpty()
    setSurveyTitle(titleText)
    val descriptionText =
        translatedDescription?.takeIf { it.isNotBlank() }
            ?: survey.description.orEmpty()
    setSurveyDescription(descriptionText)
    updateDescriptionVisibility(currentIndex)
}

internal fun SurveyWizardFragment.setSurveyTitle(title: String) {
    markwon.setMarkdown(titleView, title.replace("\n", "  \n"))
}

internal fun SurveyWizardFragment.setSurveyDescription(description: String) {
    val rendered = description.replace("\n", "  \n")
    markwon.setMarkdown(descriptionView, rendered)
}

internal fun SurveyWizardFragment.setQuestionBody(body: String) {
    markwon.setMarkdown(questionBodyView, body.replace("\n", "  \n"))
}

internal fun SurveyWizardFragment.updateTranslationNotice(
    showConsentNotice: Boolean,
    showAppliedNotice: Boolean,
) {
    when {
        showConsentNotice -> {
            translationNoticeView.text = getString(R.string.dashboard_survey_translation_notice)
            translationNoticeView.isVisible = true
        }

        showAppliedNotice -> {
            translationNoticeView.text = getString(R.string.dashboard_survey_translation_applied_notice)
            translationNoticeView.isVisible = true
        }

        else -> {
            translationNoticeView.isVisible = false
        }
    }
}

internal fun SurveyWizardFragment.setTranslationInProgress(inProgress: Boolean) {
    translationOverlay.isVisible = inProgress
    translationProgressBar.isVisible = inProgress
    if (inProgress) {
        translationOverlay.bringToFront()
    }
    updateNavigationEnabled()
}

internal fun SurveyWizardFragment.updateNavigationEnabled() {
    val enabled = !isSubmitting && !translationOverlay.isVisible
    previousButton.isEnabled = enabled && currentIndex > 0
    nextButton.isEnabled = enabled
}

internal fun SurveyWizardFragment.updateDescriptionVisibility(stepIndex: Int) {
    descriptionView.isVisible = stepIndex == 0 && descriptionView.text.isNotBlank()
}

internal fun SurveyWizardFragment.renderStep(step: WizardStep): Pair<View, () -> Boolean> =
    when (step) {
        WizardStep.Basics -> {
            renderBasicsStep()
        }

        WizardStep.Names -> {
            renderNamesStep()
        }

        WizardStep.BirthDate -> {
            renderBirthDateStep()
        }

        WizardStep.Contact -> {
            renderContactStep()
        }

        WizardStep.LanguageLevel -> {
            renderLanguageLevelStep()
        }

        is WizardStep.Question -> {
            renderQuestion(
                step.question,
                step.questionIndex,
                questionTranslations[step.questionIndex],
            )
        }
    }

internal fun SurveyWizardFragment.buildSteps(includeDetails: Boolean): List<WizardStep> =
    buildList {
        add(WizardStep.Basics)
        if (includeDetails) {
            add(WizardStep.Names)
            add(WizardStep.BirthDate)
            add(WizardStep.Contact)
            add(WizardStep.LanguageLevel)
        }
        questions.forEachIndexed { index, question ->
            add(WizardStep.Question(question, index))
        }
    }

internal fun SurveyWizardFragment.finishWithResult() {
    requireActivity().setResult(Activity.RESULT_OK)
    requireActivity().finish()
}

internal fun SurveyWizardFragment.navigateHomeAfterPublicSurvey() {
    val intent =
        Intent(requireContext().applicationContext, SplashScreen::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
    startActivity(intent)
    requireActivity().finish()
}

internal fun SurveyWizardFragment.setSubmitting(submitting: Boolean) {
    isSubmitting = submitting
    updateNavigationEnabled()
}

internal fun SurveyWizardFragment.showValidationMessage(
    @StringRes messageRes: Int,
) {
    Toast.makeText(requireContext(), getString(messageRes), Toast.LENGTH_SHORT).show()
}
