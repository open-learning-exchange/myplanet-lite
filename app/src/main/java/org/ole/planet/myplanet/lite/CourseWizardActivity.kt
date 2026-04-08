/**
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2026-01-04
 */
package org.ole.planet.myplanet.lite

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
import java.util.ArrayList
import java.util.Locale
import kotlinx.coroutines.launch
import okhttp3.Credentials
import org.ole.planet.myplanet.lite.dashboard.DashboardCoursesRepository
import org.ole.planet.myplanet.lite.dashboard.DashboardImagePreviewActivity
import org.ole.planet.myplanet.lite.dashboard.DashboardServerPreferences
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyDocument
import org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore
import org.ole.planet.myplanet.lite.profile.StoredCredentials

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class CourseWizardActivity : AppCompatActivity() {
    private lateinit var markwon: Markwon
    private var steps: List<StepDisplay> = emptyList()
    private var baseUrl: String? = null
    private var currentIndex: Int = 0
    private var courseId: String? = null
    private var credentials: StoredCredentials? = null
    private lateinit var stepPositionView: TextView
    private lateinit var stepTitleView: TextView
    private lateinit var descriptionView: TextView
    private lateinit var attachmentsContainer: LinearLayout
    private lateinit var attachmentsList: LinearLayout
    private lateinit var attachmentsTitle: TextView
    private lateinit var previousButton: View
    private lateinit var nextButton: View
    private val playlistIndexByResourceId = mutableMapOf<String, Int>()
    private val currentPlaylistUrls = mutableListOf<String>()
    private var lastPlaybackIndex = 0
    private var lastPlaybackPositionMs = 0L
    private val coursesRepository = DashboardCoursesRepository()
    private var hasAutoCompletedFirstStep = false
    private val audioPlayers = mutableListOf<ExoPlayer>()
    private val completedExamSteps = mutableSetOf<Int>()
    private var pendingExamStepIndex: Int? = null
    private var cachedProgressDocument: DashboardCoursesRepository.CourseProgressDocument? = null
    private val fullscreenLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data ?: return@registerForActivityResult
            lastPlaybackIndex = data.getIntExtra(FullscreenPlayerActivity.EXTRA_RESULT_INDEX, 0)
            lastPlaybackPositionMs = data.getLongExtra(
                FullscreenPlayerActivity.EXTRA_RESULT_POSITION,
                0L
            )
        }
    private val examLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                pendingExamStepIndex?.let { completedExamSteps.add(it) }
            }
            pendingExamStepIndex = null
            if (this::stepPositionView.isInitialized) {
                bindStep(
                    stepPositionView,
                    stepTitleView,
                    descriptionView,
                    attachmentsContainer,
                    attachmentsTitle,
                    attachmentsList,
                    previousButton,
                    nextButton
                )
            }
        }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        WindowCompat.setDecorFitsSystemWindows(window, true)
        setContentView(R.layout.activity_course_wizard)
        markwon = Markwon.builder(this)
            .usePlugin(TablePlugin.create(this))
            .build()
        val (courseTitle, startIndex) = parseIntentData(savedInstanceState)
        if (steps.isEmpty()) {
            finish()
            return
        }
        setupViews(courseTitle)
        lifecycleScope.launch {
            currentIndex = resolveInitialStepIndex(startIndex)
            bindStep(
                stepPositionView,
                stepTitleView,
                descriptionView,
                attachmentsContainer,
                attachmentsTitle,
                attachmentsList,
                previousButton,
                nextButton
            )
            maybeAutoCompleteFirstStep()
        }
    }
    private fun parseIntentData(savedInstanceState: Bundle?): Pair<String, Int> {
        val courseTitle = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        courseId = intent.getStringExtra(EXTRA_COURSE_ID)
        val startIndex = intent.getIntExtra(EXTRA_START_STEP, 0)
        baseUrl = DashboardServerPreferences.getServerBaseUrl(applicationContext)
        credentials = ProfileCredentialsStore.getStoredCredentials(applicationContext)
        savedInstanceState?.getIntegerArrayList(EXTRA_COMPLETED_EXAM_STEPS)?.let { restored ->
            completedExamSteps.clear()
            completedExamSteps.addAll(restored)
        }
        @Suppress("UNCHECKED_CAST")
        val stepPayload: ArrayList<DashboardCoursePageFragment.CourseItem.LessonStep>? =
            IntentCompat.getSerializableExtra(
                intent,
                EXTRA_STEPS,
                ArrayList::class.java
            ) as? ArrayList<DashboardCoursePageFragment.CourseItem.LessonStep>
        steps = stepPayload?.map { step ->
            StepDisplay(
                title = step.title,
                description = step.description,
                resources = step.resources,
                survey = step.survey,
                exam = step.exam
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
                initialPaddingBottom + systemBars.bottom
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
            EXTRA_COMPLETED_EXAM_STEPS,
            ArrayList(completedExamSteps)
        )
    }
    private suspend fun resolveInitialStepIndex(fallbackIndex: Int): Int {
        if (cachedProgressDocument != null) {
            val progressStep = cachedProgressDocument?.stepNum
            val resolvedIndex = progressStep?.minus(1) ?: fallbackIndex
            return resolvedIndex.coerceIn(0, steps.lastIndex)
        }
        val normalizedBase = baseUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() } ?: return fallbackIndex
        val creds = credentials ?: return fallbackIndex
        val id = courseId?.takeIf { it.isNotBlank() } ?: return fallbackIndex
        val progressDocuments = coursesRepository.fetchCoursesProgressDocuments(
            normalizedBase,
            creds,
            listOf(id)
        ).getOrNull()
        cachedProgressDocument = progressDocuments?.get(id)
        val progressStep = cachedProgressDocument?.stepNum
        val resolvedIndex = progressStep?.minus(1) ?: fallbackIndex
        return resolvedIndex.coerceIn(0, steps.lastIndex)
    }
    private fun maybeAutoCompleteFirstStep() {
        if (hasAutoCompletedFirstStep || currentIndex != 0) return
        hasAutoCompletedFirstStep = true
        lifecycleScope.launch {
            runCatching { updateCourseProgressIfNeeded(1) }
        }
    }
    private fun bindStep(
        stepPositionView: TextView,
        stepTitleView: TextView,
        descriptionView: TextView,
        attachmentsContainer: LinearLayout,
        attachmentsTitle: TextView,
        attachmentsList: LinearLayout,
        previousButton: View,
        nextButton: View
    ) {
        val step = steps[currentIndex]
        stepPositionView.text = getString(
            R.string.course_wizard_step_position,
            currentIndex + 1,
            steps.size
        )
        stepTitleView.text = step.title
        markwon.setMarkdown(descriptionView, step.description.replace("\n", "  \n"))
        bindAttachments(
            step.resources,
            step.survey,
            step.exam,
            attachmentsContainer,
            attachmentsTitle,
            attachmentsList
        )
        previousButton.isEnabled = currentIndex > 0
        previousButton.setOnClickListener {
            if (currentIndex > 0) {
                currentIndex -= 1
                bindStep(
                    stepPositionView,
                    stepTitleView,
                    descriptionView,
                    attachmentsContainer,
                    attachmentsTitle,
                    attachmentsList,
                    previousButton,
                    nextButton
                )
            }
        }
        val examPending = step.exam?.questions?.isNotEmpty() == true &&
                !completedExamSteps.contains(currentIndex)
        if (currentIndex >= steps.lastIndex) {
            nextButton.isEnabled = true
            if (nextButton is TextView) {
                nextButton.text = getString(R.string.course_wizard_finish)
            }
            nextButton.setOnClickListener { finish() }
        } else {
            nextButton.isEnabled = true
            if (nextButton is TextView) {
                nextButton.text = getString(R.string.course_wizard_next)
            }
            nextButton.setOnClickListener {
                if (currentIndex < steps.lastIndex) {
                    lifecycleScope.launch {
                        advanceToNextStep(
                            stepPositionView,
                            stepTitleView,
                            descriptionView,
                            attachmentsContainer,
                            attachmentsTitle,
                            attachmentsList,
                            previousButton,
                            nextButton
                        )
                    }
                }
            }
        }
        if (examPending) {
            nextButton.isEnabled = false
        }
    }
    private suspend fun advanceToNextStep(
        stepPositionView: TextView,
        stepTitleView: TextView,
        descriptionView: TextView,
        attachmentsContainer: LinearLayout,
        attachmentsTitle: TextView,
        attachmentsList: LinearLayout,
        previousButton: View,
        nextButton: View
    ) {
        val targetIndex = (currentIndex + 1).coerceAtMost(steps.lastIndex)
        val targetStepNumber = targetIndex + 1
        lifecycleScope.launch {
            runCatching { updateCourseProgressIfNeeded(targetStepNumber) }
        }
        currentIndex = targetIndex
        bindStep(
            stepPositionView,
            stepTitleView,
            descriptionView,
            attachmentsContainer,
            attachmentsTitle,
            attachmentsList,
            previousButton,
            nextButton
        )
    }
    private suspend fun updateCourseProgressIfNeeded(targetStepNumber: Int) {
        val normalizedBase = baseUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() } ?: return
        val creds = credentials ?: return
        val id = courseId?.takeIf { it.isNotBlank() } ?: return

        if (cachedProgressDocument == null) {
            val existingDocuments = coursesRepository.fetchCoursesProgressDocuments(normalizedBase, creds, listOf(id))
                .getOrNull()
            cachedProgressDocument = existingDocuments?.get(id)
        }

        val existingStep = cachedProgressDocument?.stepNum ?: 0
        if (existingStep >= targetStepNumber) return

        val now = System.currentTimeMillis()
        val document = DashboardCoursesRepository.CourseProgressUpdateDocument(
            id = cachedProgressDocument?.id,
            rev = cachedProgressDocument?.rev,
            userId = "org.couchdb.user:${creds.username}",
            courseId = id,
            stepNum = targetStepNumber,
            passed = true,
            createdOn = cachedProgressDocument?.createdOn
                ?: DashboardServerPreferences.getServerCode(applicationContext),
            parentCode = cachedProgressDocument?.parentCode
                ?: DashboardServerPreferences.getServerParentCode(applicationContext),
            createdDate = cachedProgressDocument?.createdDate ?: now,
            updatedDate = now
        )

        val saveResult = coursesRepository.saveCourseProgress(normalizedBase, creds, document).getOrNull()
        val newId = saveResult?.firstOrNull()?.id ?: document.id
        val newRev = saveResult?.firstOrNull()?.rev ?: document.rev

        // Update cache with the new progress step
        cachedProgressDocument = DashboardCoursesRepository.CourseProgressDocument(
            id = newId,
            rev = newRev,
            courseId = document.courseId,
            stepNum = document.stepNum,
            passed = document.passed,
            createdDate = document.createdDate,
            updatedDate = document.updatedDate,
            createdOn = document.createdOn,
            parentCode = document.parentCode
        )
    }
    private fun bindAttachments(
        resources: List<DashboardCoursePageFragment.CourseItem.LessonResource>,
        survey: SurveyDocument?,
        exam: SurveyDocument?,
        container: LinearLayout,
        titleView: TextView,
        listContainer: LinearLayout
    ) {
        listContainer.removeAllViews()
        playlistIndexByResourceId.clear()
        currentPlaylistUrls.clear()
        lastPlaybackIndex = 0
        lastPlaybackPositionMs = 0L
        releaseAudioPlayers()
        val videoResources = resources.filter { it.mediaType.lowercase(Locale.ROOT).contains("video") }
        val imageResources = resources.filter { it.mediaType.lowercase(Locale.ROOT).contains("image") }
        val displayResources = resources.filter { resource ->
            val mediaType = resource.mediaType.lowercase(Locale.ROOT)
            mediaType.contains("video") || mediaType.contains("pdf") || mediaType.contains("image") ||
                    mediaType.contains("audio")
        }
        val surveyDocument = survey
        val examDocument = exam
        val hasSurvey = surveyDocument?.questions?.isNotEmpty() == true
        val hasExam = examDocument?.questions?.isNotEmpty() == true
        if (displayResources.isEmpty() && !hasSurvey && !hasExam) {
            container.visibility = View.GONE
            return
        }
        container.visibility = View.VISIBLE
        titleView.visibility = View.VISIBLE
        val inflater = layoutInflater
        videoResources.forEachIndexed { index, resource ->
            val resolvedUrl = buildResourceUrl(resource)
            if (resolvedUrl != null) {
                playlistIndexByResourceId[resource.id] = index
                currentPlaylistUrls.add(resolvedUrl)
            }
        }
        if (videoResources.isNotEmpty() && currentPlaylistUrls.isEmpty()) {
            container.visibility = View.GONE
            Toast.makeText(this, getString(R.string.course_wizard_play_error), Toast.LENGTH_SHORT)
                .show()
            return
        }
        if (hasSurvey) {
            val itemView = inflater.inflate(
                R.layout.item_course_wizard_attachment,
                listContainer,
                false
            )
            val titleText: TextView = itemView.findViewById(R.id.courseWizardAttachmentTitle)
            val subtitle: TextView = itemView.findViewById(R.id.courseWizardAttachmentSubtitle)
            val iconView: android.widget.ImageView =
                itemView.findViewById(R.id.courseWizardAttachmentIcon)
            val playButton: ImageButton = itemView.findViewById(R.id.courseWizardAttachmentPlay)
            titleText.text = surveyDocument.name.orEmpty().ifBlank {
                getString(R.string.dashboard_surveys_title)
            }
            subtitle.text = getString(R.string.dashboard_surveys_title)
            iconView.setImageResource(R.drawable.ic_surveys_24)
            iconView.contentDescription = getString(R.string.dashboard_surveys_title)
            playButton.contentDescription = getString(R.string.course_wizard_open_survey)
            val openSurvey = {
                SurveyWizardActivity.newIntent(
                    this,
                    surveyDocument,
                    surveyDocument.teamId,
                    null,
                    courseId
                ).also { startActivity(it) }
            }
            itemView.setOnClickListener { openSurvey() }
            playButton.setOnClickListener { openSurvey() }
            listContainer.addView(itemView)
        }
        if (hasExam) {
            val examPayload = requireNotNull(examDocument)
            val itemView = inflater.inflate(
                R.layout.item_course_wizard_attachment,
                listContainer,
                false
            )
            val titleText: TextView = itemView.findViewById(R.id.courseWizardAttachmentTitle)
            val subtitle: TextView = itemView.findViewById(R.id.courseWizardAttachmentSubtitle)
            val iconView: android.widget.ImageView =
                itemView.findViewById(R.id.courseWizardAttachmentIcon)
            val playButton: ImageButton = itemView.findViewById(R.id.courseWizardAttachmentPlay)
            titleText.text = examPayload.name.orEmpty().ifBlank {
                getString(R.string.dashboard_exam_title)
            }
            subtitle.text = getString(R.string.dashboard_exam_title)
            iconView.setImageResource(R.drawable.ic_course_step_exam_24)
            iconView.contentDescription = getString(R.string.dashboard_exam_title)
            playButton.contentDescription = getString(R.string.course_wizard_open_exam)
            val openExam = openExam@{
                pendingExamStepIndex = currentIndex
                SurveyWizardActivity.newIntent(
                    this,
                    examPayload,
                    examPayload.teamId,
                    null,
                    courseId,
                    isExam = true
                ).also { examLauncher.launch(it) }
            }
            itemView.setOnClickListener { openExam() }
            playButton.setOnClickListener { openExam() }
            listContainer.addView(itemView)
        }
        displayResources.forEach { resource ->
            val isAudio = resource.mediaType.lowercase(Locale.ROOT).contains("audio")
            val itemView = if (isAudio) {
                inflater.inflate(
                    R.layout.item_course_wizard_audio_attachment,
                    listContainer,
                    false
                )
            } else {
                inflater.inflate(
                    R.layout.item_course_wizard_attachment,
                    listContainer,
                    false
                )
            }
            val titleText: TextView = itemView.findViewById(R.id.courseWizardAttachmentTitle)
            val subtitle: TextView = itemView.findViewById(R.id.courseWizardAttachmentSubtitle)
            val iconView: android.widget.ImageView =
                itemView.findViewById(R.id.courseWizardAttachmentIcon)
            titleText.text = resource.filename
            subtitle.text = resource.mediaType
            val isVideo = resource.mediaType.lowercase(Locale.ROOT).contains("video")
            val isImage = resource.mediaType.lowercase(Locale.ROOT).contains("image")
            when {
                isVideo -> {
                    val playButton: ImageButton = itemView.findViewById(R.id.courseWizardAttachmentPlay)
                    iconView.setImageResource(R.drawable.ic_course_resource_video_24)
                    iconView.contentDescription = getString(R.string.course_wizard_attachment_video)
                    playButton.contentDescription = getString(R.string.course_wizard_play_video)
                }
                isImage -> {
                    val playButton: ImageButton = itemView.findViewById(R.id.courseWizardAttachmentPlay)
                    iconView.setImageResource(R.drawable.ic_course_step_image_24)
                    iconView.contentDescription = getString(R.string.course_wizard_attachment_image)
                    playButton.contentDescription = getString(R.string.course_wizard_open_image)
                }
                isAudio -> {
                    iconView.setImageResource(R.drawable.ic_course_step_audio_24)
                    iconView.contentDescription = getString(R.string.course_wizard_attachment_audio)
                    bindAudioPlayer(itemView, resource)
                }
                else -> {
                    val playButton: ImageButton = itemView.findViewById(R.id.courseWizardAttachmentPlay)
                    iconView.setImageResource(R.drawable.ic_course_step_pdf_24)
                    iconView.contentDescription = getString(R.string.course_wizard_attachment_pdf)
                    playButton.contentDescription = getString(R.string.course_wizard_open_pdf)
                }
            }
            val openResource = {
                if (isVideo) {
                    if (playlistIndexByResourceId.containsKey(resource.id)) {
                        selectResource(resource)
                    } else {
                        Toast.makeText(
                            this,
                            getString(R.string.course_wizard_play_error),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else if (isImage) {
                    openImageResource(resource, imageResources)
                } else {
                    openPdfResource(resource)
                }
            }
            if (!isAudio) {
                val playButton: ImageButton = itemView.findViewById(R.id.courseWizardAttachmentPlay)
                itemView.setOnClickListener { openResource() }
                playButton.setOnClickListener { openResource() }
            }
            listContainer.addView(itemView)
        }
    }
    private fun bindAudioPlayer(
        itemView: View,
        resource: DashboardCoursePageFragment.CourseItem.LessonResource
    ) {
        val url = buildResourceUrl(resource)
        if (url.isNullOrBlank()) {
            Toast.makeText(this, getString(R.string.course_wizard_play_error), Toast.LENGTH_SHORT)
                .show()
            return
        }
        val authHeader = credentials?.let { Credentials.basic(it.username, it.password) }
        val playerView: PlayerView = itemView.findViewById(R.id.courseWizardAudioPlayer)
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(buildAudioDataSourceFactory(authHeader)))
            .build()
        playerView.player = player
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.playWhenReady = false
        audioPlayers.add(player)
    }
    private fun buildAudioDataSourceFactory(authorizationHeader: String?): DefaultHttpDataSource.Factory {
        val factory = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
        authorizationHeader?.let { factory.setDefaultRequestProperties(mapOf("Authorization" to it)) }
        return factory
    }
    private fun releaseAudioPlayers() {
        audioPlayers.forEach { it.release() }
        audioPlayers.clear()
    }
    private fun selectResource(resource: DashboardCoursePageFragment.CourseItem.LessonResource) {
        val targetIndex = playlistIndexByResourceId[resource.id] ?: return
        launchFullscreenPlayer(targetIndex)
    }
    private fun launchFullscreenPlayer(startIndex: Int) {
        if (currentPlaylistUrls.isEmpty()) {
            Toast.makeText(this, getString(R.string.course_wizard_play_error), Toast.LENGTH_SHORT)
                .show()
            return
        }
        val startPosition = if (startIndex == lastPlaybackIndex) {
            lastPlaybackPositionMs
        } else {
            0L
        }
        val authHeader = credentials?.let { Credentials.basic(it.username, it.password) }
        val intent = FullscreenPlayerActivity.createIntent(
            context = this,
            mediaUrls = ArrayList(currentPlaylistUrls),
            startIndex = startIndex,
            startPositionMs = startPosition,
            authorizationHeader = authHeader
        )
        fullscreenLauncher.launch(intent)
    }
    private fun openPdfResource(resource: DashboardCoursePageFragment.CourseItem.LessonResource) {
        val url = buildResourceUrl(resource)
        if (url.isNullOrBlank()) {
            Toast.makeText(this, getString(R.string.course_wizard_play_error), Toast.LENGTH_SHORT)
                .show()
            return
        }
        val authHeader = credentials?.let { Credentials.basic(it.username, it.password) }
        val intent = FullscreenPdfActivity.createIntent(this, url, authHeader)
        startActivity(intent)
    }
    private fun openImageResource(
        resource: DashboardCoursePageFragment.CourseItem.LessonResource,
        imageResources: List<DashboardCoursePageFragment.CourseItem.LessonResource>
    ) {
        val validResources = imageResources.mapNotNull { image ->
            buildResourcePath(image)?.let { path -> image to path }
        }
        if (validResources.isEmpty()) {
            Toast.makeText(this, getString(R.string.course_wizard_play_error), Toast.LENGTH_SHORT)
                .show()
            return
        }
        val imagePaths = validResources.map { it.second }
        val startIndex = validResources.indexOfFirst {
            it.first.id == resource.id && it.first.filename == resource.filename
        }
            .coerceAtLeast(0)
        val intent = Intent(this, DashboardImagePreviewActivity::class.java).apply {
            putStringArrayListExtra(
                DashboardImagePreviewActivity.EXTRA_IMAGE_PATHS,
                ArrayList(imagePaths)
            )
            putExtra(DashboardImagePreviewActivity.EXTRA_START_INDEX, startIndex)
        }
        startActivity(intent)
    }
    private fun buildResourceUrl(resource: DashboardCoursePageFragment.CourseItem.LessonResource): String? {
        val normalizedBase = baseUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() } ?: return null
        val parsed = Uri.parse(normalizedBase)
        val scheme = parsed.scheme ?: return null
        val authority = parsed.encodedAuthority ?: return null
        return parsed.buildUpon()
            .scheme(scheme)
            .encodedAuthority(authority)
            .appendPath("db")
            .appendPath("resources")
            .appendPath(resource.id)
            .appendPath(resource.filename)
            .build()
            .toString()
    }
    private fun buildResourcePath(resource: DashboardCoursePageFragment.CourseItem.LessonResource): String? {
        val resourceId = resource.id.trim().takeIf { it.isNotEmpty() } ?: return null
        val filename = resource.filename.trim().takeIf { it.isNotEmpty() } ?: return null
        return "resources/$resourceId/$filename"
    }
    data class StepDisplay(
        val title: String,
        val description: String,
        val resources: List<DashboardCoursePageFragment.CourseItem.LessonResource>,
        val survey: SurveyDocument? = null,
        val exam: SurveyDocument? = null
    )
    companion object {
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_COURSE_ID = "extra_course_id"
        private const val EXTRA_STEPS = "extra_steps"
        private const val EXTRA_START_STEP = "extra_start_step"
        private const val EXTRA_COMPLETED_EXAM_STEPS = "extra_completed_exam_steps"
        fun start(
            context: Context,
            courseId: String,
            courseTitle: String,
            steps: List<DashboardCoursePageFragment.CourseItem.LessonStep>,
            startStep: Int
        ) {
            val intent = Intent(context, CourseWizardActivity::class.java).apply {
                putExtra(EXTRA_COURSE_ID, courseId)
                putExtra(EXTRA_TITLE, courseTitle)
                putExtra(EXTRA_STEPS, ArrayList(steps))
                putExtra(EXTRA_START_STEP, startStep)
            }
            context.startActivity(intent)
        }
    }
}
