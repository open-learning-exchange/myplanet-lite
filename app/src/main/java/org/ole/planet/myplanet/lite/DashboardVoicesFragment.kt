/*
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-12-12
 */

package org.ole.planet.myplanet.lite

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import io.noties.markwon.Markwon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.lite.auth.AuthDependencies
import org.ole.planet.myplanet.lite.dashboard.DashboardAvatarLoader
import org.ole.planet.myplanet.lite.dashboard.DashboardNewsActionsRepository
import org.ole.planet.myplanet.lite.dashboard.DashboardNewsRepository
import org.ole.planet.myplanet.lite.dashboard.DashboardNewsRepository.NewsPage
import org.ole.planet.myplanet.lite.dashboard.DashboardPostDetailActivity
import org.ole.planet.myplanet.lite.dashboard.DashboardPostImageLoader
import org.ole.planet.myplanet.lite.dashboard.DashboardServerPreferences
import org.ole.planet.myplanet.lite.dashboard.PostShareHelper
import org.ole.planet.myplanet.lite.dashboard.SharedBitmapDependencies
import org.ole.planet.myplanet.lite.profile.AvatarUpdateNotifier
import org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore
import org.ole.planet.myplanet.lite.profile.StoredCredentials
import org.ole.planet.myplanet.lite.profile.UserProfile
import org.ole.planet.myplanet.lite.profile.UserProfileDatabase
import org.ole.planet.myplanet.lite.util.enableDrag

