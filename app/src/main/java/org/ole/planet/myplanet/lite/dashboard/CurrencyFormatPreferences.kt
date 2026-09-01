package org.ole.planet.myplanet.lite.dashboard

import android.content.Context
import androidx.core.content.edit
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormatSymbols
import java.util.Currency
import java.util.Locale
import org.ole.planet.myplanet.lite.util.SecurePreferencesProvider

object CurrencyFormatPreferences {
    private const val KEY_CODE = "finance_currency_code"
    private const val KEY_SYMBOL = "finance_currency_symbol"
    private const val KEY_GROUPING = "finance_currency_grouping_separator"
    private const val KEY_DECIMAL = "finance_currency_decimal_separator"
    private const val SERVER_DEFAULT_CURRENCY_CODE = "GTQ"
    private const val SERVER_DEFAULT_CURRENCY_SYMBOL = "Q"
    private val symbolCache = mutableMapOf<String, String>()

    data class Settings(
        val currencyCode: String,
        val currencySymbol: String,
        val groupingSeparator: Char,
        val decimalSeparator: Char,
    )

    fun load(context: Context): Settings {
        val preferences = SecurePreferencesProvider.getServerPreferences(context.applicationContext)
        val symbols = DecimalFormatSymbols.getInstance(Locale.getDefault())
        val currencyCode = preferences.getString(KEY_CODE, null)
            ?.takeIf { runCatching { Currency.getInstance(it) }.isSuccess }
            ?: SERVER_DEFAULT_CURRENCY_CODE
        val preferredSymbol = preferredCurrencySymbol(Currency.getInstance(currencyCode))
        val savedSymbol = preferences.getString(KEY_SYMBOL, null)
        val currencySymbol = savedSymbol
            ?.takeIf { it.isNotBlank() && !it.equals(currencyCode, ignoreCase = true) && it.length <= preferredSymbol.length }
            ?: preferredSymbol
        return Settings(
            currencyCode,
            currencySymbol,
            preferences.getString(KEY_GROUPING, null)?.firstOrNull() ?: symbols.groupingSeparator,
            preferences.getString(KEY_DECIMAL, null)?.firstOrNull() ?: symbols.decimalSeparator,
        )
    }

    fun save(context: Context, settings: Settings) {
        require(settings.groupingSeparator != settings.decimalSeparator)
        Currency.getInstance(settings.currencyCode)
        SecurePreferencesProvider.getServerPreferences(context.applicationContext).edit {
            putString(KEY_CODE, settings.currencyCode)
            putString(KEY_SYMBOL, settings.currencySymbol)
            putString(KEY_GROUPING, settings.groupingSeparator.toString())
            putString(KEY_DECIMAL, settings.decimalSeparator.toString())
        }
    }

    fun format(context: Context, amount: Double): String {
        return format(load(context), amount)
    }

    fun format(settings: Settings, amount: Double): String {
        Currency.getInstance(settings.currencyCode)
        val rounded = BigDecimal.valueOf(amount).abs().setScale(2, RoundingMode.HALF_UP).toPlainString()
        val parts = rounded.split('.')
        val groupedInteger = parts[0].reversed().chunked(3).joinToString(settings.groupingSeparator.toString()).reversed()
        val sign = if (amount < 0) "-" else ""
        return "$sign${settings.currencySymbol}$groupedInteger${settings.decimalSeparator}${parts[1]}"
    }

    fun availableCurrencies(locale: Locale = Locale.getDefault()): List<CurrencyOption> =
        Currency.getAvailableCurrencies().map { currency ->
            CurrencyOption(currency.currencyCode, preferredCurrencySymbol(currency), currency.getDisplayName(locale))
        }.sortedWith(compareBy<CurrencyOption> { it.displayName }.thenBy { it.code })

    internal fun preferredCurrencySymbol(currency: Currency): String = synchronized(symbolCache) {
        symbolCache.getOrPut(currency.currencyCode) {
            if (currency.currencyCode == SERVER_DEFAULT_CURRENCY_CODE) return@getOrPut SERVER_DEFAULT_CURRENCY_SYMBOL
            Locale.getAvailableLocales().asSequence()
                .filter { it.country.isNotBlank() }
                .filter { locale -> runCatching { Currency.getInstance(locale) == currency }.getOrDefault(false) }
                .map { locale -> currency.getSymbol(locale) }
                .filter { symbol -> symbol.isNotBlank() && !symbol.equals(currency.currencyCode, ignoreCase = true) }
                .minWithOrNull(compareBy<String> { it.length }.thenBy { it })
                ?: currency.getSymbol(Locale.getDefault())
        }
    }
}

data class CurrencyOption(val code: String, val symbol: String, val displayName: String) {
    override fun toString(): String = "$displayName ($code · $symbol)"
}
