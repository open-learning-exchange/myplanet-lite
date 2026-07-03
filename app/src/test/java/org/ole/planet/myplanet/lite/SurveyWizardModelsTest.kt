package org.ole.planet.myplanet.lite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyDocument
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyQuestion
import org.ole.planet.myplanet.lite.profile.UserProfile

class SurveyWizardModelsTest {

    @Test
    fun `test SelectedOption toSubmissionValue`() {
        val selectedOption = SelectedOption(id = "opt1", text = "Option 1", isOther = true)
        val submissionValue = selectedOption.toSubmissionValue()

        assertEquals("opt1", submissionValue.id)
        assertEquals("Option 1", submissionValue.text)
        assertTrue(submissionValue.isOther)
    }

    @Test
    fun `test SelectedOption default isOther is false`() {
        val selectedOption = SelectedOption(id = "opt2", text = "Option 2")
        assertFalse(selectedOption.isOther)

        val submissionValue = selectedOption.toSubmissionValue()
        assertFalse(submissionValue.isOther)
    }

    @Test
    fun `test SurveyRespondent default values`() {
        val respondent = SurveyRespondent()

        assertNull(respondent.gender)
        assertNull(respondent.birthYear)
        assertNull(respondent.age)
        assertFalse(respondent.additionalInfo)
        assertNull(respondent.firstName)
        assertNull(respondent.middleName)
        assertNull(respondent.lastName)
        assertNull(respondent.birthDate)
        assertNull(respondent.email)
        assertNull(respondent.phoneNumber)
        assertNull(respondent.language)
        assertNull(respondent.level)
    }

    @Test
    fun `test SurveyRespondent modify values`() {
        val respondent = SurveyRespondent()
        respondent.firstName = "John"
        respondent.lastName = "Doe"
        respondent.age = 30

        assertEquals("John", respondent.firstName)
        assertEquals("Doe", respondent.lastName)
        assertEquals(30, respondent.age)
    }

    @Test
    fun `test WizardStep objects`() {
        val basics: WizardStep = WizardStep.Basics
        val names: WizardStep = WizardStep.Names
        val birthDate: WizardStep = WizardStep.BirthDate
        val contact: WizardStep = WizardStep.Contact
        val languageLevel: WizardStep = WizardStep.LanguageLevel

        assertEquals(WizardStep.Basics, basics)
        assertEquals(WizardStep.Names, names)
        assertEquals(WizardStep.BirthDate, birthDate)
        assertEquals(WizardStep.Contact, contact)
        assertEquals(WizardStep.LanguageLevel, languageLevel)
    }

    @Test
    fun `test WizardStep Question`() {
        val question = SurveyQuestion(body = "What?", type = "text", choices = emptyList())
        val step = WizardStep.Question(question = question, questionIndex = 5)

        assertEquals("What?", step.question.body)
        assertEquals(5, step.questionIndex)
    }

    @Test
    fun `test constants`() {
        assertEquals("other_choice", OTHER_CHOICE_TAG)
        assertEquals("survey_birth_date_picker", BIRTH_DATE_PICKER_TAG)
        assertEquals(9, DEFAULT_RATING_SCALE_MAX)
        assertEquals(5, MAX_SINGLE_ROW_RATING_SCALE)
        assertEquals(3, DEFAULT_MULTI_ROW_COLUMN_COUNT)
        assertEquals(100, DEFAULT_EXAM_PASSING_PERCENTAGE)
        assertEquals("device_custom_device_name", KEY_DEVICE_CUSTOM_DEVICE_NAME)
        assertEquals("Anonymous", ANONYMOUS_RESPONDENT_NAME)
    }

    @Test
    fun `test SurveyAnswer sealed classes`() {
        val textAnswer = SurveyAnswer.Text("Answer text")
        assertEquals("Answer text", textAnswer.value)

        val singleChoiceAnswer = SurveyAnswer.SingleChoice(SelectedOption("1", "A"))
        assertEquals("1", singleChoiceAnswer.choice?.id)
        assertEquals("A", singleChoiceAnswer.choice?.text)

        val multipleChoiceAnswer = SurveyAnswer.MultipleChoice(listOf(SelectedOption("1", "A"), SelectedOption("2", "B")))
        assertEquals(2, multipleChoiceAnswer.choices.size)
        assertEquals("2", multipleChoiceAnswer.choices[1].id)
        assertEquals("B", multipleChoiceAnswer.choices[1].text)

        val ratingAnswer = SurveyAnswer.Rating(5)
        assertEquals(5, ratingAnswer.score)
    }

    @Test
    fun `test SubmissionOptionValue defaults`() {
        val optionValue = SubmissionOptionValue(id = "val1", text = "Value 1")
        assertEquals("val1", optionValue.id)
        assertEquals("Value 1", optionValue.text)
        assertFalse(optionValue.isOther)
    }

    @Test
    fun `test SurveySubmissionParams`() {
        val survey = SurveyDocument(id = "survey123")
        val params = SurveySubmissionParams(
            survey = survey,
            existingSubmission = null,
            username = "test_user",
            fullName = "Test User",
            parentId = "parent1",
            answersPayload = emptyList(),
            totalGrade = 100,
            profile = null
        )

        assertEquals("survey123", params.survey.id)
        assertNull(params.existingSubmission)
        assertEquals("test_user", params.username)
        assertEquals("Test User", params.fullName)
        assertEquals("parent1", params.parentId)
        assertTrue(params.answersPayload.isEmpty())
        assertEquals(100, params.totalGrade)
        assertNull(params.profile)
    }
}
