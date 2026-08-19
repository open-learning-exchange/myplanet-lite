package org.ole.planet.myplanet.lite.dashboard

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceMarkdownFormatterTest {

    private val placeholder = "text"

    @Test
    fun `getHeadingReplacement calculates correct headings`() {
        assertEquals("# ", VoiceMarkdownFormatter.getHeadingReplacement(0))
        assertEquals("## ", VoiceMarkdownFormatter.getHeadingReplacement(1))
        assertEquals("### ", VoiceMarkdownFormatter.getHeadingReplacement(2))
        assertEquals("#### ", VoiceMarkdownFormatter.getHeadingReplacement(3))
        assertEquals("##### ", VoiceMarkdownFormatter.getHeadingReplacement(4))
        assertEquals("###### ", VoiceMarkdownFormatter.getHeadingReplacement(5))
        assertEquals("# ", VoiceMarkdownFormatter.getHeadingReplacement(6))
        assertEquals("## ", VoiceMarkdownFormatter.getHeadingReplacement(7))
    }

    @Test
    fun `formatBullet with empty selection inserts empty bullet`() {
        assertEquals("- ", VoiceMarkdownFormatter.formatBullet("", placeholder))
        assertEquals("- ", VoiceMarkdownFormatter.formatBullet("   ", placeholder))
    }

    @Test
    fun `formatBullet wraps multiline text correctly`() {
        val input = "line1\nline2"
        val expected = "- line1\n- line2"
        assertEquals(expected, VoiceMarkdownFormatter.formatBullet(input, placeholder))
    }

    @Test
    fun `formatBullet preserves leading indentation`() {
        val input = "  line1\n    line2"
        val expected = "  - line1\n    - line2"
        assertEquals(expected, VoiceMarkdownFormatter.formatBullet(input, placeholder))
    }

    @Test
    fun `formatBullet inserts placeholder for blank lines`() {
        val input = "line1\n\nline3"
        val expected = "- line1\n- text\n- line3"
        assertEquals(expected, VoiceMarkdownFormatter.formatBullet(input, placeholder))
    }

    @Test
    fun `formatNumberedList with empty selection inserts empty number`() {
        assertEquals("1. ", VoiceMarkdownFormatter.formatNumberedList("", placeholder))
        assertEquals("1. ", VoiceMarkdownFormatter.formatNumberedList("  ", placeholder))
    }

    @Test
    fun `formatNumberedList increments numbers for multiline text`() {
        val input = "apple\nbanana\ncherry"
        val expected = "1. apple\n2. banana\n3. cherry"
        assertEquals(expected, VoiceMarkdownFormatter.formatNumberedList(input, placeholder))
    }

    @Test
    fun `formatNumberedList preserves leading indentation`() {
        val input = "  apple\n    banana"
        val expected = "  1. apple\n    2. banana"
        assertEquals(expected, VoiceMarkdownFormatter.formatNumberedList(input, placeholder))
    }

    @Test
    fun `formatNumberedList inserts placeholder for blank lines`() {
        val input = "apple\n\ncherry"
        val expected = "1. apple\n2. text\n3. cherry"
        assertEquals(expected, VoiceMarkdownFormatter.formatNumberedList(input, placeholder))
    }

    @Test
    fun `formatQuote with empty selection inserts empty quote`() {
        assertEquals("> ", VoiceMarkdownFormatter.formatQuote("", placeholder))
        assertEquals("> ", VoiceMarkdownFormatter.formatQuote("   ", placeholder))
    }

    @Test
    fun `formatQuote wraps multiline text correctly`() {
        val input = "quote1\nquote2"
        val expected = "> quote1\n> quote2"
        assertEquals(expected, VoiceMarkdownFormatter.formatQuote(input, placeholder))
    }

    @Test
    fun `formatQuote preserves leading indentation`() {
        val input = "  quote1\n    quote2"
        val expected = "  > quote1\n    > quote2"
        assertEquals(expected, VoiceMarkdownFormatter.formatQuote(input, placeholder))
    }

    @Test
    fun `formatQuote inserts placeholder for blank lines`() {
        val input = "quote1\n\nquote3"
        val expected = "> quote1\n> text\n> quote3"
        assertEquals(expected, VoiceMarkdownFormatter.formatQuote(input, placeholder))
    }
}
