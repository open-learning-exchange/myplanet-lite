package org.ole.planet.myplanet.lite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourceSearchEngineTest {

    private val communityResource1 = ResourceUi(
        id = "1",
        filename = "document1.pdf",
        name = "Math Fundamentals",
        type = "pdf",
        date = "2023-01-01",
        createdDate = 1000L,
        isDownloaded = false,
        isDownloadable = true,
        isTeamResource = false
    )

    private val communityResource2 = ResourceUi(
        id = "2",
        filename = "video1.mp4",
        name = "History Lesson",
        type = "video",
        date = "2023-01-02",
        createdDate = 2000L,
        isDownloaded = true,
        isDownloadable = false,
        isTeamResource = false
    )

    private val teamResource1 = ResourceUi(
        id = "3",
        filename = "team_doc.pdf",
        name = "Team Guidelines",
        type = "pdf",
        date = "2023-01-03",
        createdDate = 3000L,
        isDownloaded = false,
        isDownloadable = true,
        isTeamResource = true
    )

    private val teamResource2 = ResourceUi(
        id = "4",
        filename = "audio.mp3",
        name = "Team Meeting Recording",
        type = "audio",
        date = "2023-01-04",
        createdDate = 4000L,
        isDownloaded = true,
        isDownloadable = false,
        isTeamResource = true
    )

    private val allResources = listOf(
        communityResource1,
        communityResource2,
        teamResource1,
        teamResource2
    )

    @Test
    fun `filterByTabQueryAndType returns only community resources when isTeamResourcesTab is false`() {
        val result = ResourceSearchEngine.filterByTabQueryAndType(
            resources = allResources,
            isTeamResourcesTab = false,
            query = "",
            selectedMediaType = null
        )

        assertEquals(2, result.size)
        assertTrue(result.contains(communityResource1))
        assertTrue(result.contains(communityResource2))
    }

    @Test
    fun `filterByTabQueryAndType returns only team resources when isTeamResourcesTab is true`() {
        val result = ResourceSearchEngine.filterByTabQueryAndType(
            resources = allResources,
            isTeamResourcesTab = true,
            query = "",
            selectedMediaType = null
        )

        assertEquals(2, result.size)
        assertTrue(result.contains(teamResource1))
        assertTrue(result.contains(teamResource2))
    }

    @Test
    fun `filterByTabQueryAndType matches query against name ignoring case`() {
        val result = ResourceSearchEngine.filterByTabQueryAndType(
            resources = allResources,
            isTeamResourcesTab = false,
            query = "MATH",
            selectedMediaType = null
        )

        assertEquals(1, result.size)
        assertEquals(communityResource1, result.first())
    }

    @Test
    fun `filterByTabQueryAndType matches query against filename ignoring case`() {
        val result = ResourceSearchEngine.filterByTabQueryAndType(
            resources = allResources,
            isTeamResourcesTab = true,
            query = "AUDIO.MP3",
            selectedMediaType = null
        )

        assertEquals(1, result.size)
        assertEquals(teamResource2, result.first())
    }

    @Test
    fun `filterByTabQueryAndType handles blank query by not filtering by query`() {
        val result = ResourceSearchEngine.filterByTabQueryAndType(
            resources = allResources,
            isTeamResourcesTab = false,
            query = "   ",
            selectedMediaType = null
        )

        assertEquals(2, result.size)
        assertTrue(result.contains(communityResource1))
        assertTrue(result.contains(communityResource2))
    }

    @Test
    fun `filterByTabQueryAndType matches selectedMediaType exactly`() {
        val result = ResourceSearchEngine.filterByTabQueryAndType(
            resources = allResources,
            isTeamResourcesTab = false,
            query = "",
            selectedMediaType = "pdf"
        )

        assertEquals(1, result.size)
        assertEquals(communityResource1, result.first())
    }

    @Test
    fun `filterByTabQueryAndType matches selectedMediaType ignoring case`() {
        val result = ResourceSearchEngine.filterByTabQueryAndType(
            resources = allResources,
            isTeamResourcesTab = false,
            query = "",
            selectedMediaType = "PDF"
        )

        assertEquals(1, result.size)
        assertEquals(communityResource1, result.first())
    }

    @Test
    fun `filterByTabQueryAndType handles blank selectedMediaType by not filtering by type`() {
        val result = ResourceSearchEngine.filterByTabQueryAndType(
            resources = allResources,
            isTeamResourcesTab = true,
            query = "",
            selectedMediaType = "  "
        )

        assertEquals(2, result.size)
        assertTrue(result.contains(teamResource1))
        assertTrue(result.contains(teamResource2))
    }

    @Test
    fun `filterByTabQueryAndType handles null selectedMediaType by not filtering by type`() {
        val result = ResourceSearchEngine.filterByTabQueryAndType(
            resources = allResources,
            isTeamResourcesTab = true,
            query = "",
            selectedMediaType = null
        )

        assertEquals(2, result.size)
        assertTrue(result.contains(teamResource1))
        assertTrue(result.contains(teamResource2))
    }

    @Test
    fun `filterByTabQueryAndType combines tab, query, and type constraints correctly`() {
        // Create a matching community resource to ensure tab filter works
        val communityDoc = communityResource1.copy(name = "Team Guidelines Community", isTeamResource = false)

        val modifiedResources = allResources + communityDoc

        val result = ResourceSearchEngine.filterByTabQueryAndType(
            resources = modifiedResources,
            isTeamResourcesTab = true, // Must be team resource
            query = "guidelines",      // Name must contain guidelines
            selectedMediaType = "pdf"  // Type must be pdf
        )

        assertEquals(1, result.size)
        assertEquals(teamResource1, result.first())
    }
}
