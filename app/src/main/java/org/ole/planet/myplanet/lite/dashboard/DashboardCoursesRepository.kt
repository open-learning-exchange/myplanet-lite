/*
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-12-28
 */

package org.ole.planet.myplanet.lite.dashboard

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyDocument
import org.ole.planet.myplanet.lite.profile.StoredCredentials
import org.ole.planet.myplanet.lite.util.MarkdownUtils
import org.ole.planet.myplanet.lite.util.PlanetAppIdentity
import java.io.File
import java.io.IOException

class DashboardCoursesRepository(
    internal val client: OkHttpClient = OkHttpClient.Builder().build(),
    internal val moshi: Moshi =
        Moshi
            .Builder()
            .add(FlexibleSurveyJsonAdapter())
            .addLast(KotlinJsonAdapterFactory())
            .build(),
    internal val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    internal val findRequestAdapter = moshi.adapter(ShelfFindRequest::class.java)
    internal val findResponseAdapter = moshi.adapter(ShelfFindResponse::class.java)
    internal val shelfDocumentAdapter = moshi.adapter(ShelfDocument::class.java)
    internal val coursesProgressRequestAdapter = moshi.adapter(CoursesProgressFindRequest::class.java)
    internal val coursesProgressResponseAdapter = moshi.adapter(CoursesProgressResponse::class.java)
    internal val coursesProgressBulkAdapter = moshi.adapter(CoursesProgressBulkRequest::class.java)
    internal val bulkDocsResultAdapter =
        moshi.adapter<List<BulkDocResult>>(
            com.squareup.moshi.Types
                .newParameterizedType(List::class.java, BulkDocResult::class.java),
        )
    internal val allDocsRequestAdapter = moshi.adapter(AllDocsRequest::class.java)
    internal val allDocsResponseAdapter = moshi.adapter(AllDocsResponse::class.java)
    internal val coursesFindRequestAdapter = moshi.adapter(CoursesFindRequest::class.java)
    internal val coursesFindResponseAdapter = moshi.adapter(CourseFindResponse::class.java)
    internal val teamCoursesRequestAdapter = moshi.adapter(TeamCoursesFindRequest::class.java)
    internal val teamCoursesResponseAdapter = moshi.adapter(TeamCoursesResponse::class.java)
    internal val tagsFindRequestAdapter = moshi.adapter(TagsFindRequest::class.java)
    internal val tagsFindResponseAdapter = moshi.adapter(TagsFindResponse::class.java)
    internal val tagLinksFindRequestAdapter = moshi.adapter(TagLinksFindRequest::class.java)
    internal val tagLinksFindResponseAdapter = moshi.adapter(TagLinksFindResponse::class.java)
    internal val courseCache = mutableMapOf<String, CourseDocument>()
    internal var shelfCache: ShelfDocument? = null

    fun clearCourseCache() = clearCourseCacheImpl()

    suspend fun fetchUserCourseIds(
        baseUrl: String,
        credentials: StoredCredentials,
    ): Result<List<String>> = fetchUserCourseIdsImpl(baseUrl, credentials)

    suspend fun fetchShelfDocument(
        baseUrl: String,
        credentials: StoredCredentials,
    ): Result<ShelfDocument> = fetchShelfDocumentImpl(baseUrl, credentials)

    suspend fun joinCourse(
        baseUrl: String,
        credentials: StoredCredentials,
        courseId: String,
    ): Result<Unit> = joinCourseImpl(baseUrl, credentials, courseId)

    suspend fun leaveCourse(
        baseUrl: String,
        credentials: StoredCredentials,
        courseId: String,
    ): Result<Unit> = leaveCourseImpl(baseUrl, credentials, courseId)

    suspend fun fetchCourses(
        baseUrl: String,
        credentials: StoredCredentials,
        courseIds: List<String>,
        forceRefresh: Boolean = false,
    ): Result<List<CourseDocument>> = fetchCoursesImpl(baseUrl, credentials, courseIds, forceRefresh)

    suspend fun fetchCoursesProgress(
        baseUrl: String,
        credentials: StoredCredentials,
        courseIds: List<String>,
    ): Result<Map<String, Int>> = fetchCoursesProgressImpl(baseUrl, credentials, courseIds)

    suspend fun fetchCoursesProgressDocuments(
        baseUrl: String,
        credentials: StoredCredentials,
        courseIds: List<String>,
        stepNum: Int? = null,
    ): Result<Map<String, CourseProgressDocument>> = fetchCoursesProgressDocumentsImpl(baseUrl, credentials, courseIds, stepNum)

    suspend fun saveCourseProgress(
        baseUrl: String,
        credentials: StoredCredentials,
        documents: List<CourseProgressUpdateDocument>,
    ): Result<List<BulkDocResult>> = saveCourseProgressImpl(baseUrl, credentials, documents)

    suspend fun fetchCoursesByParent(
        baseUrl: String,
        credentials: StoredCredentials,
        excludedCourseIds: List<String>,
        skip: Int,
        limit: Int,
    ): Result<PagedCourses> = fetchCoursesByParentImpl(baseUrl, credentials, excludedCourseIds, skip, limit)

    suspend fun fetchTeamCourses(
        baseUrl: String,
        credentials: StoredCredentials,
        teamId: String,
        forceRefresh: Boolean = false,
    ): Result<List<CourseDocument>> = fetchTeamCoursesImpl(baseUrl, credentials, teamId)

    suspend fun fetchCourseTags(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
    ): Result<List<TagDocument>> = fetchCourseTagsImpl(baseUrl, credentials, sessionCookie)

    suspend fun fetchTagLinks(
        baseUrl: String,
        credentials: StoredCredentials?,
        sessionCookie: String?,
        tagId: String,
    ): Result<List<TagLinkDocument>> = fetchTagLinksImpl(baseUrl, credentials, sessionCookie, tagId)

    @JsonClass(generateAdapter = true)
    data class ShelfFindRequest(
        val selector: Map<String, Any>,
    )

    @JsonClass(generateAdapter = true)
    data class ShelfFindResponse(
        val docs: List<ShelfDocument> = emptyList(),
    )

    @JsonClass(generateAdapter = true)
    data class ShelfDocument(
        @param:Json(name = "_id") val id: String?,
        @param:Json(name = "_rev") val rev: String? = null,
        val courseIds: List<String> = emptyList(),
    )

    @JsonClass(generateAdapter = true)
    data class CourseDocument(
        @param:Json(name = "_id") val id: String?,
        val courseTitle: String?,
        val description: String?,
        val coverFileName: String? = null,
        @param:Json(name = "_attachments") val attachments: Map<String, Attachment> = emptyMap(),
        val steps: List<CourseStep> = emptyList(),
    )

    @JsonClass(generateAdapter = true)
    data class CourseStep(
        val stepTitle: String?,
        val description: String? = null,
        val resources: List<CourseResource>? = emptyList(),
        val survey: SurveyDocument? = null,
        val exam: SurveyDocument? = null,
    )

    @JsonClass(generateAdapter = true)
    data class CourseResource(
        @param:Json(name = "_id") val id: String? = null,
        @param:Json(name = "_attachments") val attachments: Map<String, Attachment> = emptyMap(),
        val filename: String? = null,
        val mediaType: String?,
    )

    @JsonClass(generateAdapter = true)
    data class Attachment(
        @param:Json(name = "content_type") val contentType: String? = null,
    )

    @JsonClass(generateAdapter = true)
    data class CoursesProgressFindRequest(
        val selector: CoursesProgressSelector,
        val limit: Int? = null,
    )

    @JsonClass(generateAdapter = true)
    data class CoursesProgressSelector(
        val userId: String,
        val courseId: CourseInSelector,
        val stepNum: Int? = null,
    )

    @JsonClass(generateAdapter = true)
    data class CourseInSelector(
        @param:Json(name = $$"$in") val included: List<String>,
    )

    @JsonClass(generateAdapter = true)
    data class CoursesProgressResponse(
        val docs: List<CourseProgressDocument> = emptyList(),
    )

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
        val parentCode: String? = null,
        val androidId: String? = null,
        @param:Json(name = PlanetAppIdentity.FIELD_NAME) val app: String? = null,
    )

    @JsonClass(generateAdapter = true)
    data class CoursesProgressBulkRequest(
        val docs: List<CourseProgressUpdateDocument>,
    )

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
        val updatedDate: Long,
        val androidId: String?,
        @param:Json(name = PlanetAppIdentity.FIELD_NAME) val app: String?,
    )

    typealias CoursesProgressBulkResponse = List<BulkDocResult>

    @JsonClass(generateAdapter = true)
    data class BulkDocResult(
        val ok: Boolean? = null,
        val id: String? = null,
        val rev: String? = null,
        val error: String? = null,
        val reason: String? = null,
    )

    @JsonClass(generateAdapter = true)
    data class CoursesFindRequest(
        val selector: CoursesSelector,
        val limit: Int,
        val skip: Int,
    )

    @JsonClass(generateAdapter = true)
    data class CoursesSelector(
        @param:Json(name = "_id") val id: CourseIdFilter? = null,
    )

    @JsonClass(generateAdapter = true)
    data class CourseIdFilter(
        @param:Json(name = $$"$gt") val gt: Any? = null,
        @param:Json(name = $$"$nin") val notIn: List<String>? = null,
        @param:Json(name = $$"$in") val inList: List<String>? = null,
    )

    @JsonClass(generateAdapter = true)
    data class CourseFindResponse(
        val docs: List<CourseDocument> = emptyList(),
    )

    @JsonClass(generateAdapter = true)
    data class TeamCoursesFindRequest(
        val selector: TeamCoursesSelector,
    )

    @JsonClass(generateAdapter = true)
    data class TeamCoursesSelector(
        val status: String,
        val type: String,
        val teamType: String,
        @param:Json(name = "_id") val id: TeamIdsSelector,
    )

    @JsonClass(generateAdapter = true)
    data class TeamIdsSelector(
        @param:Json(name = $$"$in") val ids: List<String>,
    )

    @JsonClass(generateAdapter = true)
    data class TeamCoursesResponse(
        val docs: List<TeamDocument> = emptyList(),
    )

    @JsonClass(generateAdapter = true)
    data class TeamDocument(
        val courses: List<CourseDocument>? = null,
    )

    @JsonClass(generateAdapter = true)
    data class TagsFindRequest(
        val selector: TagsSelector,
    )

    @JsonClass(generateAdapter = true)
    data class TagsSelector(
        val db: String,
        val docType: String,
    )

    @JsonClass(generateAdapter = true)
    data class TagsFindResponse(
        val docs: List<TagDocument>?,
    )

    @JsonClass(generateAdapter = true)
    data class TagDocument(
        @param:Json(name = "_id") val id: String?,
        val name: String?,
    )

    @JsonClass(generateAdapter = true)
    data class TagLinksFindRequest(
        val selector: TagLinksSelector,
    )

    @JsonClass(generateAdapter = true)
    data class TagLinksSelector(
        val db: String,
        val docType: String,
        val tagId: String,
    )

    @JsonClass(generateAdapter = true)
    data class TagLinksFindResponse(
        val docs: List<TagLinkDocument>?,
    )

    @JsonClass(generateAdapter = true)
    data class TagLinkDocument(
        @param:Json(name = "_id") val id: String?,
        val linkId: String?,
    )

    data class PagedCourses(
        val courses: List<CourseDocument>,
        val fetchedCount: Int,
        val hasMore: Boolean,
    )

    @JsonClass(generateAdapter = true)
    data class AllDocsRequest(
        val keys: List<String>,
    )

    @JsonClass(generateAdapter = true)
    data class AllDocsResponse(
        val rows: List<AllDocsRow> = emptyList(),
    )

    @JsonClass(generateAdapter = true)
    data class AllDocsRow(
        val doc: CourseDocument? = null,
    )

    data class DownloadResource(
        val id: String,
        val filename: String,
    )

    internal fun buildServerResourceUrl(
        base: String,
        resourceId: String,
        filename: String,
    ): String? = buildServerResourceUrlImpl(base, resourceId, filename)

    suspend fun estimateResourcesSize(
        base: String,
        creds: StoredCredentials,
        resources: List<DownloadResource>,
    ): Long = estimateResourcesSizeImpl(base, creds, resources)

    suspend fun estimateMarkdownImagesSize(
        base: String,
        creds: StoredCredentials,
        sources: List<String>,
    ): Long = estimateMarkdownImagesSizeImpl(base, creds, sources)

    suspend fun estimateCourseCoverSize(
        base: String,
        creds: StoredCredentials,
        coverPath: String?,
    ): Long = estimateCourseCoverSizeImpl(base, creds, coverPath)

    internal fun buildCourseCoverUrl(base: String, coverPath: String?): String? = buildCourseCoverUrlImpl(base, coverPath)

    suspend fun downloadCourseResources(
        base: String,
        creds: StoredCredentials,
        resources: List<DownloadResource>,
        markdownImageSources: List<String>,
        getResourceTarget: (String, String) -> File,
        getMarkdownTarget: (String) -> File,
        coverPath: String? = null,
        getCoverTarget: ((String) -> File)? = null,
        onProgress: (Pair<Int, Int>) -> Unit,
    ): Boolean = downloadCourseResourcesImpl(
        base,
        creds,
        resources,
        markdownImageSources,
        getResourceTarget,
        getMarkdownTarget,
        coverPath,
        getCoverTarget,
        onProgress,
    )

}
