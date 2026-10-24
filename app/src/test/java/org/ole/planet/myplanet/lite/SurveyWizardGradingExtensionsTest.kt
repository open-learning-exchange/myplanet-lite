package org.ole.planet.myplanet.lite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyChoice
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyDocument
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyQuestion
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SurveyWizardGradingExtensionsTest {

    private val fragment = SurveyWizardFragment()

    @Test
    fun `29 of 50 marks passes a 58 percent exam`() {
        givenExam(passingPercentage = 58, marks = listOf(29, 21), correct = listOf(true, false))

        assertTrue(fragment.isExamPassing())
    }

    @Test
    fun `28 of 50 marks fails a 58 percent exam`() {
        givenExam(passingPercentage = 58, marks = listOf(28, 22), correct = listOf(true, false))

        assertFalse(fragment.isExamPassing())
    }

    @Test
    fun `an exam worth zero marks passes only when every answer is correct`() {
        givenExam(passingPercentage = 58, marks = listOf(0, 0), correct = listOf(true, true))
        assertTrue(fragment.isExamPassing())

        givenExam(passingPercentage = 58, marks = listOf(0, 0), correct = listOf(true, false))
        assertFalse(fragment.isExamPassing())
    }

    @Test
    fun `only a completed exam attempt passes the step`() {
        givenExam(passingPercentage = 58, marks = listOf(29, 21), correct = listOf(true, false))
        fragment.isExam = true

        assertTrue(fragment.isExamAttemptPassing("complete"))
        assertFalse(fragment.isExamAttemptPassing("requires grading"))
        assertFalse(fragment.isExamAttemptPassing("pending"))
        fragment.isExam = false
        assertFalse(fragment.isExamAttemptPassing("complete"))
    }

    @Test
    fun `a completed attempt below the pass mark doesn't pass the step`() {
        givenExam(passingPercentage = 58, marks = listOf(28, 22), correct = listOf(true, false))
        fragment.isExam = true

        assertFalse(fragment.isExamAttemptPassing("complete"))
    }

    @Test
    fun `exam answers are graded 1 or 0 and written answers are left for grading`() {
        val questions = listOf(
            SurveyQuestion(body = "Pick", type = "select", correctChoice = "right", marks = 2,
                choices = listOf(SurveyChoice(id = "right", text = "Right"), SurveyChoice(id = "wrong", text = "Wrong"))),
            SurveyQuestion(body = "Explain", type = "textarea", marks = 3),
        )
        fragment.document = SurveyDocument(id = "exam1", passingPercentage = 100, questions = questions)
        fragment.questions = questions
        fragment.isExam = true
        fragment.answers.clear()
        fragment.answers[0] = SurveyAnswer.SingleChoice(SelectedOption(id = "right", text = "Right"))
        fragment.answers[1] = SurveyAnswer.Text("Because")

        val (answers, totalGrade) = fragment.buildSubmissionPayload()

        assertEquals(listOf(1, null), answers.map { it.grade })
        assertEquals(2, totalGrade)
    }

    private fun givenExam(
        passingPercentage: Int,
        marks: List<Int>,
        correct: List<Boolean>,
    ) {
        val questions =
            marks.map { mark ->
                SurveyQuestion(
                    body = "Question",
                    type = "select",
                    correctChoice = "right",
                    marks = mark,
                    choices = listOf(SurveyChoice(id = "right", text = "Right"), SurveyChoice(id = "wrong", text = "Wrong")),
                )
            }
        fragment.document = SurveyDocument(id = "exam1", passingPercentage = passingPercentage, questions = questions)
        fragment.answers.clear()
        correct.forEachIndexed { index, isCorrect ->
            val choice = if (isCorrect) SelectedOption(id = "right", text = "Right") else SelectedOption(id = "wrong", text = "Wrong")
            fragment.answers[index] = SurveyAnswer.SingleChoice(choice)
        }
    }
}
