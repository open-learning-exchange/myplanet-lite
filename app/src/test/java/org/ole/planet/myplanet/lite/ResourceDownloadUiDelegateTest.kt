package org.ole.planet.myplanet.lite

import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class ResourceDownloadUiDelegateTest {

    private val mockResourceSyncService: ResourceSyncService = mock()
    private val mockResourceDownloadService: ResourceDownloadService = mock()

    private val sampleResource = ResourceUi(
        id = "1",
        filename = "test.pdf",
        name = "Test Resource",
        type = "PDF",
        date = "2023-01-01",
        createdDate = 1000L,
        isDownloaded = false,
        isDownloadable = true,
        isTeamResource = false
    )

    @Test
    fun `test downloadResource calls service`() = runTest {
        whenever(mockResourceSyncService.downloadResource("http://base", "cookie", sampleResource))
            .thenReturn(true)

        val result = ResourceDownloadUiDelegate.downloadResource(
            baseUrl = "http://base",
            sessionCookie = "cookie",
            item = sampleResource,
            resourceSyncService = mockResourceSyncService
        )

        assertTrue(result)
        verify(mockResourceSyncService).downloadResource("http://base", "cookie", sampleResource)
    }

    @Test
    fun `test deleteDownloadedResource`() {
        val tempFile = File.createTempFile("test", ".txt")
        assertTrue(tempFile.exists())

        whenever(mockResourceDownloadService.findLocalResourceFile(
            sampleResource.id,
            sampleResource.filename,
            sampleResource.isTeamResource
        )).thenReturn(tempFile)

        ResourceDownloadUiDelegate.deleteDownloadedResource(mockResourceDownloadService, sampleResource)

        assertFalse(tempFile.exists())
        verify(mockResourceDownloadService).removeDownloadedResource(sampleResource)
    }

    @Test
    fun `test updateDownloadStateInList existing item`() {
        val list = mutableListOf(sampleResource.copy(), sampleResource.copy(id = "2"))

        ResourceDownloadUiDelegate.updateDownloadStateInList(
            items = list,
            target = sampleResource,
            downloaded = true,
            selectedSortBy = ResourceSortBy.NAME,
            isSortDescending = false
        )

        assertEquals(2, list.size)
        val updatedItem = list.find { it.uniqueKey() == sampleResource.uniqueKey() }
        assertNotNull(updatedItem)
        assertTrue(updatedItem!!.isDownloaded)
    }

    @Test
    fun `test updateDownloadStateInList new item`() {
        val list = mutableListOf<ResourceUi>()

        ResourceDownloadUiDelegate.updateDownloadStateInList(
            items = list,
            target = sampleResource,
            downloaded = true,
            selectedSortBy = ResourceSortBy.NAME,
            isSortDescending = false
        )

        assertEquals(1, list.size)
        assertTrue(list[0].isDownloaded)
        assertEquals(sampleResource.id, list[0].id)
    }

    @Test
    fun `test updateDownloadStateInList new item not downloaded`() {
        val list = mutableListOf<ResourceUi>()

        ResourceDownloadUiDelegate.updateDownloadStateInList(
            items = list,
            target = sampleResource,
            downloaded = false,
            selectedSortBy = ResourceSortBy.NAME,
            isSortDescending = false
        )

        assertEquals(0, list.size)
    }

    @Test
    fun `test markResourceDownloadState`() {
        val mainList = mutableListOf(sampleResource.copy())
        val teamList = mutableListOf(sampleResource.copy())

        ResourceDownloadUiDelegate.markResourceDownloadState(
            mainResourcesItems = mainList,
            teamResourcesItems = teamList,
            item = sampleResource,
            downloaded = true,
            selectedSortBy = ResourceSortBy.NAME,
            isSortDescending = false
        )

        assertTrue(mainList[0].isDownloaded)
        assertTrue(teamList[0].isDownloaded)
    }

    @Test
    fun `test loadDownloadedResourcesFiltered`() {
        val resources = listOf(
            sampleResource.copy(isDownloaded = true),
            sampleResource.copy(id = "2", name = "Team Resource", isTeamResource = true, isDownloaded = true)
        )
        whenever(mockResourceDownloadService.loadDownloadedResources()).thenReturn(resources)

        val result = ResourceDownloadUiDelegate.loadDownloadedResourcesFiltered(
            downloadService = mockResourceDownloadService,
            isTeamResourcesTab = false,
            searchQuery = "",
            selectedMediaType = null
        )

        assertEquals(1, result.size)
        assertEquals(sampleResource.id, result[0].id)

        val teamResult = ResourceDownloadUiDelegate.loadDownloadedResourcesFiltered(
            downloadService = mockResourceDownloadService,
            isTeamResourcesTab = true,
            searchQuery = "",
            selectedMediaType = null
        )

        assertEquals(1, teamResult.size)
        assertEquals("2", teamResult[0].id)
    }
}
