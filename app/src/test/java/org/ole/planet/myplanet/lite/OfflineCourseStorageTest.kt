package org.ole.planet.myplanet.lite

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyChoice
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyDocument
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyQuestion
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class OfflineCourseStorageTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val rootDir = File(context.filesDir, ".offline_courses")
        if (rootDir.exists()) {
            rootDir.deleteRecursively()
        }
    }

    @After
    fun tearDown() {
        val rootDir = File(context.filesDir, ".offline_courses")
        if (rootDir.exists()) {
            rootDir.deleteRecursively()
        }
    }

    private fun normalizedPath(file: File): String {
        return file.absolutePath.replace('\\', '/')
    }

    private fun createDummyCourse(id: String = "test-course-1"): CourseItem {
        val survey = SurveyDocument(
            id = "survey-1",
            rev = "1-rev",
            name = "Test Survey",
            description = "A survey for testing",
            passingPercentage = 50,
            sourceSurveyId = "src-survey-1",
            teamId = "team-1",
            createdDate = "2023-10-01",
            questions = listOf(
                SurveyQuestion(
                    body = "Question 1",
                    type = "radio",
                    marks = 5,
                    scaleMax = 5,
                    choices = listOf(
                        SurveyChoice(text = "Choice A", id = "A"),
                        SurveyChoice(text = "Choice B", id = "B")
                    )
                )
            ),
            totalMarks = 5
        )

        return CourseItem(
            id = id,
            title = "Test Course",
            description = "Test description",
            coverPath = "/path/to/cover",
            rating = 4.5,
            progressPercent = 10,
            currentStep = 0,
            steps = listOf(
                CourseItem.LessonStep(
                    title = "Step 1",
                    description = "First step",
                    mediaTypes = listOf("pdf", "video"),
                    resources = listOf(
                        CourseItem.LessonResource(
                            id = "res-1",
                            filename = "doc1.pdf",
                            mediaType = "pdf"
                        )
                    ),
                    survey = survey,
                    exam = null
                )
            )
        )
    }

    @Test
    fun testIsCourseDownloaded_whenNotDownloaded() {
        assertFalse(OfflineCourseStorage.isCourseDownloaded(context, "missing-course"))
    }

    @Test
    fun testDownloadedCourseIds_whenEmpty() {
        val ids = OfflineCourseStorage.downloadedCourseIds(context)
        assertTrue(ids.isEmpty())
    }

    @Test
    fun testSaveAndLoadCourse() {
        val course = createDummyCourse("course-123")
        OfflineCourseStorage.saveCourseManifest(context, course, OfflineCourseStorage.DownloadSource.MY_COURSES)

        assertTrue(OfflineCourseStorage.isCourseDownloaded(context, "course-123"))

        val ids = OfflineCourseStorage.downloadedCourseIds(context)
        assertEquals(setOf("course-123"), ids)

        val courses = OfflineCourseStorage.loadDownloadedCourses(context)
        assertEquals(1, courses.size)

        val loadedCourse = courses.first()
        assertEquals("course-123", loadedCourse.id)
        assertEquals("Test Course", loadedCourse.title)
        assertEquals("Test description", loadedCourse.description)
        assertEquals("/path/to/cover", loadedCourse.coverPath)
        assertEquals(4.5, loadedCourse.rating, 0.01)
        assertEquals(10, loadedCourse.progressPercent)
        assertEquals(0, loadedCourse.currentStep)

        assertEquals(1, loadedCourse.steps.size)

        val step = loadedCourse.steps.first()
        assertEquals("Step 1", step.title)
        assertEquals("First step", step.description)
        assertEquals(listOf("pdf", "video"), step.mediaTypes)

        assertEquals(1, step.resources.size)

        val resource = step.resources.first()
        assertEquals("res-1", resource.id)
        assertEquals("doc1.pdf", resource.filename)
        assertEquals("pdf", resource.mediaType)

        assertNotNull(step.survey)
        assertEquals("survey-1", step.survey?.id)
        assertEquals("1-rev", step.survey?.rev)
        assertEquals("Test Survey", step.survey?.name)
        assertEquals(1, step.survey?.questions?.size)

        val question = step.survey?.questions?.first()
        assertEquals("Question 1", question?.body)
        assertEquals("radio", question?.type)
        assertEquals(5, question?.marks)
        assertEquals(5, question?.scaleMax)
        assertEquals(2, question?.choices?.size)

        assertNull(step.exam)
    }

    @Test
    fun `saved manifest uses private local cover when it was downloaded`() {
        val course = createDummyCourse("course-with-cover")
        val cover = OfflineCourseStorage.coverFile(context, course.id, course.coverPath!!)
        cover.parentFile?.mkdirs()
        cover.writeBytes(byteArrayOf(1, 2, 3))

        OfflineCourseStorage.saveCourseManifest(
            context,
            course,
            OfflineCourseStorage.DownloadSource.MY_COURSES,
        )

        val loadedCourse = OfflineCourseStorage.loadDownloadedCourses(context).first()
        assertEquals(cover.toURI().toString(), loadedCourse.coverPath)
        assertTrue(cover.absolutePath.startsWith(context.filesDir.absolutePath))
    }

    @Test
    fun testLoadDownloadedCourses_withCorruptedJson() {
        val dir = File(File(context.filesDir, ".offline_courses"), "corrupted-course")
        dir.mkdirs()
        File(dir, "course.json").writeText("{ invalid_json ]")

        val courses = OfflineCourseStorage.loadDownloadedCourses(context)
        assertTrue(courses.isEmpty())
    }

    @Test
    fun testResourceFile() {
        val file = OfflineCourseStorage.resourceFile(context, "course-1", "res-1", "file.pdf")
        assertTrue(normalizedPath(file).endsWith(".offline_courses/course-1/resources/res-1/file.pdf"))
    }

    @Test
    fun testFindExistingResourceFile() {
        val courseId = "course-1"
        val resourceId = "res-1"
        val filename = "my doc.pdf"

        assertNull(OfflineCourseStorage.findExistingResourceFile(context, courseId, resourceId, filename))

        val expectedFile = OfflineCourseStorage.resourceFile(context, courseId, resourceId, "my%20doc.pdf")
        expectedFile.parentFile?.mkdirs()
        expectedFile.writeText("dummy content")

        val found = OfflineCourseStorage.findExistingResourceFile(context, courseId, resourceId, filename)
        assertNotNull(found)
        assertEquals(expectedFile.absolutePath, found?.absolutePath)

        val exactFile = OfflineCourseStorage.resourceFile(context, courseId, resourceId, "exact.pdf")
        exactFile.parentFile?.mkdirs()
        exactFile.writeText("dummy")

        val foundExact = OfflineCourseStorage.findExistingResourceFile(context, courseId, resourceId, "exact.pdf")
        assertNotNull(foundExact)
        assertEquals(exactFile.absolutePath, foundExact?.absolutePath)
    }

    @Test
    fun testDeleteCourse() {
        val courseId = "course-to-delete"
        val course = createDummyCourse(courseId)

        OfflineCourseStorage.saveCourseManifest(context, course, OfflineCourseStorage.DownloadSource.MY_COURSES)

        assertTrue(OfflineCourseStorage.isCourseDownloaded(context, courseId))

        val deleted = OfflineCourseStorage.deleteCourse(context, courseId)

        assertTrue(deleted)
        assertFalse(OfflineCourseStorage.isCourseDownloaded(context, courseId))
    }

    @Test
    fun testDeleteCourse_emptyOrBlankId() {
        assertFalse(OfflineCourseStorage.deleteCourse(context, ""))
        assertFalse(OfflineCourseStorage.deleteCourse(context, "   "))
    }

    @Test
    fun testAvailableStorageBytes() {
        val bytes = OfflineCourseStorage.availableStorageBytes(context)
        assertTrue(bytes > 0L)
    }

    @Test
    fun testMarkdownImageFile() {
        val courseId = "course-img"
        val source = "https://example.com/image.png"
        val file = OfflineCourseStorage.markdownImageFile(context, courseId, source)

        assertTrue(file.absolutePath.endsWith(".png"))
        assertTrue(normalizedPath(file).contains(".offline_courses/course-img/markdown/"))
    }

    @Test
    fun testLocalMarkdownImageUri_migratesLegacySha1File() {
        val courseId = "course-img-migrate"
        val source = "https://example.com/legacy.png"

        val digest = java.security.MessageDigest.getInstance("SHA-1").digest(source.toByteArray())
        val sha1Hex = digest.joinToString("") { "%02x".format(it) }

        val legacyFile = File(File(File(context.filesDir, ".offline_courses"), courseId), "markdown/$sha1Hex.png")
        legacyFile.parentFile?.mkdirs()
        legacyFile.writeText("legacy image data")

        val newFile = OfflineCourseStorage.markdownImageFile(context, courseId, source)
        assertFalse(newFile.exists())

        val uri = OfflineCourseStorage.localMarkdownImageUri(context, courseId, source)

        assertNotNull(uri)
        assertTrue(uri?.startsWith("file:/") == true)
        assertTrue(uri?.endsWith(".png") == true)

        assertTrue(newFile.exists())
        assertEquals("legacy image data", newFile.readText())
        assertFalse(legacyFile.exists())
    }

    @Test
    fun testLocalMarkdownImageUri() {
        val courseId = "course-img-uri"
        val source = "https://example.com/img2.jpg?test=1#anchor"

        assertNull(OfflineCourseStorage.localMarkdownImageUri(context, courseId, source))

        val file = OfflineCourseStorage.markdownImageFile(context, courseId, source)
        file.parentFile?.mkdirs()
        file.writeText("image data")

        val uri = OfflineCourseStorage.localMarkdownImageUri(context, courseId, source)

        assertNotNull(uri)
        assertTrue(uri?.startsWith("file:/") == true)
        assertTrue(uri?.endsWith(".jpg") == true)
    }

    @Test
    fun testSha256() {
        val method = OfflineCourseStorage::class.java.getDeclaredMethod("sha256", String::class.java)
        method.isAccessible = true

        val emptyHash = method.invoke(OfflineCourseStorage, "") as String
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", emptyHash)

        val testHash = method.invoke(OfflineCourseStorage, "test") as String
        assertEquals("9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08", testHash)

        val testStringHash = method.invoke(OfflineCourseStorage, "test string") as String
        assertEquals("d5579c46dfcc7f18207013e65b44e4cb4e2c2298f4ac457ba8f82743f31e930b", testStringHash)

        val specialCharsHash = method.invoke(OfflineCourseStorage, "test@123! \n") as String
        assertEquals("0cfd4bc47bbb9810356c301eb59acad0362c2846fdf3197115e8e2f1842f43fd", specialCharsHash)
    }

    @Test
    fun testLegacySha1() {
        val method = OfflineCourseStorage::class.java.getDeclaredMethod("legacySha1", String::class.java)
        method.isAccessible = true

        val emptyHash = method.invoke(OfflineCourseStorage, "") as String
        assertEquals("da39a3ee5e6b4b0d3255bfef95601890afd80709", emptyHash)

        val testHash = method.invoke(OfflineCourseStorage, "test") as String
        assertEquals("a94a8fe5ccb19ba61c4c0873d391e987982fbbd3", testHash)

        val testStringHash = method.invoke(OfflineCourseStorage, "test string") as String
        assertEquals("661295c9cbf9d6b2f6428414504a8deed3020641", testStringHash)

        val specialCharsHash = method.invoke(OfflineCourseStorage, "test@123! \n") as String
        assertEquals("d15229ba5391227d0c41c5705b9d9c4bf1497b0c", specialCharsHash)
    }
}
