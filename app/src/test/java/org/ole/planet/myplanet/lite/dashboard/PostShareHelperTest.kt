package org.ole.planet.myplanet.lite.dashboard

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockedStatic
import org.mockito.Mockito.`when`
import org.mockito.Mockito.any
import org.mockito.Mockito.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.verify
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import org.ole.planet.myplanet.lite.R
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
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

        val intentCaptor = ArgumentCaptor.forClass(Intent::class.java)
        verify(context).startActivity(intentCaptor.capture())

        val chooserIntent = intentCaptor.value
        assertEquals("Share post", chooserIntent.getStringExtra(Intent.EXTRA_TITLE))

        val targetIntent = chooserIntent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertNotNull(targetIntent)
        assertEquals(Intent.ACTION_SEND, targetIntent?.action)
        assertEquals("text/plain", targetIntent?.type)

        val expectedText = "Shared from my server\n\nJohn Doe\n\nHello world!"
        assertEquals(expectedText, targetIntent?.getStringExtra(Intent.EXTRA_TEXT))
        val expectedHtml = "Shared from my server<br/><br/>Hello world!"
        assertEquals(expectedHtml, targetIntent?.getStringExtra(Intent.EXTRA_HTML_TEXT))
        assertEquals("Shared from my server", targetIntent?.getStringExtra(Intent.EXTRA_SUBJECT))
        assertEquals("Shared from my server", targetIntent?.getStringExtra(Intent.EXTRA_TITLE))
    }

    @Test
    fun testSharePostWithImages() = runTest {
        mockWebServer.enqueue(MockResponse().setBody("image content").setResponseCode(200))

        val mockContentResolver = mock(android.content.ContentResolver::class.java)
        `when`(context.contentResolver).thenReturn(mockContentResolver)

        val clipDataStaticMock = mockStatic(android.content.ClipData::class.java)
        try {
            val mockClipData = mock(android.content.ClipData::class.java)
            clipDataStaticMock.`when`<android.content.ClipData> { android.content.ClipData.newUri(any(), anyString(), any()) }.thenReturn(mockClipData)

            helper.sharePost(
                _postId = "123",
                author = "John Doe",
                message = "Hello world!",
                imagePaths = listOf("image1.jpg")
            )
            testDispatcher.scheduler.advanceUntilIdle()

            val intentCaptor = ArgumentCaptor.forClass(Intent::class.java)
            verify(context).startActivity(intentCaptor.capture())

            val targetIntent = intentCaptor.value.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
            assertNotNull(targetIntent)
            assertEquals(Intent.ACTION_SEND, targetIntent?.action)
            assertEquals("image/*", targetIntent?.type)
            assertTrue((targetIntent?.flags ?: 0) and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertNotNull(targetIntent?.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        } finally {
            clipDataStaticMock.close()
        }
    }

    @Test
    fun testSharePostWithMultipleImages() = runTest {
        mockWebServer.enqueue(MockResponse().setBody("image content 1").setResponseCode(200))
        mockWebServer.enqueue(MockResponse().setBody("image content 2").setResponseCode(200))

        val mockContentResolver = mock(android.content.ContentResolver::class.java)
        `when`(context.contentResolver).thenReturn(mockContentResolver)

        val clipDataStaticMock = mockStatic(android.content.ClipData::class.java)
        try {
            val mockClipData = mock(android.content.ClipData::class.java)
            clipDataStaticMock.`when`<android.content.ClipData> { android.content.ClipData.newUri(any(), anyString(), any()) }.thenReturn(mockClipData)

            helper.sharePost(
                _postId = "123",
                author = "John Doe",
                message = "Hello world!",
                imagePaths = listOf("image1.jpg", "image2.jpg")
            )
            testDispatcher.scheduler.advanceUntilIdle()

            val intentCaptor = ArgumentCaptor.forClass(Intent::class.java)
            verify(context).startActivity(intentCaptor.capture())

            val targetIntent = intentCaptor.value.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
            assertNotNull(targetIntent)
            assertEquals(Intent.ACTION_SEND_MULTIPLE, targetIntent?.action)
            assertEquals("image/*", targetIntent?.type)
            assertTrue((targetIntent?.flags ?: 0) and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)

            val streams = targetIntent?.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            assertNotNull(streams)
            assertEquals(2, streams?.size)
        } finally {
            clipDataStaticMock.close()
        }
    }

    @Test
    fun testSharePostMissingBaseUrl() = runTest {
        val helperNoUrl = PostShareHelper(
            context = context,
            baseUrlProvider = { null },
            sessionCookieProvider = { "session=1234" },
            serverNameProvider = { "my server" }
        )

        helperNoUrl.sharePost(
            _postId = "123",
            author = "John Doe",
            message = "Hello world!",
            imagePaths = listOf("image1.jpg")
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val intentCaptor = ArgumentCaptor.forClass(Intent::class.java)
        verify(context).startActivity(intentCaptor.capture())

        val targetIntent = intentCaptor.value.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertNotNull(targetIntent)
        // Since URL is missing, images aren't downloaded, and it falls back to text/plain
        assertEquals(Intent.ACTION_SEND, targetIntent?.action)
        assertEquals("text/plain", targetIntent?.type)
        assertNull(targetIntent?.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
    }

    @Test
    fun testSharePostMissingCookie() = runTest {
        mockWebServer.enqueue(MockResponse().setBody("image content").setResponseCode(200))
        val mockContentResolver = mock(android.content.ContentResolver::class.java)
        `when`(context.contentResolver).thenReturn(mockContentResolver)

        val clipDataStaticMock = mockStatic(android.content.ClipData::class.java)
        try {
            val mockClipData = mock(android.content.ClipData::class.java)
            clipDataStaticMock.`when`<android.content.ClipData> { android.content.ClipData.newUri(any(), anyString(), any()) }.thenReturn(mockClipData)

            val helperNoCookie = PostShareHelper(
                context = context,
                baseUrlProvider = { mockWebServer.url("/").toString() },
                sessionCookieProvider = { null },
                serverNameProvider = { "my server" }
            )

            helperNoCookie.sharePost(
                _postId = "123",
                author = "John Doe",
                message = "Hello world!",
                imagePaths = listOf("image1.jpg")
            )
            testDispatcher.scheduler.advanceUntilIdle()

            val intentCaptor = ArgumentCaptor.forClass(Intent::class.java)
            verify(context).startActivity(intentCaptor.capture())

            val targetIntent = intentCaptor.value.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
            assertNotNull(targetIntent)
            // Image still downloads even without a cookie header if server doesn't reject it
            assertEquals(Intent.ACTION_SEND, targetIntent?.action)
            assertEquals("image/*", targetIntent?.type)
            assertNotNull(targetIntent?.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        } finally {
            clipDataStaticMock.close()
        }
    }

    @Test
    fun testSharePostNetworkError() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(500))

        helper.sharePost(
            _postId = "123",
            author = "John Doe",
            message = "Hello world!",
            imagePaths = listOf("image1.jpg")
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val intentCaptor = ArgumentCaptor.forClass(Intent::class.java)
        verify(context).startActivity(intentCaptor.capture())

        val targetIntent = intentCaptor.value.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertNotNull(targetIntent)
        // Server returned 500, so image download failed. Falls back to text/plain.
        assertEquals(Intent.ACTION_SEND, targetIntent?.action)
        assertEquals("text/plain", targetIntent?.type)
        assertNull(targetIntent?.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
    }

}
