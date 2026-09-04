package org.ole.planet.myplanet.lite

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.fragment.app.FragmentActivity
import androidx.test.core.app.ApplicationProvider
import com.google.mlkit.common.sdkinternal.MlKitContext
import org.robolectric.Robolectric
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveySubmissionsRepository.PublicSurveyRespondent
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveySubmissionsRepository.SubmissionLookup
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyDocument
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyChoice
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyQuestion

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SurveyWizardSubmissionExtensionsTest {

    private val fragment = SurveyWizardFragment()

    @Before
    fun injectPlainPreferences() {
        // The keystore EncryptedSharedPreferences needs is not available under Robolectric.
        SecurePreferencesProvider.injectedPreferences =
            ApplicationProvider
                .getApplicationContext<android.content.Context>()
                .getSharedPreferences("survey_wizard_test_prefs", android.content.Context.MODE_PRIVATE)
    }

    @After
    fun clearInjectedPreferences() {
        SecurePreferencesProvider.injectedPreferences = null
    }

    @Test
    fun testNormalizeSelectedId() {
        assertEquals("123", fragment.normalizeSelectedId("123"))
        assertEquals("123", fragment.normalizeSelectedId(" 123 "))
        assertEquals("123", fragment.normalizeSelectedId("123/456"))
        assertEquals(null, fragment.normalizeSelectedId(""))
        assertEquals(null, fragment.normalizeSelectedId(null))
    }

    @Test
    fun testNormalizeCorrectChoice() {
        // String
        assertEquals(listOf("correct"), fragment.normalizeCorrectChoice("correct"))

        // List of strings
        assertEquals(listOf("c1", "c2"), fragment.normalizeCorrectChoice(listOf("c1", "c2")))

        // List of maps
        val mapsList = listOf(
            mapOf("id" to "id1"),
            mapOf("_id" to "id2"),
            mapOf("text" to "text1"),
            mapOf("other" to "other")
        )

        assertEquals(listOf("id1", "id2", "text1"), fragment.normalizeCorrectChoice(mapsList))

        // Null
        assertEquals(emptyList<String>(), fragment.normalizeCorrectChoice(null))

        // Other type
        assertEquals(listOf("123"), fragment.normalizeCorrectChoice(123))
    }

    @Test
    fun testIsTextInputCorrect() {
        // Blank correct text, answer must be not blank
        assertTrue(fragment.isTextInputCorrect(emptyList(), SurveyAnswer.Text("hello")))
        assertTrue(fragment.isTextInputCorrect(listOf(" "), SurveyAnswer.Text("hello")))
        assertFalse(fragment.isTextInputCorrect(emptyList(), SurveyAnswer.Text("   ")))
        assertFalse(fragment.isTextInputCorrect(emptyList(), null))
        assertFalse(fragment.isTextInputCorrect(emptyList(), SurveyAnswer.Rating(5))) // wrong type

        // Correct text specified
        assertTrue(fragment.isTextInputCorrect(listOf("correct"), SurveyAnswer.Text("correct")))
        assertTrue(fragment.isTextInputCorrect(listOf("correct"), SurveyAnswer.Text("CORRECT")))
        assertTrue(fragment.isTextInputCorrect(listOf("correct"), SurveyAnswer.Text(" correct ")))
        assertFalse(fragment.isTextInputCorrect(listOf("correct"), SurveyAnswer.Text("wrong")))
    }

    @Test
    fun testIsRatingScaleInputCorrect() {
        assertTrue(fragment.isRatingScaleInputCorrect(listOf("5"), SurveyAnswer.Rating(5)))
        assertFalse(fragment.isRatingScaleInputCorrect(listOf("5"), SurveyAnswer.Rating(4)))
        assertFalse(fragment.isRatingScaleInputCorrect(listOf("5"), null))
        assertFalse(fragment.isRatingScaleInputCorrect(emptyList(), SurveyAnswer.Rating(5)))
    }

    @Test
    fun testIsSelectMultipleInputCorrect() {
        val correctIds = listOf("id1", "id2")
        val answer = SurveyAnswer.MultipleChoice(
            listOf(
                SelectedOption(id = "id1", text = "t1"),
                SelectedOption(id = "id2", text = "t2")
            )
        )
        assertTrue(fragment.isSelectMultipleInputCorrect(correctIds, answer))

        val answerWrongSize = SurveyAnswer.MultipleChoice(
            listOf(
                SelectedOption(id = "id1", text = "t1")
            )
        )
        assertFalse(fragment.isSelectMultipleInputCorrect(correctIds, answerWrongSize))

        val answerWrongItems = SurveyAnswer.MultipleChoice(
            listOf(
                SelectedOption(id = "id1", text = "t1"),
                SelectedOption(id = "id3", text = "t3")
            )
        )
        assertFalse(fragment.isSelectMultipleInputCorrect(correctIds, answerWrongItems))

        val answerWithOther = SurveyAnswer.MultipleChoice(
            listOf(
                SelectedOption(id = "id1", text = "t1"),
                SelectedOption(id = "id2", text = "t2", isOther = true) // Filtered out
            )
        )
        assertFalse(fragment.isSelectMultipleInputCorrect(correctIds, answerWithOther))
    }

    @Test
    fun testIsSelectInputCorrect() {
        // correctIds is not empty
        val correctIds = listOf("id1")
        val choiceTextToId = mapOf("text2" to "id2", "text1" to "id1")

        // With id
        val answerWithId = SurveyAnswer.SingleChoice(SelectedOption(id = "id1", text = "t1"))
        assertTrue(fragment.isSelectInputCorrect(correctIds, emptyList(), choiceTextToId, answerWithId))

        // With bad id but id present
        val answerWithBadId = SurveyAnswer.SingleChoice(SelectedOption(id = "id2", text = "t2"))
        assertFalse(fragment.isSelectInputCorrect(correctIds, emptyList(), choiceTextToId, answerWithBadId))

        // Without id but text matching
        val answerWithTextMatching = SurveyAnswer.SingleChoice(SelectedOption(id = null, text = "text1"))
        assertTrue(fragment.isSelectInputCorrect(correctIds, emptyList(), choiceTextToId, answerWithTextMatching))

        // correctIds is empty, check correctTexts
        val correctTexts = listOf("text1")
        val answerText = SurveyAnswer.SingleChoice(SelectedOption(id = "id1", text = "TEXT1"))
        assertTrue(fragment.isSelectInputCorrect(emptyList(), correctTexts, emptyMap(), answerText))
        val answerBadText = SurveyAnswer.SingleChoice(SelectedOption(id = "id1", text = "text2"))
        assertFalse(fragment.isSelectInputCorrect(emptyList(), correctTexts, emptyMap(), answerBadText))
    }

    @Test
    fun testIsAnswerCorrect() {
        val question = SurveyQuestion(
            type = "select",
            correctChoice = "id1",
            choices = listOf(
                SurveyChoice(id = "id1", text = "text1"),
                SurveyChoice(id = "id2", text = "text2")
            )
        )
        val answer = SurveyAnswer.SingleChoice(SelectedOption(id = "id1", text = "text1"))
        assertTrue(fragment.isAnswerCorrect(question, answer))

        val answerWrong = SurveyAnswer.SingleChoice(SelectedOption(id = "id2", text = "text2"))
        assertFalse(fragment.isAnswerCorrect(question, answerWrong))

        val unknownTypeQuestion = SurveyQuestion(type = "unknown")
        assertFalse(fragment.isAnswerCorrect(unknownTypeQuestion, answer))
    }

    @Test
    fun optIntOrNull_nullJsonObject_returnsNull() {
        val json: JSONObject? = null
        assertNull(json.optIntOrNull("key"))
    }

    @Test
    fun optIntOrNull_missingKey_returnsNull() {
        val json = JSONObject()
        assertNull(json.optIntOrNull("key"))
    }

    @Test
    fun optIntOrNull_nullValue_returnsNull() {
        val json = JSONObject().apply { put("key", JSONObject.NULL) }
        assertNull(json.optIntOrNull("key"))
    }

    @Test
    fun optIntOrNull_validNumber_returnsInt() {
        val json = JSONObject().apply { put("key", 42) }
        assertEquals(42, json.optIntOrNull("key"))

        val jsonDouble = JSONObject().apply { put("key", 42.5) }
        assertEquals(42, jsonDouble.optIntOrNull("key"))
    }

    @Test
    fun optIntOrNull_validString_returnsInt() {
        val json = JSONObject().apply { put("key", "42") }
        assertEquals(42, json.optIntOrNull("key"))
    }

    @Test
    fun optIntOrNull_invalidString_returnsNull() {
        val json = JSONObject().apply { put("key", "not a number") }
        assertNull(json.optIntOrNull("key"))
    }

    @Test
    fun optIntOrNull_booleanValue_returnsNull() {
        val json = JSONObject().apply { put("key", true) }
        assertNull(json.optIntOrNull("key"))
    }

    @Test
    fun resolveSubmissionApp_tagsNewSubmissionsOnly() {
        assertEquals("myplanet-lite", resolveSubmissionApp(null))
        assertEquals("myplanet", resolveSubmissionApp(SubmissionLookup(app = "myplanet")))
        assertNull(resolveSubmissionApp(SubmissionLookup(app = null)))
    }

    private fun attachedFragment(): SurveyWizardFragment {
        MlKitContext.initializeIfNeeded(ApplicationProvider.getApplicationContext())
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).create().start().resume().get()
        activity.setTheme(androidx.appcompat.R.style.Theme_AppCompat)
        val document = SurveyDocument(
            id = "survey1",
            rev = "rev1",
            name = "NY - Tech Pioneers",
            questions = listOf(SurveyQuestion(body = "Rate the team", type = "input"))
        )
        val attached = SurveyWizardFragment().apply {
            arguments = android.os.Bundle().apply { putSerializable("arg_document", document) }
        }
        activity.supportFragmentManager.beginTransaction()
            .add(android.R.id.content, attached, "survey")
            .commit()
        activity.supportFragmentManager.executePendingTransactions()
        return attached
    }

    private fun submissionParams(survey: SurveyDocument) = SurveySubmissionParams(
        survey = survey,
        existingSubmission = null,
        username = "gg",
        fullName = "gg",
        parentId = "survey1",
        answersPayload = emptyList(),
        totalGrade = 0,
        profile = null
    )

    @Test
    fun `team survey separates the respondent from the operator who collected it`() {
        val survey = SurveyDocument(id = "survey1", rev = "rev1", name = "NY - Tech Pioneers", teamId = "team1")
        val attached = attachedFragment().apply {
            teamId = "team1"
            teamName = "Tech Pioneers"
            courseId = null
            respondent.age = 34
            respondent.gender = "male"
        }

        val submission = attached.buildSurveySubmission(submissionParams(survey))

        // The walk-up respondent is not the signed-in operator, so the response carries no identity.
        assertNull(submission.user.id)
        assertNull(submission.user.name)
        assertEquals(34, submission.user.age)
        assertEquals(34, submission.respondent?.age)
        assertEquals("male", submission.respondent?.gender)
        assertEquals("org.couchdb.user:gg", submission.collectedBy?.id)
        assertEquals("gg", submission.collectedBy?.name)
        // The team document, not the wizard, decides whether this is a team or an enterprise.
        assertNull(submission.team?.type)
        assertEquals("team1", submission.team?.id)
    }

    @Test
    fun `course content stays attributed to the learner who answered it`() {
        val survey = SurveyDocument(id = "survey1", rev = "rev1", name = "Course survey")
        val attached = attachedFragment().apply {
            courseId = "course1"
            respondent.age = 20
            respondent.gender = "female"
        }

        val submission = attached.buildSurveySubmission(submissionParams(survey))

        assertEquals("org.couchdb.user:gg", submission.user.id)
        assertEquals("gg", submission.user.name)
        assertNull(submission.respondent)
        assertNull(submission.collectedBy)
    }

    @Test
    fun `parent snapshot reports the survey's own sharing flag`() {
        val survey = SurveyDocument(id = "survey1", rev = "rev1", name = "Shared survey", teamShareAllowed = true)
        val attached = attachedFragment()

        val submission = attached.buildSurveySubmission(submissionParams(survey))

        assertEquals(true, submission.parent.teamShareAllowed)
    }

    @Test
    fun `public respondent keeps only details the server accepts`() {
        val respondent = PublicSurveyRespondent.of(34, " Male ")
        assertEquals(34, respondent?.age)
        assertEquals("male", respondent?.gender)

        assertNull(PublicSurveyRespondent.of(null, null))
        assertNull(PublicSurveyRespondent.of(0, "unspecified"))
        assertEquals(null, PublicSurveyRespondent.of(200, "female")?.age)
        assertEquals("female", PublicSurveyRespondent.of(200, "female")?.gender)
    }
}
