/**
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-11-24
 */

package org.ole.planet.myplanet.lite.dashboard

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyQuestion
import org.ole.planet.myplanet.lite.profile.StoredCredentials

class DashboardSurveySubmissionsRepository {

    private val client: OkHttpClient = OkHttpClient.Builder().build()
    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val submissionAdapter = moshi.adapter(SurveySubmission::class.java)
    private val lookupRequestAdapter = moshi.adapter(SubmissionLookupRequest::class.java)
    private val lookupResponseAdapter = moshi.adapter(SubmissionLookupResponse::class.java)

    suspend fun submitSurvey(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        submission: SurveySubmission,
    ): Result<Unit> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val normalizedBase = baseUrl.trim().trimEnd('/')
                if (normalizedBase.isEmpty()) {
                    throw IOException("Missing server base URL")
                }
                val endpoint = "$normalizedBase/db/submissions"
                val payload = submissionAdapter.toJson(submission)
                val requestBuilder = Request.Builder()
                    .url(endpoint)
                    .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                credentials?.let { creds ->
                    requestBuilder.addHeader("Authorization", Credentials.basic(creds.username, creds.password))
                }
                sessionCookie?.takeIf { it.isNotBlank() }?.let { cookie ->
                    requestBuilder.addHeader("Cookie", cookie)
                }
                client.newCall(requestBuilder.build()).execute().use { response ->
                    val responseBody = response.body.string()
                    if (!response.isSuccessful) {
                        throw IOException("Unexpected response ${response.code}. body=$responseBody")
                    }
                }
            }
        }
    }

    suspend fun fetchExistingSubmission(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        parentId: String,
        userId: String?,
        userName: String?,
        parentRev: String?,
        type: String = "survey",
    ): Result<SubmissionLookup?> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val normalizedBase = baseUrl.trim().trimEnd('/')
                if (normalizedBase.isEmpty()) {
                    throw IOException("Missing server base URL")
                }
                if (userId.isNullOrBlank() && userName.isNullOrBlank()) {
                    throw IOException("Missing user identifier")
                }
                val payload = lookupRequestAdapter.toJson(
                    SubmissionLookupRequest(
                        selector = SubmissionLookupSelector(
                            type = type,
                            parentId = parentId,
                            userId = userId,
                            userName = userName,
                            parentRev = parentRev,
                        ),
                    ),
                )
                val endpoint = "$normalizedBase/db/submissions/_find"
                val requestBuilder = Request.Builder()
                    .url(endpoint)
                    .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                credentials?.let { creds ->
                    requestBuilder.addHeader("Authorization", Credentials.basic(creds.username, creds.password))
                }
                sessionCookie?.takeIf { it.isNotBlank() }?.let { cookie ->
                    requestBuilder.addHeader("Cookie", cookie)
                }
                client.newCall(requestBuilder.build()).execute().use { response ->
                    val body = response.body.string()
                    if (!response.isSuccessful) {
                        throw IOException("Unexpected response ${response.code}. body=$body")
                    }
                    if (body.isEmpty()) {
                        null
                    } else {
                        lookupResponseAdapter.fromJson(body)?.docs?.firstOrNull()
                    }
                }
            }
        }
    }

    @JsonClass(generateAdapter = true)
    data class SurveySubmission(
        @param:Json(name = "_id") val id: String? = null,
        @param:Json(name = "_rev") val rev: String? = null,
        @param:Json(name = "type") val type: String = "survey",
        @param:Json(name = "parentId") val parentId: String?,
        @param:Json(name = "parent") val parent: SubmissionParent,
        @param:Json(name = "user") val user: SubmissionUser,
        @param:Json(name = "team") val team: SubmissionTeam?,
        @param:Json(name = "answers") val answers: List<SubmissionAnswer>,
        @param:Json(name = "grade") val grade: Int = 0,
        @param:Json(name = "status") val status: String,
        @param:Json(name = "startTime") val startTime: Long,
        @param:Json(name = "lastUpdateTime") val lastUpdateTime: Long,
        @param:Json(name = "source") val source: String?,
        @param:Json(name = "parentCode") val parentCode: String?,
        @param:Json(name = "deviceName") val deviceName: String? = null,
        @param:Json(name = "customDeviceName") val customDeviceName: String? = null,
    )

    @JsonClass(generateAdapter = true)
    data class SubmissionParent(
        @param:Json(name = "_id") val id: String?,
        @param:Json(name = "_rev") val rev: String?,
        @param:Json(name = "name") val name: String?,
        @param:Json(name = "type") val type: String? = null,
        @param:Json(name = "passingPercentage") val passingPercentage: Int? = null,
        @param:Json(name = "teamShareAllowed") val teamShareAllowed: Boolean? = null,
        @param:Json(name = "questions") val questions: List<SurveyQuestion>?,
        @param:Json(name = "description") val description: String?,
    )

    @JsonClass(generateAdapter = true)
    data class SubmissionUser(
        @param:Json(name = "_id") val id: String?,
        @param:Json(name = "name") val name: String?,
        @param:Json(name = "planetCode") val planetCode: String?,
        @param:Json(name = "parentCode") val parentCode: String?,
        @param:Json(name = "firstName") val firstName: String? = null,
        @param:Json(name = "middleName") val middleName: String? = null,
        @param:Json(name = "lastName") val lastName: String? = null,
        @param:Json(name = "email") val email: String? = null,
        @param:Json(name = "language") val language: String? = null,
        @param:Json(name = "phoneNumber") val phoneNumber: String? = null,
        @param:Json(name = "birthDate") val birthDate: String? = null,
        @param:Json(name = "age") val age: Int? = null,
        @param:Json(name = "gender") val gender: String? = null,
        @param:Json(name = "level") val level: String? = null,
        @param:Json(name = "betaEnabled") val betaEnabled: Boolean = false,
    )

    @JsonClass(generateAdapter = true)
    data class SubmissionTeam(
        @param:Json(name = "_id") val id: String?,
        @param:Json(name = "name") val name: String?,
        @param:Json(name = "type") val type: String?,
    )

    @JsonClass(generateAdapter = true)
    data class SubmissionAnswer(
        @param:Json(name = "value") val value: Any?,
        @param:Json(name = "mistakes") val mistakes: Int = 0,
        @param:Json(name = "passed") val passed: Boolean = true,
        @param:Json(name = "grade") val grade: Int = 0,
    )

    @JsonClass(generateAdapter = true)
    data class SubmissionLookupRequest(
        @param:Json(name = "selector") val selector: SubmissionLookupSelector,
        @param:Json(name = "limit") val limit: Int = 1,
    )

    @JsonClass(generateAdapter = true)
    data class SubmissionLookupSelector(
        @param:Json(name = "type") val type: String = "survey",
        @param:Json(name = "parentId") val parentId: String,
        @param:Json(name = "user._id") val userId: String? = null,
        @param:Json(name = "user.name") val userName: String? = null,
        @param:Json(name = "parent._rev") val parentRev: String? = null,
    )

    @JsonClass(generateAdapter = true)
    data class SubmissionLookupResponse(
        @param:Json(name = "docs") val docs: List<SubmissionLookup> = emptyList(),
    )

    @JsonClass(generateAdapter = true)
    data class SubmissionLookup(
        @param:Json(name = "_id") val id: String? = null,
        @param:Json(name = "_rev") val rev: String? = null,
    )

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
