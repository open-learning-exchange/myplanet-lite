package org.ole.planet.myplanet.lite

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyDocument
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyQuestion
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyChoice

@RunWith(RobolectricTestRunner::class)
class OfflineCourseStorageTest {

    private lateinit var context: Context
    private val courseId = "test_course_id"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val offlineDir = File(context.filesDir, ".offline_courses")
        if (offlineDir.exists()) {
            offlineDir.deleteRecursively()
        }
    }

    @Test
    fun `isCourseDownloaded returns false initially`() {
        assertFalse(OfflineCourseStorage.isCourseDownloaded(context, courseId))
    }

    @Test
    fun `downloadedCourseIds returns empty set initially`() {
        assertTrue(OfflineCourseStorage.downloadedCourseIds(context).isEmpty())
    }

    @Test
    fun `saveCourseManifest and loadDownloadedCourses`() {
        val course = DashboardCoursePageFragment.CourseItem(
            id = courseId,
            title = "Test Course",
            description = "Test Description",
            coverPath = "test_cover.jpg",
            steps = listOf(
                DashboardCoursePageFragment.CourseItem.LessonStep(
                    title = "Step 1",
                    description = "Step 1 Description",
                    mediaTypes = listOf("video", "pdf"),
                    resources = listOf(
                        DashboardCoursePageFragment.CourseItem.LessonResource(
                            id = "res1",
                            filename = "file1.pdf",
                            mediaType = "pdf"
                        )
                    ),
                    survey = SurveyDocument(
                        id = "survey1",
                        rev = "rev1",
                        name = "Survey 1",
                        description = "Survey Description",
                        passingPercentage = 80,
                        sourceSurveyId = "src1",
                        teamId = "team1",
                        createdDate = "2024-01-01",
                        totalMarks = 100,
                        questions = listOf(
                            SurveyQuestion(
                                body = "Question 1?",
                                type = "text",
                                correctChoice = "A",
                                marks = 10,
                                hasOtherOption = false,
                                choices = listOf(
                                    SurveyChoice(text = "A", id = "c1"),
                                    SurveyChoice(text = "B", id = "c2")
                                )
                            )
                        )
                    )
                )
            ),
            rating = 4.5,
            progressPercent = 50,
            currentStep = 1
        )

        OfflineCourseStorage.saveCourseManifest(context, course)

        assertTrue(OfflineCourseStorage.isCourseDownloaded(context, courseId))
        assertEquals(setOf(courseId), OfflineCourseStorage.downloadedCourseIds(context))

        val loadedCourses = OfflineCourseStorage.loadDownloadedCourses(context)
        assertEquals(1, loadedCourses.size)
        val loadedCourse = loadedCourses[0]
        assertEquals(courseId, loadedCourse.id)
        assertEquals("Test Course", loadedCourse.title)
        assertEquals("Test Description", loadedCourse.description)
        assertEquals("test_cover.jpg", loadedCourse.coverPath)
        assertEquals(4.5, loadedCourse.rating, 0.0)
        assertEquals(50, loadedCourse.progressPercent)
        assertEquals(1, loadedCourse.currentStep)

        assertEquals(1, loadedCourse.steps.size)
        val loadedStep = loadedCourse.steps[0]
        assertEquals("Step 1", loadedStep.title)
        assertEquals("Step 1 Description", loadedStep.description)
        assertEquals(listOf("video", "pdf"), loadedStep.mediaTypes)

        assertEquals(1, loadedStep.resources.size)
        val loadedResource = loadedStep.resources[0]
        assertEquals("res1", loadedResource.id)
        assertEquals("file1.pdf", loadedResource.filename)
        assertEquals("pdf", loadedResource.mediaType)

        assertNotNull(loadedStep.survey)
        val loadedSurvey = loadedStep.survey!!
        assertEquals("survey1", loadedSurvey.id)
        assertEquals("rev1", loadedSurvey.rev)
        assertEquals("Survey 1", loadedSurvey.name)
        assertEquals("Survey Description", loadedSurvey.description)
        assertEquals(80, loadedSurvey.passingPercentage)
        assertEquals("src1", loadedSurvey.sourceSurveyId)
        assertEquals("team1", loadedSurvey.teamId)
        assertEquals("2024-01-01", loadedSurvey.createdDate)
        assertEquals(100, loadedSurvey.totalMarks)

        assertEquals(1, loadedSurvey.questions?.size)
        val loadedQuestion = loadedSurvey.questions!![0]
        assertEquals("Question 1?", loadedQuestion.body)
        assertEquals("text", loadedQuestion.type)
        assertEquals("A", loadedQuestion.correctChoice)
        assertEquals(10, loadedQuestion.marks)
        assertFalse(loadedQuestion.hasOtherOption)

        assertEquals(2, loadedQuestion.choices?.size)
        assertEquals("A", loadedQuestion.choices?.get(0)?.text)
        assertEquals("c1", loadedQuestion.choices?.get(0)?.id)
    }

    @Test
    fun `resourceFile returns correct file path`() {
        val resourceFile = OfflineCourseStorage.resourceFile(context, courseId, "res1", "file1.pdf")
        val expectedPath = File(File(context.filesDir, ".offline_courses/$courseId"), "resources/res1/file1.pdf").absolutePath
        assertEquals(expectedPath, resourceFile.absolutePath)
    }

    @Test
    fun `findExistingResourceFile returns null when not exists`() {
        assertNull(OfflineCourseStorage.findExistingResourceFile(context, courseId, "res1", "file1.pdf"))
    }

    @Test
    fun `findExistingResourceFile returns file when exists`() {
        val resourceFile = OfflineCourseStorage.resourceFile(context, courseId, "res1", "file1.pdf")
        resourceFile.parentFile?.mkdirs()
        resourceFile.createNewFile()

        val foundFile = OfflineCourseStorage.findExistingResourceFile(context, courseId, "res1", "file1.pdf")
        assertNotNull(foundFile)
        assertEquals(resourceFile.absolutePath, foundFile?.absolutePath)
    }

    @Test
    fun `deleteCourse removes course directory`() {
        val courseDir = File(context.filesDir, ".offline_courses/$courseId")
        courseDir.mkdirs()
        assertTrue(courseDir.exists())

        assertTrue(OfflineCourseStorage.deleteCourse(context, courseId))
        assertFalse(courseDir.exists())
    }

    @Test
    fun `deleteCourse returns false for empty courseId`() {
        assertFalse(OfflineCourseStorage.deleteCourse(context, ""))
    }

    @Test
    fun `markdownImageFile returns correct file path`() {
        val source = "https://example.com/image.jpg"
        val markdownFile = OfflineCourseStorage.markdownImageFile(context, courseId, source)

        assertTrue(markdownFile.absolutePath.contains(".offline_courses/$courseId/markdown/"))
        assertTrue(markdownFile.absolutePath.endsWith(".jpg"))
    }

    @Test
    fun `localMarkdownImageUri returns uri when file exists`() {
        val source = "https://example.com/image.jpg"
        val markdownFile = OfflineCourseStorage.markdownImageFile(context, courseId, source)
        markdownFile.parentFile?.mkdirs()
        markdownFile.createNewFile()

        val uri = OfflineCourseStorage.localMarkdownImageUri(context, courseId, source)
        assertNotNull(uri)
        assertTrue(uri?.startsWith("file:/") == true)
        assertTrue(uri?.endsWith(".jpg") == true)
    }

    @Test
    fun `localMarkdownImageUri returns null when file not exists`() {
        val source = "https://example.com/image.jpg"
        assertNull(OfflineCourseStorage.localMarkdownImageUri(context, courseId, source))
    }

    @Test
    fun `availableStorageBytes returns non-negative value`() {
        val space = OfflineCourseStorage.availableStorageBytes(context)
        assertTrue(space >= 0)
    }
}
