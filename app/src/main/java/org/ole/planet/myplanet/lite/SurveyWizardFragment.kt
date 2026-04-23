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
import android.net.ConnectivityManager
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

class SurveyWizardFragment : Fragment(R.layout.fragment_survey_wizard) {

    private var document: SurveyDocument? = null
    private var questions: List<SurveyQuestion> = emptyList()
    private var teamId: String? = null
    private var teamName: String? = null
    private var courseId: String? = null
    private var steps: List<WizardStep> = emptyList()
    private var currentIndex = 0
    private val answers: MutableMap<Int, SurveyAnswer> = mutableMapOf()
    private var activeCollector: (() -> Boolean)? = null
    private val respondent = SurveyRespondent()
    private var birthDateSelection: Long? = null
    private var includeOptionalDetails = false
    private val submissionRepository = DashboardSurveySubmissionsRepository()
    private var baseUrl: String? = null
    private var credentials: StoredCredentials? = null
    private var sessionCookie: String? = null
    private var serverCode: String? = null
    private var parentCode: String? = null
    private var isExam: Boolean = false
    private var startTimeMillis: Long = System.currentTimeMillis()
    private var isSubmitting = false
    private lateinit var translationManager: SurveyTranslationManager
    private var questionTranslations: Map<Int, TranslatedQuestion> = emptyMap()
    private var targetSurveyLanguage: String? = null
    private var detectedSurveyLanguage: String? = null
    private var translatedTitle: String? = null
    private var translatedDescription: String? = null
    private val localSurveyRepository by lazy { DashboardLocalSurveyRepository(requireContext()) }
    private val connectivityManager by lazy {
        context?.getSystemService(ConnectivityManager::class.java)
    }

    private lateinit var titleView: TextView
    private lateinit var descriptionView: TextView
    private lateinit var counterView: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var translationOverlay: View
    private lateinit var translationProgressBar: ProgressBar
    private lateinit var translationNoticeView: TextView
    private var translationApplied: Boolean = false
    private lateinit var questionBodyView: TextView
    private lateinit var questionScrollView: ScrollView
    private lateinit var questionContainer: LinearLayout
    private lateinit var previousButton: Button
    private lateinit var nextButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        document = arguments?.let { args ->
            BundleCompat.getSerializable(args, ARG_DOCUMENT, SurveyDocument::class.java)
        }
        teamId = arguments?.getString(ARG_TEAM_ID)
        teamName = arguments?.getString(ARG_TEAM_NAME)
        courseId = arguments?.getString(ARG_COURSE_ID)
        isExam = arguments?.getBoolean(ARG_IS_EXAM) == true
        questions = document?.questions.orEmpty()
        applyProfileDefaultsForCourseContent()
        birthDateSelection = respondent.birthDate?.let { parseBirthDateIso(it) } ?: birthDateSelection
        includeOptionalDetails = respondent.additionalInfo
        steps = buildSteps(includeOptionalDetails)
        translationManager = SurveyTranslationManager(requireContext().applicationContext)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindViews(view)
        setupInsets(view)

        translationNoticeView.setOnClickListener {
            startActivity(Intent(requireContext(), PrivacyPolicyActivity::class.java))
        }

        lifecycleScope.launch {
            initializeSession()
            attemptSurveyTranslation()
            flushPendingSurveySubmissions()
        }

