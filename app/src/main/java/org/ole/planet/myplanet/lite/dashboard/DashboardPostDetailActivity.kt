/**
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-12-12
 */

package org.ole.planet.myplanet.lite.dashboard

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.TouchDelegate
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.PopupMenu
import androidx.core.os.BundleCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.noties.markwon.Markwon
import org.ole.planet.myplanet.lite.util.MarkdownUtils
import java.util.ArrayList
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.ole.planet.myplanet.lite.R
import org.ole.planet.myplanet.lite.auth.AuthDependencies
import org.ole.planet.myplanet.lite.dashboard.DashboardNewsRepository.NewsDocument
import org.ole.planet.myplanet.lite.profile.AvatarUpdateNotifier
import org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore
import org.ole.planet.myplanet.lite.profile.StoredCredentials
import org.ole.planet.myplanet.lite.profile.UserProfile
import org.ole.planet.myplanet.lite.profile.UserProfileDatabase
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider

private fun transformCommentMarkdownForDisplay(markdown: String): String {
    return markdown.replace("\n", "  \n")
}

class DashboardPostDetailActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var loadingView: View

    private val repository = DashboardNewsRepository(
        client = OkHttpClient.Builder().build(),
        moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    )
    private val actionsRepository = DashboardNewsActionsRepository()
    private val composerRepository = VoicesComposerRepository(
        client = OkHttpClient.Builder().build(),
        moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    )
    private val httpClient = OkHttpClient.Builder().build()
    private lateinit var adapter: PostDetailAdapter
    private lateinit var markwon: Markwon

    private lateinit var replyContainer: View
    private lateinit var replyInputLayout: TextInputLayout
    private lateinit var replyInput: EditText
    private lateinit var replyExpandedContent: View
    private lateinit var replyPreviewLabel: TextView
    private lateinit var replyPreview: TextView
    private lateinit var replyPreviewContainer: LinearLayout
    private lateinit var replyPreviewImagesRow: View
    private lateinit var replyPreviewImages: LinearLayout
    private lateinit var replySendButton: MaterialButton
    private lateinit var replyActionsRow: View
    private lateinit var replyMarkdownToolbar: LinearLayout
    private lateinit var replyingToLabel: TextView
    private lateinit var backCallback: OnBackPressedCallback

    private val pendingReplyImages = LinkedHashMap<String, PendingVoiceImage>()
    private var replyPendingNewlineIndex: Int? = null
    private var isHandlingReplyListContinuation = false
    private val replyImagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            lifecycleScope.launch {
                handleReplyImageSelection(it)
            }
        }
    }
    private val editVoiceLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                setResult(Activity.RESULT_OK)
                finish()
            }
        }

    private var avatarLoader: DashboardAvatarLoader? = null
    private var imageLoader: DashboardPostImageLoader? = null
    private var shareHelper: PostShareHelper? = null
    private var baseUrl: String? = null
    private var sessionCookie: String? = null
    private var credentials: StoredCredentials? = null
    private var serverCode: String? = null
    private var serverParentCode: String? = null
    private var selectedTeamId: String? = null
    private var selectedTeamName: String? = null
    private var currentUsername: String? = null
    private var isUserAdmin: Boolean = false
    private var currentComments: List<PostDetailItem.Comment> = emptyList()
    private var isEditingComment: Boolean = false
    private var editingCommentDocument: NewsDocument? = null
    private var document: NewsDocument? = null
    private var isPostingReply: Boolean = false
    private var isReplyComposerExpanded: Boolean = false
    private var replyContextHandle: String? = null

    private lateinit var headerItem: PostDetailItem.Header
    private var avatarUpdateListener: AvatarUpdateNotifier.Listener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard_post_detail)

        setupToolbar()
        setupViews()
        setupBackNavigation()
        setupReplyComposer()
        if (!loadIntentData()) return
        setupAdapter()
        loadInitialData()
    }

    private fun setupToolbar() {
        val toolbar: MaterialToolbar = findViewById(R.id.postDetailToolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(R.string.dashboard_post_detail_title)
        toolbar.setNavigationOnClickListener { onSupportNavigateUp() }
        toolbar.post {
            val navButton = toolbar.children.firstOrNull { child ->
                child is android.widget.ImageButton
            } ?: return@post
            val extraTapArea = resources.getDimensionPixelSize(R.dimen.dashboard_nav_touch_inset)
            val hitRect = Rect()
            navButton.getHitRect(hitRect)
            hitRect.top -= extraTapArea
            hitRect.bottom += extraTapArea
            hitRect.left -= extraTapArea
            hitRect.right += extraTapArea
            toolbar.touchDelegate = TouchDelegate(hitRect, navButton)
        }
    }

    private fun setupViews() {
        markwon = Markwon.builder(this).build()
        recyclerView = findViewById(R.id.postDetailRecyclerView)
        loadingView = findViewById(R.id.postDetailLoading)
    }

    private fun setupBackNavigation() {
        backCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (collapseReplyComposerIfExpanded()) {
                    return
                }
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }
        onBackPressedDispatcher.addCallback(this, backCallback)
    }

    private fun setupReplyComposer() {
        replyContainer = findViewById(R.id.postDetailReplyContainer)
        replyInputLayout = findViewById(R.id.dashboardReplyInputLayout)
        replyInput = findViewById(R.id.dashboardReplyInput)
        replyExpandedContent = findViewById(R.id.dashboardReplyExpandedContent)
        replyPreviewLabel = findViewById(R.id.dashboardReplyPreviewLabel)
        replyPreview = findViewById(R.id.dashboardReplyPreview)
        replyPreviewContainer = findViewById(R.id.dashboardReplyPreviewContainer)
        replyPreviewImagesRow = findViewById(R.id.dashboardReplyPreviewImagesRow)
        replyPreviewImages = findViewById(R.id.dashboardReplyPreviewImages)
        replySendButton = findViewById(R.id.dashboardReplySendButton)
        replyActionsRow = findViewById(R.id.dashboardReplyActions)
        replyMarkdownToolbar = findViewById(R.id.dashboardReplyMarkdownToolbar)
        replyingToLabel = findViewById(R.id.postDetailReplyingTo)
        val replyBold: MaterialButton = findViewById(R.id.dashboardReplyMarkdownBold)
        val replyItalic: MaterialButton = findViewById(R.id.dashboardReplyMarkdownItalic)
        val replyHeading: MaterialButton = findViewById(R.id.dashboardReplyMarkdownHeading)
        val replyBullet: MaterialButton = findViewById(R.id.dashboardReplyMarkdownBullet)
        val replyNumbered: MaterialButton = findViewById(R.id.dashboardReplyMarkdownNumbered)
        val replyQuote: MaterialButton = findViewById(R.id.dashboardReplyMarkdownQuote)
        val replyLink: MaterialButton = findViewById(R.id.dashboardReplyMarkdownLink)
        val replyImage: MaterialButton = findViewById(R.id.dashboardReplyMarkdownImage)
        val baseReplyContainerMarginBottom =
            (replyContainer.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin

        ViewCompat.setOnApplyWindowInsetsListener(replyContainer) { view, insets ->
            val systemBarsBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val bottomInset = max(systemBarsBottom, imeBottom)
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = baseReplyContainerMarginBottom + bottomInset
            }
            insets
        }
        ViewCompat.requestApplyInsets(replyContainer)

        replyInputLayout.helperText = null
        replyInput.doAfterTextChanged { text ->
            updateReplyPreview(replyPreview, text?.toString())
            updateReplyActionAvailability(text)
        }
        replyInput.addTextChangedListener(replyListContinuationWatcher)
        replyInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                expandReplyComposer()
            }
        }
        replyInput.setOnTouchListener { v, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                v.parent?.requestDisallowInterceptTouchEvent(true)
            }
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                v.parent?.requestDisallowInterceptTouchEvent(false)
            }
            false
        }
        replyInput.isVerticalScrollBarEnabled = true
        replyInput.setOnClickListener {
            expandReplyComposer()
        }
        replySendButton.setOnClickListener {
            val message = replyInput.text?.toString()?.trim().orEmpty()
            if (isEditingComment) {
                attemptUpdateComment(message)
            } else {
                attemptReply(message)
            }
        }
        replyBold.setOnClickListener {
            applyWrappedFormatting("**", "**", "", placeCursorInsideWhenNoSelection = true)
        }
        replyItalic.setOnClickListener {
            applyWrappedFormatting("*", "*", "", placeCursorInsideWhenNoSelection = true)
        }
        replyHeading.setOnClickListener { applyReplyHeadingFormatting() }
        replyBullet.setOnClickListener { applyLinePrefix("- ") }
        replyNumbered.setOnClickListener { applyLinePrefix("1. ") }
        replyQuote.setOnClickListener { applyLinePrefix("> ") }
        replyLink.setOnClickListener {
            applyWrappedFormatting("[", "](https://)", "", true)
        }
        replyImage.setOnClickListener {
            handleReplyInsertImageClick()
        }
        updateReplyPreview(replyPreview, "")

        setMarkdownToolbarEnabled(false)
        replySendButton.isEnabled = false
    }

    private fun loadIntentData(): Boolean {
        val postId = intent.getStringExtra(EXTRA_POST_ID)
        if (postId.isNullOrBlank()) {
            finish()
            return false
        }
        val author = intent.getStringExtra(EXTRA_AUTHOR) ?: ""
        val username = intent.getStringExtra(EXTRA_USERNAME)
        val message = intent.getStringExtra(EXTRA_MESSAGE)
        val imagePaths = intent.getStringArrayListExtra(EXTRA_IMAGE_PATHS)?.filterNotNull()
        selectedTeamId = intent.getStringExtra(EXTRA_TEAM_ID)
        selectedTeamName = intent.getStringExtra(EXTRA_TEAM_NAME)
        val hasAvatar = intent.getBooleanExtra(EXTRA_HAS_AVATAR, false)
        val timestamp = intent.getLongExtra(EXTRA_TIMESTAMP, 0L)
        val commentCount = intent.getIntExtra(EXTRA_COMMENT_COUNT, 0)
        document = intent.extras?.let { bundle ->
            BundleCompat.getSerializable(bundle, EXTRA_DOCUMENT, NewsDocument::class.java)
        }

        headerItem = PostDetailItem.Header(
            id = postId,
            author = author,
            username = username,
            hasAvatar = hasAvatar,
            message = message,
            imagePaths = imagePaths ?: emptyList(),
            timestamp = timestamp,
            commentCount = commentCount,
            isLoadingComments = true,
            canReply = false,
            canEdit = false,
            canDelete = false,
            canShare = false
        )

        updateReplyingToLabel(username)
        updateReplyComposerVisibility()
        return true
    }

    private fun setupAdapter() {
        adapter = PostDetailAdapter(
            markwon,
            avatarBinder = { imageView, user, hasAvatar ->
                val shouldAttemptLoad = hasAvatar || !user.isNullOrBlank()
                avatarLoader?.bind(imageView, user, shouldAttemptLoad)
            },
            imageBinder = { imageView, path ->
                val loader = imageLoader
                if (loader != null) {
                    loader.bind(imageView, path)
                } else {
                    imageView.isVisible = false
                    imageView.setImageDrawable(null)
                }
            },
            onImageClicked = { paths, index ->
                openImagePreview(paths, index)
            },
            onDeleteClicked = {
                attemptDeletePost()
            },
            onShareClicked = {
                shareCurrentPost()
            },
            onEditClicked = { header ->
                launchEditVoice(header)
            },
            onReplyClicked = {
                promptReply()
            },
            onCommentEditClicked = { comment ->
                startEditingComment(comment)
            },
            onCommentDeleteClicked = { comment ->
                attemptDeleteComment(comment)
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        submitItems(currentComments)
    }

    private fun loadInitialData() {
        lifecycleScope.launch {
            initializeSession()
            val profile = loadCachedProfile()
            currentUsername = profile?.username
            isUserAdmin = profile?.isUserAdmin == true
            refreshHeaderActions()
            val base = baseUrl
            if (base.isNullOrBlank()) {
                Toast.makeText(
                    this@DashboardPostDetailActivity,
                    R.string.dashboard_post_detail_comments_error,
                    Toast.LENGTH_SHORT
                ).show()
                updateItems(emptyList())
                loadingView.isVisible = false
                return@launch
            }
            avatarLoader = DashboardAvatarLoader(base, sessionCookie, credentials, lifecycleScope)
            avatarUpdateListener = AvatarUpdateNotifier.register(AvatarUpdateNotifier.Listener { username ->
                handleAvatarUpdated(username)
            })
            imageLoader = DashboardPostImageLoader(base, sessionCookie, lifecycleScope)
            shareHelper = PostShareHelper(
                applicationContext,
                { baseUrl },
                { sessionCookie },
                { serverCode ?: baseUrl?.let { Uri.parse(it).host } }
            )
            loadComments(headerItem.id)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private var cachedProfile: UserProfile? = null

    private fun handleAvatarUpdated(username: String) {
        if (!::adapter.isInitialized) {
            return
        }
        val positions = adapter.currentList.mapIndexedNotNull { index, item ->
            val itemUsername = when (item) {
                is PostDetailItem.Header -> item.username
                is PostDetailItem.Comment -> item.username
            }
            if (itemUsername?.equals(username, ignoreCase = true) == true) {
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
        val context = applicationContext
        baseUrl = DashboardServerPreferences.getServerBaseUrl(context)
        credentials = ProfileCredentialsStore.getStoredCredentials(context)
        serverCode = DashboardServerPreferences.getServerCode(context)
        serverParentCode = DashboardServerPreferences.getServerParentCode(context)
        baseUrl?.let { base ->
            val authService = AuthDependencies.provideAuthService(context, base)
            sessionCookie = authService.getStoredToken()
        }
    }

    private suspend fun loadComments(postId: String) {
        val base = baseUrl ?: return
        loadingView.isVisible = true
        val result = repository.fetchComments(
            base,
            sessionCookie,
            postId,
            COMMENTS_LIMIT,
            serverCode,
            serverParentCode,
            selectedTeamName
        )
        result.onSuccess { docs ->
            val sorted = docs.sortedBy { it.time ?: 0L }
            val mapped = sorted.mapNotNull { mapToCommentItem(it) }
            updateItems(mapped)
        }.onFailure {
            Toast.makeText(
                this,
                R.string.dashboard_post_detail_comments_error,
                Toast.LENGTH_SHORT
            ).show()
            updateItems(emptyList())
        }
        loadingView.isVisible = false
    }

    private fun updateItems(comments: List<PostDetailItem.Comment>) {
        headerItem = headerItem.copy(
            commentCount = comments.size,
            isLoadingComments = false
        )
        currentComments = comments
        submitItems(currentComments)
    }

    private fun refreshHeaderActions() {
        val username = headerItem.username
        val hasSession = !sessionCookie.isNullOrBlank()
        val isAuthor = hasSession && !username.isNullOrBlank() && currentUsername?.equals(username, ignoreCase = true) == true
        val canShare = hasSession
        val canDelete = (isAuthor || isUserAdmin) && document != null
        headerItem = headerItem.copy(
            canReply = hasSession,
            canEdit = isAuthor,
            canDelete = canDelete,
            canShare = canShare
        )
        updateReplyComposerVisibility()
        submitItems(currentComments)
    }

    private fun promptReply() {
        if (!headerItem.canReply || isPostingReply) {
            return
        }
        exitCommentEditMode()
        expandReplyComposer()
        replyContainer.isVisible = true
        replyInput.requestFocus()
        replyInput.post {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(replyInput, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun startEditingComment(comment: PostDetailItem.Comment) {
        if (isPostingReply) {
            return
        }
        val doc = comment.document
        if (doc == null) {
            Toast.makeText(this, R.string.dashboard_comment_edit_error, Toast.LENGTH_SHORT).show()
            return
        }
        isEditingComment = true
        editingCommentDocument = doc
        replyInput.hint = getString(R.string.dashboard_comment_edit_hint)
        replyInputLayout.helperText = getString(R.string.dashboard_comment_edit_helper)
        replySendButton.setText(R.string.dashboard_comment_edit_send)
        replyInput.setText(comment.message.orEmpty())
        replyInput.setSelection(replyInput.text?.length ?: 0)
        clearPendingReplyImages()
        lifecycleScope.launch {
            loadExistingCommentImages(comment)
        }
        expandReplyComposer()
        replyContainer.isVisible = true
        replyInput.requestFocus()
        replyInput.post {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(replyInput, InputMethodManager.SHOW_IMPLICIT)
        }
        updateReplyPreview(replyPreview, replyInput.text?.toString())
    }

    private fun exitCommentEditMode(clearFields: Boolean = false) {
        if (!isEditingComment) {
            if (clearFields) {
                replyInput.setText("")
                clearPendingReplyImages()
                updateReplyPreview(replyPreview, "")
            }
            return
        }
        isEditingComment = false
        editingCommentDocument = null
        replyInput.hint = getString(R.string.dashboard_post_reply_hint)
        replyInputLayout.helperText = null
        replySendButton.setText(R.string.dashboard_post_reply_send)
        if (clearFields) {
            replyInput.setText("")
            clearPendingReplyImages()
            updateReplyPreview(replyPreview, "")
        }
    }

    private fun attemptReply(message: String) {
        if (message.isBlank()) {
            Toast.makeText(this, R.string.dashboard_post_reply_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val base = baseUrl
        val postId = headerItem.id
        val doc = document
        if (base.isNullOrBlank() || doc == null) {
            Toast.makeText(this, R.string.dashboard_post_detail_comments_error, Toast.LENGTH_SHORT).show()
            return
        }
        val credentials = ProfileCredentialsStore.getStoredCredentials(this)
        if (credentials == null) {
            Toast.makeText(this, R.string.dashboard_post_reply_missing_credentials, Toast.LENGTH_SHORT).show()
            return
        }
        val cookie = sessionCookie
        if (cookie.isNullOrBlank()) {
            Toast.makeText(this, R.string.dashboard_post_detail_comments_error, Toast.LENGTH_SHORT).show()
            return
        }
        if (isPostingReply) {
            return
        }
        setReplyPosting(true)
        lifecycleScope.launch {
            val prepared = prepareReplyImagesForPosting(base, credentials, message)
            val userPayload = buildUserPayload(credentials)
            val result = composerRepository.createVoice(
                baseUrl = base,
                credentials = credentials,
                sessionCookie = cookie,
                message = prepared.message,
                createdOn = doc.createdOn ?: serverCode,
                parentCode = doc.parentCode,
                replyTo = postId,
                images = prepared.images,
                labels = emptyList(),
                userPayload = userPayload,
                teamId = selectedTeamId,
                teamName = selectedTeamName
            )
            result.onSuccess {
                Toast.makeText(this@DashboardPostDetailActivity, R.string.dashboard_post_reply_success, Toast.LENGTH_SHORT).show()
                replyInput.setText("")
                clearPendingReplyImages()
                updateReplyPreview(replyPreview, "")
                hideReplyKeyboard()
                collapseReplyComposerIfExpanded()
                loadComments(postId)
            }.onFailure {
                Toast.makeText(this@DashboardPostDetailActivity, R.string.dashboard_post_reply_error, Toast.LENGTH_SHORT).show()
            }
            setReplyPosting(false)
        }
    }

    private fun attemptUpdateComment(message: String) {
        if (message.isBlank()) {
            Toast.makeText(this, R.string.dashboard_post_reply_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val base = baseUrl
        val doc = editingCommentDocument
        if (base.isNullOrBlank() || doc == null) {
            Toast.makeText(this, R.string.dashboard_comment_edit_error, Toast.LENGTH_SHORT).show()
            exitCommentEditMode(clearFields = true)
            return
        }
        val credentials = ProfileCredentialsStore.getStoredCredentials(this)
        if (credentials == null) {
            Toast.makeText(this, R.string.dashboard_post_reply_missing_credentials, Toast.LENGTH_SHORT).show()
            return
        }
        val cookie = sessionCookie
        if (cookie.isNullOrBlank()) {
            Toast.makeText(this, R.string.dashboard_comment_edit_error, Toast.LENGTH_SHORT).show()
            return
        }
        if (isPostingReply) {
            return
        }
        setReplyPosting(true)
        lifecycleScope.launch {
            val prepared = prepareReplyImagesForPosting(base, credentials, message)
            val newImages = prepared.images.map { image ->
                DashboardNewsRepository.NewsImage(
                    resourceId = image.resourceId,
                    filename = image.filename,
                    markdown = image.markdown
                )
            }
            val mergedImages = mergeNewsImages(doc.images, newImages)
            val result = actionsRepository.updateNews(
                baseUrl = base,
                sessionCookie = cookie,
                document = doc,
                message = prepared.message,
                images = mergedImages,
                teamId = selectedTeamId,
                teamName = selectedTeamName
            )
            result.onSuccess {
                Toast.makeText(this@DashboardPostDetailActivity, R.string.dashboard_comment_edit_success, Toast.LENGTH_SHORT)
                    .show()
                exitCommentEditMode(clearFields = true)
                hideReplyKeyboard()
                collapseReplyComposerIfExpanded()
                loadComments(headerItem.id)
            }.onFailure {
                Toast.makeText(this@DashboardPostDetailActivity, R.string.dashboard_comment_edit_error, Toast.LENGTH_SHORT)
                    .show()
            }
            setReplyPosting(false)
        }
    }

    private fun updateReplyPreview(preview: TextView, message: String?) {
        val content = message?.takeIf { it.isNotBlank() }
        val previewText = content ?: getString(R.string.dashboard_post_reply_preview_placeholder)
        val transformed = transformReplyMarkdownForPreview(previewText)
        markwon.setMarkdown(preview, transformed)
        updateReplyPreviewImages()
    }

    private fun mergeNewsImages(
        existingImages: List<DashboardNewsRepository.NewsImage>?,
        newImages: List<DashboardNewsRepository.NewsImage>
    ): List<DashboardNewsRepository.NewsImage> {
        if (existingImages.isNullOrEmpty() && newImages.isEmpty()) {
            return emptyList()
        }
        val merged = LinkedHashMap<String, DashboardNewsRepository.NewsImage>()
        existingImages?.forEach { image ->
            val key = image.markdown ?: buildResourcePath(image.resourceId, image.filename)
            if (!key.isNullOrBlank() && !merged.containsKey(key)) {
                merged[key] = image
            }
        }
        newImages.forEach { image ->
            val key = image.markdown ?: buildResourcePath(image.resourceId, image.filename)
            if (!key.isNullOrBlank() && !merged.containsKey(key)) {
                merged[key] = image
            }
        }
        return merged.values.toList()
    }

    private fun updateReplyPreviewImages() {
        val images = pendingReplyImages.values.toList()
        if (!isReplyComposerExpanded || images.isEmpty()) {
            replyPreviewImages.removeAllViews()
            replyPreviewImagesRow.isVisible = false
            return
        }

        replyPreviewImages.removeAllViews()
        val size = resources.getDimensionPixelSize(R.dimen.dashboard_reply_preview_image_size)
        val spacing = resources.getDimensionPixelSize(R.dimen.dashboard_reply_preview_image_spacing)
        images.forEachIndexed { index, pending ->
            val imageView = AppCompatImageView(this)
            val params = LinearLayout.LayoutParams(size, size)
            if (index < images.lastIndex) {
                params.marginEnd = spacing
            }
            imageView.layoutParams = params
            imageView.scaleType = ImageView.ScaleType.CENTER_CROP
            imageView.clipToOutline = true
            imageView.setImageURI(Uri.fromFile(pending.file))
            replyPreviewImages.addView(imageView)
        }
        replyPreviewImagesRow.isVisible = true
    }

    private fun updateReplyComposerVisibility() {
        val canReply = headerItem.canReply
        replyInputLayout.isEnabled = canReply && !isPostingReply
        applyReplyExpansionState()
        updateReplyActionAvailability(replyInput.text)
    }

    private fun updateReplyActionAvailability(text: CharSequence?) {
        val hasContent = !text.isNullOrBlank()
        val canSend = (headerItem.canReply || isEditingComment) && !isPostingReply && hasContent && isReplyComposerExpanded
        replySendButton.isEnabled = canSend
    }

    private fun clearPendingReplyImages() {
        pendingReplyImages.values.forEach { pending ->
            if (pending.file.exists()) {
                pending.file.delete()
            }
        }
        pendingReplyImages.clear()
        updateReplyPreviewImages()
    }

    private fun setReplyPosting(posting: Boolean) {
        isPostingReply = posting
        updateReplyComposerVisibility()
    }

    private fun setMarkdownToolbarEnabled(enabled: Boolean) {
        replyMarkdownToolbar.isEnabled = enabled
        for (index in 0 until replyMarkdownToolbar.childCount) {
            replyMarkdownToolbar.getChildAt(index)?.isEnabled = enabled
        }
    }

    private fun handleReplyInsertImageClick() {
        launchReplyImagePicker()
    }

    private fun launchReplyImagePicker() {
        replyImagePickerLauncher.launch("image/*")
    }

    private suspend fun handleReplyImageSelection(uri: Uri) {
        val pendingResult = withContext(Dispatchers.IO) {
            runCatching { VoiceImageFactory.createPendingVoiceImage(uri, contentResolver, cacheDir, ::generatePendingImageId) }
        }
        pendingResult.onSuccess { pending ->
            pendingReplyImages[pending.id] = pending
            insertReplyImageMarkdown(pending.fileName)
            updateReplyPreview(replyPreview, replyInput.text?.toString())
        }.onFailure {
            Toast.makeText(this, R.string.create_voice_image_processing_error, Toast.LENGTH_SHORT).show()
        }
    }

    private fun insertReplyImageMarkdown(fileName: String) {
        val editText = replyInput
        val editable = editText.text ?: return
        val start = max(0, editText.selectionStart)
        val end = max(0, editText.selectionEnd)
        val insertStart = min(start, end)
        val insertEnd = max(start, end)
        val needsLeadingLineBreak = insertStart > 0 && editable[insertStart - 1] != '\n'
        val snippet = buildString {
            if (needsLeadingLineBreak) {
                append("\n\n")
            }
            append("![](")
            append(fileName)
            append(")\n")
        }
        editable.replace(insertStart, insertEnd, snippet)
        val cursor = (insertStart + snippet.length).coerceAtMost(editable.length)
        editText.setSelection(cursor)
    }

    private fun collapseReplyComposerIfExpanded(): Boolean {
        if (!isReplyComposerExpanded) {
            return false
        }
        isReplyComposerExpanded = false
        exitCommentEditMode(clearFields = true)
        hideReplyKeyboard()
        replyInput.clearFocus()
        applyReplyExpansionState()
        updateReplyActionAvailability(replyInput.text)
        return true
    }

    private fun expandReplyComposer() {
        if (isReplyComposerExpanded) {
            return
        }
        isReplyComposerExpanded = true
        applyReplyExpansionState()
    }

    private fun applyReplyExpansionState() {
        val canReply = headerItem.canReply
        val expanded = canReply && isReplyComposerExpanded
        replyContainer.isVisible = canReply
        val minLines = if (expanded) EXPANDED_REPLY_MIN_LINES else COLLAPSED_REPLY_MIN_LINES
        replyInput.setMinLines(minLines)
        replyExpandedContent.isVisible = expanded
        replyMarkdownToolbar.isVisible = expanded
        replyPreviewContainer.isVisible = expanded
        replyPreviewLabel.isVisible = expanded
        replyPreview.isVisible = expanded
        replyActionsRow.isVisible = expanded
        updateReplyPreviewImages()
        replyInputLayout.helperText = null
        setMarkdownToolbarEnabled(expanded && !isPostingReply)
        updateReplyingToVisibility(expanded)
    }

    private fun applyWrappedFormatting(prefix: String, suffix: String, placeholder: String, placeCursorInsideWhenNoSelection: Boolean = false) {
        if (!headerItem.canReply || isPostingReply) {
            return
        }
        val editable = replyInput.text ?: return
        val start = max(replyInput.selectionStart, 0)
        val end = max(replyInput.selectionEnd, 0)
        val selectionStart = min(start, end)
        val selectionEnd = max(start, end)
        val selected = editable.substring(selectionStart, selectionEnd)
        val replacement = if (selectionStart == selectionEnd) {
            val defaultText = placeholder
            "$prefix$defaultText$suffix"
        } else {
            "$prefix$selected$suffix"
        }
        editable.replace(selectionStart, selectionEnd, replacement)
        val newCursor = if (selectionStart == selectionEnd && placeCursorInsideWhenNoSelection) {
            selectionStart + prefix.length
        } else {
            selectionStart + replacement.length
        }
        val boundedCursor = min(max(newCursor, 0), editable.length)
        replyInput.setSelection(boundedCursor)
    }

    private fun applyReplyHeadingFormatting() {
        if (!headerItem.canReply || isPostingReply) {
            return
        }
        val editText = replyInput
        val editable = editText.text ?: return
        val cursor = max(0, editText.selectionStart)
        val lineStart = findReplyLineStart(editable, cursor)
        val lineLength = editable.length
        var prefixEnd = lineStart
        var currentHashes = 0
        while (prefixEnd < lineLength && editable[prefixEnd] == '#') {
            currentHashes++
            prefixEnd++
        }
        while (prefixEnd < lineLength && editable[prefixEnd].isWhitespace() && editable[prefixEnd] != '\n') {
            prefixEnd++
        }
        val newHeadingLevel = if (currentHashes == 0) {
            1
        } else {
            (currentHashes % MAX_HEADING_LEVEL) + 1
        }
        val replacement = buildString {
            repeat(newHeadingLevel) { append('#') }
            append(' ')
        }
        editable.replace(lineStart, prefixEnd, replacement)
        val selection = (lineStart + replacement.length).coerceAtMost(editable.length)
        editText.setSelection(selection)
    }

    private fun applyLinePrefix(prefix: String) {
        if (!headerItem.canReply || isPostingReply) {
            return
        }
        val editable = replyInput.text ?: return
        val selectionStart = max(replyInput.selectionStart, 0)
        val lastLineBreak = editable.lastIndexOf('\n', selectionStart - 1)
        val lineStart = if (lastLineBreak == -1) 0 else lastLineBreak + 1
        editable.insert(lineStart, prefix)
        val cursor = min(selectionStart + prefix.length, editable.length)
        replyInput.setSelection(cursor)
    }

    private val replyListContinuationWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            if (isHandlingReplyListContinuation) {
                return
            }
            if (count <= 0 || s == null) {
                return
            }
            val inserted = s.subSequence(start, start + count)
            val newlineOffset = inserted.lastIndexOf('\n')
            if (newlineOffset >= 0) {
                replyPendingNewlineIndex = start + newlineOffset
            }
        }

        override fun afterTextChanged(s: Editable?) {
            if (isHandlingReplyListContinuation) {
                return
            }
            val newlineIndex = replyPendingNewlineIndex ?: return
            replyPendingNewlineIndex = null
            s ?: return
            handleReplyListContinuation(s, newlineIndex)
        }
    }

    private fun handleReplyListContinuation(editable: Editable, newlineIndex: Int) {
        if (newlineIndex <= 0 || newlineIndex > editable.length) {
            return
        }
        val previousLineStart = findReplyLineStart(editable, newlineIndex)
        val previousLine = editable.subSequence(previousLineStart, newlineIndex).toString()
        if (previousLine.isEmpty()) {
            return
        }
        val indentLength = findIndentLength(previousLine)
        val indent = previousLine.substring(0, indentLength)
        val contentAfterIndent = previousLine.substring(indentLength)
        if (contentAfterIndent.isBlank()) {
            return
        }

        if (contentAfterIndent.startsWith("- ") || contentAfterIndent.startsWith("* ")) {
            val marker = contentAfterIndent.substring(0, 2)
            val hasText = contentAfterIndent.substring(2).isNotBlank()
            if (hasText) {
                insertReplyListPrefix(editable, newlineIndex, "$indent$marker")
            } else {
                removeReplyListPrefix(editable, previousLineStart + indentLength, marker.length)
            }
            return
        }

        val numberMatch = NUMBERED_LIST_REGEX.matchEntire(contentAfterIndent)
        if (numberMatch != null) {
            val number = numberMatch.groupValues.getOrNull(1)?.toIntOrNull() ?: return
            val remainder = numberMatch.groupValues.getOrNull(2).orEmpty()
            val markerLength = numberMatch.groupValues[1].length + 2
            if (remainder.isBlank()) {
                removeReplyListPrefix(editable, previousLineStart + indentLength, markerLength)
            } else {
                val nextMarker = "$indent${number + 1}. "
                insertReplyListPrefix(editable, newlineIndex, nextMarker)
            }
        }
    }

    private fun insertReplyListPrefix(editable: Editable, newlineIndex: Int, prefix: String) {
        val insertPosition = (newlineIndex + 1).coerceAtMost(editable.length)
        isHandlingReplyListContinuation = true
        editable.insert(insertPosition, prefix)
        replyInput.setSelection((insertPosition + prefix.length).coerceAtMost(editable.length))
        isHandlingReplyListContinuation = false
    }

    private fun removeReplyListPrefix(editable: Editable, start: Int, markerLength: Int) {
        val end = (start + markerLength).coerceAtMost(editable.length)
        isHandlingReplyListContinuation = true
        editable.delete(start, end)
        replyInput.setSelection(start.coerceAtMost(editable.length))
        isHandlingReplyListContinuation = false
    }

    private fun findReplyLineStart(editable: Editable, index: Int): Int {
        val boundedIndex = index.coerceIn(0, editable.length)
        for (i in boundedIndex - 1 downTo 0) {
            if (editable[i] == '\n') {
                return i + 1
            }
        }
        return 0
    }

    private fun findIndentLength(line: String): Int {
        for (i in line.indices) {
            if (!line[i].isWhitespace()) {
                return i
            }
        }
        return line.length
    }

    private fun hideReplyKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(replyInput.windowToken, 0)
    }

    private fun updateReplyingToLabel(username: String?) {
        replyContextHandle = username?.takeIf { it.isNotBlank() }
        updateReplyingToVisibility(isReplyComposerExpanded && headerItem.canReply)
    }

    private fun updateReplyingToVisibility(expanded: Boolean) {
        val handle = replyContextHandle
        if (expanded && handle != null) {
            replyingToLabel.text = getString(R.string.dashboard_post_replying_to, handle)
            replyingToLabel.isVisible = true
        } else {
            replyingToLabel.isVisible = false
        }
    }

    private suspend fun prepareReplyImagesForPosting(
        baseUrl: String,
        credentials: StoredCredentials,
        originalMessage: String
    ): PreparedVoicePost {
        if (pendingReplyImages.isEmpty()) {
            return PreparedVoicePost(originalMessage, emptyList())
        }
        val context = buildReplyImageResourceContext(credentials)

        val uploads = pendingReplyImages.values.filter { it.resourceId == null }
        val uploadResults = coroutineScope {
            uploads.map { pending ->
                async {
                    val markdown = ensureReplyImageUpload(baseUrl, credentials, context, pending)
                    pending to markdown
                }
            }.awaitAll()
        }

        var updatedMessage = originalMessage
        val preparedImages = mutableListOf<VoicesComposerRepository.ImagePayload>()

        for ((pending, markdown) in uploadResults) {
            val replaced = MarkdownUtils.replaceImagePlaceholder(updatedMessage, pending.fileName, markdown)
            updatedMessage = ensureMarkdownPresent(replaced, markdown)
            val resourceId = pending.resourceId
            if (resourceId != null) {
                preparedImages += VoicesComposerRepository.ImagePayload(
                    resourceId = resourceId,
                    filename = pending.fileName,
                    markdown = markdown
                )
            }
        }
        return PreparedVoicePost(updatedMessage, preparedImages)
    }

    private suspend fun ensureReplyImageUpload(
        baseUrl: String,
        credentials: StoredCredentials,
        context: VoiceImageResourceContext,
        pending: PendingVoiceImage
    ): String {
        pending.uploadedMarkdown?.let { return it }
        val existingResourceId = pending.resourceId
        if (existingResourceId != null) {
            val markdown = "![](resources/${existingResourceId.trim()}/${pending.fileName.trim()})"
            pending.uploadedMarkdown = markdown
            return markdown
        }

        val metadata = VoicesComposerRepository.ResourceMetadataRequest.fromContext(context, pending.fileName)
        val creationResponse = composerRepository.createResourceDocument(baseUrl, credentials, metadata)
        pending.resourceId = creationResponse.id
        pending.resourceRevision = creationResponse.revision
        val uploadResponse = composerRepository.uploadResourceBinary(
            baseUrl,
            credentials,
            creationResponse.id,
            pending.fileName,
            creationResponse.revision,
            pending.jpegBytes
        )
        val resolvedResourceId = uploadResponse.resourceId ?: creationResponse.id
        val resolvedFileName = uploadResponse.filename ?: pending.fileName
        pending.resourceId = resolvedResourceId
        pending.resourceRevision = uploadResponse.revision ?: creationResponse.revision
        val relativeMarkdown = uploadResponse.markdown
            ?: "![](resources/${resolvedResourceId.trim()}/${resolvedFileName.trim()})"
        pending.uploadedMarkdown = relativeMarkdown
        return relativeMarkdown
    }

    private fun transformReplyMarkdownForPreview(markdown: String): String {
        var processed = markdown.replace("\n", "  \n")
        val base = baseUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() }

        if (!base.isNullOrEmpty()) {
            val resourcesPattern = Regex("!\\[[^\\]]*\\]\\((resources/[^)]+)\\)")
            processed = resourcesPattern.replace(processed) { matchResult ->
                val path = matchResult.groupValues.getOrNull(1)?.trim().orEmpty()
                val absolute = "$base/db/$path"
                "![]($absolute)"
            }
        }

        pendingReplyImages.values.forEach { pending ->
            val pattern = Regex("(!\\[[^\\]]*\\]\\()${Regex.escape(pending.fileName)}(\\))")
            processed = pattern.replace(processed) { matchResult ->
                val prefix = matchResult.groupValues.getOrNull(1).orEmpty()
                val suffix = matchResult.groupValues.getOrNull(2).orEmpty()
                "$prefix${pending.file.toURI()}$suffix"
            }
        }
        return processed
    }

    private suspend fun loadExistingCommentImages(comment: PostDetailItem.Comment) {
        val base = baseUrl ?: return
        if (comment.imagePaths.isEmpty()) {
            return
        }
        val loaded = coroutineScope {
            comment.imagePaths.map { path ->
                async(Dispatchers.IO) { VoiceImageFetcher.fetchExistingImage(httpClient, cacheDir, sessionCookie, base, path, { VoiceImageFactory.generateImageFileName() }, { generatePendingImageId(it) }) }
            }.awaitAll().filterNotNull().toMutableList()
        }
        if (loaded.isEmpty()) {
            return
        }
        loaded.forEach { pending ->
            pendingReplyImages[pending.id] = pending
        }
        updateReplyPreview(replyPreview, replyInput.text?.toString())
    }

    private fun ensureMarkdownPresent(message: String, markdown: String): String {
        if (message.contains(markdown)) {
            return message
        }
        val builder = StringBuilder(message.trimEnd())
        if (builder.isNotEmpty()) {
            builder.append("\n\n")
        }
        builder.append(markdown)
        return builder.toString()
    }

    private suspend fun buildReplyImageResourceContext(credentials: StoredCredentials): VoiceImageResourceContext {
        val preferences = SecurePreferencesProvider.getServerPreferences(applicationContext)
        val androidId = preferences.getString(KEY_DEVICE_ANDROID_ID, null)?.takeIf { it.isNotBlank() }
        val customDeviceName = preferences.getString(KEY_DEVICE_CUSTOM_DEVICE_NAME, null)?.takeIf { it.isNotBlank() }
        val storedServerCode = preferences.getString(KEY_SERVER_CODE, null)?.takeIf { it.isNotBlank() }
        val storedParentCode = preferences.getString(KEY_SERVER_PARENT_CODE, null)?.takeIf { it.isNotBlank() }
        val profile = withContext(Dispatchers.IO) { loadCachedProfile() }
        val parsedCodes = parseCodesFromProfile(profile?.rawDocument)
        val resolvedResideOn = serverCode?.takeIf { it.isNotBlank() }
            ?: storedServerCode
            ?: parsedCodes?.planetCode
        val resolvedParent = storedParentCode ?: parsedCodes?.parentCode
        return VoiceImageResourceContext(
            username = credentials.username,
            resideOn = resolvedResideOn,
            sourcePlanet = resolvedParent,
            androidId = androidId,
            deviceName = org.ole.planet.myplanet.lite.util.DeviceUtils.getDeviceName(),
            customDeviceName = customDeviceName
        )
    }

    private fun parseCodesFromProfile(rawDocument: String?): ProfileCodes? {
        if (rawDocument.isNullOrBlank()) {
            return null
        }
        return runCatching {
            val json = org.json.JSONObject(rawDocument)
            val planetCode = json.optString("planetCode").takeIf { it.isNotBlank() }
            val parentCode = json.optString("parentCode").takeIf { it.isNotBlank() }
            ProfileCodes(planetCode, parentCode)
        }.getOrNull()
    }

    private fun replaceImagePlaceholder(source: String, fileName: String, replacement: String): String {
        val escapedName = Regex.escape(fileName)
        val pattern = Regex("!\\[([^\\]]*)\\]\\($escapedName\\)")
        var matched = false
        val updated = pattern.replace(source) { matchResult ->
            matched = true
            val altText = matchResult.groupValues.getOrNull(1).orEmpty()
            if (altText.isBlank()) {
                replacement
            } else {
                MarkdownUtils.applyAltText(replacement, altText)
            }
        }
        if (matched) {
            return updated
        }
        val builder = StringBuilder(source)
        if (builder.isNotEmpty()) {
            if (builder[builder.length - 1] != '\n') {
                builder.append('\n')
            }
            builder.append('\n')
        }
        builder.append(replacement)
        return builder.toString()
    }

    private suspend fun loadCachedProfile(): UserProfile? {
        val existing = cachedProfile
        if (existing != null) {
            return existing
        }
        val profile = withContext(Dispatchers.IO) {
            UserProfileDatabase.getInstance(applicationContext).getProfile()
        }
        cachedProfile = profile
        return profile
    }


    private fun resolveDeviceName(): String? {
        val manufacturer = Build.MANUFACTURER?.trim().orEmpty()
        val model = Build.MODEL?.trim().orEmpty()
        val base = when {
            manufacturer.isNotEmpty() && model.isNotEmpty() -> "$manufacturer $model"
            model.isNotEmpty() -> model
            manufacturer.isNotEmpty() -> manufacturer
            else -> null
        }
        return base
    }

    private fun generatePendingImageId(baseName: String): String {
        var candidate = baseName
        var counter = 1
        while (pendingReplyImages.containsKey(candidate)) {
            candidate = "${baseName}_$counter"
            counter++
        }
        return candidate
    }

    override fun onDestroy() {
        super.onDestroy()
        recyclerView.adapter = null
        avatarLoader?.destroy()
        avatarLoader = null
        imageLoader = null
        shareHelper = null
        AvatarUpdateNotifier.unregister(avatarUpdateListener)
        avatarUpdateListener = null
        clearPendingReplyImages()
    }

    private suspend fun buildUserPayload(credentials: StoredCredentials): VoicesComposerRepository.UserPayload {
        val profile = withContext(Dispatchers.IO) {
            UserProfileDatabase.getInstance(applicationContext).getProfile()
        }
        val planetCode = serverCode?.takeIf { it.isNotBlank() } ?: document?.createdOn
        return VoicesComposerRepository.UserPayload(
            id = "org.couchdb.user:${credentials.username}",
            name = profile?.username ?: credentials.username,
            firstName = profile?.firstName,
            middleName = profile?.middleName,
            lastName = profile?.lastName,
            email = profile?.email,
            language = profile?.language,
            phoneNumber = profile?.phoneNumber,
            planetCode = planetCode,
            parentCode = document?.parentCode,
            roles = null,
            joinDate = null,
            attachments = null
        )
    }

    private fun shareCurrentPost() {
        val helper = shareHelper ?: return
        val header = headerItem
        lifecycleScope.launch {
            helper.sharePost(header.id, header.author, header.message, header.imagePaths)
        }
    }

    private fun attemptDeletePost() {
        val base = baseUrl
        val doc = document
        if (base.isNullOrBlank() || doc == null) {
            Toast.makeText(this, R.string.dashboard_post_delete_error, Toast.LENGTH_SHORT).show()
            return
        }
        val cookie = sessionCookie
        if (cookie.isNullOrBlank()) {
            Toast.makeText(this, R.string.dashboard_post_delete_error, Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            val result = actionsRepository.deleteNews(
                base,
                cookie,
                doc,
                teamId = selectedTeamId,
                teamName = selectedTeamName
            )
            result.onSuccess {
                Toast.makeText(this@DashboardPostDetailActivity, R.string.dashboard_post_delete_success, Toast.LENGTH_SHORT)
                    .show()
                val deletedIntent = Intent().putExtra(EXTRA_DELETED_POST_ID, doc.id)
                setResult(RESULT_OK, deletedIntent)
                finish()
            }.onFailure {
                Toast.makeText(this@DashboardPostDetailActivity, R.string.dashboard_post_delete_error, Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    private fun attemptDeleteComment(comment: PostDetailItem.Comment) {
        val base = baseUrl
        val doc = comment.document
        if (base.isNullOrBlank() || doc == null) {
            Toast.makeText(this, R.string.dashboard_comment_delete_error, Toast.LENGTH_SHORT).show()
            return
        }
        val cookie = sessionCookie
        if (cookie.isNullOrBlank()) {
            Toast.makeText(this, R.string.dashboard_comment_delete_error, Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            val result = actionsRepository.deleteNews(
                base,
                cookie,
                doc,
                teamId = selectedTeamId,
                teamName = selectedTeamName
            )
            result.onSuccess {
                Toast.makeText(this@DashboardPostDetailActivity, R.string.dashboard_comment_delete_success, Toast.LENGTH_SHORT)
                    .show()
                currentComments = currentComments.filterNot { it.id == comment.id }
                updateItems(currentComments)
            }.onFailure {
                Toast.makeText(this@DashboardPostDetailActivity, R.string.dashboard_comment_delete_error, Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    private fun submitItems(comments: List<PostDetailItem.Comment>) {
        val newItems = ArrayList<PostDetailItem>(1 + comments.size)
        newItems.add(headerItem)
        newItems.addAll(comments)
        adapter.submitList(newItems)
    }

    private fun mapToCommentItem(document: NewsDocument): PostDetailItem.Comment? {
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
        val message = document.message?.takeUnless { it.isNullOrBlank() }
        val imagePaths = mapCommentImages(document)
        val hasAvatar = document.user?.attachments?.isNullOrEmpty() == false
        val timestamp = document.time ?: 0L
        val hasSession = !sessionCookie.isNullOrBlank()
        val isAuthor = hasSession && !username.isNullOrBlank() && currentUsername?.equals(username, ignoreCase = true) == true
        val canDelete = hasSession && (isAuthor || isUserAdmin)
        return PostDetailItem.Comment(
            id = id,
            author = displayName,
            username = username,
            hasAvatar = hasAvatar,
            message = message,
            imagePaths = imagePaths,
            timestamp = timestamp,
            canEdit = isAuthor,
            canDelete = canDelete,
            document = document
        )
    }

    private fun mapCommentImages(document: NewsDocument): List<String> {
        val fromImages = document.images
            ?.mapNotNull { image ->
                extractImagePath(image.markdown)
                    ?: buildResourcePath(image.resourceId, image.filename)
            }
            ?.filter { it.isNotBlank() }
            .orEmpty()
        val fromMessage = collectImagePaths(document.message)
        return mergeImagePaths(fromImages + fromMessage)
    }

    private fun collectImagePaths(markdown: String?): List<String> {
        if (markdown.isNullOrBlank()) {
            return emptyList()
        }
        val regex = Regex("!\\[[^\\]]*\\]\\(([^)]+)\\)")
        return regex.findAll(markdown)
            .mapNotNull { match ->
                match.groupValues.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
            }
            .toList()
    }

    private fun mergeImagePaths(paths: List<String>): List<String> {
        if (paths.isEmpty()) {
            return emptyList()
        }
        val seen = LinkedHashSet<String>()
        val merged = mutableListOf<String>()
        for (path in paths) {
            val normalized = normalizeImagePath(path)
            if (seen.add(normalized)) {
                merged += path
            }
        }
        return merged
    }

    private fun normalizeImagePath(path: String): String {
        val extracted = extractImagePath(path) ?: path
        val trimmed = extracted.trim()
        val resourcesMatch = Regex("resources/[^/]+/[^/]+", RegexOption.IGNORE_CASE)
            .find(trimmed)
        val reduced = if (resourcesMatch != null) {
            resourcesMatch.value
        } else {
            trimmed.trimStart('/')
                .removePrefix("db/")
                .trimStart('/')
        }
        return reduced.lowercase(Locale.US)
    }

    private fun extractImagePath(markdown: String?): String? {
        if (markdown.isNullOrBlank()) {
            return null
        }
        val pattern = Regex("!\\[[^\\]]*\\]\\(([^)]+)\\)")
        val match = pattern.find(markdown)
        return match?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun buildResourcePath(resourceId: String?, filename: String?): String? {
        val id = resourceId?.trim().takeUnless { it.isNullOrEmpty() }
        val name = filename?.trim().takeUnless { it.isNullOrEmpty() }
        if (id == null || name == null) {
            return null
        }
        return "resources/$id/$name"
    }

    private fun openImagePreview(imagePaths: List<String>, startIndex: Int) {
        if (imagePaths.isEmpty()) {
            return
        }
        val intent = Intent(this, DashboardImagePreviewActivity::class.java)
        intent.putStringArrayListExtra(
            DashboardImagePreviewActivity.EXTRA_IMAGE_PATHS,
            ArrayList(imagePaths)
        )
        intent.putExtra(DashboardImagePreviewActivity.EXTRA_START_INDEX, startIndex)
        startActivity(intent)
    }

    private fun launchEditVoice(item: PostDetailItem.Header) {
        val intent = Intent(this, CreateVoiceActivity::class.java)
        intent.putExtra(CreateVoiceActivity.EXTRA_IS_EDIT_MODE, true)
        intent.putExtra(CreateVoiceActivity.EXTRA_EDIT_POST_ID, item.id)
        intent.putExtra(CreateVoiceActivity.EXTRA_EDIT_INITIAL_MESSAGE, item.message)
        intent.putStringArrayListExtra(
            CreateVoiceActivity.EXTRA_EDIT_INITIAL_IMAGE_PATHS,
            ArrayList(item.imagePaths)
        )
        document?.let { intent.putExtra(CreateVoiceActivity.EXTRA_EDIT_DOCUMENT, it) }
        selectedTeamId?.let { intent.putExtra(CreateVoiceActivity.EXTRA_TARGET_TEAM_ID, it) }
        selectedTeamName?.let { intent.putExtra(CreateVoiceActivity.EXTRA_TARGET_TEAM_NAME, it) }
        editVoiceLauncher.launch(intent)
    }

    private class PostDetailAdapter(
        private val markwon: Markwon,
        private val avatarBinder: (ImageView, String?, Boolean) -> Unit,
        private val imageBinder: (ImageView, String) -> Unit,
        private val onImageClicked: (List<String>, Int) -> Unit,
        private val onDeleteClicked: () -> Unit,
        private val onShareClicked: (PostDetailItem.Header) -> Unit,
        private val onEditClicked: (PostDetailItem.Header) -> Unit,
        private val onReplyClicked: () -> Unit,
        private val onCommentEditClicked: (PostDetailItem.Comment) -> Unit,
        private val onCommentDeleteClicked: (PostDetailItem.Comment) -> Unit
    ) : androidx.recyclerview.widget.ListAdapter<PostDetailItem, RecyclerView.ViewHolder>(DIFF_CALLBACK) {

        override fun getItemViewType(position: Int): Int {
            return when (getItem(position)) {
                is PostDetailItem.Header -> VIEW_TYPE_HEADER
                is PostDetailItem.Comment -> VIEW_TYPE_COMMENT
            }
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = android.view.LayoutInflater.from(parent.context)
            return when (viewType) {
                VIEW_TYPE_HEADER -> {
                    val view = inflater.inflate(R.layout.item_dashboard_post_detail_header, parent, false)
                    HeaderViewHolder(
                        view,
                        markwon,
                        avatarBinder,
                        imageBinder,
                        onImageClicked,
                        onDeleteClicked,
                        onShareClicked,
                        onEditClicked,
                        onReplyClicked
                    )
                }
                else -> {
                    val view = inflater.inflate(R.layout.item_dashboard_comment, parent, false)
                    CommentViewHolder(
                        view,
                        markwon,
                        avatarBinder,
                        imageBinder,
                        onImageClicked,
                        onCommentEditClicked,
                        onCommentDeleteClicked
                    )
                }
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = getItem(position)
            when {
                holder is HeaderViewHolder && item is PostDetailItem.Header -> holder.bind(item)
                holder is CommentViewHolder && item is PostDetailItem.Comment -> {
                    val isLast = position == itemCount - 1
                    holder.bind(item, isLast)
                }
            }
        }

        private class HeaderViewHolder(
            view: View,
            private val markwon: Markwon,
            private val avatarBinder: (ImageView, String?, Boolean) -> Unit,
            private val imageBinder: (ImageView, String) -> Unit,
            private val onImageClicked: (List<String>, Int) -> Unit,
            private val onDeleteClicked: () -> Unit,
            private val onShareClicked: (PostDetailItem.Header) -> Unit,
            private val onEditClicked: (PostDetailItem.Header) -> Unit,
            private val onReplyClicked: () -> Unit
        ) : RecyclerView.ViewHolder(view) {

            private val authorView: TextView = view.findViewById(R.id.postDetailAuthor)
            private val metadataView: TextView = view.findViewById(R.id.postDetailMetadata)
            private val bodyView: TextView = view.findViewById(R.id.postDetailBody)
            private val avatarView: ImageView = view.findViewById(R.id.postDetailAvatar)
            private val imagesContainer: LinearLayout = view.findViewById(R.id.postDetailImagesContainer)
            private val commentsLabel: TextView = view.findViewById(R.id.postDetailCommentsLabel)
            private val commentsEmpty: TextView = view.findViewById(R.id.postDetailCommentsEmpty)
            private val dividerView: View = view.findViewById(R.id.postDetailDivider)
            private val overflowMenu: View = view.findViewById(R.id.postDetailOverflowMenu)

            fun bind(item: PostDetailItem.Header) {
                authorView.text = item.author
                val relativeTime = formatRelativeTime(itemView.context, item.timestamp)
                metadataView.text = buildMetadata(item.username, relativeTime)
                if (item.message.isNullOrBlank()) {
                    bodyView.isVisible = false
                    bodyView.text = ""
                } else {
                    bodyView.isVisible = true
                    val renderedMessage = transformCommentMarkdownForDisplay(item.message)
                    markwon.setMarkdown(bodyView, renderedMessage)
                }
                avatarBinder(avatarView, item.username, item.hasAvatar)
                bindImages(item)
                val count = item.commentCount.coerceAtLeast(0)
                commentsLabel.isVisible = true
                commentsEmpty.isVisible = !item.isLoadingComments && count == 0
                dividerView.isVisible = count > 0
                bindActions(item)
            }

            @SuppressLint("RestrictedApi")
            private fun bindActions(item: PostDetailItem.Header) {
                val hasActions = item.canReply || item.canEdit || item.canDelete || item.canShare
                overflowMenu.isVisible = hasActions
                if (!hasActions) {
                    overflowMenu.setOnClickListener(null)
                    return
                }

                overflowMenu.setOnClickListener {
                    val themedContext = ContextThemeWrapper(itemView.context, R.style.Widget_MyPlanet_PopupMenu)
                    val popup = PopupMenu(themedContext, overflowMenu)
                    popup.menuInflater.inflate(R.menu.menu_dashboard_post_actions, popup.menu)
                    popup.menu.findItem(R.id.action_reply).isVisible = item.canReply
                    popup.menu.findItem(R.id.action_edit).isVisible = item.canEdit
                    popup.menu.findItem(R.id.action_delete).isVisible = item.canDelete
                    popup.menu.findItem(R.id.action_share).isVisible = item.canShare
                    if (popup.menu is MenuBuilder) {
                        (popup.menu as MenuBuilder).setOptionalIconsVisible(true)
                    }

                    popup.setOnMenuItemClickListener { menuItem ->
                        when (menuItem.itemId) {
                            R.id.action_reply -> {
                                onReplyClicked()
                                true
                            }

                            R.id.action_edit -> {
                                onEditClicked(item)
                                true
                            }

                            R.id.action_delete -> {
                                onDeleteClicked()
                                true
                            }

                            R.id.action_share -> {
                                onShareClicked(item)
                                true
                            }

                            else -> false
                        }
                    }

                    popup.show()
                }
            }

            private fun bindImages(item: PostDetailItem.Header) {
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
                imagePaths.forEachIndexed { index, path ->
                    val imageView = AppCompatImageView(context)
                    val params = LinearLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    if (index > 0) {
                        params.topMargin = spacing
                    }
                    imageView.layoutParams = params
                    imageView.adjustViewBounds = true
                    imageView.scaleType = ImageView.ScaleType.FIT_CENTER
                    imageView.setBackgroundResource(R.drawable.dashboard_post_image_placeholder)
                    imageView.contentDescription = context.getString(R.string.dashboard_post_image_content_description)
                    imagesContainer.addView(imageView)
                    imageBinder(imageView, path)
                    imageView.setOnClickListener { onImageClicked(item.imagePaths, index) }
                }
            }
            companion object {
            }
        }

        private class CommentViewHolder(
            view: View,
            private val markwon: Markwon,
            private val avatarBinder: (ImageView, String?, Boolean) -> Unit,
            private val imageBinder: (ImageView, String) -> Unit,
            private val onImageClicked: (List<String>, Int) -> Unit,
            private val onEditClicked: (PostDetailItem.Comment) -> Unit,
            private val onDeleteClicked: (PostDetailItem.Comment) -> Unit
        ) : RecyclerView.ViewHolder(view) {

            private val authorView: TextView = view.findViewById(R.id.commentAuthor)
            private val metadataView: TextView = view.findViewById(R.id.commentMetadata)
            private val bodyView: TextView = view.findViewById(R.id.commentBody)
            private val avatarView: ImageView = view.findViewById(R.id.commentAvatar)
            private val imagesContainer: LinearLayout = view.findViewById(R.id.commentImagesContainer)
            private val dividerView: View = view.findViewById(R.id.commentDivider)
            private val overflowMenu: View = view.findViewById(R.id.commentOverflowMenu)

            fun bind(item: PostDetailItem.Comment, isLast: Boolean) {
                authorView.text = item.author
                val relativeTime = formatRelativeTime(itemView.context, item.timestamp)
                metadataView.text = buildMetadata(item.username, relativeTime)
                if (item.message.isNullOrBlank()) {
                    bodyView.isVisible = false
                    bodyView.text = ""
                } else {
                    bodyView.isVisible = true
                    val renderedMessage = transformCommentMarkdownForDisplay(item.message)
                    markwon.setMarkdown(bodyView, renderedMessage)
                }
                if (item.imagePaths.isEmpty()) {
                    imagesContainer.isVisible = false
                    imagesContainer.removeAllViews()
                } else {
                    val context = imagesContainer.context
                    val spacing = context.resources.getDimensionPixelSize(R.dimen.dashboard_post_image_spacing)
                    imagesContainer.removeAllViews()
                    item.imagePaths.forEachIndexed { index, path ->
                        val imageView = AppCompatImageView(context)
                        val params = LinearLayout.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                        if (index > 0) {
                            params.topMargin = spacing
                        }
                        imageView.layoutParams = params
                        imageView.adjustViewBounds = true
                        imageView.scaleType = ImageView.ScaleType.FIT_CENTER
                        imageView.setBackgroundResource(R.drawable.dashboard_post_image_placeholder)
                        imageView.contentDescription = context.getString(R.string.dashboard_post_image_content_description)
                        imagesContainer.addView(imageView)
                        imageBinder(imageView, path)
                        imageView.setOnClickListener { onImageClicked(item.imagePaths, index) }
                    }
                    imagesContainer.isVisible = true
                }
                avatarBinder(avatarView, item.username, item.hasAvatar)
                bindActions(item)
                dividerView.isVisible = !isLast
            }

            @SuppressLint("RestrictedApi")
            private fun bindActions(item: PostDetailItem.Comment) {
                val hasActions = item.canEdit || item.canDelete
                overflowMenu.isVisible = hasActions
                if (!hasActions) {
                    overflowMenu.setOnClickListener(null)
                    return
                }
                overflowMenu.setOnClickListener {
                    val themedContext = ContextThemeWrapper(itemView.context, R.style.Widget_MyPlanet_PopupMenu)
                    val popup = PopupMenu(themedContext, overflowMenu)
                    popup.menuInflater.inflate(R.menu.menu_dashboard_comment_actions, popup.menu)
                    popup.menu.findItem(R.id.action_edit).isVisible = item.canEdit
                    popup.menu.findItem(R.id.action_delete).isVisible = item.canDelete
                    if (popup.menu is MenuBuilder) {
                        (popup.menu as MenuBuilder).setOptionalIconsVisible(true)
                    }
                    popup.setOnMenuItemClickListener { menuItem ->
                        when (menuItem.itemId) {
                            R.id.action_edit -> {
                                onEditClicked(item)
                                true
                            }

                            R.id.action_delete -> {
                                onDeleteClicked(item)
                                true
                            }

                            else -> false
                        }
                    }
                    popup.show()
                }
            }
        }

        companion object {
            private const val VIEW_TYPE_HEADER = 0
            private const val VIEW_TYPE_COMMENT = 1

            private val DIFF_CALLBACK = org.ole.planet.myplanet.lite.util.DiffUtils.itemCallback<PostDetailItem>({ oldItem, newItem ->
                when {
                    oldItem is PostDetailItem.Header && newItem is PostDetailItem.Header -> oldItem.id == newItem.id
                    oldItem is PostDetailItem.Comment && newItem is PostDetailItem.Comment -> oldItem.id == newItem.id
                    else -> false
                }
            })
        }
    }

    private sealed class PostDetailItem {
        data class Header(
            val id: String,
            val author: String,
            val username: String?,
            val hasAvatar: Boolean,
            val message: String?,
            val imagePaths: List<String>,
            val timestamp: Long,
            val commentCount: Int,
            val isLoadingComments: Boolean,
            val canReply: Boolean,
            val canEdit: Boolean,
            val canDelete: Boolean,
            val canShare: Boolean
        ) : PostDetailItem()

        data class Comment(
            val id: String,
            val author: String,
            val username: String?,
            val hasAvatar: Boolean,
            val message: String?,
            val imagePaths: List<String>,
            val timestamp: Long,
            val canEdit: Boolean,
            val canDelete: Boolean,
            val document: NewsDocument?
        ) : PostDetailItem()
    }

    companion object {
        const val EXTRA_POST_ID = "extra_post_id"
        const val EXTRA_AUTHOR = "extra_author"
        const val EXTRA_USERNAME = "extra_username"
        const val EXTRA_MESSAGE = "extra_message"
        const val EXTRA_IMAGE_PATHS = "extra_image_paths"
        const val EXTRA_HAS_AVATAR = "extra_has_avatar"
        const val EXTRA_TIMESTAMP = "extra_timestamp"
        const val EXTRA_COMMENT_COUNT = "extra_comment_count"
        const val EXTRA_DOCUMENT = "extra_document"
        const val EXTRA_TEAM_ID = "extra_team_id"
        const val EXTRA_TEAM_NAME = "extra_team_name"
        const val EXTRA_DELETED_POST_ID = "extra_deleted_post_id"

        private const val COMMENTS_LIMIT = 50
        private const val MINUTE_MILLIS = 60_000L
        private const val HOUR_MILLIS = 60 * MINUTE_MILLIS
        private const val DAY_MILLIS = 24 * HOUR_MILLIS
        private const val MONTH_MILLIS = 30 * DAY_MILLIS
        private const val YEAR_MILLIS = 12 * MONTH_MILLIS
        private const val PRIVATE_FOR_COMMUNITY = "community"
        private const val JPEG_QUALITY = 85
        private const val MAX_IMAGE_DIMENSION = 1280
        private const val PREFS_NAME = "PREFS"
        private const val KEY_DEVICE_ANDROID_ID = "device_android_id"
        private const val KEY_DEVICE_CUSTOM_DEVICE_NAME = "device_custom_device_name"
        private const val KEY_SERVER_PARENT_CODE = "server_parent_code"
        private const val KEY_SERVER_CODE = "server_code"
        private const val COLLAPSED_REPLY_MIN_LINES = 1
        private const val EXPANDED_REPLY_MIN_LINES = 3
        private const val MAX_HEADING_LEVEL = 6

        private fun formatRelativeTime(context: Context, timestamp: Long): String {
            val now = System.currentTimeMillis()
            val diffMillis = max(0L, now - timestamp)
            val minutes = diffMillis / MINUTE_MILLIS
            val hours = diffMillis / HOUR_MILLIS
            val days = diffMillis / DAY_MILLIS
            val months = diffMillis / MONTH_MILLIS
            val years = diffMillis / YEAR_MILLIS
            return when {
                years >= 1 -> if (years == 1L) {
                    context.getString(R.string.dashboard_relative_time_year)
                } else {
                    context.getString(R.string.dashboard_relative_time_years, years)
                }
                months >= 1 -> if (months == 1L) {
                    context.getString(R.string.dashboard_relative_time_month)
                } else {
                    context.getString(R.string.dashboard_relative_time_months, months)
                }
                days >= 1 -> if (days == 1L) {
                    context.getString(R.string.dashboard_relative_time_day)
                } else {
                    context.getString(R.string.dashboard_relative_time_days, days)
                }
                hours >= 1 -> if (hours == 1L) {
                    context.getString(R.string.dashboard_relative_time_hour)
                } else {
                    context.getString(R.string.dashboard_relative_time_hours, hours)
                }
                minutes >= 1 -> if (minutes == 1L) {
                    context.getString(R.string.dashboard_relative_time_minute)
                } else {
                    context.getString(R.string.dashboard_relative_time_minutes, minutes)
                }
                else -> context.getString(R.string.dashboard_relative_time_seconds)
            }
        }

        private fun buildMetadata(username: String?, relativeTime: String): String {
            return if (!username.isNullOrBlank()) {
                "@${username.trim()} • $relativeTime"
            } else {
                relativeTime
            }
        }

        private val NUMBERED_LIST_REGEX = Regex("^(\\d+)\\.\\s*(.*)$")
    }
}
