/*
 * Author: Walfre Lopez Prado
 * Email: loppra@plataformasinformaticas.com
 * Creation date: 2026-07-08
 */

package org.ole.planet.myplanet.lite.profile

import android.os.Build
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.TextInputEditText
import org.ole.planet.myplanet.lite.R
import org.ole.planet.myplanet.lite.util.BirthDateConstraints
import org.ole.planet.myplanet.lite.util.DateUtils
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneOffset
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal fun ProfileActivity.formatBirthDateImpl(raw: String?): String {
    return DateUtils.formatBirthDate(raw, raw ?: "", "MMM d, yyyy")
}

internal fun ProfileActivity.showBirthDatePickerImpl(targetView: TextInputEditText) {
    val selection = BirthDateConstraints.coerceSelection(DateUtils.parseBirthDateToMillis(selectedBirthDateIso))
    val picker =
        MaterialDatePicker.Builder
            .datePicker()
            .setTitleText(R.string.profile_birth_date_picker_title)
            .setSelection(selection)
            .setCalendarConstraints(BirthDateConstraints.calendarConstraints())
            .build()

    picker.addOnPositiveButtonClickListener { selection ->
        if (BirthDateConstraints.isFuture(selection)) {
            return@addOnPositiveButtonClickListener
        }
        val iso =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Instant
                    .ofEpochMilli(selection)
                    .atZone(ZoneOffset.UTC)
                    .toInstant()
                    .toString()
            } else {
                val format =
                    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }
                format.format(Date(selection))
        }
        selectedBirthDateIso = iso
        targetView.setText(formatBirthDateImpl(iso))
    }

    picker.show(supportFragmentManager, "birthDatePicker")
}

internal fun ProfileActivity.normalizeBirthDateIsoImpl(raw: String?): String? {
    val millis = DateUtils.parseBirthDateToMillis(raw)
    return raw?.takeIf { millis != null && !BirthDateConstraints.isFuture(millis) }
}

internal fun ProfileActivity.extractBirthYearFromIsoImpl(iso: String?): String? {
    return DateUtils.extractBirthYearFromIso(iso)
}

internal fun ProfileActivity.calculateAgeFromIsoImpl(iso: String?): String? {
    return DateUtils.calculateAgeFromIso(iso)
}
