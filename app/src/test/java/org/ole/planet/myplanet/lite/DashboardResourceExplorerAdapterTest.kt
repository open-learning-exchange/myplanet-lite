package org.ole.planet.myplanet.lite

import android.view.ContextThemeWrapper
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DashboardResourceExplorerAdapterTest {

    private lateinit var themedContext: ContextThemeWrapper
    private lateinit var onViewResource: (ResourceUi) -> Unit
    private lateinit var onSecondaryAction: (ResourceUi) -> Unit

    private val samplePdfResource = ResourceUi(
        id = "res-1",
        filename = "document.pdf",
        name = "Sample Document",
        type = "PDF",
        date = "2026-08-19",
        createdDate = 1724000000000L,
        isDownloaded = false,
        isDownloadable = true,
        isTeamResource = false
    )

    private val sampleAudioResource = ResourceUi(
        id = "res-2",
        filename = "audio.mp3",
        name = "Sample Audio",
        type = "Audio",
        date = "2026-08-19",
        createdDate = 1724000000000L,
        isDownloaded = true,
        isDownloadable = true,
        isTeamResource = false
    )

    private val sampleVideoResource = ResourceUi(
        id = "res-3",
        filename = "video.mp4",
        name = "Sample Video",
        type = "Video",
        date = "2026-08-19",
        createdDate = 1724000000000L,
        isDownloaded = false,
        isDownloadable = true,
        isTeamResource = true
    )

    private val sampleImageResource = ResourceUi(
        id = "res-4",
        filename = "photo.jpg",
        name = "Sample Image",
        type = "Image",
        date = "2026-08-19",
        createdDate = 1724000000000L,
        isDownloaded = false,
        isDownloadable = false,
        isTeamResource = false
    )

    @Before
    fun setup() {
        val appContext = ApplicationProvider.getApplicationContext<android.content.Context>()
        themedContext = ContextThemeWrapper(appContext, com.google.android.material.R.style.Theme_MaterialComponents_Light)
        onViewResource = mock()
        onSecondaryAction = mock()
    }

    @Test
    fun `single click on item root invokes onViewResource`() {
        val adapter = DashboardResourcesPageFragment.ResourceExplorerAdapter(
            initialResources = listOf(samplePdfResource),
            downloadProgressByKey = emptyMap(),
            onViewResource = onViewResource,
            onSecondaryAction = onSecondaryAction
        )

        val parent = FrameLayout(themedContext)
        val viewHolder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(viewHolder, 0)

        // Single click on root
        viewHolder.itemView.performClick()

        verify(onViewResource).invoke(samplePdfResource)
    }

    @Test
    fun `click on resourceViewButton invokes onViewResource`() {
        val adapter = DashboardResourcesPageFragment.ResourceExplorerAdapter(
            initialResources = listOf(samplePdfResource),
            downloadProgressByKey = emptyMap(),
            onViewResource = onViewResource,
            onSecondaryAction = onSecondaryAction
        )

        val parent = FrameLayout(themedContext)
        val viewHolder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(viewHolder, 0)

        val viewButton = viewHolder.itemView.findViewById<ImageButton>(R.id.resourceViewButton)
        viewButton.performClick()

        verify(onViewResource).invoke(samplePdfResource)
    }

    @Test
    fun `click on secondary action button invokes onSecondaryAction when not downloading`() {
        val adapter = DashboardResourcesPageFragment.ResourceExplorerAdapter(
            initialResources = listOf(samplePdfResource),
            downloadProgressByKey = emptyMap(),
            onViewResource = onViewResource,
            onSecondaryAction = onSecondaryAction
        )

        val parent = FrameLayout(themedContext)
        val viewHolder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(viewHolder, 0)

        val secondaryButton = viewHolder.itemView.findViewById<ImageButton>(R.id.resourceSecondaryActionButton)
        secondaryButton.performClick()

        verify(onSecondaryAction).invoke(samplePdfResource)
    }

    @Test
    fun `bind correctly populates item view fields`() {
        val adapter = DashboardResourcesPageFragment.ResourceExplorerAdapter(
            initialResources = listOf(samplePdfResource, sampleAudioResource, sampleVideoResource, sampleImageResource),
            downloadProgressByKey = emptyMap(),
            onViewResource = onViewResource,
            onSecondaryAction = onSecondaryAction
        )

        val parent = FrameLayout(themedContext)

        // Test PDF
        val pdfHolder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(pdfHolder, 0)
        assertEquals("Sample Document", pdfHolder.itemView.findViewById<TextView>(R.id.resourceName).text.toString())
        assertEquals("PDF", pdfHolder.itemView.findViewById<TextView>(R.id.resourceType).text.toString())
        assertEquals("2026-08-19", pdfHolder.itemView.findViewById<TextView>(R.id.resourceDate).text.toString())

        // Test Audio
        val audioHolder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(audioHolder, 1)
        assertEquals("Sample Audio", audioHolder.itemView.findViewById<TextView>(R.id.resourceName).text.toString())
        assertEquals("Audio", audioHolder.itemView.findViewById<TextView>(R.id.resourceType).text.toString())

        // Test Video
        val videoHolder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(videoHolder, 2)
        assertEquals("Sample Video", videoHolder.itemView.findViewById<TextView>(R.id.resourceName).text.toString())
        assertEquals("Video", videoHolder.itemView.findViewById<TextView>(R.id.resourceType).text.toString())

        // Test Image
        val imageHolder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(imageHolder, 3)
        assertEquals("Sample Image", imageHolder.itemView.findViewById<TextView>(R.id.resourceName).text.toString())
        assertEquals("Image", imageHolder.itemView.findViewById<TextView>(R.id.resourceType).text.toString())
    }

    @Test
    fun `download progress is shown when item is downloading`() {
        val progressMap = mapOf(samplePdfResource.uniqueKey() to 45)
        val adapter = DashboardResourcesPageFragment.ResourceExplorerAdapter(
            initialResources = listOf(samplePdfResource),
            downloadProgressByKey = progressMap,
            onViewResource = onViewResource,
            onSecondaryAction = onSecondaryAction
        )

        val parent = FrameLayout(themedContext)
        val viewHolder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(viewHolder, 0)

        val progressBar = viewHolder.itemView.findViewById<ProgressBar>(R.id.resourceDownloadProgress)
        assertTrue(progressBar.isVisible)
        assertEquals(45, progressBar.progress)
        assertFalse(progressBar.isIndeterminate)
    }

    @Test
    fun `indeterminate progress is shown when progress is null during downloading`() {
        val progressMap = mapOf<String, Int?>(samplePdfResource.uniqueKey() to null)
        val adapter = DashboardResourcesPageFragment.ResourceExplorerAdapter(
            initialResources = listOf(samplePdfResource),
            downloadProgressByKey = progressMap,
            onViewResource = onViewResource,
            onSecondaryAction = onSecondaryAction
        )

        val parent = FrameLayout(themedContext)
        val viewHolder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(viewHolder, 0)

        val progressBar = viewHolder.itemView.findViewById<ProgressBar>(R.id.resourceDownloadProgress)
        assertTrue(progressBar.isVisible)
        assertTrue(progressBar.isIndeterminate)
    }
}
