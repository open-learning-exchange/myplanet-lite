package org.ole.planet.myplanet.lite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardResourcesMediaUtilsTest {

    @Test
    fun sanitizeResourceName_removesSpecialCharacters() {
        assertEquals("My_Resource", DashboardResourcesMediaUtils.sanitizeResourceName("My Resource!"))
        assertEquals("test-name_123", DashboardResourcesMediaUtils.sanitizeResourceName("test-name@123"))
    }

    @Test
    fun sanitizeResourceName_collapsesUnderscores() {
        assertEquals("a_b_c", DashboardResourcesMediaUtils.sanitizeResourceName("a____b!!!!c"))
    }

    @Test
    fun sanitizeResourceName_trimsUnderscores() {
        assertEquals("trimmed", DashboardResourcesMediaUtils.sanitizeResourceName("___trimmed___"))
    }

    @Test
    fun sanitizeResourceName_handlesBlankInput() {
        val result = DashboardResourcesMediaUtils.sanitizeResourceName("   ")
        assertTrue(result.startsWith("resource_"))
        assertTrue(result.substringAfter("resource_").toLong() > 0)
    }

    @Test
    fun sanitizeResourceName_handlesEmptyInput() {
        val result = DashboardResourcesMediaUtils.sanitizeResourceName("")
        assertTrue(result.startsWith("resource_"))
        assertTrue(result.substringAfter("resource_").toLong() > 0)
    }

    @Test
    fun sanitizeResourceName_handlesOnlySpecialCharacters() {
        val result = DashboardResourcesMediaUtils.sanitizeResourceName("!!!")
        assertTrue(result.startsWith("resource_"))
    }

    @Test
    fun normalizeResourceMediaType_returnsCorrectTypes() {
        assertEquals("image", DashboardResourcesMediaUtils.normalizeResourceMediaType("image/jpeg"))
        assertEquals("image", DashboardResourcesMediaUtils.normalizeResourceMediaType("IMAGE/PNG"))
        assertEquals("video", DashboardResourcesMediaUtils.normalizeResourceMediaType("video/mp4"))
        assertEquals("audio", DashboardResourcesMediaUtils.normalizeResourceMediaType("audio/mpeg"))
        assertEquals("pdf", DashboardResourcesMediaUtils.normalizeResourceMediaType("application/pdf"))
        assertEquals("text/plain", DashboardResourcesMediaUtils.normalizeResourceMediaType("text/plain"))
    }

    @Test
    fun extensionForImageMimeType_returnsCorrectExtensions() {
        assertEquals("png", DashboardResourcesMediaUtils.extensionForImageMimeType("image/png"))
        assertEquals("webp", DashboardResourcesMediaUtils.extensionForImageMimeType("image/webp"))
        assertEquals("jpg", DashboardResourcesMediaUtils.extensionForImageMimeType("image/jpeg"))
        assertEquals("jpg", DashboardResourcesMediaUtils.extensionForImageMimeType("unknown"))
    }

    @Test
    fun formatDurationMs_formatsCorrectly() {
        assertEquals("00:05", DashboardResourcesMediaUtils.formatDurationMs(5000))
        assertEquals("01:05", DashboardResourcesMediaUtils.formatDurationMs(65000))
        assertEquals("1:00:05", DashboardResourcesMediaUtils.formatDurationMs(3605000))
        assertEquals("00:00", DashboardResourcesMediaUtils.formatDurationMs(0))
        assertEquals("00:00", DashboardResourcesMediaUtils.formatDurationMs(-1000))
    }
}
