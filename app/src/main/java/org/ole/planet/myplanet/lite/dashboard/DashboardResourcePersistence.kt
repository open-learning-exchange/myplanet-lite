package org.ole.planet.myplanet.lite.dashboard

import java.io.IOException
import java.net.URLEncoder
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

internal class DashboardResourcePersistence(
    private val client: OkHttpClient,
    private val ioDispatcher: CoroutineDispatcher
) {
    suspend fun createDatabaseDocument(
        baseUrl: String,
        dbName: String,
        sessionCookie: String?,
        username: String?,
        password: String?,
        payload: JSONObject
    ): Result<JSONObject> = withContext(ioDispatcher) {
        runCatching {
            val normalizedBase = normalizedBaseUrl(baseUrl)
            val request = Request.Builder()
                .url("$normalizedBase/db/$dbName")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .addAuthentication(username, password, sessionCookie)
                .build()

            client.newCall(request).execute().use(::parseJsonResponse)
        }
    }

    suspend fun updateResourceDocument(
        baseUrl: String,
        sessionCookie: String?,
        username: String?,
        password: String?,
        resourceId: String,
        payload: JSONObject
    ): Result<JSONObject> = withContext(ioDispatcher) {
        runCatching {
            val normalizedBase = normalizedBaseUrl(baseUrl)
            val request = Request.Builder()
                .url("$normalizedBase/db/resources/${resourceId.trim()}")
                .put(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .addAuthentication(username, password, sessionCookie)
                .build()

            client.newCall(request).execute().use(::parseJsonResponse)
        }
    }

    suspend fun createAndUploadResourceSequence(
        request: DashboardResourcesRepository.CreateAndUploadResourceRequest
    ): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            val creationResponse = createDatabaseDocument(
                baseUrl = request.baseUrl,
                dbName = "resources",
                sessionCookie = request.sessionCookie,
                username = request.credentials?.username,
                password = request.credentials?.password,
                payload = request.payload
            ).getOrThrow()

            val resourceId = creationResponse.optString("id").orEmpty()
            val creationRevision = creationResponse.optString("rev").orEmpty()
            if (resourceId.isBlank() || creationRevision.isBlank()) {
                throw DashboardResourcesRepository.InvalidServerResponseException("Invalid server response")
            }

            val renamedFileName = "${resourceId}.${request.fileExtension.lowercase(Locale.ROOT)}"
            val updatePayload = JSONObject(request.payload.toString())
                .put("_id", resourceId)
                .put("_rev", creationRevision)
                .put("filename", renamedFileName)
                .put("mediaType", request.mediaType)

            val updateResponse = updateResourceDocument(
                baseUrl = request.baseUrl,
                sessionCookie = request.sessionCookie,
                username = request.credentials?.username,
                password = request.credentials?.password,
                resourceId = resourceId,
                payload = updatePayload
            ).getOrThrow()
            val updateRevision = updateResponse.optString("rev").orEmpty().ifBlank { creationRevision }

            uploadResourceAttachment(
                DashboardResourcesRepository.UploadAttachmentRequest(
                    baseUrl = request.baseUrl,
                    sessionCookie = request.sessionCookie,
                    username = request.credentials?.username,
                    password = request.credentials?.password,
                    resourceId = resourceId,
                    filename = renamedFileName,
                    revision = updateRevision,
                    mimeType = request.mimeType,
                    bytes = request.bytes
                )
            ).getOrThrow()

            if (!request.teamId.isNullOrBlank() && !request.planetCode.isNullOrBlank()) {
                val linkPayload = JSONObject()
                    .put("resourceId", resourceId)
                    .put("sourcePlanet", request.planetCode)
                    .put("title", request.payload.optString("title"))
                    .put("teamId", request.teamId)
                    .put("teamPlanetCode", request.planetCode)
                    .put("teamType", "local")
                    .put("docType", "resourceLink")

                createDatabaseDocument(
                    baseUrl = request.baseUrl,
                    dbName = "teams",
                    sessionCookie = request.sessionCookie,
                    username = request.credentials?.username,
                    password = request.credentials?.password,
                    payload = linkPayload
                ).getOrNull()
            }
        }
    }

    suspend fun uploadResourceAttachment(
        request: DashboardResourcesRepository.UploadAttachmentRequest
    ): Result<JSONObject> = withContext(ioDispatcher) {
        runCatching {
            val normalizedBase = normalizedBaseUrl(request.baseUrl)
            val encodedRev = URLEncoder.encode(request.revision, Charsets.UTF_8.name())
            val requestUrl =
                "$normalizedBase/db/resources/${request.resourceId.trim()}/${request.filename.trim()}?rev=$encodedRev"
            val httpRequest = Request.Builder()
                .url(requestUrl)
                .put(request.bytes.toRequestBody(request.mimeType.toMediaType()))
                .addAuthentication(request.username, request.password, request.sessionCookie)
                .build()

            client.newCall(httpRequest).execute().use(::parseJsonResponse)
        }
    }

    private fun normalizedBaseUrl(baseUrl: String): String {
        return baseUrl.trim().trimEnd('/').also {
            if (it.isEmpty()) throw IOException("Missing server base URL")
        }
    }

    private fun Request.Builder.addAuthentication(
        username: String?,
        password: String?,
        sessionCookie: String?
    ): Request.Builder = apply {
        if (!username.isNullOrBlank() && !password.isNullOrBlank()) {
            addHeader("Authorization", Credentials.basic(username, password))
        }
        sessionCookie?.takeIf { it.isNotBlank() }?.let { cookie ->
            addHeader("Cookie", cookie.substringBefore(";"))
        }
    }

    private fun parseJsonResponse(response: Response): JSONObject {
        val body = response.body.string()
        if (!response.isSuccessful) {
            val reason = runCatching { JSONObject(body).optString("reason") }.getOrNull()
            val message = if (!reason.isNullOrBlank()) {
                "Error ${response.code}: $reason"
            } else {
                "Unexpected response ${response.code}"
            }
            throw IOException(message)
        }
        return runCatching { JSONObject(body) }.getOrElse {
            JSONObject().put("raw", body)
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
