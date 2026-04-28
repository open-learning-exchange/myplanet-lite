package org.ole.planet.myplanet.lite

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.mockito.Mockito.*
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P])
class DashboardResourcesMediaUtilsTest {

    @Test
    fun testSanitizeResourceName() {
        assertEquals("valid_name", DashboardResourcesMediaUtils.sanitizeResourceName("valid_name"))
        assertEquals("invalid_name", DashboardResourcesMediaUtils.sanitizeResourceName("invalid@name!"))
        assertEquals("multiple_spaces", DashboardResourcesMediaUtils.sanitizeResourceName("multiple   spaces"))
        assertTrue(DashboardResourcesMediaUtils.sanitizeResourceName("").startsWith("resource_"))
    }

    @Test
    fun testApplyWebCompatibleResourceDefaults() {
        val payload = JSONObject()
        DashboardResourcesMediaUtils.applyWebCompatibleResourceDefaults(payload)

        assertEquals("", payload.getString("author"))
        assertEquals("", payload.getString("year"))
        assertEquals("", payload.getString("publisher"))
        assertEquals("", payload.getString("linkToLicense"))
        assertEquals("", payload.getString("openWith"))
        assertEquals(0, payload.getJSONArray("resourceFor").length())
        assertEquals("", payload.getString("medium"))
        assertEquals("", payload.getString("resourceType"))

        val existingPayload = JSONObject().apply {
            put("author", "Test Author")
            put("year", "2023")
        }
        DashboardResourcesMediaUtils.applyWebCompatibleResourceDefaults(existingPayload)

        assertEquals("Test Author", existingPayload.getString("author"))
        assertEquals("2023", existingPayload.getString("year"))
        assertEquals("", existingPayload.getString("publisher"))
    }

    @Test
    fun testNormalizeResourceMediaType() {
        assertEquals("image", DashboardResourcesMediaUtils.normalizeResourceMediaType("IMAGE/PNG"))
        assertEquals("video", DashboardResourcesMediaUtils.normalizeResourceMediaType("video/mp4"))
        assertEquals("audio", DashboardResourcesMediaUtils.normalizeResourceMediaType("audio/mpeg"))
        assertEquals("pdf", DashboardResourcesMediaUtils.normalizeResourceMediaType("application/pdf"))
        assertEquals("text/plain", DashboardResourcesMediaUtils.normalizeResourceMediaType("text/plain"))
    }

    @Test
    fun testExtensionForImageMimeType() {
        assertEquals("png", DashboardResourcesMediaUtils.extensionForImageMimeType("image/png"))
        assertEquals("webp", DashboardResourcesMediaUtils.extensionForImageMimeType("image/webp"))
        assertEquals("jpg", DashboardResourcesMediaUtils.extensionForImageMimeType("image/jpeg"))
        assertEquals("jpg", DashboardResourcesMediaUtils.extensionForImageMimeType("unknown/mime"))
    }

    @Test
    fun testAllowedVideoHeights() {
        assertEquals(listOf(480, 576, 720, 1080), DashboardResourcesMediaUtils.allowedVideoHeights(1920))
        assertEquals(listOf(480, 576, 720, 1080), DashboardResourcesMediaUtils.allowedVideoHeights(1080))
        assertEquals(listOf(480, 576, 720), DashboardResourcesMediaUtils.allowedVideoHeights(720))
        assertEquals(listOf(480, 576), DashboardResourcesMediaUtils.allowedVideoHeights(576))
        assertEquals(listOf(480), DashboardResourcesMediaUtils.allowedVideoHeights(480))
        assertEquals(listOf(480), DashboardResourcesMediaUtils.allowedVideoHeights(240))
    }

    @Test
    fun testDefaultVideoHeightSelection() {
        assertEquals(720, DashboardResourcesMediaUtils.defaultVideoHeightSelection(1920))
        assertEquals(720, DashboardResourcesMediaUtils.defaultVideoHeightSelection(1080))
        assertEquals(720, DashboardResourcesMediaUtils.defaultVideoHeightSelection(720))
        assertEquals(576, DashboardResourcesMediaUtils.defaultVideoHeightSelection(576))
        assertEquals(480, DashboardResourcesMediaUtils.defaultVideoHeightSelection(480))
        assertEquals(480, DashboardResourcesMediaUtils.defaultVideoHeightSelection(240))
    }

