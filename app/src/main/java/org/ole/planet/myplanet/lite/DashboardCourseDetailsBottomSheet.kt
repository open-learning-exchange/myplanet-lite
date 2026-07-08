/*
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-12-28
 */

package org.ole.planet.myplanet.lite

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.noties.markwon.Markwon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.lite.auth.AuthDependencies
import org.ole.planet.myplanet.lite.dashboard.DashboardPostImageLoader
import org.ole.planet.myplanet.lite.dashboard.DashboardServerPreferences

class DashboardCourseDetailsBottomSheet : BottomSheetDialogFragment() {
    private lateinit var markwon: Markwon
    private var imageLoader: DashboardPostImageLoader? = null
    private var onJoinCourse: (() -> Unit)? = null
    private var onLeaveCourse: (() -> Unit)? = null
    private var onOpenCourse: (() -> Unit)? = null

    private data class StepDisplay(
        val title: String,
        val mediaTypes: List<String>,
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = inflater.inflate(R.layout.bottom_sheet_dashboard_course_details, container, false)

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        val args = requireArguments()
        val courseTitle = args.getString(ARG_TITLE).orEmpty()
        val courseId = args.getString(ARG_COURSE_ID).orEmpty()
        val courseDescription = args.getString(ARG_DESCRIPTION).orEmpty()
        val stepTitles = args.getStringArrayList(ARG_STEP_TITLES) ?: arrayListOf()
        val stepMediaTypes = args.getStringArrayList(ARG_STEP_MEDIA_TYPES) ?: arrayListOf()
        val steps =
            stepTitles.mapIndexed { index, title ->
                val mediaTypes =
                    stepMediaTypes
                        .getOrNull(index)
                        ?.split(",")
                        ?.mapNotNull { it.trim().takeIf(String::isNotBlank) }
                        .orEmpty()
                StepDisplay(title, mediaTypes)
            }
        val currentStep = args.getInt(ARG_CURRENT_STEP, -1).takeIf { it >= 0 }
        val isEnrolled = args.getBoolean(ARG_IS_ENROLLED, true)

        markwon = Markwon.builder(requireContext()).build()

        val titleView: TextView = view.findViewById(R.id.dashboardCourseDetailsTitle)
        val summaryView: TextView = view.findViewById(R.id.dashboardCourseDetailsSummary)
        val progressView: TextView = view.findViewById(R.id.dashboardCourseDetailsProgress)
        val progressBar: com.google.android.material.progressindicator.LinearProgressIndicator =
            view.findViewById(R.id.dashboardCourseDetailsProgressIndicator)
        val descriptionView: TextView = view.findViewById(R.id.dashboardCourseDetailsDescription)
        val imagesContainer: LinearLayout = view.findViewById(R.id.dashboardCourseDetailsImagesContainer)
        val stepsContainer: LinearLayout = view.findViewById(R.id.dashboardCourseDetailsStepsContainer)
        val closeButton: View = view.findViewById(R.id.dashboardCourseDetailsClose)
        val leaveButton: View = view.findViewById(R.id.dashboardCourseDetailsLeave)
        val ctaButton: View = view.findViewById(R.id.dashboardCourseDetailsCta)

        titleView.text = getString(R.string.dashboard_course_lessons_title, courseTitle)
        summaryView.isVisible = false
        progressView.visibility = View.GONE
        progressBar.visibility = View.GONE

        bindDescription(courseId, courseDescription, descriptionView, imagesContainer)

        bindSteps(steps, currentStep, stepsContainer)

        if (ctaButton is TextView) {
            ctaButton.text =
                if (isEnrolled) {
                    getString(R.string.dashboard_course_lessons_open_course)
                } else {
                    getString(R.string.dashboard_course_lessons_join_course)
                }
        }

        leaveButton.isVisible = isEnrolled
        if (isEnrolled) {
            leaveButton.setOnClickListener {
                MaterialAlertDialogBuilder(requireContext())
                    .setMessage(getString(R.string.dashboard_course_leave_confirm))
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.dashboard_course_leave_course) { _, _ ->
                        onLeaveCourse?.invoke()
                        dismiss()
                    }.show()
            }
        }

