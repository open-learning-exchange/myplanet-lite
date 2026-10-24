package org.ole.planet.myplanet.lite

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
