/*
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2026-08-12
 */

package org.ole.planet.myplanet.lite.dashboard

import android.widget.EditText
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.ole.planet.myplanet.lite.LanguagePreferences
import org.ole.planet.myplanet.lite.R
import java.io.IOException
import java.util.WeakHashMap
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.min

private data class VoiceAiState(
    val button: MaterialButton,
    val input: EditText,
    val progress: android.view.View,
    var apiKey: String? = null,
    var model: String = DEFAULT_OPEN_AI_MODEL,
    var isEnhancing: Boolean = false,
)

private data class VoiceAiSelection(
    val text: String,
    val start: Int,
    val end: Int,
)

private val createVoiceAiStates = WeakHashMap<CreateVoiceActivity, VoiceAiState>()
private val replyVoiceAiStates = WeakHashMap<DashboardPostDetailActivity, VoiceAiState>()
private val voiceAiConfigurationRepository = ServerConfigurationRepository(client = SharedBitmapDependencies.client)
private val voiceAiClient = VoiceAiContentClient(client = SharedBitmapDependencies.client)

internal fun CreateVoiceActivity.setupVoiceAiButton(button: MaterialButton) {
    val state = VoiceAiState(button = button, input = createVoiceInput, progress = createVoiceProgress)
    createVoiceAiStates[this] = state
    button.setOnClickListener { enhanceCreateVoiceContent() }
    createVoiceInput.doAfterTextChanged { updateCreateVoiceAiAvailability() }
    updateCreateVoiceAiAvailability()
}

internal suspend fun CreateVoiceActivity.loadVoiceAiConfiguration() {
    val state = createVoiceAiStates[this] ?: return
    val configuration = voiceAiConfigurationRepository.fetchConfiguration(baseUrl).getOrNull()
    state.apiKey = configuration?.keys?.openAi?.trim()?.takeIf { it.isNotEmpty() }
    state.model = configuration?.models?.openAi?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_OPEN_AI_MODEL
    updateCreateVoiceAiAvailability()
}

internal fun CreateVoiceActivity.updateCreateVoiceAiAvailability() {
    val state = createVoiceAiStates[this] ?: return
    state.button.isEnabled =
        state.apiKey != null && !state.input.text.isNullOrBlank() && !state.isEnhancing && !isPosting
}

private fun CreateVoiceActivity.enhanceCreateVoiceContent() {
    val state = createVoiceAiStates[this] ?: return
    enhanceVoiceContent(state)
}

internal fun DashboardPostDetailActivity.setupReplyVoiceAiButton(button: MaterialButton) {
    val progress: android.view.View = findViewById(R.id.dashboardReplyAiProgress)
    val state = VoiceAiState(button = button, input = replyInput, progress = progress)
    replyVoiceAiStates[this] = state
    button.setOnClickListener { enhanceReplyVoiceContent() }
    replyInput.doAfterTextChanged { updateReplyVoiceAiAvailability() }
    updateReplyVoiceAiAvailability()
}

internal suspend fun DashboardPostDetailActivity.loadReplyVoiceAiConfiguration() {
    val state = replyVoiceAiStates[this] ?: return
    val configuration = voiceAiConfigurationRepository.fetchConfiguration(baseUrl).getOrNull()
    state.apiKey = configuration?.keys?.openAi?.trim()?.takeIf { it.isNotEmpty() }
    state.model = configuration?.models?.openAi?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_OPEN_AI_MODEL
    updateReplyVoiceAiAvailability()
}

internal fun DashboardPostDetailActivity.updateReplyVoiceAiAvailability() {
    val state = replyVoiceAiStates[this] ?: return
    state.button.isEnabled =
        state.apiKey != null && !state.input.text.isNullOrBlank() && !state.isEnhancing &&
        !isPostingReply && isReplyComposerExpanded
}

private fun DashboardPostDetailActivity.enhanceReplyVoiceContent() {
    val state = replyVoiceAiStates[this] ?: return
    enhanceVoiceContent(state)
}

private fun CreateVoiceActivity.enhanceVoiceContent(state: VoiceAiState) {
    val selection = state.captureSelection() ?: return
    val apiKey = state.apiKey ?: return
    if (state.isEnhancing) return
    state.isEnhancing = true
    state.input.isEnabled = false
    state.progress.isVisible = true
    updateCreateVoiceAiAvailability()
    lifecycleScope.launch {
        val languageTag = LanguagePreferences.getSelectedLanguage(this@enhanceVoiceContent)
        val enhanced = voiceAiClient.enhance(selection.text, apiKey, state.model, languageTag)
        state.isEnhancing = false
        state.input.isEnabled = !isPosting
        state.progress.isVisible = false
        if (enhanced.isNullOrBlank()) {
            Toast.makeText(this@enhanceVoiceContent, R.string.create_voice_generate_ai_error, Toast.LENGTH_SHORT).show()
        } else {
            state.replaceSelection(selection, enhanced)
        }
        updateCreateVoiceAiAvailability()
    }
}

