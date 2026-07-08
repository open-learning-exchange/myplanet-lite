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
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal fun ProfileActivity.formatBirthDateImpl(raw: String?): String {
    if (raw.isNullOrBlank()) {
        return ""
    }
    val targetPattern = "MMM d, yyyy"

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val instant =
            runCatching { Instant.parse(raw) }.getOrNull()
                ?: runCatching { LocalDate.parse(raw).atStartOfDay(ZoneOffset.UTC).toInstant() }.getOrNull()

        if (instant != null) {
            val zonedDate = instant.atZone(ZoneOffset.UTC).toLocalDate()
            val formatter = DateTimeFormatter.ofPattern(targetPattern, Locale.getDefault())
            zonedDate.format(formatter)
        } else {
            raw
        }
    } else {
        val inputPatterns =
            listOf(
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd",
            )
        val outputFormat =
            SimpleDateFormat(targetPattern, Locale.getDefault()).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }

        inputPatterns.firstNotNullOfOrNull { pattern ->
            runCatching {
                SimpleDateFormat(pattern, Locale.US)
                    .apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }.parse(raw)
            }.getOrNull()?.let { date ->
                outputFormat.format(date)
            }
        } ?: raw
    }
}

internal fun ProfileActivity.showBirthDatePickerImpl(targetView: TextInputEditText) {
    val selection = BirthDateConstraints.coerceSelection(parseBirthDateToMillis(selectedBirthDateIso))
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
    val millis = parseBirthDateToMillis(raw)
    return raw?.takeIf { millis != null && !BirthDateConstraints.isFuture(millis) }
}

private fun ProfileActivity.parseBirthDateToMillis(raw: String?): Long? {
    if (raw.isNullOrBlank()) {
        return null
    }
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull()
            ?: runCatching {
                LocalDate
                    .parse(raw)
                    .atStartOfDay(ZoneOffset.UTC)
                    .toInstant()
                    .toEpochMilli()
            }.getOrNull()
    } else {
        val patterns =
            listOf(
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd",
            )
        patterns.firstNotNullOfOrNull { pattern ->
            runCatching {
                SimpleDateFormat(pattern, Locale.US)
                    .apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }.parse(raw)
                    ?.time
            }.getOrNull()
        }
    }
}

internal fun ProfileActivity.extractBirthYearFromIsoImpl(iso: String?): String? {
    if (iso.isNullOrBlank()) {
        return null
    }
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        runCatching {
            Instant
                .parse(iso)
                .atZone(ZoneOffset.UTC)
                .year
                .toString()
        }.getOrNull()
    } else {
        runCatching {
            val format =
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
            val date = format.parse(iso) ?: return@runCatching null
            val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
            calendar.time = date
            calendar.get(Calendar.YEAR).toString()
        }.getOrNull()
    }
}

internal fun ProfileActivity.calculateAgeFromIsoImpl(iso: String?): String? {
    if (iso.isNullOrBlank()) {
        return null
    }
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        runCatching {
            val birthDate = Instant.parse(iso).atZone(ZoneOffset.UTC).toLocalDate()
            val now = Instant.now().atZone(ZoneOffset.UTC).toLocalDate()
            Period
                .between(birthDate, now)
                .years
                .takeIf { it >= 0 }
                ?.toString()
        }.getOrNull()
    } else {
        runCatching {
            val format =
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
            val date = format.parse(iso) ?: return@runCatching null
            val birthCalendar = Calendar.getInstance().apply { time = date }
            val today = Calendar.getInstance()
            var age = today.get(Calendar.YEAR) - birthCalendar.get(Calendar.YEAR)
            if (today.get(Calendar.DAY_OF_YEAR) < birthCalendar.get(Calendar.DAY_OF_YEAR)) {
                age -= 1
            }
            age.takeIf { it >= 0 }?.toString()
        }.getOrNull()
    }
}
