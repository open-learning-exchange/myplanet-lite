/**
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-12-12
 */

package org.ole.planet.myplanet.lite

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.ole.planet.myplanet.lite.auth.AuthDependencies
import org.ole.planet.myplanet.lite.dashboard.CreateVoiceActivity
import org.ole.planet.myplanet.lite.dashboard.DashboardAvatarLoader
import org.ole.planet.myplanet.lite.dashboard.DashboardImagePreviewActivity
import org.ole.planet.myplanet.lite.dashboard.DashboardNewsActionsRepository
import org.ole.planet.myplanet.lite.dashboard.DashboardNewsRepository
import org.ole.planet.myplanet.lite.dashboard.DashboardNewsRepository.NewsDocument
import org.ole.planet.myplanet.lite.dashboard.DashboardNewsRepository.NewsPage
import org.ole.planet.myplanet.lite.dashboard.DashboardPostDetailActivity
import org.ole.planet.myplanet.lite.dashboard.DashboardPostImageLoader
import org.ole.planet.myplanet.lite.dashboard.DashboardServerPreferences
import org.ole.planet.myplanet.lite.dashboard.DashboardTeamMemberProfileActivity
import org.ole.planet.myplanet.lite.dashboard.PostShareHelper
import org.ole.planet.myplanet.lite.profile.AvatarUpdateNotifier
import org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore
import org.ole.planet.myplanet.lite.profile.StoredCredentials
import org.ole.planet.myplanet.lite.profile.UserProfile
import org.ole.planet.myplanet.lite.profile.UserProfileDatabase
import org.ole.planet.myplanet.lite.util.enableDrag

import io.noties.markwon.Markwon

import kotlin.math.max

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import java.text.DecimalFormat
import java.util.ArrayList

class DashboardVoicesFragment : Fragment(R.layout.fragment_dashboard_voices) {

