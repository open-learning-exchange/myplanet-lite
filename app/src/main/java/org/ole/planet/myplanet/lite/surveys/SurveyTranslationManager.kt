/**
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-11-25
 */

package org.ole.planet.myplanet.lite.surveys

import android.content.Context
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
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

class SurveyTranslationManager(
    private val appContext: Context,
    private val configurationRepository: ServerConfigurationRepository = ServerConfigurationRepository(),
    private val languageIdentifier: com.google.mlkit.nl.languageid.LanguageIdentifier =
        LanguageIdentification.getClient(),
    private val translationClient: OpenAiTranslationClient = OpenAiTranslationClient(),
    private val translationCache: SurveyTranslationCache = SurveyTranslationCache.getInstance(appContext),
) {

    suspend fun translateSurvey(
        baseUrl: String?,
        survey: SurveyDocument,
        targetLanguage: String,
        detectedLanguage: String? = null,
    ): SurveyTranslationResult {
        if (targetLanguage.isBlank()) {
            return SurveyTranslationResult(null, null, null, emptyMap())
        }
        val normalizedTargetLanguage = normalizeLanguageCode(targetLanguage)
        val cacheSurveyId = survey.id ?: survey.sourceSurveyId
        val cachedTranslations = cacheSurveyId?.let {
            translationCache.getTranslations(it, normalizedTargetLanguage)
        }.orEmpty()
        val cachedTitle = cachedTranslations[TITLE_INDEX]?.body
        val cachedDescription = cachedTranslations[DESCRIPTION_INDEX]?.body
        val cachedQuestionTranslations = cachedTranslations.filterKeys { it >= 0 }

        val configuration = configurationRepository.fetchConfiguration(baseUrl).getOrNull()
            ?: return SurveyTranslationResult(null, cachedTitle, cachedDescription, cachedQuestionTranslations)
        val openAiKey = configuration.keys?.openAi?.takeIf { it.isNotBlank() }
            ?: return SurveyTranslationResult(null, cachedTitle, cachedDescription, cachedQuestionTranslations)
        val openAiModel = configuration.models?.openAi?.takeIf { it.isNotBlank() } ?: DEFAULT_MODEL

        val sourceLanguage = detectedLanguage ?: runCatching {
            detectLanguage(buildLanguageSample(survey))
        }.getOrNull()
        val normalizedSourceLanguage = sourceLanguage?.let(::normalizeLanguageCode)
        if (sourceLanguage == null) {
            return if (cachedQuestionTranslations.isNotEmpty() || cachedTitle != null || cachedDescription != null) {
                SurveyTranslationResult(null, cachedTitle, cachedDescription, cachedQuestionTranslations)
            } else {
                SurveyTranslationResult(null, null, null, emptyMap())
            }
        }
        if (normalizedSourceLanguage == normalizedTargetLanguage) {
            return SurveyTranslationResult(sourceLanguage, null, null, emptyMap())
        }

        val (translatedTitle, translatedDescription) = translateSurveyMetadata(
            survey = survey,
            cachedTitle = cachedTitle,
            cachedDescription = cachedDescription,
            cacheSurveyId = cacheSurveyId,
            normalizedTargetLanguage = normalizedTargetLanguage,
            sourceLanguage = normalizedSourceLanguage ?: sourceLanguage,
            openAiKey = openAiKey,
            openAiModel = openAiModel,
        )

        val translations = translateSurveyQuestionList(
            questions = survey.questions.orEmpty(),
            cachedQuestionTranslations = cachedQuestionTranslations,
            cacheSurveyId = cacheSurveyId,
            normalizedTargetLanguage = normalizedTargetLanguage,
            sourceLanguage = normalizedSourceLanguage ?: sourceLanguage,
            openAiKey = openAiKey,
            openAiModel = openAiModel,
        )

        return SurveyTranslationResult(sourceLanguage, translatedTitle, translatedDescription, translations)
    }

    private suspend fun translateSurveyMetadata(
        survey: SurveyDocument,
        cachedTitle: String?,
        cachedDescription: String?,
        cacheSurveyId: String?,
        normalizedTargetLanguage: String,
        sourceLanguage: String,
        openAiKey: String,
        openAiModel: String,
    ): Pair<String?, String?> {
        val translatedTitle = survey.name?.let { title ->
            cachedTitle ?: translationClient.translate(
                text = title,
                targetLanguage = normalizedTargetLanguage,
                apiKey = openAiKey,
                model = openAiModel,
                sourceLanguage = sourceLanguage,
            )
        }
        val translatedDescription = survey.description?.let { description ->
            cachedDescription ?: translationClient.translate(
                text = description,
                targetLanguage = normalizedTargetLanguage,
                apiKey = openAiKey,
                model = openAiModel,
                sourceLanguage = sourceLanguage,
            )
        }
        if (cacheSurveyId != null) {
            if (!translatedTitle.isNullOrBlank()) {
                translationCache.saveTranslation(
                    cacheSurveyId,
                    TITLE_INDEX,
                    normalizedTargetLanguage,
                    TranslatedQuestion(body = translatedTitle),
                )
            }
            if (!translatedDescription.isNullOrBlank()) {
                translationCache.saveTranslation(
                    cacheSurveyId,
                    DESCRIPTION_INDEX,
                    normalizedTargetLanguage,
                    TranslatedQuestion(body = translatedDescription),
                )
            }
        }
        return Pair(translatedTitle, translatedDescription)
    }

    private suspend fun translateSurveyQuestionList(
        questions: List<SurveyQuestion>,
        cachedQuestionTranslations: Map<Int, TranslatedQuestion>,
        cacheSurveyId: String?,
        normalizedTargetLanguage: String,
        sourceLanguage: String,
        openAiKey: String,
        openAiModel: String,
    ): Map<Int, TranslatedQuestion> = coroutineScope {
        val semaphore = Semaphore(5)
        val translations = questions.mapIndexed { index, question ->
            async {
                val cached = cachedQuestionTranslations[index]
                val translatedBody: String?
                val translatedChoices: List<String?>

                if (cached != null && cached.hasTranslation) {
                    translatedBody = cached.body
                    translatedChoices = cached.choices
                } else {
                    translatedBody = semaphore.withPermit {
                        translationClient.translate(
                            text = question.body.orEmpty(),
                            targetLanguage = normalizedTargetLanguage,
                            apiKey = openAiKey,
                            model = openAiModel,
                            sourceLanguage = sourceLanguage,
                        )
                    }
                    translatedChoices = question.choices.orEmpty().map { choice ->
                        async {
                            choice.text?.let { text ->
                                semaphore.withPermit {
                                    translationClient.translate(
                                        text = text,
                                        targetLanguage = normalizedTargetLanguage,
                                        apiKey = openAiKey,
                                        model = openAiModel,
                                        sourceLanguage = sourceLanguage,
                                    )
                                }
                            }
                        }
                    }.awaitAll()

                    if (cacheSurveyId != null && (!translatedBody.isNullOrBlank() || translatedChoices.any { !it.isNullOrBlank() })) {
                        translationCache.saveTranslation(
                            cacheSurveyId,
                            index,
                            normalizedTargetLanguage,
                            TranslatedQuestion(translatedBody, translatedChoices),
                        )
                    }
                }

                if (!translatedBody.isNullOrBlank() || translatedChoices.any { !it.isNullOrBlank() }) {
                    index to TranslatedQuestion(
                        body = translatedBody,
                        choices = translatedChoices,
                    )
                } else {
                    null
                }
            }
        }.awaitAll()

        translations.filterNotNull().toMap()
    }

    suspend fun translateQuestion(
        baseUrl: String?,
        survey: SurveyDocument,
        questionIndex: Int,
        targetLanguage: String,
        detectedLanguage: String? = null,
        forceRetranslate: Boolean = false,
    ): TranslatedQuestion? {
        if (targetLanguage.isBlank()) return null
        val normalizedTargetLanguage = normalizeLanguageCode(targetLanguage)
        val cacheSurveyId = survey.id ?: survey.sourceSurveyId
        if (!forceRetranslate) {
            val cached = cacheSurveyId?.let {
                translationCache.getTranslations(it, normalizedTargetLanguage)[questionIndex]
            }
            if (cached?.hasTranslation == true) {
                return cached
            }
        }

        val configuration = configurationRepository.fetchConfiguration(baseUrl).getOrNull()
            ?: return null
        val openAiKey = configuration.keys?.openAi?.takeIf { it.isNotBlank() } ?: return null
        val openAiModel = configuration.models?.openAi?.takeIf { it.isNotBlank() } ?: DEFAULT_MODEL
        val sourceLanguage = detectedLanguage ?: runCatching {
            detectLanguage(buildLanguageSample(survey))
        }.getOrNull()
        val normalizedSourceLanguage = sourceLanguage?.let(::normalizeLanguageCode)
        if (sourceLanguage == null || normalizedSourceLanguage == normalizedTargetLanguage) {
            return null
        }

        val question = survey.questions?.getOrNull(questionIndex) ?: return null
        val translatedBody = translationClient.translate(
            text = question.body.orEmpty(),
            targetLanguage = normalizedTargetLanguage,
            apiKey = openAiKey,
            model = openAiModel,
            sourceLanguage = normalizedSourceLanguage ?: sourceLanguage,
        )
        val translatedChoices = question.choices.orEmpty().map { choice ->
            choice.text?.let { text ->
                translationClient.translate(
                    text = text,
                    targetLanguage = normalizedTargetLanguage,
                    apiKey = openAiKey,
                    model = openAiModel,
                    sourceLanguage = normalizedSourceLanguage ?: sourceLanguage,
                )
            }
        }
        val translation = TranslatedQuestion(translatedBody, translatedChoices)
        if (cacheSurveyId != null && translation.hasTranslation) {
            translationCache.saveTranslation(
                cacheSurveyId,
                questionIndex,
                normalizedTargetLanguage,
                translation,
            )
        }
        return translation
    }

    suspend fun detectSurveyLanguage(survey: SurveyDocument): String? {
        val sample = buildLanguageSample(survey)
        return runCatching { detectLanguage(sample) }.getOrNull()
    }

    private suspend fun detectLanguage(text: String): String? {
        if (text.isBlank()) return null
        return suspendCancellableCoroutine { continuation ->
            val task = languageIdentifier.identifyLanguage(text)
            task.addOnSuccessListener { languageCode ->
                val code = languageCode.takeIf { it.isNotBlank() && it.lowercase() != "und" }
                if (continuation.isActive) {
                    continuation.resume(code)
                }
            }
            task.addOnFailureListener { error ->
                if (continuation.isActive) {
                    continuation.resumeWithException(error)
                }
            }
            continuation.invokeOnCancellation {
                languageIdentifier.close()
            }
        }
    }

    private fun buildLanguageSample(survey: SurveyDocument): String {
        val questionBodies = survey.questions
            .orEmpty()
            .asSequence()
            .mapNotNull { it.body?.takeIf(String::isNotBlank) }
            .take(LANGUAGE_SAMPLE_QUESTION_LIMIT)
            .toList()
        val choiceTexts = survey.questions
            .orEmpty()
            .asSequence()
            .flatMap { it.choices.orEmpty().asSequence() }
            .mapNotNull { it.text?.takeIf(String::isNotBlank) }
            .take(LANGUAGE_SAMPLE_CHOICE_LIMIT)
            .toList()
        return buildList {
            survey.name?.takeIf(String::isNotBlank)?.let(::add)
            survey.description?.takeIf(String::isNotBlank)?.let(::add)
            addAll(questionBodies)
            addAll(choiceTexts)
        }.joinToString(". ").take(MAX_LANGUAGE_SAMPLE_LENGTH)
    }

    private fun normalizeLanguageCode(value: String): String {
        return value.trim()
            .replace('_', '-')
            .substringBefore('-')
            .lowercase(Locale.ROOT)
    }

    data class SurveyTranslationResult(
        val sourceLanguage: String?,
        val titleTranslation: String?,
        val descriptionTranslation: String?,
        val translations: Map<Int, TranslatedQuestion>,
    )

    data class TranslatedQuestion(
        val body: String?,
        val choices: List<String?> = emptyList(),
    ) {
        val hasTranslation: Boolean
            get() = !body.isNullOrBlank() || choices.any { !it.isNullOrBlank() }
    }

    private companion object {
        private const val DEFAULT_MODEL = "gpt-3.5-turbo"
        private const val MAX_LANGUAGE_SAMPLE_LENGTH = 300
        private const val LANGUAGE_SAMPLE_QUESTION_LIMIT = 3
        private const val LANGUAGE_SAMPLE_CHOICE_LIMIT = 6
        private const val TITLE_INDEX = -1
        private const val DESCRIPTION_INDEX = -2
    }
}

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
        val payload = requestAdapter.toJson(
            ChatCompletionRequest(
                model = model,
                messages = listOf(
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
                val request = Request.Builder()
                    .url(OPEN_AI_CHAT_URL)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                val call = client.newCall(request)

                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(e)
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        try {
                            if (!response.isSuccessful) {
                                throw IOException("Translation request failed with ${response.code}")
                            }
                            val body = response.body?.string() ?: ""
                            val result = responseAdapter.fromJson(body)?.choices?.firstOrNull()?.message?.content?.trim()
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
                })

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

    private fun buildSystemPrompt(targetLanguage: String, sourceLanguage: String?): String {
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
