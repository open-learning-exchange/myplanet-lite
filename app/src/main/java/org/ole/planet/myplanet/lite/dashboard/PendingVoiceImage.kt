package org.ole.planet.myplanet.lite.dashboard

import java.io.File

data class PendingVoiceImage(
    val id: String,
    val fileName: String,
    val file: File,
    val jpegBytes: ByteArray,
    var resourceId: String? = null,
    var resourceRevision: String? = null,
    var uploadedMarkdown: String? = null
)
