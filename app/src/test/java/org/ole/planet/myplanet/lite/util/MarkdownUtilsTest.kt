package org.ole.planet.myplanet.lite.util

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class MarkdownUtilsTest {

    private val mockContext: Context = mock(Context::class.java)

    @Test
    fun normalizeMarkdownImageSource_plainSource_returnsTrimmed() {
        assertEquals("image.png", MarkdownUtils.normalizeMarkdownImageSource(" image.png "))
    }

    @Test
    fun normalizeMarkdownImageSource_withAngleBrackets_removesBrackets() {
        assertEquals("image.png", MarkdownUtils.normalizeMarkdownImageSource("<image.png>"))
    }

    @Test
    fun normalizeMarkdownImageSource_withSpace_removesSpaceAndSuffix() {
        assertEquals("image.png", MarkdownUtils.normalizeMarkdownImageSource("image.png \"title\""))
    }

    @Test
    fun normalizeMarkdownImageSource_withAngleBracketsAndTitle_handlesCorrectly() {
        assertEquals("<image.png>", MarkdownUtils.normalizeMarkdownImageSource("<image.png> \"title\""))
    }

    @Test
    fun resolveMarkdownSourceUrl_blankSource_returnsNull() {
        assertNull(MarkdownUtils.resolveMarkdownSourceUrl("http://base", "   "))
    }

    @Test
    fun resolveMarkdownSourceUrl_absoluteUrl_returnsAsIs() {
        assertEquals("http://test.com/image.png", MarkdownUtils.resolveMarkdownSourceUrl("http://base", "http://test.com/image.png"))
        assertEquals("https://test.com/image.png", MarkdownUtils.resolveMarkdownSourceUrl("http://base", "https://test.com/image.png"))
        assertEquals("file:///image.png", MarkdownUtils.resolveMarkdownSourceUrl("http://base", "file:///image.png"))
    }

    @Test
    fun resolveMarkdownSourceUrl_blankBase_returnsNull() {
        assertNull(MarkdownUtils.resolveMarkdownSourceUrl("   ", "image.png"))
        assertNull(MarkdownUtils.resolveMarkdownSourceUrl(null, "image.png"))
    }

    @Test
    fun resolveMarkdownSourceUrl_resolvesWithDbPath() {
        assertEquals("http://base/db/image.png", MarkdownUtils.resolveMarkdownSourceUrl("http://base", "image.png"))
        assertEquals("http://base/db/image.png", MarkdownUtils.resolveMarkdownSourceUrl("http://base/", "/image.png"))
        assertEquals("http://base/db/image.png", MarkdownUtils.resolveMarkdownSourceUrl("http://base", "db/image.png"))
        assertEquals("http://base/db/image.png", MarkdownUtils.resolveMarkdownSourceUrl("http://base/", "/db/image.png"))
    }

    @Test
    fun extractImageSourcesFromText_extractsFromMarkdown() {
        val text = "Some text ![alt text](image1.png) and ![another](<image2.png> \"title\")"
        val sources = MarkdownUtils.extractImageSourcesFromText(text)
        assertEquals(listOf("image1.png", "<image2.png>"), sources)
    }

    @Test
    fun extractImageSourcesFromText_extractsFromHtml() {
        val text = "Some text <img src=\"image1.png\"> and <IMG src='image2.png' alt='test'>"
        val sources = MarkdownUtils.extractImageSourcesFromText(text)
        assertEquals(listOf("image1.png", "image2.png"), sources)
    }

    @Test
    fun extractImageSourcesFromText_mixedContent() {
        val text = "![alt](md.png) <img src=\"html.png\">"
        val sources = MarkdownUtils.extractImageSourcesFromText(text)
        assertEquals(listOf("md.png", "html.png"), sources)
    }

    @Test
    fun resolveOfflineMarkdownImages_emptyCourseId_returnsOriginal() {
        val markdown = "![alt](image.png)"
        assertEquals(markdown, MarkdownUtils.resolveOfflineMarkdownImages(mockContext, markdown, "", "http://base"))
        assertEquals(markdown, MarkdownUtils.resolveOfflineMarkdownImages(mockContext, markdown, null, "http://base"))
    }

    @Test
    fun resolveOfflineMarkdownImages_withMarkdownAndHtmlImages_resolvesCorrectly() {
        val markdown = "![alt](image1.png) <img src=\"image2.png\" alt=\"html\">"
        // Without mocking Static, let's see what happens.
        // It returns null natively because context has no files.
        // So fallback should work.
        val result = MarkdownUtils.resolveOfflineMarkdownImages(mockContext, markdown, "course123", "http://base")
        val expected = "![alt](http://base/db/image1.png) ![html](http://base/db/image2.png)"
        assertEquals(expected, result)
    }

    @Test
    fun replaceImagePlaceholder_emptyAltText_returnsReplacement() {
        val source = "Some text ![](image.png) here"
        val replacement = "![new](new.png)"
        val expected = "Some text ![new](new.png) here"
        assertEquals(expected, MarkdownUtils.replaceImagePlaceholder(source, "image.png", replacement))
    }

    @Test
    fun replaceImagePlaceholder_withAltText_insertsAltTextIntoReplacement() {
        val source = "Some text ![alt text](image.png) here"
        val replacement = "![](new.png)"
        val expected = "Some text ![alt text](new.png) here"
        assertEquals(expected, MarkdownUtils.replaceImagePlaceholder(source, "image.png", replacement))
    }

    @Test
    fun replaceImagePlaceholder_noMatch_appendsReplacement() {
        val source = "Some text"
        val replacement = "![new](new.png)"
        val expected = "Some text\n\n![new](new.png)"
        assertEquals(expected, MarkdownUtils.replaceImagePlaceholder(source, "image.png", replacement))

        val sourceEmpty = ""
        val expectedEmpty = "![new](new.png)"
        assertEquals(expectedEmpty, MarkdownUtils.replaceImagePlaceholder(sourceEmpty, "image.png", replacement))
    }

    @Test
    fun applyAltText_emptyAltText_returnsOriginal() {
        val markdown = "![](image.png)"
        assertEquals(markdown, MarkdownUtils.applyAltText(markdown, "   "))
        assertEquals(markdown, MarkdownUtils.applyAltText(markdown, ""))
    }

    @Test
    fun applyAltText_noBrackets_returnsOriginal() {
        val markdown = "image.png"
        assertEquals(markdown, MarkdownUtils.applyAltText(markdown, "alt text"))

        val markdownMissingOpen = "image.png]"
        assertEquals(markdownMissingOpen, MarkdownUtils.applyAltText(markdownMissingOpen, "alt text"))

        val markdownMissingClose = "[image.png"
        assertEquals(markdownMissingClose, MarkdownUtils.applyAltText(markdownMissingClose, "alt text"))

        val markdownWrongOrder = "]image.png["
        assertEquals(markdownWrongOrder, MarkdownUtils.applyAltText(markdownWrongOrder, "alt text"))
    }

    @Test
    fun applyAltText_validBrackets_insertsAltText() {
        val markdown = "![](image.png)"
        val expected = "![myalt](image.png)"
        assertEquals(expected, MarkdownUtils.applyAltText(markdown, "myalt"))

        val markdownWithExisting = "![old](image.png)"
        val expectedExisting = "![myalt](image.png)"
        assertEquals(expectedExisting, MarkdownUtils.applyAltText(markdownWithExisting, "myalt"))
    }
}
