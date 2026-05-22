package org.ole.planet.myplanet.lite.dashboard

import android.view.ContextThemeWrapper
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.test.core.app.ApplicationProvider
import io.noties.markwon.Markwon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.ole.planet.myplanet.lite.R
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PostDetailAdapterTest {

    private lateinit var adapter: PostDetailAdapter
    private lateinit var markwon: Markwon
    private lateinit var avatarBinder: (ImageView, String?, Boolean) -> Unit
    private lateinit var imageBinder: (ImageView, String) -> Unit
    private lateinit var onImageClicked: (List<String>, Int) -> Unit
    private lateinit var onDeleteClicked: () -> Unit
    private lateinit var onShareClicked: (PostDetailItem.Header) -> Unit
    private lateinit var onEditClicked: (PostDetailItem.Header) -> Unit
    private lateinit var onReplyClicked: () -> Unit
    private lateinit var onCommentEditClicked: (PostDetailItem.Comment) -> Unit
    private lateinit var onCommentDeleteClicked: (PostDetailItem.Comment) -> Unit
    private lateinit var themedContext: ContextThemeWrapper

    @Before
    fun setup() {
        val appContext = ApplicationProvider.getApplicationContext<android.content.Context>()
        themedContext = ContextThemeWrapper(appContext, com.google.android.material.R.style.Theme_MaterialComponents_Light)

        markwon = mock()
        avatarBinder = mock()
        imageBinder = mock()
        onImageClicked = mock()
        onDeleteClicked = mock()
        onShareClicked = mock()
        onEditClicked = mock()
        onReplyClicked = mock()
        onCommentEditClicked = mock()
        onCommentDeleteClicked = mock()

        adapter = PostDetailAdapter(
            markwon,
            avatarBinder,
            imageBinder,
            onImageClicked,
            onDeleteClicked,
            onShareClicked,
            onEditClicked,
            onReplyClicked,
            onCommentEditClicked,
            onCommentDeleteClicked
        )
    }

    @Test
    fun `getItemViewType returns correct types`() {
        val header = PostDetailItem.Header("1", "author", "user", true, "msg", emptyList(), 123L, 0, false, true, true, true, true)
        val comment = PostDetailItem.Comment("2", "author2", "user2", true, "msg2", emptyList(), 123L, true, true, null)
        adapter.submitList(listOf(header, comment))

        assertEquals(0, adapter.getItemViewType(0))
        assertEquals(1, adapter.getItemViewType(1))
    }

    @Test
    fun `bind header correctly populates data`() {
        val header = PostDetailItem.Header("1", "author1", "user1", true, "message1", emptyList(), 0L, 0, false, true, true, true, true)
        adapter.submitList(listOf(header))

        val parent = FrameLayout(themedContext)
        val viewHolder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(viewHolder, 0)

        val authorView = viewHolder.itemView.findViewById<TextView>(R.id.postDetailAuthor)
        val bodyView = viewHolder.itemView.findViewById<TextView>(R.id.postDetailBody)

        assertEquals("author1", authorView.text.toString())
        assertTrue(bodyView.isVisible)
        verify(markwon).setMarkdown(eq(bodyView), eq("message1"))
        verify(avatarBinder).invoke(any(), eq("user1"), eq(true))
    }

    @Test
    fun `bind comment correctly populates data`() {
        val comment = PostDetailItem.Comment("2", "author2", "user2", true, "message2", emptyList(), 0L, true, true, null)
        adapter.submitList(listOf(comment))

        val parent = FrameLayout(themedContext)
        val viewHolder = adapter.onCreateViewHolder(parent, 1)
        adapter.onBindViewHolder(viewHolder, 0)

        val authorView = viewHolder.itemView.findViewById<TextView>(R.id.commentAuthor)
        val bodyView = viewHolder.itemView.findViewById<TextView>(R.id.commentBody)

        assertEquals("author2", authorView.text.toString())
        assertTrue(bodyView.isVisible)
        verify(markwon).setMarkdown(eq(bodyView), eq("message2"))
        verify(avatarBinder).invoke(any(), eq("user2"), eq(true))
    }

    @Test
    fun `bind header handles empty message`() {
        val header = PostDetailItem.Header("1", "author1", "user1", true, "", emptyList(), 0L, 0, false, true, true, true, true)
        adapter.submitList(listOf(header))

        val parent = FrameLayout(themedContext)
        val viewHolder = adapter.onCreateViewHolder(parent, 0)
        adapter.onBindViewHolder(viewHolder, 0)

        val bodyView = viewHolder.itemView.findViewById<TextView>(R.id.postDetailBody)

        assertFalse(bodyView.isVisible)
        assertEquals("", bodyView.text.toString())
    }

    @Test
    fun `bind comment handles empty message`() {
        val comment = PostDetailItem.Comment("2", "author2", "user2", true, null, emptyList(), 0L, true, true, null)
        adapter.submitList(listOf(comment))

        val parent = FrameLayout(themedContext)
        val viewHolder = adapter.onCreateViewHolder(parent, 1)
        adapter.onBindViewHolder(viewHolder, 0)

        val bodyView = viewHolder.itemView.findViewById<TextView>(R.id.commentBody)

        assertFalse(bodyView.isVisible)
        assertEquals("", bodyView.text.toString())
    }
}
