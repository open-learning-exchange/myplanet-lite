package org.ole.planet.myplanet.lite

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.lite.auth.AuthDependencies
import org.ole.planet.myplanet.lite.dashboard.DashboardTeamSelectionPreferences
import org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore
import org.ole.planet.myplanet.lite.util.NetworkUtils

private const val RESULT_JOINED_COURSE = "dashboard_course_joined"
private const val KEY_JOINED_COURSE_ID = "joined_course_id"
private const val KEY_LEFT_COURSE = "left_course"
private val pendingRefreshTabs = mutableSetOf<Int>()

fun DashboardCoursePageFragment.isOfflineModeActive(): Boolean {
    return (activity as? DashboardActivity)?.isOfflineModeActive() == true
}

fun DashboardCoursePageFragment.showOfflineDownloadedCourses(
    adapter: CourseAdapter,
    refreshLayout: SwipeRefreshLayout?
) {
    val offlineCourses = when (tabPosition) {
        0 -> OfflineCourseStorage.loadDownloadedCourses(
            requireContext(),
            OfflineCourseStorage.DownloadSource.MY_COURSES
        )
        2 -> OfflineCourseStorage.loadDownloadedCourses(
            requireContext(),
            OfflineCourseStorage.DownloadSource.TEAM_COURSES
        )
        else -> emptyList()
    }
    adapter.submitCourses(offlineCourses)
    adapter.updateDownloadedCourses(OfflineCourseStorage.downloadedCourseIds(requireContext()))
    refreshLayout?.isRefreshing = false
    showLoadingOverlay(false)
}

fun DashboardCoursePageFragment.loadCourseCategories() {
    val base = baseUrl ?: return
    viewLifecycleOwner.lifecycleScope.launch {
        val credentials = ProfileCredentialsStore.getStoredCredentials(requireContext())
        val authService = AuthDependencies.provideAuthService(requireContext(), base)
        val sessionCookie = withContext(Dispatchers.IO) { authService.getStoredToken() }
        val tagsResult = coursesRepository.fetchCourseTags(base, credentials, sessionCookie)
        val tags = tagsResult.getOrElse { emptyList() }
        val mapped = tags.mapNotNull { tag ->
            val id = tag.id?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val name = tag.name?.trim()?.takeIf { it.isNotEmpty() } ?: id
            CourseCategory(id = id, name = name)
        }
        courseCategories = listOf(
            CourseCategory(id = null, name = getString(R.string.dashboard_courses_category_all))
        ) + mapped
        adapter.updateCategories()
    }
}

fun DashboardCoursePageFragment.loadTagLinks(tagId: String) {
    val cached = tagCourseIdsByTag[tagId]
    if (cached != null) {
        adapter.updateTagFilter(cached)
        return
    }
    val base = baseUrl ?: return
    viewLifecycleOwner.lifecycleScope.launch {
        val credentials = ProfileCredentialsStore.getStoredCredentials(requireContext())
        val authService = AuthDependencies.provideAuthService(requireContext(), base)
        val sessionCookie = withContext(Dispatchers.IO) { authService.getStoredToken() }
        val linksResult = coursesRepository.fetchTagLinks(base, credentials, sessionCookie, tagId)
        val links = linksResult.getOrElse { emptyList() }
        val courseIds = links.mapNotNull { it.linkId?.trim()?.takeIf { id -> id.isNotEmpty() } }.toSet()
        tagCourseIdsByTag[tagId] = courseIds
        if (selectedCategoryId == tagId) {
            adapter.updateTagFilter(courseIds)
        }
    }
}

fun DashboardCoursePageFragment.registerJoinListener() {
    val resultManager = requireActivity().supportFragmentManager
    resultManager.setFragmentResultListener(RESULT_JOINED_COURSE, viewLifecycleOwner) { _, bundle ->
        val joinedCourseId = bundle.getString(KEY_JOINED_COURSE_ID)
        val leftCourse = bundle.getBoolean(KEY_LEFT_COURSE, false)
        if (!joinedCourseId.isNullOrBlank()) {
            myCourseIds = if (leftCourse) {
                myCourseIds.filterNot { it == joinedCourseId }
            } else {
                (myCourseIds + joinedCourseId).distinct()
            }
            needsMyCourseIdsRefresh = true
            synchronized(pendingRefreshTabs) {
                pendingRefreshTabs.addAll(listOf(0, 1, 2))
            }
        }
        resetPagingState()
        adapter.submitCourses(emptyList())
        when (tabPosition) {
            0 -> refreshUserCourses(adapter, refreshLayout)
            1 -> refreshAllCourses(adapter, refreshLayout)
            else -> refreshTeamCourses(adapter, refreshLayout, forceReload = true)
        }
        synchronized(pendingRefreshTabs) {
            pendingRefreshTabs.remove(tabPosition)
        }
    }
}

