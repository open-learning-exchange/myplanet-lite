package org.ole.planet.myplanet.lite.dashboard

import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.core.view.isVisible
import androidx.test.core.app.ApplicationProvider
import com.github.chrisbanes.photoview.PhotoView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.ole.planet.myplanet.lite.R
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DashboardImagePreviewAdapterTest {

    private lateinit var adapter: DashboardImagePreviewAdapter
    private lateinit var imageLoader: DashboardPostImageLoader
    private var dismissCount = 0

    @Before
    fun setup() {
        dismissCount = 0
        imageLoader = mock()
        val imagePaths = listOf("path1", "path2")
        adapter = DashboardImagePreviewAdapter(imagePaths, imageLoader) {
            dismissCount++
        }
    }

    @Test
    fun `getItemCount returns correct size`() {
        assertEquals(2, adapter.itemCount)
    }

    @Test
    fun `onCreateViewHolder inflates layout`() {
        val parent = FrameLayout(ApplicationProvider.getApplicationContext())
        val viewHolder = adapter.onCreateViewHolder(parent, 0)

        val photoView = viewHolder.itemView.findViewById<PhotoView>(R.id.previewPhotoView)
        assertEquals(5f, photoView.maximumScale)
        assertEquals(2.5f, photoView.mediumScale)
        assertEquals(0.6f, photoView.minimumScale)
    }

    @Test
    fun `onBindViewHolder binds image successfully`() {
        val parent = FrameLayout(ApplicationProvider.getApplicationContext())
        val viewHolder = adapter.onCreateViewHolder(parent, 0)
        val photoView = viewHolder.itemView.findViewById<PhotoView>(R.id.previewPhotoView)
        val progressBar = viewHolder.itemView.findViewById<ProgressBar>(R.id.previewPageLoading)

        // Mock image loader to invoke the callback with success = true
        doAnswer { invocation ->
            val callback = invocation.getArgument<((Boolean) -> Unit)?>(2)
            callback?.invoke(true)
            null
        }.`when`(imageLoader).bind(any(), eq("path1"), anyOrNull())

        adapter.onBindViewHolder(viewHolder, 0)

        verify(imageLoader).bind(eq(photoView), eq("path1"), anyOrNull())

        assertFalse(progressBar.isVisible)
        assertTrue(photoView.isVisible)
        assertEquals(0, dismissCount)
    }

    @Test
    fun `onBindViewHolder dismisses on image load failure`() {
        val parent = FrameLayout(ApplicationProvider.getApplicationContext())
        val viewHolder = adapter.onCreateViewHolder(parent, 0)
        val photoView = viewHolder.itemView.findViewById<PhotoView>(R.id.previewPhotoView)
        val progressBar = viewHolder.itemView.findViewById<ProgressBar>(R.id.previewPageLoading)

        // Mock image loader to invoke the callback with success = false
        doAnswer { invocation ->
            val callback = invocation.getArgument<((Boolean) -> Unit)?>(2)
            callback?.invoke(false)
            null
        }.`when`(imageLoader).bind(any(), eq("path2"), anyOrNull())

        adapter.onBindViewHolder(viewHolder, 1)

        verify(imageLoader).bind(eq(photoView), eq("path2"), anyOrNull())

        assertFalse(progressBar.isVisible)
        assertFalse(photoView.isVisible)
        assertEquals(1, dismissCount)
    }
}
