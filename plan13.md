So I need to add:
```kotlin
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
```
Wait, let's write a python script that does the replacement properly.

I will request a plan review for this exact plan.

Plan:
1. Modify `app/src/main/java/org/ole/planet/myplanet/lite/dashboard/CreateVoiceActivity.kt`
2. Add imports for `okhttp3.MediaType.Companion.toMediaType` and `okhttp3.RequestBody.Companion.toRequestBody`.
3. Add a new `fetchExistingVoiceImagesBulk` function that uses `_all_docs` with `include_docs=true` and `attachments=true` to fetch CouchDB attachments in bulk.
4. Modify `loadEditInitialImages` to call `fetchExistingVoiceImagesBulk`.
5. Run tests.
6. Submit.
