package org.ole.planet.myplanet.lite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SurveyWizardModelsTest {

    @Test
    fun `SelectedOption toSubmissionValue maps fields correctly`() {
        val selectedOption = SelectedOption(id = "opt1", text = "Option 1", isOther = true)
        val submissionValue = selectedOption.toSubmissionValue()

        assertEquals("opt1", submissionValue.id)
        assertEquals("Option 1", submissionValue.text)
        assertTrue(submissionValue.isOther)
    }

    @Test
    fun `SelectedOption default isOther is false`() {
        val selectedOption = SelectedOption(id = "opt2", text = "Option 2")
        assertFalse(selectedOption.isOther)

        val submissionValue = selectedOption.toSubmissionValue()
        assertFalse(submissionValue.isOther)
    }

    @Test
    fun `SurveyRespondent initializes with correct default values`() {
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
}
