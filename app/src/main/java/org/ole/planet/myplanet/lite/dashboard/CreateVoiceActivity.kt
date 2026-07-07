/*
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-11-17
 */

@file:Suppress("ktlint:standard:kdoc")

package org.ole.planet.myplanet.lite.dashboard

import android.app.Activity
import android.graphics.Bitmap
import android.os.Bundle
import android.text.Editable
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.BundleCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.noties.markwon.Markwon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.ole.planet.myplanet.lite.BaseActivity
import org.ole.planet.myplanet.lite.R
import org.ole.planet.myplanet.lite.auth.AuthDependencies
import org.ole.planet.myplanet.lite.dashboard.DashboardServerPreferences.getServerBaseUrl
import org.ole.planet.myplanet.lite.dashboard.DashboardServerPreferences.getServerCode
import org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore
import org.ole.planet.myplanet.lite.profile.StoredCredentials
import org.ole.planet.myplanet.lite.profile.UserProfile
import org.ole.planet.myplanet.lite.util.ApplicationScope
import org.ole.planet.myplanet.lite.util.FileUtils.deleteFiles
import java.util.LinkedHashMap

class CreateVoiceActivity : BaseActivity() {
    internal lateinit var toolbar: MaterialToolbar
    internal lateinit var markwon: Markwon
    internal lateinit var createVoiceInput: TextInputEditText
    internal lateinit var createVoiceSubmitButton: MaterialButton
    internal lateinit var createVoiceProgress: LinearProgressIndicator
    internal lateinit var createVoicePreviewText: TextView
    internal lateinit var createVoicePreviewImages: LinearLayout
    internal lateinit var createVoiceEditorLabel: TextView
    internal lateinit var markdownToolbar: LinearLayout

