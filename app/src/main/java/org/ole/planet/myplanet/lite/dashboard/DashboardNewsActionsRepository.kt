/**
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-11-19
 */

package org.ole.planet.myplanet.lite.dashboard

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class DashboardNewsActionsRepository(
    private val client: OkHttpClient,
    private val moshi: Moshi,
    private val dispatcher: CoroutineDispatcher
) {

    private val deleteRequestAdapter = moshi.adapter(DeleteNewsRequest::class.java)
    private val updateRequestAdapter = moshi.adapter(UpdateNewsRequest::class.java)
    private val responseAdapter = moshi.adapter(DeleteNewsResponse::class.java)

    suspend fun deleteNews(
        baseUrl: String,
        sessionCookie: String?,
        document: DashboardNewsRepository.NewsDocument,
        teamId: String? = null,
        teamName: String? = null
    ): Result<DeleteNewsResponse> {
        return withContext(dispatcher) {
            runCatching {
                val normalizedBase = baseUrl.trim().trimEnd('/')
                if (normalizedBase.isEmpty()) {
                    throw IOException("Missing server base URL")
                }
                val id = document.id?.takeIf { it.isNotBlank() }
                    ?: throw IOException("Missing document id")
                val revision = document.revision?.takeIf { it.isNotBlank() }
                    ?: throw IOException("Missing document revision")
                val payload = DeleteNewsRequest(
                    id = id,
                    revision = revision,
                    docType = document.docType,
                    time = document.time,
                    createdOn = document.createdOn,
                    parentCode = document.parentCode,
                    user = document.user,
                    viewIn = resolveViewInEntries(document, teamId, teamName),
                    messageType = document.messageType,
                    messagePlanetCode = document.messagePlanetCode,
                    message = document.message,
                    images = document.images,
                    updatedDate = System.currentTimeMillis(),
                    deleted = true
                )
                val requestBody = deleteRequestAdapter.toJson(payload)
                    .toRequestBody(JSON_MEDIA_TYPE)
                val requestBuilder = Request.Builder()
                    .url("$normalizedBase/db/news/")
                    .post(requestBody)
                sessionCookie?.takeIf { it.isNotBlank() }?.let { cookie ->
                    requestBuilder.addHeader("Cookie", cookie)
                }
                client.newCall(requestBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Unexpected response ${response.code}")
                    }
                    val body = response.body.string()
                    responseAdapter.fromJson(body) ?: throw IOException("Invalid response body")
                }
            }
        }
    }

    suspend fun updateNews(
        baseUrl: String,
        sessionCookie: String?,
        document: DashboardNewsRepository.NewsDocument,
        message: String,
        images: List<DashboardNewsRepository.NewsImage>,
        teamId: String? = null,
        teamName: String? = null
    ): Result<DeleteNewsResponse> {
        return withContext(dispatcher) {
            runCatching {
                val normalizedBase = baseUrl.trim().trimEnd('/')
                if (normalizedBase.isEmpty()) {
                    throw IOException("Missing server base URL")
                }
                val id = document.id?.takeIf { it.isNotBlank() }
                    ?: throw IOException("Missing document id")
                val revision = document.revision?.takeIf { it.isNotBlank() }
                    ?: throw IOException("Missing document revision")
                val payload = UpdateNewsRequest(
                    id = id,
                    revision = revision,
                    docType = document.docType,
                    time = document.time,
                    createdOn = document.createdOn,
                    parentCode = document.parentCode,
                    replyTo = document.replyTo,
                    user = document.user,
                    viewIn = resolveViewInEntries(document, teamId, teamName),
                    messageType = document.messageType,
                    messagePlanetCode = document.messagePlanetCode,
                    message = message,
                    images = images.takeUnless { it.isEmpty() },
                    updatedDate = System.currentTimeMillis()
                )
                val requestBody = updateRequestAdapter.toJson(payload)
                    .toRequestBody(JSON_MEDIA_TYPE)
                val requestBuilder = Request.Builder()
                    .url("$normalizedBase/db/news/")
                    .post(requestBody)
                sessionCookie?.takeIf { it.isNotBlank() }?.let { cookie ->
                    requestBuilder.addHeader("Cookie", cookie)
                }
                client.newCall(requestBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Unexpected response ${response.code}")
                    }
                    val body = response.body.string()
                    responseAdapter.fromJson(body) ?: throw IOException("Invalid response body")
                }
            }
        }
    }

    @JsonClass(generateAdapter = true)
    data class DeleteNewsRequest(
        @param:Json(name = "_id") val id: String,
        @param:Json(name = "_rev") val revision: String,
        val docType: String?,
        val time: Long?,
        val createdOn: String?,
        val parentCode: String?,
        val user: DashboardNewsRepository.NewsUser?,
        val viewIn: List<DashboardNewsRepository.ViewInEntry>?,
        val messageType: String?,
        val messagePlanetCode: String?,
        val message: String?,
        val images: List<DashboardNewsRepository.NewsImage>?,
        val updatedDate: Long?,
        @param:Json(name = "_deleted") val deleted: Boolean
    )

    @JsonClass(generateAdapter = true)
    data class UpdateNewsRequest(
        @param:Json(name = "_id") val id: String,
        @param:Json(name = "_rev") val revision: String,
        val docType: String?,
        val time: Long?,
        val createdOn: String?,
        val parentCode: String?,
        val replyTo: String?,
        val user: DashboardNewsRepository.NewsUser?,
        val viewIn: List<DashboardNewsRepository.ViewInEntry>?,
        val messageType: String?,
        val messagePlanetCode: String?,
        val message: String?,
        val images: List<DashboardNewsRepository.NewsImage>?,
        val updatedDate: Long?
    )

    @JsonClass(generateAdapter = true)
    data class DeleteNewsResponse(
        val ok: Boolean?,
        @param:Json(name = "id") val id: String?,
        @param:Json(name = "rev") val revision: String?
    )

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        @androidx.annotation.VisibleForTesting
        internal fun resolveViewInEntries(
            document: DashboardNewsRepository.NewsDocument,
            teamId: String?,
            teamName: String?
        ): List<DashboardNewsRepository.ViewInEntry> {
            val existing = document.viewIn?.takeUnless { it.isEmpty() }?.map { entry ->
                if (entry.section == "teams") {
                    entry.copy(
                        isPublic = entry.isPublic ?: false,
                        name = entry.name ?: teamName,
                        mode = entry.mode ?: "team"
                    )
                } else {
                    entry
                }
            }
            if (!existing.isNullOrEmpty()) {
                return existing
            }
            return buildViewInEntries(document.createdOn, document.parentCode, teamId, teamName)
        }

        @androidx.annotation.VisibleForTesting
        internal fun buildViewInEntries(
            createdOn: String?,
            parentCode: String?,
            teamId: String?,
            teamName: String?
        ): List<DashboardNewsRepository.ViewInEntry> {
            val targetTeamId = teamId?.takeIf { it.isNotBlank() }
            val targetTeamName = teamName?.takeIf { it.isNotBlank() }
            if (!targetTeamId.isNullOrEmpty()) {
                return listOf(
                    DashboardNewsRepository.ViewInEntry(
                        section = "teams",
                        id = targetTeamId,
                        isPublic = false,
                        name = targetTeamName,
                        mode = "team"
                    )
                )
            }

            val planet = createdOn?.takeIf { it.isNotBlank() }
            val parent = parentCode?.takeIf { it.isNotBlank() }
            if (planet == null || parent == null) {
                return emptyList()
            }
            return listOf(
                DashboardNewsRepository.ViewInEntry(
                    section = "community",
                    id = "$planet@$parent"
                )
            )
        }
    }
}
