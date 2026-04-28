package org.ole.planet.myplanet.lite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourceSearchEngineTest {

    private val resources = listOf(
        ResourceUi(
            id = "1",
            filename = "file1.pdf",
            name = "First Resource",
            type = "pdf",
            date = "2023-01-01",
            createdDate = 1000L,
            isDownloaded = false,
            isDownloadable = true,
            isTeamResource = false
        ),
        ResourceUi(
            id = "2",
            filename = "file2.mp4",
            name = "Second Resource",
            type = "video",
            date = "2023-01-02",
            createdDate = 2000L,
            isDownloaded = true,
            isDownloadable = false,
            isTeamResource = true
        ),
        ResourceUi(
            id = "3",
            filename = "file3.mp3",
            name = "Third Audio",
            type = "audio",
            date = "2023-01-03",
            createdDate = 3000L,
            isDownloaded = false,
            isDownloadable = true,
            isTeamResource = false
        ),
        ResourceUi(
            id = "4",
            filename = "file4.pdf",
            name = "Fourth Team Pdf",
            type = "pdf",
            date = "2023-01-04",
            createdDate = 4000L,
            isDownloaded = false,
            isDownloadable = true,
            isTeamResource = true
        )
    )

    @Test
    fun `test filterByTabQueryAndType with different tabs`() {
        // Test community resources
        val communityResources = ResourceSearchEngine.filterByTabQueryAndType(
            resources = resources,
            isTeamResourcesTab = false,
            query = "",
            selectedMediaType = null
        )
        assertEquals(2, communityResources.size)
        assertTrue(communityResources.all { !it.isTeamResource })

        // Test team resources
        val teamResources = ResourceSearchEngine.filterByTabQueryAndType(
            resources = resources,
            isTeamResourcesTab = true,
            query = "",
            selectedMediaType = null
        )
        assertEquals(2, teamResources.size)
        assertTrue(teamResources.all { it.isTeamResource })
    }

    @Test
    fun `test filterByTabQueryAndType with query`() {
        // Query matching name
        val queryNameResources = ResourceSearchEngine.filterByTabQueryAndType(
            resources = resources,
            isTeamResourcesTab = false,
            query = "first",
            selectedMediaType = null
        )
        assertEquals(1, queryNameResources.size)
        assertEquals("1", queryNameResources[0].id)

        // Query matching filename
        val queryFilenameResources = ResourceSearchEngine.filterByTabQueryAndType(
            resources = resources,
            isTeamResourcesTab = true,
            query = "file2",
            selectedMediaType = null
        )
        assertEquals(1, queryFilenameResources.size)
        assertEquals("2", queryFilenameResources[0].id)
    }

    @Test
    fun `test filterByTabQueryAndType with type`() {
        val pdfResources = ResourceSearchEngine.filterByTabQueryAndType(
            resources = resources,
            isTeamResourcesTab = false,
            query = "",
            selectedMediaType = "pdf"
        )
        assertEquals(1, pdfResources.size)
        assertEquals("1", pdfResources[0].id)

        val teamPdfResources = ResourceSearchEngine.filterByTabQueryAndType(
            resources = resources,
            isTeamResourcesTab = true,
            query = "",
            selectedMediaType = "pdf"
        )
        assertEquals(1, teamPdfResources.size)
        assertEquals("4", teamPdfResources[0].id)
    }

    @Test
    fun `test filterByTabQueryAndType with combination`() {
        val combinedResources = ResourceSearchEngine.filterByTabQueryAndType(
            resources = resources,
            isTeamResourcesTab = true,
            query = "fourth",
            selectedMediaType = "pdf"
        )
        assertEquals(1, combinedResources.size)
        assertEquals("4", combinedResources[0].id)
    }

    @Test
    fun `test sortResources by NAME`() {
        val listToSort = resources.toMutableList()

        // Ascending
        ResourceSearchEngine.sortResources(listToSort, ResourceSortBy.NAME, isSortDescending = false)
        assertEquals("First Resource", listToSort[0].name)
        assertEquals("Fourth Team Pdf", listToSort[1].name)
        assertEquals("Second Resource", listToSort[2].name)
        assertEquals("Third Audio", listToSort[3].name)

        // Descending
        ResourceSearchEngine.sortResources(listToSort, ResourceSortBy.NAME, isSortDescending = true)
        assertEquals("Third Audio", listToSort[0].name)
        assertEquals("Second Resource", listToSort[1].name)
        assertEquals("Fourth Team Pdf", listToSort[2].name)
        assertEquals("First Resource", listToSort[3].name)
    }

    @Test
    fun `test sortResources by DATE`() {
        val listToSort = resources.toMutableList()

        // Ascending
        ResourceSearchEngine.sortResources(listToSort, ResourceSortBy.DATE, isSortDescending = false)
        assertEquals("First Resource", listToSort[0].name)
        assertEquals("Second Resource", listToSort[1].name)
        assertEquals("Third Audio", listToSort[2].name)
        assertEquals("Fourth Team Pdf", listToSort[3].name)

        // Descending
        ResourceSearchEngine.sortResources(listToSort, ResourceSortBy.DATE, isSortDescending = true)
        assertEquals("Fourth Team Pdf", listToSort[0].name)
        assertEquals("Third Audio", listToSort[1].name)
        assertEquals("Second Resource", listToSort[2].name)
        assertEquals("First Resource", listToSort[3].name)
    }

    @Test
    fun `test parseIsDownloadable`() {
        // Boolean
        assertTrue(ResourceSearchEngine.parseIsDownloadable(true))
        assertFalse(ResourceSearchEngine.parseIsDownloadable(false))

        // Number
        assertTrue(ResourceSearchEngine.parseIsDownloadable(1))
        assertTrue(ResourceSearchEngine.parseIsDownloadable(10))
        assertFalse(ResourceSearchEngine.parseIsDownloadable(0))

        // String
        assertTrue(ResourceSearchEngine.parseIsDownloadable("true"))
        assertTrue(ResourceSearchEngine.parseIsDownloadable("TRUE"))
        assertTrue(ResourceSearchEngine.parseIsDownloadable("1"))
        assertTrue(ResourceSearchEngine.parseIsDownloadable("yes"))
        assertTrue(ResourceSearchEngine.parseIsDownloadable(" YES "))

        assertFalse(ResourceSearchEngine.parseIsDownloadable("false"))
        assertFalse(ResourceSearchEngine.parseIsDownloadable("0"))
        assertFalse(ResourceSearchEngine.parseIsDownloadable("no"))
        assertFalse(ResourceSearchEngine.parseIsDownloadable("random"))

        // Null and other types
        assertFalse(ResourceSearchEngine.parseIsDownloadable(null))
        assertFalse(ResourceSearchEngine.parseIsDownloadable(Any()))
    }
}
