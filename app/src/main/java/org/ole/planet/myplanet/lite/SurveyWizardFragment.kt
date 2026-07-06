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
import io.noties.markwon.Markwon
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

class SurveyWizardFragment : Fragment(R.layout.fragment_survey_wizard) {

    internal var document: SurveyDocument? = null
    internal var questions: List<SurveyQuestion> = emptyList()
    internal var teamId: String? = null
    internal var teamName: String? = null
    internal var courseId: String? = null
    internal var baseUrlOverride: String? = null
    internal var includeUserContext: Boolean = true
    internal var steps: List<WizardStep> = emptyList()
    internal var currentIndex = 0
    internal val answers: MutableMap<Int, SurveyAnswer> = mutableMapOf()
    internal var activeCollector: (() -> Boolean)? = null
    internal val respondent = SurveyRespondent()
    internal var birthDateSelection: Long? = null
    internal var includeOptionalDetails = false
    internal val submissionRepository = DashboardSurveySubmissionsRepository()
    internal var baseUrl: String? = null
    internal var credentials: StoredCredentials? = null
    internal var sessionCookie: String? = null
    internal var serverCode: String? = null
    internal var parentCode: String? = null
    internal var isExam: Boolean = false
    internal var startTimeMillis: Long = System.currentTimeMillis()
    internal var isSubmitting = false
    internal lateinit var translationManager: SurveyTranslationManager
    internal var questionTranslations: Map<Int, TranslatedQuestion> = emptyMap()
    internal var targetSurveyLanguage: String? = null
    internal var detectedSurveyLanguage: String? = null
    internal var translatedTitle: String? = null
    internal var translatedDescription: String? = null
    internal val localSurveyRepository by lazy { DashboardLocalSurveyRepository(requireContext()) }

    internal lateinit var markwon: Markwon
    internal lateinit var titleView: TextView
    internal lateinit var descriptionView: TextView
    internal lateinit var counterView: TextView
    internal lateinit var progressBar: ProgressBar
    internal lateinit var translationOverlay: View
    internal lateinit var translationProgressBar: ProgressBar
    internal lateinit var translationNoticeView: TextView
    internal var translationApplied: Boolean = false
    internal lateinit var questionBodyView: TextView
    internal lateinit var questionScrollView: ScrollView
    internal lateinit var questionContainer: LinearLayout
    internal lateinit var previousButton: Button
    internal lateinit var nextButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        document = arguments?.let { args ->
            BundleCompat.getSerializable(args, ARG_DOCUMENT, SurveyDocument::class.java)
        }
        teamId = arguments?.getString(ARG_TEAM_ID)
        teamName = arguments?.getString(ARG_TEAM_NAME)
        courseId = arguments?.getString(ARG_COURSE_ID)
        isExam = arguments?.getBoolean(ARG_IS_EXAM) == true
        baseUrlOverride = arguments?.getString(ARG_BASE_URL)?.trim()?.trimEnd('/')?.takeIf { it.isNotBlank() }
        includeUserContext = arguments?.getBoolean(ARG_INCLUDE_USER_CONTEXT, true) != false
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
        markwon = Markwon.builder(requireContext()).build()

        translationNoticeView.setOnClickListener {
            startActivity(Intent(requireContext(), PrivacyPolicyActivity::class.java))
        }

        lifecycleScope.launch {
            initializeSession()
            attemptSurveyTranslation()
            if (baseUrlOverride == null) {
                flushPendingSurveySubmissions()
            }
        }

        val survey = document
        if (survey == null || questions.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.dashboard_survey_wizard_empty_questions), Toast.LENGTH_SHORT).show()
            parentFragmentManager.popBackStack()
            return
        }

        setSurveyTitle(survey.name.orEmpty())
        val description = survey.description?.takeIf { it.isNotBlank() }
        setSurveyDescription(description.orEmpty())
        descriptionView.isVisible = !description.isNullOrBlank()

        progressBar.max = steps.size

        showStep(currentIndex)

        setupNavigationButtons()
    }

    override fun onStart() {
        super.onStart()
        viewLifecycleOwner.lifecycleScope.launch {
            if (baseUrlOverride == null) {
                flushPendingSurveySubmissions()
            }
        }
    }

    fun showStep(index: Int) {
        val step = steps[index]
        progressBar.max = steps.size
        counterView.text = getString(R.string.dashboard_survey_wizard_step_counter, index + 1, steps.size)
        updateDescriptionVisibility(index)
        val questionBody = when (step) {
            WizardStep.Basics -> getString(R.string.dashboard_survey_wizard_participant_basics_title)
            WizardStep.Names -> getString(R.string.dashboard_survey_wizard_names_title)
            WizardStep.BirthDate -> getString(R.string.dashboard_survey_wizard_birthdate_title)
            WizardStep.Contact -> getString(R.string.dashboard_survey_wizard_contact_title)
            WizardStep.LanguageLevel -> getString(R.string.dashboard_survey_wizard_language_level_title)
            is WizardStep.Question -> questionTranslations[step.questionIndex]?.body
                ?: step.question.body.orEmpty()
        }
        setQuestionBody(questionBody)
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

    companion object {
        private const val ARG_DOCUMENT = "arg_document"
        private const val ARG_TEAM_ID = "arg_team_id"
        private const val ARG_TEAM_NAME = "arg_team_name"
        private const val ARG_COURSE_ID = "arg_course_id"
        private const val ARG_IS_EXAM = "arg_is_exam"
        private const val ARG_BASE_URL = "arg_base_url"
        private const val ARG_INCLUDE_USER_CONTEXT = "arg_include_user_context"

        fun newInstance(
            document: SurveyDocument,
            teamId: String?,
            teamName: String?,
            courseId: String?,
            isExam: Boolean = false,
            baseUrl: String? = null,
            includeUserContext: Boolean = true,
        ): SurveyWizardFragment {
            return SurveyWizardFragment().apply {
                arguments = Bundle().apply {
                    putSerializable(ARG_DOCUMENT, document)
                    putString(ARG_TEAM_ID, teamId)
                    putString(ARG_TEAM_NAME, teamName)
                    putString(ARG_COURSE_ID, courseId)
                    putBoolean(ARG_IS_EXAM, isExam)
                    putString(ARG_BASE_URL, baseUrl)
                    putBoolean(ARG_INCLUDE_USER_CONTEXT, includeUserContext)
                }
            }
        }
    }
}