        val survey = document
        if (survey == null || questions.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.dashboard_survey_wizard_empty_questions), Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()
            return
        }

        titleView.text = survey.name.orEmpty()
        val description = survey.description?.takeIf { it.isNotBlank() }
        descriptionView.text = description.orEmpty()
        descriptionView.isVisible = !description.isNullOrBlank()

        progressBar.max = steps.size

        showStep(currentIndex)

        setupNavigationButtons()
    }

    private fun setupNavigationButtons() {
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

    private fun setupInsets(view: View) {
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
                initialPaddingBottom + bottomInset
            )
            insets
        }
    }

    private fun bindViews(view: View) {
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

    override fun onStart() {
        super.onStart()
        viewLifecycleOwner.lifecycleScope.launch {
            flushPendingSurveySubmissions()
        }
    }

    private suspend fun initializeSession() {
        val context = requireContext().applicationContext
        baseUrl = DashboardServerPreferences.getServerBaseUrl(context)
        credentials = ProfileCredentialsStore.getStoredCredentials(context)
        serverCode = DashboardServerPreferences.getServerCode(context)
        parentCode = DashboardServerPreferences.getServerParentCode(context)
        baseUrl?.let { base ->
            val authService = AuthDependencies.provideAuthService(context, base)
            sessionCookie = authService.getStoredToken()
        }
    }

    private suspend fun attemptSurveyTranslation() {
        val survey = document ?: return
        val translationPreferenceEnabled = DashboardActivity.isSurveyTranslationEnabled(requireContext())
        val translationConsentAccepted = DashboardActivity.isSurveyTranslationConsentAccepted(requireContext())
        val translationAllowed = translationPreferenceEnabled && translationConsentAccepted
        if (!translationAllowed) {
            translatedTitle = null
            translatedDescription = null
            questionTranslations = emptyMap()
            translationApplied = false
            updateTranslationNotice(
                showConsentNotice = translationPreferenceEnabled && !translationConsentAccepted,
                showAppliedNotice = false,
            )
            setTranslationInProgress(false)
            return
        }
        val base = baseUrl?.takeIf { it.isNotBlank() }
            ?: DashboardServerPreferences.getServerBaseUrl(requireContext().applicationContext)
            ?: return
        val appLocales = AppCompatDelegate.getApplicationLocales()
        val targetLanguage = appLocales[0]?.language
            ?.takeIf { it.isNotBlank() }
            ?: Locale.getDefault().language
        targetSurveyLanguage = targetLanguage
        val detectedLanguage = translationManager.detectSurveyLanguage(survey)
        detectedSurveyLanguage = detectedLanguage
        val shouldShowTranslationProgress = detectedLanguage != null &&
            !detectedLanguage.equals(targetLanguage, ignoreCase = true)
        setTranslationInProgress(shouldShowTranslationProgress)
        val result = translationManager.translateSurvey(
            base,
            survey,
            targetLanguage,
            detectedLanguage,
        )
        setTranslationInProgress(false)
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
            translations.isNotEmpty() || (detectedSurveyLanguage != null &&
            targetSurveyLanguage != null &&
            !detectedSurveyLanguage.equals(targetSurveyLanguage, ignoreCase = true))
        updateTranslationNotice(
            showConsentNotice = translationPreferenceEnabled && !translationConsentAccepted,
            showAppliedNotice = translationApplied,
        )
    }

    private fun applySurveyTranslations() {
        val survey = document ?: return
        val titleText = translatedTitle?.takeIf { it.isNotBlank() }
            ?: survey.name.orEmpty()
        titleView.text = titleText
        val descriptionText = translatedDescription?.takeIf { it.isNotBlank() }
            ?: survey.description.orEmpty()
        descriptionView.text = descriptionText
        updateDescriptionVisibility(currentIndex)
    }

    private fun updateTranslationNotice(showConsentNotice: Boolean, showAppliedNotice: Boolean) {
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

    private fun setTranslationInProgress(inProgress: Boolean) {
        translationOverlay.isVisible = inProgress
        translationProgressBar.isVisible = inProgress
        if (inProgress) {
            translationOverlay.bringToFront()
        }
        updateNavigationEnabled()
    }

    private fun updateNavigationEnabled() {
        val enabled = !isSubmitting && !translationOverlay.isVisible
        previousButton.isEnabled = enabled && currentIndex > 0
        nextButton.isEnabled = enabled
    }

    private fun showStep(index: Int) {
        val step = steps[index]
        progressBar.max = steps.size
        counterView.text = getString(R.string.dashboard_survey_wizard_step_counter, index + 1, steps.size)
        updateDescriptionVisibility(index)
        questionBodyView.text = when (step) {
            WizardStep.Basics -> getString(R.string.dashboard_survey_wizard_participant_basics_title)
            WizardStep.Names -> getString(R.string.dashboard_survey_wizard_names_title)
            WizardStep.BirthDate -> getString(R.string.dashboard_survey_wizard_birthdate_title)
            WizardStep.Contact -> getString(R.string.dashboard_survey_wizard_contact_title)
            WizardStep.LanguageLevel -> getString(R.string.dashboard_survey_wizard_language_level_title)
            is WizardStep.Question -> questionTranslations[step.questionIndex]?.body
                ?: step.question.body.orEmpty()
        }
        questionContainer.removeAllViews()
        val (renderedView, collector) = renderStep(step)
        questionContainer.addView(renderedView)
        activeCollector = collector
        progressBar.progress = index + 1
        val isLast = index == steps.lastIndex
        nextButton.text = if (isLast) {
            getString(R.string.dashboard_survey_wizard_finish)
        } else {
            getString(R.string.dashboard_survey_wizard_next)
        }
        updateNavigationEnabled()
    }

    private fun updateDescriptionVisibility(stepIndex: Int) {
        descriptionView.isVisible = stepIndex == 0 && descriptionView.text.isNotBlank()
    }

    private fun renderStep(step: WizardStep): Pair<View, () -> Boolean> {
        return when (step) {
            WizardStep.Basics -> renderBasicsStep()
            WizardStep.Names -> renderNamesStep()
            WizardStep.BirthDate -> renderBirthDateStep()
            WizardStep.Contact -> renderContactStep()
            WizardStep.LanguageLevel -> renderLanguageLevelStep()
            is WizardStep.Question -> renderQuestion(
                step.question,
                step.questionIndex,
                questionTranslations[step.questionIndex],
            )
        }
    }

    private fun buildSteps(includeDetails: Boolean): List<WizardStep> {
        return buildList {
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
    }

    private fun applyProfileDefaultsForCourseContent() {
        if (courseId.isNullOrBlank()) {
            return
        }
        val profile = UserProfileDatabase.getInstance(requireContext()).getProfile() ?: return
        var hasOptionalDetails = false
        fun assignIfEmpty(current: String?, incoming: String?): String? {
            return if (current.isNullOrBlank() && !incoming.isNullOrBlank()) {
                hasOptionalDetails = true
                incoming
            } else {
                current
            }
        }
        respondent.gender = assignIfEmpty(respondent.gender, profile.gender)
        respondent.firstName = assignIfEmpty(respondent.firstName, profile.firstName)
        respondent.middleName = assignIfEmpty(respondent.middleName, profile.middleName)
        respondent.lastName = assignIfEmpty(respondent.lastName, profile.lastName)
        respondent.email = assignIfEmpty(respondent.email, profile.email)
        respondent.phoneNumber = assignIfEmpty(respondent.phoneNumber, profile.phoneNumber)
        respondent.language = assignIfEmpty(respondent.language, profile.language)
        respondent.level = assignIfEmpty(respondent.level, profile.level)
        if (respondent.birthDate.isNullOrBlank() && !profile.birthDate.isNullOrBlank()) {
            respondent.birthDate = profile.birthDate
            birthDateSelection = parseBirthDateIso(profile.birthDate)
            hasOptionalDetails = true
        }
        val birthDateMillis = birthDateSelection
        if (birthDateMillis != null) {
            val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                timeInMillis = birthDateMillis
            }
            val year = calendar.get(Calendar.YEAR)
            if (respondent.birthYear == null) {
                respondent.birthYear = year
            }
            if (respondent.age == null) {
                val nowYear = Calendar.getInstance().get(Calendar.YEAR)
                respondent.age = nowYear - year
            }
        }
        if (hasOptionalDetails) {
            respondent.additionalInfo = true
        }
    }

    private fun renderQuestion(
        question: SurveyQuestion,
        index: Int,
        translation: TranslatedQuestion?,
    ): Pair<View, () -> Boolean> {
        return when (question.type) {
            "input" -> renderTextInputQuestion(index, false)
            "textarea" -> renderTextInputQuestion(index, true)
            "select" -> renderSingleChoiceQuestion(question, index, translation)
            "selectMultiple" -> renderMultiChoiceQuestion(question, index, translation)
            "ratingScale" -> renderRatingQuestion(index)
            else -> renderTextInputQuestion(index, false)
        }
    }

    private fun buildSubmissionPayload(): Pair<List<SubmissionAnswer>, Int> {
        val answersPayload = questions.mapIndexed { index, question ->
            val answer = answers[index] ?: throw IllegalStateException("Missing answer for question $index")
            if (isExam) {
                val correct = isAnswerCorrect(question, answer)
                val grade = if (correct) question.marks ?: 1 else 0
                val mistakes = if (correct) 0 else 1
                val value = when (answer) {
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
                    is SurveyAnswer.Text -> SubmissionAnswer(value = answer.value)
                    is SurveyAnswer.SingleChoice -> SubmissionAnswer(
                        value = answer.choice?.toSubmissionValue(),
                    )
                    is SurveyAnswer.MultipleChoice -> SubmissionAnswer(
                        value = answer.choices.map { it.toSubmissionValue() },
                    )
                    is SurveyAnswer.Rating -> SubmissionAnswer(value = answer.score.toString())
                }
            }
        }
        val totalGrade = if (isExam) {
            answersPayload.sumOf { it.grade }
        } else {
            0
        }
        return answersPayload to totalGrade
    }

    private fun submitSurvey() {
        if (isSubmitting) return
        val survey = document ?: return
        val base = baseUrl?.takeIf { it.isNotBlank() }
            ?: DashboardServerPreferences.getServerBaseUrl(requireContext().applicationContext)
        if (base.isNullOrBlank()) {
            showValidationMessage(R.string.dashboard_surveys_missing_server)
            return
        }

        val (answersPayload, totalGrade) = try {
            buildSubmissionPayload()
        } catch (_: IllegalStateException) {
            showValidationMessage(R.string.dashboard_survey_wizard_input_required)
            return
        }
        if (isExam && !isExamPassing()) {
            showValidationMessage(R.string.dashboard_exam_incorrect_answers)
            return
        }

        val profile = UserProfileDatabase.getInstance(requireContext()).getProfile()
        val username = profile?.username ?: credentials?.username
        if (username.isNullOrBlank()) {
            showValidationMessage(R.string.dashboard_survey_wizard_submission_failed)
            return
        }
        val fullName = listOfNotNull(profile?.firstName, profile?.middleName, profile?.lastName)
            .joinToString(" ")
            .trim()
            .takeIf { it.isNotBlank() }
            ?: username

        val parentId = buildSubmissionParentId(survey)
        if (parentId == null) {
            showValidationMessage(R.string.dashboard_survey_wizard_submission_failed)
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            setSubmitting(true)
            val existingSubmission = fetchExistingSubmissionOrNull(base, username, parentId, survey)
            val submission = buildSurveySubmission(
                survey, existingSubmission, username, fullName,
                parentId, answersPayload, totalGrade, profile
            )
            processSubmission(base, submission, survey, username)
        }
    }

    private suspend fun fetchExistingSubmissionOrNull(
        base: String,
        username: String,
        parentId: String,
        survey: SurveyDocument
    ): DashboardSurveySubmissionsRepository.SubmissionLookup? {
        return courseId?.let {
            submissionRepository.fetchExistingSubmission(
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

    private fun buildSurveySubmission(
        survey: SurveyDocument,
        existingSubmission: DashboardSurveySubmissionsRepository.SubmissionLookup?,
        username: String,
        fullName: String,
        parentId: String,
        answersPayload: List<SubmissionAnswer>,
        totalGrade: Int,
        profile: UserProfile?
    ): SurveySubmission {
        val rawProfile = parseProfileRawDocument(profile?.rawDocument)
        val fallbackUserId = "org.couchdb.user:$username"
        val resolvedUserId = rawProfile?.optString("_id").takeIf { !it.isNullOrBlank() } ?: fallbackUserId
        val resolvedUserName = rawProfile?.optString("name").takeIf { !it.isNullOrBlank() }
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
            parent = SubmissionParent(
                id = survey.id,
                rev = survey.rev,
                name = survey.name,
                type = if (isExam) "courses" else "surveys",
                passingPercentage = survey.passingPercentage,
                teamShareAllowed = if (isExam) null else false,
                questions = survey.questions,
                description = survey.description,
            ),
            user = DashboardSurveySubmissionsRepository.SubmissionUser(
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
            team = (teamId ?: survey.teamId)?.let { id ->
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
            deviceName = org.ole.planet.myplanet.lite.util.DeviceUtils.getDeviceName(),
            customDeviceName = resolveCustomDeviceName(),
        )
    }

    private fun parseProfileRawDocument(rawDocument: String?): JSONObject? {
        if (rawDocument.isNullOrBlank()) return null
        return runCatching { JSONObject(rawDocument) }.getOrNull()
    }

    private fun JSONObject?.optIntOrNull(key: String): Int? {
        this ?: return null
        if (!has(key)) return null
        return when (val value = opt(key)) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull()
            else -> null
        }
    }

    private suspend fun processSubmission(
        base: String,
        submission: SurveySubmission,
        survey: SurveyDocument,
        username: String
    ) {
        val isOnline = NetworkUtils.isDeviceOnline(requireContext())
        if (!isOnline) {
            setSubmitting(false)
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
            Toast.makeText(
                requireContext(),
                getString(
                    if (isExam) {
                        R.string.dashboard_exam_completed
                    } else {
                        R.string.dashboard_survey_wizard_completed
                    }
                ),
                Toast.LENGTH_SHORT
            ).show()
            finishWithResult()
        } else {
            queueSubmissionForOutbox(submission, survey)
        }
    }

    private fun queueSubmissionForOutbox(submission: SurveySubmission, survey: SurveyDocument) {
        viewLifecycleOwner.lifecycleScope.launch {
            val saved = localSurveyRepository.saveSubmission(
                submission = submission,
                surveyId = survey.id,
                surveyName = survey.name,
                teamId = teamId,
                teamName = teamName,
            )
            if (saved) {
                Toast.makeText(
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

    private suspend fun flushPendingSurveySubmissions() {
        localSurveyRepository.flushPendingSurveyOutbox()
    }

    private fun buildSubmissionParentId(survey: SurveyDocument): String? {
        val surveyId = survey.id?.takeIf { it.isNotBlank() } ?: return null
        val course = courseId?.takeIf { it.isNotBlank() }
        return if (course == null) {
            surveyId
        } else {
            "$surveyId@$course"
        }
    }

    private fun resolveExamStatus(survey: SurveyDocument): String {
        val requiresGrading = survey.questions.orEmpty().any { question ->
            question.type.equals("input", ignoreCase = true) ||
                question.type.equals("textarea", ignoreCase = true)
        }
        return if (requiresGrading) "requires grading" else "complete"
    }

    private fun isExamPassing(): Boolean {
        val survey = document ?: return true
        val questions = survey.questions.orEmpty()
        if (questions.isEmpty()) return true
        val passingPercentage = survey.passingPercentage ?: DEFAULT_EXAM_PASSING_PERCENTAGE
        val totalMarks = questions.sumOf { it.marks ?: 1 }
        if (totalMarks == 0) return true
        val earned = questions.mapIndexed { index, question ->
            if (isAnswerCorrect(question, answers[index])) {
                question.marks ?: 1
            } else {
                0
            }
        }.sum()
        val percentage = (earned * 100.0 / totalMarks).roundToInt()
        return percentage >= passingPercentage
    }

    private fun isAnswerCorrect(question: SurveyQuestion, answer: SurveyAnswer?): Boolean {
        val correctChoice = question.correctChoice
        val normalizedCorrect = normalizeCorrectChoice(correctChoice)
        val choiceIds = question.choices.orEmpty()
            .mapNotNull { it.id?.trim() }
            .filter { it.isNotBlank() }
            .toSet()
        val choiceTextToId = question.choices.orEmpty()
            .mapNotNull { choice ->
                val text = choice.text?.trim().orEmpty()
                val id = choice.id?.trim().orEmpty()
                if (text.isNotBlank() && id.isNotBlank()) text to id else null
            }
            .toMap()
        val correctIds = normalizedCorrect
            .map { it.trim() }
            .filter { it.isNotBlank() && choiceIds.contains(it) }
        val correctTexts = if (correctIds.isEmpty()) {
            normalizedCorrect.map { it.trim() }.filter { it.isNotBlank() }
        } else {
            emptyList()
        }
        return when (question.type) {
            "input", "textarea" -> {
                val correctText = normalizedCorrect.firstOrNull()?.trim().orEmpty()
                val response = (answer as? SurveyAnswer.Text)?.value?.trim().orEmpty()
                if (correctText.isBlank()) {
                    response.isNotBlank()
                } else {
                    response.equals(correctText, ignoreCase = true)
                }
            }
            "select" -> {
                val selectedChoice = (answer as? SurveyAnswer.SingleChoice)?.choice
                if (correctIds.isNotEmpty()) {
                    val selectedId = normalizeSelectedId(selectedChoice?.id)
                    val resolvedId = selectedId?.takeIf { it.isNotBlank() }
                        ?: selectedChoice?.text?.trim()?.let { choiceTextToId[it] }
                    resolvedId != null && correctIds.contains(resolvedId)
                } else {
                    val selectedText = selectedChoice?.text?.trim()
                    selectedText != null && correctTexts.any { it.equals(selectedText, ignoreCase = true) }
                }
            }
            "selectMultiple" -> {
                val selectedIds = (answer as? SurveyAnswer.MultipleChoice)?.choices
                    ?.filter { !it.isOther }
                    ?.mapNotNull { normalizeSelectedId(it.id) }
                    ?.filter { it.isNotBlank() }
                    .orEmpty()
                selectedIds.size == correctIds.size && selectedIds.toSet() == correctIds.toSet()
            }
            "ratingScale" -> {
                val correctValue = normalizedCorrect.firstOrNull()?.trim().orEmpty()
                val selectedScore = (answer as? SurveyAnswer.Rating)?.score?.toString()
                correctValue.isNotBlank() && selectedScore == correctValue
            }
            else -> false
        }
    }

    private fun normalizeCorrectChoice(correctChoice: Any?): List<String> {
        return when (correctChoice) {
            is String -> listOf(correctChoice)
            is List<*> -> correctChoice.mapNotNull { item ->
                when (item) {
                    is Map<*, *> -> item["id"]?.toString()
                        ?: item["_id"]?.toString()
                        ?: item["text"]?.toString()
                    else -> item?.toString()
                }
            }
            null -> emptyList()
            else -> listOf(correctChoice.toString())
        }
    }

    private fun normalizeSelectedId(rawId: String?): String? {
        val trimmed = rawId?.trim().orEmpty()
        if (trimmed.isBlank()) return null
        return trimmed.substringBefore("/").trim().takeIf { it.isNotBlank() }
    }

    private fun resolveCustomDeviceName(): String? {
        val prefs = SecurePreferencesProvider.getServerPreferences(requireContext().applicationContext)
        return prefs.getString(KEY_DEVICE_CUSTOM_DEVICE_NAME, null)?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun finishWithResult() {
        if (isExam) {
            requireActivity().setResult(Activity.RESULT_OK)
        }
        requireActivity().finish()
    }



    private fun setSubmitting(submitting: Boolean) {
        isSubmitting = submitting
        updateNavigationEnabled()
    }

    private fun createGenderGroup(context: android.content.Context): Pair<TextView, RadioGroup> {
        val genderLabel = TextView(context).apply {
            text = getString(R.string.dashboard_survey_wizard_gender_label)
        }
        val genderGroup = RadioGroup(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        val maleButton = RadioButton(context).apply {
            text = getString(R.string.signup_gender_option_male)
            tag = GENDER_MALE
            id = View.generateViewId()
        }
        val femaleButton = RadioButton(context).apply {
            text = getString(R.string.signup_gender_option_female)
            tag = GENDER_FEMALE
            id = View.generateViewId()
        }
        genderGroup.addView(maleButton)
        genderGroup.addView(femaleButton)
        when (respondent.gender) {
            GENDER_MALE -> maleButton.isChecked = true
            GENDER_FEMALE -> femaleButton.isChecked = true
        }
        return genderLabel to genderGroup
    }

    private fun createBirthYearLayout(context: android.content.Context): Pair<TextInputLayout, TextInputEditText> {
        val birthYearLayout = TextInputLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        birthYearLayout.hint = getString(R.string.dashboard_survey_wizard_birth_year_label)
        val birthYearInput = TextInputEditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(respondent.birthYear?.toString().orEmpty())
            setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    scrollFocusedFieldIntoView(view)
                } else {
                    updateRespondentBirthYear(text?.toString())
                }
            }
            setOnClickListener { scrollFocusedFieldIntoView(this) }
            imeOptions = EditorInfo.IME_ACTION_DONE
            setOnEditorActionListener { _, actionId, event ->
                val isDoneAction = actionId == EditorInfo.IME_ACTION_DONE ||
                    event?.keyCode == KeyEvent.KEYCODE_ENTER
                if (isDoneAction) {
                    updateRespondentBirthYear(text?.toString())
                }
                false
            }
        }
        birthYearLayout.addView(birthYearInput)
        return birthYearLayout to birthYearInput
    }

    private fun scrollFocusedFieldIntoView(view: View) {
        questionScrollView.post {
            questionScrollView.postDelayed({
                val scrollChild = questionScrollView.getChildAt(0) as? ViewGroup ?: return@postDelayed
                val targetRect = Rect().also { view.getDrawingRect(it) }
                scrollChild.offsetDescendantRectToMyCoords(view, targetRect)
                targetRect.bottom += questionScrollView.height / 3
                questionScrollView.requestChildRectangleOnScreen(scrollChild, targetRect, true)
            }, 180)
        }
    }

    private fun updateRespondentBirthYear(value: String?) {
        val year = value?.trim().orEmpty().toIntOrNull()
        respondent.birthYear = year
        respondent.age = year?.let { Calendar.getInstance().get(Calendar.YEAR) - it }
    }

    private fun createAdditionalCheckBox(context: android.content.Context): CheckBox {
        return CheckBox(context).apply {
            text = getString(R.string.dashboard_survey_wizard_additional_info_label)
            isChecked = respondent.additionalInfo
        }
    }

    private fun renderBasicsStep(): Pair<View, () -> Boolean> {
        val context = requireContext()
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }

        val (genderLabel, genderGroup) = createGenderGroup(context)
        val (birthYearLayout, birthYearInput) = createBirthYearLayout(context)
        val additionalCheckBox = createAdditionalCheckBox(context)

        container.addView(genderLabel)
        container.addView(genderGroup)
        container.addView(birthYearLayout)
        container.addView(additionalCheckBox)

        val collector = {
            val checkedId = genderGroup.checkedRadioButtonId
            val yearText = birthYearInput.text?.toString()?.trim().orEmpty()
            val year = yearText.toIntOrNull()
            if (yearText.isNotEmpty() && year == null) {
                showValidationMessage(R.string.dashboard_survey_wizard_birth_year_required)
                false
            } else {
                val nowYear = Calendar.getInstance().get(Calendar.YEAR)
                respondent.gender = genderGroup.findViewById<RadioButton>(checkedId)?.tag as? String
                respondent.birthYear = year
                respondent.age = year?.let { nowYear - it }
                respondent.additionalInfo = additionalCheckBox.isChecked
                if (includeOptionalDetails != respondent.additionalInfo) {
                    includeOptionalDetails = respondent.additionalInfo
                    steps = buildSteps(includeOptionalDetails)
                }
                true
            }
        }
        return container to collector
    }

    private fun renderNamesStep(): Pair<View, () -> Boolean> {
        val context = requireContext()
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }

        val firstLayout = TextInputLayout(context).apply {
            hint = getString(R.string.dashboard_survey_wizard_first_name_label)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        val firstInput = TextInputEditText(context)
        firstInput.setText(respondent.firstName.orEmpty())
        firstLayout.addView(firstInput)

        val middleLayout = TextInputLayout(context).apply {
            hint = getString(R.string.dashboard_survey_wizard_middle_name_label)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        val middleInput = TextInputEditText(context)
        middleInput.setText(respondent.middleName.orEmpty())
        middleLayout.addView(middleInput)

        val lastLayout = TextInputLayout(context).apply {
            hint = getString(R.string.dashboard_survey_wizard_last_name_label)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        val lastInput = TextInputEditText(context)
        lastInput.setText(respondent.lastName.orEmpty())
        lastLayout.addView(lastInput)

        container.addView(firstLayout)
        container.addView(middleLayout)
        container.addView(lastLayout)

        val collector = {
            respondent.firstName = firstInput.text?.toString()?.trim().orEmpty().takeIf { it.isNotBlank() }
            respondent.middleName = middleInput.text?.toString()?.trim().orEmpty().takeIf { it.isNotBlank() }
            respondent.lastName = lastInput.text?.toString()?.trim().orEmpty().takeIf { it.isNotBlank() }
            true
        }
        return container to collector
    }

    private fun renderBirthDateStep(): Pair<View, () -> Boolean> {
        val context = requireContext()
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }

        val birthDateLayout = TextInputLayout(context).apply {
            hint = getString(R.string.dashboard_survey_wizard_birth_date_label)
            layoutParams = LinearLayout.LayoutParams(
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
            setText(respondent.birthDate?.let { formatBirthDateDisplay(it) }
                ?: birthDateSelection?.let { formatBirthDateIso(it) }.orEmpty())
            setOnClickListener { showBirthDatePicker(this) }
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    showBirthDatePicker(this)
                }
            }
        }
        birthDateLayout.addView(birthDateInput)
        container.addView(birthDateLayout)

        val collector = {
            respondent.birthDate = birthDateSelection?.let { formatBirthDateIso(it) }
            true
        }
        return container to collector
    }

    private fun showBirthDatePicker(input: TextInputEditText) {
        if (childFragmentManager.findFragmentByTag(BIRTH_DATE_PICKER_TAG) != null) {
            return
        }

        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(getString(R.string.signup_birth_date_picker_title))
            .apply {
                birthDateSelection?.let { setSelection(it) }
            }
            .build()

        picker.addOnPositiveButtonClickListener { selection ->
            birthDateSelection = selection
            input.setText(formatBirthDateIso(selection))
        }

        picker.addOnDismissListener {
            input.clearFocus()
        }

        picker.show(childFragmentManager, BIRTH_DATE_PICKER_TAG)
    }

    private fun parseBirthDateIso(value: String?): Long? {
        if (value.isNullOrBlank()) {
            return null
        }
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return try {
            formatter.parse(value)?.time
        } catch (_: ParseException) {
            null
        }
    }

    private fun formatBirthDateIso(selection: Long): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return formatter.format(Date(selection))
    }

    private fun formatBirthDateDisplay(value: String): String {
        val parsed = parseBirthDateIso(value)
        return parsed?.let { formatBirthDateIso(it) } ?: value
    }

    private fun renderContactStep(): Pair<View, () -> Boolean> {
        val context = requireContext()
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }

        val emailLayout = TextInputLayout(context).apply {
            hint = getString(R.string.dashboard_survey_wizard_email_label)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        val emailInput = TextInputEditText(context).apply {
            inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            setText(respondent.email.orEmpty())
        }
        emailLayout.addView(emailInput)

        val phoneLayout = TextInputLayout(context).apply {
            hint = getString(R.string.dashboard_survey_wizard_phone_label)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        val phoneInput = TextInputEditText(context).apply {
            inputType = InputType.TYPE_CLASS_PHONE
            setText(respondent.phoneNumber.orEmpty())
        }
        phoneLayout.addView(phoneInput)

        container.addView(emailLayout)
        container.addView(phoneLayout)

        val collector = {
            respondent.email = emailInput.text?.toString()?.trim().orEmpty().takeIf { it.isNotBlank() }
            respondent.phoneNumber = phoneInput.text?.toString()?.trim().orEmpty().takeIf { it.isNotBlank() }
            true
        }
        return container to collector
    }

    private fun createDropdownLayout(context: android.content.Context, hintText: String): Pair<TextInputLayout, AutoCompleteTextView> {
        val layout = TextInputLayout(context).apply {
            hint = hintText
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        val input = AutoCompleteTextView(context).apply {
            inputType = InputType.TYPE_NULL
            keyListener = null
            setOnClickListener { showDropDown() }
            setOnFocusChangeListener { _, hasFocus -> if (hasFocus) showDropDown() }
        }
        layout.addView(input)
        return layout to input
    }

    private fun levelArrayForLanguage(languageLabel: String?): Int {
        val normalized = languageLabel?.trim()?.lowercase(Locale.ROOT)
        return when (normalized) {
            getString(R.string.language_name_spanish).lowercase(Locale.ROOT) -> R.array.signup_level_options_language_es
            getString(R.string.language_name_french).lowercase(Locale.ROOT) -> R.array.signup_level_options_language_fr
            getString(R.string.language_name_portuguese).lowercase(Locale.ROOT) -> R.array.signup_level_options_language_pt
            getString(R.string.language_name_arabic).lowercase(Locale.ROOT) -> R.array.signup_level_options_language_ar
            getString(R.string.language_name_somali).lowercase(Locale.ROOT) -> R.array.signup_level_options_language_so
            getString(R.string.language_name_nepali).lowercase(Locale.ROOT) -> R.array.signup_level_options_language_ne
            getString(R.string.language_name_hindi).lowercase(Locale.ROOT) -> R.array.signup_level_options_language_hi
            else -> R.array.signup_level_options_language_en
        }
    }

    private fun renderLanguageLevelStep(): Pair<View, () -> Boolean> {
        val context = requireContext()
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }

        val (languageLayout, languageInput) = createDropdownLayout(
            context,
            getString(R.string.dashboard_survey_wizard_language_label)
        )
        val (levelLayout, levelInput) = createDropdownLayout(
            context,
            getString(R.string.dashboard_survey_wizard_level_label)
        )

        val languages = resources.getStringArray(R.array.signup_language_options).toList()
        val languageAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, languages)
        languageInput.setAdapter(languageAdapter)
        respondent.language?.let { languageInput.setText(it, false) }

        var currentLevelOptions: List<String> = emptyList()

        fun updateLevelOptions(languageLabel: String?) {
            val arrayRes = levelArrayForLanguage(languageLabel)
            val options = resources.getStringArray(arrayRes).toList()
            currentLevelOptions = options
            val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, options)
            levelInput.setAdapter(adapter)
            val localizedLevel = LearningLevelTranslator.toLocalized(context, respondent.level, arrayRes)
            if (!localizedLevel.isNullOrBlank() && options.contains(localizedLevel)) {
                levelInput.setText(localizedLevel, false)
            } else {
                levelInput.setText("", false)
            }
        }

        languageInput.setOnItemClickListener { _, _, position, _ ->
            val selected = languageAdapter.getItem(position)
            updateLevelOptions(selected)
        }

        updateLevelOptions(respondent.language)

        container.addView(languageLayout)
        container.addView(levelLayout)

        val collector = {
            val selectedLanguage = languageInput.text?.toString()?.trim().orEmpty()
            respondent.language = selectedLanguage.takeIf { languages.contains(it) }

            val levelText = levelInput.text?.toString()?.trim().orEmpty()
            respondent.level = if (currentLevelOptions.contains(levelText)) {
                LearningLevelTranslator.toEnglish(context, levelText)
            } else {
                null
            }
            true
        }
        return container to collector
    }

    private fun renderTextInputQuestion(index: Int, multiline: Boolean): Pair<View, () -> Boolean> {
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

    private fun renderSingleChoiceQuestion(
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

    private fun buildSingleChoiceOptions(
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

    private fun restoreSingleChoiceSelection(
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

    private fun collectSingleChoiceAnswer(
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

    private fun renderMultiChoiceQuestion(
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

    private fun buildMultiChoiceCheckboxes(
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

    private fun buildMultiChoiceOtherOption(
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

    private fun restoreMultiChoiceSelections(
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

    private fun collectMultiChoiceAnswer(
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

    private fun renderRatingQuestion(index: Int): Pair<View, () -> Boolean> {
        val context = requireContext()
        val gridLayout = GridLayout(context).apply {
            rowCount = 3
            columnCount = 3
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }

        val basePalette = listOf(
            Color.parseColor("#F3C4E3"),
            Color.parseColor("#F6CEDE"),
            Color.parseColor("#F8D7DA"),
            Color.parseColor("#FAE0D6"),
            Color.parseColor("#FDEAD1"),
            Color.parseColor("#FFF3CD"),
            Color.parseColor("#F1F1D1"),
            Color.parseColor("#E2EFD6"),
            Color.parseColor("#D4EDDA"),
        )
        val selectedColor = ContextCompat.getColor(context, R.color.survey_rating_selected_background)
        val selectedTextColor = ContextCompat.getColor(context, R.color.survey_rating_selected_text)
        val defaultTextColor = ContextCompat.getColor(context, R.color.survey_rating_default_text)
        val horizontalMargin = resources.getDimensionPixelSize(R.dimen.padding_small)
        val bottomMargin = resources.getDimensionPixelSize(R.dimen.padding_small)
        val buttons = mutableListOf<MaterialButton>()
        var selectedValue: Int? = (answers[index] as? SurveyAnswer.Rating)?.score

        fun applySelection() {
            buttons.forEachIndexed { idx, button ->
                val value = idx + 1
                val isSelected = selectedValue == value
                val backgroundColor = if (isSelected) {
                    selectedColor
                } else {
                    basePalette[value - 1]
                }
                button.backgroundTintList = ColorStateList.valueOf(backgroundColor)
                button.setTextColor(if (isSelected) selectedTextColor else defaultTextColor)
                button.strokeWidth = 0
            }
        }

        (1..9).forEach { value ->
            val button = createRatingButton(context, value, horizontalMargin, bottomMargin) { selected ->
                selectedValue = selected
                applySelection()
            }
            buttons.add(button)
            gridLayout.addView(button)
        }

        applySelection()

        val collector = {
            val score = selectedValue
            if (score == null) {
                showValidationMessage(R.string.dashboard_survey_wizard_rating_required)
                false
            } else {
                answers[index] = SurveyAnswer.Rating(score)
                true
            }
        }
        return gridLayout to collector
    }

    private fun createRatingButton(
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

    private fun buildOtherInputField(context: android.content.Context): TextInputLayout {
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

    private fun showValidationMessage(@StringRes messageRes: Int) {
        Toast.makeText(requireContext(), getString(messageRes), Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val ARG_DOCUMENT = "arg_document"
        private const val ARG_TEAM_ID = "arg_team_id"
        private const val ARG_TEAM_NAME = "arg_team_name"
        private const val ARG_COURSE_ID = "arg_course_id"
        private const val ARG_IS_EXAM = "arg_is_exam"
        private const val OTHER_CHOICE_TAG = "other_choice"
        private const val BIRTH_DATE_PICKER_TAG = "survey_birth_date_picker"
        private const val DEFAULT_EXAM_PASSING_PERCENTAGE = 100
        private const val KEY_DEVICE_CUSTOM_DEVICE_NAME = "device_custom_device_name"

        fun newInstance(
            document: SurveyDocument,
            teamId: String?,
            teamName: String?,
            courseId: String?,
            isExam: Boolean = false,
        ): SurveyWizardFragment {
            return SurveyWizardFragment().apply {
                arguments = Bundle().apply {
                    putSerializable(ARG_DOCUMENT, document)
                    putString(ARG_TEAM_ID, teamId)
                    putString(ARG_TEAM_NAME, teamName)
                    putString(ARG_COURSE_ID, courseId)
                    putBoolean(ARG_IS_EXAM, isExam)
                }
            }
        }
    }
}

private sealed class WizardStep : java.io.Serializable {
    object Basics : WizardStep()
    object Names : WizardStep()
    object BirthDate : WizardStep()
    object Contact : WizardStep()
    object LanguageLevel : WizardStep()
    data class Question(val question: SurveyQuestion, val questionIndex: Int) : WizardStep()
}

private data class SurveyRespondent(
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
)

private sealed class SurveyAnswer : java.io.Serializable {
    data class Text(val value: String) : SurveyAnswer()
    data class SingleChoice(val choice: SelectedOption?) : SurveyAnswer()
    data class MultipleChoice(val choices: List<SelectedOption>) : SurveyAnswer()
    data class Rating(val score: Int) : SurveyAnswer()
}

private data class SelectedOption(
    val id: String?,
    val text: String,
    val isOther: Boolean = false,
) : java.io.Serializable {
    fun toSubmissionValue(): SubmissionOptionValue {
        return SubmissionOptionValue(
            id = id,
            text = text,
            isOther = isOther,
        )
    }
}

private data class SubmissionOptionValue(
    @param:Json(name = "id") val id: String?,
    @param:Json(name = "text") val text: String?,
    @param:Json(name = "isOther") val isOther: Boolean = false,
)
