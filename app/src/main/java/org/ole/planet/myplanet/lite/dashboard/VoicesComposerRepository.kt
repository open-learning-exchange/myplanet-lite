/**
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-11-17
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
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.ole.planet.myplanet.lite.profile.StoredCredentials


class VoicesComposerRepository(
    private val client: OkHttpClient,
    private val moshi: Moshi,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val requestAdapter = moshi.adapter(CreateVoiceRequest::class.java)
    private val responseAdapter = moshi.adapter(CreateVoiceResponse::class.java)
    private val resourceMetadataAdapter = moshi.adapter(ResourceMetadataRequest::class.java)
    private val resourceCreationAdapter = moshi.adapter(ResourceCreationResponse::class.java)
    private val resourceUploadAdapter = moshi.adapter(ResourceUploadResponse::class.java)

    suspend fun createVoice(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        message: String,
        createdOn: String?,
        parentCode: String?,
        replyTo: String?,
        images: List<ImagePayload>,
        labels: List<String>,
        userPayload: UserPayload?,
        teamId: String? = null,
        teamName: String? = null
    ): Result<CreateVoiceResponse> {
        return withContext(dispatcher) {
            runCatching {
                val normalizedBase = baseUrl.trim().trimEnd('/')
                if (normalizedBase.isEmpty()) {
                    throw IOException("Missing server base URL")
                }
                val timestamp = System.currentTimeMillis()
                val payload = CreateVoiceRequest(
                    chat = false,
                    message = message,
                    time = timestamp,
                    createdOn = createdOn,
                    docType = "message",
                    viewIn = buildViewInEntries(createdOn, parentCode, teamId, teamName),
                    avatar = "",
                    messageType = "news",
                    messagePlanetCode = createdOn,
                    replyTo = replyTo ?: "",
                    parentCode = parentCode,
                    images = images,
                    labels = labels,
                    user = userPayload,
                    news = buildNewsMetadata(userPayload?.id ?: userPayload?.name, timestamp)
                )
                val requestBody = requestAdapter.toJson(payload)
                    .toRequestBody(JSON_MEDIA_TYPE)
                val requestBuilder = Request.Builder()
                    .url("$normalizedBase/db/news")
                    .post(requestBody)
                credentials?.let {
                    requestBuilder.addHeader("Authorization", Credentials.basic(it.username, it.password))
                }
                sessionCookie?.takeIf { it.isNotBlank() }?.let { cookie ->
                    requestBuilder.addHeader("Cookie", cookie)
                }
                client.newCall(requestBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Unexpected response ${'$'}{response.code}")
                    }
                    val body = response.body.string()
                    responseAdapter.fromJson(body)
                        ?: throw IOException("Invalid response body")
                }
            }
        }
    }

    suspend fun createResourceDocument(
        baseUrl: String,
        credentials: StoredCredentials,
        metadata: ResourceMetadataRequest
    ): ResourceCreationResponse {
        return withContext(dispatcher) {
            val normalizedBase = baseUrl.trim().trimEnd('/')
            if (normalizedBase.isEmpty()) {
                throw IOException("Missing server base URL")
            }
            val requestBody = resourceMetadataAdapter.toJson(metadata)
                .toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url("$normalizedBase/db/resources")
                .post(requestBody)
                .addHeader("Authorization", Credentials.basic(credentials.username, credentials.password))
                .build()
            val creationResponse = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Unexpected response ${'$'}{response.code}")
                }
                val body = response.body.string()
                resourceCreationAdapter.fromJson(body)
                    ?: throw IOException("Invalid response body")
            }
            creationResponse
        }
    }

    suspend fun uploadResourceBinary(
        baseUrl: String,
        credentials: StoredCredentials,
        resourceId: String,
        fileName: String,
        revision: String,
        bytes: ByteArray
    ): ResourceUploadResponse {
        return withContext(dispatcher) {
            val normalizedBase = baseUrl.trim().trimEnd('/')
            if (normalizedBase.isEmpty()) {
                throw IOException("Missing server base URL")
            }
            val url = "$normalizedBase/db/resources/${resourceId.trim()}/${fileName.trim()}"
            val request = Request.Builder()
                .url(url)
                .put(bytes.toRequestBody(OCTET_STREAM_MEDIA_TYPE))
                .addHeader("Authorization", Credentials.basic(credentials.username, credentials.password))
                .addHeader("If-Match", revision)
                .build()
            val uploadResponse = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Unexpected response ${'$'}{response.code}")
                }
                val body = response.body.string()
                resourceUploadAdapter.fromJson(body)
                    ?: throw IOException("Invalid response body")
            }
            uploadResponse
        }
    }

    @JsonClass(generateAdapter = true)
    data class CreateVoiceRequest(
        val chat: Boolean,
        val message: String,
        val time: Long,
        val createdOn: String?,
        val docType: String,
        val viewIn: List<ViewInEntry>,
        val avatar: String,
        val messageType: String,
        val messagePlanetCode: String?,
        val replyTo: String,
        val parentCode: String?,
        val images: List<ImagePayload>,
        val labels: List<String>,
        val user: UserPayload?,
        val news: NewsMetadata?
    )

    @JsonClass(generateAdapter = true)
    data class CreateVoiceResponse(
        val ok: Boolean?,
        @param:Json(name = "id") val id: String?,
        @param:Json(name = "rev") val revision: String?
    )

    @JsonClass(generateAdapter = true)
    data class ResourceMetadataRequest(
        val title: String,
        val createdDate: Long,
        val filename: String,
        @param:Json(name = "private") val isPrivate: Boolean,
        val addedBy: String,
        val resideOn: String?,
        val sourcePlanet: String?,
        val androidId: String?,
        val deviceName: String?,
        val customDeviceName: String?,
        val mediaType: String,
        val privateFor: String
    )

    @JsonClass(generateAdapter = true)
    data class ResourceCreationResponse(
        val ok: Boolean?,
        @param:Json(name = "id") val id: String,
        @param:Json(name = "rev") val revision: String
    )

    @JsonClass(generateAdapter = true)
    data class ResourceUploadResponse(
        val ok: Boolean? = null,
        @param:Json(name = "id") val id: String? = null,
        @param:Json(name = "resourceId") val resourceId: String? = null,
        val filename: String? = null,
        val markdown: String? = null,
        @param:Json(name = "rev") val revision: String? = null
    )

    @JsonClass(generateAdapter = true)
    data class ImagePayload(
        val resourceId: String,
        val filename: String,
        val markdown: String
    )

    @JsonClass(generateAdapter = true)
    data class ViewInEntry(
        val section: String,
        @param:Json(name = "_id") val id: String,
        @param:Json(name = "public") val isPublic: Boolean? = null,
        val name: String? = null,
        val mode: String? = null
    )

    @JsonClass(generateAdapter = true)
    data class UserPayload(
        @param:Json(name = "_id") val id: String?,
        val name: String?,
        val firstName: String?,
        val middleName: String?,
        val lastName: String?,
        val email: String?,
        val language: String?,
        val phoneNumber: String?,
        val planetCode: String?,
        val parentCode: String?,
        val roles: List<String>?,
        val joinDate: Long?,
        @param:Json(name = "_attachments") val attachments: Map<String, AttachmentPayload>?
    )

    @JsonClass(generateAdapter = true)
    data class AttachmentPayload(
        @param:Json(name = "content_type") val contentType: String?,
        val revpos: Int?,
        val digest: String?,
        val length: Int?,
        val stub: Boolean?,
        val data: String?
    )

    @JsonClass(generateAdapter = true)
    data class NewsMetadata(
        @param:Json(name = "_id") val id: String?,
        @param:Json(name = "_rev") val revision: String?,
        val user: String?,
        val aiProvider: String?,
        val title: String?,
        val conversations: List<Any>?,
        val createdDate: Long?,
        val updatedDate: Long?,
        val sharedBy: String?
    )

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val OCTET_STREAM_MEDIA_TYPE = "application/octet-stream".toMediaType()
    }

    private fun buildViewInEntries(
        createdOn: String?,
        parentCode: String?,
        teamId: String?,
        teamName: String?
    ): List<ViewInEntry> {
        val targetTeamId = teamId?.takeIf { it.isNotBlank() }
        val targetTeamName = teamName?.takeIf { it.isNotBlank() }
        if (!targetTeamId.isNullOrEmpty()) {
            return listOf(
                ViewInEntry(
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
        return listOf(ViewInEntry(section = "community", id = "$planet@$parent"))
    }

    private fun buildNewsMetadata(userId: String?, timestamp: Long): NewsMetadata {
        return NewsMetadata(
            id = null,
            revision = null,
            user = userId,
            aiProvider = "",
            title = "",
            conversations = emptyList<Any>(),
            createdDate = timestamp,
            updatedDate = timestamp,
            sharedBy = ""
        )
    }
}
