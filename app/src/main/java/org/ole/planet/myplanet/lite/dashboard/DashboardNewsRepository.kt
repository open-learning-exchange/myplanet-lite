/**
 * Author: Walfre López Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2025-11-15
 */

package org.ole.planet.myplanet.lite.dashboard

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.io.IOException
import java.io.Serializable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class DashboardNewsRepository(
    private val client: OkHttpClient,
    private val moshi: Moshi,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val requestAdapter = moshi.adapter(NewsFindRequest::class.java)
    private val responseAdapter = moshi.adapter(NewsFindResponse::class.java)

    suspend fun fetchNews(
        baseUrl: String,
        sessionCookie: String?,
        skip: Int,
        bookmark: String?,
        limit: Int,
        createdOn: String?,
        parentCode: String?,
        teamName: String? = null
    ): Result<NewsPage> {
        return withContext(dispatcher) {
            runCatching {
                val normalizedBase = baseUrl.trim().trimEnd('/')
                if (normalizedBase.isEmpty()) {
                    throw IOException("Missing server base URL")
                }
                val requestUrl = "$normalizedBase/db/news/_find"
                val selector = mutableMapOf<String, Any>()
                createdOn?.takeIf { it.isNotBlank() }?.let { code ->
                    selector["createdOn"] = code
                    val viewInClause = if (!teamName.isNullOrBlank()) {
                        mapOf(
                            "section" to "teams",
                            "name" to teamName,
                            "mode" to "team"
                        )
                    } else {
                        parentCode?.takeIf { it.isNotBlank() }?.let { parent ->
                            mapOf(
                                "section" to "community",
                                "_id" to "$code@$parent"
                            )
                        }
                    }
                    viewInClause?.let { clause ->
                        selector["viewIn"] = mapOf("\$elemMatch" to clause)
                    }
                }
                val payload = NewsFindRequest(
                    selector = selector,
                    limit = limit,
                    sort = listOf(mapOf("time" to "desc")),
                    skip = skip,
                    bookmark = bookmark
                )
                val json = requestAdapter.toJson(payload)
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
                    val docs = parsed.docs ?: emptyList()
                    val commentCounts = mutableMapOf<String, Int>()
                    docs.forEach { document ->
                        val parentId = document.replyTo
                        if (!parentId.isNullOrEmpty()) {
                            val current = commentCounts[parentId] ?: 0
                            commentCounts[parentId] = current + 1
                        }
                    }
                    val filtered = docs.filter { it.replyTo.isNullOrEmpty() }
                    val consumed = docs.size
                    NewsPage(
                        items = filtered,
                        consumed = consumed,
                        hasMore = docs.size == limit,
                        bookmark = parsed.bookmark,
                        commentCounts = commentCounts
                    )
                }
            }
        }
    }

    suspend fun fetchComments(
        baseUrl: String,
        sessionCookie: String?,
        postId: String,
        limit: Int,
        createdOn: String?,
        parentCode: String?,
        teamName: String? = null
    ): Result<List<NewsDocument>> {
        return withContext(dispatcher) {
            runCatching {
                val normalizedBase = baseUrl.trim().trimEnd('/')
                if (normalizedBase.isEmpty()) {
                    throw IOException("Missing server base URL")
                }
                if (postId.isBlank()) {
                    throw IOException("Missing post id")
                }
                val requestUrl = "$normalizedBase/db/news/_find"
                val selector = mutableMapOf<String, Any>("replyTo" to postId)
                createdOn?.takeIf { it.isNotBlank() }?.let { code ->
                    selector["createdOn"] = code
                    val viewInClause = if (!teamName.isNullOrBlank()) {
                        mapOf(
                            "section" to "teams",
                            "name" to teamName,
                            "mode" to "team"
                        )
                    } else {
                        parentCode?.takeIf { it.isNotBlank() }?.let { parent ->
                            mapOf(
                                "section" to "community",
                                "_id" to "$code@$parent"
                            )
                        }
                    }
                    viewInClause?.let { clause ->
                        selector["viewIn"] = mapOf("\$elemMatch" to clause)
                    }
                }
                val payload = NewsFindRequest(
                    selector = selector,
                    limit = limit,
                    sort = listOf(mapOf("time" to "desc"))
                )
                val json = requestAdapter.toJson(payload)
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
                    val docs = parsed.docs ?: emptyList()
                    docs.filter { it.replyTo == postId }
                }
            }
        }
    }

    @JsonClass(generateAdapter = true)
    data class NewsFindRequest(
        val selector: Map<String, Any>,
        val limit: Int,
        val sort: List<Map<String, String>>,
        val skip: Int? = null,
        val bookmark: String? = null
    )

    @JsonClass(generateAdapter = true)
    data class NewsFindResponse(
        val docs: List<NewsDocument>?,
        val bookmark: String?
    )

    @JsonClass(generateAdapter = true)
    data class NewsDocument(
        @param:Json(name = "_id") val id: String?,
        @param:Json(name = "_rev") val revision: String?,
        val docType: String?,
        val time: Long?,
        val createdOn: String?,
        val parentCode: String?,
        val user: NewsUser?,
        val replyTo: String?,
        val viewIn: List<ViewInEntry>?,
        val messageType: String?,
        val messagePlanetCode: String?,
        val message: String?,
        val images: List<NewsImage>?,
        val updatedDate: Long?,
        @param:Json(name = "_deleted") val isDeleted: Boolean?
    ) : Serializable

    @JsonClass(generateAdapter = true)
    data class NewsUser(
        val name: String?,
        val firstName: String?,
        val middleName: String?,
        val lastName: String?,
        @param:Json(name = "_attachments") val attachments: Map<String, NewsAttachment>?
    ) : Serializable

    @JsonClass(generateAdapter = true)
    data class NewsAttachment(
        @param:Json(name = "content_type") val contentType: String?
    ) : Serializable

    @JsonClass(generateAdapter = true)
    data class NewsImage(
        val resourceId: String?,
        val filename: String?,
        val markdown: String?
    ) : Serializable

    @JsonClass(generateAdapter = true)
    data class ViewInEntry(
        val section: String?,
        @param:Json(name = "_id") val id: String?,
        @param:Json(name = "public") val isPublic: Boolean? = null,
        val name: String? = null,
        val mode: String? = null
    ) : Serializable

    data class NewsPage(
        val items: List<NewsDocument>,
        val consumed: Int,
        val hasMore: Boolean,
        val bookmark: String?,
        val commentCounts: Map<String, Int>
    )
}
