package org.ole.planet.myplanet.lite.dashboard

import java.io.File

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PendingVoiceImage(
    val id: String,
    val fileName: String,
    val file: File,
    val jpegBytes: ByteArray,
    var resourceId: String? = null,
    var resourceRevision: String? = null,
    var uploadedMarkdown: String? = null
)

suspend fun deleteFiles(files: List<File>, dispatcher: CoroutineDispatcher = Dispatchers.IO) {
    withContext(dispatcher) {
        files.forEach { file ->
            if (file.exists()) {
                file.delete()
            }
        }
    }
}
