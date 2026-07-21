/*
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-12-28
 */

package org.ole.planet.myplanet.lite

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.lite.auth.AuthDependencies
import org.ole.planet.myplanet.lite.dashboard.DashboardCoursesRepository
import org.ole.planet.myplanet.lite.dashboard.DashboardPostImageLoader
import org.ole.planet.myplanet.lite.dashboard.DashboardServerPreferences
import org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore
import org.ole.planet.myplanet.lite.profile.StoredCredentials
import org.ole.planet.myplanet.lite.survey.DashboardLocalSurveyRepository

class DashboardCoursePageFragment : Fragment(R.layout.fragment_dashboard_courses_page) {
    val coursesRepository = DashboardCoursesRepository()
    var baseUrl: String? = null
    var credentials: StoredCredentials? = null
    var currentTeamId: String? = null
    var myCourseIds: List<String> = emptyList()
    var needsMyCourseIdsRefresh: Boolean = false
    var isPaging = false
    var hasMorePages = true
    var currentSkip = 0
    val pageSize = 20
    var tabPosition: Int = 0
    lateinit var recyclerView: RecyclerView
    lateinit var refreshLayout: SwipeRefreshLayout
    lateinit var emptyView: TextView
    lateinit var loadingOverlay: View
    lateinit var adapter: CourseAdapter
    private var courseImageLoader: DashboardPostImageLoader? = null
    private var isCourseImageLoaderLoading = false
    var courseCategories: List<CourseCategory> = emptyList()
    var selectedCategoryId: String? = null
    val tagCourseIdsByTag = mutableMapOf<String, Set<String>>()
    val activeDownloads = mutableMapOf<String, Job>()
    val localSurveyRepository by lazy { DashboardLocalSurveyRepository(requireContext()) }
    private val courseWizardLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != android.app.Activity.RESULT_OK) return@registerForActivityResult
            val data = result.data
            val courseId = data?.getStringExtra(CourseWizardActivity.EXTRA_RESULT_COURSE_ID)
            val progressPercent =
                data
                    ?.getIntExtra(
                        CourseWizardActivity.EXTRA_RESULT_PROGRESS_PERCENT,
                        -1,
                    )?.takeIf { it >= 0 }
            val currentStep =
                data
                    ?.getIntExtra(
                        CourseWizardActivity.EXTRA_RESULT_CURRENT_STEP,
                        -1,
                    )?.takeIf { it >= 0 }
            if (!courseId.isNullOrBlank() && progressPercent != null && currentStep != null) {
                adapter.updateCourseProgress(courseId, progressPercent, currentStep)
            }
            refreshCoursesAfterWizard()
        }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        tabPosition = requireArguments().getInt(ARG_TAB_POSITION)

        baseUrl = DashboardServerPreferences.getServerBaseUrl(requireContext())
        credentials = ProfileCredentialsStore.getStoredCredentials(requireContext())

        setupViews(view)
        setupAdapter()
        setupRecyclerView()
        setupRefreshLayout()

        registerJoinListener()
        maybeHandlePendingJoinRefresh()

        performInitialLoad()
        setupPagination()
    }

    private fun setupViews(view: View) {
        recyclerView = view.findViewById(R.id.dashboardCoursesRecycler)
        emptyView = view.findViewById(R.id.dashboardCoursesEmptyView)
        refreshLayout = view.findViewById(R.id.dashboardCoursesRefresh)
        loadingOverlay = view.findViewById(R.id.dashboardCoursesLoadingOverlay)
        courseCategories =
            listOf(
                CourseCategory(id = null, name = getString(R.string.dashboard_courses_category_all)),
            )
    }

    private fun setupAdapter() {
        adapter =
            CourseAdapter(
                showProgress = tabPosition == 0,
                showDownloadButton = tabPosition == 0,
                imageLoaderProvider = { courseImageLoader },
                ensureImageLoader = { ensureCourseImageLoader() },
                categoriesProvider = { courseCategories },
            )
        adapter.onCategorySelected = { categoryId ->
            selectedCategoryId = categoryId
            if (categoryId.isNullOrBlank()) {
                adapter.updateTagFilter(null)
            } else {
                loadTagLinks(categoryId)
            }
        }
        adapter.onCourseDownloadClick = { course ->
            if (tabPosition == 0) {
                downloadCourse(course)
            }
        }
        adapter.onCourseDeleteClick = { course ->
            if (tabPosition == 0) {
                confirmDeleteDownloadedCourse(course)
            }
        }
        adapter.onCourseClick = { course ->
            val isEnrolledCourse = tabPosition == 0 || myCourseIds.contains(course.id)
            DashboardCourseDetailsBottomSheet.show(
                childFragmentManager,
                course,
                isEnrolled = isEnrolledCourse,
                onJoinCourse =
                    if (isEnrolledCourse) {
                        null
                    } else {
                        { handleJoinCourse(course) }
                    },
                onLeaveCourse =
                    if (isEnrolledCourse) {
                        { handleLeaveCourse(course) }
                    } else {
                        null
                    },
                onOpenCourse =
                    if (isEnrolledCourse) {
                        { openCourseWizard(course) }
                    } else {
                        null
                    },
            )
        }
    }

    private fun setupRecyclerView() {
        val spanCount = 2
        val layoutManager = GridLayoutManager(requireContext(), spanCount)
        layoutManager.spanSizeLookup =
            object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int = if (adapter.isHeader(position)) spanCount else 1
            }

        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter
        recyclerView.addItemDecoration(CourseGridSpacingDecoration(spanCount))
    }

    private fun setupRefreshLayout() {
        refreshLayout.setOnRefreshListener {
            refreshLayout.isRefreshing = false
            showLoadingOverlay(true)
            when (tabPosition) {
                0 -> {
                    refreshUserCourses(adapter, refreshLayout)
                }

                1 -> {
                    refreshAllCourses(adapter, refreshLayout)
                }

                else -> {
                    refreshTeamCourses(adapter, refreshLayout, forceReload = true)
                }
            }
        }
    }

    private fun performInitialLoad() {
        showLoadingOverlay(true)
        when (tabPosition) {
            0 -> {
                refreshUserCourses(adapter, refreshLayout)
            }

            1 -> {
                refreshAllCourses(adapter, refreshLayout)
            }

            else -> {
                refreshTeamCourses(adapter, refreshLayout, forceReload = true)
            }
        }

        loadCourseCategories()
    }

    private fun setupPagination() {
        if (tabPosition == 1) {
            recyclerView.addOnScrollListener(
                object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(
                        recyclerView: RecyclerView,
                        dx: Int,
                        dy: Int,
                    ) {
                        super.onScrolled(recyclerView, dx, dy)
                        if (dy <= 0 || isPaging || !hasMorePages) return

                        val manager = recyclerView.layoutManager as? GridLayoutManager ?: return
                        val lastVisible = manager.findLastVisibleItemPosition()
                        if (lastVisible >= adapter.itemCount - 4) {
                            loadNextCoursesPage(adapter, null)
                        }
                    }
                },
            )
        }
    }

    private fun ensureCourseImageLoader() {
        if (courseImageLoader != null || isCourseImageLoaderLoading) {
            return
        }
        val base = baseUrl?.trim()?.trimEnd('/').orEmpty()
        if (base.isEmpty()) {
            return
        }
        isCourseImageLoaderLoading = true
        viewLifecycleOwner.lifecycleScope.launch {
            val authService = AuthDependencies.provideAuthService(requireContext(), base)
            val sessionCookie = withContext(Dispatchers.IO) { authService.getStoredToken() }
            courseImageLoader = DashboardPostImageLoader(base, sessionCookie, viewLifecycleOwner.lifecycleScope)
            isCourseImageLoaderLoading = false
            adapter.notifyItemRangeChanged(1, (adapter.itemCount - 1).coerceAtLeast(0), CourseAdapter.IMAGE_UPDATE_PAYLOAD)
        }
    }

    override fun onResume() {
        super.onResume()
        viewLifecycleOwner.lifecycleScope.launch {
            flushPendingSurveyOutbox()
        }
        if (tabPosition == 2 && this::adapter.isInitialized) {
            refreshTeamCourses(adapter, refreshLayout, forceReload = false)
        }

        maybeHandlePendingJoinRefresh()
    }

    private fun openCourseWizard(course: CourseItem) {
        if (course.steps.isEmpty()) {
            Toast
                .makeText(
                    requireContext(),
                    getString(R.string.dashboard_courses_loading_error),
                    Toast.LENGTH_SHORT,
                ).show()
            return
        }
        val startStep = course.currentStep?.coerceIn(0, course.steps.lastIndex) ?: 0
        courseWizardLauncher.launch(
            CourseWizardActivity.createIntent(
                requireContext(),
                course.id,
                course.title,
                course.steps,
                startStep,
            ),
        )
    }

    private fun refreshCoursesAfterWizard() {
        if (!this::adapter.isInitialized) return
        when (tabPosition) {
            0 -> refreshUserCourses(adapter, refreshLayout)
            1 -> refreshAllCourses(adapter, refreshLayout)
            else -> refreshTeamCourses(adapter, refreshLayout, forceReload = false)
        }
    }

    companion object {
        private const val ARG_TAB_POSITION = "tab_position"

        fun newInstance(position: Int) =
            DashboardCoursePageFragment().apply {
                arguments =
                    Bundle().apply {
                        putInt(ARG_TAB_POSITION, position)
                    }
            }
    }
}
