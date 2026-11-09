package org.ole.planet.myplanet.lite.dashboard

import org.junit.Assert.assertEquals
import org.junit.Test
import org.ole.planet.myplanet.lite.dashboard.DashboardSurveysRepository.SurveyDocument

class DashboardSurveyOrderingTest {
    @Test
    fun `sorts numeric and ISO dates from newest to oldest with missing dates last`() {
        val surveys = listOf(
            SurveyDocument(id = "missing", name = "Missing"),
            SurveyDocument(id = "older", name = "Older", createdDate = "2024-01-01"),
            SurveyDocument(id = "newest", name = "Newest", createdDate = "1773949429414"),
            SurveyDocument(id = "middle", name = "Middle", createdDate = "2025-01-01T00:00:00Z"),
        )

        assertEquals(
            listOf("newest", "middle", "older", "missing"),
            surveys.sortedNewestFirst().map { it.id },
        )
    }

    @Test
    fun `uses survey name as deterministic fallback for equal dates`() {
        val surveys = listOf(
            SurveyDocument(id = "z", name = "Zulu", createdDate = "1000"),
            SurveyDocument(id = "a", name = "Alpha", createdDate = "1000"),
        )

        assertEquals(listOf("a", "z"), surveys.sortedNewestFirst().map { it.id })
    }
}
