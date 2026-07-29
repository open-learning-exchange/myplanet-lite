package org.ole.planet.myplanet.lite.util

import com.google.android.material.datepicker.MaterialDatePicker
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BirthDateConstraintsTest {

    private val mockedToday = 1000000000000L

    @Before
    fun setup() {
        mockkStatic(MaterialDatePicker::class)
        every { MaterialDatePicker.todayInUtcMilliseconds() } returns mockedToday
    }

    @After
    fun teardown() {
        unmockkStatic(MaterialDatePicker::class)
    }

    @Test
    fun testCalendarConstraints() {
        val constraints = BirthDateConstraints.calendarConstraints()
        assertNotNull(constraints)
        assertNotNull(constraints.dateValidator)
    }

    @Test
    fun testCoerceSelection() {
        assertEquals(mockedToday, BirthDateConstraints.coerceSelection(null))

        val pastDate = mockedToday - 100000L
        assertEquals(pastDate, BirthDateConstraints.coerceSelection(pastDate))

        val futureDate = mockedToday + 100000L
        assertEquals(mockedToday, BirthDateConstraints.coerceSelection(futureDate))

        assertEquals(mockedToday, BirthDateConstraints.coerceSelection(mockedToday))
    }

    @Test
    fun testIsFuture() {
        val pastDate = mockedToday - 100000L
        assertFalse(BirthDateConstraints.isFuture(pastDate))

        val futureDate = mockedToday + 100000L
        assertTrue(BirthDateConstraints.isFuture(futureDate))

        assertFalse(BirthDateConstraints.isFuture(mockedToday))
    }
}