fun DashboardCoursePageFragment.refreshUserCourses(
    adapter: CourseAdapter,
    refreshLayout: SwipeRefreshLayout
) {
    showLoadingOverlay(true)
    viewLifecycleOwner.lifecycleScope.launch {
        if (isOfflineModeActive() || !NetworkUtils.isDeviceOnline(requireContext())) {
            showOfflineDownloadedCourses(adapter, refreshLayout)
            return@launch
        }
        val base = baseUrl
        val creds = credentials
        if (base.isNullOrBlank() || creds == null) {
            handleMissingCredentials(adapter, refreshLayout)
            return@launch
        }
        val courseIdsResult = coursesRepository.fetchUserCourseIds(base, creds)
        val courseIds = courseIdsResult.getOrElse {
            Toast.makeText(
                requireContext(),
                it.message ?: getString(R.string.dashboard_courses_loading_error),
                Toast.LENGTH_SHORT
            ).show()
            refreshLayout.isRefreshing = false
            showLoadingOverlay(false)
            return@launch
        }
        myCourseIds = courseIds
        needsMyCourseIdsRefresh = false
        if (courseIds.isEmpty()) {
            adapter.submitCourses(emptyList())
            refreshLayout.isRefreshing = false
            showLoadingOverlay(false)
            return@launch
        }
        val coursesResult = coursesRepository.fetchCourses(base, creds, courseIds)
        val courses = coursesResult.getOrElse {
            Toast.makeText(
                requireContext(),
                it.message ?: getString(R.string.dashboard_courses_loading_error),
                Toast.LENGTH_SHORT
            ).show()
            refreshLayout.isRefreshing = false
            showLoadingOverlay(false)
            return@launch
        }
        val courseProgress = coursesRepository.fetchCoursesProgress(base, creds, courseIds)
            .getOrElse {
                Toast.makeText(
                    requireContext(),
                    it.message ?: getString(R.string.dashboard_courses_loading_error),
                    Toast.LENGTH_SHORT
                ).show()
                emptyMap()
            }
        val mapped = courses
            .filter { !it.id.isNullOrBlank() }
            .distinctBy { it.id }
            .map {
                it.toCourseItem(
                    getString(R.string.dashboard_courses_title),
                    courseProgress[it.id]
                )
            }
        adapter.submitCourses(mapped)
        adapter.updateDownloadedCourses(OfflineCourseStorage.downloadedCourseIds(requireContext()))
        refreshLayout.isRefreshing = false
        showLoadingOverlay(false)
    }
}

fun DashboardCoursePageFragment.refreshAllCourses(
    adapter: CourseAdapter,
    refreshLayout: SwipeRefreshLayout
) {
    if (isOfflineModeActive() || !NetworkUtils.isDeviceOnline(requireContext())) {
        showOfflineDownloadedCourses(adapter, refreshLayout)
        return
    }
    resetPagingState()
    adapter.submitCourses(emptyList())
    showLoadingOverlay(true)
    loadNextCoursesPage(adapter, refreshLayout)
}

fun DashboardCoursePageFragment.refreshTeamCourses(
    adapter: CourseAdapter,
    refreshLayout: SwipeRefreshLayout,
    forceReload: Boolean
) {
    if (isOfflineModeActive() || !NetworkUtils.isDeviceOnline(requireContext())) {
        showOfflineDownloadedCourses(adapter, refreshLayout)
        return
    }
    val selectedTeamId = DashboardTeamSelectionPreferences.getSelectedTeamId(requireContext())
    val selectedTeamName = DashboardTeamSelectionPreferences.getSelectedTeamName(requireContext())
    if (selectedTeamId.isNullOrBlank() || selectedTeamName.isNullOrBlank()) {
        adapter.submitCourses(emptyList())
        refreshLayout.isRefreshing = false
        showLoadingOverlay(false)
        showEmptyState(getString(R.string.dashboard_teams_select_team_hint))
        currentTeamId = null
        return
    }

    val unchangedSelection = selectedTeamId == currentTeamId && !forceReload
    if (unchangedSelection && adapter.itemCount > 1) {
        hideEmptyState()
        refreshLayout.isRefreshing = false
        showLoadingOverlay(false)
        return
    }

    hideEmptyState()
    showLoadingOverlay(true)
    currentTeamId = selectedTeamId

    viewLifecycleOwner.lifecycleScope.launch {
        fetchAndSubmitTeamCourses(adapter, refreshLayout, selectedTeamId)
    }
}

