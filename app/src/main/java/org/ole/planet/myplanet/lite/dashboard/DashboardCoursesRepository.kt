/**
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-12-28
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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.ole.planet.myplanet.lite.util.MarkdownUtils
import androidx.core.net.toUri
import java.io.File
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyDocument
import org.ole.planet.myplanet.lite.profile.StoredCredentials

class DashboardCoursesRepository(
    private val client: OkHttpClient = OkHttpClient.Builder().build(),
    private val moshi: Moshi = Moshi.Builder()
        .add(FlexibleSurveyJsonAdapter())
        .addLast(KotlinJsonAdapterFactory())
        .build(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val findRequestAdapter = moshi.adapter(ShelfFindRequest::class.java)
    private val findResponseAdapter = moshi.adapter(ShelfFindResponse::class.java)
    private val shelfDocumentAdapter = moshi.adapter(ShelfDocument::class.java)
    private val coursesProgressRequestAdapter = moshi.adapter(CoursesProgressFindRequest::class.java)
    private val coursesProgressResponseAdapter = moshi.adapter(CoursesProgressResponse::class.java)
    private val coursesProgressBulkAdapter = moshi.adapter(CoursesProgressBulkRequest::class.java)
    private val bulkDocsResultAdapter = moshi.adapter<List<BulkDocResult>>(
        com.squareup.moshi.Types.newParameterizedType(List::class.java, BulkDocResult::class.java)
    )
    private val allDocsRequestAdapter = moshi.adapter(AllDocsRequest::class.java)
    private val allDocsResponseAdapter = moshi.adapter(AllDocsResponse::class.java)
    private val coursesFindRequestAdapter = moshi.adapter(CoursesFindRequest::class.java)
    private val coursesFindResponseAdapter = moshi.adapter(CourseFindResponse::class.java)
    private val teamCoursesRequestAdapter = moshi.adapter(TeamCoursesFindRequest::class.java)
    private val teamCoursesResponseAdapter = moshi.adapter(TeamCoursesResponse::class.java)
    private val tagsFindRequestAdapter = moshi.adapter(TagsFindRequest::class.java)
    private val tagsFindResponseAdapter = moshi.adapter(TagsFindResponse::class.java)
    private val tagLinksFindRequestAdapter = moshi.adapter(TagLinksFindRequest::class.java)
    private val tagLinksFindResponseAdapter = moshi.adapter(TagLinksFindResponse::class.java)
    private val courseCache = mutableMapOf<String, CourseDocument>()
    private var shelfCache: ShelfDocument? = null

    suspend fun fetchUserCourseIds(
        baseUrl: String,
        credentials: StoredCredentials
    ): Result<List<String>> {
        return withContext(dispatcher) {
            runCatching {
                val normalizedBase = baseUrl.trim().trimEnd('/')
                if (normalizedBase.isEmpty()) {
                    throw IOException("Missing server base URL")
                }
                val requestUrl = "$normalizedBase/db/shelf/_find"
                val payload = findRequestAdapter.toJson(
                    ShelfFindRequest(
                        selector = mapOf("_id" to "org.couchdb.user:${credentials.username}")
                    )
                )
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val request = Request.Builder()
                    .url(requestUrl)
                    .post(payload.toRequestBody(mediaType))
                    .addHeader("Authorization", Credentials.basic(credentials.username, credentials.password))
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        response.body.string()
                        throw IOException("Unexpected response ${response.code}")
                    }
                    val parsed = findResponseAdapter.fromJson(response.body.string())
                        ?: throw IOException("Invalid response body")
                    val document = parsed.docs.firstOrNull()
                    if (document != null) {
                        shelfCache = document
                    }
                    document?.courseIds ?: emptyList()
                }
            }
        }
    }

    suspend fun fetchShelfDocument(
        baseUrl: String,
        credentials: StoredCredentials
    ): Result<ShelfDocument> {
        return withContext(dispatcher) {
            runCatching {
                val normalizedBase = baseUrl.trim().trimEnd('/')
                if (normalizedBase.isEmpty()) {
                    throw IOException("Missing server base URL")
                }

                val requestUrl = "$normalizedBase/db/shelf/org.couchdb.user:${credentials.username}"
                val request = Request.Builder()
                    .url(requestUrl)
                    .get()
                    .addHeader("Authorization", Credentials.basic(credentials.username, credentials.password))
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val responseBody = response.body.string()
                        if (response.code == 404) {
                            val reason = runCatching {
                                org.json.JSONObject(responseBody).optString("reason")
                            }.getOrNull()
                            if (reason == "missing") {
                                val fallbackId = "org.couchdb.user:${credentials.username}"
                                return@runCatching ShelfDocument(
                                    id = fallbackId,
                                    rev = null
                                )
                            }
                        }
                        throw IOException("Unexpected response ${response.code}")
                    }
                    val document = shelfDocumentAdapter.fromJson(response.body.string())
                        ?: throw IOException("Invalid shelf response")
                    shelfCache = document
                    document
                }
            }
        }
    }

    suspend fun joinCourse(
        baseUrl: String,
        credentials: StoredCredentials,
        courseId: String
    ): Result<Unit> {
        return withContext(dispatcher) {
            runCatching {
                val normalizedBase = baseUrl.trim().trimEnd('/')
                if (normalizedBase.isEmpty()) {
                    throw IOException("Missing server base URL")
                }
                val sanitizedCourseId = courseId.takeIf { it.isNotBlank() }
                    ?: throw IOException("Missing course id")

                var shelfDocument = shelfCache ?: fetchShelfDocument(baseUrl, credentials)
                    .getOrElse { throw it }

                repeat(2) { attempt ->
                    val shelfId = shelfDocument.id
                        ?: "org.couchdb.user:${credentials.username}"

                    val updatedCourseIds = (shelfDocument.courseIds + sanitizedCourseId)
                        .filter { it.isNotBlank() }
                        .distinct()

                    val updatedDocument = shelfDocument.copy(
                        id = shelfId,
                        rev = shelfDocument.rev,
                        courseIds = updatedCourseIds
                    )

                    val requestUrl = "$normalizedBase/db/shelf/$shelfId"
                    val payload = shelfDocumentAdapter.toJson(updatedDocument)
                    val mediaType = "application/json; charset=utf-8".toMediaType()
                    val request = Request.Builder()
                        .url(requestUrl)
                        .put(payload.toRequestBody(mediaType))
                        .addHeader("Authorization", Credentials.basic(credentials.username, credentials.password))
                        .build()

                    client.newCall(request).execute().use { response ->
                        val responseBody = response.body.string()
                        if (response.isSuccessful) {
                            val updatedRev = runCatching {
                                org.json.JSONObject(responseBody).optString("rev").takeIf { it.isNotBlank() }
                            }.getOrNull()

                            val cached = updatedDocument.copy(rev = updatedRev ?: shelfDocument.rev)
                            shelfCache = cached
                            return@runCatching
                        }

                        if (response.code == 409 && attempt == 0) {
                            shelfDocument = fetchShelfDocument(baseUrl, credentials)
                                .getOrElse { throw it }
                            return@use
                        }

                        throw IOException("Unexpected response ${response.code}")
                    }
                }
            }
        }
    }

    suspend fun leaveCourse(
        baseUrl: String,
        credentials: StoredCredentials,
        courseId: String
    ): Result<Unit> {
        return withContext(dispatcher) {
            runCatching {
                val normalizedBase = baseUrl.trim().trimEnd('/')
                if (normalizedBase.isEmpty()) {
                    throw IOException("Missing server base URL")
                }
                val sanitizedCourseId = courseId.takeIf { it.isNotBlank() }
                    ?: throw IOException("Missing course id")

                var shelfDocument = shelfCache ?: fetchShelfDocument(baseUrl, credentials)
                    .getOrElse { throw it }

                repeat(2) { attempt ->
                    val shelfId = shelfDocument.id
                        ?: "org.couchdb.user:${credentials.username}"
                    val shelfRev = shelfDocument.rev
                        ?: throw IOException("Missing shelf revision")

                    val updatedCourseIds = shelfDocument.courseIds
                        .filter { it.isNotBlank() && it != sanitizedCourseId }

                    val updatedDocument = shelfDocument.copy(
                        id = shelfId,
                        rev = shelfRev,
                        courseIds = updatedCourseIds
                    )

                    val requestUrl = "$normalizedBase/db/shelf/$shelfId"
                    val payload = shelfDocumentAdapter.toJson(updatedDocument)
                    val mediaType = "application/json; charset=utf-8".toMediaType()
                    val request = Request.Builder()
                        .url(requestUrl)
                        .put(payload.toRequestBody(mediaType))
                        .addHeader("Authorization", Credentials.basic(credentials.username, credentials.password))
                        .build()

                    client.newCall(request).execute().use { response ->
                        val responseBody = response.body.string()
                        if (response.isSuccessful) {
                            val updatedRev = runCatching {
                                org.json.JSONObject(responseBody).optString("rev").takeIf { it.isNotBlank() }
                            }.getOrNull()

                            val cached = updatedDocument.copy(rev = updatedRev ?: shelfRev)
                            shelfCache = cached
                            return@runCatching
                        }

                        if (response.code == 409 && attempt == 0) {
                            shelfDocument = fetchShelfDocument(baseUrl, credentials)
                                .getOrElse { throw it }
                            return@use
                        }

                        throw IOException("Unexpected response ${response.code}")
                    }
                }
            }
        }
    }

    suspend fun fetchCourses(
        baseUrl: String,
        credentials: StoredCredentials,
        courseIds: List<String>
    ): Result<List<CourseDocument>> {
        return withContext(dispatcher) {
            runCatching {
                val normalizedBase = baseUrl.trim().trimEnd('/')
                if (normalizedBase.isEmpty()) {
                    throw IOException("Missing server base URL")
                }
                val sanitizedIds = courseIds.filter { it.isNotBlank() }
                if (sanitizedIds.isEmpty()) return@runCatching emptyList()

                val uniqueIds = sanitizedIds.distinct()
                val cachedDocuments = mutableMapOf<String, CourseDocument>()
                uniqueIds.forEach { id ->
                    courseCache[id]?.let { cachedDocuments[id] = it }
                }

                val remainingIds = uniqueIds.filterNot { cachedDocuments.containsKey(it) }
                if (remainingIds.isNotEmpty()) {
                    val requestUrl = "$normalizedBase/db/courses/_all_docs?include_docs=true"
                    val payload = allDocsRequestAdapter.toJson(AllDocsRequest(keys = remainingIds))
                    val mediaType = "application/json; charset=utf-8".toMediaType()
                    val request = Request.Builder()
                        .url(requestUrl)
                        .post(payload.toRequestBody(mediaType))
                        .addHeader("Authorization", Credentials.basic(credentials.username, credentials.password))
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            response.body.string()
                            throw IOException("Unexpected response ${response.code}")
                        }
                        val parsed = allDocsResponseAdapter.fromJson(response.body.string())
                            ?: throw IOException("Invalid response body")

                        parsed.rows.mapNotNull { it.doc }.forEach { document ->
                            val id = document.id ?: return@forEach
                            courseCache[id] = document
                            cachedDocuments[id] = document
                        }
                    }
                }

                val orderedResults = uniqueIds.mapNotNull { id ->
                    cachedDocuments[id]
                }
                orderedResults
            }
        }
    }

    suspend fun fetchCoursesProgress(
        baseUrl: String,
        credentials: StoredCredentials,
        courseIds: List<String>
    ): Result<Map<String, Int>> {
        return withContext(dispatcher) {
            runCatching {
                val normalizedBase = baseUrl.trim().trimEnd('/')
                if (normalizedBase.isEmpty()) {
                    throw IOException("Missing server base URL")
                }
                val sanitizedIds = courseIds.filter { it.isNotBlank() }
                if (sanitizedIds.isEmpty()) return@runCatching emptyMap()

                val progressByCourse = mutableMapOf<String, Int>()
                val requestUrl = "$normalizedBase/db/courses_progress/_find"
                val payload = coursesProgressRequestAdapter.toJson(
                    CoursesProgressFindRequest(
                        selector = CoursesProgressSelector(
                            userId = "org.couchdb.user:${credentials.username}",
                            courseId = CourseInSelector(included = sanitizedIds)
                        ),
                        limit = 50000
                    )
                )
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val request = Request.Builder()
                    .url(requestUrl)
                    .post(payload.toRequestBody(mediaType))
                    .addHeader("Authorization", Credentials.basic(credentials.username, credentials.password))
                    .build()

                val docs = withContext(Dispatchers.IO) {
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            response.body.string()
                            throw IOException("Unexpected response ${response.code}")
                        }
                        val parsed = coursesProgressResponseAdapter.fromJson(response.body.string())
                            ?: throw IOException("Invalid response body")
                        parsed.docs
                            .filter { !it.courseId.isNullOrBlank() && it.stepNum != null }
                    }
                }

                docs.forEach { doc ->
                    val courseId = doc.courseId ?: return@forEach
                    val stepNum = doc.stepNum ?: return@forEach
                    val currentMax = progressByCourse[courseId] ?: 0
                    if (stepNum > currentMax) {
                        progressByCourse[courseId] = stepNum
                    }
                }
                progressByCourse
            }
        }
    }

    suspend fun fetchCoursesProgressDocuments(
        baseUrl: String,
        credentials: StoredCredentials,
        courseIds: List<String>,
        stepNum: Int? = null
    ): Result<Map<String, CourseProgressDocument>> {
        return withContext(dispatcher) {
            runCatching {
                val normalizedBase = baseUrl.trim().trimEnd('/')
                if (normalizedBase.isEmpty()) {
                    throw IOException("Missing server base URL")
                }
                val sanitizedIds = courseIds.filter { it.isNotBlank() }
                if (sanitizedIds.isEmpty()) return@runCatching emptyMap()

                val requestUrl = "$normalizedBase/db/courses_progress/_find"
                val payload = coursesProgressRequestAdapter.toJson(
                    CoursesProgressFindRequest(
                        selector = CoursesProgressSelector(
                            userId = "org.couchdb.user:${credentials.username}",
                            courseId = CourseInSelector(included = sanitizedIds),
                            stepNum = stepNum
                        ),
                        limit = 50000
                    )
                )
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val request = Request.Builder()
                    .url(requestUrl)
                    .post(payload.toRequestBody(mediaType))
                    .addHeader("Authorization", Credentials.basic(credentials.username, credentials.password))
                    .build()

                val docs = withContext(Dispatchers.IO) {
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            response.body.string()
                            throw IOException("Unexpected response ${response.code}")
                        }
                        val parsed = coursesProgressResponseAdapter.fromJson(response.body.string())
                            ?: throw IOException("Invalid response body")
                        parsed.docs.filter { !it.courseId.isNullOrBlank() && it.stepNum != null }
                    }
                }
                if (stepNum != null) {
                    docs.associateBy { it.courseId!! }
                } else {
                    docs
                        .groupBy { it.courseId!! }
                        .mapValues { entry ->
                            entry.value.maxByOrNull { doc -> doc.stepNum ?: 0 }!!
                        }
                }
            }
        }
    }

    suspend fun saveCourseProgress(
        baseUrl: String,
        credentials: StoredCredentials,
        documents: List<CourseProgressUpdateDocument>
    ): Result<List<BulkDocResult>> {
        return withContext(dispatcher) {
            runCatching {
                val normalizedBase = baseUrl.trim().trimEnd('/')
                if (normalizedBase.isEmpty()) {
                    throw IOException("Missing server base URL")
                }
                if (documents.isEmpty()) return@runCatching emptyList()

                val requestUrl = "$normalizedBase/db/courses_progress/_bulk_docs"
                val payload = coursesProgressBulkAdapter.toJson(CoursesProgressBulkRequest(docs = documents))
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val request = Request.Builder()
                    .url(requestUrl)
                    .post(payload.toRequestBody(mediaType))
                    .addHeader("Authorization", Credentials.basic(credentials.username, credentials.password))
                    .build()

                client.newCall(request).execute().use { response ->
                    val responseBody = response.body.string()
                    if (!response.isSuccessful) {
                        throw IOException("Unexpected response ${response.code}")
                    }
                    bulkDocsResultAdapter.fromJson(responseBody)
                        ?: throw IOException("Invalid response body")
                }
            }
        }
    }

    suspend fun fetchCoursesByParent(
        baseUrl: String,
        credentials: StoredCredentials,
        excludedCourseIds: List<String>,
        skip: Int,
        limit: Int
    ): Result<PagedCourses> {
        return withContext(dispatcher) {
            runCatching {
                val normalizedBase = baseUrl.trim().trimEnd('/')
                if (normalizedBase.isEmpty()) {
                    throw IOException("Missing server base URL")
                }

                val courseIdFilter = excludedCourseIds.takeIf { it.isNotEmpty() }
                    ?.let { CourseIdFilter(gt = null, notIn = it) }

                val requestUrl = "$normalizedBase/db/courses/_find"
                val payload = coursesFindRequestAdapter.toJson(
                    CoursesFindRequest(
                        selector = CoursesSelector(id = courseIdFilter),
                        limit = limit,
                        skip = skip
                    )
                )
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val request = Request.Builder()
                    .url(requestUrl)
                    .post(payload.toRequestBody(mediaType))
                    .addHeader("Authorization", Credentials.basic(credentials.username, credentials.password))
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Unexpected response ${response.code}")
                    }
                    val parsed = coursesFindResponseAdapter.fromJson(response.body.string())
                        ?: throw IOException("Invalid response body")
                    val documents = parsed.docs
                        .filter { !it.id.isNullOrBlank() }
                        .distinctBy { it.id }
                    PagedCourses(
                        courses = documents,
                        fetchedCount = documents.size,
                        hasMore = parsed.docs.size >= limit
                    )
                }
            }
        }
    }

    suspend fun fetchTeamCourses(
        baseUrl: String,
        credentials: StoredCredentials,
        teamId: String
    ): Result<List<CourseDocument>> {
        return withContext(dispatcher) {
            runCatching {
                val normalizedBase = baseUrl.trim().trimEnd('/')
                if (normalizedBase.isEmpty()) {
                    throw IOException("Missing server base URL")
                }
                val sanitizedId = teamId.takeIf { it.isNotBlank() }
                    ?: throw IOException("Missing team id")

                val requestUrl = "$normalizedBase/db/teams/_find"
                val payload = teamCoursesRequestAdapter.toJson(
                    TeamCoursesFindRequest(
                        selector = TeamCoursesSelector(
                            status = "active",
                            type = "team",
                            teamType = "local",
                            id = TeamIdsSelector(listOf(sanitizedId))
                        )
                    )
                )
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val request = Request.Builder()
                    .url(requestUrl)
                    .post(payload.toRequestBody(mediaType))
                    .addHeader("Authorization", Credentials.basic(credentials.username, credentials.password))
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Unexpected response ${response.code}")
                    }
                    val parsed = teamCoursesResponseAdapter.fromJson(response.body.string())
                        ?: throw IOException("Invalid response body")
                    parsed.docs.flatMap { it.courses ?: emptyList() }
                }
            }
        }
    }

    suspend fun fetchCourseTags(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?
    ): Result<List<TagDocument>> {
        return withContext(dispatcher) {
            runCatching {
                val normalizedBase = baseUrl.trim().trimEnd('/')
                if (normalizedBase.isEmpty()) {
                    throw IOException("Missing server base URL")
                }
                val payload = tagsFindRequestAdapter.toJson(
                    TagsFindRequest(
                        selector = TagsSelector(
                            db = "courses",
                            docType = "definition"
                        )
                    )
                )
                val request = Request.Builder()
                    .url("$normalizedBase/db/tags/_find")
                    .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .apply {
                        credentials?.let {
                            addHeader("Authorization", Credentials.basic(it.username, it.password))
                        }
                        sessionCookie?.takeIf { it.isNotBlank() }?.let { cookie ->
                            addHeader("Cookie", cookie)
                        }
                    }
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Unexpected response ${response.code}")
                    }
                    val body = response.body.string()
                    tagsFindResponseAdapter.fromJson(body)?.docs ?: emptyList()
                }
            }
        }
    }

    suspend fun fetchTagLinks(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        tagId: String
    ): Result<List<TagLinkDocument>> {
        return withContext(dispatcher) {
            runCatching {
                val normalizedBase = baseUrl.trim().trimEnd('/')
                if (normalizedBase.isEmpty()) {
                    throw IOException("Missing server base URL")
                }
                val payload = tagLinksFindRequestAdapter.toJson(
                    TagLinksFindRequest(
                        selector = TagLinksSelector(
                            db = "courses",
                            docType = "link",
                            tagId = tagId
                        )
                    )
                )
                val request = Request.Builder()
                    .url("$normalizedBase/db/tags/_find")
                    .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .apply {
                        credentials?.let {
                            addHeader("Authorization", Credentials.basic(it.username, it.password))
                        }
                        sessionCookie?.takeIf { it.isNotBlank() }?.let { cookie ->
                            addHeader("Cookie", cookie)
                        }
                    }
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Unexpected response ${response.code}")
                    }
                    val body = response.body.string()
                    tagLinksFindResponseAdapter.fromJson(body)?.docs ?: emptyList()
                }
            }
        }
    }

    @JsonClass(generateAdapter = true)
    data class ShelfFindRequest(val selector: Map<String, Any>)

    @JsonClass(generateAdapter = true)
    data class ShelfFindResponse(val docs: List<ShelfDocument> = emptyList())

    @JsonClass(generateAdapter = true)
    data class ShelfDocument(
        @param:Json(name = "_id") val id: String?,
        @param:Json(name = "_rev") val rev: String? = null,
        val courseIds: List<String> = emptyList()
    )

    @JsonClass(generateAdapter = true)
    data class CourseDocument(
        @param:Json(name = "_id") val id: String?,
        val courseTitle: String?,
        val description: String?,
        val cover: String? = null,
        val steps: List<CourseStep> = emptyList()
    )

    @JsonClass(generateAdapter = true)
    data class CourseStep(
        val stepTitle: String?,
        val description: String? = null,
        val resources: List<CourseResource>? = emptyList(),
        val survey: SurveyDocument? = null,
        val exam: SurveyDocument? = null
    )

    @JsonClass(generateAdapter = true)
    data class CourseResource(
        @param:Json(name = "_id") val id: String? = null,
        @param:Json(name = "_attachments") val attachments: Map<String, Attachment> = emptyMap(),
        val filename: String? = null,
        val mediaType: String?
    )

    @JsonClass(generateAdapter = true)
    data class Attachment(val contentType: String? = null)

    @JsonClass(generateAdapter = true)
    data class CoursesProgressFindRequest(val selector: CoursesProgressSelector, val limit: Int? = null)

    @JsonClass(generateAdapter = true)
    data class CoursesProgressSelector(
        val userId: String,
        val courseId: CourseInSelector,
        val stepNum: Int? = null
    )

    @JsonClass(generateAdapter = true)
    data class CourseInSelector(
        @param:Json(name = $$"$in") val included: List<String>
    )

    @JsonClass(generateAdapter = true)
    data class CoursesProgressResponse(val docs: List<CourseProgressDocument> = emptyList())

    @JsonClass(generateAdapter = true)
    data class CourseProgressDocument(
        @param:Json(name = "_id") val id: String? = null,
        @param:Json(name = "_rev") val rev: String? = null,
        val courseId: String?,
        val stepNum: Int?,
        val passed: Boolean? = null,
        val createdDate: Long? = null,
        val updatedDate: Long? = null,
        val createdOn: String? = null,
        val parentCode: String? = null
    )

    @JsonClass(generateAdapter = true)
    data class CoursesProgressBulkRequest(val docs: List<CourseProgressUpdateDocument>)

    @JsonClass(generateAdapter = true)
    data class CourseProgressUpdateDocument(
        @param:Json(name = "_id") val id: String? = null,
        @param:Json(name = "_rev") val rev: String? = null,
        val userId: String,
        val courseId: String,
        val stepNum: Int,
        val passed: Boolean,
        val createdOn: String? = null,
        val parentCode: String? = null,
        val createdDate: Long,
        val updatedDate: Long
    )

    typealias CoursesProgressBulkResponse = List<BulkDocResult>

    @JsonClass(generateAdapter = true)
    data class BulkDocResult(
        val ok: Boolean? = null,
        val id: String? = null,
        val rev: String? = null,
        val error: String? = null,
        val reason: String? = null
    )

    @JsonClass(generateAdapter = true)
    data class CoursesFindRequest(
        val selector: CoursesSelector,
        val limit: Int,
        val skip: Int
    )

    @JsonClass(generateAdapter = true)
    data class CoursesSelector(
        @param:Json(name = "_id") val id: CourseIdFilter? = null
    )

    @JsonClass(generateAdapter = true)
    data class CourseIdFilter(
        @param:Json(name = $$"$gt") val gt: Any? = null,
        @param:Json(name = $$"$nin") val notIn: List<String>? = null,
        @param:Json(name = $$"$in") val inList: List<String>? = null
    )

    @JsonClass(generateAdapter = true)
    data class CourseFindResponse(val docs: List<CourseDocument> = emptyList())

    @JsonClass(generateAdapter = true)
    data class TeamCoursesFindRequest(val selector: TeamCoursesSelector)

    @JsonClass(generateAdapter = true)
    data class TeamCoursesSelector(
        val status: String,
        val type: String,
        val teamType: String,
        @param:Json(name = "_id") val id: TeamIdsSelector
    )

    @JsonClass(generateAdapter = true)
    data class TeamIdsSelector(@param:Json(name = $$"$in") val ids: List<String>)

    @JsonClass(generateAdapter = true)
    data class TeamCoursesResponse(val docs: List<TeamDocument> = emptyList())

    @JsonClass(generateAdapter = true)
    data class TeamDocument(val courses: List<CourseDocument>? = null)

    @JsonClass(generateAdapter = true)
    data class TagsFindRequest(val selector: TagsSelector)

    @JsonClass(generateAdapter = true)
    data class TagsSelector(
        val db: String,
        val docType: String
    )

    @JsonClass(generateAdapter = true)
    data class TagsFindResponse(val docs: List<TagDocument>?)

    @JsonClass(generateAdapter = true)
    data class TagDocument(
        @param:Json(name = "_id") val id: String?,
        val name: String?
    )

    @JsonClass(generateAdapter = true)
    data class TagLinksFindRequest(val selector: TagLinksSelector)

    @JsonClass(generateAdapter = true)
    data class TagLinksSelector(
        val db: String,
        val docType: String,
        val tagId: String
    )

    @JsonClass(generateAdapter = true)
    data class TagLinksFindResponse(val docs: List<TagLinkDocument>?)

    @JsonClass(generateAdapter = true)
    data class TagLinkDocument(
        @param:Json(name = "_id") val id: String?,
        val linkId: String?
    )

    data class PagedCourses(
        val courses: List<CourseDocument>,
        val fetchedCount: Int,
        val hasMore: Boolean
    )

    @JsonClass(generateAdapter = true)
    data class AllDocsRequest(val keys: List<String>)

    @JsonClass(generateAdapter = true)
    data class AllDocsResponse(val rows: List<AllDocsRow> = emptyList())

    @JsonClass(generateAdapter = true)
    data class AllDocsRow(val doc: CourseDocument? = null)

    data class DownloadResource(
        val id: String,
        val filename: String
    )

    private fun buildServerResourceUrl(base: String, resourceId: String, filename: String): String? {
        val normalizedBase = base.trim().trimEnd('/').takeIf { it.isNotEmpty() } ?: return null
        val parsed = normalizedBase.toUri()
        val scheme = parsed.scheme ?: return null
        val authority = parsed.encodedAuthority ?: return null
        return parsed.buildUpon()
            .scheme(scheme)
            .encodedAuthority(authority)
            .appendPath("db")
            .appendPath("resources")
            .appendPath(resourceId)
            .appendPath(filename)
            .build()
            .toString()
    }

    suspend fun estimateResourcesSize(
        base: String,
        creds: StoredCredentials,
        resources: List<DownloadResource>
    ): Long = withContext(dispatcher) {
        coroutineScope {
            resources.map { resource ->
                async {
                    val url = buildServerResourceUrl(base, resource.id, resource.filename) ?: return@async 0L
                    val requestBuilder = Request.Builder()
                        .url(url)
                        .head()
                    if (url.startsWith("https://", ignoreCase = true)) {
                        requestBuilder.header("Authorization", Credentials.basic(creds.username, creds.password))
                    }
                    val request = requestBuilder.build()
                    runCatching {
                        client.newCall(request).execute().use { response ->
                            response.header("Content-Length")?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
                        }
                    }.getOrDefault(0L)
                }
            }.awaitAll().sum()
        }
    }

    suspend fun estimateMarkdownImagesSize(
        base: String,
        creds: StoredCredentials,
        sources: List<String>
    ): Long = withContext(dispatcher) {
        val authHeader = Credentials.basic(creds.username, creds.password)
        coroutineScope {
            sources.map { source ->
                async {
                    val url = MarkdownUtils.resolveMarkdownSourceUrl(base, source) ?: return@async 0L
                    val requestBuilder = Request.Builder()
                        .url(url)
                        .head()
                    if (url.startsWith("https://", ignoreCase = true)) {
                        requestBuilder.header("Authorization", authHeader)
                    }
                    val request = requestBuilder.build()
                    runCatching {
                        client.newCall(request).execute().use { response ->
                            response.header("Content-Length")?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
                        }
                    }.getOrDefault(0L)
                }
            }.awaitAll().sum()
        }
    }

    suspend fun downloadCourseResources(
        base: String,
        creds: StoredCredentials,
        resources: List<DownloadResource>,
        markdownImageSources: List<String>,
        getResourceTarget: (String, String) -> File,
        getMarkdownTarget: (String) -> File,
        onProgress: (Pair<Int, Int>) -> Unit
    ): Boolean = withContext(dispatcher) {
        val totalItems = resources.size + markdownImageSources.size
        if (totalItems == 0) {
            onProgress(0 to 0)
            return@withContext true
        }
        var downloaded = 0
        val progressMutex = Mutex()
        val authHeader = Credentials.basic(creds.username, creds.password)

        coroutineScope {
            val resourceJobs = resources.map { resource ->
                async {
                    val url = buildServerResourceUrl(base, resource.id, resource.filename) ?: return@async false
                    val target = getResourceTarget(resource.id, resource.filename)
                    target.parentFile?.mkdirs()
                    val requestBuilder = Request.Builder()
                        .url(url)
                    if (url.startsWith("https://", ignoreCase = true)) {
                        requestBuilder.header("Authorization", authHeader)
                    }
                    val request = requestBuilder.build()
                    val success = runCatching {
                        client.newCall(request).execute().use { response ->
                            if (!response.isSuccessful) return@use false
                            val body = response.body
                            body.byteStream().use { input ->
                                target.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            true
                        }
                    }.getOrDefault(false)
                    if (!success) return@async false
                    progressMutex.withLock {
                        downloaded += 1
                        onProgress(downloaded to totalItems)
                    }
                    true
                }
            }

            val markdownJobs = markdownImageSources.map { source ->
                async {
                    val resolvedUrl = MarkdownUtils.resolveMarkdownSourceUrl(base, source) ?: return@async true
                    val target = getMarkdownTarget(source)
                    target.parentFile?.mkdirs()
                    val requestBuilder = Request.Builder()
                        .url(resolvedUrl)
                    if (resolvedUrl.startsWith("https://", ignoreCase = true)) {
                        requestBuilder.header("Authorization", authHeader)
                    }
                    val request = requestBuilder.build()
                    runCatching {
                        client.newCall(request).execute().use { response ->
                            if (!response.isSuccessful) return@use false
                            response.body.byteStream().use { input ->
                                target.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            true
                        }
                    }.getOrDefault(false)
                    progressMutex.withLock {
                        downloaded += 1
                        onProgress(downloaded to totalItems)
                    }
                    true
                }
            }

            val resourceResults = resourceJobs.awaitAll()
            // Wait for markdown jobs to complete
            markdownJobs.awaitAll()

            // Return false if any resource download failed
            !resourceResults.contains(false)
        }
    }
}
