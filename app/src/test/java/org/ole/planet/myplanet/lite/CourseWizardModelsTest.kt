package org.ole.planet.myplanet.lite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyDocument

class CourseWizardModelsTest {

    @Test
    fun testStepDisplayDefaults() {
        val resources = listOf(
            CourseItem.LessonResource(
                id = "res1",
                filename = "file1.pdf",
                mediaType = "application/pdf"
            )
        )

        val stepDisplay = StepDisplay(
            title = "Step 1",
            description = "Description 1",
            resources = resources
        )

        assertEquals("Step 1", stepDisplay.title)
        assertEquals("Description 1", stepDisplay.description)
        assertEquals(resources, stepDisplay.resources)
        assertNull(stepDisplay.survey)
        assertNull(stepDisplay.exam)
    }

    @Test
    fun testStepDisplayFullInitialization() {
        val resources = listOf(
            CourseItem.LessonResource(
                id = "res2",
                filename = "file2.mp4",
                mediaType = "video/mp4"
            )
        )
        val survey = SurveyDocument(id = "survey1", name = "Test Survey")
        val exam = SurveyDocument(id = "exam1", name = "Test Exam")

        val stepDisplay = StepDisplay(
            title = "Step 2",
            description = "Description 2",
            resources = resources,
            survey = survey,
            exam = exam
        )

        assertEquals("Step 2", stepDisplay.title)
        assertEquals("Description 2", stepDisplay.description)
        assertEquals(resources, stepDisplay.resources)
        assertEquals(survey, stepDisplay.survey)
        assertEquals(exam, stepDisplay.exam)
    }

    @Test
    fun testStepDisplayCopy() {
        val resources = listOf(
            CourseItem.LessonResource(
                id = "res3",
                filename = "file3.png",
                mediaType = "image/png"
            )
        )
        val survey = SurveyDocument(id = "survey2", name = "Survey 2")
        val original = StepDisplay(
            title = "Original Title",
            description = "Original Description",
            resources = resources,
            survey = survey
        )

        val copy = original.copy(title = "Copied Title", exam = SurveyDocument(id = "exam2", name = "Exam 2"))

        assertEquals("Copied Title", copy.title)
        assertEquals("Original Description", copy.description)
        assertEquals(resources, copy.resources)
        assertEquals(survey, copy.survey)
        assertEquals("exam2", copy.exam?.id)
        assertEquals("Exam 2", copy.exam?.name)

        assertNotEquals(original.title, copy.title)
        assertNotEquals(original.exam, copy.exam)
    }

    @Test
    fun testStepDisplayEquality() {
        val resources = listOf(
            CourseItem.LessonResource(
                id = "res4",
                filename = "file4.txt",
                mediaType = "text/plain"
            )
        )
        val survey = SurveyDocument(id = "survey3", name = "Survey 3")

        val display1 = StepDisplay(
            title = "Equality Step",
            description = "Equality Description",
            resources = resources,
            survey = survey
        )

        val display2 = StepDisplay(
            title = "Equality Step",
            description = "Equality Description",
            resources = resources,
            survey = survey
        )

        val display3 = StepDisplay(
            title = "Different Step",
            description = "Equality Description",
            resources = resources,
            survey = survey
        )

        assertEquals(display1, display2)
        assertEquals(display1.hashCode(), display2.hashCode())

        assertNotEquals(display1, display3)
        assertNotEquals(display1.hashCode(), display3.hashCode())
    }
}