private suspend fun DashboardCoursePageFragment.fetchAndSubmitTeamCourses(
    adapter: CourseAdapter,
    refreshLayout: SwipeRefreshLayout,
    selectedTeamId: String
) {
    val base = baseUrl
    val creds = credentials
    if (base.isNullOrBlank() || creds == null) {
        handleMissingCredentials(adapter, refreshLayout)
        return
    }

    ensureUserCourseIds()

    val coursesResult = coursesRepository.fetchTeamCourses(base, creds, selectedTeamId)
    val courses = coursesResult.getOrElse {
        Toast.makeText(
            requireContext(),
            it.message ?: getString(R.string.dashboard_courses_loading_error),
            Toast.LENGTH_SHORT
        ).show()
        refreshLayout.isRefreshing = false
        showLoadingOverlay(false)
        return
    }
    val mapped = courses
        .filter { !it.id.isNullOrBlank() }
        .distinctBy { it.id }
        .map {
            it.toCourseItem(
                getString(R.string.dashboard_courses_title),
                null
            )
        }
    adapter.submitCourses(mapped)
    refreshLayout.isRefreshing = false
    showLoadingOverlay(false)
}

fun DashboardCoursePageFragment.handleJoinCourse(course: CourseItem) {
    showLoadingOverlay(true)
    viewLifecycleOwner.lifecycleScope.launch {
        val base = baseUrl
        val creds = credentials
        if (base.isNullOrBlank() || creds == null) {
            handleMissingCredentials()
            return@launch
        }

        coursesRepository.joinCourse(base, creds, course.id).onFailure { error ->
            Toast.makeText(
                requireContext(),
                error.message ?: getString(R.string.dashboard_courses_loading_error),
                Toast.LENGTH_SHORT
            ).show()
        }.onSuccess {
            myCourseIds = (myCourseIds + course.id).distinct()
            needsMyCourseIdsRefresh = true
            synchronized(pendingRefreshTabs) {
                pendingRefreshTabs.addAll(listOf(0, 1, 2))
            }
            resetPagingState()
            adapter.submitCourses(emptyList())
            when (tabPosition) {
                1 -> refreshAllCourses(adapter, refreshLayout)
                2 -> refreshTeamCourses(adapter, refreshLayout, forceReload = true)
            }
            requireActivity().supportFragmentManager.setFragmentResult(
                RESULT_JOINED_COURSE,
                Bundle().apply {
                    putString(KEY_JOINED_COURSE_ID, course.id)
                }
            )
        }

        showLoadingOverlay(false)
    }
}

fun DashboardCoursePageFragment.handleLeaveCourse(course: CourseItem) {
    showLoadingOverlay(true)
    viewLifecycleOwner.lifecycleScope.launch {
        val base = baseUrl
        val creds = credentials
        if (base.isNullOrBlank() || creds == null) {
            handleMissingCredentials()
            return@launch
        }

        coursesRepository.leaveCourse(base, creds, course.id).onFailure { error ->
            Toast.makeText(
                requireContext(),
                error.message ?: getString(R.string.dashboard_courses_loading_error),
                Toast.LENGTH_SHORT
            ).show()
        }.onSuccess {
            myCourseIds = myCourseIds.filterNot { it == course.id }
            needsMyCourseIdsRefresh = true
            synchronized(pendingRefreshTabs) {
                pendingRefreshTabs.addAll(listOf(0, 1, 2))
            }
            resetPagingState()
            adapter.submitCourses(emptyList())
            when (tabPosition) {
                0 -> refreshUserCourses(adapter, refreshLayout)
                1 -> refreshAllCourses(adapter, refreshLayout)
                else -> refreshTeamCourses(adapter, refreshLayout, forceReload = true)
            }
            requireActivity().supportFragmentManager.setFragmentResult(
                RESULT_JOINED_COURSE,
                Bundle().apply {
                    putString(KEY_JOINED_COURSE_ID, course.id)
                    putBoolean(KEY_LEFT_COURSE, true)
                }
            )
        }

        showLoadingOverlay(false)
    }
}

