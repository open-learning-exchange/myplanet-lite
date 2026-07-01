/**
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-11-17
 */

package org.ole.planet.myplanet.lite.dashboard

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
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
import com.google.android.material.textfield.TextInputLayout
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.noties.markwon.Markwon
import java.util.ArrayList
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.ole.planet.myplanet.lite.BaseActivity
import org.ole.planet.myplanet.lite.R
import org.ole.planet.myplanet.lite.auth.AuthDependencies
import org.ole.planet.myplanet.lite.dashboard.DashboardServerPreferences.getServerBaseUrl
import org.ole.planet.myplanet.lite.dashboard.DashboardServerPreferences.getServerCode
import org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore
import org.ole.planet.myplanet.lite.profile.StoredCredentials
import org.ole.planet.myplanet.lite.profile.UserProfile
import org.ole.planet.myplanet.lite.profile.UserProfileDatabase
import org.ole.planet.myplanet.lite.util.MarkdownUtils
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider

class CreateVoiceActivity : BaseActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var markwon: Markwon
    private lateinit var createVoiceInput: TextInputEditText
    private lateinit var createVoiceSubmitButton: MaterialButton
    private lateinit var createVoiceProgress: LinearProgressIndicator
    private lateinit var createVoicePreviewText: TextView
    private lateinit var createVoicePreviewImages: LinearLayout
    private lateinit var createVoiceEditorLabel: TextView
    private lateinit var markdownToolbar: LinearLayout

    private val repository = VoicesComposerRepository(
        client = OkHttpClient.Builder().build(),
        moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    )
    private val newsActionsRepository = DashboardNewsActionsRepository()
    private val httpClient = OkHttpClient.Builder().build()
    private val pendingImages = LinkedHashMap<String, PendingVoiceImage>()
    private val decodedBitmaps = java.util.concurrent.ConcurrentHashMap<String, Bitmap>()
    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            lifecycleScope.launch {
                handleImageSelection(it)
            }
        }
    }

    private var baseUrl: String? = null
    private var sessionCookie: String? = null
    private var serverCode: String? = null
    private var cachedProfile: UserProfile? = null

    private var isPosting = false
    private var isSessionReady = false
    private var previewJob: Job? = null
    private var pendingNewlineIndex: Int? = null
    private var isHandlingListContinuation = false
    private var isEditMode = false
    private var editPostId: String? = null
    private var editInitialMessage: String? = null
    private var editInitialImagePaths: List<String> = emptyList()
    private var editImagesLoaded = false
    private var editDocument: DashboardNewsRepository.NewsDocument? = null
    private var targetTeamId: String? = null
    private var targetTeamName: String? = null

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

    private fun setupMarkdownToolbar() {
        markdownToolbar = findViewById(R.id.markdownToolbar)
        val boldButton: MaterialButton = findViewById(R.id.markdownBoldButton)
        val italicButton: MaterialButton = findViewById(R.id.markdownItalicButton)
        val headingButton: MaterialButton = findViewById(R.id.markdownHeadingButton)
        val bulletButton: MaterialButton = findViewById(R.id.markdownBulletButton)
        val numberedButton: MaterialButton = findViewById(R.id.markdownNumberedButton)
        val quoteButton: MaterialButton = findViewById(R.id.markdownQuoteButton)
        val linkButton: MaterialButton = findViewById(R.id.markdownLinkButton)
        val imageButton: MaterialButton = findViewById(R.id.markdownImageButton)

        boldButton.setOnClickListener {
            applyWrappedFormatting("**", "**", "", placeCursorInsideWhenNoSelection = true)
        }
        italicButton.setOnClickListener {
            applyWrappedFormatting("*", "*", "", placeCursorInsideWhenNoSelection = true)
        }
        headingButton.setOnClickListener {
            applyHeadingFormatting()
        }
        bulletButton.setOnClickListener {
            applyBulletFormatting()
        }
        numberedButton.setOnClickListener {
            applyNumberedListFormatting()
        }
        quoteButton.setOnClickListener {
            applyQuoteFormatting()
        }
        linkButton.setOnClickListener {
            applyLinkFormatting()
        }
        imageButton.setOnClickListener {
            handleInsertImageClick()
        }
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

        @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
        GlobalScope.launch(Dispatchers.IO) {
            filesToDelete.forEach { file ->
                if (file.exists()) {
                    file.delete()
                }
            }
        }

        decodedBitmaps.values.forEach { bitmap ->
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
        decodedBitmaps.clear()
        super.onDestroy()
    }

    private fun handleInsertImageClick() {
        launchImagePicker()
    }

    private fun setupEditModeIfNeeded() {
        isEditMode = intent.getBooleanExtra(EXTRA_IS_EDIT_MODE, false)
        if (!isEditMode) {
            return
        }

        editPostId = intent.getStringExtra(EXTRA_EDIT_POST_ID)
        editInitialMessage = intent.getStringExtra(EXTRA_EDIT_INITIAL_MESSAGE)
        editDocument = intent.extras?.let { bundle ->
            BundleCompat.getSerializable(
                bundle,
                EXTRA_EDIT_DOCUMENT,
                DashboardNewsRepository.NewsDocument::class.java
            )
        }
        val documentImagePaths = editDocument?.images
            ?.mapNotNull { image ->
                extractPathFromMarkdown(image.markdown)
                    ?: buildResourcePath(image.resourceId, image.filename)
            }
            ?.filter { it.isNotBlank() }
            .orEmpty()
        val combinedImagePaths = (
            intent.getStringArrayListExtra(EXTRA_EDIT_INITIAL_IMAGE_PATHS) ?: emptyList()
            )
            .plus(documentImagePaths)
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
        previewJob = lifecycleScope.launch {
            delay(PREVIEW_DEBOUNCE_MS)
            updatePreview(text?.toString().orEmpty())
            updateActionAvailability()
        }
    }

    private val listContinuationWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            if (isHandlingListContinuation) {
                return
            }
            if (count <= 0 || s == null) {
                return
            }
            val inserted = s.subSequence(start, start + count)
            val newlineOffset = inserted.lastIndexOf('\n')
            if (newlineOffset >= 0) {
                pendingNewlineIndex = start + newlineOffset
            }
        }

        override fun afterTextChanged(s: Editable?) {
            if (isHandlingListContinuation) {
                return
            }
            val newlineIndex = pendingNewlineIndex ?: return
            pendingNewlineIndex = null
            s ?: return
            handleListContinuation(s, newlineIndex)
        }
    }

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
        val message = createVoiceInput.text?.toString()?.trim().orEmpty()
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
        val confirmTitle = if (isEditMode) {
            R.string.edit_voice_confirm_title
        } else {
            R.string.create_voice_confirm_title
        }
        val confirmMessage = if (isEditMode) {
            R.string.edit_voice_confirm_message
        } else {
            R.string.create_voice_confirm_message
        }
        val positiveAction = if (isEditMode) {
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
            }
            .setNegativeButton(R.string.create_voice_confirm_negative, null)
            .show()
    }

    private fun postVoice(message: String, base: String, credentials: StoredCredentials) {
        setPosting(true)
        lifecycleScope.launch {
            val profile = loadCachedProfile()
            val codes = resolvePostingCodes(profile)
            val preparedContent = runCatching {
                prepareImagesForPosting(base, credentials, message)
            }.getOrElse {
                Toast.makeText(this@CreateVoiceActivity, R.string.create_voice_image_upload_error, Toast.LENGTH_SHORT).show()
                setPosting(false)
                return@launch
            }
            val userPayload = buildUserPayload(profile, credentials, codes)
            val result = repository.createVoice(
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
                    teamName = targetTeamName
                )
            )
            result.onSuccess {
                Toast.makeText(this@CreateVoiceActivity, R.string.create_voice_post_success, Toast.LENGTH_SHORT).show()
                setResult(Activity.RESULT_OK)
                finish()
            }.onFailure {
                Toast.makeText(this@CreateVoiceActivity, R.string.create_voice_post_error, Toast.LENGTH_SHORT).show()
                setPosting(false)
            }
        }
    }

    private fun updateVoice(message: String, base: String, credentials: StoredCredentials) {
        setPosting(true)
        lifecycleScope.launch {
            val doc = editDocument
            if (doc == null || doc.id.isNullOrBlank() || doc.revision.isNullOrBlank()) {
                Toast.makeText(this@CreateVoiceActivity, R.string.edit_voice_missing_document, Toast.LENGTH_SHORT).show()
                setPosting(false)
                return@launch
            }
            val preparedContent = runCatching {
                prepareImagesForPosting(base, credentials, message)
            }.getOrElse {
                Toast.makeText(this@CreateVoiceActivity, R.string.create_voice_image_upload_error, Toast.LENGTH_SHORT).show()
                setPosting(false)
                return@launch
            }
            val images = preparedContent.images.map { image ->
                DashboardNewsRepository.NewsImage(
                    resourceId = image.resourceId,
                    filename = image.filename,
                    markdown = image.markdown
                )
            }
            val result = newsActionsRepository.updateNews(
                baseUrl = base,
                sessionCookie = sessionCookie,
                document = doc,
                message = preparedContent.message,
                images = images,
                teamId = targetTeamId,
                teamName = targetTeamName
            )
            result.onSuccess {
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

    private fun updatePreview(text: String) {
        val trimmed = text.trim()
        val content = if (trimmed.isEmpty()) {
            createVoicePreviewText.alpha = 0.6f
            getString(R.string.create_voice_preview_placeholder)
        } else {
            createVoicePreviewText.alpha = 1f
            trimmed
        }
        val previewSource = transformMarkdownForPreview(content)
        markwon.setMarkdown(createVoicePreviewText, previewSource)
    }

    private fun transformMarkdownForPreview(markdown: String): String {
        var processed = markdown.replace("\n", "  \n")
        if (pendingImages.isNotEmpty()) {
            val pendingByFileName = pendingImages.values.associateBy { it.fileName }
            if (pendingByFileName.isNotEmpty()) {
                val globalPattern = Regex("(!\\[[^\\]]*\\]\\()(.*?)(\\))")
                processed = globalPattern.replace(processed) { matchResult ->
                    val path = matchResult.groupValues.getOrNull(2).orEmpty()
                    val pending = pendingByFileName[path]
                    if (pending != null) {
                        val prefix = matchResult.groupValues.getOrNull(1).orEmpty()
                        val suffix = matchResult.groupValues.getOrNull(3).orEmpty()
                        "$prefix${pending.file.toURI()}$suffix"
                    } else {
                        matchResult.value
                    }
                }
            }
        }
        val base = baseUrl?.trim()?.trimEnd('/')
        if (base.isNullOrEmpty()) {
            return processed
        }
        val resourcesPattern = Regex("!\\[[^\\]]*\\]\\((?:/?db/)?/?resources/([^)]+)\\)")
        return resourcesPattern.replace(processed) { matchResult ->
            val path = matchResult.groupValues.getOrNull(1).orEmpty()
            "![]($base/db/resources/$path)"
        }
    }

    private fun renderPreviewImages() {
        val wrapper = createVoicePreviewImages
        wrapper.removeAllViews()
        if (pendingImages.isEmpty()) {
            wrapper.isVisible = false
            return
        }

        val displayPendings = buildUniquePendingList(includeUploaded = true)

        if (displayPendings.isEmpty()) {
            wrapper.isVisible = false
            return
        }

        wrapper.isVisible = true
        val spacing = resources.getDimensionPixelSize(R.dimen.create_voice_preview_image_spacing)
        val thumbnailSize = resources.getDimensionPixelSize(R.dimen.create_voice_preview_image_thumbnail_size)
        displayPendings.forEachIndexed { index, pending ->
            val preview = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    thumbnailSize,
                    thumbnailSize
                ).apply {
                    setMargins(0, if (index == 0) 0 else spacing, 0, 0)
                }
                adjustViewBounds = false
                scaleType = ImageView.ScaleType.CENTER_CROP
                contentDescription = pending.fileName
                setOnClickListener {
                    showImageOptionsDialog(pending)
                }
            }
            wrapper.addView(preview)
            lifecycleScope.launch(Dispatchers.Default) {
                val bitmap = decodedBitmaps.getOrPut(pending.id) {
                    BitmapFactory.decodeByteArray(pending.jpegBytes, 0, pending.jpegBytes.size)
                }
                withContext(Dispatchers.Main) {
                    preview.setImageBitmap(bitmap)
                }
            }
        }
    }

    private fun showImageOptionsDialog(pending: PendingVoiceImage) {
        val optionItems = arrayOf(
            getString(R.string.create_voice_image_option_view),
            getString(R.string.create_voice_image_option_delete)
        )
        val optionIcons = intArrayOf(
            R.drawable.icon_image_view,
            R.drawable.ic_dashboard_delete_24
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.create_voice_image_options_title)
            .setAdapter(ImageOptionAdapter(this, optionItems, optionIcons)) { dialog, which ->
                when (which) {
                    0 -> showImagePreviewDialog(pending)
                    1 -> deletePendingImage(pending)
                }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showImagePreviewDialog(pending: PendingVoiceImage) {
        val imageView = ImageView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = pending.fileName
            val padding = resources.getDimensionPixelSize(R.dimen.create_voice_image_preview_dialog_padding)
            setPadding(padding, padding, padding, padding)
        }
        lifecycleScope.launch(Dispatchers.Default) {
            val bitmap = decodedBitmaps.getOrPut(pending.id) {
                BitmapFactory.decodeByteArray(pending.jpegBytes, 0, pending.jpegBytes.size)
            }
            withContext(Dispatchers.Main) {
                imageView.setImageBitmap(bitmap)
            }
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(pending.fileName)
            .setView(imageView)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun deletePendingImage(pending: PendingVoiceImage) {
        val normalizedKey = derivePendingNormalizedKey(pending)
        val idsToRemove = pendingImages.values
            .filter { derivePendingNormalizedKey(it) == normalizedKey }
            .map { it.id }

        idsToRemove.forEach { id ->
            val removed = pendingImages.remove(id) ?: return@forEach
            decodedBitmaps.remove(id)?.let { bitmap ->
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }
            removeImageMarkdownReferences(removed)
            if (removed.file.exists()) {
                removed.file.delete()
            }
        }

        updatePreview(createVoiceInput.text?.toString().orEmpty())
        renderPreviewImages()
    }

    private fun removeImageMarkdownReferences(pending: PendingVoiceImage) {
        val editable = createVoiceInput.text ?: return
        val current = editable.toString()
        var updated = current
        val candidates = listOfNotNull(
            pending.fileName.takeIf { it.isNotBlank() },
            extractPathFromMarkdown(pending.uploadedMarkdown).takeIf { !it.isNullOrBlank() }
        ).distinct()

        candidates.forEach { candidate ->
            val escaped = Regex.escape(candidate.trim())
            val pattern = Regex("(?:^|\\n)!\\[[^\\]]*\\]\\((?:https?://[^)]+/)?(?:/?db/)?/?$escaped\\)\\n?")
            updated = pattern.replace(updated, "\n")
        }

        updated = updated
            .replace(Regex("\\n{3,}"), "\n\n")
            .trimEnd()

        if (updated != current) {
            editable.replace(0, editable.length, updated)
            createVoiceInput.setSelection(updated.length.coerceAtLeast(0))
        }
    }

    private class ImageOptionAdapter(
        context: CreateVoiceActivity,
        private val items: Array<String>,
        private val icons: IntArray
    ) : android.widget.ArrayAdapter<String>(
        context,
        android.R.layout.select_dialog_item,
        items
    ) {
        override fun getView(position: Int, convertView: android.view.View?, parent: ViewGroup): android.view.View {
            val view = super.getView(position, convertView, parent)
            val text = view.findViewById<TextView>(android.R.id.text1)
            text.setCompoundDrawablesRelativeWithIntrinsicBounds(icons[position], 0, 0, 0)
            text.compoundDrawablePadding = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                12f,
                context.resources.displayMetrics
            ).toInt()
            return view
        }
    }

    private fun buildUniquePendingList(includeUploaded: Boolean = false): List<PendingVoiceImage> {
        val uniquePendingMap = LinkedHashMap<String, PendingVoiceImage>()
        pendingImages.values.forEach { pending ->
            if (!includeUploaded && !pending.resourceId.isNullOrBlank()) {
                return@forEach
            }
            val key = derivePendingNormalizedKey(pending)
            if (key.isNotBlank() && !uniquePendingMap.containsKey(key)) {
                uniquePendingMap[key] = pending
            }
        }
        return uniquePendingMap.values.toList()
    }

    private fun updateActionAvailability() {
        val hasContent = !createVoiceInput.text.isNullOrBlank()
        val enabled = hasContent && !isPosting && isSessionReady
        toolbar.menu?.findItem(R.id.action_post_voice)?.isEnabled = enabled
        createVoiceSubmitButton.isEnabled = enabled
    }

    private fun applyWrappedFormatting(
        prefix: String,
        suffix: String,
        placeholder: String,
        placeCursorInsideWhenNoSelection: Boolean = false
    ) {
        val editText = createVoiceInput
        val editable = editText.text ?: return
        val start = max(0, editText.selectionStart)
        val end = max(0, editText.selectionEnd)
        val rangeStart = min(start, end)
        val rangeEnd = max(start, end)
        val hasSelection = rangeStart != rangeEnd
        val selected = if (hasSelection) {
            editable.subSequence(rangeStart, rangeEnd).toString()
        } else {
            placeholder
        }
        val replacement = "$prefix$selected$suffix"
        editable.replace(rangeStart, rangeEnd, replacement)
        val cursorPosition = when {
            hasSelection -> rangeStart + replacement.length
            placeCursorInsideWhenNoSelection -> rangeStart + prefix.length
            else -> rangeStart + prefix.length + selected.length
        }
        editText.setSelection(cursorPosition.coerceIn(0, editable.length))
    }

    private fun applyHeadingFormatting() {
        val editText = createVoiceInput
        val editable = editText.text ?: return
        val cursor = max(0, editText.selectionStart)
        val lineStart = findLineStart(editable, cursor)
        val lineLength = editable.length
        var prefixEnd = lineStart
        var currentHashes = 0
        while (prefixEnd < lineLength && editable[prefixEnd] == '#') {
            currentHashes++
            prefixEnd++
        }
        while (
            prefixEnd < lineLength &&
            editable[prefixEnd] == ' ' &&
            editable[prefixEnd] != '\n'
        ) {
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

    private fun applyBulletFormatting() {
        val editText = createVoiceInput
        val editable = editText.text ?: return
        val start = max(0, editText.selectionStart)
        val end = max(0, editText.selectionEnd)
        val rangeStart = min(start, end)
        val rangeEnd = max(start, end)
        val selection = editable.subSequence(rangeStart, rangeEnd).toString()
        if (selection.isBlank()) {
            editable.replace(rangeStart, rangeEnd, "- ")
            editText.setSelection((rangeStart + 2).coerceAtMost(editable.length))
            return
        }
        val formatted = selection.lines().joinToString("\n") { line ->
            val trimmed = line.trimStart()
            val indent = line.substring(0, line.length - trimmed.length)
            "$indent- ${trimmed.ifEmpty { getString(R.string.create_voice_format_placeholder) }}"
        }
        editable.replace(rangeStart, rangeEnd, formatted)
        editText.setSelection((rangeStart + formatted.length).coerceAtMost(editable.length))
    }

    private fun applyNumberedListFormatting() {
        val editText = createVoiceInput
        val editable = editText.text ?: return
        val start = max(0, editText.selectionStart)
        val end = max(0, editText.selectionEnd)
        val rangeStart = min(start, end)
        val rangeEnd = max(start, end)
        val selection = editable.subSequence(rangeStart, rangeEnd).toString()
        if (selection.isBlank()) {
            editable.replace(rangeStart, rangeEnd, "1. ")
            editText.setSelection((rangeStart + 3).coerceAtMost(editable.length))
            return
        }
        val placeholder = getString(R.string.create_voice_format_placeholder)
        val formatted = selection.lines().mapIndexed { index, line ->
            val trimmed = line.trimStart()
            val indent = line.substring(0, line.length - trimmed.length)
            val content = trimmed.ifEmpty { placeholder }
            "$indent${index + 1}. $content"
        }.joinToString("\n")
        editable.replace(rangeStart, rangeEnd, formatted)
        editText.setSelection((rangeStart + formatted.length).coerceAtMost(editable.length))
    }

    private fun applyQuoteFormatting() {
        val editText = createVoiceInput
        val editable = editText.text ?: return
        val start = max(0, editText.selectionStart)
        val end = max(0, editText.selectionEnd)
        val rangeStart = min(start, end)
        val rangeEnd = max(start, end)
        val selection = editable.subSequence(rangeStart, rangeEnd).toString()
        if (selection.isBlank()) {
            editable.replace(rangeStart, rangeEnd, "> ")
            editText.setSelection((rangeStart + 2).coerceAtMost(editable.length))
            return
        }
        val placeholder = getString(R.string.create_voice_format_placeholder)
        val formatted = selection.lines().joinToString("\n") { line ->
            val trimmed = line.trimStart()
            val indent = line.substring(0, line.length - trimmed.length)
            val content = trimmed.ifEmpty { placeholder }
            "$indent> $content"
        }
        editable.replace(rangeStart, rangeEnd, formatted)
        editText.setSelection((rangeStart + formatted.length).coerceAtMost(editable.length))
    }

    private fun applyLinkFormatting() {
        val editText = createVoiceInput
        val editable = editText.text ?: return
        val start = max(0, editText.selectionStart)
        val end = max(0, editText.selectionEnd)
        val rangeStart = min(start, end)
        val rangeEnd = max(start, end)
        val selected = editable.subSequence(rangeStart, rangeEnd).toString()
            .takeIf { it.isNotBlank() }
            ?: ""
        showInsertLinkDialog(rangeStart, rangeEnd, selected)
    }

    private fun showInsertLinkDialog(rangeStart: Int, rangeEnd: Int, selectedTitle: String) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val spacing = (12 * resources.displayMetrics.density).roundToInt()
            setPadding(spacing, spacing, spacing, spacing)
        }

        val titleInputLayout = TextInputLayout(this).apply {
            hint = getString(R.string.create_voice_link_title_hint)
        }
        val titleInput = TextInputEditText(this).apply {
            setText(selectedTitle)
            setSelection(text?.length ?: 0)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        titleInputLayout.addView(
            titleInput,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val urlInputLayout = TextInputLayout(this).apply {
            hint = getString(R.string.create_voice_link_url_hint)
        }
        val urlInput = TextInputEditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        }
        urlInputLayout.addView(
            urlInput,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        container.addView(titleInputLayout)
        container.addView(urlInputLayout)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.create_voice_link_dialog_title)
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.create_voice_link_insert_button, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                val linkTitle = titleInput.text?.toString()?.trim().orEmpty()
                val linkUrl = urlInput.text?.toString()?.trim().orEmpty()
                if (linkTitle.isBlank() || linkUrl.isBlank()) {
                    Toast.makeText(
                        this,
                        R.string.create_voice_link_required_fields,
                        Toast.LENGTH_SHORT
                    ).show()
                    return@setOnClickListener
                }
                insertLinkMarkdown(rangeStart, rangeEnd, linkTitle, linkUrl)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun insertLinkMarkdown(rangeStart: Int, rangeEnd: Int, title: String, url: String) {
        val editText = createVoiceInput
        val editable = editText.text ?: return
        val safeStart = rangeStart.coerceIn(0, editable.length)
        val safeEnd = rangeEnd.coerceIn(safeStart, editable.length)
        val replacement = "[$title]($url)"
        editable.replace(safeStart, safeEnd, replacement)
        val cursorPosition = (safeStart + replacement.length).coerceAtMost(editable.length)
        editText.setSelection(cursorPosition)
    }

    private fun handleListContinuation(editable: Editable, newlineIndex: Int) {
        if (newlineIndex <= 0 || newlineIndex > editable.length) {
            return
        }
        val previousLineStart = findLineStart(editable, newlineIndex)
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
                insertListPrefix(editable, newlineIndex, "$indent$marker")
            } else {
                removeListPrefix(editable, previousLineStart + indentLength, marker.length)
            }
            return
        }

        val numberMatch = NUMBERED_LIST_REGEX.matchEntire(contentAfterIndent)
        if (numberMatch != null) {
            val number = numberMatch.groupValues.getOrNull(1)?.toIntOrNull() ?: return
            val remainder = numberMatch.groupValues.getOrNull(2).orEmpty()
            val markerLength = numberMatch.groupValues[1].length + 2
            if (remainder.isBlank()) {
                removeListPrefix(editable, previousLineStart + indentLength, markerLength)
            } else {
                val nextMarker = "$indent${number + 1}. "
                insertListPrefix(editable, newlineIndex, nextMarker)
            }
        }
    }

    private fun insertListPrefix(editable: Editable, newlineIndex: Int, prefix: String) {
        val insertPosition = (newlineIndex + 1).coerceAtMost(editable.length)
        isHandlingListContinuation = true
        editable.insert(insertPosition, prefix)
        createVoiceInput.setSelection((insertPosition + prefix.length).coerceAtMost(editable.length))
        isHandlingListContinuation = false
    }

    private fun removeListPrefix(editable: Editable, start: Int, markerLength: Int) {
        val end = (start + markerLength).coerceAtMost(editable.length)
        isHandlingListContinuation = true
        editable.delete(start, end)
        createVoiceInput.setSelection(start.coerceAtMost(editable.length))
        isHandlingListContinuation = false
    }

    private fun findIndentLength(line: String): Int {
        val index = line.indexOfFirst { !it.isWhitespace() }
        return if (index == -1) line.length else index
    }

    private suspend fun handleImageSelection(uri: Uri) {
        val pendingResult = withContext(Dispatchers.IO) {
            runCatching { VoiceImageFactory.createPendingVoiceImage(uri, contentResolver, cacheDir, ::generatePendingImageId) }
        }
        pendingResult.onSuccess { pending ->
            pendingImages[pending.id] = pending
            if (isEditMode) {
                insertTemporaryImagePlaceholder(pending.fileName)
            }
            updatePreview(createVoiceInput.text?.toString().orEmpty())
            renderPreviewImages()
        }.onFailure {
            Toast.makeText(this, R.string.create_voice_image_processing_error, Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun loadEditInitialImages() {
        if (!isEditMode || editInitialImagePaths.isEmpty() || editImagesLoaded) {
            return
        }
        val base = baseUrl ?: return
        val loaded = coroutineScope {
            val semaphore = Semaphore(10)
            editInitialImagePaths.map { path ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        runCatching { VoiceImageFetcher.fetchExistingImage(httpClient, cacheDir, sessionCookie, base, path, { VoiceImageFactory.generateImageFileName() }, { generatePendingImageId(it) }) }.getOrNull()
                    }
                }
            }.awaitAll().filterNotNull()
        }
        if (loaded.isEmpty()) {
            return
        }
        editImagesLoaded = true
        loaded.forEach { pending ->
            pendingImages[pending.id] = pending
        }
        renderPreviewImages()
    }

    private fun extractPathFromMarkdown(markdown: String?): String? {
        if (markdown.isNullOrBlank()) {
            return null
        }
        val match = MARKDOWN_IMAGE_REGEX.find(markdown)
        return match?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun buildResourcePath(resourceId: String?, fileName: String?): String? {
        if (resourceId.isNullOrBlank() || fileName.isNullOrBlank()) {
            return null
        }
        return "resources/${resourceId.trim()}/${fileName.trim()}"
    }

    private fun derivePendingNormalizedKey(pending: PendingVoiceImage): String {
        val candidates = listOfNotNull(
            pending.uploadedMarkdown?.let { extractPathFromMarkdown(it) },
            buildResourcePath(pending.resourceId, pending.fileName),
            pending.fileName
        )
        val normalized = candidates
            .map { candidate -> normalizeImagePath(candidate) }
            .firstOrNull { candidate -> candidate.isNotBlank() }
        return (normalized ?: normalizeImagePath(pending.fileName)).trim()
    }

    private fun mergeImagePaths(paths: List<String>): List<String> {
        return paths.distinctBy { normalizeImagePath(it) }
    }

    private fun normalizeImagePath(path: String): String {
        val extracted = extractPathFromMarkdown(path) ?: path
        val trimmed = extracted.trim()
        val resourcesMatch = Regex("resources/[^/]+/[^/]+", RegexOption.IGNORE_CASE)
            .find(trimmed)
        val reduced = if (resourcesMatch != null) {
            resourcesMatch.value
        } else {
            trimmed.trimStart('/').removePrefix("db/").trimStart('/')
        }
        return reduced.lowercase(Locale.US)
    }

    private fun deduplicateMessageImages(markdown: String): Pair<String, Set<String>> {
        if (markdown.isBlank()) {
            return "" to emptySet()
        }
        val builder = StringBuilder()
        var lastIndex = 0
        val seen = LinkedHashSet<String>()
        MARKDOWN_IMAGE_REGEX.findAll(markdown).forEach { match ->
            val path = match.groupValues.getOrNull(1)
            val normalized = path?.let { normalizeImagePath(it) }.orEmpty()
            val keep = normalized.isNotBlank() && seen.add(normalized)
            builder.append(markdown.substring(lastIndex, match.range.first))
            if (keep) {
                builder.append(match.value)
            }
            lastIndex = match.range.last + 1
        }
        if (lastIndex < markdown.length) {
            builder.append(markdown.substring(lastIndex))
        }
        return builder.toString() to seen
    }

    private fun insertTemporaryImagePlaceholder(fileName: String) {
        val editable = createVoiceInput.text ?: return
        val placeholder = "![]($fileName)"
        val current = editable.toString()
        if (current.contains(placeholder)) {
            return
        }
        val builder = StringBuilder(current)
        if (builder.isNotEmpty() && builder.last() != '\n') {
            builder.append('\n')
        }
        builder.append('\n').append(placeholder)
        val updated = builder.toString()
        editable.replace(0, editable.length, updated)
        createVoiceInput.setSelection(updated.length)
    }

    private fun generatePendingImageId(baseName: String): String {
        var candidate = baseName
        var counter = 1
        while (pendingImages.containsKey(candidate)) {
            candidate = "${baseName}_$counter"
            counter++
        }
        return candidate
    }

    private fun findLineStart(editable: Editable, position: Int): Int {
        var index = position - 1
        while (index >= 0) {
            if (editable[index] == '\n') {
                return index + 1
            }
            index--
        }
        return 0
    }

    private fun setMarkdownToolbarEnabled(enabled: Boolean) {
        markdownToolbar.isEnabled = enabled
        for (index in 0 until markdownToolbar.childCount) {
            markdownToolbar.getChildAt(index)?.isEnabled = enabled
        }
    }

    private suspend fun prepareImagesForPosting(
        baseUrl: String,
        credentials: StoredCredentials,
        originalMessage: String
    ): PreparedVoicePost {
        val context = buildImageResourceContext(credentials)
        val (dedupedMessage, dedupedExistingImages) = deduplicateMessageImages(originalMessage)
        val uniquePendings = buildUniquePendingList()
        if (uniquePendings.isEmpty()) {
            return PreparedVoicePost(dedupedMessage, emptyList())
        }
        var updatedMessage = dedupedMessage
        val preparedImages = LinkedHashMap<String, VoicesComposerRepository.ImagePayload>()
        val normalizedMessageImages = dedupedExistingImages.toMutableSet()
        val uploadResults = coroutineScope {
            uniquePendings.map { pending ->
                async {
                    val requiresUpload = shouldUploadPending(pending)
                    val markdown = if (requiresUpload) {
                        ensureImageUpload(baseUrl, credentials, context, pending)
                    } else {
                        resolveExistingMarkdown(pending) ?: ensureImageUpload(baseUrl, credentials, context, pending)
                    }
                    pending to markdown
                }
            }.awaitAll()
        }

        for ((pending, markdown) in uploadResults) {
            val normalizedPath = normalizeImagePath(markdown)
            val replaced = MarkdownUtils.replaceImagePlaceholder(updatedMessage, pending.fileName, markdown)
            if (!normalizedMessageImages.contains(normalizedPath)) {
                updatedMessage = ensureMarkdownPresent(replaced, markdown)
                normalizedMessageImages += normalizedPath
            } else {
                updatedMessage = replaced
            }
            val resourceId = pending.resourceId
            if (resourceId != null && normalizedPath.isNotBlank()) {
                preparedImages.putIfAbsent(normalizedPath, VoicesComposerRepository.ImagePayload(
                    resourceId = resourceId,
                    filename = pending.fileName,
                    markdown = markdown
                ))
            }
        }
        return PreparedVoicePost(updatedMessage, preparedImages.values.toList())
    }

    private fun shouldUploadPending(pending: PendingVoiceImage): Boolean {
        pending.uploadedMarkdown?.let { existing ->
            val path = extractPathFromMarkdown(existing)
            val normalized = path?.removePrefix("db/")?.trimStart('/') ?: ""
            if (normalized.startsWith("resources/", ignoreCase = true)
                || path?.startsWith("http://", ignoreCase = true) == true
                || path?.startsWith("https://", ignoreCase = true) == true
            ) {
                return false
            }
        }
        if (pending.resourceId != null && pending.resourceRevision != null) {
            return false
        }
        return true
    }

    private fun resolveExistingMarkdown(pending: PendingVoiceImage): String? {
        pending.uploadedMarkdown?.let { existing ->
            val path = extractPathFromMarkdown(existing)
            val normalized = path?.removePrefix("db/")?.trimStart('/') ?: ""
            val resolved = when {
                path.isNullOrBlank() -> existing
                path.startsWith("http://", ignoreCase = true) || path.startsWith("https://", ignoreCase = true) ->
                    "![](${path.trim()})"
                normalized.startsWith("resources/", ignoreCase = true) -> "![](${normalized})"
                else -> existing
            }
            pending.uploadedMarkdown = resolved
            return resolved
        }
        val resourceId = pending.resourceId ?: return null
        val markdown = "![](resources/${resourceId}/${pending.fileName})"
        pending.uploadedMarkdown = markdown
        return markdown
    }

    private suspend fun ensureImageUpload(
        baseUrl: String,
        credentials: StoredCredentials,
        context: VoiceImageResourceContext,
        pending: PendingVoiceImage
    ): String {
        pending.uploadedMarkdown?.let { existing ->
            val path = extractPathFromMarkdown(existing)
            val normalized = path?.removePrefix("db/")?.trimStart('/') ?: ""
            val shouldUpload = normalized.startsWith("post", ignoreCase = true)
                    || normalized.isEmpty()
            if (!shouldUpload) {
                val resolved = when {
                    path.isNullOrBlank() -> existing
                    path.startsWith("http://", ignoreCase = true) || path.startsWith("https://", ignoreCase = true) ->
                        "![](${path.trim()})"
                    normalized.startsWith("resources/", ignoreCase = true) ->
                        "![](${normalized})"
                    else -> existing
                }
                pending.uploadedMarkdown = resolved
                return resolved
            }
        }
        val resourceId = pending.resourceId
        val resourceRevision = pending.resourceRevision
        val creationResponse = if (resourceId != null && resourceRevision != null) {
            VoicesComposerRepository.ResourceCreationResponse(
                ok = true,
                id = resourceId,
                revision = resourceRevision
            )
        } else {
            val metadata = VoicesComposerRepository.ResourceMetadataRequest.fromContext(context, pending.fileName)
            repository.createResourceDocument(baseUrl, credentials, metadata)
        }
        pending.resourceId = creationResponse.id
        pending.resourceRevision = creationResponse.revision
        val uploadResponse = repository.uploadResourceBinary(
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
        val sanitizedId = resolvedResourceId.trim()
        val sanitizedName = resolvedFileName.trim()
        val normalizedMarkdown = "![](resources/${sanitizedId}/${sanitizedName})"
        pending.uploadedMarkdown = normalizedMarkdown
        return normalizedMarkdown
    }

    private fun ensureMarkdownPresent(message: String, markdown: String): String {
        if (message.contains(markdown)) {
            return message
        }
        val resourcePath = extractResourcePath(markdown)
        if (resourcePath != null) {
            val pattern = Regex("!\\[[^\\]]*\\]\\((?:https?://[^)]+/)?(?:/?db/)?/?${Regex.escape(resourcePath)})\\)")
            if (pattern.containsMatchIn(message)) {
                return message
            }
        }
        val builder = StringBuilder(message.trimEnd())
        if (builder.isNotEmpty()) {
            builder.append("\n\n")
        }
        builder.append(markdown)
        return builder.toString()
    }

    private fun extractResourcePath(markdown: String): String? {
        val matcher = MARKDOWN_IMAGE_REGEX.find(markdown) ?: return null
        val rawPath = matcher.groupValues.getOrNull(1)?.trim('/') ?: return null
        val trimmed = when {
            rawPath.startsWith("db/resources/", ignoreCase = true) -> rawPath.removePrefix("db/")
            rawPath.startsWith("resources/", ignoreCase = true) -> rawPath
            else -> return null
        }
        return trimmed
    }

    private suspend fun buildImageResourceContext(credentials: StoredCredentials): VoiceImageResourceContext {
        val preferences = SecurePreferencesProvider.getServerPreferences(applicationContext)
        val androidId = preferences.getString(KEY_DEVICE_ANDROID_ID, null)?.takeIf { it.isNotBlank() }
        val customDeviceName = preferences.getString(KEY_DEVICE_CUSTOM_DEVICE_NAME, null)?.takeIf { it.isNotBlank() }
        val storedServerCode = preferences.getString(KEY_SERVER_CODE, null)?.takeIf { it.isNotBlank() }
        val storedParentCode = preferences.getString(KEY_SERVER_PARENT_CODE, null)?.takeIf { it.isNotBlank() }
        val profile = loadCachedProfile()
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
            val json = JSONObject(rawDocument)
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

    private fun resolvePostingCodes(profile: UserProfile?): ProfileCodes? {
        val preferences = SecurePreferencesProvider.getServerPreferences(applicationContext)
        val storedParentCode = preferences.getString(KEY_SERVER_PARENT_CODE, null)?.takeIf { it.isNotBlank() }
        val storedServerCode = preferences.getString(KEY_SERVER_CODE, null)?.takeIf { it.isNotBlank() }
        val parsedCodes = parseCodesFromProfile(profile?.rawDocument)
        val resolvedPlanet = serverCode?.takeIf { it.isNotBlank() }
            ?: storedServerCode
            ?: parsedCodes?.planetCode
        val resolvedParent = storedParentCode ?: parsedCodes?.parentCode
        if (resolvedPlanet.isNullOrBlank() && resolvedParent.isNullOrBlank()) {
            return null
        }
        return ProfileCodes(resolvedPlanet, resolvedParent)
    }

    private fun buildUserPayload(
        profile: UserProfile?,
        credentials: StoredCredentials,
        codes: ProfileCodes?
    ): VoicesComposerRepository.UserPayload {
        val fallbackId = "org.couchdb.user:${credentials.username}"
        val rawDocument = profile?.rawDocument
        if (rawDocument.isNullOrBlank()) {
            return VoicesComposerRepository.UserPayload(
                id = fallbackId,
                name = credentials.username,
                firstName = profile?.firstName,
                middleName = profile?.middleName,
                lastName = profile?.lastName,
                email = profile?.email,
                language = profile?.language,
                phoneNumber = profile?.phoneNumber,
                planetCode = codes?.planetCode,
                parentCode = codes?.parentCode,
                roles = null,
                joinDate = null,
                attachments = null
            )
        }
        val json = JSONObject(rawDocument)
        val attachments = parseAttachmentPayloads(json.optJSONObject("_attachments"))
        val roles = json.optJSONArray("roles")?.let { array ->
            val length = array.length()
            val collected = ArrayList<String>(length)
            for (index in 0 until length) {
                val s = array.optString(index)
                if (s.isNotBlank()) {
                    collected.add(s)
                }
            }
            collected.takeIf { it.isNotEmpty() }
        }
        val joinDate = if (json.has("joinDate")) json.optLong("joinDate") else null
        val nonNullProfile = requireNotNull(profile)
        return VoicesComposerRepository.UserPayload(
            id = json.optString("_id").takeIf { it.isNotBlank() } ?: fallbackId,
            name = json.optString("name").takeIf { it.isNotBlank() } ?: credentials.username,
            firstName = json.optString("firstName").takeIf { it.isNotBlank() } ?: nonNullProfile.firstName,
            middleName = json.optString("middleName").takeIf { it.isNotBlank() } ?: nonNullProfile.middleName,
            lastName = json.optString("lastName").takeIf { it.isNotBlank() } ?: nonNullProfile.lastName,
            email = json.optString("email").takeIf { it.isNotBlank() } ?: nonNullProfile.email,
            language = json.optString("language").takeIf { it.isNotBlank() } ?: nonNullProfile.language,
            phoneNumber = json.optString("phoneNumber").takeIf { it.isNotBlank() } ?: nonNullProfile.phoneNumber,
            planetCode = json.optString("planetCode").takeIf { it.isNotBlank() } ?: codes?.planetCode,
            parentCode = json.optString("parentCode").takeIf { it.isNotBlank() } ?: codes?.parentCode,
            roles = roles,
            joinDate = joinDate,
            attachments = attachments
        )
    }

    private fun parseAttachmentPayloads(
        attachmentsObject: JSONObject?
    ): Map<String, VoicesComposerRepository.AttachmentPayload>? {
        attachmentsObject ?: return null
        val iterator = attachmentsObject.keys()
        if (!iterator.hasNext()) {
            return null
        }
        val result = mutableMapOf<String, VoicesComposerRepository.AttachmentPayload>()
        while (iterator.hasNext()) {
            val key = iterator.next()
            val attachment = attachmentsObject.optJSONObject(key) ?: continue
            val contentType = attachment.optString("content_type").takeIf { it.isNotBlank() }
            val revpos = if (attachment.has("revpos")) attachment.optInt("revpos") else null
            val digest = attachment.optString("digest").takeIf { it.isNotBlank() }
            val length = if (attachment.has("length")) attachment.optInt("length") else null
            val stub = if (attachment.has("stub")) attachment.optBoolean("stub") else null
            val data = attachment.optString("data").takeIf { attachment.has("data") && it.isNotBlank() }
            result[key] = VoicesComposerRepository.AttachmentPayload(
                contentType = contentType,
                revpos = revpos,
                digest = digest,
                length = length,
                stub = stub,
                data = data
            )
        }
        return result.takeIf { it.isNotEmpty() }
    }

    companion object {
        private const val PREVIEW_DEBOUNCE_MS = 150L
        private const val MAX_HEADING_LEVEL = 6
        private val NUMBERED_LIST_REGEX = Regex("^(\\d+)\\.\\s*(.*)$")
        private val MARKDOWN_IMAGE_REGEX = Regex("!\\[[^\\]]*\\]\\(([^)]+)\\)")
        private const val KEY_DEVICE_ANDROID_ID = "device_android_id"
        private const val KEY_DEVICE_CUSTOM_DEVICE_NAME = "device_custom_device_name"
        private const val KEY_SERVER_PARENT_CODE = "server_parent_code"
        private const val KEY_SERVER_CODE = "server_code"
        private const val PRIVATE_FOR_COMMUNITY = "community"
        private const val DEFAULT_DEVICE_NAME = "Android"

        const val EXTRA_IS_EDIT_MODE = "extra_is_edit_mode"
        const val EXTRA_EDIT_POST_ID = "extra_edit_post_id"
        const val EXTRA_EDIT_INITIAL_MESSAGE = "extra_edit_initial_message"
        const val EXTRA_EDIT_INITIAL_IMAGE_PATHS = "extra_edit_initial_image_paths"
        const val EXTRA_EDIT_DOCUMENT = "extra_edit_document"
        const val EXTRA_TARGET_TEAM_ID = "extra_target_team_id"
        const val EXTRA_TARGET_TEAM_NAME = "extra_target_team_name"
    }


}




data class PreparedVoicePost(
    val message: String,
    val images: List<VoicesComposerRepository.ImagePayload>
)
