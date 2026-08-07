package org.ole.planet.myplanet.lite

import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.lite.dashboard.DashboardCoursesRepository
import org.ole.planet.myplanet.lite.profile.StoredCredentials
import org.ole.planet.myplanet.lite.util.MarkdownUtils
import org.ole.planet.myplanet.lite.util.NetworkUtils

private const val MIN_DOWNLOAD_BUFFER_BYTES = 10L * 1024L * 1024L

fun DashboardCoursePageFragment.downloadCourse(course: CourseItem) {
    if (OfflineCourseStorage.isCourseDownloaded(requireContext(), course.id)) {
        adapter.updateDownloadedCourses(OfflineCourseStorage.downloadedCourseIds(requireContext()))
        return
    }
    if (activeDownloads.containsKey(course.id)) return

    val base = baseUrl
    val creds = credentials
    if (base.isNullOrBlank() || creds == null) {
        Toast.makeText(requireContext(), getString(R.string.dashboard_courses_loading_error), Toast.LENGTH_SHORT)
            .show()
        return
    }
    val resources = course.steps.flatMap { it.resources }
        .distinctBy { "${it.id}/${it.filename}" }
    val markdownImageSources = extractMarkdownImageSources(course)

    val job = viewLifecycleOwner.lifecycleScope.launch {
        performCourseDownload(course, base, creds, resources, markdownImageSources)
    }
    activeDownloads[course.id] = job
}

suspend fun DashboardCoursePageFragment.performCourseDownload(
    course: CourseItem,
    base: String,
    creds: StoredCredentials,
    resources: List<CourseItem.LessonResource>,
    markdownImageSources: List<String>
) {
    val totalInventoryItems = resources.size + markdownImageSources.size + if (course.coverPath != null) 1 else 0
    adapter.updateDownloadProgress(course.id, 0, totalInventoryItems)

    if (!hasSufficientStorage(course, base, creds, resources, markdownImageSources)) {
        activeDownloads.remove(course.id)
        return
    }

    val mappedResources = resources.map { DashboardCoursesRepository.DownloadResource(it.id, it.filename) }
    val result = withContext(Dispatchers.IO) {
        coursesRepository.downloadCourseResources(
            base,
            creds,
            mappedResources,
            markdownImageSources,
            { resourceId, filename -> OfflineCourseStorage.resourceFile(requireContext(), course.id, resourceId, filename) },
            { source -> OfflineCourseStorage.markdownImageFile(requireContext(), course.id, source) },
            course.coverPath,
            { source -> OfflineCourseStorage.coverFile(requireContext(), course.id, source) },
        ) { progress ->
            viewLifecycleOwner.lifecycleScope.launch {
                adapter.updateDownloadProgress(course.id, progress.first, progress.second)
            }
        }
    }

    handleDownloadResult(course, result)
    activeDownloads.remove(course.id)
}

suspend fun DashboardCoursePageFragment.hasSufficientStorage(
    course: CourseItem,
    base: String,
    creds: StoredCredentials,
    resources: List<CourseItem.LessonResource>,
    markdownImageSources: List<String>
): Boolean {
    val mappedResources = resources.map { DashboardCoursesRepository.DownloadResource(it.id, it.filename) }
    val estimatedSize = coursesRepository.estimateResourcesSize(base, creds, mappedResources) +
        coursesRepository.estimateMarkdownImagesSize(base, creds, markdownImageSources) +
        coursesRepository.estimateCourseCoverSize(base, creds, course.coverPath)
    val available = OfflineCourseStorage.availableStorageBytes(requireContext())
    val required = estimatedSize + MIN_DOWNLOAD_BUFFER_BYTES
    if (available <= required) {
        adapter.updateDownloadProgress(course.id, null, null)
        Toast.makeText(
            requireContext(),
            getString(R.string.dashboard_course_insufficient_storage),
            Toast.LENGTH_SHORT
        ).show()
        return false
    }
    return true
}

fun DashboardCoursePageFragment.handleDownloadResult(course: CourseItem, result: Boolean) {
    if (result) {
        val source = if (tabPosition == 2) {
            OfflineCourseStorage.DownloadSource.TEAM_COURSES
        } else {
            OfflineCourseStorage.DownloadSource.MY_COURSES
        }
        OfflineCourseStorage.saveCourseManifest(requireContext(), course, source)
        adapter.updateDownloadProgress(course.id, null, null)
        adapter.updateDownloadedCourses(OfflineCourseStorage.downloadedCourseIds(requireContext()))
    } else {
        adapter.updateDownloadProgress(course.id, null, null)
        Toast.makeText(
            requireContext(),
            getString(R.string.dashboard_courses_loading_error),
            Toast.LENGTH_SHORT
        ).show()
    }
}

fun DashboardCoursePageFragment.confirmDeleteDownloadedCourse(course: CourseItem) {
    AlertDialog.Builder(requireContext())
        .setMessage(getString(R.string.dashboard_course_delete_downloaded_confirm))
        .setNegativeButton(android.R.string.cancel, null)
        .setPositiveButton(R.string.dashboard_post_action_delete) { _, _ ->
            val deleted = OfflineCourseStorage.deleteCourse(requireContext(), course.id)
            if (deleted) {
                adapter.updateDownloadedCourses(OfflineCourseStorage.downloadedCourseIds(requireContext()))
                if (!NetworkUtils.isDeviceOnline(requireContext()) && tabPosition == 0) {
                    refreshUserCourses(adapter, refreshLayout)
                }
            } else {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.dashboard_courses_loading_error),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        .show()
}

fun DashboardCoursePageFragment.extractMarkdownImageSources(course: CourseItem): List<String> {
    val fromCourseDescription = MarkdownUtils.extractImageSourcesFromText(course.description)
    val fromSteps = course.steps.flatMap { step -> MarkdownUtils.extractImageSourcesFromText(step.description) }
    return (fromCourseDescription + fromSteps).distinct()
}
