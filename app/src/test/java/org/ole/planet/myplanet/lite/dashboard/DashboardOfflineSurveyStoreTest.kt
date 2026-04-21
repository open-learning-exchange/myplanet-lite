package org.ole.planet.myplanet.lite.dashboard

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyDocument
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class DashboardOfflineSurveyStoreTest {

    private lateinit var store: DashboardOfflineSurveyStore

    private lateinit var mockPrefs: android.content.SharedPreferences

    @Before
    fun setup() {
        mockPrefs = org.mockito.Mockito.mock(android.content.SharedPreferences::class.java)
        org.ole.planet.myplanet.lite.util.SecurePreferencesProvider.injectedPreferences = mockPrefs

        val context = ApplicationProvider.getApplicationContext<Context>()
        store = DashboardOfflineSurveyStore(context)
        // Clear db before test
        store.writableDatabase.delete("surveys", null, null)
    }

    @After
    fun teardown() {
        store.close()
        org.ole.planet.myplanet.lite.util.SecurePreferencesProvider.injectedPreferences = null
    }

    @Test
    fun `saveSurvey with null id returns false`() = runTest {
        val document = SurveyDocument(id = null)
        val result = store.saveSurvey(document, "fallbackTeam")
        assertFalse(result)

        val savedIds = store.getSavedSurveyIds()
        assertTrue(savedIds.isEmpty())
    }

    @Test
    fun `saveSurvey with empty id returns false`() = runTest {
        val document = SurveyDocument(id = "   ")
        val result = store.saveSurvey(document, "fallbackTeam")
        assertFalse(result)

        val savedIds = store.getSavedSurveyIds()
        assertTrue(savedIds.isEmpty())
    }

    @Test
    fun `saveSurvey successfully saves valid document`() = runTest {
        val document = SurveyDocument(id = "survey123", rev = "1-abc", name = "Test Survey")
        val result = store.saveSurvey(document, "teamA")

        assertTrue(result)

        val savedIds = store.getSavedSurveyIds()
        assertEquals(setOf("survey123"), savedIds)

        val savedSurveys = store.getSavedSurveysForTeam("teamA")
        assertEquals(1, savedSurveys.size)
        assertEquals("survey123", savedSurveys[0].id)
        assertEquals("1-abc", savedSurveys[0].rev)
        assertEquals("Test Survey", savedSurveys[0].name)
    }

    @Test
    fun `saveSurvey prioritizes document teamId over fallbackTeamId`() = runTest {
        val document = SurveyDocument(id = "survey123", teamId = "documentTeamId")
        val result = store.saveSurvey(document, "fallbackTeamId")

        assertTrue(result)

        val fallbackSurveys = store.getSavedSurveysForTeam("fallbackTeamId")
        assertTrue(fallbackSurveys.isEmpty())

        val docSurveys = store.getSavedSurveysForTeam("documentTeamId")
        assertEquals(1, docSurveys.size)
        assertEquals("survey123", docSurveys[0].id)
    }

    @Test
    fun `saveSurvey uses fallbackTeamId when document teamId is null`() = runTest {
        val document = SurveyDocument(id = "survey123", teamId = null)
        val result = store.saveSurvey(document, "fallbackTeamId")

        assertTrue(result)

        val surveys = store.getSavedSurveysForTeam("fallbackTeamId")
        assertEquals(1, surveys.size)
        assertEquals("survey123", surveys[0].id)
    }

    @Test
    fun `saveSurvey with existing id replaces old record`() = runTest {
        val documentV1 = SurveyDocument(id = "survey123", rev = "1", name = "V1")
        store.saveSurvey(documentV1, "teamA")

        val documentV2 = SurveyDocument(id = "survey123", rev = "2", name = "V2")
        val result = store.saveSurvey(documentV2, "teamA")

        assertTrue(result)

        val savedIds = store.getSavedSurveyIds()
        assertEquals(setOf("survey123"), savedIds)

        val surveys = store.getSavedSurveysForTeam("teamA")
        assertEquals(1, surveys.size)
        assertEquals("survey123", surveys[0].id)
        assertEquals("2", surveys[0].rev)
        assertEquals("V2", surveys[0].name)

        val revisions = store.getSavedSurveyRevisions()
        assertEquals("2", revisions["survey123"])
    }
}
