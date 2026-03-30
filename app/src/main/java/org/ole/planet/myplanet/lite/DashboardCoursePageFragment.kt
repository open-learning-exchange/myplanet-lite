/**
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-12-28
 */

package org.ole.planet.myplanet.lite

import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.color.MaterialColors
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.lite.CourseWizardActivity
import org.ole.planet.myplanet.lite.auth.AuthDependencies
import org.ole.planet.myplanet.lite.dashboard.DashboardCoursesRepository
import org.ole.planet.myplanet.lite.dashboard.DashboardCoursesRepository.CourseDocument
import org.ole.planet.myplanet.lite.dashboard.DashboardPostImageLoader
import org.ole.planet.myplanet.lite.dashboard.DashboardServerPreferences
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyDocument
import org.ole.planet.myplanet.lite.dashboard.DashboardTeamSelectionPreferences
import org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore
import org.ole.planet.myplanet.lite.profile.StoredCredentials

class DashboardCoursePageFragment : Fragment(R.layout.fragment_dashboard_courses_page) {

    private val coursesRepository = DashboardCoursesRepository()
    private var baseUrl: String? = null
    private var credentials: StoredCredentials? = null
    private var currentTeamId: String? = null
    private var myCourseIds: List<String> = emptyList()
    private var needsMyCourseIdsRefresh: Boolean = false
    private var isPaging = false
    private var hasMorePages = true
    private var currentSkip = 0
    private val pageSize = 20
    private var tabPosition: Int = 0
    private lateinit var recyclerView: RecyclerView
    private lateinit var refreshLayout: SwipeRefreshLayout
    private lateinit var emptyView: TextView
    private lateinit var loadingOverlay: View
    private lateinit var adapter: CourseAdapter
    private var courseImageLoader: DashboardPostImageLoader? = null
    private var isCourseImageLoaderLoading = false
    private var courseCategories: List<CourseCategory> = emptyList()
    private var selectedCategoryId: String? = null
    private val tagCourseIdsByTag = mutableMapOf<String, Set<String>>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tabPosition = requireArguments().getInt(ARG_TAB_POSITION)

        baseUrl = DashboardServerPreferences.getServerBaseUrl(requireContext())
        credentials = ProfileCredentialsStore.getStoredCredentials(requireContext())

