package org.ole.planet.myplanet.lite.util

import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FileUtils {
    suspend fun deleteFiles(files: List<File>, dispatcher: CoroutineDispatcher = Dispatchers.IO) {
        withContext(dispatcher) {
            files.forEach { file ->
                if (file.exists()) {
                    file.delete()
                }
            }
        }
    }
}
