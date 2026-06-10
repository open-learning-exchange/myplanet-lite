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
            data = Uri.parse("myplanetlite://post/path?postId=12345")
        }
        assertEquals("12345", IntentUtils.extractDeepLinkPostId(intent))
    }

    @Test
    fun `extractDeepLinkPostId returns postId from query parameter even if blank`() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("myplanetlite://post/path?postId=")
        }
        // Will fallback to segments
        assertEquals("path", IntentUtils.extractDeepLinkPostId(intent))
    }

    @Test
    fun `extractDeepLinkPostId returns null when there are no segments`() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("myplanetlite://post")
        }
        assertNull(IntentUtils.extractDeepLinkPostId(intent))
    }

    @Test
    fun `extractDeepLinkPostId returns path segment`() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("myplanetlite://post/67890")
        }
        assertEquals("67890", IntentUtils.extractDeepLinkPostId(intent))
    }

    @Test
    fun `extractDeepLinkPostId accepts post host case insensitive`() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("myplanetlite://POST/67890")
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
            data = Uri.parse("myplanetlite://post/%20/")
        }
        assertNull(IntentUtils.extractDeepLinkPostId(intent))
    }

    @Test
    fun `extractDeepLinkSurvey parses learning survey link`() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://planet.learning.ole.org/survey/c533316781dc0ed1f8d421ae4c5bc20d/00d2d353e83ebadf06f2051402d104a7")
        }

        val result = IntentUtils.extractDeepLinkSurvey(intent)

        assertEquals("https://planet.learning.ole.org", result?.baseUrl)
        assertEquals("c533316781dc0ed1f8d421ae4c5bc20d", result?.teamId)
        assertEquals("00d2d353e83ebadf06f2051402d104a7", result?.surveyId)
    }

    @Test
    fun `extractDeepLinkSurvey parses survey link from another server`() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://example.com/survey/team/survey")
        }

        val result = IntentUtils.extractDeepLinkSurvey(intent)

        assertEquals("https://example.com", result?.baseUrl)
        assertEquals("team", result?.teamId)
        assertEquals("survey", result?.surveyId)
    }

    @Test
    fun `extractDeepLinkSurvey rejects non survey web links`() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://example.com/news/team/survey")
        }

        assertNull(IntentUtils.extractDeepLinkSurvey(intent))
    }

    @Test
    fun `extractDeepLinkSurvey parses custom scheme with server query`() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("myplanetlite://survey/team-1/survey-1?server=https%3A%2F%2Fplanet.learning.ole.org%2F")
        }

        val result = IntentUtils.extractDeepLinkSurvey(intent)

        assertEquals("https://planet.learning.ole.org", result?.baseUrl)
        assertEquals("team-1", result?.teamId)
        assertEquals("survey-1", result?.surveyId)
    }

    @Test
    fun `sameServer ignores trailing slash and case`() {
        assertEquals(
            true,
            IntentUtils.sameServer("https://PLANET.learning.ole.org/", "https://planet.learning.ole.org"),
        )
    }
}
