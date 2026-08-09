package org.ole.planet.myplanet.lite.dashboard

import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.Buffer

internal class DashboardResourceDownloads(
    private val client: OkHttpClient,
    private val ioDispatcher: CoroutineDispatcher
) {
    suspend fun downloadResourceBytes(
        baseUrl: String,
        sessionCookie: String?,
        resourceId: String,
        filename: String,
        onProgress: ((Int?) -> Unit)?
    ): Result<ByteArray> = withContext(ioDispatcher) {
        runCatching {
            val normalizedBase = baseUrl.trim().trimEnd('/')
            if (normalizedBase.isEmpty()) throw IOException("Missing server base URL")
            if (resourceId.isBlank() || filename.isBlank()) {
                throw IOException("Missing resource id or filename")
            }

            val requestBuilder = Request.Builder()
                .url("$normalizedBase/db/resources/${resourceId.trim()}/${filename.trim()}")
                .get()
            sessionCookie?.takeIf { it.isNotBlank() }?.let { requestBuilder.addHeader("Cookie", it) }

            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Unexpected response ${response.code}")
                readResponseBytes(response, onProgress)
            }
        }
    }

    suspend fun downloadPdfToCache(url: String, authHeader: String?, cacheDir: File): File? {
        return withContext(ioDispatcher) {
            runCatching {
                val parsedUri = android.net.Uri.parse(url)
                if (parsedUri.scheme == "file") {
                    val localFile = File(parsedUri.path.orEmpty())
                    if (localFile.exists()) return@withContext localFile
                }

                val request = Request.Builder()
                    .url(url)
                    .apply {
                        if (!authHeader.isNullOrBlank() && url.startsWith("https://", ignoreCase = true)) {
                            addHeader("Authorization", authHeader)
                        }
                    }
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    saveToTempFile(response, cacheDir)
                }
            }.getOrNull()
        }
    }

    private fun readResponseBytes(response: Response, onProgress: ((Int?) -> Unit)?): ByteArray {
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
        if (totalBytes > 0L && lastProgress != 100) onProgress?.invoke(100)
        return sink.readByteArray()
    }

    private fun saveToTempFile(response: Response, cacheDir: File): File {
        val file = File.createTempFile("course_resource_", ".pdf", cacheDir)
        response.body.byteStream().use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        return file
    }
}
