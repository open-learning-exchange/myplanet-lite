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
    fun `parseBirthDateToMillis returns null for null or blank input`() {
        assertEquals(null, DateUtils.parseBirthDateToMillis(null))
        assertEquals(null, DateUtils.parseBirthDateToMillis(""))
        assertEquals(null, DateUtils.parseBirthDateToMillis("   "))
    }

    @Test
    fun `parseBirthDateToMillis parses valid ISO8601 with millis (Pre-O)`() {
        DateUtils.sdkInt = Build.VERSION_CODES.N
        val input = "1990-05-15T14:30:00.000Z"
        val expected = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.parse(input)?.time
        assertEquals(expected, DateUtils.parseBirthDateToMillis(input))
    }

    @Test
    fun `parseBirthDateToMillis parses valid ISO8601 with millis (O and above)`() {
        DateUtils.sdkInt = Build.VERSION_CODES.O
        val input = "1990-05-15T14:30:00.000Z"
        val expected = java.time.Instant.parse(input).toEpochMilli()
        assertEquals(expected, DateUtils.parseBirthDateToMillis(input))
    }

    @Test
    fun `parseBirthDateToMillis parses valid short date (Pre-O)`() {
        DateUtils.sdkInt = Build.VERSION_CODES.N
        val input = "2000-01-01"
        val expected = SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.parse(input)?.time
        assertEquals(expected, DateUtils.parseBirthDateToMillis(input))
    }

    @Test
    fun `parseBirthDateToMillis parses valid short date (O and above)`() {
        DateUtils.sdkInt = Build.VERSION_CODES.O
        val input = "2000-01-01"
        val expected = java.time.LocalDate.parse(input).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals(expected, DateUtils.parseBirthDateToMillis(input))
    }

    @Test
    fun `extractBirthYearFromIso returns null for null or blank input`() {
        assertEquals(null, DateUtils.extractBirthYearFromIso(null))
        assertEquals(null, DateUtils.extractBirthYearFromIso(""))
        assertEquals(null, DateUtils.extractBirthYearFromIso("   "))
    }

    @Test
    fun `extractBirthYearFromIso extracts year from valid ISO8601 (Pre-O)`() {
        DateUtils.sdkInt = Build.VERSION_CODES.N
        val input = "1990-05-15T14:30:00.000Z"
        assertEquals("1990", DateUtils.extractBirthYearFromIso(input))
    }

    @Test
    fun `extractBirthYearFromIso extracts year from valid ISO8601 (O and above)`() {
        DateUtils.sdkInt = Build.VERSION_CODES.O
        val input = "1990-05-15T14:30:00.000Z"
        assertEquals("1990", DateUtils.extractBirthYearFromIso(input))
    }

    @Test
    fun `calculateAgeFromIso returns null for null or blank input`() {
        assertEquals(null, DateUtils.calculateAgeFromIso(null))
        assertEquals(null, DateUtils.calculateAgeFromIso(""))
        assertEquals(null, DateUtils.calculateAgeFromIso("   "))
    }

    @Test
    fun `calculateAgeFromIso calculates correct age from valid ISO8601 (Pre-O)`() {
        DateUtils.sdkInt = Build.VERSION_CODES.N
        val input = "1990-05-15T14:30:00.000Z"

        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        val date = format.parse(input)
        val birthCalendar = java.util.Calendar.getInstance().apply { time = date }
        val today = java.util.Calendar.getInstance()
        var expectedAge = today.get(java.util.Calendar.YEAR) - birthCalendar.get(java.util.Calendar.YEAR)
        if (today.get(java.util.Calendar.DAY_OF_YEAR) < birthCalendar.get(java.util.Calendar.DAY_OF_YEAR)) {
            expectedAge -= 1
        }

        assertEquals(expectedAge.toString(), DateUtils.calculateAgeFromIso(input))
    }

    @Test
    fun `calculateAgeFromIso calculates correct age from valid ISO8601 (O and above)`() {
        DateUtils.sdkInt = Build.VERSION_CODES.O
        val input = "1990-05-15T14:30:00.000Z"

        val birthDate = java.time.Instant.parse(input).atZone(java.time.ZoneOffset.UTC).toLocalDate()
        val now = java.time.Instant.now().atZone(java.time.ZoneOffset.UTC).toLocalDate()
        val expectedAge = java.time.Period.between(birthDate, now).years

        assertEquals(expectedAge.toString(), DateUtils.calculateAgeFromIso(input))
    }

}
