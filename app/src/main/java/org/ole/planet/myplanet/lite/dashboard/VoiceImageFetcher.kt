package org.ole.planet.myplanet.lite.dashboard

import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import okhttp3.OkHttpClient
import okhttp3.Request

object VoiceImageFetcher {

    fun fetchExistingImage(
        httpClient: OkHttpClient,
        cacheDir: File,
        sessionCookie: String?,
        baseUrl: String,
        imagePath: String
,
        generateImageFileName: () -> String,
        generatePendingImageId: (String) -> String
    ): PendingVoiceImage? {
        val requestUrl = resolveImageUrl(baseUrl, imagePath) ?: return null
        val requestBuilder = Request.Builder()
            .url(requestUrl)
            .get()
        sessionCookie?.takeIf { it.isNotBlank() }?.let { cookie ->
            requestBuilder.addHeader("Cookie", cookie)
        }
        return runCatching {
            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    return null
                }
                val body = response.body
                val bytes = body.byteStream().use { stream ->
                    BufferedInputStream(stream).readBytes()
                }
                val fileName = extractFileName(imagePath) ?: generateImageFileName()
                val tempFile = File(cacheDir, fileName)
                FileOutputStream(tempFile).use { output ->
                    output.write(bytes)
                }
                val (resourceId, resourceFileName) = parseResourceFromPath(imagePath)
                val resolvedFileName = resourceFileName ?: fileName
                val markdown = buildExistingImageMarkdown(baseUrl, imagePath)
                PendingVoiceImage(
                    id = generatePendingImageId(resolvedFileName),
                    fileName = resolvedFileName,
                    file = tempFile,
                    jpegBytes = bytes,
                    resourceId = resourceId,
                    uploadedMarkdown = markdown
                )
            }
        }.getOrNull()
    }

    private fun resolveImageUrl(baseUrl: String, path: String): String? {
        val normalizedBase = baseUrl.trim().trimEnd('/')
        if (normalizedBase.isEmpty()) {
            return null
        }
        val trimmedPath = path.trim()
        if (trimmedPath.isEmpty()) {
            return null
        }
        if (trimmedPath.startsWith("http://") || trimmedPath.startsWith("https://")) {
            return trimmedPath
        }
        val normalizedPath = trimmedPath.trimStart('/')
        val finalPath = if (normalizedPath.startsWith("db/")) normalizedPath else "db/$normalizedPath"
        return "$normalizedBase/$finalPath"
    }

    private fun buildExistingImageMarkdown(baseUrl: String, path: String): String {
        val trimmedPath = path.trim()
        if (trimmedPath.isEmpty()) {
            return "![]($path)"
        }
        if (trimmedPath.startsWith("http://") || trimmedPath.startsWith("https://")) {
            return "![](${trimmedPath.trim()})"
        }
        val normalizedPath = trimmedPath.trimStart('/')
        val withoutDb = if (normalizedPath.startsWith("db/")) {
            normalizedPath.removePrefix("db/")
        } else {
            normalizedPath
        }
        return if (withoutDb.startsWith("resources/")) {
            "![](${withoutDb.trim()})"
        } else {
            "![](resources/${withoutDb.trim()})"
        }
    }

        private fun parseResourceFromPath(path: String): Pair<String?, String?> {
        val parts = path.split('/')
        if (parts.isEmpty()) {
            return null to null
        }
        val resourcesIndex = parts.indexOfFirst { it.equals("resources", ignoreCase = true) }
        if (resourcesIndex == -1 || resourcesIndex + 2 >= parts.size) {
            return null to null
        }
        val resourceId = parts.getOrNull(resourcesIndex + 1)?.takeIf { it.isNotBlank() }
        val fileName = parts.getOrNull(resourcesIndex + 2)?.takeIf { it.isNotBlank() }
        return resourceId to fileName
    }

    private fun extractFileName(path: String): String? {
        val trimmed = path.trim().trimEnd('/')
        val lastSlash = trimmed.lastIndexOf('/')
        if (lastSlash == -1 || lastSlash == trimmed.lastIndex) {
            return trimmed.takeIf { it.isNotEmpty() }
        }
        return trimmed.substring(lastSlash + 1).takeIf { it.isNotEmpty() }
    }

}
