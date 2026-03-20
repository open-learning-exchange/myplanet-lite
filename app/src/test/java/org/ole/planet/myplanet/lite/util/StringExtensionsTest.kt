package org.ole.planet.myplanet.lite.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StringExtensionsTest {

    @Test
    fun testNullIfBlank() {
        val nullString: String? = null
        assertNull(nullString.nullIfBlank())

        assertEquals(null, "".nullIfBlank())
        assertEquals(null, " ".nullIfBlank())
        assertEquals(null, "  \n\t ".nullIfBlank())

        assertEquals("hello", "hello".nullIfBlank())
        assertEquals(" hello ", " hello ".nullIfBlank())
    }
}
