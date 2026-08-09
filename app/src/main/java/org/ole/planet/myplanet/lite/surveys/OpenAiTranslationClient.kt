/*
Author: Walfre López Prado
Email: loppra@plataformasinformaticas.com
Creation date: 2026-08-09
 */

package org.ole.planet.myplanet.lite.surveys

import android.content.Context
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyDocument
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyQuestion
import org.ole.planet.myplanet.lite.dashboard.ServerConfigurationRepository
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class OpenAiTranslationClient(
    private val client: OkHttpClient = OkHttpClient(),
    moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build(),
) {
    private val requestAdapter = moshi.adapter(ChatCompletionRequest::class.java)
    private val responseAdapter = moshi.adapter(ChatCompletionResponse::class.java)

    suspend fun translate(
        text: String,
        targetLanguage: String,
        apiKey: String,
        model: String,
        sourceLanguage: String? = null,
    ): String? {
        if (text.isBlank()) return null
        val sourceHint = sourceLanguage?.takeIf { it.isNotBlank() }
        val payload =
            requestAdapter.toJson(
                ChatCompletionRequest(
                    model = model,
                    messages =
                        listOf(
                            Message(
                                role = "system",
                                content = buildSystemPrompt(targetLanguage, sourceHint),
                            ),
                            Message(role = "user", content = text),
                        ),
                    temperature = 0.2,
                ),
            )
        return runCatching {
            suspendCancellableCoroutine { continuation ->
                val request =
                    Request
                        .Builder()
                        .url(OPEN_AI_CHAT_URL)
                        .addHeader("Authorization", "Bearer $apiKey")
                        .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                        .build()

                val call = client.newCall(request)

                call.enqueue(
                    object : Callback {
                        override fun onFailure(
                            call: Call,
                            e: IOException,
                        ) {
                            if (continuation.isActive) {
                                continuation.resumeWithException(e)
                            }
                        }

                        override fun onResponse(
                            call: Call,
                            response: Response,
                        ) {
                            try {
                                if (!response.isSuccessful) {
                                    throw IOException("Translation request failed with ${response.code}")
                                }
                                val body = response.body.string()
                                val result =
                                    responseAdapter
                                        .fromJson(body)
                                        ?.choices
                                        ?.firstOrNull()
                                        ?.message
                                        ?.content
                                        ?.trim()
                                if (continuation.isActive) {
                                    continuation.resume(result)
                                }
                            } catch (e: Exception) {
                                if (continuation.isActive) {
                                    continuation.resumeWithException(e)
                                }
                            } finally {
                                response.close()
                            }
                        }
                    },
                )

                continuation.invokeOnCancellation {
                    call.cancel()
                }
            }
        }.getOrNull()
    }

    @JsonClass(generateAdapter = true)
    data class ChatCompletionRequest(
        @param:Json(name = "model") val model: String,
        @param:Json(name = "messages") val messages: List<Message>,
        @param:Json(name = "temperature") val temperature: Double = 0.2,
    )

    private fun buildSystemPrompt(
        targetLanguage: String,
        sourceLanguage: String?,
    ): String {
        val direction = sourceLanguage?.let { "from $it to $targetLanguage" } ?: "to $targetLanguage"
        return """
            Translate the following survey text $direction.
            Preserve the original meaning, especially for yes/no style answers and negations.
            Respond with only the translated text and nothing else. If the text is already in $targetLanguage, return it unchanged.
            """.trimIndent()
    }

    @JsonClass(generateAdapter = true)
    data class Message(
        @param:Json(name = "role") val role: String,
        @param:Json(name = "content") val content: String,
    )

    @JsonClass(generateAdapter = true)
    data class ChatCompletionResponse(
        @param:Json(name = "choices") val choices: List<Choice> = emptyList(),
    )

    @JsonClass(generateAdapter = true)
    data class Choice(
        @param:Json(name = "message") val message: MessageContent,
    )

    @JsonClass(generateAdapter = true)
    data class MessageContent(
        @param:Json(name = "content") val content: String?,
    )

    private companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val OPEN_AI_CHAT_URL = "https://api.openai.com/v1/chat/completions"
    }
}

