package org.ole.planet.myplanet.lite.dashboard

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import java.io.File
import java.io.IOException
import java.util.regex.Pattern
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.ole.planet.myplanet.lite.auth.AuthDependencies
import org.ole.planet.myplanet.lite.profile.StoredCredentials
import org.ole.planet.myplanet.lite.util.BirthDateString
import org.ole.planet.myplanet.lite.util.DateStringAdapter

class DashboardResourcesRepository(
    private val client: OkHttpClient = AuthDependencies.client,
    private val moshi: Moshi = AuthDependencies.moshi.newBuilder().add(DateStringAdapter()).build(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val responseAdapter = moshi.adapter(ResourcesFindResponse::class.java)
    private val teamLinksResponseAdapter = moshi.adapter(TeamLinksFindResponse::class.java)
    private val persistence = DashboardResourcePersistence(client, ioDispatcher)
    private val downloads = DashboardResourceDownloads(client, ioDispatcher)

    suspend fun fetchCommunityResources(
        baseUrl: String,
        sessionCookie: String?,
        searchQuery: String,
        mediaTypeFilter: String?,
        sortBy: String,
        sortDescending: Boolean,
        limit: Int = 1000,
        skip: Int = 0
    ): Result<List<ResourceDocument>> {
        return withContext(ioDispatcher) {
            runCatching {
                val normalizedBase = baseUrl.trim().trimEnd('/')
                if (normalizedBase.isEmpty()) {
                    throw IOException("Missing server base URL")
                }
                val requestUrl = "$normalizedBase/db/resources/_find"

                val selectorJson = JSONObject()
                    .put("_id", JSONObject().put($$"$gt", JSONObject.NULL))
                    .put("privateFor", JSONObject().put($$"$exists", false))

                if (searchQuery.isNotBlank()) {
                    selectorJson.put("title", JSONObject().put($$"$regex", "(?i)" + Pattern.quote(searchQuery)))
                }

                mediaTypeFilter?.takeIf { it.isNotBlank() }?.let { type ->
                    selectorJson.put("mediaType", type)
                }

                val sortArray = JSONArray()
                sortArray.put(JSONObject().put(sortBy, if (sortDescending) "desc" else "asc"))

                val payload = JSONObject()
                    .put("selector", selectorJson)
                    .put("skip", skip)
                    .put("limit", limit)
                    .put("sort", sortArray)

                val json = payload.toString()
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val requestBuilder = Request.Builder()
                    .url(requestUrl)
                    .post(json.toRequestBody(mediaType))
                sessionCookie?.takeIf { it.isNotBlank() }?.let { cookie ->
                    requestBuilder.addHeader("Cookie", cookie)
                }

                client.newCall(requestBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Unexpected response ${response.code}")
                    }
                    val body = response.body.string()
                    val parsed = responseAdapter.fromJson(body)
                        ?: throw IOException("Invalid response body")
                    parsed.docs ?: emptyList()
                }
            }.onFailure { }
        }
    }

    suspend fun createTeamDocument(
        baseUrl: String,
        sessionCookie: String?,
        username: String?,
        password: String?,
        payload: JSONObject
    ): Result<JSONObject> {
        return persistence.createDatabaseDocument(baseUrl, "teams", sessionCookie, username, password, payload)
    }

    data class ResourceMetadataRequest(
        val title: String,
        val description: String,
        val language: String,
        val username: String,
        val planetCode: String,
        val isDownloadable: Boolean
    )

    fun buildResourcePayload(
        request: ResourceMetadataRequest,
        subject: String,
        level: String
    ): JSONObject {
        return JSONObject()
            .put("title", request.title)
            .put("description", request.description)
            .put("subject", JSONArray().put(subject))
            .put("level", JSONArray().put(level))
            .put("language", request.language)
            .put("addedBy", request.username)
            .put("sourcePlanet", request.planetCode)
            .put("resideOn", request.planetCode)
            .put("isDownloadable", request.isDownloadable)
            .put("private", false)
    }

    suspend fun createResourceDocument(
        baseUrl: String,
        sessionCookie: String?,
        username: String?,
        password: String?,
        payload: JSONObject
    ): Result<JSONObject> {
        return persistence.createDatabaseDocument(baseUrl, "resources", sessionCookie, username, password, payload)
    }

    suspend fun updateResourceDocument(
        baseUrl: String,
        sessionCookie: String?,
        username: String?,
        password: String?,
        resourceId: String,
        payload: JSONObject
    ): Result<JSONObject> {
        return persistence.updateResourceDocument(
            baseUrl, sessionCookie, username, password, resourceId, payload
        )
    }



    class InvalidServerResponseException(message: String) : IOException(message)

    class CreateAndUploadResourceRequest(
        val baseUrl: String,
        val sessionCookie: String?,
        val credentials: StoredCredentials?,
        val payload: JSONObject,
        val fileExtension: String,
        val mediaType: String,
        val mimeType: String,
        val bytes: ByteArray,
        val teamId: String?,
        val planetCode: String?
    )

    suspend fun createAndUploadResourceSequence(
        request: CreateAndUploadResourceRequest
    ): Result<Unit> {
        return persistence.createAndUploadResourceSequence(request)
    }

    data class UploadAttachmentRequest(
        val baseUrl: String,
        val sessionCookie: String?,
        val username: String?,
        val password: String?,
        val resourceId: String,
        val filename: String,
        val revision: String,
        val mimeType: String,
        val bytes: ByteArray
    )

    suspend fun uploadResourceAttachment(
        request: UploadAttachmentRequest
    ): Result<JSONObject> {
        return persistence.uploadResourceAttachment(request)
    }

    suspend fun downloadResourceBytes(
        baseUrl: String,
        sessionCookie: String?,
        resourceId: String,
        filename: String,
        onProgress: ((Int?) -> Unit)? = null
    ): Result<ByteArray> {
        return downloads.downloadResourceBytes(
            baseUrl, sessionCookie, resourceId, filename, onProgress
        )
    }

    data class TeamResourcesRequest(
        val baseUrl: String,
        val sessionCookie: String?,
        val username: String?,
        val password: String?,
        val teamId: String,
        val searchQuery: String,
        val mediaTypeFilter: String?,
        val sortBy: String,
        val sortDescending: Boolean,
        val limit: Int = 1000
    )

    suspend fun fetchTeamResources(
        request: TeamResourcesRequest
    ): Result<List<ResourceDocument>> {
        return withContext(ioDispatcher) {
            runCatching {
                val normalizedBase = request.baseUrl.trim().trimEnd('/')
                if (normalizedBase.isEmpty()) {
                    throw IOException("Missing server base URL")
                }
                if (request.teamId.isBlank()) {
                    throw IOException("Missing team id")
                }
                val resourceIds = runCatching {
                    fetchTeamResourceIds(
                        normalizedBase = normalizedBase,
                        username = request.username,
                        password = request.password,
                        teamId = request.teamId.trim()
                    )
                }.getOrDefault(emptyList())

                fetchTeamResourcesByIds(
                    normalizedBase = normalizedBase,
                    sessionCookie = request.sessionCookie,
                    username = request.username,
                    password = request.password,
                    teamId = request.teamId.trim(),
                    resourceIds = resourceIds,
                    searchQuery = request.searchQuery,
                    mediaTypeFilter = request.mediaTypeFilter,
                    sortBy = request.sortBy,
                    sortDescending = request.sortDescending,
                    limit = request.limit
                )
            }.onFailure { }
        }
    }

    private fun fetchTeamResourceIds(
        normalizedBase: String,
        username: String?,
        password: String?,
        teamId: String
    ): List<String> {
        val requestUrl = "$normalizedBase/db/teams/_find"
        val payload = JSONObject()
            .put("selector", JSONObject()
                .put("docType", "resourceLink")
                .put("teamId", teamId)
            )
            .put("fields", JSONArray().put("resourceId"))

        val json = payload.toString()
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBuilder = Request.Builder()
            .url(requestUrl)
            .post(json.toRequestBody(mediaType))
        if (!username.isNullOrBlank() && !password.isNullOrBlank()) {
            requestBuilder.addHeader("Authorization", Credentials.basic(username, password))
        }
        client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Unexpected response ${response.code}")
            }
            val body = response.body.string()
            val parsed = teamLinksResponseAdapter.fromJson(body)
                ?: throw IOException("Invalid team links response")
            return parsed.docs.orEmpty()
                .mapNotNull { it.resourceId?.trim()?.takeIf { id -> id.isNotEmpty() } }
                .distinct()
        }
    }

    private fun fetchTeamResourcesByIds(
        normalizedBase: String,
        sessionCookie: String?,
        username: String?,
        password: String?,
        teamId: String,
        resourceIds: List<String>,
        searchQuery: String,
        mediaTypeFilter: String?,
        sortBy: String,
        sortDescending: Boolean,
        limit: Int
    ): List<ResourceDocument> {
        val requestUrl = "$normalizedBase/db/resources/_find"

        val idsArray = JSONArray()
        resourceIds.forEach { idsArray.put(it) }

        val selectorJson = JSONObject()
        val orArray = JSONArray()
        if (idsArray.length() > 0) {
            orArray.put(JSONObject().put("_id", JSONObject().put($$"$in", idsArray)))
        }
        orArray.put(JSONObject().put("privateFor.teams", teamId))
        selectorJson.put($$"$or", orArray)

        if (searchQuery.isNotBlank()) {
            selectorJson.put("title", JSONObject().put($$"$regex", "(?i)" + Pattern.quote(searchQuery)))
        }

        mediaTypeFilter?.takeIf { it.isNotBlank() }?.let { type ->
            selectorJson.put("mediaType", type)
        }

        val sortArray = JSONArray()
        sortArray.put(JSONObject().put(sortBy, if (sortDescending) "desc" else "asc"))

        val payload = JSONObject()
            .put("selector", selectorJson)
            .put("skip", 0)
            .put("limit", limit)
            .put("sort", sortArray)

        val json = payload.toString()
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBuilder = Request.Builder()
            .url(requestUrl)
            .post(json.toRequestBody(mediaType))

        if (!username.isNullOrBlank() && !password.isNullOrBlank()) {
            requestBuilder.addHeader("Authorization", Credentials.basic(username, password))
        }
        sessionCookie?.takeIf { it.isNotBlank() }?.let { cookie ->
            requestBuilder.addHeader("Cookie", cookie)
        }
        client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Unexpected response ${response.code}")
            }
            val body = response.body.string()
            val parsed = responseAdapter.fromJson(body)
                ?: throw IOException("Invalid resources response")
            return parsed.docs ?: emptyList()
        }
    }



    suspend fun downloadPdfToCache(url: String, authHeader: String?, cacheDir: File): File? {
        return downloads.downloadPdfToCache(url, authHeader, cacheDir)
    }

    @JsonClass(generateAdapter = true)
    data class ResourcesFindResponse(
        val docs: List<ResourceDocument>?,
        val bookmark: String?
    )

    @JsonClass(generateAdapter = true)
    data class TeamLinksFindResponse(
        val docs: List<TeamLinkDoc>?
    )

    @JsonClass(generateAdapter = true)
    data class TeamLinkDoc(
        val resourceId: String?
    )

    @JsonClass(generateAdapter = true)
    data class ResourceDocument(
        @param:Json(name = "_id") val id: String?,
        val title: String?,
        val filename: String?,
        @param:BirthDateString val createdDate: Long?,
        val mediaType: String?,
        val isDownloadable: Any?,
        @param:Json(name = "_attachments") val attachments: Map<String, ResourceAttachment>?
    )

    @JsonClass(generateAdapter = true)
    data class ResourceAttachment(
        @param:Json(name = "content_type") val contentType: String?
    )
}
