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

suspend fun DashboardCoursesRepository.fetchTeamCoursesImpl(
    baseUrl: String,
    credentials: StoredCredentials,
    teamId: String,
    forceRefresh: Boolean = false,
): Result<List<CourseDocument>> =
    withContext(dispatcher) {
        runCatching {
            val normalizedBase = baseUrl.trim().trimEnd('/')
            if (normalizedBase.isEmpty()) {
                throw IOException("Missing server base URL")
            }
            val sanitizedId =
                teamId.takeIf { it.isNotBlank() }
                    ?: throw IOException("Missing team id")

            val requestUrl = "$normalizedBase/db/teams/_find"
            val payload =
                teamCoursesRequestAdapter.toJson(
                    TeamCoursesFindRequest(
                        selector =
                            TeamCoursesSelector(
                                status = "active",
                                type = "team",
                                teamType = "local",
                                id = TeamIdsSelector(listOf(sanitizedId)),
                            ),
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
                    teamCoursesResponseAdapter.fromJson(response.body.string())
                        ?: throw IOException("Invalid response body")
                val embeddedCourses = parsed.docs.flatMap { it.courses ?: emptyList() }
                val courseIds = embeddedCourses.mapNotNull { it.id }.distinct()
                if (courseIds.isEmpty()) {
                    emptyList()
                } else {
                    fetchCourses(
                        normalizedBase,
                        credentials,
                        courseIds,
                        forceRefresh = forceRefresh,
                    ).getOrElse { throw it }
                }
            }
        }
    }


suspend fun DashboardCoursesRepository.fetchCourseTagsImpl(
    baseUrl: String,
    credentials: StoredCredentials?,
    sessionCookie: String?,
): Result<List<TagDocument>> =
    withContext(dispatcher) {
        runCatching {
            val normalizedBase = baseUrl.trim().trimEnd('/')
            if (normalizedBase.isEmpty()) {
                throw IOException("Missing server base URL")
            }
            val payload =
                tagsFindRequestAdapter.toJson(
                    TagsFindRequest(
                        selector =
                            TagsSelector(
                                db = "courses",
                                docType = "definition",
                            ),
                    ),
                )
            val request =
                Request
                    .Builder()
                    .url("$normalizedBase/db/tags/_find")
                    .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .apply {
                        credentials?.let {
                            addHeader("Authorization", Credentials.basic(it.username, it.password))
                        }
                        sessionCookie?.takeIf { it.isNotBlank() }?.let { cookie ->
                            addHeader("Cookie", cookie)
                        }
                    }.build()
            client.newCall(request).await().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Unexpected response ${response.code}")
                }
                val body = response.body.string()
                tagsFindResponseAdapter.fromJson(body)?.docs ?: emptyList()
            }
        }
    }


suspend fun DashboardCoursesRepository.fetchTagLinksImpl(
    baseUrl: String,
    credentials: StoredCredentials?,
    sessionCookie: String?,
    tagId: String,
): Result<List<TagLinkDocument>> =
    withContext(dispatcher) {
        runCatching {
            val normalizedBase = baseUrl.trim().trimEnd('/')
            if (normalizedBase.isEmpty()) {
                throw IOException("Missing server base URL")
            }
            val payload =
                tagLinksFindRequestAdapter.toJson(
                    TagLinksFindRequest(
                        selector =
                            TagLinksSelector(
                                db = "courses",
                                docType = "link",
                                tagId = tagId,
                            ),
                    ),
                )
            val request =
                Request
                    .Builder()
                    .url("$normalizedBase/db/tags/_find")
                    .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .apply {
                        credentials?.let {
                            addHeader("Authorization", Credentials.basic(it.username, it.password))
                        }
                        sessionCookie?.takeIf { it.isNotBlank() }?.let { cookie ->
                            addHeader("Cookie", cookie)
                        }
                    }.build()
            client.newCall(request).await().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Unexpected response ${response.code}")
                }
                val body = response.body.string()
                tagLinksFindResponseAdapter.fromJson(body)?.docs ?: emptyList()
            }
        }
    }


internal fun DashboardCoursesRepository.buildServerResourceUrlImpl(
    base: String,
    resourceId: String,
    filename: String,
): String? {
    val normalizedBase = base.trim().trimEnd('/').takeIf { it.isNotEmpty() } ?: return null
    val parsed = normalizedBase.toHttpUrlOrNull() ?: return null
    return parsed
        .newBuilder()
        .addPathSegment("db")
        .addPathSegment("resources")
        .addPathSegment(resourceId)
        .addPathSegment(filename)
        .build()
        .toString()
}


suspend fun DashboardCoursesRepository.estimateResourcesSizeImpl(
    base: String,
    creds: StoredCredentials,
    resources: List<DownloadResource>,
): Long =
    withContext(dispatcher) {
        coroutineScope {
            resources
                .map { resource ->
                    async {
                        val url = buildServerResourceUrl(base, resource.id, resource.filename) ?: return@async 0L
                        val requestBuilder =
                            Request
                                .Builder()
                                .url(url)
                                .head()
                        if (url.startsWith("https://", ignoreCase = true)) {
                            requestBuilder.header("Authorization", Credentials.basic(creds.username, creds.password))
                        }
                        val request = requestBuilder.build()
                        runCatching {
                            client.newCall(request).await().use { response ->
                                response.header("Content-Length")?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
                            }
                        }.getOrDefault(0L)
                    }
                }.awaitAll()
                .sum()
        }
    }


