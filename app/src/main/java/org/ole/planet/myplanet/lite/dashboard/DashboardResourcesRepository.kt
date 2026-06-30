package org.ole.planet.myplanet.lite.dashboard

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.IOException
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.ole.planet.myplanet.lite.util.BirthDateString
import org.ole.planet.myplanet.lite.util.DateStringAdapter

class DashboardResourcesRepository {

    private val client: OkHttpClient = OkHttpClient.Builder().build()
    private val moshi: Moshi = Moshi.Builder()
        .add(DateStringAdapter())
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val responseAdapter = moshi.adapter(ResourcesFindResponse::class.java)
    private val teamLinksResponseAdapter = moshi.adapter(TeamLinksFindResponse::class.java)

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
        return withContext(Dispatchers.IO) {
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
                    selectorJson.put("title", JSONObject().put($$"$regex", "(?i)$searchQuery"))
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
        return createDatabaseDocument(baseUrl, "teams", sessionCookie, username, password, payload)
    }

    suspend fun createResourceDocument(
        baseUrl: String,
        sessionCookie: String?,
        username: String?,
        password: String?,
        payload: JSONObject
    ): Result<JSONObject> {
        return createDatabaseDocument(baseUrl, "resources", sessionCookie, username, password, payload)
    }

    private suspend fun createDatabaseDocument(
        baseUrl: String,
        dbName: String,
        sessionCookie: String?,
        username: String?,
        password: String?,
        payload: JSONObject
    ): Result<JSONObject> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val normalizedBase = baseUrl.trim().trimEnd('/')
                if (normalizedBase.isEmpty()) {
                    throw IOException("Missing server base URL")
                }
                val requestUrl = "$normalizedBase/db/$dbName"
                val json = payload.toString()
                val requestBuilder = Request.Builder()
                    .url(requestUrl)
                    .post(json.toRequestBody("application/json; charset=utf-8".toMediaType()))

                if (!username.isNullOrBlank() && !password.isNullOrBlank()) {
                    requestBuilder.addHeader("Authorization", Credentials.basic(username, password))
                }
                sessionCookie?.takeIf { it.isNotBlank() }?.let { cookie ->
                    requestBuilder.addHeader("Cookie", cookie.substringBefore(";"))
                }

