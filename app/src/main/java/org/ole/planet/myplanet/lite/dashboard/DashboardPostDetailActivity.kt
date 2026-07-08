/*
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-12-12
 */

package org.ole.planet.myplanet.lite.dashboard

import android.app.Activity
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.TouchDelegate
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.os.BundleCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.core.view.isVisible
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
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
import java.util.ArrayList
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

internal fun transformCommentMarkdownForDisplay(markdown: String): String = markdown.replace("\n", "  \n")

class DashboardPostDetailActivity : org.ole.planet.myplanet.lite.BaseActivity() {
    internal lateinit var recyclerView: RecyclerView
    internal lateinit var loadingView: View

    internal val repository =
        DashboardNewsRepository(
            client = OkHttpClient.Builder().build(),
            moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build(),
        )
    internal val actionsRepository = DashboardNewsActionsRepository(AuthDependencies.client, AuthDependencies.moshi, Dispatchers.IO)
    internal val composerRepository =
        VoicesComposerRepository(
            client = OkHttpClient.Builder().build(),
            moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build(),
        )
    internal val httpClient = OkHttpClient.Builder().build()
    internal lateinit var adapter: PostDetailAdapter
    internal lateinit var markwon: Markwon

    internal lateinit var replyContainer: View
    internal lateinit var replyInputLayout: TextInputLayout
    internal lateinit var replyInput: EditText
    internal lateinit var replyExpandedContent: View
    internal lateinit var replyPreviewLabel: TextView
    internal lateinit var replyPreview: TextView
    internal lateinit var replyPreviewContainer: LinearLayout
    internal lateinit var replyPreviewImagesRow: View
    internal lateinit var replyPreviewImages: LinearLayout
    internal lateinit var replySendButton: MaterialButton
    internal lateinit var replyActionsRow: View
    internal lateinit var replyMarkdownToolbar: LinearLayout
    internal lateinit var replyingToLabel: TextView
    internal lateinit var backCallback: OnBackPressedCallback

    internal val pendingReplyImages = LinkedHashMap<String, PendingVoiceImage>()
    internal var replyPendingNewlineIndex: Int? = null
    internal var isHandlingReplyListContinuation = false
    internal val replyImagePickerLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                lifecycleScope.launch {
                    handleReplyImageSelection(it)
                }
            }
        }
    internal val editVoiceLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                setResult(Activity.RESULT_OK)
                finish()
            }
        }

    internal var avatarLoader: DashboardAvatarLoader? = null
    internal var imageLoader: DashboardPostImageLoader? = null
    internal var shareHelper: PostShareHelper? = null
    internal var baseUrl: String? = null
    internal var sessionCookie: String? = null
    internal var credentials: StoredCredentials? = null
    internal var serverCode: String? = null
    internal var serverParentCode: String? = null
    internal var selectedTeamId: String? = null
    internal var selectedTeamName: String? = null
    internal var currentUsername: String? = null
    internal var isUserAdmin: Boolean = false
    internal var currentComments: List<PostDetailItem.Comment> = emptyList()
    internal var isEditingComment: Boolean = false
    internal var editingCommentDocument: NewsDocument? = null
    internal var document: NewsDocument? = null
    internal var isPostingReply: Boolean = false
    internal var isReplyComposerExpanded: Boolean = false
    internal var replyContextHandle: String? = null

    internal lateinit var headerItem: PostDetailItem.Header
    internal var avatarUpdateListener: AvatarUpdateNotifier.Listener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyDeviceOrientationLock()
        setContentView(R.layout.activity_dashboard_post_detail)

        setupToolbar()
        setupViews()
        setupBackNavigation()
        setupReplyComposer()
        if (!loadIntentData()) return
        setupAdapter()
        loadInitialData()
    }

    internal fun setupToolbar() {
        val toolbar: MaterialToolbar = findViewById(R.id.postDetailToolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(R.string.dashboard_post_detail_title)
        toolbar.setNavigationOnClickListener { onSupportNavigateUp() }
        toolbar.post {
            val navButton =
                toolbar.children.firstOrNull { child ->
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

    internal fun setupViews() {
        markwon = Markwon.builder(this).build()
        recyclerView = findViewById(R.id.postDetailRecyclerView)
        loadingView = findViewById(R.id.postDetailLoading)
    }

    internal fun hasAdapter(): Boolean = ::adapter.isInitialized

    @Suppress("unused")
    private fun applyWrappedFormatting(
        prefix: String,
        suffix: String,
        placeholder: String,
        placeCursorInsideWhenNoSelection: Boolean = false,
    ) {
        applyWrappedFormattingInternal(prefix, suffix, placeholder, placeCursorInsideWhenNoSelection)
    }

    @Suppress("unused")
    private fun updateReplyActionAvailability(text: CharSequence?) {
        updateReplyActionAvailabilityInternal(text)
    }

    internal fun loadIntentData(): Boolean {
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
        document =
            intent.extras?.let { bundle ->
                BundleCompat.getSerializable(bundle, EXTRA_DOCUMENT, NewsDocument::class.java)
            }

        headerItem =
            PostDetailItem.Header(
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
                canShare = false,
            )

        updateReplyingToLabel(username)
        updateReplyComposerVisibility()
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    internal var cachedProfile: UserProfile? = null

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

        internal const val COMMENTS_LIMIT = 50
        internal const val MINUTE_MILLIS = 60_000L
        internal const val HOUR_MILLIS = 60 * MINUTE_MILLIS
        internal const val DAY_MILLIS = 24 * HOUR_MILLIS
        internal const val MONTH_MILLIS = 30 * DAY_MILLIS
        internal const val YEAR_MILLIS = 12 * MONTH_MILLIS
        internal const val PRIVATE_FOR_COMMUNITY = "community"
        internal const val JPEG_QUALITY = 85
        internal const val MAX_IMAGE_DIMENSION = 1280
        internal const val PREFS_NAME = "PREFS"
        internal const val KEY_DEVICE_ANDROID_ID = "device_android_id"
        internal const val KEY_DEVICE_CUSTOM_DEVICE_NAME = "device_custom_device_name"
        internal const val KEY_SERVER_PARENT_CODE = "server_parent_code"
        internal const val KEY_SERVER_CODE = "server_code"
        internal const val COLLAPSED_REPLY_MIN_LINES = 1
        internal const val EXPANDED_REPLY_MIN_LINES = 3
        internal const val MAX_HEADING_LEVEL = 6

        internal val NUMBERED_LIST_REGEX = Regex("^(\\d+)\\.\\s*(.*)$")
        internal val IMAGE_MARKDOWN_REGEX = Regex("!\\[([^]]*)]\\(([^)]+)\\)")
        internal val RESOURCES_MARKDOWN_REGEX = Regex("!\\[[^]]*]\\((resources/[^)]+)\\)")
        internal val RESOURCES_PATH_REGEX = Regex("resources/[^/]+/[^/]+", RegexOption.IGNORE_CASE)
    }
}
