package org.ole.planet.myplanet.lite

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import android.net.Uri
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import android.media.MediaMetadataRetriever
import androidx.test.core.app.ApplicationProvider
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowMediaMetadataRetriever
import org.robolectric.shadows.util.DataSource

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
        assertEquals("My_Resource", DashboardResourcesMediaUtils.sanitizeResourceName("My Resource!"))
    }

    @Test
    fun sanitizeResourceName_removesSpecialCharacters() {
        assertEquals("file_name", DashboardResourcesMediaUtils.sanitizeResourceName("file!@#$%^&*()name"))
        assertEquals("test-name_123", DashboardResourcesMediaUtils.sanitizeResourceName("test-name@123"))
    }

    @Test
    fun sanitizeResourceName_collapsesMultipleUnderscores() {
        assertEquals(
            "file_name---with_multiple_underscores",
            DashboardResourcesMediaUtils.sanitizeResourceName("file___name---with___multiple___underscores")
        )
        assertEquals("a_b_c", DashboardResourcesMediaUtils.sanitizeResourceName("a____b!!!!c"))
    }

    @Test
    fun sanitizeResourceName_removesLeadingAndTrailingUnderscores() {
        assertEquals("file_name", DashboardResourcesMediaUtils.sanitizeResourceName("___file_name___"))
        assertEquals("trimmed", DashboardResourcesMediaUtils.sanitizeResourceName("___trimmed___"))
    }

    @Test
    fun sanitizeResourceName_emptyString_defaultsToResourcePrefix() {
        val result = DashboardResourcesMediaUtils.sanitizeResourceName("")
        assertTrue(result.startsWith("resource_"))
        assertTrue(result.substringAfter("resource_").toLong() > 0)
    }

    @Test
    fun sanitizeResourceName_blankString_defaultsToResourcePrefix() {
        val result = DashboardResourcesMediaUtils.sanitizeResourceName("   ")
        assertTrue(result.startsWith("resource_"))
        assertTrue(result.substringAfter("resource_").toLong() > 0)
    }

    @Test
    fun sanitizeResourceName_onlySpecialCharacters_defaultsToResourcePrefix() {
        assertTrue(DashboardResourcesMediaUtils.sanitizeResourceName("!!!").startsWith("resource_"))
    }

    @Test
    fun applyWebCompatibleResourceDefaults_addsMissingFields() {
        val payload = JSONObject()

        DashboardResourcesMediaUtils.applyWebCompatibleResourceDefaults(payload)

        assertTrue(payload.has("author"))
        assertEquals("", payload.getString("author"))
        assertTrue(payload.has("year"))
        assertEquals("", payload.getString("year"))
        assertTrue(payload.has("publisher"))
        assertEquals("", payload.getString("publisher"))
        assertTrue(payload.has("linkToLicense"))
        assertEquals("", payload.getString("linkToLicense"))
        assertTrue(payload.has("openWith"))
        assertEquals("", payload.getString("openWith"))
        assertTrue(payload.has("resourceFor"))
        assertEquals(0, payload.getJSONArray("resourceFor").length())
        assertTrue(payload.has("medium"))
        assertEquals("", payload.getString("medium"))
        assertTrue(payload.has("resourceType"))
        assertEquals("", payload.getString("resourceType"))
    }

    @Test
    fun applyWebCompatibleResourceDefaults_preservesExistingFields() {
        val payload = JSONObject().apply {
            put("author", "Test Author")
            put("year", "2023")
            put("publisher", "Test Publisher")
            put("linkToLicense", "http://license.com")
            put("openWith", "PDF Viewer")
            put("resourceFor", JSONArray().apply { put("Test Group") })
            put("medium", "digital")
            put("resourceType", "book")
        }

        DashboardResourcesMediaUtils.applyWebCompatibleResourceDefaults(payload)

        assertEquals("Test Author", payload.getString("author"))
        assertEquals("2023", payload.getString("year"))
        assertEquals("Test Publisher", payload.getString("publisher"))
        assertEquals("http://license.com", payload.getString("linkToLicense"))
        assertEquals("PDF Viewer", payload.getString("openWith"))
        assertEquals(1, payload.getJSONArray("resourceFor").length())
        assertEquals("Test Group", payload.getJSONArray("resourceFor").getString(0))
        assertEquals("digital", payload.getString("medium"))
        assertEquals("book", payload.getString("resourceType"))
    }

    @Test
    fun testNormalizeResourceMediaType() {
        assertEquals("image", DashboardResourcesMediaUtils.normalizeResourceMediaType("image/jpeg"))
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
        assertEquals("jpg", DashboardResourcesMediaUtils.extensionForImageMimeType("unknown"))
        assertEquals("jpg", DashboardResourcesMediaUtils.extensionForImageMimeType("unknown/mime"))
    }

    @Test
    fun testAllowedVideoHeights() {
        val cases = listOf(
            1080 to listOf(480, 576, 720, 1080),
            1920 to listOf(480, 576, 720, 1080),
            2160 to listOf(480, 576, 720, 1080),
            720 to listOf(480, 576, 720),
            800 to listOf(480, 576, 720),
            1079 to listOf(480, 576, 720),
            576 to listOf(480, 576),
            600 to listOf(480, 576),
            719 to listOf(480, 576),
            575 to listOf(480),
            480 to listOf(480),
            360 to listOf(480),
            240 to listOf(480),
            0 to listOf(480),
            -10 to listOf(480)
        )

        cases.forEach { (input, expected) ->
            assertEquals("Failed for height $input", expected, DashboardResourcesMediaUtils.allowedVideoHeights(input))
        }
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
        assertEquals("00:00", DashboardResourcesMediaUtils.formatDurationMs(-1000L))
        assertEquals("00:05", DashboardResourcesMediaUtils.formatDurationMs(5000L))
        assertEquals("01:05", DashboardResourcesMediaUtils.formatDurationMs(65000L))
        assertEquals("1:00:00", DashboardResourcesMediaUtils.formatDurationMs(3600000L))
        assertEquals("1:00:05", DashboardResourcesMediaUtils.formatDurationMs(3605000L))
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
                1000000L, 1080, 720, 10000L, 0L, 5000L
            )
        )

        assertEquals(
            65536L,
            DashboardResourcesMediaUtils.estimateVideoUploadSizeBytes(
                1L, 1080, 720, 10000L, 0L, 5000L
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

    @Test
    fun extractWaveform_errorPath_returnsEmptyArray() = runBlocking {
        val context = mock(Context::class.java)
        val uri = mock(Uri::class.java)

        `when`(context.contentResolver).thenThrow(RuntimeException("Simulated error"))

        val result = DashboardResourcesMediaUtils.extractWaveform(context, uri)
        assertEquals(0, result.size)
    }

    @Test
    fun resolveVideoDurationMs_validDuration_returnsDuration() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uri = Uri.parse("content://media/external/video/media/1")
        ShadowMediaMetadataRetriever.addMetadata(
            DataSource.toDataSource(context, uri),
            MediaMetadataRetriever.METADATA_KEY_DURATION,
            "12345"
        )

        val result = DashboardResourcesMediaUtils.resolveVideoDurationMs(context, uri)

        assertEquals(12345L, result)
    }

    @Test
    fun resolveVideoDurationMs_invalidDuration_returnsZero() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uri = Uri.parse("content://media/external/video/media/1")
        ShadowMediaMetadataRetriever.addMetadata(
            DataSource.toDataSource(context, uri),
            MediaMetadataRetriever.METADATA_KEY_DURATION,
            "invalid"
        )

        val result = DashboardResourcesMediaUtils.resolveVideoDurationMs(context, uri)

        assertEquals(0L, result)
    }

    @Test
    fun resolveVideoDurationMs_nullDuration_returnsZero() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val uri = Uri.parse("content://media/external/video/media/1")
        ShadowMediaMetadataRetriever.addMetadata(
            DataSource.toDataSource(context, uri),
            MediaMetadataRetriever.METADATA_KEY_DURATION,
            null
        )

        val result = DashboardResourcesMediaUtils.resolveVideoDurationMs(context, uri)

        assertEquals(0L, result)
    }

    @Test
    fun resolveVideoDurationMs_exception_returnsZero() {
        val context = mock(Context::class.java)
        val uri = Uri.parse("content://media/external/video/media/1")
        `when`(context.contentResolver).thenThrow(RuntimeException("Mocked Context Error"))

        val result = DashboardResourcesMediaUtils.resolveVideoDurationMs(context, uri)

        assertEquals(0L, result)
    }
}
