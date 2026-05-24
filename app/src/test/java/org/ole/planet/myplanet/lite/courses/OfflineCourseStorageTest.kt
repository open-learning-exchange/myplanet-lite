package org.ole.planet.myplanet.lite.courses

import android.content.Context
import java.io.File
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.ole.planet.myplanet.lite.OfflineCourseStorage

class OfflineCourseStorageTest {

    private lateinit var context: Context
    private lateinit var tempFilesDir: File

    @Before
    fun setUp() {
        val tempFile = File.createTempFile("test_files_dir", "")
        tempFile.delete() // Delete the file so we can create a directory with the same name
        tempFile.mkdir()
        tempFilesDir = tempFile

        context = Mockito.mock(Context::class.java)
        Mockito.`when`(context.filesDir).thenReturn(tempFilesDir)
    }

    @After
    fun tearDown() {
        tempFilesDir.deleteRecursively()
    }

    @Test
    fun `downloadedCourseIds returns empty set when root dir does not exist`() {
        // Assert that the ROOT_DIR (".offline_courses") does not exist in tempFilesDir initially
        val rootDir = File(tempFilesDir, ".offline_courses")
        Assert.assertEquals(false, rootDir.exists())

        val result = OfflineCourseStorage.downloadedCourseIds(context)
        Assert.assertEquals(emptySet<String>(), result)
    }

    @Test
    fun `downloadedCourseIds returns empty set when root dir is a file`() {
        // Create root dir as a regular file
        val rootDir = File(tempFilesDir, ".offline_courses")
        rootDir.createNewFile()

        val result = OfflineCourseStorage.downloadedCourseIds(context)
        Assert.assertEquals(emptySet<String>(), result)
    }

    @Test
    fun `downloadedCourseIds returns empty set when root dir is empty`() {
        val rootDir = File(tempFilesDir, ".offline_courses")
        rootDir.mkdirs()

        val result = OfflineCourseStorage.downloadedCourseIds(context)
        Assert.assertEquals(emptySet<String>(), result)
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
        Assert.assertEquals(setOf("valid_course"), result)
    }

    @Test
    fun `deleteCourse returns false for blank or empty courseId`() {
        Assert.assertFalse(OfflineCourseStorage.deleteCourse(context, ""))
        Assert.assertFalse(OfflineCourseStorage.deleteCourse(context, "   "))
    }

    @Test
    fun `deleteCourse returns false for path traversal attempts`() {
        Assert.assertFalse(OfflineCourseStorage.deleteCourse(context, "../../../etc/passwd"))
        Assert.assertFalse(OfflineCourseStorage.deleteCourse(context, "..\\..\\windows\\system32"))
        Assert.assertFalse(OfflineCourseStorage.deleteCourse(context, "some/course/id"))
        Assert.assertFalse(OfflineCourseStorage.deleteCourse(context, "course..id"))
    }

    @Test
    fun `deleteCourse returns true if course directory does not exist`() {
        val courseId = "non_existent_course"
        val courseDir = File(File(tempFilesDir, ".offline_courses"), courseId)
        Assert.assertFalse(courseDir.exists())

        val result = OfflineCourseStorage.deleteCourse(context, courseId)
        Assert.assertTrue(result)
    }

    @Test
    fun `deleteCourse deletes course directory and its contents recursively`() {
        val courseId = "course_to_delete"
        val rootDir = File(tempFilesDir, ".offline_courses")
        val courseDir = File(rootDir, courseId)
        courseDir.mkdirs()

        // Create some files and subdirectories
        File(courseDir, "course.json").createNewFile()
        val resourcesDir = File(courseDir, "resources")
        resourcesDir.mkdir()
        File(resourcesDir, "file1.txt").createNewFile()

        Assert.assertTrue(courseDir.exists())
        Assert.assertTrue(File(courseDir, "course.json").exists())
        Assert.assertTrue(File(resourcesDir, "file1.txt").exists())

        val result = OfflineCourseStorage.deleteCourse(context, courseId)

        Assert.assertTrue(result)
        Assert.assertFalse(courseDir.exists())
    }
}
