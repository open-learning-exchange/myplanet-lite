import re

file_path = "app/src/main/java/org/ole/planet/myplanet/lite/dashboard/CreateVoiceActivity.kt"

with open(file_path, "r") as f:
    content = f.read()

# 1. Optimize renderPreviewImages
old_render = """            val preview = ImageView(this).apply {
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
            wrapper.addView(preview)"""

new_render = """            val preview = ImageView(this).apply {
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
            }"""

content = content.replace(old_render, new_render)

# 2. Optimize showImagePreviewDialog
old_dialog = """    private fun showImagePreviewDialog(pending: PendingVoiceImage) {
        val imageView = ImageView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            val bitmap = BitmapFactory.decodeByteArray(pending.jpegBytes, 0, pending.jpegBytes.size)
            setImageBitmap(bitmap)
            contentDescription = pending.fileName
            val padding = resources.getDimensionPixelSize(R.dimen.create_voice_image_preview_dialog_padding)
            setPadding(padding, padding, padding, padding)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(pending.fileName)
            .setView(imageView)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }"""

new_dialog = """    private fun showImagePreviewDialog(pending: PendingVoiceImage) {
        val imageView = ImageView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = pending.fileName
            val padding = resources.getDimensionPixelSize(R.dimen.create_voice_image_preview_dialog_padding)
            setPadding(padding, padding, padding, padding)
        }
        lifecycleScope.launch(Dispatchers.Default) {
            val bitmap = BitmapFactory.decodeByteArray(pending.jpegBytes, 0, pending.jpegBytes.size)
            withContext(Dispatchers.Main) {
                imageView.setImageBitmap(bitmap)
            }
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(pending.fileName)
            .setView(imageView)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }"""

content = content.replace(old_dialog, new_dialog)

with open(file_path, "w") as f:
    f.write(content)
