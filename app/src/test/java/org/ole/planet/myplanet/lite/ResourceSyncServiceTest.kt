package org.ole.planet.myplanet.lite

import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.ole.planet.myplanet.lite.dashboard.DashboardResourcesRepository
import org.ole.planet.myplanet.lite.dashboard.DashboardResourcesRepository.ResourceDocument

@OptIn(ExperimentalCoroutinesApi::class)
class ResourceSyncServiceTest {

    private lateinit var repository: DashboardResourcesRepository
    private lateinit var downloadService: ResourceDownloadService
    private lateinit var service: ResourceSyncService

    @Before
    fun setUp() {
        repository = mock()
        downloadService = mock()
        service = ResourceSyncService(repository, downloadService)
    }

    @Test
    fun `fetchCommunityResources filters duplicates and maps correctly`() = runTest {
        val doc1 = ResourceDocument(
            id = "id1",
            title = "Title 1",
            filename = "file1.pdf",
            createdDate = 1600000000000L,
            mediaType = "pdf",
            isDownloadable = true,
            attachments = null
        )
        val doc2 = ResourceDocument(
            id = "id2",
            title = "", // empty title should fallback to filename
            filename = "file2.pdf",
            createdDate = null,
            mediaType = null, // should fallback to PDF
            isDownloadable = "true",
            attachments = null
        )

        whenever(repository.fetchCommunityResources(
            baseUrl = any(),
            sessionCookie = anyOrNull(),
            searchQuery = any(),
            mediaTypeFilter = anyOrNull(),
            sortBy = eq("title"),
            sortDescending = eq(false),
            limit = eq(100),
            skip = eq(0)
        )).doReturn(Result.success(listOf(doc1, doc2, doc1))) // doc1 repeated

        val mockFile = mock<File>()
        whenever(mockFile.exists()).doReturn(true)
        whenever(downloadService.findLocalResourceFile(eq("id1"), eq("file1.pdf"), eq(false))).doReturn(mockFile)
        whenever(downloadService.findLocalResourceFile(eq("id2"), eq("file2.pdf"), eq(false))).doReturn(null)

        val result = service.fetchCommunityResources(
            baseUrl = "http://base",
            sessionCookie = "cookie",
            searchQuery = "query",
            mediaTypeFilter = null,
            isSortDescending = false,
            skip = 0,
            limit = 100,
            existingKeys = setOf()
        )

        assertEquals(2, result.page.size) // duplicate filtered out

        val ui1 = result.page[0]
        assertEquals("id1", ui1.id)
        assertEquals("Title 1", ui1.name)
        assertEquals("file1.pdf", ui1.filename)
        assertEquals("PDF", ui1.type)
        assertTrue(ui1.isDownloaded)
        assertTrue(ui1.isDownloadable)
        assertFalse(ui1.isTeamResource)

        val ui2 = result.page[1]
        assertEquals("id2", ui2.id)
        assertEquals("file2.pdf", ui2.name) // fallback to filename
        assertEquals("file2.pdf", ui2.filename)
        assertEquals("PDF", ui2.type) // fallback
        assertFalse(ui2.isDownloaded)
        assertTrue(ui2.isDownloadable) // "true" parsed to true
    }

    @Test
    fun `fetchCommunityResources filters against existing keys`() = runTest {
        val doc1 = ResourceDocument("id1", "Title 1", "file1.pdf", null, null, null, null)

        whenever(repository.fetchCommunityResources(
            any(), anyOrNull(), any(), anyOrNull(), any(), any(), any(), any()
        )).doReturn(Result.success(listOf(doc1)))

        val existingKey = storedResourceKey("id1", "file1.pdf", false)

        val result = service.fetchCommunityResources(
            baseUrl = "http://base",
            sessionCookie = null,
            searchQuery = "",
            mediaTypeFilter = null,
            isSortDescending = false,
            skip = 0,
            limit = 100,
            existingKeys = setOf(existingKey)
        )

        assertTrue(result.page.isEmpty()) // Filtered out
    }

    @Test
    fun `fetchTeamResources appends and deduplicates`() = runTest {
        val doc1 = ResourceDocument("id1", "Title 1", "file1.pdf", null, null, null, null)
        val doc2 = ResourceDocument("id2", "Title 2", "file2.pdf", null, null, null, null)

        whenever(repository.fetchTeamResources(
            baseUrl = any(),
            sessionCookie = anyOrNull(),
            username = anyOrNull(),
            password = anyOrNull(),
            teamId = any(),
            searchQuery = any(),
            mediaTypeFilter = anyOrNull(),
            sortBy = eq("title"),
            sortDescending = eq(false),
            limit = eq(100)
        )).doReturn(Result.success(listOf(doc1, doc2)))

        val downloadedResource = ResourceUi(
            id = "id1",
            filename = "file1.pdf",
            name = "Title 1",
            type = "PDF",
            date = "-",
            createdDate = null,
            isDownloaded = true,
            isDownloadable = true,
            isTeamResource = true
        )

        val result = service.fetchTeamResources(
            baseUrl = "http://base",
            sessionCookie = null,
            username = "user",
            password = "pwd",
            teamId = "team1",
            searchQuery = "",
            mediaTypeFilter = null,
            isSortDescending = false,
            limit = 100,
            downloadedResources = listOf(downloadedResource)
        )

        assertEquals(2, result.size)
        // First should be the downloaded one (retained from input)
        assertEquals(downloadedResource, result[0])
        // Second should be doc2
        assertEquals("id2", result[1].id)
    }

    @Test
    fun `downloadResource returns true on success`() = runTest {
        val item = ResourceUi("id1", "file1.pdf", "Title", "PDF", "-", null, false, true, false)
        val bytes = byteArrayOf(1, 2, 3)
        val file = File("dummy")

        whenever(repository.downloadResourceBytes(any(), anyOrNull(), eq("id1"), eq("file1.pdf")))
            .doReturn(Result.success(bytes))
        whenever(downloadService.saveDownloadedResourceFile(eq(item), eq(bytes)))
            .doReturn(file)

        val success = service.downloadResource("http://base", null, item)

        assertTrue(success)
        verify(downloadService).upsertDownloadedResource(item.copy(isDownloaded = true))
    }

    @Test
    fun `downloadResource returns false when save fails`() = runTest {
        val item = ResourceUi("id1", "file1.pdf", "Title", "PDF", "-", null, false, true, false)
        val bytes = byteArrayOf(1, 2, 3)

        whenever(repository.downloadResourceBytes(any(), anyOrNull(), eq("id1"), eq("file1.pdf")))
            .doReturn(Result.success(bytes))
        whenever(downloadService.saveDownloadedResourceFile(eq(item), eq(bytes)))
            .doReturn(null)

        val success = service.downloadResource("http://base", null, item)

        assertFalse(success)
        verify(downloadService, never()).upsertDownloadedResource(any())
    }

    @Test
    fun `downloadResource returns false when download fails`() = runTest {
        val item = ResourceUi("id1", "file1.pdf", "Title", "PDF", "-", null, false, true, false)

        whenever(repository.downloadResourceBytes(any(), anyOrNull(), eq("id1"), eq("file1.pdf")))
            .doReturn(Result.failure(Exception("Network Error")))

        val success = service.downloadResource("http://base", null, item)

        assertFalse(success)
        verify(downloadService, never()).saveDownloadedResourceFile(any(), any())
    }
}