private fun DashboardPostDetailActivity.enhanceVoiceContent(state: VoiceAiState) {
    val selection = state.captureSelection() ?: return
    val apiKey = state.apiKey ?: return
    if (state.isEnhancing) return
    state.isEnhancing = true
    state.input.isEnabled = false
    state.progress.isVisible = true
    updateReplyVoiceAiAvailability()
    lifecycleScope.launch {
        val languageTag = LanguagePreferences.getSelectedLanguage(this@enhanceVoiceContent)
        val enhanced = voiceAiClient.enhance(selection.text, apiKey, state.model, languageTag)
        state.isEnhancing = false
        state.input.isEnabled = (headerItem.canReply || isEditingComment) && !isPostingReply
        state.progress.isVisible = false
        if (enhanced.isNullOrBlank()) {
            Toast.makeText(this@enhanceVoiceContent, R.string.create_voice_generate_ai_error, Toast.LENGTH_SHORT).show()
        } else {
            state.replaceSelection(selection, enhanced)
        }
        updateReplyVoiceAiAvailability()
    }
}

private fun VoiceAiState.captureSelection(): VoiceAiSelection? {
    val content = input.text ?: return null
    val first = input.selectionStart.coerceAtLeast(0)
    val second = input.selectionEnd.coerceAtLeast(0)
    val selectionStart = min(first, second).coerceAtMost(content.length)
    val selectionEnd = max(first, second).coerceAtMost(content.length)
    val hasSelection = selectionStart < selectionEnd
    val start = if (hasSelection) selectionStart else 0
    val end = if (hasSelection) selectionEnd else content.length
    val selectedText = content.subSequence(start, end).toString()
    return selectedText.takeIf { it.isNotBlank() }?.let { VoiceAiSelection(it, start, end) }
}

private fun VoiceAiState.replaceSelection(selection: VoiceAiSelection, enhanced: String) {
    val editable = input.text ?: return
    if (selection.start > editable.length || selection.end > editable.length) return
    editable.replace(selection.start, selection.end, enhanced)
    input.setSelection((selection.start + enhanced.length).coerceAtMost(editable.length))
}

private class VoiceAiContentClient(
    private val client: OkHttpClient,
    moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build(),
) {
    private val requestAdapter = moshi.adapter(ChatRequest::class.java)
    private val responseAdapter = moshi.adapter(ChatResponse::class.java)

    suspend fun enhance(
        text: String,
        apiKey: String,
        model: String,
        targetLanguageTag: String,
    ): String? =
        runCatching {
            val targetLanguage =
                Locale.forLanguageTag(targetLanguageTag).getDisplayLanguage(Locale.ENGLISH)
                    .takeIf { it.isNotBlank() } ?: targetLanguageTag
            val payload = requestAdapter.toJson(
                ChatRequest(
                    model = model,
                    messages = listOf(
                        Message("system", buildSystemPrompt(targetLanguage, targetLanguageTag)),
                        Message("user", text),
                    ),
                ),
            )
            suspendCancellableCoroutine { continuation ->
                val request = Request.Builder()
                    .url(OPEN_AI_CHAT_URL)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                    .build()
                val call = client.newCall(request)
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        response.use {
                            try {
                                if (!response.isSuccessful) throw IOException("OpenAI request failed with ${response.code}")
                                val result = responseAdapter.fromJson(response.body.string())
                                    ?.choices?.firstOrNull()?.message?.content?.trim()
                                if (continuation.isActive) continuation.resume(result)
                            } catch (e: Exception) {
                                if (continuation.isActive) continuation.resumeWithException(e)
                            }
                        }
                    }
                })
                continuation.invokeOnCancellation { call.cancel() }
            }
        }.getOrNull()

    @JsonClass(generateAdapter = true)
    data class ChatRequest(
        @param:Json(name = "model") val model: String,
        @param:Json(name = "messages") val messages: List<Message>,
        @param:Json(name = "temperature") val temperature: Double = 0.5,
    )

    @JsonClass(generateAdapter = true)
    data class Message(
        @param:Json(name = "role") val role: String,
        @param:Json(name = "content") val content: String,
    )

    @JsonClass(generateAdapter = true)
    data class ChatResponse(@param:Json(name = "choices") val choices: List<Choice> = emptyList())

    @JsonClass(generateAdapter = true)
    data class Choice(@param:Json(name = "message") val message: MessageContent)

    @JsonClass(generateAdapter = true)
    data class MessageContent(@param:Json(name = "content") val content: String?)
}

private const val DEFAULT_OPEN_AI_MODEL = "gpt-3.5-turbo"
private const val OPEN_AI_CHAT_URL = "https://api.openai.com/v1/chat/completions"
private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
private fun buildSystemPrompt(targetLanguage: String, targetLanguageTag: String) = """
    Improve and expand the user's text while preserving its meaning, facts, and tone.
    Write the entire response in $targetLanguage (language tag: $targetLanguageTag), translating the
    source text when necessary. Do not mix languages unless a proper name or quoted text requires it.
    Make it clearer and more useful. Format it with basic Markdown where appropriate: bold, italics,
    headings, ordered lists, unordered lists, block quotes, and links. Do not invent facts or URLs.
    Return only the improved content, without explanations, introductions, or Markdown code fences.
""".trimIndent()