                client.newCall(requestBuilder.build()).execute().use { response ->
                    val body = response.body.string()
                    if (!response.isSuccessful) {
                        val reason = runCatching { JSONObject(body).optString("reason") }.getOrNull()
                        val message = if (!reason.isNullOrBlank()) "Error ${response.code}: $reason" else "Unexpected response ${response.code}"
                        throw IOException(message)
                    }
                    runCatching { JSONObject(body) }.getOrElse {
                        JSONObject().put("raw", body)
                    }
                }
            }.onFailure { }
        }
    }

    suspend fun updateResourceDocument(
        baseUrl: String,
        sessionCookie: String?,
        username: String?,
        password: String?,
        resourceId: String,
        payload: JSONObject
    ): Result<JSONObject> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val normalizedBase = baseUrl.trim().trimEnd('/')
                if (normalizedBase.isEmpty()) {
                    throw IOException("Missing server base URL")
                }
                val requestUrl = "$normalizedBase/db/resources/${resourceId.trim()}"
                val json = payload.toString()
                val requestBuilder = Request.Builder()
                    .url(requestUrl)
                    .put(json.toRequestBody("application/json; charset=utf-8".toMediaType()))

                if (!username.isNullOrBlank() && !password.isNullOrBlank()) {
                    requestBuilder.addHeader("Authorization", Credentials.basic(username, password))
                }
                sessionCookie?.takeIf { it.isNotBlank() }?.let { cookie ->
                    requestBuilder.addHeader("Cookie", cookie.substringBefore(";"))
                }

                client.newCall(requestBuilder.build()).execute().use { response ->
                    val body = response.body.string()
                    if (!response.isSuccessful) {
                        val reason = runCatching { JSONObject(body).optString("reason") }.getOrNull()
                        val message = if (!reason.isNullOrBlank()) "Error ${response.code}: $reason" else "Unexpected response ${response.code}"
                        throw IOException(message)
                    }
                    runCatching { JSONObject(body) }.getOrElse {
                        JSONObject().put("raw", body)
                    }
                }
            }.onFailure { }
        }
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
        return withContext(Dispatchers.IO) {
            runCatching {
                val normalizedBase = request.baseUrl.trim().trimEnd('/')
                if (normalizedBase.isEmpty()) {
                    throw IOException("Missing server base URL")
                }
                val encodedRev = URLEncoder.encode(request.revision, Charsets.UTF_8.name())
                val requestUrl = "$normalizedBase/db/resources/${request.resourceId.trim()}/${request.filename.trim()}?rev=$encodedRev"
                val requestBuilder = Request.Builder()
                    .url(requestUrl)
                    .put(request.bytes.toRequestBody(request.mimeType.toMediaType()))

                if (!request.username.isNullOrBlank() && !request.password.isNullOrBlank()) {
                    requestBuilder.addHeader("Authorization", Credentials.basic(request.username, request.password))
                }
                request.sessionCookie?.takeIf { it.isNotBlank() }?.let { cookie ->
                    requestBuilder.addHeader("Cookie", cookie.substringBefore(";"))
                }

                client.newCall(requestBuilder.build()).execute().use { response ->
                    val body = response.body.string()
                    if (!response.isSuccessful) {
                        val reason = runCatching { JSONObject(body).optString("reason") }.getOrNull()
                        val message = if (!reason.isNullOrBlank()) "Error ${response.code}: $reason" else "Unexpected response ${response.code}"
                        throw IOException(message)
                    }
                    runCatching { JSONObject(body) }.getOrElse {
                        JSONObject().put("raw", body)
                    }
                }
            }.onFailure { }
        }
    }

    suspend fun downloadResourceBytes(
        baseUrl: String,
        sessionCookie: String?,
        resourceId: String,
        filename: String,
        onProgress: ((Int?) -> Unit)? = null
    ): Result<ByteArray> {
        return withContext(Dispatchers.IO) {
            runCatching {
                val normalizedBase = baseUrl.trim().trimEnd('/')
                if (normalizedBase.isEmpty()) {
                    throw IOException("Missing server base URL")
                }
                if (resourceId.isBlank() || filename.isBlank()) {
                    throw IOException("Missing resource id or filename")
                }
                val requestUrl = "$normalizedBase/db/resources/${resourceId.trim()}/${filename.trim()}"
                val requestBuilder = Request.Builder()
                    .url(requestUrl)
                    .get()
                sessionCookie?.takeIf { it.isNotBlank() }?.let { cookie ->
                    requestBuilder.addHeader("Cookie", cookie)
                }
                client.newCall(requestBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Unexpected response ${response.code}")
                    }
                    val body = response.body
                    val totalBytes = body.contentLength()
                    val source = body.source()
                    val sink = Buffer()
                    val chunkSize = 8_192L
                    var downloadedBytes = 0L
                    var lastProgress: Int? = null
                    onProgress?.invoke(if (totalBytes > 0L) 0 else null)
                    while (true) {
                        val read = source.read(sink, chunkSize)
                        if (read == -1L) break
                        downloadedBytes += read
                        if (totalBytes > 0L) {
                            val progress = ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
                            if (progress != lastProgress) {
                                lastProgress = progress
                                onProgress?.invoke(progress)
                            }
                        }
                    }
                    if (totalBytes > 0L && lastProgress != 100) {
                        onProgress?.invoke(100)
                    }
                    sink.readByteArray()
                }
            }.onFailure { }
        }
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
        return withContext(Dispatchers.IO) {
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
            selectorJson.put("title", JSONObject().put($$"$regex", "(?i)$searchQuery"))
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