    @Test
    fun testFormatDurationMs() {
        assertEquals("00:00", DashboardResourcesMediaUtils.formatDurationMs(0L))
        assertEquals("00:05", DashboardResourcesMediaUtils.formatDurationMs(5000L))
        assertEquals("01:05", DashboardResourcesMediaUtils.formatDurationMs(65000L))
        assertEquals("1:00:00", DashboardResourcesMediaUtils.formatDurationMs(3600000L))
        assertEquals("1:01:05", DashboardResourcesMediaUtils.formatDurationMs(3665000L))
    }

    @Test
    fun testEstimateAudioUploadSizeBytes() {
        // 128 kbps, 10 seconds -> 128,000 bits/sec * 10 sec = 1,280,000 bits = 160,000 bytes. With 1.05 multiplier = 168,000 bytes
        assertEquals(168000L, DashboardResourcesMediaUtils.estimateAudioUploadSizeBytes(128, 10000L))
        // 64 kbps, 5 seconds -> 64,000 * 5 = 320,000 bits = 40,000 bytes. With 1.05 multiplier = 42,000 bytes
        assertEquals(42000L, DashboardResourcesMediaUtils.estimateAudioUploadSizeBytes(64, 5000L))
        // Minimum threshold test
        assertEquals(1024L, DashboardResourcesMediaUtils.estimateAudioUploadSizeBytes(1, 1L))
    }

    @Test
    fun testEstimateVideoUploadSizeBytes() {
        // sourceSizeBytes = 1000000L, sourceHeight = 1080, selectedHeight = 720, sourceDuration = 10000, start = 0, end = 5000
        // durationFactor = 5000 / 10000 = 0.5
        // resolutionFactor = (720 / 1080)^2 = (2/3)^2 = 4/9
        // estimated = 1000000 * 4/9 * 0.5 * 0.95 = 211111L
        assertEquals(211111L, DashboardResourcesMediaUtils.estimateVideoUploadSizeBytes(1000000L, 1080, 720, 10000L, 0L, 5000L))

        // Minimum threshold test
        assertEquals(65536L, DashboardResourcesMediaUtils.estimateVideoUploadSizeBytes(1L, 1080, 720, 10000L, 0L, 5000L))
    }

    @Test
    fun testResolveDefaultLanguageIndex() {
        val context = mock(Context::class.java)
        val resources = mock(Resources::class.java)
        val configuration = mock(Configuration::class.java)
        val options = listOf("English", "French", "Spanish")

        // Mock getting string resources directly to bypass actual application context
        `when`(context.getString(R.string.language_name_nepali)).thenReturn("Nepali")
        `when`(context.getString(R.string.language_name_french)).thenReturn("French")
        `when`(context.getString(R.string.language_name_spanish)).thenReturn("Spanish")
        `when`(context.getString(R.string.language_name_arabic)).thenReturn("Arabic")
        `when`(context.getString(R.string.language_name_somali)).thenReturn("Somali")
        `when`(context.getString(R.string.language_name_hindi)).thenReturn("Hindi")
        `when`(context.getString(R.string.language_name_portuguese)).thenReturn("Portuguese")
        `when`(context.getString(R.string.language_name_english)).thenReturn("English")

        // Test English locale
        val localesEnglish = android.os.LocaleList(Locale.ENGLISH)
        `when`(configuration.locales).thenReturn(localesEnglish)
        `when`(resources.configuration).thenReturn(configuration)
        assertEquals(0, DashboardResourcesMediaUtils.resolveDefaultLanguageIndex(context, resources, options))

        // Test French locale
        val localesFrench = android.os.LocaleList(Locale.FRENCH)
        `when`(configuration.locales).thenReturn(localesFrench)
        assertEquals(1, DashboardResourcesMediaUtils.resolveDefaultLanguageIndex(context, resources, options))

        // Test Spanish locale
        val localesSpanish = android.os.LocaleList(Locale.forLanguageTag("es"))
        `when`(configuration.locales).thenReturn(localesSpanish)
        assertEquals(2, DashboardResourcesMediaUtils.resolveDefaultLanguageIndex(context, resources, options))

        // Test unlisted locale defaults to English if present
        val localesGerman = android.os.LocaleList(Locale.GERMAN)
        `when`(configuration.locales).thenReturn(localesGerman)
        assertEquals(0, DashboardResourcesMediaUtils.resolveDefaultLanguageIndex(context, resources, options))

        // Test unlisted locale defaults to 0 if English is also absent
        val optionsNoEnglish = listOf("French", "Spanish")
        assertEquals(0, DashboardResourcesMediaUtils.resolveDefaultLanguageIndex(context, resources, optionsNoEnglish))
    }
}
