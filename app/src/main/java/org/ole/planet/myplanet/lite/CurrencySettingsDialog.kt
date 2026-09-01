package org.ole.planet.myplanet.lite

import android.app.Activity
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import org.ole.planet.myplanet.lite.dashboard.CurrencyFormatPreferences

object CurrencySettingsDialog {
    private val groupingSeparators = listOf(',', '.', ' ', '\'')
    private val decimalSeparators = listOf('.', ',')

    fun show(activity: Activity, onSaved: (() -> Unit)? = null) {
        val content = activity.layoutInflater.inflate(R.layout.dialog_currency_format, null)
        val currencySpinner = content.findViewById<Spinner>(R.id.currencySettingsCurrency)
        val groupingSpinner = content.findViewById<Spinner>(R.id.currencySettingsGrouping)
        val decimalSpinner = content.findViewById<Spinner>(R.id.currencySettingsDecimal)
        val preview = content.findViewById<TextView>(R.id.currencySettingsPreview)
        val currencies = CurrencyFormatPreferences.availableCurrencies()
        val current = CurrencyFormatPreferences.load(activity)
        currencySpinner.adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, currencies)
        groupingSpinner.adapter = ArrayAdapter(
            activity, android.R.layout.simple_spinner_dropdown_item,
            groupingSeparators.map { separatorLabel(activity, it) },
        )
        decimalSpinner.adapter = ArrayAdapter(
            activity, android.R.layout.simple_spinner_dropdown_item,
            decimalSeparators.map { separatorLabel(activity, it) },
        )
        currencySpinner.setSelection(currencies.indexOfFirst { it.code == current.currencyCode }.coerceAtLeast(0))
        groupingSpinner.setSelection(groupingSeparators.indexOf(current.groupingSeparator).coerceAtLeast(0))
        decimalSpinner.setSelection(decimalSeparators.indexOf(current.decimalSeparator).coerceAtLeast(0))
        fun selectedSettings() = CurrencyFormatPreferences.Settings(
            currencies[currencySpinner.selectedItemPosition].code,
            currencies[currencySpinner.selectedItemPosition].symbol,
            groupingSeparators[groupingSpinner.selectedItemPosition],
            decimalSeparators[decimalSpinner.selectedItemPosition],
        )
        fun updatePreview() {
            preview.text = if (selectedSettings().groupingSeparator == selectedSettings().decimalSeparator) {
                activity.getString(R.string.dashboard_settings_currency_separators_different)
            } else {
                activity.getString(
                    R.string.dashboard_settings_currency_preview,
                    CurrencyFormatPreferences.format(selectedSettings(), 1234567.89),
                )
            }
        }
        val listener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) = updatePreview()
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
        currencySpinner.onItemSelectedListener = listener
        groupingSpinner.onItemSelectedListener = listener
        decimalSpinner.onItemSelectedListener = listener
        val dialog = AlertDialog.Builder(activity).setTitle(R.string.dashboard_settings_currency_title)
            .setView(content).setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.dashboard_enterprise_tasks_save, null).create()
        dialog.setOnShowListener {
            updatePreview()
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val selected = selectedSettings()
                if (selected.groupingSeparator == selected.decimalSeparator) {
                    preview.text = activity.getString(R.string.dashboard_settings_currency_separators_different)
                } else {
                    CurrencyFormatPreferences.save(activity, selected)
                    onSaved?.invoke()
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private fun separatorLabel(activity: Activity, separator: Char): String = when (separator) {
        '.' -> activity.getString(R.string.dashboard_settings_currency_separator_dot)
        ',' -> activity.getString(R.string.dashboard_settings_currency_separator_comma)
        ' ' -> activity.getString(R.string.dashboard_settings_currency_separator_space)
        else -> activity.getString(R.string.dashboard_settings_currency_separator_apostrophe)
    }
}
