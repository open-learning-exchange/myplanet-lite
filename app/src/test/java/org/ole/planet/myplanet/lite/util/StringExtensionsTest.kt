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
}