        recyclerView = view.findViewById(R.id.dashboardCoursesRecycler)
        emptyView = view.findViewById(R.id.dashboardCoursesEmptyView)
        refreshLayout = view.findViewById(R.id.dashboardCoursesRefresh)
        loadingOverlay = view.findViewById(R.id.dashboardCoursesLoadingOverlay)
        courseCategories = listOf(
            CourseCategory(id = null, name = getString(R.string.dashboard_courses_category_all))
        )
        adapter = CourseAdapter(
            showProgress = tabPosition == 0,
            imageLoaderProvider = { courseImageLoader },
            ensureImageLoader = { ensureCourseImageLoader() },
            categoriesProvider = { courseCategories }
        )
        adapter.onCategorySelected = { categoryId ->
            selectedCategoryId = categoryId
            if (categoryId.isNullOrBlank()) {
                adapter.updateTagFilter(null)
            } else {
                loadTagLinks(categoryId)
            }
        }
        val spanCount = 2
        val layoutManager = GridLayoutManager(requireContext(), spanCount)
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return if (adapter.isHeader(position)) spanCount else 1
            }
        }

        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter
        recyclerView.addItemDecoration(SpacingDecoration(spanCount))

        adapter.onCourseClick = { course ->
            val isEnrolledCourse = tabPosition == 0 || myCourseIds.contains(course.id)
            DashboardCourseDetailsBottomSheet.show(
                childFragmentManager,
                course,
                isEnrolled = isEnrolledCourse,
                onJoinCourse = if (isEnrolledCourse) null else {
                    { handleJoinCourse(course) }
                },
                onLeaveCourse = if (isEnrolledCourse) {
                    { handleLeaveCourse(course) }
                } else {
                    null
                },
                onOpenCourse = if (isEnrolledCourse) {
                    { openCourseWizard(course) }
                } else {
                    null
                }
            )
        }

        refreshLayout.setOnRefreshListener {
            refreshLayout.isRefreshing = false
            showLoadingOverlay(true)
            if (tabPosition == 0) {
                refreshUserCourses(adapter, refreshLayout)
            } else if (tabPosition == 1) {
                refreshAllCourses(adapter, refreshLayout)
            } else {
                refreshTeamCourses(adapter, refreshLayout, forceReload = true)
            }
        }

        registerJoinListener()

        maybeHandlePendingJoinRefresh()

        showLoadingOverlay(true)
        if (tabPosition == 0) {
            refreshUserCourses(adapter, refreshLayout)
        } else if (tabPosition == 1) {
            refreshAllCourses(adapter, refreshLayout)
        } else {
            refreshTeamCourses(adapter, refreshLayout, forceReload = true)
        }

        loadCourseCategories()

        if (tabPosition == 1) {
            recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    if (dy <= 0 || isPaging || !hasMorePages) return

                    val manager = recyclerView.layoutManager as? GridLayoutManager ?: return
                    val lastVisible = manager.findLastVisibleItemPosition()
                    if (lastVisible >= adapter.itemCount - 4) {
                        loadNextCoursesPage(adapter, null)
                    }
                }
            })
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
            adapter.notifyDataSetChanged()
        }
    }

    private fun loadCourseCategories() {
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

    private fun loadTagLinks(tagId: String) {
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

    private fun registerJoinListener() {
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

    override fun onResume() {
        super.onResume()
        if (tabPosition == 2 && this::adapter.isInitialized) {
            refreshTeamCourses(adapter, refreshLayout, forceReload = false)
        }

        maybeHandlePendingJoinRefresh()
    }

    private fun refreshUserCourses(
        adapter: CourseAdapter,
        refreshLayout: SwipeRefreshLayout
    ) {
        showLoadingOverlay(true)
        viewLifecycleOwner.lifecycleScope.launch {
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
                .map { toCourseItem(it, courseProgress[it.id], logProgress = true) }
            adapter.submitCourses(mapped)
            refreshLayout.isRefreshing = false
            showLoadingOverlay(false)
        }
    }

    private fun refreshAllCourses(
        adapter: CourseAdapter,
        refreshLayout: SwipeRefreshLayout
    ) {
        resetPagingState()
        adapter.submitCourses(emptyList())
        showLoadingOverlay(true)
        loadNextCoursesPage(adapter, refreshLayout)
    }

    private fun refreshTeamCourses(
        adapter: CourseAdapter,
        refreshLayout: SwipeRefreshLayout,
        forceReload: Boolean
    ) {
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
            val base = baseUrl
            val creds = credentials
            if (base.isNullOrBlank() || creds == null) {
                handleMissingCredentials(adapter, refreshLayout)
                return@launch
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
                return@launch
            }
            val mapped = courses
                .filter { !it.id.isNullOrBlank() }
                .distinctBy { it.id }
                .map { toCourseItem(it, null, logProgress = false) }
            adapter.submitCourses(mapped)
            refreshLayout.isRefreshing = false
            showLoadingOverlay(false)
        }
    }

    private fun handleJoinCourse(course: CourseItem) {
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

    private fun handleLeaveCourse(course: CourseItem) {
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

    private fun handleMissingCredentials(
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

    private fun redirectToLogin() {
        val currentActivity = activity ?: return
        val intent = Intent(currentActivity, MyPlanetLite::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        currentActivity.finish()
    }

    private fun loadNextCoursesPage(
        adapter: CourseAdapter,
        refreshLayout: SwipeRefreshLayout?
    ) {
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
                .map { toCourseItem(it, null, logProgress = false) }
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

    private fun showEmptyState(message: String) {
        emptyView.text = message
        emptyView.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
    }

    private fun hideEmptyState() {
        emptyView.visibility = View.GONE
        recyclerView.visibility = View.VISIBLE
    }

    private fun showLoadingOverlay(show: Boolean) {
        val parentOverlay = parentFragment?.view?.findViewById<View>(R.id.dashboardCoursesGlobalLoadingOverlay)
        parentOverlay?.visibility = if (show) View.VISIBLE else View.GONE
        if (parentOverlay == null) {
            loadingOverlay.visibility = if (show) View.VISIBLE else View.GONE
        } else {
            loadingOverlay.visibility = View.GONE
        }
    }

    private fun resetPagingState() {
        currentSkip = 0
        hasMorePages = true
        isPaging = false
    }

    private fun openCourseWizard(course: CourseItem) {
        if (course.steps.isEmpty()) {
            Toast.makeText(
                requireContext(),
                getString(R.string.dashboard_courses_loading_error),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val startStep = course.currentStep?.coerceIn(0, course.steps.lastIndex) ?: 0
        CourseWizardActivity.start(requireContext(), course.id, course.title, course.steps, startStep)
    }

    private suspend fun ensureUserCourseIds(): List<String> {
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

    private fun toCourseItem(
        document: CourseDocument,
        stepNum: Int?,
        logProgress: Boolean
    ): CourseItem {
        val steps = document.steps.mapNotNull { step ->
            val title = step.stepTitle?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val mediaTypes = buildList {
                step.resources
                    ?.mapNotNull { resource -> mapMediaType(resource.mediaType) }
                    ?.forEach { add(it) }
                if (step.survey != null) {
                    add("survey")
                }
                if (step.exam != null) {
                    add("exam")
                }
            }
            val resources = step.resources.orEmpty().flatMap { resource ->
                val resourceId = resource.id?.takeIf { it.isNotBlank() } ?: return@flatMap emptyList()
                val filenames = when {
                    resource.attachments.isNotEmpty() -> resource.attachments.keys
                    !resource.filename.isNullOrBlank() -> listOf(resource.filename)
                    else -> emptyList()
                }
                val mediaType = mapMediaType(resource.mediaType)
                    ?: resource.mediaType?.lowercase()?.trim().orEmpty()
                filenames.map { name ->
                    CourseItem.LessonResource(
                        id = resourceId,
                        filename = name,
                        mediaType = mediaType
                    )
                }
            }
            CourseItem.LessonStep(
                title = title,
                description = step.description.orEmpty(),
                mediaTypes = mediaTypes,
                resources = resources,
                survey = step.survey,
                exam = step.exam
            )
        }
        val random = Random(document.id.orEmpty().hashCode())
        val completedSteps = if (steps.isNotEmpty() && stepNum != null) {
            stepNum.coerceAtLeast(0).coerceAtMost(steps.size)
        } else {
            null
        }
        val progressPercent = if (steps.isNotEmpty() && completedSteps != null) {
            (completedSteps * 100.0 / steps.size)
                .toInt()
                .coerceIn(0, 100)
        } else if (steps.isNotEmpty()) {
            random.nextInt(15, 95)
        } else {
            random.nextInt(15, 95)
        }
        return CourseItem(
            id = document.id.orEmpty(),
            title = document.courseTitle?.takeIf { it.isNotBlank() }
                ?: getString(R.string.dashboard_courses_title),
            description = document.description.orEmpty(),
            coverPath = document.cover?.takeIf { it.isNotBlank() },
            steps = steps,
            rating = random.nextDouble(3.5, 5.0),
            progressPercent = progressPercent,
            currentStep = completedSteps?.minus(1)?.coerceIn(0, steps.lastIndex)
        )
    }

    private fun mapMediaType(raw: String?): String? {
        val normalized = raw?.lowercase()?.trim().orEmpty()
        if (normalized.isBlank()) return null
        return when {
            normalized.contains("video") -> "video"
            normalized.contains("pdf") -> "pdf"
            normalized.contains("image") -> "image"
            normalized.contains("audio") -> "audio"
            else -> normalized
        }
    }

    data class CourseItem(
        val id: String,
        val title: String,
        val description: String,
        val coverPath: String?,
        val steps: List<LessonStep>,
        val rating: Double,
        val progressPercent: Int,
        val currentStep: Int?
    ) {
        val lessonCount: Int
            get() = steps.size

        data class LessonStep(
            val title: String,
            val description: String,
            val mediaTypes: List<String>,
            val resources: List<LessonResource> = emptyList(),
            val survey: SurveyDocument? = null,
            val exam: SurveyDocument? = null
        ) : java.io.Serializable

        data class LessonResource(
            val id: String,
            val filename: String,
            val mediaType: String
        ) : java.io.Serializable
    }

    data class CourseCategory(
        val id: String?,
        val name: String
    )

    private class CourseAdapter(
        private val showProgress: Boolean,
        private val imageLoaderProvider: () -> DashboardPostImageLoader?,
        private val ensureImageLoader: () -> Unit,
        private val categoriesProvider: () -> List<CourseCategory>
    ) :
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val items = mutableListOf<CourseItem>()
        private val displayedItems = mutableListOf<CourseItem>()
        private var searchQuery: String = ""
        private var selectedCategory = 0
        private var activeTagCourseIds: Set<String>? = null
        var onCourseClick: ((CourseItem) -> Unit)? = null
        var onCategorySelected: ((String?) -> Unit)? = null

        override fun getItemViewType(position: Int): Int {
            return if (position == 0) VIEW_TYPE_HEADER else VIEW_TYPE_ITEM
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = android.view.LayoutInflater.from(parent.context)
            return if (viewType == VIEW_TYPE_HEADER) {
                val view = inflater.inflate(R.layout.item_dashboard_course_header, parent, false)
                HeaderViewHolder(view)
            } else {
                val view = inflater.inflate(R.layout.item_dashboard_course_card, parent, false)
                CourseViewHolder(view)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (holder is HeaderViewHolder) {
                holder.onCategorySelected = onCategorySelected
                holder.bind(selectedCategory, categoriesProvider(), searchQuery, { index ->
                    selectedCategory = index
                    notifyItemChanged(0)
                }) { query ->
                    updateSearchQuery(query)
                }
            } else if (holder is CourseViewHolder) {
                holder.bind(displayedItems[position - 1])
            }
        }

        override fun getItemCount(): Int = displayedItems.size + 1

        fun updateCategories() {
            if (selectedCategory >= categoriesProvider().size) {
                selectedCategory = 0
            }
            notifyItemChanged(0)
        }

        fun updateTagFilter(courseIds: Set<String>?) {
            activeTagCourseIds = courseIds
            applyFilter()
        }

        fun submitCourses(newItems: List<CourseItem>) {
            items.clear()
            items.addAll(newItems.distinctBy { it.id })
            displayedItems.clear()
            displayedItems.addAll(items)
            notifyDataSetChanged()
        }

        fun appendCourses(newItems: List<CourseItem>) {
            if (newItems.isEmpty()) return
            val unique = newItems.filter { newItem -> items.none { it.id == newItem.id } }
                .distinctBy { it.id }
            if (unique.isEmpty()) return
            items.addAll(unique)
            if (searchQuery.isBlank()) {
                val start = displayedItems.size
                displayedItems.addAll(unique)
                if (unique.isNotEmpty()) {
                    notifyItemRangeInserted(start + 1, unique.size)
                }
            } else {
                applyFilter()
            }
        }

        fun isHeader(position: Int) = position == 0

        private fun updateSearchQuery(query: String) {
            val normalized = query.trim()
            if (normalized == searchQuery) return
            searchQuery = normalized
            applyFilter()
        }

        private fun applyFilter() {
            val filtered = if (searchQuery.isBlank()) {
                items
            } else {
                items.filter { item ->
                    item.title.contains(searchQuery, ignoreCase = true)
                }
            }
            val tagFiltered = activeTagCourseIds?.let { ids ->
                filtered.filter { ids.contains(it.id) }
            } ?: filtered
            val oldSize = displayedItems.size
            displayedItems.clear()
            displayedItems.addAll(tagFiltered)
            val newSize = displayedItems.size
            when {
                oldSize == newSize -> {
                    if (newSize > 0) {
                        notifyItemRangeChanged(1, newSize)
                    }
                }
                else -> {
                    if (oldSize > 0) {
                        notifyItemRangeRemoved(1, oldSize)
                    }
                    if (newSize > 0) {
                        notifyItemRangeInserted(1, newSize)
                    }
                }
            }
        }

        class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val chipGroup: ChipGroup = itemView.findViewById(R.id.dashboardCoursesCategories)
            private val searchInput: com.google.android.material.textfield.TextInputEditText =
                itemView.findViewById(R.id.dashboardCoursesSearch)
            private var watcher: android.text.TextWatcher? = null
            private var chipListener: ChipGroup.OnCheckedStateChangeListener? = null
            var onCategorySelected: ((String?) -> Unit)? = null

            fun bind(
                selectedIndex: Int,
                categories: List<CourseCategory>,
                searchText: String,
                onSelectionChanged: (Int) -> Unit,
                onSearchChanged: (String) -> Unit
            ) {
                chipGroup.setOnCheckedStateChangeListener(null)
                chipGroup.removeAllViews()
                val chipIds = categories.mapIndexed { index, category ->
                    val chip = Chip(itemView.context).apply {
                        id = View.generateViewId()
                        text = category.name
                        isCheckable = true
                        isChecked = index == selectedIndex
                        setEnsureMinTouchTargetSize(false)
                        layoutParams = ChipGroup.LayoutParams(
                            ChipGroup.LayoutParams.WRAP_CONTENT,
                            ChipGroup.LayoutParams.WRAP_CONTENT
                        ).apply {
                            marginEnd = itemView.resources.getDimensionPixelSize(R.dimen.dashboard_courses_chip_spacing)
                        }
                    }
                    chipGroup.addView(chip)
                    chip.id
                }

                watcher?.let { searchInput.removeTextChangedListener(it) }
                searchInput.setText(searchText)
                searchInput.setSelection(searchInput.text?.length ?: 0)
                watcher = object : android.text.TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                    override fun afterTextChanged(s: android.text.Editable?) {
                        onSearchChanged(s?.toString().orEmpty())
                    }
                }
                searchInput.addTextChangedListener(watcher)

                searchInput.setOnEditorActionListener { _, actionId, event ->
                    val isEnterKey = event?.keyCode == android.view.KeyEvent.KEYCODE_ENTER &&
                        event.action == android.view.KeyEvent.ACTION_UP
                    if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH || isEnterKey) {
                        onSearchChanged(searchInput.text?.toString().orEmpty())
                        hideKeyboard()
                        true
                    } else {
                        false
                    }
                }

                chipListener = ChipGroup.OnCheckedStateChangeListener { _, checkedIds ->
                    val checkedId = checkedIds.firstOrNull() ?: return@OnCheckedStateChangeListener
                    val newIndex = chipIds.indexOf(checkedId)
                    if (newIndex >= 0 && newIndex != selectedIndex) {
                        onSelectionChanged(newIndex)
                        onCategorySelected?.invoke(categories.getOrNull(newIndex)?.id)
                    }
                }
                chipGroup.setOnCheckedStateChangeListener(chipListener)
            }

            private fun hideKeyboard() {
                val imm = itemView.context.getSystemService(InputMethodManager::class.java)
                imm?.hideSoftInputFromWindow(searchInput.windowToken, 0)
                searchInput.clearFocus()
            }
        }

        inner class CourseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val imageView: ImageView = itemView.findViewById(R.id.dashboardCourseImage)
            private val titleView: TextView = itemView.findViewById(R.id.dashboardCourseTitle)
            private val descriptionView: TextView = itemView.findViewById(R.id.dashboardCourseDescription)
            private val lessonsView: TextView = itemView.findViewById(R.id.dashboardCourseLessons)
            private val ratingView: TextView = itemView.findViewById(R.id.dashboardCourseRating)
            private val progressView: TextView = itemView.findViewById(R.id.dashboardCourseProgress)
            private val progressContainer: View =
                itemView.findViewById(R.id.dashboardCourseProgressContainer)
            private val progressIndicator: com.google.android.material.progressindicator.LinearProgressIndicator =
                itemView.findViewById(R.id.dashboardCourseProgressIndicator)
            private val defaultPadding = intArrayOf(
                imageView.paddingLeft,
                imageView.paddingTop,
                imageView.paddingRight,
                imageView.paddingBottom
            )
            private val defaultScaleType = imageView.scaleType

            fun bind(course: CourseItem) {
                fun showDefaultIcon() {
                    imageView.visibility = View.VISIBLE
                    imageView.setImageResource(R.drawable.ic_courses_24)
                    val primary = MaterialColors.getColor(itemView, androidx.appcompat.R.attr.colorPrimary)
                    imageView.imageTintList = android.content.res.ColorStateList.valueOf(primary)
                    imageView.setPadding(
                        defaultPadding[0],
                        defaultPadding[1],
                        defaultPadding[2],
                        defaultPadding[3]
                    )
                    imageView.scaleType = defaultScaleType
                }

                val coverPath = course.coverPath
                val loader = imageLoaderProvider()
                if (!coverPath.isNullOrBlank()) {
                    imageView.setPadding(0, 0, 0, 0)
                    imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                    imageView.imageTintList = null
                    if (loader != null) {
                        loader.bind(imageView, coverPath) { success ->
                            if (!success) {
                                showDefaultIcon()
                            }
                        }
                    } else {
                        imageView.setImageDrawable(null)
                        ensureImageLoader()
                    }
                } else {
                    showDefaultIcon()
                }

                titleView.text = course.title
                descriptionView.visibility = View.GONE
                lessonsView.text = itemView.context.getString(
                    R.string.dashboard_courses_lessons_format,
                    course.lessonCount
                )
                ratingView.visibility = View.VISIBLE
                ratingView.text = itemView.context.getString(
                    R.string.dashboard_courses_rating_format,
                    course.rating
                )
                if (showProgress) {
                    progressContainer.visibility = View.VISIBLE
                    progressView.visibility = View.VISIBLE
                    progressView.text = itemView.context.getString(
                        R.string.dashboard_course_progress_value,
                        course.progressPercent
                    )
                    progressIndicator.visibility = View.VISIBLE
                    progressIndicator.progress = course.progressPercent
                } else {
                    progressContainer.visibility = View.GONE
                    progressView.visibility = View.GONE
                    progressIndicator.visibility = View.GONE
                }

                itemView.setOnClickListener {
                    onCourseClick?.invoke(course)
                }
            }
        }

        companion object {
            private const val VIEW_TYPE_HEADER = 0
            private const val VIEW_TYPE_ITEM = 1
        }
    }

    companion object {
        private const val ARG_TAB_POSITION = "tab_position"
        private const val RESULT_JOINED_COURSE = "dashboard_course_joined"
        private const val KEY_JOINED_COURSE_ID = "joined_course_id"
        private const val KEY_LEFT_COURSE = "left_course"

        private val pendingRefreshTabs = mutableSetOf<Int>()

        fun newInstance(position: Int) = DashboardCoursePageFragment().apply {
            arguments = Bundle().apply {
                putInt(ARG_TAB_POSITION, position)
            }
        }
    }

    private fun maybeHandlePendingJoinRefresh() {
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

    private class SpacingDecoration(private val spanCount: Int) : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(
            outRect: android.graphics.Rect,
            view: View,
            parent: RecyclerView,
            state: RecyclerView.State
        ) {
            val position = parent.getChildAdapterPosition(view)
            if (position == RecyclerView.NO_POSITION || position == 0) {
                outRect.set(0, 0, 0, 0)
                return
            }

            val column = (position - 1) % spanCount
            val spacing = view.resources.getDimensionPixelSize(R.dimen.dashboard_card_spacing)
            val halfSpacing = spacing / 2

            outRect.top = spacing
            outRect.left = if (column == 0) 0 else halfSpacing
            outRect.right = if (column == spanCount - 1) 0 else halfSpacing
            outRect.bottom = 0
        }
    }
}
