package org.ole.planet.myplanet.lite

import android.content.Intent
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import java.util.Locale
import kotlinx.coroutines.launch
import okhttp3.Credentials
import org.ole.planet.myplanet.lite.dashboard.DashboardImagePreviewActivity
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyDocument
import org.ole.planet.myplanet.lite.util.MarkdownUtils

fun CourseWizardActivity.bindAttachments(
    resources: List<CourseItem.LessonResource>,
    survey: SurveyDocument?,
    exam: SurveyDocument?,
    container: LinearLayout,
    titleView: TextView,
    listContainer: LinearLayout
) {
    resetAttachmentState(listContainer)

    val videoResources = mutableListOf<CourseItem.LessonResource>()
    val imageResources = mutableListOf<CourseItem.LessonResource>()
    val displayResources = mutableListOf<CourseItem.LessonResource>()

    resources.forEach { resource ->
        val mediaType = resource.mediaType.lowercase(Locale.ROOT)
        val isVideo = mediaType.contains("video")
        val isImage = mediaType.contains("image")
        val isPdf = mediaType.contains("pdf")
        val isAudio = mediaType.contains("audio")

        if (isVideo) videoResources.add(resource)
        if (isImage) imageResources.add(resource)
        if (isVideo || isImage || isPdf || isAudio) {
            displayResources.add(resource)
        }
    }

    val hasSurvey = survey?.questions?.isNotEmpty() == true
    val hasExam = exam?.questions?.isNotEmpty() == true
    if (displayResources.isEmpty() && !hasSurvey && !hasExam) {
        container.visibility = View.GONE
        return
    }
    container.visibility = View.VISIBLE
    titleView.visibility = View.VISIBLE
    val inflater = layoutInflater
    prepareVideoPlaylist(videoResources)
    if (videoResources.isNotEmpty() && currentPlaylistUrls.isEmpty()) {
        container.visibility = View.GONE
        Toast.makeText(this, getString(R.string.course_wizard_play_error), Toast.LENGTH_SHORT)
            .show()
        return
    }
    populateAttachmentList(
        AttachmentConfig(
            survey = survey,
            hasSurvey = hasSurvey,
            exam = exam,
            hasExam = hasExam,
            displayResources = displayResources,
            imageResources = imageResources,
            inflater = inflater,
            listContainer = listContainer
        )
    )
}

fun CourseWizardActivity.resetAttachmentState(listContainer: LinearLayout) {
    listContainer.removeAllViews()
    playlistIndexByResourceId.clear()
    currentPlaylistUrls.clear()
    lastPlaybackIndex = 0
    lastPlaybackPositionMs = 0L
    releaseAudioPlayers()
}

fun CourseWizardActivity.prepareVideoPlaylist(videoResources: List<CourseItem.LessonResource>) {
    videoResources.forEachIndexed { index, resource ->
        val resolvedUrl = buildResourceUrl(resource)
        if (resolvedUrl != null) {
            playlistIndexByResourceId[resource.id] = index
            currentPlaylistUrls.add(resolvedUrl)
        }
    }
}

fun CourseWizardActivity.populateAttachmentList(config: AttachmentConfig) {
    if (config.survey != null && config.hasSurvey) {
        bindSurveyAttachment(config.survey, config.inflater, config.listContainer)
    }
    if (config.exam != null && config.hasExam) {
        bindExamAttachment(config.exam, config.inflater, config.listContainer)
    }
    config.displayResources.forEach { resource ->
        bindResourceAttachment(resource, config.imageResources, config.inflater, config.listContainer)
    }
}

fun CourseWizardActivity.bindSurveyAttachment(
    survey: SurveyDocument,
    inflater: android.view.LayoutInflater,
    listContainer: LinearLayout
) {
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
    titleText.text = survey.name.orEmpty().ifBlank {
        getString(R.string.dashboard_surveys_title)
    }
    subtitle.text = getString(R.string.dashboard_surveys_title)
    iconView.setImageResource(R.drawable.ic_surveys_24)
    iconView.contentDescription = getString(R.string.dashboard_surveys_title)
    playButton.contentDescription = getString(R.string.course_wizard_open_survey)
    val openSurvey = {
        pendingRequiredStepIndex = currentIndex
        SurveyWizardActivity.newIntent(
            this,
            survey,
            survey.teamId,
            null,
            courseId
        ).also { requiredStepLauncher.launch(it) }
    }
    itemView.setOnClickListener { openSurvey() }
    playButton.setOnClickListener { openSurvey() }
    listContainer.addView(itemView)
}

