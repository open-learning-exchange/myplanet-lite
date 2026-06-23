package org.ole.planet.myplanet.lite.util

import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.MaterialDatePicker

object BirthDateConstraints {
    fun calendarConstraints(): CalendarConstraints {
        val today = MaterialDatePicker.todayInUtcMilliseconds()
        return CalendarConstraints.Builder()
            .setEnd(today)
            .setValidator(DateValidatorPointBackward.now())
            .build()
    }

    fun coerceSelection(selection: Long?): Long {
        val today = MaterialDatePicker.todayInUtcMilliseconds()
        return selection?.coerceAtMost(today) ?: today
    }

    fun isFuture(selection: Long): Boolean {
        return selection > MaterialDatePicker.todayInUtcMilliseconds()
    }
}
