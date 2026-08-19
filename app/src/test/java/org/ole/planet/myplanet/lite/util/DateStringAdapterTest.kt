package org.ole.planet.myplanet.lite.util

import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class DateStringAdapterTest {
    private lateinit var adapter: DateStringAdapter
    private val originalTimeZone = TimeZone.getDefault()

    @Before
    fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        adapter = DateStringAdapter()
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun `fromJson returns long when input is a Number`() {
        val input = 1672531200000L
        val result = adapter.fromJson(input)
        assertEquals(input, result)
    }

    @Test
    fun `fromJson returns long when input is a valid ISO date string`() {
        val input = "2023-01-01T00:00:00Z"
        val expected = 1672531200000L
        val result = adapter.fromJson(input)
        assertEquals(expected, result)
    }

    @Test
    fun `fromJson returns long when input is a numeric string`() {
        val input = "1672531200000"
        val expected = 1672531200000L
        val result = adapter.fromJson(input)
        assertEquals(expected, result)
    }

    @Test
    fun `fromJson uses fallback toLongOrNull when SimpleDateFormat throws ParseException`() {
        // This input fails the initial SimpleDateFormat parsing but is a valid Long, hitting the catch block.
        val input = "1700000000000"
        val expected = 1700000000000L
        val result = adapter.fromJson(input)
        assertEquals(expected, result)
    }

    @Test
    fun `fromJson returns null when input is an invalid string`() {
        val input = "not-a-date"
        val result = adapter.fromJson(input)
        assertNull(result)
    }

    @Test
    fun `fromJson returns null when SimpleDateFormat throws ParseException and toLongOrNull fails`() {
        // This input throws ParseException during parse, hits the catch block, and fails toLongOrNull.
        val input = "invalid-date-and-not-numeric"
        val result = adapter.fromJson(input)
        assertNull(result)
    }

    @Test
    fun `fromJson returns null when input is null`() {
        val result = adapter.fromJson(null)
        assertNull(result)
    }

    @Test
    fun `fromJson returns null when input is an unsupported type`() {
        val result = adapter.fromJson(true)
        assertNull(result)
    }

    @Test
    fun `toJson returns ISO date string when input is a Long`() {
        val input = 1672531200000L
        val expected = "2023-01-01T00:00:00Z"
        val result = adapter.toJson(input)
        assertEquals(expected, result)
    }

    @Test
    fun `toJson returns null when input is null`() {
        val result = adapter.toJson(null)
        assertNull(result)
    }
}
