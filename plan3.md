So `imagePath` looks like `db/resources/some_id/image.jpg` or `resources/some_id/image.jpg`.
If we have multiple image paths, we can extract the `resourceId`s from them!
Wait, but does the API let us get multiple documents with attachments at once?
`POST /db/resources/_all_docs?include_docs=true&attachments=true`
Body: `{"keys": ["some_id_1", "some_id_2"]}`

Would it work?
Let's see if we can do this.
Let's look at CouchDB API docs. Yes, `POST /{db}/_all_docs` accepts `{"keys": [...]}`.
If we add `?include_docs=true&attachments=true`, the documents returned in the `rows` array will contain an `_attachments` object. Each attachment will have a `data` field with base64 encoded bytes!
This would fetch all images in ONE request!

Let's verify this against a CouchDB instance. Wait, I can't test against the real CouchDB if I don't have it running. Let's see if `VoicesComposerRepository` or similar can do this.
I can create a method `fetchExistingVoiceImagesBulk` in `VoicesComposerRepository` that queries `_all_docs` or I can just implement it directly in `CreateVoiceActivity` or a new repository.

Wait! The prompt task states:
"Uses `async` to fetch images concurrently, which is better than sequential, but still fires N requests. Grouping these requests or using a bulk endpoint (if available) would be better. Giving confidence 2 since changing this requires API support."

Is `_all_docs` safe to use for images? `attachments=true` will Base64 encode the attachments, making the payload 33% larger. If the images are large, a bulk response could be huge and take a lot of memory.
BUT the prompt says: "Grouping these requests or using a bulk endpoint (if available) would be better."
Wait, if it's "if available", what if I just use `_all_docs`?
Actually, what if I don't download the attachments at all?
Wait, `PendingVoiceImage` needs `jpegBytes` to display the image preview, right?
Let's check how it displays it.
`jpegBytes` are saved to a temporary `File`!
```kotlin
                val tempFile = File(cacheDir, fileName)
                FileOutputStream(tempFile).use { output ->
                    output.write(bytes)
                }
```
Yes, the file is saved and then `PendingVoiceImage` uses it to render the preview.

Let's test `_all_docs?include_docs=true&attachments=true` locally or check if CouchDB supports `_bulk_get`? CouchDB 2.0+ supports `_bulk_get`.
`POST /{db}/_bulk_get?attachments=true`
Body: `{"docs": [{"id": "doc1"}, {"id": "doc2"}]}`
Both work and return base64 attachments.

Alternatively, since I'm just replacing the N+1 API calls, I can write a bulk fetcher using `_all_docs`.
Wait! Is the image path ALWAYS from `db/resources/`?
What if `resolveImageUrl` handles generic URLs like `https://example.com/image.png`?
```kotlin
        if (trimmedPath.startsWith("http://") || trimmedPath.startsWith("https://")) {
            return trimmedPath
        }
```
If it's an external HTTP URL, we can't use CouchDB bulk APIs. We would have to fetch them normally.
So I should group the ones that belong to `resources/` or `db/resources/` and fetch them in bulk, and fallback to normal fetch for the rest.

Let's parse the `imagePaths`.
```kotlin
        val pathsToBulkFetch = mutableListOf<String>()
        val externalPaths = mutableListOf<String>()
        for (path in editInitialImagePaths) {
           val (resourceId, _) = parseResourceFromPath(path)
           if (resourceId != null) pathsToBulkFetch.add(path)
           else externalPaths.add(path)
        }
```
Wait! Is it possible to use `_all_docs` to get JUST the attachments?
CouchDB `_all_docs` with `attachments=true` gets the whole doc + all its attachments. A resource document usually has 1 attachment.
If I use `POST /db/resources/_all_docs?include_docs=true&attachments=true` with `keys=["id1", "id2"]`, it will work.

Let's verify the repository or create a new function in `VoicesComposerRepository` or just inside `CreateVoiceActivity`.
