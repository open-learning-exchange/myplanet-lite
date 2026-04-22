package org.ole.planet.myplanet.lite

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.ole.planet.myplanet.lite.util.IntentUtils
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class IntentSecurityTest {

    @Test
    fun extractDeepLinkPostId_withOpaqueUri_returnsNull() {
        // An opaque URI like mailto: or tel: will cause UnsupportedOperationException
        // if we try to call getQueryParameter on it.
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("mailto:test@test.com"))
        val result = IntentUtils.extractDeepLinkPostId(intent)
        assertNull("Opaque URIs should safely return null", result)
    }

    @Test
    fun extractDeepLinkPostId_withValidHierarchicalUri_returnsPostId() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://midominio.com/post?postId=12345"))
        val result = IntentUtils.extractDeepLinkPostId(intent)
        assertEquals("12345", result)
    }

    @Test
    fun extractDeepLinkPostId_withValidPathUri_returnsPostId() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("myplanetlite://post/67890"))
        val result = IntentUtils.extractDeepLinkPostId(intent)
        assertEquals("67890", result)
    }
}
