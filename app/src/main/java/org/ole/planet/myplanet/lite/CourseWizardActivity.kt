/*
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2026-01-04
 */
package org.ole.planet.myplanet.lite

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.IntentCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.exoplayer.ExoPlayer
import com.google.android.material.snackbar.Snackbar
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.glide.GlideImagesPlugin
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.lite.dashboard.DashboardCoursesRepository
import org.ole.planet.myplanet.lite.dashboard.DashboardServerPreferences
import org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore
import org.ole.planet.myplanet.lite.profile.StoredCredentials
import org.ole.planet.myplanet.lite.survey.DashboardLocalSurveyRepository
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class CourseWizardActivity : BaseActivity() {
    val pendingProgressPrefs by lazy {
        SecurePreferencesProvider.getEncryptedPreferences(
            context = applicationContext,
            prefsName = PREF_ENCRYPTED_PENDING_COURSE_PROGRESS,
            legacyPrefsName = PREF_LEGACY_PENDING_COURSE_PROGRESS,
        )
    }

    private lateinit var markwon: Markwon
    var steps: List<StepDisplay> = emptyList()
    var baseUrl: String? = null
    var currentIndex: Int = 0
    var courseId: String? = null
    var courseTitle: String = ""
    var credentials: StoredCredentials? = null
    private lateinit var stepPositionView: TextView
    private lateinit var stepTitleView: TextView
    private lateinit var descriptionView: TextView
    private lateinit var attachmentsContainer: LinearLayout
    private lateinit var attachmentsList: LinearLayout
    private lateinit var attachmentsTitle: TextView
    private lateinit var previousButton: View
    private lateinit var nextButton: View
    val playlistIndexByResourceId = mutableMapOf<String, Int>()
    val currentPlaylistUrls = mutableListOf<String>()
    var lastPlaybackIndex = 0
    var lastPlaybackPositionMs = 0L
    val coursesRepository = DashboardCoursesRepository()
    val localSurveyRepository by lazy { DashboardLocalSurveyRepository(applicationContext) }
    var hasAutoCompletedFirstStep = false
    val audioPlayers = mutableListOf<ExoPlayer>()
    private val completedRequiredSteps = mutableSetOf<Int>()
    var pendingRequiredStepIndex: Int? = null
    var cachedProgressDocument: DashboardCoursesRepository.CourseProgressDocument? = null

    val fullscreenLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data ?: return@registerForActivityResult
            lastPlaybackIndex = data.getIntExtra(FullscreenPlayerActivity.EXTRA_RESULT_INDEX, 0)
            lastPlaybackPositionMs =
                data.getLongExtra(
                    FullscreenPlayerActivity.EXTRA_RESULT_POSITION,
                    0L,
                )
        }

    val requiredStepLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                pendingRequiredStepIndex?.let { completedRequiredSteps.add(it) }
            }
            pendingRequiredStepIndex = null
            if (this::stepPositionView.isInitialized) {
                bindStep()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyDeviceOrientationLock()
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_course_wizard)
        markwon =
            Markwon
                .builder(this)
                .usePlugin(TablePlugin.create(this))
                .usePlugin(HtmlPlugin.create())
                .usePlugin(GlideImagesPlugin.create(this))
                .build()
        val (courseTitle, startIndex) = parseIntentData(savedInstanceState)
        if (steps.isEmpty()) {
            finish()
            return
        }
        setupViews(courseTitle)
        lifecycleScope.launch {
            flushPendingExamSubmissions()
            flushPendingCourseProgress()
            currentIndex = resolveInitialStepIndex(startIndex)
            bindStep()
            maybeAutoCompleteFirstStep()
        }
    }

    private fun parseIntentData(savedInstanceState: Bundle?): Pair<String, Int> {
        val courseTitle = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        this.courseTitle = courseTitle
        courseId = intent.getStringExtra(EXTRA_COURSE_ID)
        val startIndex = intent.getIntExtra(EXTRA_START_STEP, 0)
        baseUrl = DashboardServerPreferences.getServerBaseUrl(applicationContext)
        credentials = ProfileCredentialsStore.getStoredCredentials(applicationContext)
        savedInstanceState?.getIntegerArrayList(EXTRA_COMPLETED_REQUIRED_STEPS)?.let { restored ->
            completedRequiredSteps.clear()
            completedRequiredSteps.addAll(restored)
        }
        val rawSteps =
            IntentCompat.getSerializableExtra(
                intent,
                EXTRA_STEPS,
                ArrayList::class.java,
            )
        val stepPayload = rawSteps?.filterIsInstance<CourseItem.LessonStep>()
        steps =
            stepPayload
                ?.map { step ->
                    StepDisplay(
                        title = step.title,
                        description = step.description,
                        resources = step.resources,
                        survey = step.survey,
                        exam = step.exam,
                    )
                }.orEmpty()
        return Pair(courseTitle, startIndex)
    }

    private fun setupViews(courseTitle: String) {
        val root: View = findViewById(R.id.courseWizardRoot)
        WindowInsetsControllerCompat(window, root).isAppearanceLightStatusBars = true
        val titleView: TextView = findViewById(R.id.courseWizardTitle)
        stepPositionView = findViewById(R.id.courseWizardStepPosition)
        stepTitleView = findViewById(R.id.courseWizardStepTitle)
        descriptionView = findViewById(R.id.courseWizardDescription)
        attachmentsContainer = findViewById(R.id.courseWizardAttachments)
        attachmentsList = findViewById(R.id.courseWizardAttachmentsList)
        attachmentsTitle = findViewById(R.id.courseWizardAttachmentsTitle)
        previousButton = findViewById(R.id.courseWizardPrevious)
        nextButton = findViewById(R.id.courseWizardNext)
        val initialPaddingLeft = root.paddingLeft
        val initialPaddingTop = root.paddingTop
        val initialPaddingRight = root.paddingRight
        val initialPaddingBottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                initialPaddingLeft + systemBars.left,
                initialPaddingTop + systemBars.top,
                initialPaddingRight + systemBars.right,
                initialPaddingBottom + systemBars.bottom,
            )
            WindowInsetsCompat.CONSUMED
        }
        titleView.text = courseTitle
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseAudioPlayers()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putIntegerArrayList(
            EXTRA_COMPLETED_REQUIRED_STEPS,
            ArrayList(completedRequiredSteps),
        )
    }

    fun bindStep() {
        val step = steps[currentIndex]
        stepPositionView.text =
            getString(
                R.string.course_wizard_step_position,
                currentIndex + 1,
                steps.size,
            )
        stepTitleView.text = step.title
        markwon.setMarkdown(
            descriptionView,
            resolveOfflineMarkdownImages(step.description).replace("\n", "  \n"),
        )
        bindAttachments(
            step.resources,
            step.survey,
            step.exam,
            attachmentsContainer,
            attachmentsTitle,
            attachmentsList,
        )
        previousButton.isEnabled = currentIndex > 0
        previousButton.setOnClickListener {
            if (currentIndex > 0) {
                currentIndex -= 1
                bindStep()
            }
        }
        val requiredStepPending = isRequiredStepPending(step)
        if (currentIndex >= steps.lastIndex) {
            nextButton.isEnabled = true
            (nextButton as? TextView)?.text = getString(R.string.course_wizard_finish)
            nextButton.setOnClickListener {
                if (requiredStepPending) {
                    showCompleteRequiredStepSnackbar()
                } else {
                    nextButton.isEnabled = false
                    lifecycleScope.launch {
                        finishCourse()
                    }
                }
            }
        } else {
            nextButton.isEnabled = true
            (nextButton as? TextView)?.text = getString(R.string.course_wizard_next)
            nextButton.setOnClickListener {
                if (requiredStepPending) {
                    showCompleteRequiredStepSnackbar()
                } else if (currentIndex < steps.lastIndex) {
                    lifecycleScope.launch {
                        advanceToNextStep()
                    }
                }
            }
        }
    }

    private fun isRequiredStepPending(step: StepDisplay): Boolean {
        val hasSurvey = step.survey?.questions?.isNotEmpty() == true
        val hasExam = step.exam?.questions?.isNotEmpty() == true
        return (hasSurvey || hasExam) && !completedRequiredSteps.contains(currentIndex)
    }

    private fun showCompleteRequiredStepSnackbar() {
        Snackbar
            .make(
                findViewById(R.id.courseWizardRoot),
                getString(R.string.course_wizard_complete_required_step),
                Snackbar.LENGTH_SHORT,
            ).show()
    }

    companion object {
        private const val PREF_LEGACY_PENDING_COURSE_PROGRESS = "pref_pending_course_progress"
        private const val PREF_ENCRYPTED_PENDING_COURSE_PROGRESS = "encrypted_pending_course_progress"
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_COURSE_ID = "extra_course_id"
        private const val EXTRA_STEPS = "extra_steps"
        private const val EXTRA_START_STEP = "extra_start_step"
        private const val EXTRA_COMPLETED_REQUIRED_STEPS = "extra_completed_required_steps"
        const val EXTRA_RESULT_COURSE_ID = "extra_result_course_id"
        const val EXTRA_RESULT_PROGRESS_PERCENT = "extra_result_progress_percent"
        const val EXTRA_RESULT_CURRENT_STEP = "extra_result_current_step"

        fun createIntent(
            context: Context,
            courseId: String,
            courseTitle: String,
            steps: List<CourseItem.LessonStep>,
            startStep: Int,
        ): Intent =
            Intent(context, CourseWizardActivity::class.java).apply {
                putExtra(EXTRA_COURSE_ID, courseId)
                putExtra(EXTRA_TITLE, courseTitle)
                putExtra(EXTRA_STEPS, ArrayList(steps))
                putExtra(EXTRA_START_STEP, startStep)
            }

        fun start(
            context: Context,
            courseId: String,
            courseTitle: String,
            steps: List<CourseItem.LessonStep>,
            startStep: Int,
        ) {
            val intent = createIntent(context, courseId, courseTitle, steps, startStep)
            context.startActivity(intent)
        }
    }
}
