Wait, if `PendingVoiceImage` HAS to have `jpegBytes` because it's used for displaying the image, we CANNOT skip downloading them.

If we cannot skip downloading them, and if CouchDB `_all_docs` supports `attachments=true` and `include_docs=true`, we CAN group the requests.

Wait, if I use `_all_docs`, what if the URL is an external HTTP URL? We will just download those sequentially or concurrently as before, but group the CouchDB resource URLs into one `_all_docs` call!

Let me verify if `VoicesComposerRepository` can be extended with a `fetchExistingVoiceImagesBulk` function.
Wait, maybe we can just write an inline HTTP request in `CreateVoiceActivity` that POSTs to `db/resources/_all_docs` with keys.
But wait! Are we 100% sure the images are from `db/resources`?
In `resolveExistingMarkdown`:
```kotlin
            val normalized = path?.removePrefix("db/")?.trimStart('/') ?: ""
            if (normalized.startsWith("resources/", ignoreCase = true)
```
Yes, they are in the `resources` database!

If they are in `db/resources`, their document IDs are the first part after `resources/`.
`resources/RESOURCE_ID/file.jpg`.
So the keys for `_all_docs` are `[RESOURCE_ID]`.

Let's test this CouchDB query mentally.
`POST /db/resources/_all_docs?include_docs=true&attachments=true`
Body: `{"keys": ["id1", "id2"]}`
Response format:
```json
{
  "total_rows": 123,
  "offset": 0,
  "rows": [
    {
      "id": "id1",
      "key": "id1",
      "value": { "rev": "1-..." },
      "doc": {
        "_id": "id1",
        "_rev": "1-...",
        "_attachments": {
          "file.jpg": {
            "content_type": "image/jpeg",
            "revpos": 1,
            "digest": "md5-...",
            "length": 1234,
            "stub": false,
            "data": "base64_encoded_data_here..."
          }
        }
      }
    }
  ]
}
```

This is a single HTTP request! It's much faster than N requests.

Wait, is it faster to make N concurrent OkHttp `GET` requests for binary images, OR 1 `POST` request that returns a gigantic JSON string where all images are Base64 encoded?
Base64 increases size by 33%. Moshi needs to allocate a huge String and parse it, which can cause OOM on Android if images are large.
Concurrent GETs stream the binary data straight to disk using `response.body.byteStream()`, without reading it all into memory as a String!

"Grouping these requests or using a bulk endpoint (if available) would be better. Giving confidence 2 since changing this requires API support."
If CouchDB had an API to download multiple attachments as a ZIP or multipart, that would be ideal. Since it DOES NOT natively, the author writes: "Giving confidence 2 since changing this requires API support."
But what if we just use `_all_docs`? The author might consider that "using a bulk endpoint".

Alternatively, wait, is there another bulk endpoint we can use?
If there is NO efficient bulk endpoint for binary data, what else can we optimize?

Wait, what if we use `coroutineScope` and `async` like the code currently does:
```kotlin
        val loaded = coroutineScope {
            editInitialImagePaths.map { path ->
                async(Dispatchers.IO) {
                    runCatching { fetchExistingVoiceImage(base, path) }.getOrNull()
                }
            }.awaitAll().filterNotNull()
        }
```
Is this N+1 query?
The prompt title is "N+1 Query Issue in `loadEditInitialImages`".
The code iterates over `editInitialImagePaths` and calls `fetchExistingVoiceImage` for each path. This fires N requests!
If we group them into `_all_docs`, it fires 1 request.

Let's implement `fetchExistingVoiceImagesBulk` using `db/resources/_all_docs?include_docs=true&attachments=true`.
We can use OkHttp to execute it and Moshi/JSONObject to parse it. Wait, `JSONObject` is better because we can avoid creating large Moshi models. Or we can just use Moshi.

Wait! The task actually applies to `DashboardPostDetailActivity` as well:
`loadExistingCommentImages` does EXACTLY the same thing. Should I fix both? The task specifically says:
**File:** `app/src/main/java/org/ole/planet/myplanet/lite/dashboard/CreateVoiceActivity.kt:648`
**Issue:** N+1 Query Issue in `loadEditInitialImages`
I will fix `loadEditInitialImages` in `CreateVoiceActivity`.

Wait, how big are the images? They are JPEG encoded Voice Images, usually compressBitmapToJpeg compresses them to reasonably small sizes.
