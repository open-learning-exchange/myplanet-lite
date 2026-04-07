Ok, no tests mock the images.

How to implement the bulk fetch:
```kotlin
    private suspend fun fetchExistingVoiceImagesBulk(
        baseUrl: String,
        paths: List<String>
    ): List<PendingVoiceImage> {
        val normalizedBase = baseUrl.trim().trimEnd('/')
        if (normalizedBase.isEmpty()) return emptyList()

        val resourcePaths = mutableMapOf<String, String>() // resourceId -> path
        val externalPaths = mutableListOf<String>()

        for (path in paths) {
            val (resourceId, _) = parseResourceFromPath(path)
            if (resourceId != null) {
                resourcePaths[resourceId] = path
            } else {
                externalPaths.add(path)
            }
        }

        val loaded = mutableListOf<PendingVoiceImage>()

        // 1. Fetch bulk from CouchDB
        if (resourcePaths.isNotEmpty()) {
            val requestUrl = "$normalizedBase/db/resources/_all_docs?include_docs=true&attachments=true"
            val keysArray = org.json.JSONArray(resourcePaths.keys)
            val jsonBody = org.json.JSONObject().put("keys", keysArray).toString()

            val requestBuilder = Request.Builder()
                .url(requestUrl)
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
            sessionCookie?.takeIf { it.isNotBlank() }?.let { cookie ->
                requestBuilder.addHeader("Cookie", cookie)
            }

            val bulkResult = runCatching {
                withContext(Dispatchers.IO) {
                    httpClient.newCall(requestBuilder.build()).execute().use { response ->
                        if (!response.isSuccessful) return@use emptyList<PendingVoiceImage>()
                        val responseBody = response.body?.string() ?: return@use emptyList<PendingVoiceImage>()
                        val json = org.json.JSONObject(responseBody)
                        val rows = json.optJSONArray("rows") ?: return@use emptyList<PendingVoiceImage>()

                        val fetched = mutableListOf<PendingVoiceImage>()
                        for (i in 0 until rows.length()) {
                            val row = rows.optJSONObject(i) ?: continue
                            val doc = row.optJSONObject("doc") ?: continue
                            val docId = doc.optString("_id")
                            if (docId.isNullOrBlank()) continue

                            val attachments = doc.optJSONObject("_attachments") ?: continue
                            val originalPath = resourcePaths[docId] ?: continue
                            val (_, expectedFileName) = parseResourceFromPath(originalPath)

                            var targetAttachment: org.json.JSONObject? = null
                            var resolvedFileName = expectedFileName
                            if (expectedFileName != null && attachments.has(expectedFileName)) {
                                targetAttachment = attachments.optJSONObject(expectedFileName)
                            } else {
                                val keys = attachments.keys()
                                if (keys.hasNext()) {
                                    val key = keys.next()
                                    targetAttachment = attachments.optJSONObject(key)
                                    resolvedFileName = key
                                }
                            }

                            if (targetAttachment != null) {
                                val base64Data = targetAttachment.optString("data")
                                if (base64Data.isNotBlank()) {
                                    val bytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                                    val fileName = resolvedFileName ?: generateImageFileName()
                                    val tempFile = File(cacheDir, fileName)
                                    FileOutputStream(tempFile).use { output ->
                                        output.write(bytes)
                                    }
                                    val markdown = buildExistingImageMarkdown(baseUrl, originalPath)
                                    fetched.add(PendingVoiceImage(
                                        id = generatePendingImageId(fileName),
                                        fileName = fileName,
                                        file = tempFile,
                                        jpegBytes = bytes,
                                        resourceId = docId,
                                        uploadedMarkdown = markdown
                                    ))
                                }
                            }
                        }
                        fetched
                    }
                }
            }.getOrNull() ?: emptyList()

            loaded.addAll(bulkResult)
        }

        // 2. Fetch external URLs concurrently
        if (externalPaths.isNotEmpty()) {
            val externalLoaded = coroutineScope {
                externalPaths.map { path ->
                    async(Dispatchers.IO) {
                        runCatching { fetchExistingVoiceImage(baseUrl, path) }.getOrNull()
                    }
                }.awaitAll().filterNotNull()
            }
            loaded.addAll(externalLoaded)
        }

        return loaded
    }
```

Wait, `JSONObject` is not recommended if the size is very large.
Is there an alternative? Moshi is the default in this project.
If we use Moshi, it's safer and better typed, though using it inline requires creating models. Wait, Moshi can parse huge strings just fine, but `responseBody` itself will be a huge string. Is it possible to stream it? Moshi supports streaming with `JsonReader` or `Moshi.adapter().fromJson(response.body.source())`. Yes!

```kotlin
data class BulkDocsRequest(val keys: List<String>)
data class BulkDocsResponse(val rows: List<BulkDocRow>?)
data class BulkDocRow(val id: String?, val doc: BulkDoc?)
data class BulkDoc(
    @Json(name = "_id") val id: String?,
    @Json(name = "_attachments") val attachments: Map<String, Attachment>?
)
data class Attachment(val data: String?)
```

Let's do this. I'll define these inline classes or in the file.
