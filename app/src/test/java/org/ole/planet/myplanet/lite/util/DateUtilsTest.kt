package org.ole.planet.myplanet.lite.util

import org.junit.Assert.assertEquals
import org.junit.Test

class DateUtilsTest {

    private val fallbackString = "Not Available"

    @Test
    fun testFormatBirthDate_NullOrBlank_ReturnsFallback() {
        assertEquals(fallbackString, DateUtils.formatBirthDate(null, fallbackString))
        assertEquals(fallbackString, DateUtils.formatBirthDate("", fallbackString))
        assertEquals(fallbackString, DateUtils.formatBirthDate("   ", fallbackString))
    }

    @Test
    fun testFormatBirthDate_ValidISO8601WithMillis_ReturnsShortDate() {
        val input = "1990-05-15T14:30:00.000Z"
        val expected = "1990-05-15"
        assertEquals(expected, DateUtils.formatBirthDate(input, fallbackString))
    }

    @Test
    fun testFormatBirthDate_ValidISO8601WithoutMillis_ReturnsShortDate() {
        val input = "1985-11-20T08:15:00Z"
        val expected = "1985-11-20"
        assertEquals(expected, DateUtils.formatBirthDate(input, fallbackString))
    }

    @Test
    fun testFormatBirthDate_ValidShortDate_ReturnsSame() {
        val input = "2000-01-01"
        val expected = "2000-01-01"
        assertEquals(expected, DateUtils.formatBirthDate(input, fallbackString))
    }

    @Test
    fun testFormatBirthDate_InvalidDate_ReturnsOriginal() {
        val input = "Invalid-Date-String"
        assertEquals(input, DateUtils.formatBirthDate(input, fallbackString))
    }
}
