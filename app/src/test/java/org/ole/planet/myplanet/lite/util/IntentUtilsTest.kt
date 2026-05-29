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
    fun `extractDeepLinkPostId returns postId from query parameter`() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://example.com/some/path?postId=12345")
        }
        assertEquals("12345", IntentUtils.extractDeepLinkPostId(intent))
    }

    @Test
    fun `extractDeepLinkPostId returns postId from query parameter even if blank`() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://example.com/some/path?postId=")
        }
        // Will fallback to segments
        assertEquals("path", IntentUtils.extractDeepLinkPostId(intent))
    }

    @Test
    fun `extractDeepLinkPostId returns null when there are no segments`() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://example.com")
        }
        assertNull(IntentUtils.extractDeepLinkPostId(intent))
    }

    @Test
    fun `extractDeepLinkPostId returns segment after post`() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://example.com/api/post/67890/details")
        }
        assertEquals("67890", IntentUtils.extractDeepLinkPostId(intent))
    }

    @Test
    fun `extractDeepLinkPostId returns segment after POST case insensitive`() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://example.com/api/PoSt/67890/details")
        }
        assertEquals("67890", IntentUtils.extractDeepLinkPostId(intent))
    }

    @Test
    fun `extractDeepLinkPostId falls back to last segment if post is last`() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://example.com/api/post")
        }
        assertEquals("post", IntentUtils.extractDeepLinkPostId(intent))
    }

    @Test
    fun `extractDeepLinkPostId falls back to last segment if post is not found`() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://example.com/api/users/999")
        }
        assertEquals("999", IntentUtils.extractDeepLinkPostId(intent))
    }

    @Test
    fun `extractDeepLinkPostId returns null if fallback segment is blank`() {
        // Note: trailing slash in Uri.parse can result in an empty segment depending on Uri implementation.
        // Let's create an explicit scenario where the final string is blank.
        // "takeIf { it.isNotBlank() }"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://example.com/api/%20/")
        }
        assertNull(IntentUtils.extractDeepLinkPostId(intent))
    }
}
