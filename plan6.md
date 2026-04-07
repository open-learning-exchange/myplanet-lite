`jpegBytes` are used to render image previews:
`BitmapFactory.decodeByteArray(pending.jpegBytes, 0, pending.jpegBytes.size)`
And they are used when re-uploading the image to CouchDB:
`pending.jpegBytes` in `uploadResourceDocument`.

Wait! If the image is ALREADY uploaded to CouchDB, we DO NOT need to re-upload it when we edit the voice post!
Let's see if we re-upload them.
`CreateVoiceActivity.kt` has `uploadResourceDocument`.
If `resourceId` is NOT NULL, do we re-upload it?
