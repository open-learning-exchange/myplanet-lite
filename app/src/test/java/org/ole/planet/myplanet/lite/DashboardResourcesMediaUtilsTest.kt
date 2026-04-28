package org.ole.planet.myplanet.lite

import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardResourcesMediaUtilsTest {

    @Test
    fun `extensionForImageMimeType returns png for png mime types`() {
        assertEquals("png", DashboardResourcesMediaUtils.extensionForImageMimeType("image/png"))
        assertEquals("png", DashboardResourcesMediaUtils.extensionForImageMimeType("image/x-png"))
        assertEquals("png", DashboardResourcesMediaUtils.extensionForImageMimeType("PNG"))
    }

    @Test
    fun `extensionForImageMimeType returns webp for webp mime types`() {
        assertEquals("webp", DashboardResourcesMediaUtils.extensionForImageMimeType("image/webp"))
        assertEquals("webp", DashboardResourcesMediaUtils.extensionForImageMimeType("WEBP"))
    }

    @Test
    fun `extensionForImageMimeType returns jpg as fallback for other mime types`() {
        assertEquals("jpg", DashboardResourcesMediaUtils.extensionForImageMimeType("image/jpeg"))
        assertEquals("jpg", DashboardResourcesMediaUtils.extensionForImageMimeType("image/gif"))
        assertEquals("jpg", DashboardResourcesMediaUtils.extensionForImageMimeType("image/bmp"))
        assertEquals("jpg", DashboardResourcesMediaUtils.extensionForImageMimeType("application/pdf"))
        assertEquals("jpg", DashboardResourcesMediaUtils.extensionForImageMimeType(""))
        assertEquals("jpg", DashboardResourcesMediaUtils.extensionForImageMimeType("UNKNOWN"))
    }
}
