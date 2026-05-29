package org.ole.planet.myplanet.lite

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.P])
class ResourceDownloadServiceTest {
    private lateinit var context: Context
    private lateinit var downloadService: ResourceDownloadService

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val mockPrefs = context.getSharedPreferences("mock_secure_prefs", Context.MODE_PRIVATE)
        SecurePreferencesProvider.injectedPreferences = mockPrefs

        downloadService = ResourceDownloadService(
            context = context,
            prefsName = "test_downloaded_prefs",
            downloadedResourcesKey = "test_resources_key"
        )
    }

    @After
    fun tearDown() {
        SecurePreferencesProvider.resetForTesting()
    }

    @Test
    fun testUpsertAndLoadDownloadedResource() {
        val resource = ResourceUi(
            id = "res_123",
            filename = "test_file.pdf",
            name = "Test Resource",
            type = "PDF",
            date = "2023-01-01",
            createdDate = 1672531200000L,
            isDownloaded = false,
            isDownloadable = true,
            isTeamResource = false
        )

        downloadService.upsertDownloadedResource(resource)

        val mockPrefs = context.getSharedPreferences("mock_secure_prefs", Context.MODE_PRIVATE)
        val storedJson = mockPrefs.getString("test_resources_key", null)
        assertTrue("Resource should be stored in secure prefs", storedJson?.contains("res_123") == true)

        // Setup mock file so loadDownloadedResources can find it and return it
        val publicDownloads = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        val mockFile = java.io.File(publicDownloads, "test_file.pdf")
        mockFile.parentFile?.mkdirs()
        mockFile.createNewFile()

        val loadedResources = downloadService.loadDownloadedResources()
        assertEquals(1, loadedResources.size)
        assertEquals("res_123", loadedResources[0].id)

        mockFile.delete()
    }

    @Test
    fun testRemoveDownloadedResource() {
        val resource = ResourceUi(
            id = "res_456",
            filename = "another_file.mp4",
            name = "Another Resource",
            type = "MP4",
            date = "2023-01-02",
            createdDate = 1672617600000L,
            isDownloaded = false,
            isDownloadable = true,
            isTeamResource = true
        )

        downloadService.upsertDownloadedResource(resource)
        downloadService.removeDownloadedResource(resource)

        val mockPrefs = context.getSharedPreferences("mock_secure_prefs", Context.MODE_PRIVATE)
        val storedJson = mockPrefs.getString("test_resources_key", null)
        assertTrue("Resource should be removed from secure prefs", storedJson == "[]" || storedJson == null)
    }

    @Test
    fun testSaveDownloadedResourceFile_preventsPathTraversal() {
        val maliciousResource = ResourceUi(
            id = "res_789",
            filename = "../../../malicious.pdf",
            name = "Malicious Resource",
            type = "PDF",
            date = "2023-01-03",
            createdDate = 1672704000000L,
            isDownloaded = false,
            isDownloadable = true,
            isTeamResource = false
        )

        val bytes = "test content".toByteArray()
        val result = downloadService.saveDownloadedResourceFile(maliciousResource, bytes)

        assertNull("saveDownloadedResourceFile should return null for path traversal attempt", result)
    }

    @Test
    fun testSaveDownloadedResourceFile_preventsIdTraversal() {
        val maliciousResource = ResourceUi(
            id = "../../../malicious_dir",
            filename = "malicious.pdf",
            name = "Malicious Resource",
            type = "PDF",
            date = "2023-01-03",
            createdDate = 1672704000000L,
            isDownloaded = false,
            isDownloadable = true,
            isTeamResource = false
        )

        val bytes = "test content".toByteArray()
        val result = downloadService.saveDownloadedResourceFile(maliciousResource, bytes)

        assertNull("saveDownloadedResourceFile should return null for id traversal attempt", result)
    }

    @Test
    fun testResolveResourceUri_localFileExists() {
        val resource = ResourceUi(
            id = "res_local",
            filename = "local_file.pdf",
            name = "Local Resource",
            type = "PDF",
            date = "2023-01-04",
            createdDate = 1672790400000L,
            isDownloaded = false,
            isDownloadable = true,
            isTeamResource = false
        )

        // Setup mock file so findLocalResourceFile can find it
        val publicDownloads = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
        val mockFile = java.io.File(publicDownloads, "local_file.pdf")
        mockFile.parentFile?.mkdirs()
        mockFile.createNewFile()

        val uri = downloadService.resolveResourceUri("http://example.com", resource)
        assertEquals(mockFile.toURI().toString(), uri)

        mockFile.delete()
    }

    @Test
    fun testResolveResourceUri_remoteFileValid() {
        val resource = ResourceUi(
            id = "res_remote",
            filename = "remote_file.pdf",
            name = "Remote Resource",
            type = "PDF",
            date = "2023-01-05",
            createdDate = 1672876800000L,
            isDownloaded = false,
            isDownloadable = true,
            isTeamResource = false
        )

        val expectedUri = "http://example.com/db/resources/res_remote/remote_file.pdf"

        val baseUrls = listOf(
            "http://example.com",
            "http://example.com/",
            "   http://example.com   ",
            "   http://example.com/   "
        )

        for (baseUrl in baseUrls) {
            val uri = downloadService.resolveResourceUri(baseUrl, resource)
            assertEquals("Failed for baseUrl: '$baseUrl'", expectedUri, uri)
        }
    }

    @Test
    fun testResolveResourceUri_invalidInputs() {
        val validResource = ResourceUi(
            id = "res_remote",
            filename = "remote_file.pdf",
            name = "Remote Resource",
            type = "PDF",
            date = "2023-01-05",
            createdDate = 1672876800000L,
            isDownloaded = false,
            isDownloadable = true,
            isTeamResource = false
        )

        val emptyIdResource = validResource.copy(id = "")
        val blankIdResource = validResource.copy(id = "   ")
        val emptyFilenameResource = validResource.copy(filename = "")
        val blankFilenameResource = validResource.copy(filename = "   ")

        assertNull("Should return null for null baseUrl", downloadService.resolveResourceUri(null, validResource))
        assertNull("Should return null for empty baseUrl", downloadService.resolveResourceUri("", validResource))
        assertNull("Should return null for blank baseUrl", downloadService.resolveResourceUri("   ", validResource))

        assertNull("Should return null for empty id", downloadService.resolveResourceUri("http://example.com", emptyIdResource))
        assertNull("Should return null for blank id", downloadService.resolveResourceUri("http://example.com", blankIdResource))

        assertNull("Should return null for empty filename", downloadService.resolveResourceUri("http://example.com", emptyFilenameResource))
        assertNull("Should return null for blank filename", downloadService.resolveResourceUri("http://example.com", blankFilenameResource))
    }
}
