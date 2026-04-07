Let's consider streaming a multipart response.
Can we implement multipart streaming in Kotlin?
OkHttp doesn't have a built in multipart reader, but there are light libraries or simple parsers we could build. Wait, building a robust multipart parser from scratch just to avoid N network requests is risky and out of scope for a quick performance win.
Also, CouchDB's multipart API for `_all_docs` is not well documented for bulk fetching attachments, and `_bulk_get` with `Accept: multipart/mixed` returns a very complex nested multipart payload (each doc is a part, and within that part, there's the JSON doc, and then another part for attachments).
Given the constraints, the best path is to acknowledge that the concurrent binary streaming IS the optimal approach.

Wait, is there any OTHER optimization I can make to `fetchExistingVoiceImage` while keeping the concurrent binary streaming?
What if I use OkHttp's Connection Pooling? OkHttp already uses it by default.
What if I optimize `PendingVoiceImage` creation?
`BitmapFactory.decodeByteArray` runs on the UI thread when rendering!
```kotlin
                val bitmap = BitmapFactory.decodeByteArray(pending.jpegBytes, 0, pending.jpegBytes.size)
                setImageBitmap(bitmap)
```
Wait, decoding bitmaps on the UI thread causes dropped frames!
This is a HUGE performance issue in `renderPreviewImages`!
If I optimize `renderPreviewImages` to decode bitmaps using `Dispatchers.Default` and then set them on the main thread, that's a HUGE performance win!
Let's look at `renderPreviewImages`:
```kotlin
        displayPendings.forEachIndexed { index, pending ->
            val preview = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
// ...
                val bitmap = BitmapFactory.decodeByteArray(pending.jpegBytes, 0, pending.jpegBytes.size)
                setImageBitmap(bitmap)
```
Yes! I can optimize this to use Coroutines!

```kotlin
        displayPendings.forEachIndexed { index, pending ->
            val preview = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    thumbnailSize,
                    thumbnailSize
                ).apply {
                    setMargins(0, if (index == 0) 0 else spacing, 0, 0)
                }
                adjustViewBounds = false
                scaleType = ImageView.ScaleType.CENTER_CROP
                contentDescription = pending.fileName
                setOnClickListener {
                    showImageOptionsDialog(pending)
                }
            }
            wrapper.addView(preview)
            lifecycleScope.launch(Dispatchers.Default) {
                val bitmap = BitmapFactory.decodeByteArray(pending.jpegBytes, 0, pending.jpegBytes.size)
                withContext(Dispatchers.Main) {
                    preview.setImageBitmap(bitmap)
                }
            }
        }
```
Wait, the original code is:
```kotlin
            val preview = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    thumbnailSize,
                    thumbnailSize
                ).apply {
                    setMargins(0, if (index == 0) 0 else spacing, 0, 0)
                }
                adjustViewBounds = false
                scaleType = ImageView.ScaleType.CENTER_CROP
                val bitmap = BitmapFactory.decodeByteArray(pending.jpegBytes, 0, pending.jpegBytes.size)
                setImageBitmap(bitmap)
                contentDescription = pending.fileName
                setOnClickListener {
                    showImageOptionsDialog(pending)
                }
            }
            wrapper.addView(preview)
```
If I wrap the `decodeByteArray` and `setImageBitmap` in `lifecycleScope.launch`, it avoids blocking the main thread!

Also in `showImagePreviewDialog`:
```kotlin
    private fun showImagePreviewDialog(pending: PendingVoiceImage) {
        val imageView = ImageView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            val bitmap = BitmapFactory.decodeByteArray(pending.jpegBytes, 0, pending.jpegBytes.size)
            setImageBitmap(bitmap)
```
This can also be optimized!
This is a PERFECT performance optimization that is actually safe!
