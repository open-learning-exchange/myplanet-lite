/*
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-11-24
 */

package org.ole.planet.myplanet.lite.dashboard

import com.squareup.moshi.FromJson
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonQualifier
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.ole.planet.myplanet.lite.profile.StoredCredentials
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class DashboardSurveysRepository(
    private val client: OkHttpClient,
    private val moshi: Moshi =
        Moshi
            .Builder()
            .add(FlexibleSurveyJsonAdapter())
            .addLast(KotlinJsonAdapterFactory())
            .build(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val findRequestAdapter = moshi.adapter(SurveysFindRequest::class.java)
    private val findResponseAdapter = moshi.adapter(SurveysFindResponse::class.java)
    private val completionsRequestAdapter = moshi.adapter(SurveyCompletionsRequest::class.java)
    private val completionsResponseAdapter = moshi.adapter(SurveyCompletionsResponse::class.java)
    private val surveyDocumentAdapter = moshi.adapter(SurveyDocument::class.java)
    private val publicSurveyResponseAdapter = moshi.adapter(PublicSurveyResponse::class.java)

    suspend fun fetchTeamSurveys(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        teamId: String,
    ): Result<List<SurveyDocument>> =
        withContext(dispatcher) {
            runCatching {
                val normalizedBase = baseUrl.trim().trimEnd('/')
                if (normalizedBase.isEmpty()) {
                    throw IOException("Missing server base URL")
                }
                if (teamId.isBlank()) {
                    throw IOException("Missing team id")
                }
                val selector =
                    SurveySelector(
                        type = "surveys",
                        teamId = teamId,
                        isArchived = mapOf($$"$exists" to false),
                    )
                val payload = findRequestAdapter.toJson(SurveysFindRequest(selector))
                val endpoint = "$normalizedBase/db/exams/_find"
                val requestBuilder =
                    Request
                        .Builder()
                        .url(endpoint)
                        .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                credentials?.let { creds ->
                    requestBuilder.addHeader("Authorization", Credentials.basic(creds.username, creds.password))
                }
                sessionCookie?.takeIf { it.isNotBlank() }?.let { cookie ->
                    requestBuilder.addHeader("Cookie", cookie)
                }
                client.newCall(requestBuilder.build()).await().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Unexpected response ${response.code}")
                    }
                    val body = response.body.string()
                    findResponseAdapter.fromJson(body)?.docs ?: emptyList()
                }
            }
        }

    suspend fun fetchSurveyById(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        surveyId: String,
    ): Result<SurveyDocument> =
        withContext(dispatcher) {
            runCatching {
                val normalizedBase = baseUrl.trim().trimEnd('/')
                if (normalizedBase.isEmpty()) {
                    throw IOException("Missing server base URL")
                }
                if (surveyId.isBlank()) {
                    throw IOException("Missing survey id")
                }
                val base =
                    normalizedBase.toHttpUrlOrNull()
                        ?: throw IOException("Invalid server base URL")
                val endpoint =
                    base
                        .newBuilder()
                        .addPathSegment("db")
                        .addPathSegment("exams")
                        .addPathSegment(surveyId)
                        .build()
                val requestBuilder = Request.Builder().url(endpoint)
                credentials?.let { creds ->
                    requestBuilder.addHeader("Authorization", Credentials.basic(creds.username, creds.password))
                }
                sessionCookie?.takeIf { it.isNotBlank() }?.let { cookie ->
                    requestBuilder.addHeader("Cookie", cookie)
                }
                client.newCall(requestBuilder.build()).await().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Unexpected response ${response.code}")
                    }
                    val body = response.body.string()
                    surveyDocumentAdapter.fromJson(body)
                        ?: throw IOException("Invalid survey response")
                }
            }
        }

    suspend fun fetchPublicSurvey(
        baseUrl: String,
        teamId: String,
        surveyId: String,
    ): Result<PublicSurveyResponse> =
        withContext(dispatcher) {
            runCatching {
                val normalizedBase = baseUrl.trim().trimEnd('/')
                if (normalizedBase.isEmpty()) {
                    throw IOException("Missing server base URL")
                }
                if (teamId.isBlank()) {
                    throw IOException("Missing team id")
                }
                if (surveyId.isBlank()) {
                    throw IOException("Missing survey id")
                }
                val base =
                    normalizedBase.toHttpUrlOrNull()
                        ?: throw IOException("Invalid server base URL")
                val endpoint =
                    base
                        .newBuilder()
                        .addPathSegment("api")
                        .addPathSegment("public")
                        .addPathSegment("surveys")
                        .addPathSegment(teamId)
                        .addPathSegment(surveyId)
                        .build()
                val request =
                    Request
                        .Builder()
                        .url(endpoint)
                        .get()
                        .build()
                client.newCall(request).await().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Unexpected response ${response.code}")
                    }
                    val body = response.body.string()
                    publicSurveyResponseAdapter.fromJson(body)
                        ?: throw IOException("Invalid public survey response")
                }
            }
        }

    @JsonClass(generateAdapter = true)
    data class SurveysFindRequest(
        @param:Json(name = "selector") val selector: SurveySelector,
    )

    @JsonClass(generateAdapter = true)
    data class SurveySelector(
        @param:Json(name = "type") val type: String,
        @param:Json(name = "teamId") val teamId: String,
        @param:Json(name = "isArchived") val isArchived: Map<String, Boolean>,
    )

    @JsonClass(generateAdapter = true)
    data class SurveysFindResponse(
        @param:Json(name = "docs") val docs: List<SurveyDocument> = emptyList(),
    )

    @JsonClass(generateAdapter = true)
    data class SurveyDocument(
        @param:Json(name = "_id") val id: String? = null,
        @param:Json(name = "_rev") val rev: String? = null,
        @param:Json(name = "name") val name: String? = null,
        @param:Json(name = "description") val description: String? = null,
        @param:Json(name = "passingPercentage") @param:FlexibleInt val passingPercentage: Int? = null,
        @param:Json(name = "sourceSurveyId") val sourceSurveyId: String? = null,
        @param:Json(name = "teamId") val teamId: String? = null,
        @param:Json(name = "createdDate") @param:FlexibleString val createdDate: String? = null,
        @param:Json(name = "questions") val questions: List<SurveyQuestion>? = null,
        @param:Json(name = "totalMarks") @param:FlexibleInt val totalMarks: Int? = null,
    ) : java.io.Serializable

    @JsonClass(generateAdapter = true)
    data class PublicSurveyResponse(
        @param:Json(name = "survey") val survey: SurveyDocument,
        @param:Json(name = "team") val team: PublicSurveyTeam? = null,
    )

    @JsonClass(generateAdapter = true)
    data class PublicSurveyTeam(
        @param:Json(name = "_id") val id: String? = null,
        @param:Json(name = "name") val name: String? = null,
        @param:Json(name = "type") val type: String? = null,
    )

    @JsonClass(generateAdapter = true)
    data class SurveyQuestion(
        @param:Json(name = "body") val body: String? = null,
        @param:Json(name = "type") val type: String? = null,
        @param:Json(name = "correctChoice") val correctChoice: Any? = null,
        @param:Json(name = "marks") @param:FlexibleInt val marks: Int? = null,
        @param:Json(name = "choices") val choices: List<SurveyChoice>? = null,
        @param:Json(name = "hasOtherOption") val hasOtherOption: Boolean = false,
        @param:Json(name = "scaleMax") @param:FlexibleInt val scaleMax: Int? = null,
    ) : java.io.Serializable

    @JsonClass(generateAdapter = true)
    data class SurveyChoice(
        @param:Json(name = "text") val text: String? = null,
        @param:Json(name = "id") val id: String? = null,
    ) : java.io.Serializable

    suspend fun getCompletionCountsWithDefaults(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        teamId: String,
        surveyIds: List<String>,
    ): Map<String, Int> {
        val defaults = surveyIds.associateWith { 0 }
        if (surveyIds.isEmpty()) return defaults
        val result = fetchSurveyCompletionCountsBatched(baseUrl, credentials, sessionCookie, teamId, surveyIds)
        return defaults + result.getOrDefault(emptyMap())
    }

    suspend fun fetchSurveyCompletionCountsBatched(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        teamId: String,
        surveyIds: List<String>,
    ): Result<Map<String, Int>> {
        return withContext(dispatcher) {
            runCatching {
                val normalizedBase = baseUrl.trim().trimEnd('/')
                if (normalizedBase.isEmpty()) {
                    throw IOException("Missing server base URL")
                }
                if (teamId.isBlank() || surveyIds.isEmpty()) {
                    return@runCatching emptyMap<String, Int>()
                }

                val counts = mutableMapOf<String, Int>()
                val parentMatches = surveyIds.flatMap { surveyId ->
                    listOf(
                        mapOf("parentId" to surveyId),
                        mapOf("parentId" to mapOf("\$regex" to "^$surveyId@"))
                    )
                }

                val selector =
                    SurveyCompletionsSelector(
                        type = "survey",
                        status = "complete",
                        teamId = teamId,
                        parentMatches = parentMatches,
                    )
                val payload =
                    completionsRequestAdapter.toJson(
                        SurveyCompletionsRequest(
                            selector = selector,
                            limit = 50000,
                        ),
                    )
                val endpoint = "$normalizedBase/db/submissions/_find"
                val requestBuilder =
                    Request
                        .Builder()
                        .url(endpoint)
                        .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                credentials?.let { creds ->
                    requestBuilder.addHeader("Authorization", Credentials.basic(creds.username, creds.password))
                }
                sessionCookie?.takeIf { it.isNotBlank() }?.let { cookie ->
                    requestBuilder.addHeader("Cookie", cookie)
                }

                client.newCall(requestBuilder.build()).await().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Unexpected response ${response.code}")
                    }
                    val body = response.body.string()
                    val docs = completionsResponseAdapter.fromJson(body)?.docs ?: emptyList()
                    docs.forEach { doc ->
                        val parentId = doc["parentId"] as? String ?: return@forEach
                        val baseId = parentId.substringBefore("@")
                        counts[baseId] = counts.getOrDefault(baseId, 0) + 1
                    }
                }
                counts
            }
        }
    }

    @JsonClass(generateAdapter = true)
    data class SurveyCompletionsRequest(
        @param:Json(name = "selector") val selector: SurveyCompletionsSelector,
        @param:Json(name = "limit") val limit: Int = 5000,
        @param:Json(name = "fields") val fields: List<String> = listOf("_id", "parentId"),
    )

    @JsonClass(generateAdapter = true)
    data class SurveyCompletionsSelector(
        @param:Json(name = "type") val type: String,
        @param:Json(name = "status") val status: String,
        @param:Json(name = "team._id") val teamId: String,
        @param:Json(name = $$"$or") val parentMatches: List<Map<String, Any>>,
    )

    @JsonClass(generateAdapter = true)
    data class SurveyCompletionsResponse(
        @param:Json(name = "docs") val docs: List<Map<String, Any?>> = emptyList(),
    )

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