        ctaButton.setOnClickListener {
            if (isEnrolled) {
                onOpenCourse?.invoke()
            } else {
                onJoinCourse?.invoke()
            }
            dismiss()
        }
        closeButton.setOnClickListener { dismiss() }
    }

    private fun bindDescription(
        courseId: String,
        description: String,
        descriptionView: TextView,
        imagesContainer: LinearLayout,
    ) {
        val trimmed = description.trim()
        descriptionView.isVisible = trimmed.isNotEmpty()
        if (trimmed.isEmpty()) {
            imagesContainer.isVisible = false
            imagesContainer.removeAllViews()
            return
        }

        val imagePaths = extractImagePaths(trimmed)
        val textOnly = removeImageMarkdown(trimmed)
        val rendered = transformMarkdownForDisplay(textOnly)
        markwon.setMarkdown(descriptionView, rendered)
        bindImages(courseId, imagePaths, imagesContainer)
    }

    private fun bindImages(
        courseId: String,
        imagePaths: List<String>,
        container: LinearLayout,
    ) {
        container.removeAllViews()
        if (imagePaths.isEmpty()) {
            container.isVisible = false
            return
        }

        val context = requireContext().applicationContext
        val baseUrl = DashboardServerPreferences.getServerBaseUrl(context)
        if (baseUrl.isNullOrBlank()) {
            container.isVisible = false
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            if (imageLoader == null) {
                val authService = AuthDependencies.provideAuthService(context, baseUrl)
                val sessionCookie = withContext(Dispatchers.IO) { authService.getStoredToken() }
                imageLoader = DashboardPostImageLoader(baseUrl, sessionCookie, viewLifecycleOwner.lifecycleScope)
            }

            container.isVisible = true
            imagePaths.forEachIndexed { index, path ->
                val imageView = ImageView(requireContext())
                val params =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                if (index > 0) {
                    params.topMargin = resources.getDimensionPixelSize(R.dimen.dashboard_post_image_spacing)
                }
                imageView.layoutParams = params
                imageView.adjustViewBounds = true
                imageView.scaleType = ImageView.ScaleType.FIT_CENTER
                container.addView(imageView)
                val localUri =
                    courseId.takeIf { it.isNotBlank() }?.let {
                        OfflineCourseStorage.localMarkdownImageUri(requireContext(), it, path)
                    }
                if (!localUri.isNullOrBlank()) {
                    Glide.with(imageView).load(Uri.parse(localUri)).into(imageView)
                } else {
                    imageLoader?.bind(imageView, path) { success ->
                        imageView.isVisible = success
                    }
                }
            }
        }
    }

    private fun transformMarkdownForDisplay(markdown: String): String = markdown.replace("\n", "  \n")

    private fun extractImagePaths(markdown: String): List<String> {
        if (markdown.isBlank()) return emptyList()
        return IMAGE_MARKDOWN_CAPTURE_REGEX
            .findAll(markdown)
            .mapNotNull { match ->
                match.groupValues
                    .getOrNull(1)
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
            }.toList()
    }

    private fun removeImageMarkdown(markdown: String): String {
        if (markdown.isBlank()) return markdown
        return IMAGE_MARKDOWN_CAPTURE_REGEX.replace(markdown) { _ -> "" }
    }

    private fun bindSteps(
        steps: List<StepDisplay>,
        currentStep: Int?,
        container: LinearLayout,
    ) {
        container.removeAllViews()
        if (steps.isEmpty()) return

        val inflater = LayoutInflater.from(container.context)
        steps.forEachIndexed { index, step ->
            val view = inflater.inflate(R.layout.item_dashboard_course_lesson, container, false)
            val titleView: TextView = view.findViewById(R.id.dashboardCourseLessonTitle)
            val durationView: TextView = view.findViewById(R.id.dashboardCourseLessonDuration)
            val checkView: View = view.findViewById(R.id.dashboardCourseLessonCheck)
            val mediaContainer: LinearLayout = view.findViewById(R.id.dashboardCourseLessonMediaIcons)
            titleView.text = step.title
            durationView.visibility = View.GONE
            val completedThreshold = currentStep ?: -1
            val completed = index < completedThreshold
            checkView.visibility = if (completed) View.VISIBLE else View.INVISIBLE
            bindMediaIcons(step.mediaTypes, mediaContainer)
            container.addView(view)
        }
    }

    private fun bindMediaIcons(
        mediaTypes: List<String>,
        container: LinearLayout,
    ) {
        container.removeAllViews()
        if (mediaTypes.isEmpty()) {
            container.visibility = View.GONE
            return
        }

        container.visibility = View.VISIBLE
        val inflater = LayoutInflater.from(container.context)
        mediaTypes.forEach { mediaType ->
            val iconRes =
                when (mediaType.lowercase().trim()) {
                    "video" -> R.drawable.ic_course_step_video_24
                    "pdf" -> R.drawable.ic_course_step_pdf_24
                    "image" -> R.drawable.ic_course_step_image_24
                    "audio" -> R.drawable.ic_course_step_audio_24
                    "survey" -> R.drawable.ic_course_step_survey_24
                    "exam" -> R.drawable.ic_course_step_exam_24
                    else -> null
                }
            if (iconRes != null) {
                val icon =
                    inflater.inflate(
                        R.layout.item_dashboard_course_step_media_icon,
                        container,
                        false,
                    ) as ImageView
                icon.setImageResource(iconRes)
                container.addView(icon)
            }
        }
        if (container.childCount == 0) {
            container.visibility = View.GONE
        }
    }

    companion object {
        private const val ARG_TITLE = "arg_title"
        private const val ARG_COURSE_ID = "arg_course_id"
        private const val ARG_DESCRIPTION = "arg_description"
        private const val ARG_STEP_TITLES = "arg_step_titles"
        private const val ARG_STEP_MEDIA_TYPES = "arg_step_media_types"
        private const val ARG_CURRENT_STEP = "arg_current_step"
        private const val ARG_IS_ENROLLED = "arg_is_enrolled"
        private val IMAGE_MARKDOWN_CAPTURE_REGEX = Regex("!\\[[^\\]]*\\]\\(([^)]+)\\)")

        fun show(
            fragmentManager: FragmentManager,
            course: CourseItem,
            isEnrolled: Boolean,
            onJoinCourse: (() -> Unit)? = null,
            onLeaveCourse: (() -> Unit)? = null,
            onOpenCourse: (() -> Unit)? = null,
        ) {
            val sheet = DashboardCourseDetailsBottomSheet()
            sheet.onJoinCourse = onJoinCourse
            sheet.onLeaveCourse = onLeaveCourse
            sheet.onOpenCourse = onOpenCourse
            sheet.arguments =
                Bundle().apply {
                    putString(ARG_TITLE, course.title)
                    putString(ARG_COURSE_ID, course.id)
                    putString(ARG_DESCRIPTION, course.description)
                    putStringArrayList(
                        ARG_STEP_TITLES,
                        ArrayList(course.steps.map { it.title }),
                    )
                    putStringArrayList(
                        ARG_STEP_MEDIA_TYPES,
                        ArrayList(course.steps.map { it.mediaTypes.joinToString(",") }),
                    )
                    course.currentStep?.let { putInt(ARG_CURRENT_STEP, it) }
                    putBoolean(ARG_IS_ENROLLED, isEnrolled)
                }
            sheet.show(fragmentManager, "course_details")
        }
    }
}
