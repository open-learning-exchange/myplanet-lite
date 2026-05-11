package org.ole.planet.myplanet.lite.util

import android.os.Build
import java.text.SimpleDateFormat
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Test

class DateUtilsTest {

    private val fallbackString = "Not Available"

    @Test
    fun `formatBirthDate returns fallback when input is null or blank`() {
        assertEquals(fallbackString, DateUtils.formatBirthDate(null, fallbackString))
        assertEquals(fallbackString, DateUtils.formatBirthDate("", fallbackString))
        assertEquals(fallbackString, DateUtils.formatBirthDate("   ", fallbackString))
    }

    @Test
    fun `formatBirthDate returns short date for valid ISO8601 with millis (Pre-O)`() {
        DateUtils.sdkInt = Build.VERSION_CODES.N
        val input = "1990-05-15T14:30:00.000Z"
        val expected = "1990-05-15"
        assertEquals(expected, DateUtils.formatBirthDate(input, fallbackString))
    }

    @Test
    fun `formatBirthDate returns short date for valid ISO8601 with millis (O and above)`() {
        DateUtils.sdkInt = Build.VERSION_CODES.O
        val input = "1990-05-15T14:30:00.000Z"
        val expected = "1990-05-15"
        assertEquals(expected, DateUtils.formatBirthDate(input, fallbackString))
    }

    @Test
    fun `formatBirthDate returns short date for valid ISO8601 without millis (Pre-O)`() {
        DateUtils.sdkInt = Build.VERSION_CODES.N
        val input = "1985-11-20T08:15:00Z"
        val expected = "1985-11-20"
        assertEquals(expected, DateUtils.formatBirthDate(input, fallbackString))
    }

    @Test
    fun `formatBirthDate returns short date for valid ISO8601 without millis (O and above)`() {
        DateUtils.sdkInt = Build.VERSION_CODES.O
        val input = "1985-11-20T08:15:00Z"
        val expected = "1985-11-20"
        assertEquals(expected, DateUtils.formatBirthDate(input, fallbackString))
    }

    @Test
    fun `formatBirthDate returns original short date when already in short date format (Pre-O)`() {
        DateUtils.sdkInt = Build.VERSION_CODES.N
        val input = "2000-01-01"
        val expected = "2000-01-01"
        assertEquals(expected, DateUtils.formatBirthDate(input, fallbackString))
    }

    @Test
    fun `formatBirthDate returns original short date when already in short date format (O and above)`() {
        DateUtils.sdkInt = Build.VERSION_CODES.O
        val input = "2000-01-01"
        val expected = "2000-01-01"
        assertEquals(expected, DateUtils.formatBirthDate(input, fallbackString))
    }

    @Test
    fun `formatBirthDate returns fallback for invalid date format (Pre-O)`() {
        DateUtils.sdkInt = Build.VERSION_CODES.N
        val input = "Invalid-Date-String"
        assertEquals(fallbackString, DateUtils.formatBirthDate(input, fallbackString))
    }

    @Test
    fun `formatBirthDate returns fallback for invalid date format (O and above)`() {
        DateUtils.sdkInt = Build.VERSION_CODES.O
        val input = "Invalid-Date-String"
        assertEquals(fallbackString, DateUtils.formatBirthDate(input, fallbackString))
    }

    @Test
    fun `toDisplayDate returns formatted date for valid timestamp`() {
        val timestamp = 1672531200000L // 2023-01-01 00:00:00 UTC
        val expected = SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(Date(timestamp))
        assertEquals(expected, DateUtils.toDisplayDate(timestamp))
    }

    @Test
    fun `toDisplayDate returns fallback for invalid timestamp`() {
        assertEquals("-", DateUtils.toDisplayDate(null))
        assertEquals("-", DateUtils.toDisplayDate(0L))
        assertEquals("-", DateUtils.toDisplayDate(-1L))
    }
}
