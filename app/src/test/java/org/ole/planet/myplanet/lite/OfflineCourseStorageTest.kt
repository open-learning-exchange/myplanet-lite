package org.ole.planet.myplanet.lite

import android.content.Context
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.io.File

class OfflineCourseStorageTest {

    private lateinit var context: Context
    private lateinit var tempFilesDir: File

    @Before
    fun setUp() {
        val tempFile = File.createTempFile("test_files_dir", "")
        tempFile.delete() // Delete the file so we can create a directory with the same name
        tempFile.mkdir()
        tempFilesDir = tempFile

        context = mock(Context::class.java)
        `when`(context.filesDir).thenReturn(tempFilesDir)
    }

    @After
    fun tearDown() {
        tempFilesDir.deleteRecursively()
    }

    @Test
    fun `downloadedCourseIds returns empty set when root dir does not exist`() {
        // Assert that the ROOT_DIR (".offline_courses") does not exist in tempFilesDir initially
        val rootDir = File(tempFilesDir, ".offline_courses")
        assertEquals(false, rootDir.exists())

        val result = OfflineCourseStorage.downloadedCourseIds(context)
        assertEquals(emptySet<String>(), result)
    }

    @Test
    fun `downloadedCourseIds returns empty set when root dir is a file`() {
        // Create root dir as a regular file
        val rootDir = File(tempFilesDir, ".offline_courses")
        rootDir.createNewFile()

        val result = OfflineCourseStorage.downloadedCourseIds(context)
        assertEquals(emptySet<String>(), result)
    }

    @Test
    fun `downloadedCourseIds returns empty set when root dir is empty`() {
        val rootDir = File(tempFilesDir, ".offline_courses")
        rootDir.mkdirs()

        val result = OfflineCourseStorage.downloadedCourseIds(context)
        assertEquals(emptySet<String>(), result)
    }

    @Test
    fun `downloadedCourseIds returns only valid course directories`() {
        val rootDir = File(tempFilesDir, ".offline_courses")
        rootDir.mkdirs()

        // 1. Valid course directory (directory with course.json)
        val validCourseDir = File(rootDir, "valid_course")
        validCourseDir.mkdirs()
        File(validCourseDir, "course.json").createNewFile()

        // 2. Invalid course directory (directory without course.json)
        val invalidCourseDir = File(rootDir, "invalid_course")
        invalidCourseDir.mkdirs()

        // 3. A regular file instead of a directory
        val fileNotDir = File(rootDir, "just_a_file.txt")
        fileNotDir.createNewFile()

        val result = OfflineCourseStorage.downloadedCourseIds(context)

        // Should only contain "valid_course"
        assertEquals(setOf("valid_course"), result)
    }
}
