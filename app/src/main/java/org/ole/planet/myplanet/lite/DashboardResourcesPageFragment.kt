package org.ole.planet.myplanet.lite

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.database.Cursor
import android.view.Gravity
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.text.format.Formatter
import android.view.animation.LinearInterpolator
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ContextThemeWrapper
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.widget.PopupMenu
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.core.content.edit
import androidx.core.view.MenuCompat
import androidx.core.view.isVisible
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.os.BundleCompat
import androidx.core.graphics.scale
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.MimeTypes
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.effect.Presentation
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.slider.RangeSlider
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import org.json.JSONArray
import org.json.JSONObject
import org.ole.planet.myplanet.lite.auth.AuthDependencies
import org.ole.planet.myplanet.lite.dashboard.DashboardImagePreviewActivity
import org.ole.planet.myplanet.lite.dashboard.DashboardResourcesRepository
import org.ole.planet.myplanet.lite.dashboard.DashboardServerPreferences
import org.ole.planet.myplanet.lite.dashboard.DashboardTeamSelectionPreferences
import org.ole.planet.myplanet.lite.databinding.ItemDashboardResourceExplorerBinding
import org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore
import org.ole.planet.myplanet.lite.util.AudioWaveformView
import org.ole.planet.myplanet.lite.util.enableDrag

class DashboardResourcesPageFragment : Fragment(R.layout.fragment_dashboard_resources_page) {
    companion object {
        private const val ARG_IS_TEAM_RESOURCES = "arg_is_team_resources"
        private const val MAIN_RESOURCES_PAGE_SIZE = 1000
        private const val DOUBLE_TAP_WINDOW_MS = 350L
        private const val DOWNLOADED_PREFS = "dashboard_resources_downloads"
        private const val KEY_DOWNLOADED_RESOURCES = "downloaded_resources_json"

        fun newInstance(isTeamResources: Boolean): DashboardResourcesPageFragment {
            return DashboardResourcesPageFragment().apply {
                arguments = Bundle().apply {
                    putBoolean(ARG_IS_TEAM_RESOURCES, isTeamResources)
                }
            }
        }
    }

