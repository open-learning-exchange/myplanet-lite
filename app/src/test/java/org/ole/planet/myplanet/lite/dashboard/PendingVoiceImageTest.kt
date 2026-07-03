package org.ole.planet.myplanet.lite.dashboard

import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PendingVoiceImageTest {

    @Test
    fun testDeleteFiles() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val tempFile1 = File.createTempFile("test_file_1", ".tmp")
        val tempFile2 = File.createTempFile("test_file_2", ".tmp")
        val nonExistentFile = File("non_existent_file.tmp")

        assertTrue(tempFile1.exists())
        assertTrue(tempFile2.exists())
        assertFalse(nonExistentFile.exists())

        val filesList = listOf(tempFile1, tempFile2, nonExistentFile)

        deleteFiles(filesList, testDispatcher)

        assertFalse(tempFile1.exists())
        assertFalse(tempFile2.exists())
    }
}