    private lateinit var recyclerView: RecyclerView
    private lateinit var loadingView: View
    private lateinit var emptyView: TextView
    private lateinit var markwon: Markwon
    private lateinit var adapter: DashboardNewsAdapter
    private val createVoiceLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            if (::recyclerView.isInitialized) {
                recyclerView.post { recyclerView.scrollToPosition(0) }
            }
            loadInitial()
        }
    }

    private val postDetailLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val deletedId = result.data?.getStringExtra(DashboardPostDetailActivity.EXTRA_DELETED_POST_ID)
            if (deletedId != null) {
                handlePostDeleted(deletedId)
            } else {
                if (::recyclerView.isInitialized) {
                    recyclerView.post { recyclerView.scrollToPosition(0) }
                }
                loadInitial()
            }
        }
    }

    private val repository = DashboardNewsRepository()
    private val actionsRepository = DashboardNewsActionsRepository()
    private val items = mutableListOf<DashboardNewsItem>()
    private val commentCounts = mutableMapOf<String, Int>()
    private var avatarLoader: DashboardAvatarLoader? = null
    private var postImageLoader: DashboardPostImageLoader? = null
    private var postShareHelper: PostShareHelper? = null
    private var avatarUpdateListener: AvatarUpdateNotifier.Listener? = null
    private var baseUrl: String? = null
    private var sessionCookie: String? = null
    private var credentials: StoredCredentials? = null
    private var serverCode: String? = null
    private var serverParentCode: String? = null
    private var currentUsername: String? = null
    private var isUserAdmin: Boolean = false
    private var teamId: String? = null
    private var teamName: String? = null

    private var isLoading = false
    private var hasMore = true
    private var nextSkip = 0
    private var nextBookmark: String? = null
    private var currentEmptyMessage: Int = R.string.dashboard_voices_empty
    private var pageSize: Int = DashboardActivity.VOICE_PAGE_SIZE_OPTIONS[1]

    override fun onAttach(context: Context) {
        super.onAttach(context)
        pageSize = DashboardActivity.getVoicePageSizePreference(context)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView = view.findViewById(R.id.postsRecyclerView)
        loadingView = view.findViewById(R.id.postsLoading)
        emptyView = view.findViewById(R.id.postsEmptyView)
        val fab: FloatingActionButton = view.findViewById(R.id.addVoiceFab)
        fab.setOnClickListener {
            animateFabClick(fab)
            openCreateVoiceComposer()
        }
        fab.enableDrag()

        markwon = Markwon.builder(requireContext()).build()
        adapter = DashboardNewsAdapter(
            markwon,
            avatarBinder = { imageView, username, hasAvatar ->
                avatarLoader?.bind(imageView, username, hasAvatar)
            },
            imageBinder = { imageView, imagePath ->
                val loader = postImageLoader
                if (loader != null) {
                    loader.bind(imageView, imagePath)
                } else {
                    imageView.isVisible = false
                    imageView.setImageDrawable(null)
                }
            },
            onImageClicked = { item, index ->
                openImagePreview(item, index)
            },
            onPostClicked = { item ->
                openPostDetail(item)
            },
            onDeleteClicked = { item ->
                requestDeletePost(item)
            },
            onShareClicked = { item ->
                sharePost(item)
            },
            onEditClicked = { item ->
                openEditVoice(item)
            },
            onAuthorClicked = { item ->
                openTeamMemberProfile(item)
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (dy <= 0) {
                    return
                }
                val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
                if (!isLoading && hasMore && totalItemCount > 0) {
                    if (firstVisibleItemPosition + visibleItemCount >= totalItemCount - LOAD_MORE_THRESHOLD) {
                        loadMore(pageSize)
                    }
                }
            }
        })

        viewLifecycleOwner.lifecycleScope.launch {
            initializeSession()
            val profile = loadCurrentUserProfile()
            currentUsername = profile?.username
            isUserAdmin = profile?.isUserAdmin == true
            val currentBaseUrl = baseUrl
            if (currentBaseUrl.isNullOrEmpty()) {
                showEmptyState(R.string.dashboard_voices_no_server)
                updateLoadingVisibility()
                return@launch
            }
            avatarLoader = DashboardAvatarLoader(currentBaseUrl, sessionCookie, credentials, viewLifecycleOwner.lifecycleScope)
            avatarUpdateListener = AvatarUpdateNotifier.register(AvatarUpdateNotifier.Listener { username ->
                handleAvatarUpdated(username)
            })
            postImageLoader = DashboardPostImageLoader(currentBaseUrl, sessionCookie, viewLifecycleOwner.lifecycleScope)
            postShareHelper = PostShareHelper(
                requireContext().applicationContext,
                { currentBaseUrl },
                { sessionCookie },
                { serverCode ?: Uri.parse(currentBaseUrl).host }
            )
            loadInitial()
        }
    }

    private fun animateFabClick(fab: FloatingActionButton) {
        fab.animate()
            .rotationBy(360f)
            .setDuration(250)
            .withEndAction { fab.rotation = 0f }
            .start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        recyclerView.adapter = null
        avatarLoader?.destroy()
        avatarLoader = null
        postImageLoader = null
        postShareHelper = null
        AvatarUpdateNotifier.unregister(avatarUpdateListener)
        avatarUpdateListener = null
    }

    private fun handleAvatarUpdated(username: String) {
        if (!::adapter.isInitialized) {
            return
        }
        val positions = adapter.currentList.mapIndexedNotNull { index, item ->
            if (item.username?.equals(username, ignoreCase = true) == true) {
                index
            } else {
                null
            }
        }
        if (positions.isEmpty()) {
            return
        }
        recyclerView.post {
            positions.forEach { position ->
                adapter.notifyItemChanged(position)
            }
        }
    }

    private suspend fun initializeSession() {
        val context = requireContext().applicationContext
        baseUrl = DashboardServerPreferences.getServerBaseUrl(context)
        credentials = ProfileCredentialsStore.getStoredCredentials(context)
        serverCode = DashboardServerPreferences.getServerCode(context)
        serverParentCode = DashboardServerPreferences.getServerParentCode(context)
        teamId = arguments?.getString(ARG_TEAM_ID)
        teamName = arguments?.getString(ARG_TEAM_NAME)
        baseUrl?.let { base ->
            val authService = AuthDependencies.provideAuthService(context, base)
            sessionCookie = authService.getStoredToken()
        }
    }

    private fun loadInitial() {
        pageSize = DashboardActivity.getVoicePageSizePreference(requireContext())
        items.clear()
        adapter.submitList(items.toList())
        hasMore = true
        nextSkip = 0
        nextBookmark = null
        commentCounts.clear()
        currentEmptyMessage = R.string.dashboard_voices_empty
        loadMore(pageSize)
    }

    private fun loadMore(targetPosts: Int = pageSize) {
        val base = baseUrl ?: return
        if (isLoading || !hasMore) {
            return
        }
        isLoading = true
        updateLoadingVisibility()
        viewLifecycleOwner.lifecycleScope.launch {
            var accumulatedPosts = 0
            var shouldContinue = true
            while (shouldContinue) {
                val result = repository.fetchNews(
                    base,
                    sessionCookie,
                    nextSkip,
                    nextBookmark,
                    pageSize,
                    serverCode,
                    serverParentCode,
                    teamName
                )
                val addedPosts = result.fold(
                    onSuccess = { page ->
                        handlePage(page)
                    },
                    onFailure = {
                        handleLoadError()
                        -1
                    }
                )
                if (addedPosts < 0) {
                    break
                }
                accumulatedPosts += addedPosts
                shouldContinue = hasMore && accumulatedPosts < targetPosts
            }
            isLoading = false
            updateLoadingVisibility()
        }
    }

    private fun handlePage(page: NewsPage): Int {
        nextSkip += page.consumed
        nextBookmark = page.bookmark
        hasMore = page.hasMore && (page.consumed > 0 || !nextBookmark.isNullOrEmpty())
        var shouldUpdateAdapter = false
        var newPostsCount = 0
        if (page.commentCounts.isNotEmpty()) {
            page.commentCounts.forEach { (parentId, count) ->
                if (!parentId.isNullOrEmpty()) {
                    val current = commentCounts[parentId] ?: 0
                    commentCounts[parentId] = current + count
                }
            }
            for (index in items.indices) {
                val existing = items[index]
                val updatedCount = commentCounts[existing.id] ?: existing.commentCount
                if (updatedCount != existing.commentCount) {
                    items[index] = existing.copy(commentCount = updatedCount)
                    shouldUpdateAdapter = true
                }
            }
        }
        val mapped = page.items.mapNotNull { document ->
            val id = document.id ?: return@mapNotNull null
            val commentCount = commentCounts[id] ?: 0
            mapToItem(document, commentCount)
        }
        if (mapped.isNotEmpty()) {
            items.addAll(mapped)
            shouldUpdateAdapter = true
            emptyView.isVisible = false
            newPostsCount = mapped.size
        }
        if (shouldUpdateAdapter) {
            adapter.submitList(items.toList())
        }
        if (items.isEmpty()) {
            currentEmptyMessage = if (hasMore) 0 else R.string.dashboard_voices_empty
        } else {
            emptyView.isVisible = false
        }
        return newPostsCount
    }

    private fun handleLoadError() {
        if (items.isEmpty()) {
            showEmptyState(R.string.dashboard_voices_error)
        } else {
            Toast.makeText(requireContext(), R.string.dashboard_voices_error_toast, Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestDeletePost(item: DashboardNewsItem) {
        val base = baseUrl
        if (base.isNullOrBlank()) {
            Toast.makeText(requireContext(), R.string.dashboard_voices_no_server, Toast.LENGTH_SHORT).show()
            return
        }
        val cookie = sessionCookie
        if (cookie.isNullOrBlank()) {
            Toast.makeText(requireContext(), R.string.dashboard_post_delete_error, Toast.LENGTH_SHORT).show()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val result = actionsRepository.deleteNews(
                base,
                cookie,
                item.document,
                teamId = teamId,
                teamName = teamName
            )
            result.onSuccess {
                Toast.makeText(requireContext(), R.string.dashboard_post_delete_success, Toast.LENGTH_SHORT).show()
                handlePostDeleted(item.id)
            }.onFailure {
                Toast.makeText(requireContext(), R.string.dashboard_post_delete_error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handlePostDeleted(postId: String) {
        val removed = items.removeAll { it.id == postId }
        if (removed) {
            adapter.submitList(items.toList())
            if (items.isEmpty()) {
                currentEmptyMessage = R.string.dashboard_voices_empty
                updateLoadingVisibility()
            }
        }
    }

    fun onPageSizeChanged(newPageSize: Int) {
        val normalized = DashboardActivity.VOICE_PAGE_SIZE_OPTIONS.firstOrNull { it == newPageSize }
            ?: DashboardActivity.VOICE_PAGE_SIZE_OPTIONS[1]
        if (normalized == pageSize) {
            return
        }
        pageSize = normalized
        loadInitial()
    }

    private fun updateLoadingVisibility() {
        loadingView.isVisible = isLoading && items.isEmpty()
        if (!isLoading && items.isEmpty() && currentEmptyMessage != 0) {
            emptyView.isVisible = true
            emptyView.setText(currentEmptyMessage)
        } else {
            emptyView.isVisible = false
        }
    }

    private fun openCreateVoiceComposer() {
        val context = context ?: return
        val intent = Intent(context, CreateVoiceActivity::class.java)
        teamId?.let { intent.putExtra(CreateVoiceActivity.EXTRA_TARGET_TEAM_ID, it) }
        teamName?.let { intent.putExtra(CreateVoiceActivity.EXTRA_TARGET_TEAM_NAME, it) }
        createVoiceLauncher.launch(intent)
    }

    private fun showEmptyState(messageRes: Int) {
        currentEmptyMessage = messageRes
        emptyView.setText(messageRes)
        emptyView.isVisible = true
    }

    private fun mapToItem(document: NewsDocument, commentCount: Int): DashboardNewsItem? {
        val id = document.id ?: return null
        val username = document.user?.name?.takeIf { it.isNotBlank() }
        val displayName = document.user?.let { user ->
            val parts = listOfNotNull(user.firstName, user.middleName, user.lastName)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            when {
                parts.isNotEmpty() -> TextUtils.join(" ", parts)
                !username.isNullOrEmpty() -> username
                else -> getString(R.string.dashboard_profile_name_placeholder)
            }
        } ?: (username ?: getString(R.string.dashboard_profile_name_placeholder))
        val timeMillis = document.time ?: 0L
        val relativeTime = formatRelativeTime(timeMillis)
        val metadata = buildMetadata(username, relativeTime)
        val rawMessage = document.message
        val messageImages = extractImagePaths(rawMessage)
        val documentImages = mapDocumentImages(document)
        val imagePaths = (messageImages + documentImages).distinct()
        val message = rawMessage?.trim()?.takeIf { it.isNotEmpty() }
        val hasAvatar = document.user?.attachments?.containsKey("img") == true
        val hasSession = !sessionCookie.isNullOrBlank()
        val isAuthor = hasSession && !username.isNullOrBlank() && currentUsername?.equals(username, ignoreCase = true) == true
        return DashboardNewsItem(
            id = id,
            author = displayName,
            username = username,
            metadata = metadata,
            message = message,
            hasAvatar = hasAvatar,
            imagePaths = imagePaths,
            commentCount = commentCount,
            timestamp = timeMillis,
            canEdit = isAuthor,
            canDelete = isAuthor || isUserAdmin,
            canShare = hasSession,
            document = document
        )
    }

    private fun extractImagePaths(raw: String?): List<String> {
        if (raw.isNullOrBlank()) {
            return emptyList()
        }
        return IMAGE_MARKDOWN_CAPTURE_REGEX.findAll(raw)
            .mapNotNull { match ->
                match.groupValues.getOrNull(1)?.trim().orEmpty().takeIf { it.isNotBlank() }
            }
            .toList()
    }

    private fun mapDocumentImages(document: NewsDocument): List<String> {
        return document.images
            ?.mapNotNull { image ->
                extractImagePath(image.markdown)
                    ?: buildResourcePath(image.resourceId, image.filename)
            }
            ?.filter { it.isNotBlank() }
            .orEmpty()
    }

    private fun extractImagePath(markdown: String?): String? {
        if (markdown.isNullOrBlank()) {
            return null
        }
        val match = IMAGE_MARKDOWN_CAPTURE_REGEX.find(markdown) ?: return null
        return match.groupValues.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun buildResourcePath(resourceId: String?, filename: String?): String? {
        val id = resourceId?.trim().takeUnless { it.isNullOrEmpty() }
        val name = filename?.trim().takeUnless { it.isNullOrEmpty() }
        if (id == null || name == null) {
            return null
        }
        return "resources/$id/$name"
    }

    private fun buildMetadata(username: String?, relativeTime: String): String {
        return if (!username.isNullOrBlank()) {
            "@${username.trim()} • $relativeTime"
        } else {
            relativeTime
        }
    }

    private fun formatRelativeTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diffMillis = max(0L, now - timestamp)
        val minutes = diffMillis / MINUTE_MILLIS
        val hours = diffMillis / HOUR_MILLIS
        val days = diffMillis / DAY_MILLIS
        val months = diffMillis / MONTH_MILLIS
        val years = diffMillis / YEAR_MILLIS
        return when {
            years >= 1 -> if (years == 1L) getString(R.string.dashboard_relative_time_year) else getString(
                R.string.dashboard_relative_time_years,
                years
            )
            months >= 1 -> if (months == 1L) getString(R.string.dashboard_relative_time_month) else getString(
                R.string.dashboard_relative_time_months,
                months
            )
            days >= 1 -> if (days == 1L) getString(R.string.dashboard_relative_time_day) else getString(
                R.string.dashboard_relative_time_days,
                days
            )
            hours >= 1 -> if (hours == 1L) getString(R.string.dashboard_relative_time_hour) else getString(
                R.string.dashboard_relative_time_hours,
                hours
            )
            minutes >= 1 -> if (minutes == 1L) getString(R.string.dashboard_relative_time_minute) else getString(
                R.string.dashboard_relative_time_minutes,
                minutes
            )
            else -> getString(R.string.dashboard_relative_time_seconds)
        }
    }

    private data class DashboardNewsItem(
        val id: String,
        val author: String,
        val username: String?,
        val metadata: String,
        val message: String?,
        val hasAvatar: Boolean,
        val imagePaths: List<String>,
        val commentCount: Int,
        val timestamp: Long,
        val canEdit: Boolean,
        val canDelete: Boolean,
        val canShare: Boolean,
        val document: NewsDocument
    )

    private suspend fun loadCurrentUserProfile(): UserProfile? {
        val context = requireContext().applicationContext
        return withContext(Dispatchers.IO) {
            UserProfileDatabase.getInstance(context).getProfile()
        }
    }

    private fun openImagePreview(item: DashboardNewsItem, index: Int) {
        val context = context ?: return
        if (item.imagePaths.isEmpty()) {
            return
        }
        val intent = Intent(context, DashboardImagePreviewActivity::class.java)
        intent.putStringArrayListExtra(
            DashboardImagePreviewActivity.EXTRA_IMAGE_PATHS,
            ArrayList(item.imagePaths)
        )
        intent.putExtra(DashboardImagePreviewActivity.EXTRA_START_INDEX, index)
        startActivity(intent)
    }

    private fun openPostDetail(item: DashboardNewsItem) {
        val context = context ?: return
        val intent = Intent(context, DashboardPostDetailActivity::class.java)
        intent.putExtra(DashboardPostDetailActivity.EXTRA_POST_ID, item.id)
        intent.putExtra(DashboardPostDetailActivity.EXTRA_AUTHOR, item.author)
        intent.putExtra(DashboardPostDetailActivity.EXTRA_USERNAME, item.username)
        intent.putExtra(DashboardPostDetailActivity.EXTRA_MESSAGE, item.message)
        intent.putStringArrayListExtra(
            DashboardPostDetailActivity.EXTRA_IMAGE_PATHS,
            ArrayList(item.imagePaths)
        )
        intent.putExtra(DashboardPostDetailActivity.EXTRA_HAS_AVATAR, item.hasAvatar)
        intent.putExtra(DashboardPostDetailActivity.EXTRA_TIMESTAMP, item.timestamp)
        intent.putExtra(DashboardPostDetailActivity.EXTRA_COMMENT_COUNT, item.commentCount)
        intent.putExtra(DashboardPostDetailActivity.EXTRA_DOCUMENT, item.document)
        teamId?.let { intent.putExtra(DashboardPostDetailActivity.EXTRA_TEAM_ID, it) }
        teamName?.let { intent.putExtra(DashboardPostDetailActivity.EXTRA_TEAM_NAME, it) }
        postDetailLauncher.launch(intent)
    }

    private fun sharePost(item: DashboardNewsItem) {
        val helper = postShareHelper ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            helper.sharePost(item.id, item.author, item.message, item.imagePaths)
        }
    }

    private fun openTeamMemberProfile(item: DashboardNewsItem) {
        val username = item.username
        if (username.isNullOrBlank()) {
            Toast.makeText(
                requireContext(),
                R.string.dashboard_team_members_profile_unavailable,
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        val displayName = item.author.ifBlank { username }
        val intent = DashboardTeamMemberProfileActivity.buildIntent(
            requireContext(),
            username,
            displayName,
            false
        )
        startActivity(intent)
    }

    private fun openEditVoice(item: DashboardNewsItem) {
        val context = context ?: return
        val intent = Intent(context, CreateVoiceActivity::class.java)
        intent.putExtra(CreateVoiceActivity.EXTRA_IS_EDIT_MODE, true)
        intent.putExtra(CreateVoiceActivity.EXTRA_EDIT_POST_ID, item.id)
        intent.putExtra(CreateVoiceActivity.EXTRA_EDIT_INITIAL_MESSAGE, item.message)
        intent.putStringArrayListExtra(
            CreateVoiceActivity.EXTRA_EDIT_INITIAL_IMAGE_PATHS,
            ArrayList(item.imagePaths)
        )
        intent.putExtra(CreateVoiceActivity.EXTRA_EDIT_DOCUMENT, item.document)
        teamId?.let { intent.putExtra(CreateVoiceActivity.EXTRA_TARGET_TEAM_ID, it) }
        teamName?.let { intent.putExtra(CreateVoiceActivity.EXTRA_TARGET_TEAM_NAME, it) }
        createVoiceLauncher.launch(intent)
    }

    fun isTeamFeedFor(id: String, name: String): Boolean {
        val sameId = teamId?.equals(id, ignoreCase = true) == true
        val sameName = teamName?.equals(name, ignoreCase = true) == true
        return sameId && sameName
    }

    private class DashboardNewsAdapter(
        private val markwon: Markwon,
        private val avatarBinder: (ImageView, String?, Boolean) -> Unit,
        private val imageBinder: (ImageView, String) -> Unit,
        private val onImageClicked: (DashboardNewsItem, Int) -> Unit,
        private val onPostClicked: (DashboardNewsItem) -> Unit,
        private val onDeleteClicked: (DashboardNewsItem) -> Unit,
        private val onShareClicked: (DashboardNewsItem) -> Unit,
        private val onEditClicked: (DashboardNewsItem) -> Unit,
        private val onAuthorClicked: (DashboardNewsItem) -> Unit
    ) : androidx.recyclerview.widget.ListAdapter<DashboardNewsItem, DashboardNewsViewHolder>(DIFF_CALLBACK) {

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): DashboardNewsViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_dashboard_post, parent, false)
            return DashboardNewsViewHolder(
                view,
                markwon,
                avatarBinder,
                imageBinder,
                onImageClicked,
                onPostClicked,
                onDeleteClicked,
                onShareClicked,
                onEditClicked,
                onAuthorClicked
            )
        }

        override fun onBindViewHolder(holder: DashboardNewsViewHolder, position: Int) {
            holder.bind(getItem(position))
        }

        companion object {
            private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<DashboardNewsItem>() {
                override fun areItemsTheSame(oldItem: DashboardNewsItem, newItem: DashboardNewsItem): Boolean {
                    return oldItem.id == newItem.id
                }

                override fun areContentsTheSame(oldItem: DashboardNewsItem, newItem: DashboardNewsItem): Boolean {
                    return oldItem == newItem
                }
            }
        }
    }

    private class DashboardNewsViewHolder(
        view: View,
        private val markwon: Markwon,
        private val avatarBinder: (ImageView, String?, Boolean) -> Unit,
        private val imageBinder: (ImageView, String) -> Unit,
        private val onImageClicked: (DashboardNewsItem, Int) -> Unit,
        private val onPostClicked: (DashboardNewsItem) -> Unit,
        private val onDeleteClicked: (DashboardNewsItem) -> Unit,
        private val onShareClicked: (DashboardNewsItem) -> Unit,
        private val onEditClicked: (DashboardNewsItem) -> Unit,
        private val onAuthorClicked: (DashboardNewsItem) -> Unit
    ) : RecyclerView.ViewHolder(view) {

        private val authorView: TextView = view.findViewById(R.id.postAuthor)
        private val metadataView: TextView = view.findViewById(R.id.postMetadata)
        private val bodyView: TextView = view.findViewById(R.id.postBody)
        private val avatarView: ImageView = view.findViewById(R.id.postAvatar)
        private val imagesContainer: LinearLayout = view.findViewById(R.id.postImagesContainer)
        private val commentCountView: TextView = view.findViewById(R.id.postCommentsCount)
        private val commentsIconView: ImageView = view.findViewById(R.id.postCommentsIcon)
        private val commentsContainer: View = view.findViewById(R.id.postCommentsContainer)
        private val editAction: View = view.findViewById(R.id.postActionEdit)
        private val deleteAction: View = view.findViewById(R.id.postActionDelete)
        private val shareAction: View = view.findViewById(R.id.postActionShare)

        fun bind(item: DashboardNewsItem) {
            authorView.text = item.author
            metadataView.text = item.metadata
            if (item.message.isNullOrBlank()) {
                bodyView.isVisible = false
                bodyView.text = ""
                bodyView.setOnClickListener(null)
            } else {
                bodyView.isVisible = true
                markwon.setMarkdown(bodyView, item.message)
                bodyView.setOnClickListener { onPostClicked(item) }
            }
            avatarBinder(avatarView, item.username, item.hasAvatar)
            avatarView.setOnClickListener { onAuthorClicked(item) }
            authorView.setOnClickListener { onAuthorClicked(item) }
            metadataView.setOnClickListener { onAuthorClicked(item) }
            bindImages(item)
            commentCountView.text = formatCount(item.commentCount)
            commentsIconView.setOnClickListener { onPostClicked(item) }
            commentCountView.setOnClickListener { onPostClicked(item) }
            commentsContainer.setOnClickListener { onPostClicked(item) }
            bindActions(item)
        }

        private fun bindActions(item: DashboardNewsItem) {
            setupAction(editAction, item.canEdit, R.string.dashboard_post_action_edit_toast) {
                onEditClicked(item)
            }
            setupAction(deleteAction, item.canDelete, R.string.dashboard_post_action_delete_toast) {
                onDeleteClicked(item)
            }
            setupAction(shareAction, item.canShare, R.string.dashboard_post_action_share_toast) {
                onShareClicked(item)
            }
        }

        private fun setupAction(
            view: View,
            enabled: Boolean,
            messageRes: Int,
            onClick: (() -> Unit)? = null
        ) {
            view.isEnabled = enabled
            view.alpha = if (enabled) 1f else DISABLED_ACTION_ALPHA
            if (enabled) {
                view.setOnClickListener { onClick?.invoke() ?: Toast.makeText(view.context, messageRes, Toast.LENGTH_SHORT).show() }
            } else {
                view.setOnClickListener(null)
            }
        }

        private fun bindImages(item: DashboardNewsItem) {
            val imagePaths = item.imagePaths
            if (imagePaths.isEmpty()) {
                imagesContainer.isVisible = false
                imagesContainer.removeAllViews()
                return
            }
            imagesContainer.isVisible = true
            imagesContainer.removeAllViews()
            val context = imagesContainer.context
            val spacing = context.resources.getDimensionPixelSize(R.dimen.dashboard_post_image_spacing)
            val height = context.resources.getDimensionPixelSize(R.dimen.dashboard_card_image_height)
            imagePaths.forEachIndexed { index, path ->
                val imageView = AppCompatImageView(context)
                val params = LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    height
                )
                if (index > 0) {
                    params.topMargin = spacing
                }
                imageView.layoutParams = params
                imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                imageView.setBackgroundResource(R.drawable.dashboard_post_image_placeholder)
                imageView.contentDescription = context.getString(R.string.dashboard_post_image_content_description)
                imagesContainer.addView(imageView)
                imageBinder(imageView, path)
                imageView.setOnClickListener { onImageClicked(item, index) }
            }
        }

        private fun formatCount(count: Int): String {
            if (count < 0) {
                return "0"
            }
            if (count < 1000) {
                return count.toString()
            }
            var value = count.toDouble()
            val suffixes = arrayOf("K", "M", "B")
            var suffixIndex = -1
            while (value >= 1000 && suffixIndex < suffixes.lastIndex) {
                value /= 1000.0
                suffixIndex++
            }
            val hasFraction = value < 100 && value % 1.0 != 0.0
            val formatted = if (hasFraction) {
                DECIMAL_FORMAT.format(value)
            } else {
                value.toInt().toString()
            }
            return if (suffixIndex >= 0) formatted + suffixes[suffixIndex] else formatted
        }

        companion object {
            private val DECIMAL_FORMAT = DecimalFormat("#.#")
            private const val DISABLED_ACTION_ALPHA = 0.4f
        }
    }

        companion object {
            private const val ARG_TEAM_ID = "arg_team_id"
            private const val ARG_TEAM_NAME = "arg_team_name"
            private const val LOAD_MORE_THRESHOLD = 3
            private val IMAGE_MARKDOWN_CAPTURE_REGEX = Regex("!\\[[^]]*]\\(([^)]+)\\)")
        private const val MINUTE_MILLIS = 60_000L
        private const val HOUR_MILLIS = 60 * MINUTE_MILLIS
        private const val DAY_MILLIS = 24 * HOUR_MILLIS
        private const val MONTH_MILLIS = 30 * DAY_MILLIS
        private const val YEAR_MILLIS = 12 * MONTH_MILLIS

        fun newInstanceForTeam(teamId: String, teamName: String): DashboardVoicesFragment {
            return DashboardVoicesFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TEAM_ID, teamId)
                    putString(ARG_TEAM_NAME, teamName)
                }
            }
        }
    }
}
