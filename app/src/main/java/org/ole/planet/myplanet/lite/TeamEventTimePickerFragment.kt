package org.ole.planet.myplanet.lite

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.TimePicker
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

internal val eventDurationMinutes = listOf(5, 15, 30, 60, 90, 120, 150, 180, 210, 240, 270, 300)

internal fun minimumEventTime(date: LocalDate, earliest: LocalTime?, now: LocalDateTime = LocalDateTime.now()): LocalTime? {
    if (date < now.toLocalDate()) return null
    val current = if (date == now.toLocalDate()) now.toLocalTime().withSecond(0).withNano(0) else LocalTime.MIN
    return maxOf(current, earliest ?: LocalTime.MIN)
}

class TeamEventTimePickerFragment : DialogFragment() {
    private lateinit var clock: TimePicker
    private var duration = 30
    private lateinit var error: TextView
    private val date get() = LocalDate.parse(requireArguments().getString("date"))
    private val earliest get() = requireArguments().getString("earliest")?.let(LocalTime::parse)
    private var adjustingClock = false

    private fun adjustToMinimum(): Boolean {
        val minimum = if (requireArguments().getBoolean("futureOnly", true)) minimumEventTime(date, earliest) else earliest ?: LocalTime.MIN
        if (minimum == null) {
            error.setText(R.string.calendar_past_date); error.isVisible = true
            return false
        }
        val valid = LocalTime.of(clock.hour, clock.minute) >= minimum
        if (!valid) {
            adjustingClock = true
            clock.hour = minimum.hour; clock.minute = minimum.minute
            adjustingClock = false
        }
        error.setText(if (date == LocalDate.now()) R.string.calendar_past_time else R.string.calendar_invalid_times)
        error.isVisible = !valid
        return valid
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val state = savedInstanceState ?: requireArguments()
        val isStart = requireArguments().getBoolean("isStart")
        duration = state.getInt("duration", 30)
        val content = layoutInflater.inflate(R.layout.dialog_team_event_time, null)
        error = content.findViewById(R.id.eventTimeError)
        clock = content.findViewById<TimePicker>(R.id.eventTimeClock).apply {
            setIs24HourView(false)
            hour = state.getInt("hour", 9)
            minute = state.getInt("minute", 0)
        }
        adjustToMinimum()
        clock.setOnTimeChangedListener { _, _, _ -> if (!adjustingClock) adjustToMinimum() }
        content.findViewById<View>(R.id.eventDurationLabel).isVisible = isStart
        content.findViewById<ChipGroup>(R.id.eventDurationOptions).apply {
            isVisible = isStart
            isSingleSelection = true
            isSelectionRequired = true
            eventDurationMinutes.forEach { minutes ->
                addView(Chip(context).apply {
                    id = View.generateViewId()
                    tag = minutes
                    text = when {
                        minutes < 60 -> getString(R.string.calendar_duration_minutes, minutes)
                        minutes % 60 == 0 -> getString(R.string.calendar_duration_hours, minutes / 60)
                        else -> getString(R.string.calendar_duration_hours_minutes, minutes / 60, minutes % 60)
                    }
                    isCheckable = true
                    isChecked = minutes == duration
                    setOnCheckedChangeListener { _, checked -> if (checked) duration = minutes }
                })
            }
        }
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (isStart) R.string.calendar_start_time else R.string.calendar_end_time)
            .setView(content).setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok, null).create()
    }

    override fun onStart() {
        super.onStart()
        adjustToMinimum()
        (requireDialog() as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (adjustToMinimum()) {
                parentFragmentManager.setFragmentResult(RESULT_KEY, Bundle().apply {
                    putBoolean("isStart", requireArguments().getBoolean("isStart"))
                    putInt("hour", clock.hour); putInt("minute", clock.minute)
                    putInt("duration", duration)
                })
                dismiss()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("hour", clock.hour); outState.putInt("minute", clock.minute)
        outState.putInt("duration", duration)
    }

    companion object {
        const val RESULT_KEY = "team_event_time"
        fun newInstance(isStart: Boolean, time: LocalTime, duration: Int, date: LocalDate = LocalDate.now(), earliest: LocalTime? = null, futureOnly: Boolean = true) = TeamEventTimePickerFragment().apply {
            arguments = Bundle().apply {
                putBoolean("futureOnly", futureOnly)
                putString("date", date.toString()); putString("earliest", earliest?.toString())
                putBoolean("isStart", isStart)
                putInt("hour", time.hour); putInt("minute", time.minute)
                putInt("duration", duration)
            }
        }
    }
}