fun CourseWizardActivity.bindExamAttachment(
    exam: SurveyDocument,
    inflater: android.view.LayoutInflater,
    listContainer: LinearLayout
) {
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
    titleText.text = exam.name.orEmpty().ifBlank {
        getString(R.string.dashboard_exam_title)
    }
    subtitle.text = getString(R.string.dashboard_exam_title)
    iconView.setImageResource(R.drawable.ic_course_step_exam_24)
    iconView.contentDescription = getString(R.string.dashboard_exam_title)
    playButton.contentDescription = getString(R.string.course_wizard_open_exam)
    val openExam = {
        pendingRequiredStepIndex = currentIndex
        SurveyWizardActivity.newIntent(
            this,
            exam,
            exam.teamId,
            null,
            courseId,
            isExam = true
        ).also { requiredStepLauncher.launch(it) }
    }
    itemView.setOnClickListener { openExam() }
    playButton.setOnClickListener { openExam() }
    listContainer.addView(itemView)
}

fun CourseWizardActivity.bindResourceAttachment(
    resource: CourseItem.LessonResource,
    imageResources: List<CourseItem.LessonResource>,
    inflater: android.view.LayoutInflater,
    listContainer: LinearLayout
) {
    val mediaTypeLower = resource.mediaType.lowercase(Locale.ROOT)
    val isAudio = mediaTypeLower.contains("audio")
    val itemView = if (isAudio) {
        inflater.inflate(R.layout.item_course_wizard_audio_attachment, listContainer, false)
    } else {
        inflater.inflate(R.layout.item_course_wizard_attachment, listContainer, false)
    }
    val titleText: TextView = itemView.findViewById(R.id.courseWizardAttachmentTitle)
    val subtitle: TextView = itemView.findViewById(R.id.courseWizardAttachmentSubtitle)
    val iconView: android.widget.ImageView =
        itemView.findViewById(R.id.courseWizardAttachmentIcon)
    titleText.text = resource.filename
    subtitle.text = resource.mediaType
    val isVideo = mediaTypeLower.contains("video")
    val isImage = mediaTypeLower.contains("image")
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
                Toast.makeText(this, getString(R.string.course_wizard_play_error), Toast.LENGTH_SHORT).show()
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

@OptIn(UnstableApi::class)
fun CourseWizardActivity.bindAudioPlayer(itemView: View, resource: CourseItem.LessonResource) {
    val url = buildResourceUrl(resource)
    if (url.isNullOrBlank()) {
        Toast.makeText(this, getString(R.string.course_wizard_play_error), Toast.LENGTH_SHORT).show()
        return
    }
    val authHeader = if (baseUrl?.startsWith("https://", ignoreCase = true) == true) {
        credentials?.let { Credentials.basic(it.username, it.password) }
    } else {
        null
    }
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

@OptIn(UnstableApi::class)
fun CourseWizardActivity.buildAudioDataSourceFactory(authorizationHeader: String?): DataSource.Factory {
    val httpFactory = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
    authorizationHeader?.let { httpFactory.setDefaultRequestProperties(mapOf("Authorization" to it)) }
    return DefaultDataSource.Factory(this, httpFactory)
}

fun CourseWizardActivity.releaseAudioPlayers() {
    audioPlayers.forEach { it.release() }
    audioPlayers.clear()
}

fun CourseWizardActivity.selectResource(resource: CourseItem.LessonResource) {
    val targetIndex = playlistIndexByResourceId[resource.id] ?: return
    launchFullscreenPlayer(targetIndex)
}

fun CourseWizardActivity.launchFullscreenPlayer(startIndex: Int) {
    if (currentPlaylistUrls.isEmpty()) {
        Toast.makeText(this, getString(R.string.course_wizard_play_error), Toast.LENGTH_SHORT).show()
        return
    }
    val startPosition = if (startIndex == lastPlaybackIndex) lastPlaybackPositionMs else 0L
    val authHeader = if (baseUrl?.startsWith("https://", ignoreCase = true) == true) {
        credentials?.let { Credentials.basic(it.username, it.password) }
    } else {
        null
    }
    val intent = FullscreenPlayerActivity.createIntent(
        context = this,
        mediaUrls = ArrayList(currentPlaylistUrls),
        startIndex = startIndex,
        startPositionMs = startPosition,
        authorizationHeader = authHeader
    )
    fullscreenLauncher.launch(intent)
}

fun CourseWizardActivity.openPdfResource(resource: CourseItem.LessonResource) {
    val url = buildResourceUrl(resource)
    if (url.isNullOrBlank()) {
        Toast.makeText(this, getString(R.string.course_wizard_play_error), Toast.LENGTH_SHORT).show()
        return
    }
    val authHeader = if (baseUrl?.startsWith("https://", ignoreCase = true) == true) {
        credentials?.let { Credentials.basic(it.username, it.password) }
    } else {
        null
    }
    val intent = FullscreenPdfActivity.createIntent(this, url, authHeader)
    startActivity(intent)
}

fun CourseWizardActivity.openImageResource(
    resource: CourseItem.LessonResource,
    imageResources: List<CourseItem.LessonResource>
) {
    val validResources = imageResources.mapNotNull { image ->
        buildResourcePath(image)?.let { path -> image to path }
    }
    if (validResources.isEmpty()) {
        Toast.makeText(this, getString(R.string.course_wizard_play_error), Toast.LENGTH_SHORT).show()
        return
    }
    val imagePaths = validResources.map { it.second }
    val startIndex = validResources.indexOfFirst {
        it.first.id == resource.id && it.first.filename == resource.filename
    }.coerceAtLeast(0)
    val intent = Intent(this, DashboardImagePreviewActivity::class.java).apply {
        putStringArrayListExtra(
            DashboardImagePreviewActivity.EXTRA_IMAGE_PATHS,
            ArrayList(imagePaths)
        )
        putExtra(DashboardImagePreviewActivity.EXTRA_START_INDEX, startIndex)
    }
    startActivity(intent)
}

fun CourseWizardActivity.buildResourceUrl(resource: CourseItem.LessonResource): String? {
    val safeCourseId = courseId?.takeIf { it.isNotBlank() } ?: return null
    val localFile = OfflineCourseStorage.findExistingResourceFile(
        this,
        safeCourseId,
        resource.id,
        resource.filename
    )
    if (localFile?.exists() == true) {
        return localFile.toURI().toString()
    }
    val normalizedBase = baseUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() } ?: return null
    val parsed = normalizedBase.toUri()
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

fun CourseWizardActivity.buildResourcePath(resource: CourseItem.LessonResource): String? {
    val safeCourseId = courseId?.takeIf { it.isNotBlank() }
    if (safeCourseId != null) {
        val localFile = OfflineCourseStorage.findExistingResourceFile(
            this,
            safeCourseId,
            resource.id,
            resource.filename
        )
        if (localFile?.exists() == true) {
            return localFile.toURI().toString()
        }
    }
    val resourceId = resource.id.trim().takeIf { it.isNotEmpty() } ?: return null
    val filename = resource.filename.trim().takeIf { it.isNotEmpty() } ?: return null
    return "resources/$resourceId/$filename"
}

fun CourseWizardActivity.resolveOfflineMarkdownImages(markdown: String): String {
    return MarkdownUtils.resolveOfflineMarkdownImages(this, markdown, courseId, baseUrl)
}

data class AttachmentConfig(
    val survey: SurveyDocument?,
    val hasSurvey: Boolean,
    val exam: SurveyDocument?,
    val hasExam: Boolean,
    val displayResources: List<CourseItem.LessonResource>,
    val imageResources: List<CourseItem.LessonResource>,
    val inflater: android.view.LayoutInflater,
    val listContainer: LinearLayout
)