    private var isTeamResourcesTab: Boolean = false
    private var contentView: LinearLayout? = null
    private var emptyView: TextView? = null
    private var resourcesList: RecyclerView? = null
    private var swipeRefreshLayout: SwipeRefreshLayout? = null
    private var uploadLoadingView: View? = null
    private var addResourceFab: FloatingActionButton? = null
    private val repository = DashboardResourcesRepository()
    private val mainResourcesItems = mutableListOf<ResourceUi>()
    private val teamResourcesItems = mutableListOf<ResourceUi>()
    private var mainResourcesSkip = 0
    private var isLoadingMainResources = false
    private var hasMoreMainResources = true
    private var hasLoadedMainResources = false
    private var hasLoadedTeamResources = false
    private var isLoadingTeamResources = false
    private var sessionCookie: String? = null
    private var baseUrl: String? = null
    private var searchQuery: String = ""
    private var selectedMediaType: String? = null
    private var isSortDescending: Boolean = false
    private var selectedSortBy: ResourceSortBy = ResourceSortBy.NAME
    private var latestPhotoUri: Uri? = null
    private var pendingPermissionAction: ResourceMenuAction? = null
    private var isFabMenuSpinActive: Boolean = false
    private var spinningFab: View? = null
    private var latestVideoUri: Uri? = null
    private var mediaRecorder: MediaRecorder? = null
    private var currentAudioFile: File? = null
    private var recordingStartTime: Long = 0L
    private val recordingTimerHandler = Handler(Looper.getMainLooper())
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        val action = pendingPermissionAction ?: return@registerForActivityResult
        pendingPermissionAction = null
        val hasAllPermissions = requiredPermissionsFor(action).all { permission ->
            ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED
        }
        if (hasAllPermissions) {
            executeResourceMenuAction(action)
        } else {
            Toast.makeText(requireContext(), getString(R.string.dashboard_resources_permission_required), Toast.LENGTH_SHORT).show()
        }
    }
    private val pickPdfLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            return@registerForActivityResult
        }
        val resolver = requireContext().contentResolver
        runCatching {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        showPdfMetadataPopup(uri)
    }
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            return@registerForActivityResult
        }
        val resolver = requireContext().contentResolver
        runCatching {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        showImageMetadataPopup(uri)
    }
    private val pickVideoLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            return@registerForActivityResult
        }
        val resolver = requireContext().contentResolver
        runCatching {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        showVideoMetadataPopup(uri)
    }
    private val pickAudioLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            return@registerForActivityResult
        }
        val resolver = requireContext().contentResolver
        runCatching {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        showAudioMetadataPopup(uri)
    }

    private val takePhotoLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = latestPhotoUri
        if (success && uri != null) {
            showImageMetadataPopup(uri)
        }
    }

    private val takeVideoLauncher = registerForActivityResult(
        ActivityResultContracts.CaptureVideo()
    ) { success ->
        val uri = latestVideoUri
        if (success && uri != null) {
            showVideoMetadataPopup(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isTeamResourcesTab = arguments?.getBoolean(ARG_IS_TEAM_RESOURCES, false) ?: false
        if (savedInstanceState != null) {
            latestPhotoUri = BundleCompat.getParcelable(savedInstanceState, "latestPhotoUri", Uri::class.java)
            latestVideoUri = BundleCompat.getParcelable(savedInstanceState, "latestVideoUri", Uri::class.java)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable("latestPhotoUri", latestPhotoUri)
        outState.putParcelable("latestVideoUri", latestVideoUri)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val searchInput: TextInputEditText = view.findViewById(R.id.resourcesSearchInput)
        val typeSpinner: Spinner = view.findViewById(R.id.resourcesTypeSpinner)
        val sortBySpinner: Spinner = view.findViewById(R.id.resourcesSortBySpinner)
        val sortOrderToggle: ImageButton = view.findViewById(R.id.resourcesSortOrderToggle)
        val addResourceFab = view.findViewById<FloatingActionButton>(R.id.addResourceFab)
        this.addResourceFab = addResourceFab
        val list: RecyclerView = view.findViewById(R.id.resourcesList)
        val content: LinearLayout = view.findViewById(R.id.resourcesExplorerContent)
        val empty: TextView = view.findViewById(R.id.resourcesEmptyView)
        val swipeRefresh: SwipeRefreshLayout = view.findViewById(R.id.resourcesSwipeRefresh)
        contentView = content
        emptyView = empty
        resourcesList = list
        swipeRefreshLayout = swipeRefresh
        uploadLoadingView = view.findViewById(R.id.resourcesUploadLoading)

        searchInput.hint = getString(R.string.dashboard_resources_filter_name_hint)
        searchInput.setOnEditorActionListener { _, actionId, event ->
            val isSearchAction = actionId == EditorInfo.IME_ACTION_SEARCH
            val isEnterKey =
                event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
            if (isSearchAction || isEnterKey) {
                val query = searchInput.text?.toString().orEmpty().trim()
                if (query != searchQuery) {
                    searchQuery = query
                    hasLoadedMainResources = false
                    hasLoadedTeamResources = false
                }
                refreshContent(forceRefresh = true)
                true
            } else {
                false
            }
        }

        typeSpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            listOf(
                getString(R.string.dashboard_resources_filter_type_all),
                getString(R.string.dashboard_resources_filter_type_pdf),
                getString(R.string.dashboard_resources_filter_type_video),
                getString(R.string.dashboard_resources_filter_type_image),
                getString(R.string.course_wizard_attachment_audio)
            )
        )
        typeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val nextType = when (position) {
                    1 -> "pdf"
                    2 -> "video"
                    3 -> "image"
                    4 -> "audio"
                    else -> null
                }
                if (nextType != selectedMediaType) {
                    selectedMediaType = nextType
                    hasLoadedMainResources = false
                    hasLoadedTeamResources = false
                    refreshContent(forceRefresh = true)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        sortBySpinner.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            listOf(
                getString(R.string.dashboard_resources_sort_by_name),
                getString(R.string.dashboard_resources_sort_by_date)
            )
        )
        fun updateSortOrderIcon() {
            val icon = if (isSortDescending) {
                R.drawable.ic_sort_descending_24
            } else {
                R.drawable.ic_sort_ascending_24
            }
            sortOrderToggle.setImageResource(icon)
        }
        updateSortOrderIcon()
        sortOrderToggle.setOnClickListener {
            isSortDescending = !isSortDescending
            updateSortOrderIcon()
            hasLoadedMainResources = false
            hasLoadedTeamResources = false
            refreshContent(forceRefresh = true)
        }
        sortBySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val nextSortBy = if (position == 1) ResourceSortBy.DATE else ResourceSortBy.NAME
                if (nextSortBy != selectedSortBy) {
                    selectedSortBy = nextSortBy
                    hasLoadedMainResources = false
                    hasLoadedTeamResources = false
                    refreshContent(forceRefresh = true)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        addResourceFab.setOnClickListener {
            showAddResourceMenu(addResourceFab)
        }
        addResourceFab.enableDrag()

        list.layoutManager = LinearLayoutManager(requireContext())
        list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (dy <= 0 || isTeamResourcesTab || !hasMoreMainResources || isLoadingMainResources) {
                    return
                }
                val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                val totalItemCount = layoutManager.itemCount
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                if (totalItemCount > 0 && lastVisible >= totalItemCount - 1) {
                    loadMoreMainResources()
                }
            }
        })
        swipeRefresh.setOnRefreshListener {
            refreshContent(forceRefresh = true)
        }
        refreshContent()
    }

    override fun onResume() {
        super.onResume()
        refreshContent(forceRefresh = false)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopFabMenuSpin()
        runCatching {
            mediaRecorder?.stop()
        }
        mediaRecorder?.release()
        mediaRecorder = null
        recordingTimerHandler.removeCallbacksAndMessages(null)
        contentView = null
        emptyView = null
        resourcesList = null
        swipeRefreshLayout = null
        uploadLoadingView = null
        addResourceFab = null
    }

    private class ResourceExplorerAdapter(
        initialResources: List<ResourceUi>,
        private val onViewResource: (ResourceUi) -> Unit,
        private val onSecondaryAction: (ResourceUi) -> Unit
    ) : RecyclerView.Adapter<ResourceExplorerAdapter.ResourceViewHolder>() {

        private val resources = initialResources.toMutableList()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResourceViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            val binding = ItemDashboardResourceExplorerBinding.inflate(inflater, parent, false)
            return ResourceViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ResourceViewHolder, position: Int) {
            holder.bind(resources[position], onViewResource, onSecondaryAction)
        }

        override fun getItemCount(): Int = resources.size

        fun replaceResources(newResources: List<ResourceUi>) {
            val oldSize = resources.size
            resources.clear()
            resources.addAll(newResources)
            when {
                oldSize == 0 && newResources.isNotEmpty() -> notifyItemRangeInserted(0, newResources.size)
                newResources.isEmpty() && oldSize > 0 -> notifyItemRangeRemoved(0, oldSize)
                else -> {
                    val commonCount = minOf(oldSize, newResources.size)
                    if (commonCount > 0) {
                        notifyItemRangeChanged(0, commonCount)
                    }
                    if (oldSize > newResources.size) {
                        notifyItemRangeRemoved(newResources.size, oldSize - newResources.size)
                    } else if (newResources.size > oldSize) {
                        notifyItemRangeInserted(oldSize, newResources.size - oldSize)
                    }
                }
            }
        }

        class ResourceViewHolder(
            private val binding: ItemDashboardResourceExplorerBinding
        ) : RecyclerView.ViewHolder(binding.root) {
            private var lastRootTapAt: Long = 0L

            fun bind(
                item: ResourceUi,
                onViewResource: (ResourceUi) -> Unit,
                onSecondaryAction: (ResourceUi) -> Unit
            ) {
                binding.resourceName.text = item.name
                binding.resourceType.text = item.type
                binding.resourceDate.text = item.date
                val type = item.type.lowercase()
                val iconRes = when {
                    type.contains("audio") -> R.drawable.ic_course_step_audio_24
                    type.contains("video") -> R.drawable.ic_course_step_video_24
                    type.contains("image") -> R.drawable.ic_course_step_image_24
                    else -> R.drawable.ic_course_step_pdf_24
                }
                binding.resourceTypeIcon.setImageResource(iconRes)
                binding.resourceTypeIcon.contentDescription = item.type
                binding.resourceViewButton.setOnClickListener {
                    onViewResource(item)
                }
                binding.root.setOnClickListener {
                    val now = System.currentTimeMillis()
                    if (now - lastRootTapAt <= DOUBLE_TAP_WINDOW_MS) {
                        onViewResource(item)
                    }
                    lastRootTapAt = now
                }
                val (secondaryIcon, secondaryDescriptionRes) = if (item.isDownloaded) {
                    R.drawable.ic_dashboard_delete_24 to R.string.dashboard_resources_action_delete
                } else {
                    R.drawable.ic_survey_download_24 to R.string.dashboard_resources_download
                }
                binding.resourceSecondaryActionButton.setImageResource(secondaryIcon)
                binding.resourceSecondaryActionButton.contentDescription =
                    binding.root.context.getString(secondaryDescriptionRes)
                binding.resourceSecondaryActionButton.setOnClickListener {
                    onSecondaryAction(item)
                }
                binding.resourceSecondaryActionButton.alpha =
                    if (!item.isDownloaded && !item.isDownloadable) 0.4f else 1f
            }
        }
    }

    private data class ResourceUi(
        val id: String,
        val filename: String,
        val name: String,
        val type: String,
        val date: String,
        val createdDate: Long?,
        val isDownloaded: Boolean,
        val isDownloadable: Boolean
    )

    private enum class ResourceSortBy {
        NAME,
        DATE
    }

    private fun hasSelectedTeam(): Boolean {
        val teamId = DashboardTeamSelectionPreferences.getSelectedTeamId(requireContext())
        val teamName = DashboardTeamSelectionPreferences.getSelectedTeamName(requireContext())
        return !teamId.isNullOrBlank() && !teamName.isNullOrBlank()
    }

    private fun refreshContent(forceRefresh: Boolean = false) {
        val content = contentView ?: return
        val empty = emptyView ?: return
        val list = resourcesList ?: return
        addResourceFab?.isVisible = !isTeamResourcesTab || hasSelectedTeam()

        if (isTeamResourcesTab) {
            if (!hasSelectedTeam()) {
                content.isVisible = false
                empty.text = getString(R.string.dashboard_teams_select_team_hint)
                empty.isVisible = true
                swipeRefreshLayout?.isRefreshing = false
                return
            }

            content.isVisible = true
            empty.isVisible = false
            if (forceRefresh || !hasLoadedTeamResources) {
                loadTeamResources()
                return
            }
            val currentAdapter = list.adapter as? ResourceExplorerAdapter
            if (currentAdapter == null) {
                list.adapter = ResourceExplorerAdapter(teamResourcesItems.toList(), ::openResource, ::onSecondaryAction)
            } else {
                currentAdapter.replaceResources(teamResourcesItems)
            }
            swipeRefreshLayout?.isRefreshing = false
            return
        }

        content.isVisible = true
        empty.isVisible = false
        if (forceRefresh || !hasLoadedMainResources) {
            resetMainResourcesAndLoad()
            return
        }
        val currentAdapter = list.adapter as? ResourceExplorerAdapter
        if (currentAdapter == null) {
            list.adapter = ResourceExplorerAdapter(mainResourcesItems.toList(), ::openResource, ::onSecondaryAction)
        } else {
            currentAdapter.replaceResources(mainResourcesItems)
        }
        swipeRefreshLayout?.isRefreshing = false
    }

    private fun resetMainResourcesAndLoad() {
        mainResourcesSkip = 0
        hasMoreMainResources = true
        isLoadingMainResources = false
        hasLoadedMainResources = false
        mainResourcesItems.clear()
        mainResourcesItems.addAll(loadDownloadedResourcesFiltered())
        sortResources(mainResourcesItems)
        resourcesList?.adapter = ResourceExplorerAdapter(mainResourcesItems.toList(), ::openResource, ::onSecondaryAction)
        loadMoreMainResources()
    }

    private fun loadMoreMainResources() {
        if (isLoadingMainResources || !hasMoreMainResources) {
            return
        }
        val context = context ?: return
        val list = resourcesList ?: return
        val resolvedBaseUrl = DashboardServerPreferences.getServerBaseUrl(context)
        if (resolvedBaseUrl.isNullOrBlank()) {
            list.adapter = ResourceExplorerAdapter(mainResourcesItems.toList(), ::openResource, ::onSecondaryAction)
            swipeRefreshLayout?.isRefreshing = false
            return
        }
        baseUrl = resolvedBaseUrl

        isLoadingMainResources = true
        lifecycleScope.launch {
            sessionCookie = runCatching {
                AuthDependencies.provideAuthService(requireContext().applicationContext, resolvedBaseUrl)
                    .getStoredToken()
            }.getOrNull()

            val result = repository.fetchCommunityResources(
                baseUrl = resolvedBaseUrl,
                sessionCookie = sessionCookie,
                searchQuery = searchQuery,
                mediaTypeFilter = selectedMediaType,
                sortBy = "title",
                sortDescending = isSortDescending,
                skip = mainResourcesSkip,
                limit = MAIN_RESOURCES_PAGE_SIZE
            )
            result.onFailure { }
            val page = result.getOrDefault(emptyList())
            val existingKeys = mainResourcesItems.map { it.uniqueKey() }.toMutableSet()
            val items = page.map { resource ->
                val id = resource.id?.trim().orEmpty()
                val filename = resource.filename?.trim().orEmpty()
                ResourceUi(
                    id = id,
                    filename = filename,
                    name = resource.title?.takeIf { it.isNotBlank() }
                        ?: resource.filename?.takeIf { it.isNotBlank() }
                        ?: "-",
                    type = resource.mediaType?.uppercase(Locale.ROOT) ?: "PDF",
                    date = resource.createdDate.toDisplayDate(),
                    createdDate = resource.createdDate,
                    isDownloaded = findLocalResourceFile(id, filename)?.exists() == true,
                    isDownloadable = parseIsDownloadable(resource.isDownloadable)
                )
            }.filter { item ->
                val key = item.uniqueKey()
                if (existingKeys.contains(key)) {
                    false
                } else {
                    existingKeys.add(key)
                    true
                }
            }
            isLoadingMainResources = false
            if (isAdded) {
                mainResourcesItems.addAll(items)
                sortResources(mainResourcesItems)
                val currentAdapter = list.adapter as? ResourceExplorerAdapter
                if (currentAdapter == null || mainResourcesSkip == 0) {
                    list.adapter = ResourceExplorerAdapter(mainResourcesItems.toList(), ::openResource, ::onSecondaryAction)
                } else {
                    currentAdapter.replaceResources(mainResourcesItems)
                }
                mainResourcesSkip += page.size
                hasMoreMainResources = page.size >= MAIN_RESOURCES_PAGE_SIZE
                hasLoadedMainResources = true
                swipeRefreshLayout?.isRefreshing = false
            }
        }
    }

    private fun loadTeamResources() {
        if (isLoadingTeamResources) {
            return
        }
        val context = context ?: return
        val list = resourcesList ?: return
        val teamId = DashboardTeamSelectionPreferences.getSelectedTeamId(context).orEmpty()
        val resolvedBaseUrl = DashboardServerPreferences.getServerBaseUrl(context)
        val credentials = ProfileCredentialsStore.getStoredCredentials(context.applicationContext)
        if (teamId.isBlank() || resolvedBaseUrl.isNullOrBlank()) {
            teamResourcesItems.clear()
            list.adapter = ResourceExplorerAdapter(emptyList(), ::openResource, ::onSecondaryAction)
            swipeRefreshLayout?.isRefreshing = false
            return
        }
        baseUrl = resolvedBaseUrl
        isLoadingTeamResources = true
        lifecycleScope.launch {
            sessionCookie = runCatching {
                AuthDependencies.provideAuthService(requireContext().applicationContext, resolvedBaseUrl)
                    .getStoredToken()
            }.getOrNull()

            val result = repository.fetchTeamResources(
                baseUrl = resolvedBaseUrl,
                sessionCookie = sessionCookie,
                username = credentials?.username,
                password = credentials?.password,
                teamId = teamId,
                searchQuery = searchQuery,
                mediaTypeFilter = selectedMediaType,
                sortBy = "title",
                sortDescending = isSortDescending,
                limit = MAIN_RESOURCES_PAGE_SIZE
            )
            result.onFailure { }
            val page = result.getOrDefault(emptyList())
            val allRemoteItems = page.map { resource ->
                val id = resource.id?.trim().orEmpty()
                val filename = resource.filename?.trim().orEmpty()
                ResourceUi(
                    id = id,
                    filename = filename,
                    name = resource.title?.takeIf { it.isNotBlank() }
                        ?: resource.filename?.takeIf { it.isNotBlank() }
                        ?: "-",
                    type = resource.mediaType?.uppercase(Locale.ROOT) ?: "PDF",
                    date = resource.createdDate.toDisplayDate(),
                    createdDate = resource.createdDate,
                    isDownloaded = findLocalResourceFile(id, filename)?.exists() == true,
                    isDownloadable = parseIsDownloadable(resource.isDownloadable)
                )
            }
            val remoteKeys = allRemoteItems.map { it.resourceIdentityKey() }.toSet()
            val downloaded = loadDownloadedResourcesFiltered()
                .filter { downloadedItem ->
                    remoteKeys.contains(downloadedItem.resourceIdentityKey())
                }
            val existingKeys = downloaded.map { it.resourceIdentityKey() }.toMutableSet()
            val remoteItems = allRemoteItems.filter { item ->
                val key = item.resourceIdentityKey()
                if (existingKeys.contains(key)) {
                    false
                } else {
                    existingKeys.add(key)
                    true
                }
            }
            isLoadingTeamResources = false
            if (isAdded) {
                teamResourcesItems.clear()
                teamResourcesItems.addAll(downloaded)
                teamResourcesItems.addAll(remoteItems)
                sortResources(teamResourcesItems)
                hasLoadedTeamResources = true
                list.adapter = ResourceExplorerAdapter(teamResourcesItems.toList(), ::openResource, ::onSecondaryAction)
                swipeRefreshLayout?.isRefreshing = false
            }
        }
    }

    private fun onSecondaryAction(item: ResourceUi) {
        if (item.isDownloaded) {
            deleteDownloadedResource(item)
        } else {
            if (!item.isDownloadable) {
                Toast.makeText(requireContext(), getString(R.string.dashboard_resources_download), Toast.LENGTH_SHORT).show()
                return
            }
            downloadResource(item)
        }
    }

    private fun downloadResource(item: ResourceUi) {
        val currentBaseUrl = baseUrl
        if (currentBaseUrl.isNullOrBlank()) {
            Toast.makeText(requireContext(), getString(R.string.course_wizard_play_error), Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            val bytesResult = repository.downloadResourceBytes(
                baseUrl = currentBaseUrl,
                sessionCookie = sessionCookie,
                resourceId = item.id,
                filename = item.filename
            )
            bytesResult.onSuccess { bytes ->
                val localFile = saveDownloadedResourceFile(item, bytes)
                if (localFile != null) {
                    upsertDownloadedResource(item.copy(isDownloaded = true))
                    markResourceDownloadState(item, downloaded = true)
                } else {
                    Toast.makeText(requireContext(), getString(R.string.course_wizard_play_error), Toast.LENGTH_SHORT).show()
                }
            }.onFailure {
                Toast.makeText(requireContext(), getString(R.string.course_wizard_play_error), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deleteDownloadedResource(item: ResourceUi) {
        findLocalResourceFile(item.id, item.filename)?.delete()
        removeDownloadedResource(item)
        markResourceDownloadState(item, downloaded = false)
    }

    private fun markResourceDownloadState(item: ResourceUi, downloaded: Boolean) {
        updateDownloadStateInList(mainResourcesItems, item, downloaded)
        updateDownloadStateInList(teamResourcesItems, item, downloaded)
        val list = resourcesList ?: return
        val adapter = list.adapter as? ResourceExplorerAdapter
        val source = if (isTeamResourcesTab) teamResourcesItems else mainResourcesItems
        if (adapter == null) {
            list.adapter = ResourceExplorerAdapter(source.toList(), ::openResource, ::onSecondaryAction)
        } else {
            adapter.replaceResources(source)
        }
    }

    private fun updateDownloadStateInList(
        items: MutableList<ResourceUi>,
        target: ResourceUi,
        downloaded: Boolean
    ) {
        val index = items.indexOfFirst { it.uniqueKey() == target.uniqueKey() }
        if (index >= 0) {
            val existing = items[index]
            items[index] = existing.copy(isDownloaded = downloaded)
        } else if (downloaded) {
            items.add(0, target.copy(isDownloaded = true))
        }
        sortResources(items)
    }

    private fun saveDownloadedResourceFile(item: ResourceUi, bytes: ByteArray): File? {
        return runCatching {
            val context = requireContext().applicationContext
            val dir = File(context.filesDir, "resources/${item.id}").apply { mkdirs() }
            File(dir, item.filename).apply { writeBytes(bytes) }
        }.getOrNull()
    }

    private fun loadDownloadedResources(): List<ResourceUi> {
        val context = context ?: return emptyList()
        val raw = context.getSharedPreferences(DOWNLOADED_PREFS, 0)
            .getString(KEY_DOWNLOADED_RESOURCES, "[]")
            .orEmpty()
        val json = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        val items = mutableListOf<ResourceUi>()
        for (i in 0 until json.length()) {
            val obj = json.optJSONObject(i) ?: continue
            val id = obj.optString("id").orEmpty()
            val filename = obj.optString("filename").orEmpty()
            val local = findLocalResourceFile(id, filename)
            if (local?.exists() != true) {
                continue
            }
            items.add(
                ResourceUi(
                    id = id,
                    filename = filename,
                    name = obj.optString("name").ifBlank { filename },
                    type = obj.optString("type").ifBlank { "PDF" },
                    date = obj.optString("date").ifBlank { "-" },
                    createdDate = obj.optLong("createdDate").takeIf { it > 0L },
                    isDownloaded = true,
                    isDownloadable = true
                )
            )
        }
        return items
    }

    private fun loadDownloadedResourcesFiltered(): List<ResourceUi> {
        val query = searchQuery.trim()
        return loadDownloadedResources().filter { item ->
            val matchesQuery = query.isBlank() ||
                item.name.contains(query, ignoreCase = true) ||
                item.filename.contains(query, ignoreCase = true)
            val matchesType = selectedMediaType.isNullOrBlank() ||
                item.type.equals(selectedMediaType, ignoreCase = true)
            matchesQuery && matchesType
        }
    }

    private fun upsertDownloadedResource(item: ResourceUi) {
        val context = context ?: return
        val prefs = context.getSharedPreferences(DOWNLOADED_PREFS, 0)
        val existing = runCatching {
            JSONArray(prefs.getString(KEY_DOWNLOADED_RESOURCES, "[]").orEmpty())
        }.getOrElse { JSONArray() }
        val filtered = mutableListOf<JSONObject>()
        for (i in 0 until existing.length()) {
            val obj = existing.optJSONObject(i) ?: continue
            val key = "${obj.optString("id")}::${obj.optString("filename")}"
            if (key != item.uniqueKey()) {
                filtered.add(obj)
            }
        }
        filtered.add(
            JSONObject().apply {
                put("id", item.id)
                put("filename", item.filename)
                put("name", item.name)
                put("type", item.type)
                put("date", item.date)
                put("createdDate", item.createdDate)
            }
        )
        val next = JSONArray()
        filtered.forEach { next.put(it) }
        prefs.edit {
            putString(KEY_DOWNLOADED_RESOURCES, next.toString())
        }
    }

    private fun removeDownloadedResource(item: ResourceUi) {
        val context = context ?: return
        val prefs = context.getSharedPreferences(DOWNLOADED_PREFS, 0)
        val existing = runCatching {
            JSONArray(prefs.getString(KEY_DOWNLOADED_RESOURCES, "[]").orEmpty())
        }.getOrElse { JSONArray() }
        val next = JSONArray()
        for (i in 0 until existing.length()) {
            val obj = existing.optJSONObject(i) ?: continue
            val key = "${obj.optString("id")}::${obj.optString("filename")}"
            if (key != item.uniqueKey()) {
                next.put(obj)
            }
        }
        prefs.edit {
            putString(KEY_DOWNLOADED_RESOURCES, next.toString())
        }
    }

    private fun openResource(item: ResourceUi) {
        val mediaType = item.type.lowercase(Locale.ROOT)
        val resourceUri = resolveResourceUri(item)
        if (resourceUri.isNullOrBlank()) {
            Toast.makeText(requireContext(), getString(R.string.course_wizard_play_error), Toast.LENGTH_SHORT).show()
            return
        }
        when {
            mediaType.contains("image") -> {
                val intent = Intent(requireContext(), DashboardImagePreviewActivity::class.java).apply {
                    putStringArrayListExtra(
                        DashboardImagePreviewActivity.EXTRA_IMAGE_PATHS,
                        ArrayList(listOf(resourceUri))
                    )
                    putExtra(DashboardImagePreviewActivity.EXTRA_START_INDEX, 0)
                }
                startActivity(intent)
            }
            mediaType.contains("video") || mediaType.contains("audio") -> {
                val intent = FullscreenPlayerActivity.createIntent(
                    context = requireContext(),
                    mediaUrls = arrayListOf(resourceUri),
                    startIndex = 0,
                    startPositionMs = 0L,
                    authorizationHeader = resolveAuthHeader()
                )
                startActivity(intent)
            }
            else -> {
                val intent = FullscreenPdfActivity.createIntent(
                    requireContext(),
                    resourceUri,
                    resolveAuthHeader()
                )
                startActivity(intent)
            }
        }
    }

    private fun resolveResourceUri(item: ResourceUi): String? {
        val localFile = findLocalResourceFile(item.id, item.filename)
        if (localFile?.exists() == true) {
            return localFile.toURI().toString()
        }

        val baseUrl = DashboardServerPreferences.getServerBaseUrl(requireContext())
            ?.trim()
            ?.trimEnd('/')
            .orEmpty()
        if (baseUrl.isBlank() || item.id.isBlank() || item.filename.isBlank()) {
            return null
        }
        return baseUrl.toUri().buildUpon()
            .appendPath("db")
            .appendPath("resources")
            .appendPath(item.id)
            .appendPath(item.filename)
            .build()
            .toString()
    }

    private fun findLocalResourceFile(resourceId: String, filename: String): File? {
        if (resourceId.isBlank() || filename.isBlank()) {
            return null
        }
        val context = context ?: return null
        val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val candidates = mutableListOf(
            File(context.filesDir, "resources/$resourceId/$filename"),
            File(publicDownloads, filename)
        )
        context.getExternalFilesDir(null)?.let {
            candidates.add(File(it, "resources/$resourceId/$filename"))
        }
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let {
            candidates.add(File(it, "resources/$resourceId/$filename"))
        }
        return candidates.firstOrNull { it.exists() }
    }

    private fun resolveAuthHeader(): String? {
        val credentials = ProfileCredentialsStore.getStoredCredentials(requireContext().applicationContext)
        return credentials?.let { Credentials.basic(it.username, it.password) }
    }

    private fun Long?.toDisplayDate(): String {
        if (this == null || this <= 0L) {
            return "-"
        }
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
        return formatter.format(Date(this))
    }

    private fun ResourceUi.uniqueKey(): String = "$id::$filename"

    private fun ResourceUi.resourceIdentityKey(): String {
        return id.takeIf { it.isNotBlank() } ?: uniqueKey()
    }

    private fun sortResources(items: MutableList<ResourceUi>) {
        val comparator = when (selectedSortBy) {
            ResourceSortBy.DATE -> compareBy<ResourceUi> { it.createdDate ?: Long.MIN_VALUE }
            ResourceSortBy.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        }
        val ordered = if (isSortDescending) comparator.reversed() else comparator
        items.sortWith(ordered.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    private fun parseIsDownloadable(raw: Any?): Boolean {
        return when (raw) {
            is Boolean -> raw
            is Number -> raw.toInt() != 0
            is String -> {
                val normalized = raw.trim().lowercase(Locale.ROOT)
                normalized == "true" || normalized == "1" || normalized == "yes"
            }
            else -> false
        }
    }

    private fun showAddResourceMenu(anchor: View) {
        val themedContext = ContextThemeWrapper(requireContext(), R.style.Widget_MyPlanet_PopupMenu)
        val popup = PopupMenu(themedContext, anchor)
        popup.inflate(R.menu.menu_dashboard_resources_add)
        MenuCompat.setGroupDividerEnabled(popup.menu, true)
        forceShowMenuIcons(popup)
        startFabMenuSpin(anchor)
        popup.setOnMenuItemClickListener { menuItem ->
            val action = ResourceMenuAction.fromMenuItemId(menuItem.itemId)
            if (action == null) {
                false
            } else {
                handleResourceMenuAction(action)
                true
            }
        }
        popup.setOnDismissListener {
            stopFabMenuSpin()
        }
        popup.show()
    }

    private fun startFabMenuSpin(fab: View) {
        stopFabMenuSpin()
        spinningFab = fab
        isFabMenuSpinActive = true
        runFabSpinCycle(fab)
    }

    private fun stopFabMenuSpin() {
        isFabMenuSpinActive = false
        spinningFab?.animate()?.cancel()
        spinningFab?.rotation = 0f
        spinningFab = null
    }

    private fun runFabSpinCycle(fab: View) {
        if (!isFabMenuSpinActive) {
            return
        }
        fab.animate()
            .rotationBy(360f)
            .setDuration(800)
            .setInterpolator(LinearInterpolator())
            .withEndAction {
                if (!isFabMenuSpinActive) {
                    return@withEndAction
                }
                fab.animate()
                    .rotationBy(800f)
                    .setDuration(800)
                    .setInterpolator(LinearInterpolator())
                    .withEndAction {
                        if (!isFabMenuSpinActive) {
                            return@withEndAction
                        }
                        fab.animate()
                            .rotationBy(360f)
                            .setDuration(800)
                            .setInterpolator(LinearInterpolator())
                            .withEndAction {
                                if (!isFabMenuSpinActive) {
                                    return@withEndAction
                                }
                                fab.postDelayed(
                                    {
                                        runFabSpinCycle(fab)
                                    },
                                    10L
                                )
                            }
                            .start()
                    }
                    .start()
            }
            .start()
    }

    private fun handleResourceMenuAction(action: ResourceMenuAction) {
        val missingPermissions = requiredPermissionsFor(action).filter { permission ->
            ContextCompat.checkSelfPermission(requireContext(), permission) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isEmpty()) {
            executeResourceMenuAction(action)
            return
        }
        pendingPermissionAction = action
        permissionLauncher.launch(missingPermissions.toTypedArray())
    }

    private fun executeResourceMenuAction(action: ResourceMenuAction) {
        when (action) {
            ResourceMenuAction.AddPdf -> pickPdfLauncher.launch(arrayOf("application/pdf"))
            ResourceMenuAction.AddImage -> pickImageLauncher.launch(arrayOf("image/*"))
            ResourceMenuAction.AddVideo -> pickVideoLauncher.launch(arrayOf("video/*"))
            ResourceMenuAction.TakePhoto -> {
                val context = requireContext()
                val sharedImagesDir = File(context.cacheDir, "shared_images")
                if (!sharedImagesDir.exists()) {
                    sharedImagesDir.mkdirs()
                }
                val photoFile = File(sharedImagesDir, "captured_photo_${System.currentTimeMillis()}.jpg")
                val authority = "${context.packageName}.fileprovider"
                val uri = FileProvider.getUriForFile(context, authority, photoFile)
                latestPhotoUri = uri
                takePhotoLauncher.launch(uri)
            }
            ResourceMenuAction.TakeVideo -> captureVideo()
            ResourceMenuAction.AddAudio -> pickAudioLauncher.launch(arrayOf("audio/*"))
            ResourceMenuAction.RecordAudio -> showRecordAudioPopup()
        }
    }

    private fun captureVideo() {
        val context = requireContext()
        val dir = File(context.cacheDir, "shared_images").apply { mkdirs() }
        val file = File(dir, "captured_video_${System.currentTimeMillis()}.mp4")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        latestVideoUri = uri
        takeVideoLauncher.launch(uri)
    }

    @SuppressLint("SetTextI18n")
    private fun showRecordAudioPopup() {
        val context = requireContext()
        var isRecording = false
        currentAudioFile?.delete()
        currentAudioFile = null

        val dialogView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }

        val timerText = TextView(context).apply {
            text = "00:00"
            textSize = 32f
            setTextColor(Color.BLACK)
            setPadding(0, 0, 0, (24 * resources.displayMetrics.density).toInt())
        }

        val waveformView = RecordingWaveformView(context).apply {
            val size = (120 * resources.displayMetrics.density).toInt()
            layoutParams = FrameLayout.LayoutParams(size, size)
        }

        val recordButton = ImageButton(context).apply {
            setImageResource(R.drawable.ic_add_record_24)
            setColorFilter(Color.RED)
            background = null
            val size = (80 * resources.displayMetrics.density).toInt()
            layoutParams = FrameLayout.LayoutParams(size, size, Gravity.CENTER)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

        val buttonContainer = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            addView(waveformView)
            addView(recordButton)
        }

        dialogView.addView(timerText)
        dialogView.addView(buttonContainer)

        val dialog = AlertDialog.Builder(context)
            .setTitle(getString(R.string.dashboard_resources_record_audio))
            .setView(dialogView)
            .setNegativeButton(R.string.dashboard_resources_record_audio_cancel, null)
            .setPositiveButton(R.string.dashboard_resources_record_audio_accept, null)
            .setCancelable(false)
            .create()

        val updateTimerTask = object : Runnable {
            override fun run() {
                if (isRecording) {
                    val elapsed = System.currentTimeMillis() - recordingStartTime
                    timerText.text = formatDurationMs(elapsed)
                    val maxAmplitude = mediaRecorder?.maxAmplitude ?: 0
                    val normalized = (maxAmplitude.toFloat() / 32768f).coerceIn(0f, 1f)
                    waveformView.addAmplitude(normalized)
                    recordingTimerHandler.postDelayed(this, 100)
                }
            }
        }

        fun stopRecording() {
            if (!isRecording) return
            isRecording = false
            recordingTimerHandler.removeCallbacks(updateTimerTask)
            waveformView.clear()
            try {
                mediaRecorder?.stop()
            } catch (_: Exception) {
            }
            mediaRecorder?.release()
            mediaRecorder = null
            recordButton.setImageResource(R.drawable.ic_add_record_24)
            recordButton.setColorFilter(Color.RED)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
        }

        fun startRecording() {
            currentAudioFile?.delete()
            val dir = File(context.cacheDir, "shared_images").apply { mkdirs() }
            val file = File(dir, "recorded_audio_${System.currentTimeMillis()}.m4a")
            currentAudioFile = file

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mediaRecorder = recorder
            try {
                recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
                recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                recorder.setOutputFile(file.absolutePath)
                recorder.prepare()
                recorder.start()
                isRecording = true
                recordingStartTime = System.currentTimeMillis()
                recordingTimerHandler.post(updateTimerTask)
                recordButton.setImageResource(R.drawable.ic_stop)
                recordButton.setColorFilter(Color.BLACK)
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
            } catch (_: Exception) {
                Toast.makeText(context, "Recording failed to start", Toast.LENGTH_SHORT).show()
                recorder.release()
                mediaRecorder = null
                isRecording = false
            }
        }

        recordButton.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else {
                startRecording()
            }
        }

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (isRecording) stopRecording()
                val file = currentAudioFile
                if (file != null && file.exists()) {
                    val authority = "${context.packageName}.fileprovider"
                    val uri = FileProvider.getUriForFile(context, authority, file)
                    showAudioMetadataPopup(uri)
                    dialog.dismiss()
                }
            }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                if (isRecording) stopRecording()
                currentAudioFile?.delete()
                currentAudioFile = null
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun showPdfMetadataPopup(uri: Uri) {
        val context = requireContext()
        val fileName = resolveFileName(uri).ifBlank { "document.pdf" }
        val defaultTitle = fileName.replace(Regex("\\.pdf$", RegexOption.IGNORE_CASE), "").ifBlank { fileName }
        val languageOptions = resources.getStringArray(R.array.signup_language_options).toList()
        val credentials = ProfileCredentialsStore.getStoredCredentials(context.applicationContext)
        val username = credentials?.username.orEmpty()
        val planetCode = DashboardServerPreferences.getServerCode(context).orEmpty()

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        val titleInput = EditText(context).apply {
            hint = getString(R.string.dashboard_resources_pdf_field_title)
            setText(defaultTitle)
        }
        val descriptionInput = EditText(context).apply {
            hint = getString(R.string.dashboard_resources_pdf_field_description)
            setText("")
            minLines = 3
        }
        val languageLabel = TextView(context).apply {
            text = getString(R.string.dashboard_resources_pdf_field_language)
        }
        val languageSpinner = Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, languageOptions)
            setSelection(resolveDefaultLanguageIndex(languageOptions))
        }
        val isDownloadableCheck = CheckBox(context).apply {
            text = getString(R.string.dashboard_resources_pdf_field_downloadable)
            isChecked = true
        }
        content.addView(titleInput)
        content.addView(descriptionInput)
        content.addView(languageLabel)
        content.addView(languageSpinner)
        content.addView(isDownloadableCheck)

        val scroll = ScrollView(context).apply {
            addView(content)
        }

        AlertDialog.Builder(context)
            .setTitle(getString(R.string.dashboard_resources_add_pdf))
            .setView(scroll)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(getString(R.string.server_configuration_save), null)
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    button.setOnClickListener {
                        val rawTitle = titleInput.text?.toString()?.trim().orEmpty()
                        val title = sanitizeResourceName(rawTitle)
                        val description = descriptionInput.text?.toString()?.trim().orEmpty()
                        if (title.isBlank()) {
                            titleInput.error = getString(R.string.dashboard_resources_pdf_field_required)
                            return@setOnClickListener
                        }
                        if (description.isBlank()) {
                            descriptionInput.error = getString(R.string.dashboard_resources_pdf_field_required)
                            return@setOnClickListener
                        }
                        val payload = JSONObject()
                            .put("title", title)
                            .put("description", description)
                            .put("subject", JSONArray().put(getString(R.string.server_planet_learning)))
                            .put("level", JSONArray().put(getString(R.string.server_planet_early_education)))
                            .put("language", languageSpinner.selectedItem?.toString().orEmpty())
                            .put("addedBy", username)
                            .put("sourcePlanet", planetCode)
                            .put("resideOn", planetCode)
                            .put("isDownloadable", isDownloadableCheck.isChecked)
                            .put("private", false)
                        val bytesProvider: suspend () -> ByteArray? = {
                            readBytesFromUri(uri)
                        }
                        performResourceCreateAndUpload(
                            payload = payload,
                            fileExtension = "pdf",
                            mimeType = context.contentResolver.getType(uri).orEmpty().ifBlank { "application/pdf" },
                            credentials = credentials,
                            bytesProvider = bytesProvider,
                            onSuccess = {
                                Toast.makeText(context, getString(R.string.dashboard_resources_upload_success), Toast.LENGTH_SHORT).show()
                                dialog.dismiss()
                            }
                        )
                    }
                }
                dialog.setOnDismissListener {
                    if (uri.scheme == "content" && uri.authority == "${context.packageName}.fileprovider") {
                        lifecycleScope.launch(Dispatchers.IO) {
                            runCatching {
                                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                    if (nameIndex >= 0 && cursor.moveToFirst()) {
                                        val name = cursor.getString(nameIndex)
                                        if (name.startsWith("captured_photo_")) {
                                            val file = File(context.cacheDir, "shared_images/$name")
                                            if (file.exists()) {
                                                file.delete()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                dialog.show()
            }
    }

    private fun showImageMetadataPopup(uri: Uri) {
        val context = requireContext()
        val fileName = resolveFileName(uri).ifBlank { "image.jpg" }
        val defaultTitle = fileName.substringBeforeLast('.').ifBlank { fileName }
        val languageOptions = resources.getStringArray(R.array.signup_language_options).toList()
        val credentials = ProfileCredentialsStore.getStoredCredentials(context.applicationContext)
        val username = credentials?.username.orEmpty()
        val planetCode = DashboardServerPreferences.getServerCode(context).orEmpty()
        val mimeType = context.contentResolver.getType(uri).orEmpty().ifBlank { "image/jpeg" }
        val imageSize = resolveImageSize(uri) ?: (0 to 0)
        var isUploaded = false

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        val titleInput = EditText(context).apply {
            hint = getString(R.string.dashboard_resources_pdf_field_title)
            setText(defaultTitle)
        }
        val descriptionInput = EditText(context).apply {
            hint = getString(R.string.dashboard_resources_pdf_field_description)
            setText("")
            minLines = 3
        }
        val languageLabel = TextView(context).apply {
            text = getString(R.string.dashboard_resources_pdf_field_language)
        }
        val languageSpinner = Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, languageOptions)
            setSelection(resolveDefaultLanguageIndex(languageOptions))
        }
        val isDownloadableCheck = CheckBox(context).apply {
            text = getString(R.string.dashboard_resources_pdf_field_downloadable)
            isChecked = true
        }
        val previewImage = ImageView(context).apply {
            val previewHeight = (200 * resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                previewHeight
            ).apply {
                setMargins(0, 0, 0, (12 * resources.displayMetrics.density).toInt())
            }
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            adjustViewBounds = true
            Glide.with(this).load(uri).into(this)
        }
        val resolutionLabel = TextView(context).apply {
            text = getString(R.string.dashboard_resources_image_resolution)
        }
        val initialSliderValue = 90f
        val resolutionValue = TextView(context).apply {
            val factor = initialSliderValue / 100f
            val width = max(1, (imageSize.first * factor).roundToInt())
            val height = max(1, (imageSize.second * factor).roundToInt())
            text = getString(
                R.string.dashboard_resources_image_resolution_preview,
                initialSliderValue.roundToInt(),
                width,
                height
            )
        }
        val resolutionSlider = Slider(context).apply {
            contentDescription = getString(R.string.slider_desc)
            valueFrom = 40f
            valueTo = 100f
            value = initialSliderValue
            addOnChangeListener { _, value, _ ->
                val factor = value / 100f
                val width = max(1, (imageSize.first * factor).roundToInt())
                val height = max(1, (imageSize.second * factor).roundToInt())
                resolutionValue.text = getString(
                    R.string.dashboard_resources_image_resolution_preview,
                    value.roundToInt(),
                    width,
                    height
                )
            }
        }
        content.addView(titleInput)
        content.addView(descriptionInput)
        content.addView(languageLabel)
        content.addView(languageSpinner)
        content.addView(isDownloadableCheck)
        content.addView(resolutionLabel)
        content.addView(resolutionValue)
        content.addView(resolutionSlider)
        content.addView(previewImage)

        val scroll = ScrollView(context).apply {
            addView(content)
        }

        AlertDialog.Builder(context)
            .setTitle(getString(R.string.dashboard_resources_add_image))
            .setView(scroll)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(getString(R.string.server_configuration_save), null)
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    button.setOnClickListener {
                        val rawTitle = titleInput.text?.toString()?.trim().orEmpty()
                        val title = sanitizeResourceName(rawTitle)
                        val description = descriptionInput.text?.toString()?.trim().orEmpty()
                        if (title.isBlank()) {
                            titleInput.error = getString(R.string.dashboard_resources_pdf_field_required)
                            return@setOnClickListener
                        }
                        if (description.isBlank()) {
                            descriptionInput.error = getString(R.string.dashboard_resources_pdf_field_required)
                            return@setOnClickListener
                        }
                        val payload = JSONObject()
                            .put("title", title)
                            .put("description", description)
                            .put("subject", JSONArray().put(getString(R.string.server_planet_learning)))
                            .put("level", JSONArray().put(getString(R.string.server_planet_early_education)))
                            .put("language", languageSpinner.selectedItem?.toString().orEmpty())
                            .put("addedBy", username)
                            .put("sourcePlanet", planetCode)
                            .put("resideOn", planetCode)
                            .put("isDownloadable", isDownloadableCheck.isChecked)
                            .put("private", false)
                        val bytesProvider: suspend () -> ByteArray? = {
                            buildResizedImageBytes(uri, resolutionSlider.value, mimeType)
                        }
                        performResourceCreateAndUpload(
                            payload = payload,
                            fileExtension = extensionForImageMimeType(mimeType),
                            mimeType = mimeType,
                            credentials = credentials,
                            bytesProvider = bytesProvider,
                            onSuccess = {
                                isUploaded = true
                                Toast.makeText(context, getString(R.string.dashboard_resources_upload_success), Toast.LENGTH_SHORT).show()
                                dialog.dismiss()
                            }
                        )
                    }
                }
                dialog.show()
                dialog.setOnDismissListener {
                    if (uri.authority == "${context.packageName}.fileprovider" || isUploaded) {
                        runCatching {
                            File(context.cacheDir, "shared_images/${uri.lastPathSegment}").delete()
                        }
                    }
                }
            }
    }

    @OptIn(UnstableApi::class)
    private fun showAudioMetadataPopup(uri: Uri) {
        val context = requireContext()
        val fileName = resolveFileName(uri).ifBlank { "audio.mp3" }
        val defaultTitle = fileName.substringBeforeLast('.').ifBlank { fileName }
        val languageOptions = resources.getStringArray(R.array.signup_language_options).toList()
        val credentials = ProfileCredentialsStore.getStoredCredentials(context.applicationContext)
        val username = credentials?.username.orEmpty()
        val planetCode = DashboardServerPreferences.getServerCode(context).orEmpty()
        val sourceDurationMs = resolveVideoDurationMs(uri)
        val bitrates = listOf(96, 128, 192, 320)
        val sourceBitrate = resolveAudioBitrate(uri) ?: 320
        val allowedBitrates = bitrates.filter { it <= sourceBitrate || it == 96 }.sorted()
        var selectedBitrate = allowedBitrates.lastOrNull() ?: bitrates[0]
        var selectedStartMs = 0L
        var selectedEndMs = sourceDurationMs

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        val titleInput = EditText(context).apply {
            hint = getString(R.string.dashboard_resources_pdf_field_title)
            setText(defaultTitle)
        }
        val descriptionInput = EditText(context).apply {
            hint = getString(R.string.dashboard_resources_pdf_field_description)
            setText("")
            minLines = 3
        }
        val languageLabel = TextView(context).apply {
            text = getString(R.string.dashboard_resources_pdf_field_language)
        }
        val languageSpinner = Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, languageOptions)
            setSelection(resolveDefaultLanguageIndex(languageOptions))
        }
        val isDownloadableCheck = CheckBox(context).apply {
            text = getString(R.string.dashboard_resources_pdf_field_downloadable)
            isChecked = true
        }
        val qualityLabel = TextView(context).apply {
            text = getString(R.string.dashboard_resources_audio_quality)
        }
        val qualityValueLabel = TextView(context).apply {
            text = getString(R.string.dashboard_resources_audio_quality_value, selectedBitrate)
        }
        val segmentTitle = TextView(context).apply {
            text = getString(R.string.dashboard_resources_video_segment_label)
            isVisible = sourceDurationMs > 1000L
        }
        val segmentStartValue = TextView(context).apply {
            text = formatDurationMs(selectedStartMs)
            isVisible = sourceDurationMs > 1000L
        }
        val segmentEndValue = TextView(context).apply {
            text = formatDurationMs(selectedEndMs)
            isVisible = sourceDurationMs > 1000L
            textAlignment = View.TEXT_ALIGNMENT_VIEW_END
        }
        val segmentTimesRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            isVisible = sourceDurationMs > 1000L
            addView(segmentStartValue, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(segmentEndValue, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        val segmentTotalValue = TextView(context).apply {
            text = getString(
                R.string.dashboard_resources_video_segment_total,
                formatDurationMs((selectedEndMs - selectedStartMs).coerceAtLeast(0L))
            )
            isVisible = sourceDurationMs > 1000L
        }
        val sizeEstimateValue = TextView(context).apply {
            val estimated = estimateAudioUploadSizeBytes(selectedBitrate, (selectedEndMs - selectedStartMs).coerceAtLeast(0L))
            text = getString(R.string.dashboard_resources_audio_size_estimate, Formatter.formatShortFileSize(context, estimated))
        }
        val waveformView = AudioWaveformView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                (100 * resources.displayMetrics.density).toInt()
            )
        }
        val previewPlayer = ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            setSeekParameters(SeekParameters.EXACT)
            prepare()
            seekTo(selectedStartMs)
        }
        val updateSizeEstimate = {
            val estimated = estimateAudioUploadSizeBytes(selectedBitrate, (selectedEndMs - selectedStartMs).coerceAtLeast(0L))
            sizeEstimateValue.text = getString(R.string.dashboard_resources_audio_size_estimate, Formatter.formatShortFileSize(context, estimated))
        }
        val qualitySlider = Slider(context).apply {
            contentDescription = getString(R.string.slider_desc)
            val maxIndex = (allowedBitrates.size - 1).coerceAtLeast(0)
            valueFrom = 0f
            valueTo = if (maxIndex > 0) maxIndex.toFloat() else 1f
            stepSize = 1f
            value = if (maxIndex > 0) maxIndex.toFloat() else 0f
            isVisible = allowedBitrates.size > 1
            addOnChangeListener { _, value, _ ->
                val index = value.roundToInt().coerceIn(0, allowedBitrates.lastIndex)
                selectedBitrate = allowedBitrates[index]
                qualityValueLabel.text = getString(R.string.dashboard_resources_audio_quality_value, selectedBitrate)
                updateSizeEstimate()
            }
        }
        val segmentRangeSlider = RangeSlider(context).apply {
            val rawTotalSeconds = max(2, kotlin.math.ceil(sourceDurationMs / 1000.0).toInt())
            val totalSeconds = (((rawTotalSeconds + 1) / 2) * 2).coerceAtLeast(2)
            contentDescription = getString(R.string.slider_desc)
            valueFrom = 0f
            valueTo = totalSeconds.toFloat()
            stepSize = 2f
            values = mutableListOf(0f, totalSeconds.toFloat())
            isVisible = sourceDurationMs > 1000L
            addOnChangeListener { slider, _, _ ->
                val currentValues = slider.values.sorted()
                val startSecond = currentValues.firstOrNull()?.roundToInt() ?: 0
                val endSecond = currentValues.lastOrNull()?.roundToInt() ?: totalSeconds
                selectedStartMs = (startSecond * 1_000L).coerceIn(0L, sourceDurationMs)
                selectedEndMs = (endSecond * 1_000L).coerceIn(0L, sourceDurationMs)
                if (selectedEndMs <= selectedStartMs) {
                    selectedEndMs = (selectedStartMs + 2_000L).coerceAtMost(sourceDurationMs)
                }
                segmentStartValue.text = formatDurationMs(selectedStartMs)
                segmentEndValue.text = formatDurationMs(selectedEndMs)
                segmentTotalValue.text = getString(
                    R.string.dashboard_resources_video_segment_total,
                    formatDurationMs((selectedEndMs - selectedStartMs).coerceAtLeast(0L))
                )
                if (previewPlayer.currentPosition !in selectedStartMs..<selectedEndMs) {
                    previewPlayer.seekTo(selectedStartMs)
                }
                if (sourceDurationMs > 0) {
                    waveformView.setSelection(selectedStartMs.toFloat() / sourceDurationMs, selectedEndMs.toFloat() / sourceDurationMs)
                }
                updateSizeEstimate()
            }
        }
        lifecycleScope.launch {
            val waveform = extractWaveform(uri)
            waveformView.setWaveform(waveform)
        }

        val previewPlayerView = PlayerView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            player = previewPlayer
            useController = true
            showController()
            setBackgroundColor(Color.TRANSPARENT)
            findViewById<View>(androidx.media3.ui.R.id.exo_artwork)?.isVisible = false
            findViewById<View>(androidx.media3.ui.R.id.exo_content_frame)?.isVisible = false
        }

        val previewContainer = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (140 * resources.displayMetrics.density).toInt()
            ).apply {
                topMargin = (16 * resources.displayMetrics.density).toInt()
            }
            addView(waveformView)
            addView(previewPlayerView)
        }

        val progressHandler = Handler(Looper.getMainLooper())
        val updateProgressTask = object : Runnable {
            override fun run() {
                val isEnded = previewPlayer.playbackState == Player.STATE_ENDED
                if (previewPlayer.isPlaying || isEnded) {
                    val pos = previewPlayer.currentPosition
                    if (isEnded || (selectedEndMs in (selectedStartMs + 1)..pos)) {
                        previewPlayer.seekTo(selectedStartMs)
                        if (isEnded) previewPlayer.play()
                    }
                    val dur = previewPlayer.duration.toFloat()
                    if (dur > 0) {
                        waveformView.setProgress(previewPlayer.currentPosition.toFloat() / dur)
                    }
                }
                progressHandler.postDelayed(this, 100)
            }
        }
        progressHandler.post(updateProgressTask)

        val previewTitle = TextView(context).apply {
            text = getString(R.string.dashboard_resources_audio_preview)
            setPadding(0, (16 * resources.displayMetrics.density).toInt(), 0, 0)
        }

        content.addView(titleInput)
        content.addView(descriptionInput)
        content.addView(languageLabel)
        content.addView(languageSpinner)
        content.addView(isDownloadableCheck)
        content.addView(qualityLabel)
        content.addView(qualityValueLabel)
        content.addView(qualitySlider)
        content.addView(segmentTitle)
        content.addView(segmentTimesRow)
        content.addView(segmentRangeSlider)
        content.addView(segmentTotalValue)
        content.addView(sizeEstimateValue)
        content.addView(previewTitle)
        content.addView(previewContainer)

        val scroll = ScrollView(context).apply { addView(content) }

        AlertDialog.Builder(context)
            .setTitle(getString(R.string.dashboard_resources_add_audio))
            .setView(scroll)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(getString(R.string.server_configuration_save), null)
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    button.setOnClickListener {
                        val rawTitle = titleInput.text?.toString()?.trim().orEmpty()
                        val title = sanitizeResourceName(rawTitle)
                        val description = descriptionInput.text?.toString()?.trim().orEmpty()
                        if (title.isBlank()) {
                            titleInput.error = getString(R.string.dashboard_resources_pdf_field_required)
                            return@setOnClickListener
                        }
                        if (description.isBlank()) {
                            descriptionInput.error = getString(R.string.dashboard_resources_pdf_field_required)
                            return@setOnClickListener
                        }
                        val payload = JSONObject()
                            .put("title", title)
                            .put("description", description)
                            .put("subject", JSONArray().put(getString(R.string.server_planet_learning)))
                            .put("level", JSONArray().put(getString(R.string.server_planet_early_education)))
                            .put("language", languageSpinner.selectedItem?.toString().orEmpty())
                            .put("addedBy", username)
                            .put("sourcePlanet", planetCode)
                            .put("resideOn", planetCode)
                            .put("isDownloadable", isDownloadableCheck.isChecked)
                            .put("private", false)
                        val bytesProvider: suspend () -> ByteArray? = {
                            transcodeAudio(
                                uri = uri,
                                bitrateKbps = selectedBitrate,
                                startMs = selectedStartMs,
                                endMs = selectedEndMs,
                                sourceDurationMs = sourceDurationMs
                            )
                        }
                        performResourceCreateAndUpload(
                            payload = payload,
                            fileExtension = "mp3",
                            mimeType = "audio/mpeg",
                            credentials = credentials,
                            bytesProvider = bytesProvider,
                            onSuccess = {
                                Toast.makeText(context, getString(R.string.dashboard_resources_upload_success), Toast.LENGTH_SHORT).show()
                                dialog.dismiss()
                            }
                        )
                    }
                }
                dialog.show()
                dialog.setOnDismissListener {
                    progressHandler.removeCallbacks(updateProgressTask)
                    previewPlayer.release()
                }
            }
    }

    @OptIn(UnstableApi::class)
    private fun showVideoMetadataPopup(uri: Uri) {
        val context = requireContext()
        val fileName = resolveFileName(uri).ifBlank { "video.mp4" }
        val defaultTitle = fileName.substringBeforeLast('.').ifBlank { fileName }
        val languageOptions = resources.getStringArray(R.array.signup_language_options).toList()
        val credentials = ProfileCredentialsStore.getStoredCredentials(context.applicationContext)
        val username = credentials?.username.orEmpty()
        val planetCode = DashboardServerPreferences.getServerCode(context).orEmpty()
        val (sourceWidth, sourceHeight) = resolveVideoDimensions(uri)
        val sourceDurationMs = resolveVideoDurationMs(uri)
        val allowedHeights = allowedVideoHeights(sourceHeight)
        val defaultHeight = defaultVideoHeightSelection(sourceHeight)
        val defaultIndex = allowedHeights.indexOf(defaultHeight).takeIf { it >= 0 } ?: 0
        val sourceFileSizeBytes = resolveFileSizeBytes(uri)
        var selectedStartMs = 0L
        var selectedEndMs = sourceDurationMs
        var selectedHeightForEstimate = allowedHeights.getOrElse(defaultIndex) { defaultHeight }
        var selectedRotationDegrees = 0f
        var isUploaded = false

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        val titleInput = EditText(context).apply {
            hint = getString(R.string.dashboard_resources_pdf_field_title)
            setText(defaultTitle)
        }
        val descriptionInput = EditText(context).apply {
            hint = getString(R.string.dashboard_resources_pdf_field_description)
            setText("")
            minLines = 3
        }
        val languageLabel = TextView(context).apply {
            text = getString(R.string.dashboard_resources_pdf_field_language)
        }
        val languageSpinner = Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, languageOptions)
            setSelection(resolveDefaultLanguageIndex(languageOptions))
        }
        val isDownloadableCheck = CheckBox(context).apply {
            text = getString(R.string.dashboard_resources_pdf_field_downloadable)
            isChecked = true
        }
        val resolutionLabel = TextView(context).apply {
            text = getString(R.string.dashboard_resources_video_resolution)
        }
        val resolutionValue = TextView(context).apply {
            text = getString(
                R.string.dashboard_resources_video_resolution_value,
                allowedHeights.getOrElse(defaultIndex) { defaultHeight }
            )
        }
        val segmentTitle = TextView(context).apply {
            text = getString(R.string.dashboard_resources_video_segment_label)
            isVisible = sourceDurationMs > 1000L
        }
        val segmentStartValue = TextView(context).apply {
            text = formatDurationMs(selectedStartMs)
            isVisible = sourceDurationMs > 1000L
        }
        val segmentEndValue = TextView(context).apply {
            text = formatDurationMs(selectedEndMs)
            isVisible = sourceDurationMs > 1000L
            textAlignment = View.TEXT_ALIGNMENT_VIEW_END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val segmentTimesRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            isVisible = sourceDurationMs > 1000L
            addView(segmentStartValue, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(segmentEndValue)
        }
        val segmentTotalValue = TextView(context).apply {
            text = getString(
                R.string.dashboard_resources_video_segment_total,
                formatDurationMs((selectedEndMs - selectedStartMs).coerceAtLeast(0L))
            )
            isVisible = sourceDurationMs > 1000L
        }
        val sizeEstimateValue = TextView(context).apply {
            text = buildVideoSizeEstimateText(
                sourceSizeBytes = sourceFileSizeBytes,
                sourceHeight = sourceHeight,
                selectedHeight = allowedHeights.getOrElse(defaultIndex) { defaultHeight },
                sourceDurationMs = sourceDurationMs,
                selectedStartMs = selectedStartMs,
                selectedEndMs = selectedEndMs
            )
        }
        val previewTitle = TextView(context).apply {
            text = getString(R.string.dashboard_resources_video_preview)
        }
        val previewPlayer = ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            seekTo(selectedStartMs)
        }
        val previewVideoView = PlayerView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            )
            useController = false
            player = previewPlayer
        }
        val rotateLeftButton = ImageButton(context).apply {
            setImageResource(R.drawable.ic_rotate_left)
            contentDescription = getString(R.string.dashboard_resources_rotate_left)
            setBackgroundResource(android.R.drawable.btn_default_small)
        }
        val rotateRightButton = ImageButton(context).apply {
            setImageResource(R.drawable.ic_rotate_right)
            contentDescription = getString(R.string.dashboard_resources_rotate_right)
            setBackgroundResource(android.R.drawable.btn_default_small)
        }
        val rotationButtonsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(rotateRightButton)
            addView(rotateLeftButton)
        }
        val previewContainer = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (180 * resources.displayMetrics.density).toInt()
            )
            clipChildren = true
            addView(previewVideoView)
        }
        val previewToggleButton = ImageButton(context).apply {
            setImageResource(R.drawable.ic_preview_play_stop_24)
            setBackgroundResource(R.drawable.bg_preview_overlay_button)
            val iconSize = (72 * resources.displayMetrics.density).toInt()
            layoutParams = FrameLayout.LayoutParams(iconSize, iconSize, Gravity.CENTER)
            contentDescription = getString(R.string.dashboard_resources_video_preview_play)
            alpha = 1f
        }
        previewContainer.addView(previewToggleButton)
        val updatePreviewContainerSize = {
            val normalizedRotation = ((selectedRotationDegrees % 360f) + 360f) % 360f
            val isQuarterTurn = normalizedRotation == 90f || normalizedRotation == 270f
            val rotatedWidth = if (isQuarterTurn) sourceHeight else sourceWidth
            val rotatedHeight = if (isQuarterTurn) sourceWidth else sourceHeight
            val containerWidth = previewContainer.width.takeIf { it > 0 }
                ?: (resources.displayMetrics.widthPixels - (32 * resources.displayMetrics.density).roundToInt())
            val computedHeight = if (rotatedWidth > 0 && rotatedHeight > 0) {
                (containerWidth.toFloat() * rotatedHeight.toFloat() / rotatedWidth.toFloat()).roundToInt()
            } else {
                (180 * resources.displayMetrics.density).toInt()
            }
            val minHeight = (120 * resources.displayMetrics.density).toInt()
            val maxHeight = (320 * resources.displayMetrics.density).toInt()
            val finalHeight = computedHeight.coerceIn(minHeight, maxHeight)
            previewContainer.layoutParams = (previewContainer.layoutParams as LinearLayout.LayoutParams).apply {
                height = finalHeight
            }
            previewVideoView.layoutParams = (previewVideoView.layoutParams as FrameLayout.LayoutParams).apply {
                width = FrameLayout.LayoutParams.MATCH_PARENT
                height = FrameLayout.LayoutParams.MATCH_PARENT
            }
            previewContainer.requestLayout()
        }
        val applyPreviewRotation = {
            val rotationEffect = ScaleAndRotateTransformation.Builder()
                .setRotationDegrees(selectedRotationDegrees)
                .build()
            previewPlayer.setVideoEffects(listOf(rotationEffect))
            previewToggleButton.rotation = 0f
            updatePreviewContainerSize()
        }
        rotateLeftButton.setOnClickListener {
            selectedRotationDegrees = ((selectedRotationDegrees - 90f) % 360f + 360f) % 360f
            applyPreviewRotation()
        }
        rotateRightButton.setOnClickListener {
            selectedRotationDegrees = ((selectedRotationDegrees + 90f) % 360f + 360f) % 360f
            applyPreviewRotation()
        }
        applyPreviewRotation()
        previewContainer.post { updatePreviewContainerSize() }
        val previewHandler = Handler(Looper.getMainLooper())
        val showPreviewOverlayButton = {
            previewToggleButton.visibility = View.VISIBLE
            previewToggleButton.animate().alpha(1f).setDuration(180L).start()
        }
        val hidePreviewOverlayButton = {
            previewToggleButton.animate()
                .alpha(0f)
                .setDuration(180L)
                .withEndAction {
                    previewToggleButton.visibility = View.GONE
                }
                .start()
        }
        val previewMonitor = object : Runnable {
            override fun run() {
                val isEnded = previewPlayer.playbackState == Player.STATE_ENDED
                if (previewPlayer.isPlaying || isEnded) {
                    if (isEnded || (selectedEndMs > selectedStartMs && previewPlayer.currentPosition >= selectedEndMs)) {
                        previewPlayer.seekTo(selectedStartMs)
                        if (isEnded) previewPlayer.play()
                    }
                }
                previewHandler.postDelayed(this, 100L)
            }
        }
        previewHandler.post(previewMonitor)
        val togglePreviewPlayback = {
            if (previewPlayer.isPlaying) {
                previewPlayer.pause()
                previewToggleButton.contentDescription = getString(R.string.dashboard_resources_video_preview_play)
                showPreviewOverlayButton()
            } else {
                if (previewPlayer.currentPosition !in selectedStartMs..<selectedEndMs) {
                    previewPlayer.seekTo(selectedStartMs)
                }
                previewPlayer.play()
                previewToggleButton.contentDescription = getString(R.string.dashboard_resources_video_preview_stop)
                hidePreviewOverlayButton()
            }
        }
        previewToggleButton.setOnClickListener { togglePreviewPlayback() }
        previewVideoView.setOnClickListener { togglePreviewPlayback() }
        val segmentRangeSlider = RangeSlider(context).apply {
            val rawTotalSeconds = max(2, kotlin.math.ceil(sourceDurationMs / 1000.0).toInt())
            val totalSeconds = (((rawTotalSeconds + 1) / 2) * 2).coerceAtLeast(2)
            contentDescription = getString(R.string.slider_desc)
            valueFrom = 0f
            valueTo = totalSeconds.toFloat()
            stepSize = 2f
            values = mutableListOf(0f, totalSeconds.toFloat())
            isVisible = sourceDurationMs > 1000L
            addOnChangeListener { slider, _, _ ->
                val currentValues = slider.values.sorted()
                val startSecond = currentValues.firstOrNull()?.roundToInt() ?: 0
                val endSecond = currentValues.lastOrNull()?.roundToInt() ?: totalSeconds
                val startMs = (startSecond * 1_000L).coerceIn(0L, sourceDurationMs)
                var endMs = (endSecond * 1_000L).coerceIn(0L, sourceDurationMs)
                if (endMs <= startMs) {
                    endMs = (startMs + 2_000L).coerceAtMost(sourceDurationMs)
                }
                selectedStartMs = startMs
                selectedEndMs = endMs
                if (previewPlayer.currentPosition !in selectedStartMs..<selectedEndMs) {
                    previewPlayer.seekTo(selectedStartMs)
                }
                segmentStartValue.text = formatDurationMs(selectedStartMs)
                segmentEndValue.text = formatDurationMs(selectedEndMs)
                segmentTotalValue.text = getString(
                    R.string.dashboard_resources_video_segment_total,
                    formatDurationMs((selectedEndMs - selectedStartMs).coerceAtLeast(0L))
                )
                sizeEstimateValue.text = buildVideoSizeEstimateText(
                    sourceSizeBytes = sourceFileSizeBytes,
                    sourceHeight = sourceHeight,
                    selectedHeight = selectedHeightForEstimate,
                    sourceDurationMs = sourceDurationMs,
                    selectedStartMs = selectedStartMs,
                    selectedEndMs = selectedEndMs
                )
            }
        }
        val resolutionSlider = Slider(context).apply {
            contentDescription = getString(R.string.slider_desc)
            val maxIndex = (allowedHeights.size - 1).coerceAtLeast(0)
            valueFrom = 0f
            valueTo = if (maxIndex > 0) maxIndex.toFloat() else 1f
            stepSize = 1f
            value = defaultIndex.toFloat().coerceIn(0f, if (maxIndex > 0) maxIndex.toFloat() else 1f)
            isVisible = allowedHeights.size > 1
            addOnChangeListener { _, value, _ ->
                val index = value.roundToInt().coerceIn(0, allowedHeights.lastIndex)
                val selectedHeight = allowedHeights[index]
                selectedHeightForEstimate = selectedHeight
                resolutionValue.text = getString(R.string.dashboard_resources_video_resolution_value, selectedHeight)
                sizeEstimateValue.text = buildVideoSizeEstimateText(
                    sourceSizeBytes = sourceFileSizeBytes,
                    sourceHeight = sourceHeight,
                    selectedHeight = selectedHeight,
                    sourceDurationMs = sourceDurationMs,
                    selectedStartMs = selectedStartMs,
                    selectedEndMs = selectedEndMs
                )
            }
        }

        content.addView(titleInput)
        content.addView(descriptionInput)
        content.addView(languageLabel)
        content.addView(languageSpinner)
        content.addView(isDownloadableCheck)
        content.addView(resolutionLabel)
        content.addView(resolutionSlider)
        content.addView(resolutionValue)
        content.addView(segmentTitle)
        content.addView(segmentTimesRow)
        content.addView(segmentRangeSlider)
        content.addView(segmentTotalValue)
        content.addView(sizeEstimateValue)
        content.addView(rotationButtonsRow)
        content.addView(previewTitle)
        content.addView(previewContainer)

        val scroll = ScrollView(context).apply { addView(content) }

        AlertDialog.Builder(context)
            .setTitle(getString(R.string.dashboard_resources_add_video))
            .setView(scroll)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(getString(R.string.server_configuration_save), null)
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    button.setOnClickListener {
                        val rawTitle = titleInput.text?.toString()?.trim().orEmpty()
                        val title = sanitizeResourceName(rawTitle)
                        val description = descriptionInput.text?.toString()?.trim().orEmpty()
                        if (title.isBlank()) {
                            titleInput.error = getString(R.string.dashboard_resources_pdf_field_required)
                            return@setOnClickListener
                        }
                        if (description.isBlank()) {
                            descriptionInput.error = getString(R.string.dashboard_resources_pdf_field_required)
                            return@setOnClickListener
                        }
                        val selectedIndex = resolutionSlider.value.roundToInt().coerceIn(0, allowedHeights.lastIndex)
                        val selectedHeight = allowedHeights[selectedIndex]
                        val payload = JSONObject()
                            .put("title", title)
                            .put("description", description)
                            .put("subject", JSONArray().put(getString(R.string.server_planet_learning)))
                            .put("level", JSONArray().put(getString(R.string.server_planet_early_education)))
                            .put("language", languageSpinner.selectedItem?.toString().orEmpty())
                            .put("addedBy", username)
                            .put("sourcePlanet", planetCode)
                            .put("resideOn", planetCode)
                            .put("isDownloadable", isDownloadableCheck.isChecked)
                            .put("private", false)
                        val bytesProvider: suspend () -> ByteArray? = {
                            transcodeVideoToMp4(
                                uri = uri,
                                selectedHeight = selectedHeight,
                                sourceWidth = sourceWidth,
                                sourceHeight = sourceHeight,
                                startMs = selectedStartMs,
                                endMs = selectedEndMs,
                                sourceDurationMs = sourceDurationMs,
                                rotationDegrees = selectedRotationDegrees
                            )
                        }
                        performResourceCreateAndUpload(
                            payload = payload,
                            fileExtension = "mp4",
                            mimeType = "video/mp4",
                            credentials = credentials,
                            bytesProvider = bytesProvider,
                            onSuccess = {
                                isUploaded = true
                                Toast.makeText(context, getString(R.string.dashboard_resources_upload_success), Toast.LENGTH_SHORT).show()
                                dialog.dismiss()
                            }
                        )
                    }
                }
                dialog.show()
                dialog.setOnDismissListener {
                    previewHandler.removeCallbacks(previewMonitor)
                    previewPlayer.release()
                    showPreviewOverlayButton()
                    if (uri.authority == "${context.packageName}.fileprovider" || isUploaded) {
                        runCatching {
                            File(context.cacheDir, "shared_images/${uri.lastPathSegment}").delete()
                        }
                    }
                }
            }
    }

    private fun performResourceCreateAndUpload(
        payload: JSONObject,
        fileExtension: String,
        mimeType: String,
        credentials: org.ole.planet.myplanet.lite.profile.StoredCredentials?,
        bytesProvider: suspend () -> ByteArray?,
        onSuccess: () -> Unit
    ) {
        val context = requireContext()
        val planetCode = DashboardServerPreferences.getServerCode(context).orEmpty()
        if (isTeamResourcesTab) {
            val teamId = DashboardTeamSelectionPreferences.getSelectedTeamId(context)
            if (!teamId.isNullOrBlank()) {
                payload.put("private", true)
                payload.put("privateFor", JSONObject().put("teams", teamId))
            }
        }
        applyWebCompatibleResourceDefaults(payload)
        payload.put("mediaType", normalizeResourceMediaType(mimeType))
        val now = System.currentTimeMillis()
        payload.put("createdDate", now)
        payload.put("updatedDate", now)
        val resolvedBaseUrl = DashboardServerPreferences.getServerBaseUrl(context)
        if (resolvedBaseUrl.isNullOrBlank()) {
            Toast.makeText(context, getString(R.string.dashboard_voices_no_server), Toast.LENGTH_SHORT).show()
            return
        }
        setUploadLoadingVisible(true)
        lifecycleScope.launch {
            val bytes = bytesProvider()
            if (bytes == null) {
                setUploadLoadingVisible(false)
                Toast.makeText(context, getString(R.string.course_wizard_play_error), Toast.LENGTH_SHORT).show()
                return@launch
            }
            val result = repository.createResourceDocument(
                baseUrl = resolvedBaseUrl,
                sessionCookie = sessionCookie,
                username = credentials?.username,
                password = credentials?.password,
                payload = payload
            )
            result.onSuccess { creationResponse ->
                val resourceId = creationResponse.optString("id").orEmpty()
                val creationRevision = creationResponse.optString("rev").orEmpty()
                if (resourceId.isBlank() || creationRevision.isBlank()) {
                    setUploadLoadingVisible(false)
                    Toast.makeText(context, getString(R.string.dashboard_resources_error_invalid_server_response), Toast.LENGTH_SHORT).show()
                    return@onSuccess
                }
                val renamedFileName = "${resourceId}.${fileExtension.lowercase(Locale.ROOT)}"
                val normalizedMediaType = normalizeResourceMediaType(mimeType)
                val updatePayload = JSONObject(payload.toString())
                    .put("_id", resourceId)
                    .put("_rev", creationRevision)
                    .put("filename", renamedFileName)
                    .put("mediaType", normalizedMediaType)
                val updateResult = repository.updateResourceDocument(
                    baseUrl = resolvedBaseUrl,
                    sessionCookie = sessionCookie,
                    username = credentials?.username,
                    password = credentials?.password,
                    resourceId = resourceId,
                    payload = updatePayload
                )
                val updateResponse = updateResult.getOrElse { error ->
                    setUploadLoadingVisible(false)
                    Toast.makeText(context, error.message ?: getString(R.string.course_wizard_play_error), Toast.LENGTH_SHORT).show()
                    return@onSuccess
                }
                val updateRevision = updateResponse.optString("rev").orEmpty().ifBlank { creationRevision }
                val uploadResult = repository.uploadResourceAttachment(
                    baseUrl = resolvedBaseUrl,
                    sessionCookie = sessionCookie,
                    username = credentials?.username,
                    password = credentials?.password,
                    resourceId = resourceId,
                    filename = renamedFileName,
                    revision = updateRevision,
                    mimeType = mimeType,
                    bytes = bytes
                )
                uploadResult.onSuccess {
                    if (isTeamResourcesTab) {
                        val teamId = DashboardTeamSelectionPreferences.getSelectedTeamId(context)
                        if (!teamId.isNullOrBlank()) {
                            val linkPayload = JSONObject()
                            linkPayload.put("resourceId", resourceId)
                            linkPayload.put("sourcePlanet", planetCode)
                            linkPayload.put("title", payload.optString("title"))
                            linkPayload.put("teamId", teamId)
                            linkPayload.put("teamPlanetCode", planetCode)
                            linkPayload.put("teamType", "local")
                            linkPayload.put("docType", "resourceLink")
                            repository.createTeamDocument(
                                baseUrl = resolvedBaseUrl,
                                sessionCookie = sessionCookie,
                                username = credentials?.username,
                                password = credentials?.password,
                                payload = linkPayload
                            )
                        }
                    }
                    setUploadLoadingVisible(false)
                    onSuccess()
                }.onFailure { error ->
                    setUploadLoadingVisible(false)
                    Toast.makeText(context, error.message ?: getString(R.string.course_wizard_play_error), Toast.LENGTH_SHORT).show()
                }
            }.onFailure { error ->
                setUploadLoadingVisible(false)
                Toast.makeText(context, error.message ?: getString(R.string.course_wizard_play_error), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun sanitizeResourceName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9\\-_]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
            .ifBlank { "resource_" + System.currentTimeMillis() }
    }

    private fun setUploadLoadingVisible(visible: Boolean) {
        uploadLoadingView?.isVisible = visible
    }

    private fun applyWebCompatibleResourceDefaults(payload: JSONObject) {
        if (!payload.has("author")) payload.put("author", "")
        if (!payload.has("year")) payload.put("year", "")
        if (!payload.has("publisher")) payload.put("publisher", "")
        if (!payload.has("linkToLicense")) payload.put("linkToLicense", "")
        if (!payload.has("openWith")) payload.put("openWith", "")
        if (!payload.has("resourceFor")) payload.put("resourceFor", JSONArray())
        if (!payload.has("medium")) payload.put("medium", "")
        if (!payload.has("resourceType")) payload.put("resourceType", "")
    }

    private fun normalizeResourceMediaType(mimeType: String): String {
        val normalized = mimeType.lowercase(Locale.ROOT)
        return when {
            normalized.contains("image") -> "image"
            normalized.contains("video") -> "video"
            normalized.contains("audio") -> "audio"
            normalized.contains("pdf") -> "pdf"
            else -> normalized
        }
    }

    private fun resolveFileName(uri: Uri): String {
        var name = ""
        val cursor: Cursor? = runCatching {
            requireContext().contentResolver.query(uri, null, null, null, null)
        }.getOrNull()
        cursor?.use {
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && it.moveToFirst()) {
                name = it.getString(nameIndex).orEmpty()
            }
        }
        return name.ifBlank { uri.lastPathSegment.orEmpty().substringAfterLast('/') }
    }

    private fun readBytesFromUri(uri: Uri): ByteArray? {
        return runCatching {
            requireContext().contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()
    }

    private fun resolveImageSize(uri: Uri): Pair<Int, Int>? {
        return runCatching {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            requireContext().contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
            if (options.outWidth > 0 && options.outHeight > 0) {
                options.outWidth to options.outHeight
            } else {
                null
            }
        }.getOrNull()
    }

    private fun buildResizedImageBytes(uri: Uri, percent: Float, mimeType: String): ByteArray? {
        return runCatching {
            val sourceBitmap = requireContext().contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            } ?: return@runCatching null
            val factor = (percent / 100f).coerceIn(0.4f, 1f)
            val targetWidth = max(1, (sourceBitmap.width * factor).roundToInt())
            val targetHeight = max(1, (sourceBitmap.height * factor).roundToInt())
            val scaled = if (targetWidth == sourceBitmap.width && targetHeight == sourceBitmap.height) {
                sourceBitmap
            } else {
                sourceBitmap.scale(targetWidth, targetHeight, true)
            }
            val output = ByteArrayOutputStream()
            val (format, quality) = if (mimeType.contains("png", ignoreCase = true)) {
                Bitmap.CompressFormat.PNG to 100
            } else {
                Bitmap.CompressFormat.JPEG to 90
            }
            scaled.compress(format, quality, output)
            if (scaled !== sourceBitmap) {
                scaled.recycle()
            }
            sourceBitmap.recycle()
            output.toByteArray()
        }.getOrNull()
    }

    private fun extensionForImageMimeType(mimeType: String): String {
        val lower = mimeType.lowercase(Locale.ROOT)
        return when {
            lower.contains("png") -> "png"
            lower.contains("webp") -> "webp"
            else -> "jpg"
        }
    }

    private fun resolveVideoDimensions(uri: Uri): Pair<Int, Int> {
        return runCatching {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(requireContext(), uri)
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1280
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 720
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            retriever.release()
            if (rotation == 90 || rotation == 270) {
                height to width
            } else {
                width to height
            }
        }.getOrDefault(1280 to 720)
    }

    private fun resolveAudioBitrate(uri: Uri): Int? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(requireContext(), uri)
            val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()
            bitrate?.let { it / 1000 }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private suspend fun extractWaveform(uri: Uri): FloatArray {
        return withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            val extractor = MediaExtractor()
            var codec: MediaCodec? = null
            try {
                retriever.setDataSource(requireContext(), uri)
                val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L

                if (durationMs <= 0) return@withContext floatArrayOf()

                extractor.setDataSource(requireContext(), uri, null)

                var audioTrackIndex = -1
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                    if (mime.startsWith("audio/")) {
                        audioTrackIndex = i
                        break
                    }
                }

                if (audioTrackIndex == -1) return@withContext floatArrayOf()

                extractor.selectTrack(audioTrackIndex)
                val format = extractor.getTrackFormat(audioTrackIndex)
                val mime = format.getString(MediaFormat.KEY_MIME)!!
                codec = MediaCodec.createDecoderByType(mime)
                codec.configure(format, null, null, 0)
                codec.start()

                val info = MediaCodec.BufferInfo()
                val amplitudes = mutableListOf<Float>()
                var sawOutputEOS = false
                val sampleTargetCount = 200

                while (!sawOutputEOS && coroutineContext.isActive) {
                    val inputBufferIndex = codec.dequeueInputBuffer(10000)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputBufferIndex)!!
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        } else {
                            codec.queueInputBuffer(inputBufferIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }

                    val outputBufferIndex = codec.dequeueOutputBuffer(info, 10000)
                    if (outputBufferIndex >= 0) {
                        val outputBuffer = codec.getOutputBuffer(outputBufferIndex)!!
                        if (info.size > 0) {
                            val pcmData = ShortArray(info.size / 2)
                            outputBuffer.asShortBuffer().get(pcmData)
                            var maxAbs = 0
                            for (s in pcmData) {
                                val abs = kotlin.math.abs(s.toInt())
                                if (abs > maxAbs) maxAbs = abs
                            }
                            amplitudes.add(maxAbs.toFloat() / Short.MAX_VALUE)
                        }
                        codec.releaseOutputBuffer(outputBufferIndex, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            sawOutputEOS = true
                        }
                    }
                }

                if (amplitudes.size > sampleTargetCount) {
                    val step = amplitudes.size / sampleTargetCount
                    FloatArray(sampleTargetCount) { i -> amplitudes.getOrNull(i * step) ?: 0f }
                } else {
                    amplitudes.toFloatArray()
                }
            } catch (_: Exception) {
                floatArrayOf()
            } finally {
                runCatching { retriever.release() }
                runCatching { extractor.release() }
                runCatching {
                    codec?.stop()
                    codec?.release()
                }
            }
        }
    }

    private fun resolveVideoDurationMs(uri: Uri): Long {
        return runCatching {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(requireContext(), uri)
            val raw = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            raw?.toLongOrNull() ?: 0L
        }.getOrDefault(0L)
    }

    private fun allowedVideoHeights(sourceHeight: Int): List<Int> {
        return when {
            sourceHeight >= 1080 -> listOf(480, 576, 720, 1080)
            sourceHeight >= 720 -> listOf(480, 576, 720)
            sourceHeight >= 576 -> listOf(480, 576)
            else -> listOf(480)
        }
    }

    private fun defaultVideoHeightSelection(sourceHeight: Int): Int {
        return when {
            sourceHeight >= 1080 -> 720
            sourceHeight >= 720 -> 720
            sourceHeight >= 576 -> 576
            else -> 480
        }
    }

    private fun resolveFileSizeBytes(uri: Uri): Long? {
        return runCatching {
            requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && cursor.moveToFirst()) cursor.getLong(sizeIndex) else null
            }
        }.getOrNull()
    }

    private fun buildVideoSizeEstimateText(
        sourceSizeBytes: Long?,
        sourceHeight: Int,
        selectedHeight: Int,
        sourceDurationMs: Long,
        selectedStartMs: Long,
        selectedEndMs: Long
    ): String {
        if (sourceSizeBytes == null || sourceSizeBytes <= 0L) {
            return getString(R.string.dashboard_resources_video_size_unknown)
        }
        val estimatedBytes = estimateVideoUploadSizeBytes(
            sourceSizeBytes = sourceSizeBytes,
            sourceHeight = sourceHeight,
            selectedHeight = selectedHeight,
            sourceDurationMs = sourceDurationMs,
            selectedStartMs = selectedStartMs,
            selectedEndMs = selectedEndMs
        )
        val formattedSize = Formatter.formatShortFileSize(requireContext(), estimatedBytes)
        return getString(R.string.dashboard_resources_video_size_estimate, formattedSize)
    }

    private fun formatDurationMs(durationMs: Long): String {
        val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    private fun estimateAudioUploadSizeBytes(bitrateKbps: Int, durationMs: Long): Long {
        val durationSeconds = durationMs / 1000.0
        val bitsPerSecond = bitrateKbps * 1000.0
        val totalBits = bitsPerSecond * durationSeconds
        val bytes = totalBits / 8.0
        return (bytes * 1.05).toLong().coerceAtLeast(1024L)
    }

    private fun estimateVideoUploadSizeBytes(
        sourceSizeBytes: Long,
        sourceHeight: Int,
        selectedHeight: Int,
        sourceDurationMs: Long,
        selectedStartMs: Long,
        selectedEndMs: Long
    ): Long {
        val durationFactor = if (sourceDurationMs <= 0L) {
            1.0
        } else {
            val selectedDuration = (selectedEndMs - selectedStartMs).coerceAtLeast(0L)
            (selectedDuration.toDouble() / sourceDurationMs.toDouble()).coerceIn(0.0, 1.0)
        }
        val resolutionFactor = if (sourceHeight <= 0 || selectedHeight >= sourceHeight) {
            1.0
        } else {
            val ratio = selectedHeight.toDouble() / sourceHeight.toDouble()
            ratio * ratio
        }
        val estimated = (sourceSizeBytes * resolutionFactor * durationFactor * 0.95).toLong()
        return estimated.coerceAtLeast(64L * 1024L)
    }

    @OptIn(markerClass = [UnstableApi::class])
    private suspend fun transcodeAudio(
        uri: Uri,
        bitrateKbps: Int,
        startMs: Long,
        endMs: Long,
        sourceDurationMs: Long
    ): ByteArray? {
        val context = requireContext()
        val inputFile = withContext(Dispatchers.IO) { copyUriToTempFile(uri) }
        val outputFile = withContext(Dispatchers.IO) {
            File.createTempFile("resource_output_audio", ".mp3", context.cacheDir)
        }

        return try {
            val exportSucceeded = suspendCancellableCoroutine { continuation ->
                val clippingBuilder = MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(startMs.coerceAtLeast(0L))
                if (endMs in 1 until sourceDurationMs) {
                    clippingBuilder.setEndPositionMs(endMs)
                }
                val mediaItem = MediaItem.Builder()
                    .setUri(Uri.fromFile(inputFile))
                    .setClippingConfiguration(clippingBuilder.build())
                    .build()
                val audioEncoderSettings = AudioEncoderSettings.Builder()
                    .setBitrate(bitrateKbps * 1000)
                    .build()
                val editedMediaItem = EditedMediaItem.Builder(mediaItem).build()
                val transformer = Transformer.Builder(context)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .setEncoderFactory(
                        DefaultEncoderFactory.Builder(context)
                            .setRequestedAudioEncoderSettings(audioEncoderSettings)
                            .build()
                    )
                    .addListener(
                        object : Transformer.Listener {
                            override fun onCompleted(composition: Composition, result: ExportResult) {
                                if (continuation.isActive) {
                                    continuation.resume(true)
                                }
                            }

                            override fun onError(
                                composition: Composition,
                                result: ExportResult,
                                exception: ExportException
                            ) {
                                if (continuation.isActive) {
                                    continuation.resume(false)
                                }
                            }
                        }
                    )
                    .build()
                continuation.invokeOnCancellation { transformer.cancel() }
                transformer.start(editedMediaItem, outputFile.absolutePath)
            }
            if (!exportSucceeded) {
                null
            } else {
                withContext(Dispatchers.IO) { outputFile.readBytes() }
            }
        } catch (_: Throwable) {
            null
        } finally {
            withContext(Dispatchers.IO) {
                inputFile.delete()
                outputFile.delete()
            }
        }
    }

    @OptIn(markerClass = [UnstableApi::class])
    private suspend fun transcodeVideoToMp4(
        uri: Uri,
        selectedHeight: Int,
        sourceWidth: Int,
        sourceHeight: Int,
        startMs: Long,
        endMs: Long,
        sourceDurationMs: Long,
        rotationDegrees: Float
    ): ByteArray? {
        val context = requireContext()
        val inputFile = withContext(Dispatchers.IO) { copyUriToTempFile(uri) }
        val outputFile = withContext(Dispatchers.IO) {
            File.createTempFile("resource_output_video", ".mp4", context.cacheDir)
        }
        val shouldScale = selectedHeight < sourceHeight

        return try {
            val exportSucceeded = suspendCancellableCoroutine { continuation ->
                val clippingBuilder = MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(startMs.coerceAtLeast(0L))
                if (endMs in 1 until sourceDurationMs) {
                    clippingBuilder.setEndPositionMs(endMs)
                }
                val mediaItem = MediaItem.Builder()
                    .setUri(Uri.fromFile(inputFile))
                    .setClippingConfiguration(clippingBuilder.build())
                    .build()
                val editedMediaBuilder = EditedMediaItem.Builder(mediaItem)
                if (shouldScale || rotationDegrees != 0f) {
                    val videoEffects = mutableListOf<Effect>()
                    if (rotationDegrees != 0f) {
                        videoEffects += ScaleAndRotateTransformation.Builder()
                            .setRotationDegrees(rotationDegrees)
                            .build()
                    }
                    if (shouldScale) {
                        val normalizedRotation = ((rotationDegrees % 360f) + 360f) % 360f
                        val isQuarterTurn = normalizedRotation == 90f || normalizedRotation == 270f
                        val targetHeight = if (isQuarterTurn && sourceHeight > 0 && sourceWidth > 0) {
                            (selectedHeight.toFloat() * sourceWidth.toFloat() / sourceHeight.toFloat()).roundToInt()
                                .coerceAtLeast(1)
                        } else {
                            selectedHeight
                        }
                        videoEffects += Presentation.createForHeight(targetHeight)
                    }
                    editedMediaBuilder.setEffects(
                        Effects(
                            emptyList(),
                            videoEffects
                        )
                    )
                }
                val editedMediaItem = editedMediaBuilder.build()
                val transformer = Transformer.Builder(context)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .addListener(
                        object : Transformer.Listener {
                            override fun onCompleted(composition: Composition, result: ExportResult) {
                                if (continuation.isActive) {
                                    continuation.resume(true)
                                }
                            }

                            override fun onError(
                                composition: Composition,
                                result: ExportResult,
                                exception: ExportException
                            ) {
                                if (continuation.isActive) {
                                    continuation.resume(false)
                                }
                            }
                        }
                    )
                    .build()
                continuation.invokeOnCancellation { transformer.cancel() }
                transformer.start(editedMediaItem, outputFile.absolutePath)
            }
            if (!exportSucceeded) {
                null
            } else {
                withContext(Dispatchers.IO) { outputFile.readBytes() }
            }
        } catch (_: Throwable) {
            null
        } finally {
            withContext(Dispatchers.IO) {
                inputFile.delete()
                outputFile.delete()
            }
        }
    }

    private fun copyUriToTempFile(uri: Uri): File {
        val context = requireContext()
        val tempFile = File.createTempFile("resource_input_video", ".tmp", context.cacheDir)
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException(getString(R.string.dashboard_resources_error_read_file))
        return tempFile
    }

    private fun resolveDefaultLanguageIndex(options: List<String>): Int {
        val languageCode = runCatching {
            resources.configuration.locales[0]?.language?.lowercase(Locale.ROOT)
        }.getOrNull().orEmpty()
        val label = when (languageCode) {
            "ne" -> getString(R.string.language_name_nepali)
            "fr" -> getString(R.string.language_name_french)
            "es" -> getString(R.string.language_name_spanish)
            "ar" -> getString(R.string.language_name_arabic)
            "so" -> getString(R.string.language_name_somali)
            "hi" -> getString(R.string.language_name_hindi)
            "pt" -> getString(R.string.language_name_portuguese)
            else -> getString(R.string.language_name_english)
        }
        return options.indexOf(label).takeIf { it >= 0 }
            ?: options.indexOf(getString(R.string.language_name_english)).takeIf { it >= 0 }
            ?: 0
    }

    private fun requiredPermissionsFor(action: ResourceMenuAction): List<String> {
        val permissions = linkedSetOf<String>()
        when (action) {
            ResourceMenuAction.AddImage -> {
                permissions += if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.READ_MEDIA_IMAGES
                } else {
                    Manifest.permission.READ_EXTERNAL_STORAGE
                }
            }
            ResourceMenuAction.AddVideo -> {
                permissions += if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.READ_MEDIA_VIDEO
                } else {
                    Manifest.permission.READ_EXTERNAL_STORAGE
                }
            }
            ResourceMenuAction.AddAudio -> {
                permissions += if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.READ_MEDIA_AUDIO
                } else {
                    Manifest.permission.READ_EXTERNAL_STORAGE
                }
            }
            ResourceMenuAction.AddPdf -> {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                    permissions += Manifest.permission.READ_EXTERNAL_STORAGE
                }
            }
            ResourceMenuAction.TakePhoto -> {
                permissions += Manifest.permission.CAMERA
            }
            ResourceMenuAction.TakeVideo -> {
                permissions += Manifest.permission.CAMERA
                permissions += Manifest.permission.RECORD_AUDIO
            }
            ResourceMenuAction.RecordAudio -> {
                permissions += Manifest.permission.RECORD_AUDIO
            }
        }
        return permissions.toList()
    }

    private enum class ResourceMenuAction(val menuItemId: Int) {
        AddPdf(R.id.action_add_pdf),
        AddImage(R.id.action_add_image),
        AddVideo(R.id.action_add_video),
        AddAudio(R.id.action_add_audio),
        TakePhoto(R.id.action_take_photo),
        TakeVideo(R.id.action_take_video),
        RecordAudio(R.id.action_record_audio);

        companion object {
            fun fromMenuItemId(menuItemId: Int): ResourceMenuAction? {
                return entries.firstOrNull { it.menuItemId == menuItemId }
            }
        }
    }

    private fun forceShowMenuIcons(popup: PopupMenu) {
        runCatching {
            val popupField = PopupMenu::class.java.getDeclaredField("mPopup")
            popupField.isAccessible = true
            val menuPopupHelper = popupField.get(popup)
            val setForceIcons = menuPopupHelper.javaClass.getDeclaredMethod(
                "setForceShowIcon",
                Boolean::class.javaPrimitiveType
            )
            setForceIcons.invoke(menuPopupHelper, true)
        }
    }

    private class RecordingWaveformView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.RED
            style = Paint.Style.STROKE
            strokeWidth = 4f
            alpha = 100
        }
        private val amplitudes = mutableListOf<Float>()
        private val maxSamples = 50

        fun addAmplitude(amplitude: Float) {
            amplitudes.add(amplitude)
            if (amplitudes.size > maxSamples) {
                amplitudes.removeAt(0)
            }
            invalidate()
        }

        fun clear() {
            amplitudes.clear()
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (amplitudes.isEmpty()) return
            val w = width.toFloat()
            val h = height.toFloat()
            val centerY = h / 2
            val step = w / maxSamples
            val startX = w - (amplitudes.size * step)

            for (i in 0 until amplitudes.size - 1) {
                val x1 = startX + i * step
                val y1 = centerY - (amplitudes[i] * h / 2)
                val x2 = startX + (i + 1) * step
                val y2 = centerY - (amplitudes[i + 1] * h / 2)
                canvas.drawLine(x1, y1, x2, y2, paint)
                canvas.drawLine(x1, centerY + (centerY - y1), x2, centerY + (centerY - y2), paint)
            }
        }
    }
}
