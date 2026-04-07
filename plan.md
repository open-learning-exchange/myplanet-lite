We have a performance optimization task related to an N+1 query issue in `CreateVoiceActivity.kt` inside the `loadEditInitialImages` function:

```kotlin
        val loaded = coroutineScope {
            editInitialImagePaths.map { path ->
                async(Dispatchers.IO) {
                    runCatching { fetchExistingVoiceImage(base, path) }.getOrNull()
                }
            }.awaitAll().filterNotNull()
        }
```

The issue description states: "Uses `async` to fetch images concurrently, which is better than sequential, but still fires N requests. Grouping these requests or using a bulk endpoint (if available) would be better. Giving confidence 2 since changing this requires API support."

I will investigate if CouchDB or an alternative API allows fetching these images in bulk, or whether we can refactor this. Actually wait, `fetchExistingVoiceImage` is making GET requests to download the actual image bytes.

Let's look at `fetchExistingVoiceImage`:
```kotlin
    private fun fetchExistingVoiceImage(baseUrl: String, imagePath: String): PendingVoiceImage? {
        val requestUrl = resolveImageUrl(baseUrl, imagePath) ?: return null
        val requestBuilder = Request.Builder()
            .url(requestUrl)
            .get()
        sessionCookie?.takeIf { it.isNotBlank() }?.let { cookie ->
            requestBuilder.addHeader("Cookie", cookie)
        }
// ...
```

Is there a bulk image/attachment download API? CouchDB usually doesn't have a single API to download multiple attachments at once, but perhaps we can improve how we retrieve them or how we process them. Wait, maybe there's an API in `VoicesComposerRepository` or similar that we should use?
Wait, the prompt mentions CouchDB `_find` endpoints in the AGENTS.md memory: "When batching CouchDB `_find` queries (e.g., using `$or` to match multiple IDs in a single batched repository request)...". But this is fetching images by their URL, not documents. Let me check what `imagePath` looks like.

Let's read `resolveImageUrl`:
```kotlin
    private fun resolveImageUrl(baseUrl: String, path: String): String? {
        val normalizedBase = baseUrl.trim().trimEnd('/')
// ...
        if (trimmedPath.startsWith("http://") || trimmedPath.startsWith("https://")) {
            return trimmedPath
        }
        return if (trimmedPath.startsWith("/")) {
            "$normalizedBase$trimmedPath"
        } else {
            "$normalizedBase/$trimmedPath"
        }
    }
```
They are direct URLs. There might not be a bulk endpoint for images.
Ah, what about `DashboardPostDetailActivity.kt`? It has a very similar N+1 query:
```kotlin
    private suspend fun loadExistingCommentImages(comment: PostDetailItem.Comment) {
        val base = baseUrl ?: return
        if (comment.imagePaths.isEmpty()) {
            return
        }
        val loaded = coroutineScope {
            comment.imagePaths.map { path ->
                async(Dispatchers.IO) { fetchExistingCommentImage(base, path) }
            }.awaitAll().filterNotNull().toMutableList()
        }
// ...
```
But wait, the task specifically points to `CreateVoiceActivity.kt:648` (or similar line, wait, it's line 973 in current file).
The task mentions: "Grouping these requests or using a bulk endpoint (if available) would be better. Giving confidence 2 since changing this requires API support."

Is there a way to optimize this? If we are downloading images, maybe we don't need to download all of them if they are already cached?
Or perhaps we shouldn't download them at all in `loadEditInitialImages`?
Wait! `editInitialImagePaths` - maybe we can fetch the image documents instead of the image bytes first? No, we need the image bytes to show them as pending images. Wait, do we?
Let's see what `PendingVoiceImage` does. It has `val file: File`, `val jpegBytes: ByteArray`, `var resourceId: String?`.
It is used to show previews and later upload them.

Wait! If they are *existing* voice images (meaning they are already uploaded), do we really need to download them as bytes and re-upload them?
Ah! If they are existing images, they have a URL. If we just keep the URL, we wouldn't need to download them. But the user edits the post, and maybe we need to keep the existing images attached.

Let's reread the task description carefully. "N+1 Query Issue in `loadEditInitialImages`... Grouping these requests or using a bulk endpoint (if available) would be better. Giving confidence 2 since changing this requires API support."
It suggests looking for a bulk endpoint. Is there a CouchDB bulk endpoint? `/_all_docs`? But we are downloading URLs (images).
Let me check the `fetchExistingVoiceImage` in `CreateVoiceActivity`.
It fetches the URL using an HTTP GET.
Wait! Maybe there IS a bulk API in the application? Or maybe we can't group them, but we CAN optimize something else?
