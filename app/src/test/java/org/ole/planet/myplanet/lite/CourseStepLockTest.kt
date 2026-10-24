package org.ole.planet.myplanet.lite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ole.planet.myplanet.lite.dashboard.DashboardCoursesRepository.CourseDocument
import org.ole.planet.myplanet.lite.dashboard.DashboardCoursesRepository.CourseStep
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyDocument
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyQuestion

class CourseStepLockTest {

    private val exam = SurveyDocument(id = "exam1", questions = listOf(SurveyQuestion(body = "Q", type = "select")))

    @Test
    fun `course steps carry passingRequired and default to false`() {
        val course =
            CourseDocument(
                id = "course1",
                courseTitle = "Course",
                description = null,
                steps =
                    listOf(
                        CourseStep(stepTitle = "Intro"),
                        CourseStep(stepTitle = "Quiz", exam = exam, passingRequired = true),
                    ),
            ).toCourseItem(defaultTitle = "Course", stepNum = null)

        assertFalse(course.steps[0].passingRequired)
        assertTrue(course.steps[1].passingRequired)
        assertTrue(course.steps[1].requiresPass)
    }

    @Test
    fun `passingRequired without exam questions requires nothing`() {
        val step = lessonStep(passingRequired = true, exam = SurveyDocument(id = "empty", questions = emptyList()))
        assertFalse(step.requiresPass)
        assertFalse(StepDisplay("Step", "", emptyList(), passingRequired = true).requiresPass)
    }

    @Test
    fun `course page start step stops at the step whose exam must be passed`() {
        val course = courseItem(currentStep = 3)

        assertEquals(1, course.startStepIndex(emptySet()))
    }

    @Test
    fun `next keeps its other rules for exams without the flag`() {
        val ungated = StepDisplay("Quiz", "", emptyList(), exam = exam)

        assertFalse(isStepPassPending(ungated, stepIndex = 1, passedStepNumbers = emptySet()))
    }

    private fun courseItem(currentStep: Int?): CourseItem =
        CourseItem(
            id = "course1",
            title = "Course",
            description = "",
            coverPath = null,
            steps =
                listOf(
                    lessonStep(),
                    lessonStep(exam = exam, passingRequired = true),
                    lessonStep(),
                    lessonStep(),
                ),
            rating = 4.0,
            progressPercent = 0,
            currentStep = currentStep,
        )

    private fun lessonStep(
        exam: SurveyDocument? = null,
        passingRequired: Boolean = false,
    ) = CourseItem.LessonStep(
        title = "Step",
        description = "",
        mediaTypes = emptyList(),
        exam = exam,
        passingRequired = passingRequired,
    )
}
