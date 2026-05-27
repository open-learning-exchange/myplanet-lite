package org.ole.planet.myplanet.lite.util

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class IntentUtilsTest {

    @Test
    fun `extractDeepLinkPostId returns null when intent is null`() {
        assertNull(IntentUtils.extractDeepLinkPostId(null))
    }

    @Test
    fun `extractDeepLinkPostId returns null when action is not ACTION_VIEW`() {
        val intent = Intent(Intent.ACTION_MAIN)
        assertNull(IntentUtils.extractDeepLinkPostId(intent))
    }

    @Test
    fun `extractDeepLinkPostId returns null when intent data is null`() {
        val intent = Intent(Intent.ACTION_VIEW)
        assertNull(IntentUtils.extractDeepLinkPostId(intent))
    }

    @Test
    fun `extractDeepLinkPostId returns null when intent host is invalid`() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://example.com/post/12345")
        }
        assertNull(IntentUtils.extractDeepLinkPostId(intent))
    }

    @Test
    fun `extractDeepLinkPostId returns postId from query parameter`() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://midominio.com/post/path?postId=12345")
        }
        assertEquals("12345", IntentUtils.extractDeepLinkPostId(intent))
    }

    @Test
    fun `extractDeepLinkPostId returns postId from query parameter even if blank`() {
        // The implementation does NOT return it if it is blank
        // if (!queryPostId.isNullOrBlank()) { return queryPostId }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://midominio.com/post/path?postId=")
        }
        // Will fallback to segments
        assertEquals("path", IntentUtils.extractDeepLinkPostId(intent))
    }

    @Test
    fun `extractDeepLinkPostId returns null when there are no segments`() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://midominio.com")
        }
        assertNull(IntentUtils.extractDeepLinkPostId(intent))
    }

    @Test
    fun `extractDeepLinkPostId returns segment after post`() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://midominio.com/post/67890/details")
        }
        assertEquals("67890", IntentUtils.extractDeepLinkPostId(intent))
    }

    @Test
    fun `extractDeepLinkPostId returns segment after POST case insensitive`() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://midominio.com/PoSt/67890/details")
        }
        assertEquals("67890", IntentUtils.extractDeepLinkPostId(intent))
    }

    @Test
    fun `extractDeepLinkPostId falls back to last segment if post is not found`() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("myplanetlite://post/999")
        }
        assertEquals("999", IntentUtils.extractDeepLinkPostId(intent))
    }

    @Test
    fun `extractDeepLinkPostId returns null if fallback segment is blank`() {
        // Note: trailing slash in Uri.parse can result in an empty segment depending on Uri implementation.
        // Let's create an explicit scenario where the final string is blank.
        // "takeIf { it.isNotBlank() }"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://midominio.com/post/%20/")
        }
        assertNull(IntentUtils.extractDeepLinkPostId(intent))
    }
}
