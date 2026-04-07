Wait! I need `okhttp3.RequestBody.Companion.toRequestBody` and `okhttp3.MediaType.Companion.toMediaType` in Kotlin.
In Kotlin with okhttp3 4+, it's `jsonBody.toRequestBody("application/json".toMediaType())`. Let's import it or use the static methods if available, wait, in `CreateVoiceActivity` they have `import okhttp3.RequestBody.Companion.toRequestBody` ?
Let's see what `CreateVoiceActivity` imports.