suspend fun DashboardCoursesRepository.estimateMarkdownImagesSizeImpl(
    base: String,
    creds: StoredCredentials,
    sources: List<String>,
): Long =
    withContext(dispatcher) {
        val authHeader = Credentials.basic(creds.username, creds.password)
        coroutineScope {
            sources
                .map { source ->
                    async {
                        val url = MarkdownUtils.resolveMarkdownSourceUrl(base, source) ?: return@async 0L
                        val requestBuilder =
                            Request
                                .Builder()
                                .url(url)
                                .head()
                        if (url.startsWith("https://", ignoreCase = true)) {
                            requestBuilder.header("Authorization", authHeader)
                        }
                        val request = requestBuilder.build()
                        runCatching {
                            client.newCall(request).await().use { response ->
                                response.header("Content-Length")?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
                            }
                        }.getOrDefault(0L)
                    }
                }.awaitAll()
                .sum()
        }
    }


suspend fun DashboardCoursesRepository.estimateCourseCoverSizeImpl(
    base: String,
    creds: StoredCredentials,
    coverPath: String?,
): Long = withContext(dispatcher) {
    val url = buildCourseCoverUrl(base, coverPath) ?: return@withContext 0L
    val request = Request.Builder()
        .url(url)
        .head()
        .header("Authorization", Credentials.basic(creds.username, creds.password))
        .build()
    runCatching {
        client.newCall(request).await().use { response ->
            response.header("Content-Length")?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
        }
    }.getOrDefault(0L)
}


internal fun DashboardCoursesRepository.buildCourseCoverUrlImpl(base: String, coverPath: String?): String? {
    val path = coverPath?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (path.startsWith("file:", ignoreCase = true)) return null
    val parsed = base.trim().trimEnd('/').toHttpUrlOrNull() ?: return null
    return parsed.newBuilder()
        .addPathSegment("db")
        .addEncodedPathSegments(path.trimStart('/'))
        .build()
        .toString()
}


suspend fun DashboardCoursesRepository.downloadCourseResourcesImpl(
    base: String,
    creds: StoredCredentials,
    resources: List<DownloadResource>,
    markdownImageSources: List<String>,
    getResourceTarget: (String, String) -> File,
    getMarkdownTarget: (String) -> File,
    coverPath: String? = null,
    getCoverTarget: ((String) -> File)? = null,
    onProgress: (Pair<Int, Int>) -> Unit,
): Boolean =
    withContext(dispatcher) {
        val hasCover = !coverPath.isNullOrBlank() && getCoverTarget != null
        val totalItems = resources.size + markdownImageSources.size + if (hasCover) 1 else 0
        if (totalItems == 0) {
            onProgress(0 to 0)
            return@withContext true
        }
        var downloaded = 0
        val progressMutex = Mutex()
        val authHeader = Credentials.basic(creds.username, creds.password)

        coroutineScope {
            val resourceJobs =
                resources.map { resource ->
                    async {
                        val url = buildServerResourceUrl(base, resource.id, resource.filename) ?: return@async false
                        val target = getResourceTarget(resource.id, resource.filename)
                        target.parentFile?.mkdirs()
                        val requestBuilder =
                            Request
                                .Builder()
                                .url(url)
                        if (url.startsWith("https://", ignoreCase = true)) {
                            requestBuilder.header("Authorization", authHeader)
                        }
                        val request = requestBuilder.build()
                        val success =
                            runCatching {
                                client.newCall(request).await().use { response ->
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

            val markdownJobs =
                markdownImageSources.map { source ->
                    async {
                        val resolvedUrl = MarkdownUtils.resolveMarkdownSourceUrl(base, source) ?: return@async true
                        val target = getMarkdownTarget(source)
                        target.parentFile?.mkdirs()
                        val requestBuilder =
                            Request
                                .Builder()
                                .url(resolvedUrl)
                        if (resolvedUrl.startsWith("https://", ignoreCase = true)) {
                            requestBuilder.header("Authorization", authHeader)
                        }
                        val request = requestBuilder.build()
                        runCatching {
                            client.newCall(request).await().use { response ->
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

            val coverJob = if (hasCover) {
                async {
                    val source = requireNotNull(coverPath)
                    val url = buildCourseCoverUrl(base, source) ?: return@async false
                    val target = requireNotNull(getCoverTarget).invoke(source)
                    target.parentFile?.mkdirs()
                    val request = Request.Builder()
                        .url(url)
                        .header("Authorization", authHeader)
                        .header("Cache-Control", "no-cache")
                        .build()
                    val success = runCatching {
                        client.newCall(request).await().use { response ->
                            if (!response.isSuccessful) return@use false
                            response.body.byteStream().use { input ->
                                target.outputStream().use { output -> input.copyTo(output) }
                            }
                            true
                        }
                    }.getOrDefault(false)
                    if (success) {
                        progressMutex.withLock {
                            downloaded += 1
                            onProgress(downloaded to totalItems)
                        }
                    }
                    success
                }
            } else {
                null
            }

            val resourceResults = resourceJobs.awaitAll()
            // Wait for markdown jobs to complete
            markdownJobs.awaitAll()

            // Return false if any resource download failed
            !resourceResults.contains(false) && coverJob?.await() != false
        }
    }


