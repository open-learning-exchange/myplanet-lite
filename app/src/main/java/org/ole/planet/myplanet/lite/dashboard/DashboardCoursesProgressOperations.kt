/*
Author: Walfre López Prado
Email: loppra@plataformasinformaticas.com
Creation date: 2026-08-09
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
import java.io.File
import java.io.IOException

suspend fun DashboardCoursesRepository.fetchCoursesImpl(
    baseUrl: String,
    credentials: StoredCredentials,
    courseIds: List<String>,
    forceRefresh: Boolean = false,
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
            if (!forceRefresh) {
                uniqueIds.forEach { id ->
                    courseCache[id]?.let { cachedDocuments[id] = it }
                }
            }

            val remainingIds = uniqueIds.filterNot { cachedDocuments.containsKey(it) }
            if (remainingIds.isNotEmpty()) {
                val requestUrl = "$normalizedBase/db/courses/_all_docs?include_docs=true"
                val payload = allDocsRequestAdapter.toJson(AllDocsRequest(keys = remainingIds))
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val request =
                    Request
                        .Builder()
                        .url(requestUrl)
                        .post(payload.toRequestBody(mediaType))
                        .addHeader("Authorization", Credentials.basic(credentials.username, credentials.password))
                        .apply {
                            if (forceRefresh) header("Cache-Control", "no-cache")
                        }
                        .build()

                client.newCall(request).await().use { response ->
                    if (!response.isSuccessful) {
                        response.body.string()
                        throw IOException("Unexpected response ${response.code}")
                    }
                    val parsed =
                        allDocsResponseAdapter.fromJson(response.body.string())
                            ?: throw IOException("Invalid response body")

                    parsed.rows.mapNotNull { it.doc }.forEach { document ->
                        val id = document.id ?: return@forEach
                        courseCache[id] = document
                        cachedDocuments[id] = document
                    }
                }
            }

            val orderedResults =
                uniqueIds.mapNotNull { id ->
                    cachedDocuments[id]
                }
            orderedResults
        }
    }
}


suspend fun DashboardCoursesRepository.fetchCoursesProgressImpl(
    baseUrl: String,
    credentials: StoredCredentials,
    courseIds: List<String>,
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
            val payload =
                coursesProgressRequestAdapter.toJson(
                    CoursesProgressFindRequest(
                        selector =
                            CoursesProgressSelector(
                                userId = "org.couchdb.user:${credentials.username}",
                                courseId = CourseInSelector(included = sanitizedIds),
                            ),
                        limit = 50000,
                    ),
                )
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val request =
                Request
                    .Builder()
                    .url(requestUrl)
                    .post(payload.toRequestBody(mediaType))
                    .addHeader("Authorization", Credentials.basic(credentials.username, credentials.password))
                    .build()

            val docs =
                client.newCall(request).await().use { response ->
                    if (!response.isSuccessful) {
                        response.body.string()
                        throw IOException("Unexpected response ${response.code}")
                    }
                    val parsed =
                        coursesProgressResponseAdapter.fromJson(response.body.string())
                            ?: throw IOException("Invalid response body")
                    parsed.docs
                        .filter { !it.courseId.isNullOrBlank() && it.stepNum != null }
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


suspend fun DashboardCoursesRepository.fetchCoursesProgressDocumentsImpl(
    baseUrl: String,
    credentials: StoredCredentials,
    courseIds: List<String>,
    stepNum: Int? = null,
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
            val payload =
                coursesProgressRequestAdapter.toJson(
                    CoursesProgressFindRequest(
                        selector =
                            CoursesProgressSelector(
                                userId = "org.couchdb.user:${credentials.username}",
                                courseId = CourseInSelector(included = sanitizedIds),
                                stepNum = stepNum,
                            ),
                        limit = 50000,
                    ),
                )
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val request =
                Request
                    .Builder()
                    .url(requestUrl)
                    .post(payload.toRequestBody(mediaType))
                    .addHeader("Authorization", Credentials.basic(credentials.username, credentials.password))
                    .build()

            val docs =
                client.newCall(request).await().use { response ->
                    if (!response.isSuccessful) {
                        response.body.string()
                        throw IOException("Unexpected response ${response.code}")
                    }
                    val parsed =
                        coursesProgressResponseAdapter.fromJson(response.body.string())
                            ?: throw IOException("Invalid response body")
                    parsed.docs.filter { !it.courseId.isNullOrBlank() && it.stepNum != null }
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


suspend fun DashboardCoursesRepository.saveCourseProgressImpl(
    baseUrl: String,
    credentials: StoredCredentials,
    documents: List<CourseProgressUpdateDocument>,
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
            val request =
                Request
                    .Builder()
                    .url(requestUrl)
                    .post(payload.toRequestBody(mediaType))
                    .addHeader("Authorization", Credentials.basic(credentials.username, credentials.password))
                    .build()

            client.newCall(request).await().use { response ->
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


suspend fun DashboardCoursesRepository.fetchCoursesByParentImpl(
    baseUrl: String,
    credentials: StoredCredentials,
    excludedCourseIds: List<String>,
    skip: Int,
    limit: Int,
): Result<PagedCourses> =
    withContext(dispatcher) {
        runCatching {
            val normalizedBase = baseUrl.trim().trimEnd('/')
            if (normalizedBase.isEmpty()) {
                throw IOException("Missing server base URL")
            }

            val courseIdFilter =
                excludedCourseIds
                    .takeIf { it.isNotEmpty() }
                    ?.let { CourseIdFilter(gt = null, notIn = it) }

            val requestUrl = "$normalizedBase/db/courses/_find"
            val payload =
                coursesFindRequestAdapter.toJson(
                    CoursesFindRequest(
                        selector = CoursesSelector(id = courseIdFilter),
                        limit = limit,
                        skip = skip,
                    ),
                )
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val request =
                Request
                    .Builder()
                    .url(requestUrl)
                    .post(payload.toRequestBody(mediaType))
                    .addHeader("Authorization", Credentials.basic(credentials.username, credentials.password))
                    .build()

            client.newCall(request).await().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Unexpected response ${response.code}")
                }
                val parsed =
                    coursesFindResponseAdapter.fromJson(response.body.string())
                        ?: throw IOException("Invalid response body")
                val documents =
                    parsed.docs
                        .filter { !it.id.isNullOrBlank() }
                        .distinctBy { it.id }
                PagedCourses(
                    courses = documents,
                    fetchedCount = documents.size,
                    hasMore = parsed.docs.size >= limit,
                )
            }
        }
    }




