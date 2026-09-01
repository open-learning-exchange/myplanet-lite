package org.ole.planet.myplanet.lite.dashboard

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Currency

class CurrencyFormatPreferencesTest {
    @Test
    fun `formats server currency with comma thousands and dot decimals`() {
        val settings = CurrencyFormatPreferences.Settings("GTQ", "Q", ',', '.')

        assertEquals("Q1,234,567.89", CurrencyFormatPreferences.format(settings, 1234567.89))
    }

    @Test
    fun `formats server currency with dot thousands and comma decimals`() {
        val settings = CurrencyFormatPreferences.Settings("GTQ", "Q", '.', ',')

        assertEquals("Q1.234.567,89", CurrencyFormatPreferences.format(settings, 1234567.89))
    }

    @Test
    fun `places negative sign before server currency symbol`() {
        val settings = CurrencyFormatPreferences.Settings("GTQ", "Q", ',', '.')

        assertEquals("-Q1,234.50", CurrencyFormatPreferences.format(settings, -1234.5))
    }

    @Test
    fun `uses visible symbols instead of currency codes`() {
        assertEquals("Q", CurrencyFormatPreferences.preferredCurrencySymbol(Currency.getInstance("GTQ")))
        assertEquals("\$", CurrencyFormatPreferences.preferredCurrencySymbol(Currency.getInstance("USD")))
    }
}
