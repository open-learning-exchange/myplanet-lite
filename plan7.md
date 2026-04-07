If `shouldUploadPending` returns false, the image is NOT re-uploaded!
So if we can get all `PendingVoiceImage`s WITHOUT `jpegBytes`, but we STILL need `jpegBytes` to display previews?
Wait, if it's already on the server, can we just use Glide or Picasso to load the URL in the ImageView instead of downloading the bytes?

Let's check `renderPreviewImages`.
`app/src/main/java/org/ole/planet/myplanet/lite/dashboard/CreateVoiceActivity.kt`
How are images rendered?
`ImageView.setImageBitmap(bitmap)` where `bitmap = BitmapFactory.decodeByteArray(pending.jpegBytes...)`

If I change `PendingVoiceImage` to have `jpegBytes: ByteArray? = null` and a new property `remoteUrl: String? = null`
Then in `renderPreviewImages`, if `jpegBytes` is null, it uses Glide to load `remoteUrl`.
Wait, Picasso or Glide? Let's check what image loading library the project uses.
