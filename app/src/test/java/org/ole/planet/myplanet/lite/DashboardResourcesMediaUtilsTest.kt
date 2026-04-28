package org.ole.planet.myplanet.lite

import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardResourcesMediaUtilsTest {

    @Test
    fun testEstimateAudioUploadSizeBytes() {
        // Standard scenario: 128 kbps, 60 seconds (60000ms)
        // Math: 128000 bits/sec * 60 sec = 7680000 bits
        // Bytes: 7680000 / 8 = 960000 bytes
        // Overhead (1.05): 960000 * 1.05 = 1008000 bytes
        assertEquals(
            1008000L,
            DashboardResourcesMediaUtils.estimateAudioUploadSizeBytes(128, 60000L)
        )

        // Zero scenario: 0 kbps, 0ms
        // Expected: 1024L (fallback)
        assertEquals(
            1024L,
            DashboardResourcesMediaUtils.estimateAudioUploadSizeBytes(0, 0L)
        )

        // Very short duration: 128 kbps, 1ms
        // Bytes: 128000 * 0.001 / 8 = 16 bytes. With overhead -> 16.8 bytes.
        // Expected: 1024L (fallback)
        assertEquals(
            1024L,
            DashboardResourcesMediaUtils.estimateAudioUploadSizeBytes(128, 1L)
        )

        // Long duration scenario: 320 kbps, 1 hour (3600000ms)
        // Math: 320000 * 3600 = 1152000000 bits -> 144000000 bytes
        // Overhead: 144000000 * 1.05 = 151200000 bytes
        assertEquals(
            151200000L,
            DashboardResourcesMediaUtils.estimateAudioUploadSizeBytes(320, 3600000L)
        )
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
