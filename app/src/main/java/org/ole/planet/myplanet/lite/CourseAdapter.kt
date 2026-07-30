package org.ole.planet.myplanet.lite

import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import org.ole.planet.myplanet.lite.dashboard.DashboardPostImageLoader

class CourseAdapter(
    private val showProgress: Boolean,
    private val showDownloadButton: Boolean,
    private val imageLoaderProvider: () -> DashboardPostImageLoader?,
    private val ensureImageLoader: () -> Unit,
    private val categoriesProvider: () -> List<CourseCategory>
) : ListAdapter<CourseAdapter.CourseListItem, RecyclerView.ViewHolder>(CourseDiffCallback()) {

    sealed class CourseListItem {
        data class Header(
            val categories: List<CourseCategory>,
            val selectedIndex: Int,
            val searchQuery: String
        ) : CourseListItem()

        data class CourseItemWrapper(
            val course: CourseItem,
            val isDownloaded: Boolean,
            val downloadProgress: DownloadProgress?
        ) : CourseListItem()
    }

    data class DownloadProgress(val completed: Int, val total: Int)

    private val items = mutableListOf<CourseItem>()
    private val downloadedCourseIds = mutableSetOf<String>()
    private val downloadProgressByCourse = mutableMapOf<String, DownloadProgress>()
    private var searchQuery: String = ""
    private var selectedCategory = 0
    private var activeTagCourseIds: Set<String>? = null
    private var pendingImageRefresh = false
    var onCourseClick: ((CourseItem) -> Unit)? = null
    var onCourseDownloadClick: ((CourseItem) -> Unit)? = null
    var onCourseDeleteClick: ((CourseItem) -> Unit)? = null
    var onCategorySelected: ((String?) -> Unit)? = null

    init {
        submitList(listOf(CourseListItem.Header(categoriesProvider(), selectedCategory, searchQuery)))
    }

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position) is CourseListItem.Header) VIEW_TYPE_HEADER else VIEW_TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_HEADER) {
            HeaderViewHolder(inflater.inflate(R.layout.item_dashboard_course_header, parent, false))
        } else {
            CourseViewHolder(inflater.inflate(R.layout.item_dashboard_course_card, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        if (holder is HeaderViewHolder && item is CourseListItem.Header) {
            holder.onCategorySelected = onCategorySelected
            holder.bind(item.selectedIndex, item.categories, item.searchQuery, { index ->
                selectedCategory = index
                applyFilter()
            }) { query ->
                updateSearchQuery(query)
            }
        } else if (holder is CourseViewHolder && item is CourseListItem.CourseItemWrapper) {
            holder.bind(item)
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.contains(PROGRESS_UPDATE_PAYLOAD) || payloads.contains(IMAGE_UPDATE_PAYLOAD)) {
            val item = getItem(position)
            if (holder is CourseViewHolder && item is CourseListItem.CourseItemWrapper) {
                if (payloads.contains(PROGRESS_UPDATE_PAYLOAD)) {
                    holder.bindDownloadState(item)
                }
                if (payloads.contains(IMAGE_UPDATE_PAYLOAD)) {
                    holder.bindImage(item.course)
                }
                return
            }
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    fun updateCategories() {
        if (selectedCategory >= categoriesProvider().size) {
            selectedCategory = 0
        }
        applyFilter()
    }

    fun updateTagFilter(courseIds: Set<String>?) {
        activeTagCourseIds = courseIds
        applyFilter()
    }

    fun updateDownloadedCourses(courseIds: Set<String>) {
        downloadedCourseIds.clear()
        downloadedCourseIds.addAll(courseIds)
        applyFilter()
    }

    fun updateDownloadProgress(courseId: String, completed: Int?, total: Int?) {
        if (completed == null || total == null || total <= 0) {
            downloadProgressByCourse.remove(courseId)
        } else {
            downloadProgressByCourse[courseId] = DownloadProgress(
                completed = completed.coerceAtLeast(0),
                total = total.coerceAtLeast(1)
            )
        }
        applyFilter()
    }

    fun updateCourseProgress(courseId: String, progressPercent: Int, currentStep: Int) {
        val itemIndex = items.indexOfFirst { it.id == courseId }
        if (itemIndex >= 0) {
            items[itemIndex] = items[itemIndex].copy(
                progressPercent = progressPercent.coerceIn(0, 100),
                currentStep = currentStep.coerceAtLeast(0)
            )
            applyFilter()
        }
    }

    fun submitCourses(
        newItems: List<CourseItem>,
        forceImageRefresh: Boolean = false,
    ) {
        items.clear()
        items.addAll(newItems.distinctBy { it.id })
        pendingImageRefresh = pendingImageRefresh || forceImageRefresh
        applyFilter()
    }

    fun appendCourses(newItems: List<CourseItem>) {
        if (newItems.isEmpty()) return
        val unique = newItems.filter { newItem -> items.none { it.id == newItem.id } }
            .distinctBy { it.id }
        if (unique.isEmpty()) return
        items.addAll(unique)
        applyFilter()
    }

    fun isHeader(position: Int) = getItem(position) is CourseListItem.Header

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
            items.filter { item -> item.title.contains(searchQuery, ignoreCase = true) }
        }
        val tagFiltered = activeTagCourseIds?.let { ids ->
            filtered.filter { ids.contains(it.id) }
        } ?: filtered

        val newList = mutableListOf<CourseListItem>()
        newList.add(CourseListItem.Header(categoriesProvider(), selectedCategory, searchQuery))
        newList.addAll(tagFiltered.map {
            CourseListItem.CourseItemWrapper(
                course = it,
                isDownloaded = downloadedCourseIds.contains(it.id),
                downloadProgress = downloadProgressByCourse[it.id]
            )
        })
        val shouldRefreshImages = pendingImageRefresh
        submitList(newList) {
            if (shouldRefreshImages && pendingImageRefresh) {
                pendingImageRefresh = false
                notifyItemRangeChanged(1, (itemCount - 1).coerceAtLeast(0), IMAGE_UPDATE_PAYLOAD)
            }
        }
    }

    class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val chipGroup: ChipGroup = itemView.findViewById(R.id.dashboardCoursesCategories)
        private val searchInput: TextInputEditText = itemView.findViewById(R.id.dashboardCoursesSearch)
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
                        marginEnd = itemView.resources.getDimensionPixelSize(
                            R.dimen.dashboard_courses_chip_spacing
                        )
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
                val isEnterKey = event?.keyCode == KeyEvent.KEYCODE_ENTER &&
                    event.action == KeyEvent.ACTION_UP
                if (actionId == EditorInfo.IME_ACTION_SEARCH || isEnterKey) {
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
        private val progressContainer: View = itemView.findViewById(R.id.dashboardCourseProgressContainer)
        private val downloadButton: FloatingActionButton =
            itemView.findViewById(R.id.dashboardCourseDownloadButton)
        private val downloadProgressContainer: View =
            itemView.findViewById(R.id.dashboardCourseDownloadProgressContainer)
        private val downloadProgressLabel: TextView =
            itemView.findViewById(R.id.dashboardCourseDownloadProgressLabel)
        private val downloadProgressIndicator: LinearProgressIndicator =
            itemView.findViewById(R.id.dashboardCourseDownloadProgressIndicator)
        private val progressIndicator: LinearProgressIndicator =
            itemView.findViewById(R.id.dashboardCourseProgressIndicator)

        fun bind(item: CourseListItem.CourseItemWrapper) {
            bindImage(item.course)
            bindText(item.course)
            bindProgress(item.course)
            bindDownloadState(item)
            itemView.setOnClickListener {
                onCourseClick?.invoke(item.course)
            }
        }

        fun bindDownloadState(item: CourseListItem.CourseItemWrapper) {
            val course = item.course
            downloadButton.visibility = if (showDownloadButton) View.VISIBLE else View.GONE
            val isDownloaded = item.isDownloaded
            val progress = item.downloadProgress
            downloadButton.setImageResource(
                if (isDownloaded) R.drawable.ic_dashboard_delete_24 else R.drawable.ic_survey_download_24
            )
            downloadButton.contentDescription = itemView.context.getString(
                if (isDownloaded) R.string.dashboard_post_action_delete else R.string.dashboard_survey_download
            )
            downloadButton.isEnabled = progress == null
            downloadButton.alpha = if (downloadButton.isEnabled) 1f else 0.5f
            downloadButton.setOnClickListener {
                if (isDownloaded) {
                    onCourseDeleteClick?.invoke(course)
                } else {
                    onCourseDownloadClick?.invoke(course)
                }
            }
            if (progress != null && !isDownloaded) {
                downloadProgressContainer.visibility = View.VISIBLE
                val percent = ((progress.completed * 100f) / progress.total).toInt().coerceIn(0, 100)
                downloadProgressIndicator.progress = percent
                downloadProgressLabel.text = "${progress.completed}/${progress.total} ($percent%)"
            } else {
                downloadProgressContainer.visibility = View.GONE
            }
        }

        fun bindImage(course: CourseItem) {
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
        }

        private fun bindText(course: CourseItem) {
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
        }

        private fun bindProgress(course: CourseItem) {
            if (showProgress) {
                progressContainer.visibility = View.VISIBLE
                progressView.visibility = View.VISIBLE
                progressView.text = if (course.progressPercent >= 100) {
                    itemView.context.getString(R.string.dashboard_course_completed_progress)
                } else {
                    itemView.context.getString(
                        R.string.dashboard_course_progress_value,
                        course.progressPercent
                    )
                }
                progressIndicator.visibility = View.VISIBLE
                progressIndicator.progress = course.progressPercent
            } else {
                progressContainer.visibility = View.GONE
                progressView.visibility = View.GONE
                progressIndicator.visibility = View.GONE
            }
        }

        private fun showDefaultIcon() {
            imageView.visibility = View.VISIBLE
            imageView.setImageResource(R.drawable.no_image)
            imageView.imageTintList = null
            imageView.setPadding(0, 0, 0, 0)
            imageView.scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
    }

    private class CourseDiffCallback : DiffUtil.ItemCallback<CourseListItem>() {
        override fun areItemsTheSame(oldItem: CourseListItem, newItem: CourseListItem): Boolean {
            return when {
                oldItem is CourseListItem.Header && newItem is CourseListItem.Header -> true
                oldItem is CourseListItem.CourseItemWrapper && newItem is CourseListItem.CourseItemWrapper -> {
                    oldItem.course.id == newItem.course.id
                }
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: CourseListItem, newItem: CourseListItem): Boolean {
            return oldItem == newItem
        }

        override fun getChangePayload(oldItem: CourseListItem, newItem: CourseListItem): Any? {
            if (oldItem is CourseListItem.CourseItemWrapper && newItem is CourseListItem.CourseItemWrapper) {
                if (oldItem.course == newItem.course && oldItem.isDownloaded == newItem.isDownloaded) {
                    return PROGRESS_UPDATE_PAYLOAD
                }
            }
            return super.getChangePayload(oldItem, newItem)
        }
    }

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_ITEM = 1
        private const val PROGRESS_UPDATE_PAYLOAD = "PROGRESS_UPDATE"
        const val IMAGE_UPDATE_PAYLOAD = "IMAGE_UPDATE"
    }
}
