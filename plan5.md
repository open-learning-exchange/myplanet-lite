Let's see if we can use CouchDB `_all_docs` to bulk fetch images.
Wait, wait! I notice something.
The `pendingImages` map requires:
`id: String, fileName: String, file: File, jpegBytes: ByteArray, resourceId: String?, uploadedMarkdown: String?`

But do we NEED `jpegBytes`?
In `PendingVoiceImage`, `jpegBytes` is used. Wait, let me check how `jpegBytes` is used.
If we can avoid downloading the images AT ALL until they are really needed, that would be a huge optimization.
Let's see where `jpegBytes` is used.
