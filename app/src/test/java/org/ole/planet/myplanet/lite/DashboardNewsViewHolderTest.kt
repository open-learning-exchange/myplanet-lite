package org.ole.planet.myplanet.lite

import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import io.noties.markwon.Markwon
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.mockito.kotlin.mock
import org.ole.planet.myplanet.lite.dashboard.DashboardNewsRepository.NewsDocument
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DashboardNewsViewHolderTest {

    private lateinit var view: View
    private lateinit var viewHolder: DashboardVoicesFragment.DashboardNewsViewHolder
    private lateinit var markwon: Markwon
    private lateinit var avatarBinder: (ImageView, String?, Boolean) -> Unit
    private lateinit var imageBinder: (ImageView, String) -> Unit
    private lateinit var onImageClicked: (DashboardVoicesFragment.DashboardNewsItem, Int) -> Unit
    private lateinit var onPostClicked: (DashboardVoicesFragment.DashboardNewsItem) -> Unit
    private lateinit var onDeleteClicked: (DashboardVoicesFragment.DashboardNewsItem) -> Unit
    private lateinit var onShareClicked: (DashboardVoicesFragment.DashboardNewsItem) -> Unit
    private lateinit var onEditClicked: (DashboardVoicesFragment.DashboardNewsItem) -> Unit
    private lateinit var onAuthorClicked: (DashboardVoicesFragment.DashboardNewsItem) -> Unit

    private fun createDummyItem(
        message: String? = "Test Message",
        commentCount: Int = 5,
        canEdit: Boolean = false,
        canDelete: Boolean = false,
        canShare: Boolean = false,
        imagePaths: List<String> = emptyList()
    ): DashboardVoicesFragment.DashboardNewsItem {
        return DashboardVoicesFragment.DashboardNewsItem(
            id = "1",
            author = "Test Author",
            username = "testuser",
            metadata = "Test Meta",
            message = message,
            hasAvatar = true,
            imagePaths = imagePaths,
            commentCount = commentCount,
            timestamp = 0L,
            canEdit = canEdit,
            canDelete = canDelete,
            canShare = canShare,
            document = mock(NewsDocument::class.java)
        )
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.setTheme(R.style.Theme_MyPlanetLite)
        view = LayoutInflater.from(context).inflate(R.layout.item_dashboard_post, null, false)

        markwon = mock(Markwon::class.java)
        avatarBinder = mock()
        imageBinder = mock()
        onImageClicked = mock()
        onPostClicked = mock()
        onDeleteClicked = mock()
        onShareClicked = mock()
        onEditClicked = mock()
        onAuthorClicked = mock()

        viewHolder = DashboardVoicesFragment.DashboardNewsViewHolder(
            view, markwon, avatarBinder, imageBinder, onImageClicked, onPostClicked,
            onDeleteClicked, onShareClicked, onEditClicked, onAuthorClicked
        )
    }

    @Test
    fun `bind with null or blank message hides body view`() {
        val item = createDummyItem(message = "")
        viewHolder.bind(item)

        val bodyView = view.findViewById<TextView>(R.id.postBody)
        assertEquals(View.GONE, bodyView.visibility)
        assertEquals("", bodyView.text.toString())
        assertFalse(bodyView.hasOnClickListeners())
    }

    @Test
    fun `bind with message shows and sets markdown`() {
        val item = createDummyItem(message = "**Bold**")
        viewHolder.bind(item)

        val bodyView = view.findViewById<TextView>(R.id.postBody)
        assertEquals(View.VISIBLE, bodyView.visibility)
        verify(markwon).setMarkdown(bodyView, "**Bold**")
        assertTrue(bodyView.hasOnClickListeners())

        bodyView.performClick()
        verify(onPostClicked).invoke(item)
    }

    @Test
    fun `bind sets text properties`() {
        val item = createDummyItem()
        viewHolder.bind(item)

        val authorView = view.findViewById<TextView>(R.id.postAuthor)
        val metadataView = view.findViewById<TextView>(R.id.postMetadata)
        val commentCountView = view.findViewById<TextView>(R.id.postCommentsCount)

        assertEquals("Test Author", authorView.text.toString())
        assertEquals("Test Meta", metadataView.text.toString())
        assertEquals("5", commentCountView.text.toString())
    }

    @Test
    fun `bind invokes avatarBinder and sets click listeners for author info`() {
        val item = createDummyItem()
        viewHolder.bind(item)

        val avatarView = view.findViewById<ImageView>(R.id.postAvatar)
        val authorView = view.findViewById<TextView>(R.id.postAuthor)
        val metadataView = view.findViewById<TextView>(R.id.postMetadata)

        verify(avatarBinder).invoke(avatarView, item.username, item.hasAvatar)

        assertTrue(avatarView.hasOnClickListeners())
        assertTrue(authorView.hasOnClickListeners())
        assertTrue(metadataView.hasOnClickListeners())

        avatarView.performClick()
        authorView.performClick()
        metadataView.performClick()

        verify(onAuthorClicked, times(3)).invoke(item)
    }

    @Test
    fun `bind sets up comments click listeners`() {
        val item = createDummyItem()
        viewHolder.bind(item)

        val commentsIconView = view.findViewById<ImageView>(R.id.postCommentsIcon)
        val commentCountView = view.findViewById<TextView>(R.id.postCommentsCount)
        val commentsContainer = view.findViewById<View>(R.id.postCommentsContainer)

        commentsIconView.performClick()
        commentCountView.performClick()
        commentsContainer.performClick()

        verify(onPostClicked, times(3)).invoke(item)
    }

    @Test
    fun `bind sets up actions correctly when enabled`() {
        val item = createDummyItem(canEdit = true, canDelete = true, canShare = true)
        viewHolder.bind(item)

        val editAction = view.findViewById<View>(R.id.postActionEdit)
        val deleteAction = view.findViewById<View>(R.id.postActionDelete)
        val shareAction = view.findViewById<View>(R.id.postActionShare)

        assertTrue(editAction.isEnabled)
        assertEquals(1f, editAction.alpha)
        editAction.performClick()
        verify(onEditClicked).invoke(item)

        assertTrue(deleteAction.isEnabled)
        assertEquals(1f, deleteAction.alpha)
        deleteAction.performClick()
        verify(onDeleteClicked).invoke(item)

        assertTrue(shareAction.isEnabled)
        assertEquals(1f, shareAction.alpha)
        shareAction.performClick()
        verify(onShareClicked).invoke(item)
    }

    @Test
    fun `bind disables actions correctly when disabled`() {
        val item = createDummyItem(canEdit = false, canDelete = false, canShare = false)
        viewHolder.bind(item)

        val editAction = view.findViewById<View>(R.id.postActionEdit)
        val deleteAction = view.findViewById<View>(R.id.postActionDelete)
        val shareAction = view.findViewById<View>(R.id.postActionShare)

        assertFalse(editAction.isEnabled)
        assertFalse(deleteAction.isEnabled)
        assertFalse(shareAction.isEnabled)

        assertFalse(editAction.hasOnClickListeners())
        assertFalse(deleteAction.hasOnClickListeners())
        assertFalse(shareAction.hasOnClickListeners())
    }
}
