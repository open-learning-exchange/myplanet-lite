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

fun DashboardCoursesRepository.clearCourseCacheImpl() {
    courseCache.clear()
}


suspend fun DashboardCoursesRepository.fetchUserCourseIdsImpl(
    baseUrl: String,
    credentials: StoredCredentials,
): Result<List<String>> =
    withContext(dispatcher) {
        runCatching {
            val normalizedBase = baseUrl.trim().trimEnd('/')
            if (normalizedBase.isEmpty()) {
                throw IOException("Missing server base URL")
            }
            val requestUrl = "$normalizedBase/db/shelf/_find"
            val payload =
                findRequestAdapter.toJson(
                    ShelfFindRequest(
                        selector = mapOf("_id" to "org.couchdb.user:${credentials.username}"),
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
                    response.body.string()
                    throw IOException("Unexpected response ${response.code}")
                }
                val parsed =
                    findResponseAdapter.fromJson(response.body.string())
                        ?: throw IOException("Invalid response body")
                val document = parsed.docs.firstOrNull()
                if (document != null) {
                    shelfCache = document
                }
                document?.courseIds ?: emptyList()
            }
        }
    }


suspend fun DashboardCoursesRepository.fetchShelfDocumentImpl(
    baseUrl: String,
    credentials: StoredCredentials,
): Result<ShelfDocument> {
    return withContext(dispatcher) {
        runCatching {
            val normalizedBase = baseUrl.trim().trimEnd('/')
            if (normalizedBase.isEmpty()) {
                throw IOException("Missing server base URL")
            }

            val requestUrl = "$normalizedBase/db/shelf/org.couchdb.user:${credentials.username}"
            val request =
                Request
                    .Builder()
                    .url(requestUrl)
                    .get()
                    .addHeader("Authorization", Credentials.basic(credentials.username, credentials.password))
                    .build()

            client.newCall(request).await().use { response ->
                if (!response.isSuccessful) {
                    val responseBody = response.body.string()
                    if (response.code == 404) {
                        val reason =
                            runCatching {
                                org.json.JSONObject(responseBody).optString("reason")
                            }.getOrNull()
                        if (reason == "missing") {
                            val fallbackId = "org.couchdb.user:${credentials.username}"
                            return@runCatching ShelfDocument(
                                id = fallbackId,
                                rev = null,
                            )
                        }
                    }
                    throw IOException("Unexpected response ${response.code}")
                }
                val document =
                    shelfDocumentAdapter.fromJson(response.body.string())
                        ?: throw IOException("Invalid shelf response")
                shelfCache = document
                document
            }
        }
    }
}


suspend fun DashboardCoursesRepository.joinCourseImpl(
    baseUrl: String,
    credentials: StoredCredentials,
    courseId: String,
): Result<Unit> {
    return withContext(dispatcher) {
        runCatching {
            val normalizedBase = baseUrl.trim().trimEnd('/')
            if (normalizedBase.isEmpty()) {
                throw IOException("Missing server base URL")
            }
            val sanitizedCourseId =
                courseId.takeIf { it.isNotBlank() }
                    ?: throw IOException("Missing course id")

            var shelfDocument =
                shelfCache ?: fetchShelfDocument(baseUrl, credentials)
                    .getOrElse { throw it }

            repeat(2) { attempt ->
                val shelfId =
                    shelfDocument.id
                        ?: "org.couchdb.user:${credentials.username}"

                val updatedCourseIds =
                    (shelfDocument.courseIds + sanitizedCourseId)
                        .filter { it.isNotBlank() }
                        .distinct()

                val updatedDocument =
                    shelfDocument.copy(
                        id = shelfId,
                        rev = shelfDocument.rev,
                        courseIds = updatedCourseIds,
                    )

                val requestUrl = "$normalizedBase/db/shelf/$shelfId"
                val payload = shelfDocumentAdapter.toJson(updatedDocument)
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val request =
                    Request
                        .Builder()
                        .url(requestUrl)
                        .put(payload.toRequestBody(mediaType))
                        .addHeader("Authorization", Credentials.basic(credentials.username, credentials.password))
                        .build()

                client.newCall(request).await().use { response ->
                    val responseBody = response.body.string()
                    if (response.isSuccessful) {
                        val updatedRev =
                            runCatching {
                                org.json
                                    .JSONObject(responseBody)
                                    .optString("rev")
                                    .takeIf { it.isNotBlank() }
                            }.getOrNull()

                        val cached = updatedDocument.copy(rev = updatedRev ?: shelfDocument.rev)
                        shelfCache = cached
                        return@runCatching
                    }

                    if (response.code == 409 && attempt == 0) {
                        shelfDocument =
                            fetchShelfDocument(baseUrl, credentials)
                                .getOrElse { throw it }
                        return@use
                    }

                    throw IOException("Unexpected response ${response.code}")
                }
            }
        }
    }
}


suspend fun DashboardCoursesRepository.leaveCourseImpl(
    baseUrl: String,
    credentials: StoredCredentials,
    courseId: String,
): Result<Unit> {
    return withContext(dispatcher) {
        runCatching {
            val normalizedBase = baseUrl.trim().trimEnd('/')
            if (normalizedBase.isEmpty()) {
                throw IOException("Missing server base URL")
            }
            val sanitizedCourseId =
                courseId.takeIf { it.isNotBlank() }
                    ?: throw IOException("Missing course id")

            var shelfDocument =
                shelfCache ?: fetchShelfDocument(baseUrl, credentials)
                    .getOrElse { throw it }

            repeat(2) { attempt ->
                val shelfId =
                    shelfDocument.id
                        ?: "org.couchdb.user:${credentials.username}"
                val shelfRev =
                    shelfDocument.rev
                        ?: throw IOException("Missing shelf revision")

                val updatedCourseIds =
                    shelfDocument.courseIds
                        .filter { it.isNotBlank() && it != sanitizedCourseId }

                val updatedDocument =
                    shelfDocument.copy(
                        id = shelfId,
                        rev = shelfRev,
                        courseIds = updatedCourseIds,
                    )

                val requestUrl = "$normalizedBase/db/shelf/$shelfId"
                val payload = shelfDocumentAdapter.toJson(updatedDocument)
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val request =
                    Request
                        .Builder()
                        .url(requestUrl)
                        .put(payload.toRequestBody(mediaType))
                        .addHeader("Authorization", Credentials.basic(credentials.username, credentials.password))
                        .build()

                client.newCall(request).await().use { response ->
                    val responseBody = response.body.string()
                    if (response.isSuccessful) {
                        val updatedRev =
                            runCatching {
                                org.json
                                    .JSONObject(responseBody)
                                    .optString("rev")
                                    .takeIf { it.isNotBlank() }
                            }.getOrNull()

                        val cached = updatedDocument.copy(rev = updatedRev ?: shelfRev)
                        shelfCache = cached
                        return@runCatching
                    }

                    if (response.code == 409 && attempt == 0) {
                        shelfDocument =
                            fetchShelfDocument(baseUrl, credentials)
                                .getOrElse { throw it }
                        return@use
                    }

                    throw IOException("Unexpected response ${response.code}")
                }
            }
        }
    }
}




