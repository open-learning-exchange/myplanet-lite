package org.ole.planet.myplanet.lite.dashboard

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockedStatic
import org.mockito.Mockito.any
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.ole.planet.myplanet.lite.R
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@org.junit.runner.RunWith(androidx.test.ext.junit.runners.AndroidJUnit4::class)
@org.robolectric.annotation.Config(sdk = [28])
class PostShareHelperTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var helper: PostShareHelper

    @Mock
    private lateinit var context: Context

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var htmlStaticMock: MockedStatic<android.text.Html>
    private lateinit var fileProviderStaticMock: MockedStatic<FileProvider>

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)

        mockWebServer = MockWebServer()
        mockWebServer.start()

        htmlStaticMock = mockStatic(android.text.Html::class.java)
        htmlStaticMock.`when`<String> { android.text.Html.escapeHtml(anyString()) }.thenAnswer { invocation ->
            val str = invocation.getArgument<String>(0)
            str.replace("<", "&lt;").replace(">", "&gt;")
        }

        fileProviderStaticMock = mockStatic(FileProvider::class.java)
        fileProviderStaticMock.`when`<Uri> { FileProvider.getUriForFile(any(Context::class.java), anyString(), any(File::class.java)) }.thenAnswer {
            mock(Uri::class.java)
        }

        `when`(context.getString(R.string.dashboard_share_post_fallback)).thenReturn("fallback text")
        `when`(context.getString(R.string.dashboard_share_post_server_fallback)).thenReturn("fallback server")
        `when`(context.getString(R.string.dashboard_share_post_title, "my server")).thenReturn("Shared from my server")
        `when`(context.getString(R.string.dashboard_share_post_title, "fallback server")).thenReturn("Shared from fallback server")
        `when`(context.getString(R.string.dashboard_share_post_chooser_title)).thenReturn("Share post")
        `when`(context.packageName).thenReturn("org.ole.planet.myplanet.lite")
        `when`(context.cacheDir).thenReturn(File(System.getProperty("java.io.tmpdir") ?: "."))

        helper = PostShareHelper(
            context = context,
            baseUrlProvider = { mockWebServer.url("/").toString() },
            sessionCookieProvider = { "session=1234" },
            serverNameProvider = { "my server" }
        )
    }

    @After
    fun teardown() {
        mockWebServer.shutdown()
        Dispatchers.resetMain()
        htmlStaticMock.close()
        fileProviderStaticMock.close()
    }

    @Test
    fun testSanitizeMessage() {
        val raw = "Hello **world**! [link](http://example.com) ![image](http://example.com/image.jpg)"
        val expected = "Hello world! link"
        val result = PostShareHelper.sanitizeMessage(raw)
        assertEquals(expected, result)
    }

    @Test
    fun testSanitizeMessageEmpty() {
        assertNull(PostShareHelper.sanitizeMessage(null))
        assertNull(PostShareHelper.sanitizeMessage("   "))
    }

    @Test
    fun testSanitizeForHtml() {
        val raw = "Hello! ![image](http://example.com/image.jpg)"
        val expected = "Hello!"
        val result = PostShareHelper.sanitizeForHtml(raw)
        assertEquals(expected, result)
    }

    @Test
    fun testSanitizeForHtmlEmpty() {
        assertNull(PostShareHelper.sanitizeForHtml(null))
        assertNull(PostShareHelper.sanitizeForHtml("   "))
    }

    @Test
    fun testToHtml() {
        val text = "Hello **world**\n[link](http://example.com)"
        val result = PostShareHelper.toHtml(text)
        val expected = "Hello <b>world</b><br/><a href=\"http://example.com\">link</a>"
        assertEquals(expected, result)
    }

    @Test
    fun testResolveUrl() {
        val companion = PostShareHelper.Companion
        val method = companion::class.java.getDeclaredMethod("resolveUrl", String::class.java, String::class.java)
        method.isAccessible = true

        assertEquals("http://test.com/db/image.jpg", method.invoke(companion, "image.jpg", "http://test.com/"))
        assertEquals("http://test.com/db/image.jpg", method.invoke(companion, "db/image.jpg", "http://test.com/"))
        assertEquals("https://external.com/image.jpg", method.invoke(companion, "https://external.com/image.jpg", "http://test.com/"))
        assertNull(method.invoke(companion, "   ", "http://test.com/"))
        assertNull(method.invoke(companion, "image.jpg", "   "))
    }

    @Test
    fun testSharePostWithoutImages() = runTest {
        helper.sharePost(
            _postId = "123",
            author = "John Doe",
            message = "Hello world!",
            imagePaths = emptyList()
        )
        testDispatcher.scheduler.advanceUntilIdle()

        verify(context).startActivity(any(Intent::class.java))
    }

    @Test
    fun testSharePostWithImages() = runTest {
        mockWebServer.enqueue(MockResponse().setBody("image content").setResponseCode(200))

        val mockContentResolver = mock(android.content.ContentResolver::class.java)
        `when`(context.contentResolver).thenReturn(mockContentResolver)

        val clipDataStaticMock = mockStatic(android.content.ClipData::class.java)
        val mockClipData = mock(android.content.ClipData::class.java)
        clipDataStaticMock.`when`<android.content.ClipData> { android.content.ClipData.newUri(any(), anyString(), any()) }.thenReturn(mockClipData)

        helper.sharePost(
            _postId = "123",
            author = "John Doe",
            message = "Hello world!",
            imagePaths = listOf("image1.jpg")
        )
        testDispatcher.scheduler.advanceUntilIdle()

        verify(context).startActivity(any(Intent::class.java))

        clipDataStaticMock.close()
    }

    @Test
    fun testSharePostWithMultipleImages() = runTest {
        mockWebServer.enqueue(MockResponse().setBody("image content 1").setResponseCode(200))
        mockWebServer.enqueue(MockResponse().setBody("image content 2").setResponseCode(200))

        val mockContentResolver = mock(android.content.ContentResolver::class.java)
        `when`(context.contentResolver).thenReturn(mockContentResolver)

        val clipDataStaticMock = mockStatic(android.content.ClipData::class.java)
        val mockClipData = mock(android.content.ClipData::class.java)
        clipDataStaticMock.`when`<android.content.ClipData> { android.content.ClipData.newUri(any(), anyString(), any()) }.thenReturn(mockClipData)

        helper.sharePost(
            _postId = "123",
            author = "John Doe",
            message = "Hello world!",
            imagePaths = listOf("image1.jpg", "image2.jpg")
        )
        testDispatcher.scheduler.advanceUntilIdle()

        verify(context).startActivity(any(Intent::class.java))

        clipDataStaticMock.close()
    }
}