fun DashboardCoursePageFragment.handleMissingCredentials(
    adapter: CourseAdapter? = null,
    refreshLayout: SwipeRefreshLayout? = null
) {
    Toast.makeText(
        requireContext(),
        getString(R.string.dashboard_courses_missing_credentials),
        Toast.LENGTH_SHORT
    ).show()
    adapter?.submitCourses(emptyList())
    refreshLayout?.isRefreshing = false
    showLoadingOverlay(false)
    redirectToLogin()
}

fun DashboardCoursePageFragment.redirectToLogin() {
    val currentActivity = activity ?: return
    val intent = Intent(currentActivity, MyPlanetLite::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    startActivity(intent)
    currentActivity.finish()
}

fun DashboardCoursePageFragment.loadNextCoursesPage(
    adapter: CourseAdapter,
    refreshLayout: SwipeRefreshLayout?
) {
    if (isOfflineModeActive() || !NetworkUtils.isDeviceOnline(requireContext())) {
        showOfflineDownloadedCourses(adapter, refreshLayout)
        return
    }
    if (isPaging || !hasMorePages) {
        refreshLayout?.isRefreshing = false
        return
    }
    val base = baseUrl
    val creds = credentials
    if (base.isNullOrBlank() || creds == null) {
        hasMorePages = false
        handleMissingCredentials(refreshLayout = refreshLayout)
        return
    }
    isPaging = true
    showLoadingOverlay(true)
    viewLifecycleOwner.lifecycleScope.launch {
        val excludedIds = if (tabPosition == 1) ensureUserCourseIds() else emptyList()
        val pageResult = coursesRepository.fetchCoursesByParent(
            base,
            creds,
            excludedIds,
            currentSkip,
            pageSize
        ).getOrElse {
            Toast.makeText(
                requireContext(),
                it.message ?: getString(R.string.dashboard_courses_loading_error),
                Toast.LENGTH_SHORT
            ).show()
            refreshLayout?.isRefreshing = false
            isPaging = false
            hasMorePages = false
            showLoadingOverlay(false)
            return@launch
        }
        val mapped = pageResult.courses
            .filter { !it.id.isNullOrBlank() }
            .distinctBy { it.id }
            .map {
                it.toCourseItem(
                    getString(R.string.dashboard_courses_title),
                    null
                )
            }
        if (currentSkip == 0) {
            adapter.submitCourses(mapped)
        } else {
            adapter.appendCourses(mapped)
        }
        currentSkip += pageResult.fetchedCount
        hasMorePages = pageResult.hasMore && pageResult.fetchedCount > 0
        isPaging = false
        refreshLayout?.isRefreshing = false
        showLoadingOverlay(false)
    }
}

suspend fun DashboardCoursePageFragment.flushPendingSurveyOutbox() {
    localSurveyRepository.flushPendingSurveyOutbox()
}

suspend fun DashboardCoursePageFragment.ensureUserCourseIds(): List<String> {
    if (myCourseIds.isNotEmpty() && !needsMyCourseIdsRefresh) return myCourseIds

    val base = baseUrl
    val creds = credentials
    if (base.isNullOrBlank() || creds == null) {
        return emptyList()
    }

    val result = coursesRepository.fetchUserCourseIds(base, creds)
    val ids = result.getOrElse {
        Toast.makeText(
            requireContext(),
            it.message ?: getString(R.string.dashboard_courses_loading_error),
            Toast.LENGTH_SHORT
        ).show()
        emptyList()
    }
    myCourseIds = ids
    needsMyCourseIdsRefresh = false
    return myCourseIds
}

fun DashboardCoursePageFragment.maybeHandlePendingJoinRefresh() {
    val shouldRefresh = synchronized(pendingRefreshTabs) {
        pendingRefreshTabs.contains(tabPosition)
    }
    if (!shouldRefresh) return

    synchronized(pendingRefreshTabs) {
        pendingRefreshTabs.remove(tabPosition)
    }

    showLoadingOverlay(true)
    when (tabPosition) {
        0 -> refreshUserCourses(adapter, refreshLayout)
        1 -> refreshAllCourses(adapter, refreshLayout)
        else -> refreshTeamCourses(adapter, refreshLayout, forceReload = true)
    }
}