suspend fun Call.await(): Response {
    return suspendCancellableCoroutine { continuation ->
        enqueue(
            object : Callback {
                override fun onResponse(
                    call: Call,
                    response: Response,
                ) {
                    continuation.resume(response)
                }

                override fun onFailure(
                    call: Call,
                    e: IOException,
                ) {
                    if (continuation.isCancelled) return
                    continuation.resumeWithException(e)
                }
            },
        )

        continuation.invokeOnCancellation {
            try {
                cancel()
            } catch (ex: Exception) {
                // Ignore cancel exception
            }
        }
    }
}

@Retention(AnnotationRetention.RUNTIME)
@JsonQualifier
internal annotation class FlexibleInt

@Retention(AnnotationRetention.RUNTIME)
@JsonQualifier
internal annotation class FlexibleString

internal class FlexibleSurveyJsonAdapter {
    @FromJson
    @FlexibleInt
    fun fromJsonFlexibleInt(reader: JsonReader): Int? =
        when (reader.peek()) {
            JsonReader.Token.NULL -> {
                reader.nextNull()
            }

            JsonReader.Token.NUMBER -> {
                reader.nextInt()
            }

            JsonReader.Token.STRING -> {
                reader.nextString().trim().toIntOrNull()
            }

            else -> {
                reader.skipValue()
                null
            }
        }

    @ToJson
    fun toJsonFlexibleInt(
        writer: JsonWriter,
        @FlexibleInt value: Int?,
    ) {
        if (value == null) {
            writer.nullValue()
        } else {
            writer.value(value)
        }
    }

    @FromJson
    @FlexibleString
    fun fromJsonFlexibleString(reader: JsonReader): String? =
        when (reader.peek()) {
            JsonReader.Token.NULL -> {
                reader.nextNull()
            }

            JsonReader.Token.STRING -> {
                reader.nextString()
            }

            JsonReader.Token.NUMBER -> {
                reader.nextLong().toString()
            }

            JsonReader.Token.BOOLEAN -> {
                reader.nextBoolean().toString()
            }

            else -> {
                reader.skipValue()
                null
            }
        }

    @ToJson
    fun toJsonFlexibleString(
        writer: JsonWriter,
        @FlexibleString value: String?,
    ) {
        if (value == null) {
            writer.nullValue()
        } else {
            writer.value(value)
        }
    }
}
