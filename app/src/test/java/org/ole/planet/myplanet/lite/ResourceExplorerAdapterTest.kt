package org.ole.planet.myplanet.lite

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.coroutines.test.runTest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ResourceExplorerAdapterTest {

    private lateinit var adapter: DashboardResourcesPageFragment.ResourceExplorerAdapter
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        adapter = DashboardResourcesPageFragment.ResourceExplorerAdapter(
            downloadProgressByKey = emptyMap(),
            onViewResource = {},
            onSecondaryAction = {}
        )
    }

    @Test
    fun testSubmitListAndDiffing() = runTest {
        val resource1 = ResourceUi(
            id = "1",
            filename = "file1.pdf",
            name = "Test 1",
            type = "pdf",
            date = "2023",
            createdDate = 12345L,
            isDownloaded = false,
            isDownloadable = true,
            isTeamResource = false
        )
        val resource2 = ResourceUi(
            id = "2",
            filename = "file2.pdf",
            name = "Test 2",
            type = "video",
            date = "2023",
            createdDate = 12345L,
            isDownloaded = false,
            isDownloadable = true,
            isTeamResource = false
        )

        adapter.submitList(listOf(resource1, resource2)) {
            assertEquals(2, adapter.currentList.size)
            assertEquals("1", adapter.currentList[0].id)
        }

        // Remove 1, add 3
        val resource3 = ResourceUi(
            id = "3",
            filename = "file3.pdf",
            name = "Test 3",
            type = "audio",
            date = "2023",
            createdDate = 12345L,
            isDownloaded = false,
            isDownloadable = true,
            isTeamResource = false
        )

        adapter.submitList(listOf(resource2, resource3)) {
            assertEquals(2, adapter.currentList.size)
            assertEquals("2", adapter.currentList[0].id)
            assertEquals("3", adapter.currentList[1].id)
        }
    }
}