class DashboardVoicesFragment : Fragment(R.layout.fragment_dashboard_voices) {
    private lateinit var recyclerView: RecyclerView
    private lateinit var loadingView: View
    private lateinit var emptyView: TextView
    private lateinit var markwon: Markwon
    private lateinit var adapter: DashboardNewsAdapter
    private val createVoiceLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                if (::recyclerView.isInitialized) {
                    recyclerView.post { recyclerView.scrollToPosition(0) }
                }
                loadInitial()
            }
        }

    private val postDetailLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
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

    private val repository =
        DashboardNewsRepository(
            client = SharedBitmapDependencies.client,
            moshi = AuthDependencies.moshi,
        )
    private val actionsRepository = DashboardNewsActionsRepository(AuthDependencies.client, AuthDependencies.moshi, Dispatchers.IO)
    private val itemMapper by lazy { DashboardNewsItemMapper(requireContext()) }
    private val navigator by lazy { DashboardVoicesNavigator(this, createVoiceLauncher, postDetailLauncher) }

    @androidx.annotation.VisibleForTesting
    internal val items = mutableListOf<DashboardNewsItem>()
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

    private var initJob: kotlinx.coroutines.Job? = null
    private var fetchJob: kotlinx.coroutines.Job? = null
    private var isLoading = false
    private var hasMore = true
    private var nextSkip = 0
    private var nextBookmark: String? = null
    private var currentEmptyMessage: Int = R.string.dashboard_voices_empty

    @androidx.annotation.VisibleForTesting
    internal var pageSize: Int = DashboardActivity.VOICE_PAGE_SIZE_OPTIONS[1]

    override fun onAttach(context: Context) {
        super.onAttach(context)
        pageSize = DashboardActivity.getVoicePageSizePreference(context)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        setupViews(view)
        setupRecyclerView()
        setupObserversAndLoadInitial()
    }

    private fun setupViews(view: View) {
        recyclerView = view.findViewById(R.id.postsRecyclerView)
        loadingView = view.findViewById(R.id.postsLoading)
        emptyView = view.findViewById(R.id.postsEmptyView)
        val fab: FloatingActionButton = view.findViewById(R.id.addVoiceFab)
        fab.setOnClickListener {
            animateFabClick(fab)
            openCreateVoiceComposer()
        }
        fab.enableDrag()
    }

    private fun setupRecyclerView() {
        markwon = Markwon.builder(requireContext()).build()
        adapter =
            DashboardVoicesRecyclerBinder.bind(
                recyclerView,
                markwon,
                shouldLoadMore = { !isLoading && hasMore },
                loadMore = { loadMore(pageSize) },
                avatarBinder = { imageView, username, hasAvatar ->
                    avatarLoader?.bind(imageView, username, hasAvatar)
                },
                imageBinder = { imageView, imagePath ->
                    postImageLoader?.bind(imageView, imagePath) ?: run {
                        imageView.isVisible = false
                        imageView.setImageDrawable(null)
                    }
                },
                onImageClicked = ::openImagePreview,
                onPostClicked = ::openPostDetail,
                onDeleteClicked = ::requestDeletePost,
                onShareClicked = ::sharePost,
                onEditClicked = ::openEditVoice,
                onAuthorClicked = ::openTeamMemberProfile,
            )
    }

    private fun setupObserversAndLoadInitial() {
        initJob?.cancel()
        initJob =
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
                avatarUpdateListener =
                    AvatarUpdateNotifier.register(
                        AvatarUpdateNotifier.Listener { username ->
                            handleAvatarUpdated(username)
                        },
                    )
                postImageLoader = DashboardPostImageLoader(currentBaseUrl, sessionCookie, viewLifecycleOwner.lifecycleScope)
                postShareHelper =
                    PostShareHelper(
                        requireContext().applicationContext,
                        { currentBaseUrl },
                        { sessionCookie },
                        { serverCode ?: Uri.parse(currentBaseUrl).host },
                    )
                loadInitial()
            }
    }

    private fun animateFabClick(fab: FloatingActionButton) {
        fab
            .animate()
            .rotationBy(360f)
            .setDuration(250)
            .withEndAction { fab.rotation = 0f }
            .start()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        initJob?.cancel()
        initJob = null
        fetchJob?.cancel()
        fetchJob = null
        recyclerView.adapter = null
        avatarLoader?.destroy()
        avatarLoader = null
        postImageLoader = null
        postShareHelper = null
        AvatarUpdateNotifier.unregister(avatarUpdateListener)
        avatarUpdateListener = null
    }

    private fun handleAvatarUpdated(username: String) {
        if (::adapter.isInitialized) adapter.notifyAvatarUpdated(recyclerView, username)
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

    @androidx.annotation.VisibleForTesting
    internal fun loadInitial() {
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

    @androidx.annotation.VisibleForTesting
    internal fun loadMore(targetPosts: Int = pageSize) {
        val base = baseUrl ?: return
        if (isLoading || !hasMore) {
            return
        }
        isLoading = true
        updateLoadingVisibility()
        fetchJob?.cancel()
        fetchJob =
            viewLifecycleOwner.lifecycleScope.launch {
                var accumulatedPosts = 0
                var shouldContinue = true
                val fetchLimit = maxOf(pageSize * 5, 100)
                while (shouldContinue) {
                    val addedPosts = fetchNextBatch(base, fetchLimit)
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

    private suspend fun fetchNextBatch(
        base: String,
        fetchLimit: Int,
    ): Int {
        val result =
            repository.fetchNews(
                base,
                sessionCookie,
                DashboardNewsRepository.NewsQuery(
                    skip = nextSkip,
                    bookmark = nextBookmark,
                    limit = fetchLimit,
                    createdOn = serverCode,
                    parentCode = serverParentCode,
                    teamName = teamName,
                ),
            )
        return result.fold(
            onSuccess = { page ->
                handlePage(page)
            },
            onFailure = {
                handleLoadError()
                -1
            },
        )
    }

    private fun updateCommentCounts(page: NewsPage): Boolean {
        var shouldUpdateAdapter = false
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
        return shouldUpdateAdapter
    }

    private fun handlePage(page: NewsPage): Int {
        nextSkip += page.consumed
        nextBookmark = page.bookmark
        hasMore = page.hasMore && (page.consumed > 0 || !nextBookmark.isNullOrEmpty())
        var shouldUpdateAdapter = updateCommentCounts(page)
        var newPostsCount = 0
        val mapped =
            page.items.mapNotNull { document ->
                val id = document.id ?: return@mapNotNull null
                val commentCount = commentCounts[id] ?: 0
                itemMapper.map(document, commentCount, sessionCookie, currentUsername, isUserAdmin)
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
            val result =
                actionsRepository.deleteNews(
                    base,
                    cookie,
                    item.document,
                    teamId = teamId,
                    teamName = teamName,
                )
            result
                .onSuccess {
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
        val normalized =
            DashboardActivity.VOICE_PAGE_SIZE_OPTIONS.firstOrNull { it == newPageSize }
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
        navigator.openComposer(teamId, teamName)
    }

    private fun showEmptyState(messageRes: Int) {
        currentEmptyMessage = messageRes
        emptyView.setText(messageRes)
        emptyView.isVisible = true
    }

    private suspend fun loadCurrentUserProfile(): UserProfile? {
        val context = requireContext().applicationContext
        return withContext(Dispatchers.IO) {
            UserProfileDatabase.getInstance(context).getProfile()
        }
    }

    private fun openImagePreview(
        item: DashboardNewsItem,
        index: Int,
    ) {
        navigator.openImagePreview(item, index)
    }

    private fun openPostDetail(item: DashboardNewsItem) {
        navigator.openPostDetail(item, teamId, teamName)
    }

    private fun sharePost(item: DashboardNewsItem) {
        val helper = postShareHelper ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            helper.sharePost(item.id, item.author, item.message, item.imagePaths)
        }
    }

    private fun openTeamMemberProfile(item: DashboardNewsItem) {
        navigator.openTeamMemberProfile(item)
    }

    private fun openEditVoice(item: DashboardNewsItem) {
        navigator.openEditVoice(item, teamId, teamName)
    }

    fun isTeamFeedFor(
        id: String,
        name: String,
    ): Boolean {
        val sameId = teamId?.equals(id, ignoreCase = true) == true
        val sameName = teamName?.equals(name, ignoreCase = true) == true
        return sameId && sameName
    }

    companion object {
        private const val ARG_TEAM_ID = "arg_team_id"
        private const val ARG_TEAM_NAME = "arg_team_name"
        fun newInstanceForTeam(
            teamId: String,
            teamName: String,
        ): DashboardVoicesFragment =
            DashboardVoicesFragment().apply {
                arguments =
                    Bundle().apply {
                        putString(ARG_TEAM_ID, teamId)
                        putString(ARG_TEAM_NAME, teamName)
                    }
            }
    }
}
