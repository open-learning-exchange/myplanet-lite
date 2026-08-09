package org.ole.planet.myplanet.lite.dashboard

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.lite.SurveyAnswer
import org.ole.planet.myplanet.lite.SurveyRespondent
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyDocument
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DashboardSurveyDraftStoreTest {
    @Test
    fun saveRestoreListAndDeleteDraft() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val store = DashboardSurveyDraftStore(context)
        val key = DashboardSurveyDraftStore.key("survey-draft-test", "team-draft-test", "user")
        store.delete(key)
        val entry = DashboardSurveyDraftStore.DraftEntry(
            key = key,
            document = SurveyDocument(id = "survey-draft-test", rev = "2-draft", name = "Draft survey"),
            teamId = "team-draft-test",
            teamName = "Team",
            owner = "user",
            currentIndex = 2,
            answers = mapOf(0 to SurveyAnswer.Text("saved answer")),
            respondent = SurveyRespondent(birthYear = 1990),
            birthDateSelection = null,
            updatedAt = 1234L,
        )

        assertTrue(store.save(entry))
        assertEquals(entry, store.get(key))
        assertEquals("survey-draft-test", store.get(key)?.surveyId)
        assertEquals("2-draft", store.get(key)?.surveyRev)
        assertEquals(listOf(entry), store.getForTeam("team-draft-test", "user"))
        assertTrue(store.delete(key))
        assertNull(store.get(key))
    }
}
