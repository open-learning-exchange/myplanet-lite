package org.ole.planet.myplanet.lite.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StringExtensionsTest {

    @Test
    fun `nullIfBlank returns null when string is null`() {
        val nullString: String? = null
        assertNull(nullString.nullIfBlank())
    }

    @Test
    fun `nullIfBlank returns null when string is empty`() {
        assertEquals(null, "".nullIfBlank())
    }

    @Test
    fun `nullIfBlank returns null when string contains only spaces`() {
        assertEquals(null, " ".nullIfBlank())
    }

    @Test
    fun `nullIfBlank returns null when string contains only whitespace characters`() {
        assertEquals(null, "  \n\t ".nullIfBlank())
    }

    @Test
    fun `nullIfBlank returns string when string is not blank`() {
        assertEquals("hello", "hello".nullIfBlank())
    }

    @Test
    fun `nullIfBlank returns string when string has surrounding whitespace`() {
        assertEquals(" hello ", " hello ".nullIfBlank())
    }

    @Test
    fun `nullIfBlank returns null when string contains only tabs`() {
        assertEquals(null, "\t\t".nullIfBlank())
    }

    @Test
    fun `nullIfBlank returns null when string contains only newlines`() {
        assertEquals(null, "\n\n".nullIfBlank())
    }

    @Test
    fun `nullIfBlank returns null when string contains only carriage returns`() {
        assertEquals(null, "\r\r".nullIfBlank())
    }

    @Test
    fun `nullIfBlank returns null when string contains non-breaking spaces`() {
        assertEquals(null, "\u00A0".nullIfBlank())
    }

    @Test
    fun `nullIfBlank returns string when string contains whitespace in the middle`() {
        assertEquals("a b", "a b".nullIfBlank())
    }
}
