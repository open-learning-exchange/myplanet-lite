package org.ole.planet.myplanet.lite

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.P])
class DashboardResourcesMediaUtilsTest {

    @Test
    fun sanitizeResourceName_validName_noChanges() {
        assertEquals("valid_name-123", DashboardResourcesMediaUtils.sanitizeResourceName("valid_name-123"))
    }

    @Test
    fun sanitizeResourceName_replacesSpacesWithUnderscores() {
        assertEquals("file_name_with_spaces", DashboardResourcesMediaUtils.sanitizeResourceName("file name with spaces"))
    }

    @Test
    fun sanitizeResourceName_removesSpecialCharacters() {
        assertEquals("file_name", DashboardResourcesMediaUtils.sanitizeResourceName("file!@#$%^&*()name"))
    }

    @Test
    fun sanitizeResourceName_collapsesMultipleUnderscores() {
        assertEquals(
            "file_name---with_multiple_underscores",
            DashboardResourcesMediaUtils.sanitizeResourceName("file___name---with___multiple___underscores")
        )
    }

    @Test
    fun sanitizeResourceName_removesLeadingAndTrailingUnderscores() {
        assertEquals("file_name", DashboardResourcesMediaUtils.sanitizeResourceName("___file_name___"))
    }

    @Test
    fun sanitizeResourceName_emptyString_defaultsToResourcePrefix() {
        assertTrue(DashboardResourcesMediaUtils.sanitizeResourceName("").startsWith("resource_"))
    }

    @Test
    fun sanitizeResourceName_blankString_defaultsToResourcePrefix() {
        assertTrue(DashboardResourcesMediaUtils.sanitizeResourceName("   ").startsWith("resource_"))
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
        assertEquals("png", DashboardResourcesMediaUtils.extensionForImageMimeType("image/x-png"))
        assertEquals("png", DashboardResourcesMediaUtils.extensionForImageMimeType("PNG"))

        assertEquals("webp", DashboardResourcesMediaUtils.extensionForImageMimeType("image/webp"))
        assertEquals("webp", DashboardResourcesMediaUtils.extensionForImageMimeType("WEBP"))

        assertEquals("jpg", DashboardResourcesMediaUtils.extensionForImageMimeType("image/jpeg"))
        assertEquals("jpg", DashboardResourcesMediaUtils.extensionForImageMimeType("image/gif"))
        assertEquals("jpg", DashboardResourcesMediaUtils.extensionForImageMimeType("image/bmp"))
        assertEquals("jpg", DashboardResourcesMediaUtils.extensionForImageMimeType("application/pdf"))
        assertEquals("jpg", DashboardResourcesMediaUtils.extensionForImageMimeType(""))
        assertEquals("jpg", DashboardResourcesMediaUtils.extensionForImageMimeType("UNKNOWN"))
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
        val cases = listOf(
            1080 to 720,
            2160 to 720,
            720 to 720,
            1079 to 720,
            576 to 576,
            719 to 576,
            575 to 480,
            480 to 480,
            240 to 480,
            0 to 480,
            -1 to 480
        )

        cases.forEach { (sourceHeight, expectedHeight) ->
            assertEquals(
                "Failed for source height $sourceHeight",
                expectedHeight,
                DashboardResourcesMediaUtils.defaultVideoHeightSelection(sourceHeight)
            )
        }
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
        assertEquals(168000L, DashboardResourcesMediaUtils.estimateAudioUploadSizeBytes(128, 10000L))
        assertEquals(42000L, DashboardResourcesMediaUtils.estimateAudioUploadSizeBytes(64, 5000L))
        assertEquals(1024L, DashboardResourcesMediaUtils.estimateAudioUploadSizeBytes(1, 1L))
    }

    @Test
    fun testEstimateVideoUploadSizeBytes() {
        assertEquals(
            211111L,
            DashboardResourcesMediaUtils.estimateVideoUploadSizeBytes(
                1000000L,
                1080,
                720,
                10000L,
                0L,
                5000L
            )
        )

        assertEquals(
            65536L,
            DashboardResourcesMediaUtils.estimateVideoUploadSizeBytes(
                1L,
                1080,
                720,
                10000L,
                0L,
                5000L
            )
        )
    }

    @Test
    fun testResolveDefaultLanguageIndex() {
        val context = mock(Context::class.java)
        val resources = mock(Resources::class.java)
        val configuration = mock(Configuration::class.java)

        val options = listOf("English", "French", "Spanish")

        `when`(context.getString(R.string.language_name_english)).thenReturn("English")
        `when`(context.getString(R.string.language_name_french)).thenReturn("French")
        `when`(context.getString(R.string.language_name_spanish)).thenReturn("Spanish")

        val localesEnglish = android.os.LocaleList(Locale.ENGLISH)
        `when`(configuration.locales).thenReturn(localesEnglish)
        `when`(resources.configuration).thenReturn(configuration)
        assertEquals(0, DashboardResourcesMediaUtils.resolveDefaultLanguageIndex(context, resources, options))

        val localesFrench = android.os.LocaleList(Locale.FRENCH)
        `when`(configuration.locales).thenReturn(localesFrench)
        assertEquals(1, DashboardResourcesMediaUtils.resolveDefaultLanguageIndex(context, resources, options))

        val localesSpanish = android.os.LocaleList(Locale.forLanguageTag("es"))
        `when`(configuration.locales).thenReturn(localesSpanish)
        assertEquals(2, DashboardResourcesMediaUtils.resolveDefaultLanguageIndex(context, resources, options))

        val localesGerman = android.os.LocaleList(Locale.GERMAN)
        `when`(configuration.locales).thenReturn(localesGerman)
        assertEquals(0, DashboardResourcesMediaUtils.resolveDefaultLanguageIndex(context, resources, options))

        val optionsNoEnglish = listOf("French", "Spanish")
        assertEquals(0, DashboardResourcesMediaUtils.resolveDefaultLanguageIndex(context, resources, optionsNoEnglish))
    }
}