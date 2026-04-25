package org.ole.planet.myplanet.lite

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.os.BundleCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.File
import java.util.ArrayList
import java.util.Locale
import kotlinx.coroutines.launch
import okhttp3.Credentials
import org.ole.planet.myplanet.lite.dashboard.DashboardImagePreviewActivity
import org.ole.planet.myplanet.lite.dashboard.DashboardResourcesRepository
import org.ole.planet.myplanet.lite.dashboard.DashboardServerPreferences
import org.ole.planet.myplanet.lite.dashboard.DashboardTeamSelectionPreferences
import org.ole.planet.myplanet.lite.databinding.ItemDashboardResourceExplorerBinding
import org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore

class DashboardResourcesPageFragment : Fragment(R.layout.fragment_dashboard_resources_page) {
    companion object {
        private const val ARG_IS_TEAM_RESOURCES = "arg_is_team_resources"
        internal const val MAIN_RESOURCES_PAGE_SIZE = 1000
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

    internal var isTeamResourcesTab: Boolean = false
    internal var contentView: LinearLayout? = null
    internal var emptyView: TextView? = null
    internal var resourcesList: RecyclerView? = null
    internal var swipeRefreshLayout: SwipeRefreshLayout? = null
    internal var uploadLoadingView: View? = null
    internal var addResourceFab: FloatingActionButton? = null
    internal val repository = DashboardResourcesRepository()
    internal val downloadService by lazy {
        ResourceDownloadService(requireContext().applicationContext, DOWNLOADED_PREFS, KEY_DOWNLOADED_RESOURCES)
    }
    internal val resourceSyncService by lazy {
        ResourceSyncService(repository = repository, downloadService = downloadService)
    }
    internal val mainResourcesItems = mutableListOf<ResourceUi>()
    internal val teamResourcesItems = mutableListOf<ResourceUi>()
    internal var mainResourcesSkip = 0
    internal var isLoadingMainResources = false
    internal var hasMoreMainResources = true
    internal var hasLoadedMainResources = false
    internal var hasLoadedTeamResources = false
    internal var isLoadingTeamResources = false
    internal var sessionCookie: String? = null
    internal var baseUrl: String? = null
    internal var searchQuery: String = ""
    internal var selectedMediaType: String? = null
    internal var isSortDescending: Boolean = false
    internal var selectedSortBy: ResourceSortBy = ResourceSortBy.NAME
    internal var latestPhotoUri: Uri? = null
    private var pendingPermissionAction: ResourceMenuAction? = null
    internal var isFabMenuSpinActive: Boolean = false
    internal var spinningFab: View? = null
    internal var latestVideoUri: Uri? = null
    internal var mediaRecorder: MediaRecorder? = null
    internal var currentAudioFile: File? = null
    internal var recordingStartTime: Long = 0L
    internal val recordingTimerHandler = Handler(Looper.getMainLooper())
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
    internal val pickPdfLauncher = registerForActivityResult(
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
    internal val pickImageLauncher = registerForActivityResult(
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
    internal val pickVideoLauncher = registerForActivityResult(
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
    internal val pickAudioLauncher = registerForActivityResult(
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

    internal val takePhotoLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = latestPhotoUri
        if (success && uri != null) {
            showImageMetadataPopup(uri)
        }
    }

    internal val takeVideoLauncher = registerForActivityResult(
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
        setupResourcesView(view)
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

    internal class ResourceExplorerAdapter(
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

    internal fun hasSelectedTeam(): Boolean {
        val teamId = DashboardTeamSelectionPreferences.getSelectedTeamId(requireContext())
        val teamName = DashboardTeamSelectionPreferences.getSelectedTeamName(requireContext())
        return !teamId.isNullOrBlank() && !teamName.isNullOrBlank()
    }

    internal fun onSecondaryAction(item: ResourceUi) {
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

    internal fun downloadResource(item: ResourceUi) {
        val currentBaseUrl = baseUrl
        if (currentBaseUrl.isNullOrBlank()) {
            Toast.makeText(requireContext(), getString(R.string.course_wizard_play_error), Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            val wasDownloaded = ResourceDownloadUiDelegate.downloadResource(
                baseUrl = currentBaseUrl,
                sessionCookie = sessionCookie,
                item = item,
                resourceSyncService = resourceSyncService
            )
            if (wasDownloaded) {
                markResourceDownloadState(item, downloaded = true)
            } else {
                Toast.makeText(requireContext(), getString(R.string.course_wizard_play_error), Toast.LENGTH_SHORT).show()
            }
        }
    }

    internal fun deleteDownloadedResource(item: ResourceUi) {
        ResourceDownloadUiDelegate.deleteDownloadedResource(downloadService, item)
        markResourceDownloadState(item, downloaded = false)
    }

    internal fun markResourceDownloadState(item: ResourceUi, downloaded: Boolean) {
        ResourceDownloadUiDelegate.markResourceDownloadState(
            mainResourcesItems = mainResourcesItems,
            teamResourcesItems = teamResourcesItems,
            item = item,
            downloaded = downloaded,
            selectedSortBy = selectedSortBy,
            isSortDescending = isSortDescending
        )
        val list = resourcesList ?: return
        val adapter = list.adapter as? ResourceExplorerAdapter
        val source = if (isTeamResourcesTab) teamResourcesItems else mainResourcesItems
        if (adapter == null) {
            list.adapter = ResourceExplorerAdapter(source.toList(), ::openResource, ::onSecondaryAction)
        } else {
            adapter.replaceResources(source)
        }
    }

    internal fun loadDownloadedResourcesFiltered(): List<ResourceUi> {
        return ResourceDownloadUiDelegate.loadDownloadedResourcesFiltered(
            downloadService = downloadService,
            isTeamResourcesTab = isTeamResourcesTab,
            searchQuery = searchQuery,
            selectedMediaType = selectedMediaType
        )
    }

    internal fun openResource(item: ResourceUi) {
        val mediaType = item.type.lowercase(Locale.ROOT)
        val resourceUri = downloadService.resolveResourceUri(
            baseUrl = DashboardServerPreferences.getServerBaseUrl(requireContext()),
            item = item
        )
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

    private fun resolveAuthHeader(): String? {
        val credentials = ProfileCredentialsStore.getStoredCredentials(requireContext().applicationContext)
        return credentials?.let { Credentials.basic(it.username, it.password) }
    }

    internal fun handleResourceMenuAction(action: ResourceMenuAction) {
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

    internal fun captureVideo() {
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

}
