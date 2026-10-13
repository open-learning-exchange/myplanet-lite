package org.ole.planet.myplanet.lite

import android.app.Dialog
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputLayout
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import kotlinx.coroutines.launch
import org.ole.planet.myplanet.lite.dashboard.EventValidationException
import org.ole.planet.myplanet.lite.dashboard.TeamEventDraft
import org.ole.planet.myplanet.lite.dashboard.meetupCategoryLabels
import org.ole.planet.myplanet.lite.dashboard.meetupCategories
import org.ole.planet.myplanet.lite.dashboard.meetupWeekdays

class CreateTeamEventDialogFragment : DialogFragment() {
    private val fields = linkedMapOf<String, EditText>()
    private val controls = mutableListOf<View>()
    private lateinit var recurrence: Spinner
    private lateinit var category: Spinner
    private lateinit var allDay: CheckBox
    private lateinit var weekdays: List<CheckBox>
    private lateinit var error: TextView
    private var startDate = LocalDate.now()
    private var endDate: LocalDate? = null
    private var durationMinutes = 30
    private var observerJob: kotlinx.coroutines.Job? = null
    private var appliedRevision: String? = null
    private lateinit var dates: MaterialButton
    private val editingId get() = arguments?.getString("editingId")
    private lateinit var model: TeamEventCreationViewModel

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        model = ViewModelProvider(this)[TeamEventCreationViewModel::class.java]
        val state = savedInstanceState ?: arguments ?: Bundle()
        val initialDate = LocalDate.parse(state.getString("startDate") ?: LocalDate.now().toString())
        startDate = if (editingId == null) maxOf(LocalDate.now(), initialDate) else initialDate
        appliedRevision = state.getString("appliedRevision")
        endDate = state.getString("endDate")?.let(LocalDate::parse)?.takeIf { it >= startDate }
        durationMinutes = state.getInt("duration", 30)
        val context = requireContext()
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (20 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }
        fun field(key: String, label: Int, type: Int = InputType.TYPE_CLASS_TEXT): EditText {
            val input = EditText(context).apply {
                inputType = type
                setText(state.getString(key, if (key == "count") "10" else ""))
            }
            fields[key] = input
            controls += input
            body.addView(TextInputLayout(context).apply {
                hint = getString(label)
                addView(input)
            })
            return input
        }
        field("title", R.string.dashboard_resources_pdf_field_title)
        field("description", R.string.calendar_description_markdown,
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE).minLines = 3
        dates = MaterialButton(context).apply { tag = "eventDates" }
        updateDates()
        dates.setOnClickListener {
            if (childFragmentManager.findFragmentByTag("event_dates") == null) {
                TeamEventDatePickerFragment.newInstance(startDate, endDate, futureOnly = editingId == null).show(childFragmentManager, "event_dates")
            }
        }
        childFragmentManager.setFragmentResultListener(TeamEventDatePickerFragment.RESULT_KEY, this) { _, result ->
            startDate = LocalDate.parse(result.getString("startDate"))
            endDate = result.getString("endDate")?.let(LocalDate::parse)
            updateDates()
        }
        body.addView(dates); controls += dates
        allDay = CheckBox(context).apply {
            setText(R.string.calendar_all_day)
            isChecked = state.getBoolean("allDay", true)
        }
        body.addView(allDay); controls += allDay
        val start = field("startTime", R.string.calendar_start_time, InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_TIME)
        val end = field("endTime", R.string.calendar_end_time, InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_TIME)
        listOf(start, end).forEach { input ->
            input.keyListener = null
            input.showSoftInputOnFocus = false
            input.setOnClickListener {
                if (childFragmentManager.findFragmentByTag("event_time") == null) {
                    val time = runCatching { LocalTime.parse(input.text) }.getOrDefault(LocalTime.of(9, 0))
                    val date = if (input == start) startDate else endDate ?: startDate
                    val earliest = if (input == end && date == startDate) {
                        runCatching { LocalTime.parse(start.text) }.getOrNull()
                    } else null
                    TeamEventTimePickerFragment.newInstance(input == start, time, durationMinutes, date, earliest, futureOnly = editingId == null)
                        .show(childFragmentManager, "event_time")
                }
            }
        }
        childFragmentManager.setFragmentResultListener(TeamEventTimePickerFragment.RESULT_KEY, this) { _, result ->
            val time = LocalTime.of(result.getInt("hour"), result.getInt("minute"))
            val format = DateTimeFormatter.ofPattern("HH:mm", java.util.Locale.ROOT)
            if (result.getBoolean("isStart")) {
                durationMinutes = result.getInt("duration")
                start.setText(time.format(format))
                val finish = startDate.atTime(time).plusMinutes(durationMinutes.toLong())
                end.setText(finish.toLocalTime().format(format))
                endDate = finish.toLocalDate().takeIf { it != startDate || endDate != null }
                updateDates()
            } else {
                end.setText(time.format(format))
            }
        }
        fun updateTimes() {
            start.isEnabled = !allDay.isChecked; end.isEnabled = !allDay.isChecked
        }
        allDay.setOnCheckedChangeListener { _, _ -> updateTimes() }; updateTimes()
        fun spinner(label: Int, options: List<String>): Spinner {
            body.addView(TextView(context).apply { setText(label) })
            return Spinner(context).also {
                it.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, options)
                body.addView(it); controls += it
            }
        }
        recurrence = spinner(R.string.calendar_recurrence, listOf(getString(R.string.calendar_recurrence_none),
            getString(R.string.calendar_recurrence_daily), getString(R.string.calendar_recurrence_weekly)))
        recurrence.setSelection(state.getInt("recurrence", 0))
        val count = field("count", R.string.calendar_repetitions, InputType.TYPE_CLASS_NUMBER)
        val daysContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        weekdays = meetupWeekdays.map { day ->
            CheckBox(context).apply {
                text = day.getDisplayName(TextStyle.FULL, resources.configuration.locales[0])
                isChecked = state.getBoolean(day.name)
                daysContainer.addView(this); controls += this
            }
        }
        body.addView(daysContainer)
        recurrence.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                (count.parent as View).isVisible = position != 0
                daysContainer.isVisible = position == 2
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
        category = spinner(R.string.calendar_category, meetupCategories.map { getString(meetupCategoryLabels.getValue(it)) })
        category.setSelection(state.getInt("category", 0))
        field("location", R.string.calendar_location)
        field("link", R.string.calendar_link, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        error = TextView(context).apply { isVisible = false; accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        body.addView(error)
        return MaterialAlertDialogBuilder(context).setTitle(if (editingId == null) R.string.calendar_create_event else R.string.calendar_edit_event)
            .setView(ScrollView(context).apply { addView(body) })
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.server_configuration_save, null).create()
    }

    override fun onStart() {
        super.onStart()
        val alert = requireDialog() as AlertDialog
        editingId?.let { model.load(requireArguments().getString("teamId")!!, it) }
        alert.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (editingId != null && model.state.value.editable == null) {
                model.load(requireArguments().getString("teamId")!!, editingId!!, force = true)
                return@setOnClickListener
            }
            val draft = draft()
            try {
                if (editingId == null) draft.toCreationJson() else draft.toJson()
                error.isVisible = false
                model.save(requireArguments().getString("teamId")!!, draft, editingId)
            } catch (invalid: EventValidationException) {
                error.setText(invalid.messageRes); error.isVisible = true
            }
        }
        observerJob?.cancel()
        observerJob = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.state.collect { state ->
                    state.editable?.let { if (appliedRevision != it.meetup.revision) populate(it) }
                    val busy = state.saving || state.loading
                    isCancelable = !state.saving
                    alert.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = !busy
                    alert.getButton(AlertDialog.BUTTON_POSITIVE).setText(
                        if (busy) R.string.calendar_saving else R.string.server_configuration_save)
                    alert.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = !state.saving
                    controls.forEach { it.isEnabled = !busy && (editingId == null || state.editable != null) }
                    if (allDay.isChecked) {
                        fields.getValue("startTime").isEnabled = false
                        fields.getValue("endTime").isEnabled = false
                    }
                    state.errorRes?.let { error.setText(it); error.isVisible = true }
                    state.saved?.let {
                        parentFragmentManager.setFragmentResult(RESULT_KEY, Bundle().apply {
                            putString("meetupId", it.id)
                            putString("meetupRev", it.revision)
                            putString("teamId", requireArguments().getString("teamId"))
                            putString("startDate", startDate.toString())
                        })
                        dismiss()
                    }
                }
            }
        }
    }

    override fun onStop() {
        observerJob?.cancel()
        super.onStop()
    }

    private fun updateDates() {
        val format = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(resources.configuration.locales[0])
        dates.text = startDate.format(format) + (endDate?.let { " – " + it.format(format) } ?: "")
        dates.contentDescription = getString(R.string.calendar_choose_date) + ": " + dates.text
    }

    private fun populate(snapshot: org.ole.planet.myplanet.lite.dashboard.EditableTeamEvent) {
        val event = snapshot.meetup
        val zone = java.time.ZoneId.systemDefault()
        startDate = java.time.Instant.ofEpochMilli(event.startDate).atZone(zone).toLocalDate()
        endDate = event.endDate?.let { java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
        mapOf("title" to event.title, "description" to event.description, "startTime" to event.startTime,
            "endTime" to event.endTime, "count" to event.recurringNumber.toString(), "location" to event.location,
            "link" to event.link).forEach { (key, value) -> fields.getValue(key).setText(value) }
        allDay.isChecked = event.startTime.isBlank() && event.endTime.isBlank()
        recurrence.setSelection(listOf("none", "daily", "weekly").indexOf(event.recurring).coerceAtLeast(0))
        val raw = org.json.JSONObject(snapshot.raw)
        category.setSelection(meetupCategories.indexOf(raw.optString("category")).coerceAtLeast(0))
        meetupWeekdays.forEachIndexed { index, day -> weekdays[index].isChecked = day in event.days }
        updateDates()
        appliedRevision = event.revision
    }

    private fun draft(): TeamEventDraft {
        fun text(key: String) = fields.getValue(key).text.toString()
        return TeamEventDraft(text("title"), text("description"), startDate, endDate,
            if (allDay.isChecked) "" else text("startTime"), if (allDay.isChecked) "" else text("endTime"),
            listOf("none", "daily", "weekly")[recurrence.selectedItemPosition], text("count"),
            meetupWeekdays.filterIndexed { index, _ -> weekdays[index].isChecked }.toSet(),
            meetupCategories[category.selectedItemPosition], text("location"), text("link"))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("appliedRevision", appliedRevision)
        outState.putString("startDate", startDate.toString()); outState.putString("endDate", endDate?.toString())
        fields.forEach { (key, input) -> outState.putString(key, input.text.toString()) }
        outState.putInt("duration", durationMinutes)
        outState.putBoolean("allDay", allDay.isChecked)
        outState.putInt("recurrence", recurrence.selectedItemPosition); outState.putInt("category", category.selectedItemPosition)
        meetupWeekdays.forEachIndexed { index, day -> outState.putBoolean(day.name, weekdays[index].isChecked) }
    }

    companion object {
        const val RESULT_KEY = "team_event_created"
        fun editInstance(teamId: String, eventId: String) = CreateTeamEventDialogFragment().apply {
            arguments = Bundle().apply { putString("teamId", teamId); putString("editingId", eventId) }
        }
        fun newInstance(teamId: String, date: LocalDate) = CreateTeamEventDialogFragment().apply {
            arguments = Bundle().apply {
                putString("teamId", teamId)
                putString("startDate", date.toString())
            }
        }
    }
}
