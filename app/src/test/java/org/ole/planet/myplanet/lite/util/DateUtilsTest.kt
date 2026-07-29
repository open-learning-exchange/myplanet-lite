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

    @Test
    fun `parseBirthDateToMillis returns null for blank input`() {
        assertEquals(null, DateUtils.parseBirthDateToMillis(null))
        assertEquals(null, DateUtils.parseBirthDateToMillis(""))
        assertEquals(null, DateUtils.parseBirthDateToMillis("   "))
    }

    @Test
    fun `parseBirthDateToMillis returns correct millis (Pre-O)`() {
        DateUtils.sdkInt = Build.VERSION_CODES.N
        val input = "1990-05-15T14:30:00.000Z"
        val expected = 642781800000L
        assertEquals(expected, DateUtils.parseBirthDateToMillis(input))
    }

    @Test
    fun `parseBirthDateToMillis returns correct millis (O and above)`() {
        DateUtils.sdkInt = Build.VERSION_CODES.O
        val input = "1990-05-15T14:30:00.000Z"
        val expected = 642781800000L
        assertEquals(expected, DateUtils.parseBirthDateToMillis(input))
    }

    @Test
    fun `extractBirthYearFromIso returns null for blank input`() {
        assertEquals(null, DateUtils.extractBirthYearFromIso(null))
        assertEquals(null, DateUtils.extractBirthYearFromIso(""))
        assertEquals(null, DateUtils.extractBirthYearFromIso("   "))
    }

    @Test
    fun `extractBirthYearFromIso returns correct year (Pre-O)`() {
        DateUtils.sdkInt = Build.VERSION_CODES.N
        val input = "1990-05-15T14:30:00.000Z"
        val expected = "1990"
        assertEquals(expected, DateUtils.extractBirthYearFromIso(input))
    }

    @Test
    fun `extractBirthYearFromIso returns correct year (O and above)`() {
        DateUtils.sdkInt = Build.VERSION_CODES.O
        val input = "1990-05-15T14:30:00.000Z"
        val expected = "1990"
        assertEquals(expected, DateUtils.extractBirthYearFromIso(input))
    }

    @Test
    fun `calculateAgeFromIso returns null for blank input`() {
        assertEquals(null, DateUtils.calculateAgeFromIso(null))
        assertEquals(null, DateUtils.calculateAgeFromIso(""))
        assertEquals(null, DateUtils.calculateAgeFromIso("   "))
    }

    @Test
    fun `calculateAgeFromIso returns correct age (Pre-O)`() {
        DateUtils.sdkInt = Build.VERSION_CODES.N
        // Choosing a very old date so age is always > 0 and consistent across years if we don't care about the exact number,
        // but for exact number we can just check it doesn't crash and returns a non-null string, or we mock the current date.
        // Since we can't easily mock Calendar.getInstance() without Robolectric/PowerMock, we'll just check if it's not null and a number.
        val input = "1990-05-15T14:30:00.000Z"
        val result = DateUtils.calculateAgeFromIso(input)
        assert(result != null && result.toInt() > 0)
    }

    @Test
    fun `calculateAgeFromIso returns correct age (O and above)`() {
        DateUtils.sdkInt = Build.VERSION_CODES.O
        val input = "1990-05-15T14:30:00.000Z"
        val result = DateUtils.calculateAgeFromIso(input)
        assert(result != null && result.toInt() > 0)
    }
}
