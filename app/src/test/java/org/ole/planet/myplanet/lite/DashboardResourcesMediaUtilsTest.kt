package org.ole.planet.myplanet.lite

import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardResourcesMediaUtilsTest {

    @Test
    fun `normalizeResourceMediaType returns correct type for images`() {
        assertEquals("image", DashboardResourcesMediaUtils.normalizeResourceMediaType("image/jpeg"))
        assertEquals("image", DashboardResourcesMediaUtils.normalizeResourceMediaType("IMAGE/PNG"))
        assertEquals("image", DashboardResourcesMediaUtils.normalizeResourceMediaType("image/gif"))
        assertEquals("image", DashboardResourcesMediaUtils.normalizeResourceMediaType("image/webp"))
    }

    @Test
    fun `normalizeResourceMediaType returns correct type for videos`() {
        assertEquals("video", DashboardResourcesMediaUtils.normalizeResourceMediaType("video/mp4"))
        assertEquals("video", DashboardResourcesMediaUtils.normalizeResourceMediaType("VIDEO/WEBM"))
        assertEquals("video", DashboardResourcesMediaUtils.normalizeResourceMediaType("video/x-matroska"))
    }

    @Test
    fun `normalizeResourceMediaType returns correct type for audio`() {
        assertEquals("audio", DashboardResourcesMediaUtils.normalizeResourceMediaType("audio/mpeg"))
        assertEquals("audio", DashboardResourcesMediaUtils.normalizeResourceMediaType("AUDIO/WAV"))
        assertEquals("audio", DashboardResourcesMediaUtils.normalizeResourceMediaType("audio/ogg"))
    }

    @Test
    fun `normalizeResourceMediaType returns correct type for pdf`() {
        assertEquals("pdf", DashboardResourcesMediaUtils.normalizeResourceMediaType("application/pdf"))
        assertEquals("pdf", DashboardResourcesMediaUtils.normalizeResourceMediaType("APPLICATION/PDF"))
    }

    @Test
    fun `normalizeResourceMediaType returns normalized input for other types`() {
        assertEquals("application/json", DashboardResourcesMediaUtils.normalizeResourceMediaType("application/json"))
        assertEquals("text/html", DashboardResourcesMediaUtils.normalizeResourceMediaType("TEXT/HTML"))
        assertEquals("application/xml", DashboardResourcesMediaUtils.normalizeResourceMediaType("application/xml"))
        assertEquals("unknown", DashboardResourcesMediaUtils.normalizeResourceMediaType("UNKNOWN"))
    }
}