    internal val repository =
        VoicesComposerRepository(
            client = OkHttpClient.Builder().build(),
            moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build(),
        )
    internal val newsActionsRepository = DashboardNewsActionsRepository(AuthDependencies.client, AuthDependencies.moshi, Dispatchers.IO)
    internal val httpClient = OkHttpClient.Builder().build()
    internal val pendingImages = LinkedHashMap<String, PendingVoiceImage>()
    internal val decodedBitmaps = java.util.concurrent.ConcurrentHashMap<String, Bitmap>()
    private val imagePickerLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                lifecycleScope.launch {
                    handleImageSelection(it)
                }
            }
        }

    internal var baseUrl: String? = null
    internal var sessionCookie: String? = null
    internal var serverCode: String? = null
    internal var cachedProfile: UserProfile? = null

    internal var isPosting = false
    internal var isSessionReady = false
    internal var previewJob: Job? = null
    internal var pendingNewlineIndex: Int? = null
    internal var isHandlingListContinuation = false
    internal var isEditMode = false
    internal var editPostId: String? = null
    internal var editInitialMessage: String? = null
    internal var editInitialImagePaths: List<String> = emptyList()
    internal var editImagesLoaded = false
    internal var editDocument: DashboardNewsRepository.NewsDocument? = null
    internal var targetTeamId: String? = null
    internal var targetTeamName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyDeviceOrientationLock()
        setContentView(R.layout.activity_create_voice)

        targetTeamId = intent.getStringExtra(EXTRA_TARGET_TEAM_ID)
        targetTeamName = intent.getStringExtra(EXTRA_TARGET_TEAM_NAME)

        setupViews()
        setupMarkdownToolbar()

        lifecycleScope.launch {
            initializeSession()
        }

        setupEditModeIfNeeded()

        val initialText = createVoiceInput.text?.toString().orEmpty()
        updatePreview(initialText)
        updateActionAvailability()
        renderPreviewImages()
    }

    private fun setupViews() {
        toolbar = findViewById(R.id.createVoiceToolbar)
        createVoiceEditorLabel = findViewById(R.id.createVoiceEditorLabel)
        createVoiceInput = findViewById(R.id.createVoiceInput)
        createVoiceSubmitButton = findViewById(R.id.createVoiceSubmitButton)
        createVoiceProgress = findViewById(R.id.createVoiceProgress)
        createVoicePreviewText = findViewById(R.id.createVoicePreviewText)
        createVoicePreviewImages = findViewById(R.id.createVoicePreviewImages)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(R.string.create_voice_title)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        toolbar.inflateMenu(R.menu.menu_create_voice)
        toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_post_voice) {
                attemptPost()
                true
            } else {
                false
            }
        }

        markwon = Markwon.builder(this).build()

        createVoiceInput.doAfterTextChanged { text ->
            handleTextChanged(text)
        }
        createVoiceInput.addTextChangedListener(listContinuationWatcher)

        createVoiceSubmitButton.setOnClickListener {
            attemptPost()
        }
        applySubmitButtonBottomSpacing()
    }

    private fun applySubmitButtonBottomSpacing() {
        val baseBottomMargin = resources.getDimensionPixelSize(R.dimen.create_voice_submit_bottom_margin)
        val boostedBottomMargin = baseBottomMargin + (baseBottomMargin / 2)
        ViewCompat.setOnApplyWindowInsetsListener(createVoiceSubmitButton) { view, insets ->
            val navBarInset = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = boostedBottomMargin + navBarInset
            }
            insets
        }
        ViewCompat.requestApplyInsets(createVoiceSubmitButton)
    }

    override fun onDestroy() {
        previewJob?.cancel()

        val filesToDelete = pendingImages.values.map { it.file }.toList()
        pendingImages.clear()

        ApplicationScope.io.launch {
            deleteFiles(filesToDelete)
        }

        decodedBitmaps.values.forEach { bitmap ->
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
        decodedBitmaps.clear()
        super.onDestroy()
    }

    internal fun handleInsertImageClick() {
        launchImagePicker()
    }

    private fun setupEditModeIfNeeded() {
        isEditMode = intent.getBooleanExtra(EXTRA_IS_EDIT_MODE, false)
        if (!isEditMode) {
            return
        }

        editPostId = intent.getStringExtra(EXTRA_EDIT_POST_ID)
        editInitialMessage = intent.getStringExtra(EXTRA_EDIT_INITIAL_MESSAGE)
        editDocument =
            intent.extras?.let { bundle ->
                BundleCompat.getSerializable(
                    bundle,
                    EXTRA_EDIT_DOCUMENT,
                    DashboardNewsRepository.NewsDocument::class.java,
                )
            }
        val documentImagePaths =
            editDocument
                ?.images
                ?.mapNotNull { image ->
                    extractPathFromMarkdown(image.markdown)
                        ?: buildResourcePath(image.resourceId, image.filename)
                }?.filter { it.isNotBlank() }
                .orEmpty()
        val combinedImagePaths =
            (
                intent.getStringArrayListExtra(EXTRA_EDIT_INITIAL_IMAGE_PATHS) ?: emptyList()
            ).plus(documentImagePaths)
        editInitialImagePaths = mergeImagePaths(combinedImagePaths)

        supportActionBar?.setTitle(R.string.edit_voice_title)
        createVoiceEditorLabel.setText(R.string.edit_voice_editor_label)
        toolbar.menu?.findItem(R.id.action_post_voice)?.title =
            getString(R.string.edit_voice_menu_update)
        createVoiceSubmitButton.setText(R.string.edit_voice_primary_action)

        editInitialMessage?.let { message ->
            createVoiceInput.setText(message)
            createVoiceInput.setSelection(createVoiceInput.text?.length ?: 0)
            updatePreview(message)
        }

        if (editInitialImagePaths.isNotEmpty()) {
            lifecycleScope.launch {
                loadEditInitialImages()
            }
        }
    }

    private fun launchImagePicker() {
        imagePickerLauncher.launch("image/*")
    }

    private fun handleTextChanged(text: Editable?) {
        previewJob?.cancel()
        previewJob =
            lifecycleScope.launch {
                delay(PREVIEW_DEBOUNCE_MS)
                updatePreview(text?.toString().orEmpty())
                updateActionAvailability()
            }
    }

    private fun transformMarkdownForPreview(markdown: String): String = transformMarkdownForPreviewContent(markdown)

    private suspend fun initializeSession() {
        val context = applicationContext
        baseUrl = getServerBaseUrl(context)
        serverCode = getServerCode(context)
        val base = baseUrl
        if (!base.isNullOrBlank()) {
            val authService = AuthDependencies.provideAuthService(context, base)
            sessionCookie = authService.getStoredToken()
            isSessionReady = true
            updateActionAvailability()
            if (isEditMode && editInitialImagePaths.isNotEmpty()) {
                loadEditInitialImages()
            }
        } else {
            Toast.makeText(this, R.string.create_voice_missing_server, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun attemptPost() {
        val message =
            createVoiceInput.text
                ?.toString()
                ?.trim()
                .orEmpty()
        if (message.isBlank()) {
            Toast.makeText(this, R.string.create_voice_empty_error, Toast.LENGTH_SHORT).show()
            return
        }
        val base = baseUrl
        if (base.isNullOrBlank()) {
            Toast.makeText(this, R.string.create_voice_missing_server, Toast.LENGTH_SHORT).show()
            return
        }
        if (isPosting) {
            return
        }
        val credentials = ProfileCredentialsStore.getStoredCredentials(this)
        if (credentials == null) {
            Toast.makeText(this, R.string.create_voice_missing_credentials, Toast.LENGTH_SHORT).show()
            return
        }
        val confirmTitle =
            if (isEditMode) {
                R.string.edit_voice_confirm_title
            } else {
                R.string.create_voice_confirm_title
            }
        val confirmMessage =
            if (isEditMode) {
                R.string.edit_voice_confirm_message
            } else {
                R.string.create_voice_confirm_message
            }
        val positiveAction =
            if (isEditMode) {
                R.string.edit_voice_confirm_positive
            } else {
                R.string.create_voice_confirm_positive
            }
        MaterialAlertDialogBuilder(this)
            .setTitle(confirmTitle)
            .setMessage(confirmMessage)
            .setPositiveButton(positiveAction) { _, _ ->
                if (isEditMode) {
                    updateVoice(message, base, credentials)
                } else {
                    postVoice(message, base, credentials)
                }
            }.setNegativeButton(R.string.create_voice_confirm_negative, null)
            .show()
    }

    private fun postVoice(
        message: String,
        base: String,
        credentials: StoredCredentials,
    ) {
        setPosting(true)
        lifecycleScope.launch {
            val profile = loadCachedProfile()
            val codes = resolvePostingCodes(profile)
            val preparedContent =
                runCatching {
                    prepareImagesForPosting(base, credentials, message)
                }.getOrElse {
                    Toast.makeText(this@CreateVoiceActivity, R.string.create_voice_image_upload_error, Toast.LENGTH_SHORT).show()
                    setPosting(false)
                    return@launch
                }
            val userPayload = buildUserPayload(profile, credentials, codes)
            val result =
                repository.createVoice(
                    VoicesComposerRepository.CreateVoiceParams(
                        baseUrl = base,
                        credentials = credentials,
                        sessionCookie = sessionCookie,
                        message = preparedContent.message,
                        createdOn = codes?.planetCode ?: serverCode,
                        parentCode = codes?.parentCode,
                        replyTo = null,
                        images = preparedContent.images,
                        labels = emptyList(),
                        userPayload = userPayload,
                        teamId = targetTeamId,
                        teamName = targetTeamName,
                    ),
                )
            result
                .onSuccess {
                    Toast.makeText(this@CreateVoiceActivity, R.string.create_voice_post_success, Toast.LENGTH_SHORT).show()
                    setResult(Activity.RESULT_OK)
                    finish()
                }.onFailure {
                    Toast.makeText(this@CreateVoiceActivity, R.string.create_voice_post_error, Toast.LENGTH_SHORT).show()
                    setPosting(false)
                }
        }
    }

    private fun updateVoice(
        message: String,
        base: String,
        credentials: StoredCredentials,
    ) {
        setPosting(true)
        lifecycleScope.launch {
            val doc = editDocument
            if (doc == null || doc.id.isNullOrBlank() || doc.revision.isNullOrBlank()) {
                Toast.makeText(this@CreateVoiceActivity, R.string.edit_voice_missing_document, Toast.LENGTH_SHORT).show()
                setPosting(false)
                return@launch
            }
            val preparedContent =
                runCatching {
                    prepareImagesForPosting(base, credentials, message)
                }.getOrElse {
                    Toast.makeText(this@CreateVoiceActivity, R.string.create_voice_image_upload_error, Toast.LENGTH_SHORT).show()
                    setPosting(false)
                    return@launch
                }
            val images =
                preparedContent.images.map { image ->
                    DashboardNewsRepository.NewsImage(
                        resourceId = image.resourceId,
                        filename = image.filename,
                        markdown = image.markdown,
                    )
                }
            val result =
                newsActionsRepository.updateNews(
                    baseUrl = base,
                    sessionCookie = sessionCookie,
                    document = doc,
                    message = preparedContent.message,
                    images = images,
                    teamId = targetTeamId,
                    teamName = targetTeamName,
                )
            result
                .onSuccess {
                    Toast.makeText(this@CreateVoiceActivity, R.string.edit_voice_update_success, Toast.LENGTH_SHORT).show()
                    setResult(Activity.RESULT_OK)
                    finish()
                }.onFailure {
                    Toast.makeText(this@CreateVoiceActivity, R.string.edit_voice_update_error, Toast.LENGTH_SHORT).show()
                    setPosting(false)
                }
        }
    }

    private fun setPosting(posting: Boolean) {
        isPosting = posting
        createVoiceProgress.isVisible = posting
        createVoiceInput.isEnabled = !posting
        setMarkdownToolbarEnabled(!posting)
        createVoiceSubmitButton.isEnabled = !posting
        updateActionAvailability()
    }

    private fun updateActionAvailability() {
        val hasContent = !createVoiceInput.text.isNullOrBlank()
        val enabled = hasContent && !isPosting && isSessionReady
        toolbar.menu?.findItem(R.id.action_post_voice)?.isEnabled = enabled
        createVoiceSubmitButton.isEnabled = enabled
    }

    companion object {
        private const val PREVIEW_DEBOUNCE_MS = 150L
        internal const val MAX_HEADING_LEVEL = 6
        internal val NUMBERED_LIST_REGEX = Regex("^(\\d+)\\.\\s*(.*)$")
        internal val MARKDOWN_IMAGE_REGEX = Regex("!\\[[^\\]]*\\]\\(([^)]+)\\)")
        internal const val KEY_DEVICE_ANDROID_ID = "device_android_id"
        internal const val KEY_DEVICE_CUSTOM_DEVICE_NAME = "device_custom_device_name"
        internal const val KEY_SERVER_PARENT_CODE = "server_parent_code"
        internal const val KEY_SERVER_CODE = "server_code"
        const val EXTRA_IS_EDIT_MODE = "extra_is_edit_mode"
        const val EXTRA_EDIT_POST_ID = "extra_edit_post_id"
        const val EXTRA_EDIT_INITIAL_MESSAGE = "extra_edit_initial_message"
        const val EXTRA_EDIT_INITIAL_IMAGE_PATHS = "extra_edit_initial_image_paths"
        const val EXTRA_EDIT_DOCUMENT = "extra_edit_document"
        const val EXTRA_TARGET_TEAM_ID = "extra_target_team_id"
        const val EXTRA_TARGET_TEAM_NAME = "extra_target_team_name"
    }
}
