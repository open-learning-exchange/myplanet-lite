package org.ole.planet.myplanet.lite

import org.ole.planet.myplanet.lite.util.enableDrag
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import io.noties.markwon.Markwon
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ole.planet.myplanet.lite.auth.AuthDependencies
import org.ole.planet.myplanet.lite.profile.ProfileCredentialsStore
import org.ole.planet.myplanet.lite.dashboard.DashboardServerPreferences
import org.ole.planet.myplanet.lite.dashboard.TeamCalendarRepository
import org.ole.planet.myplanet.lite.dashboard.CalendarAccessDeniedException
import org.ole.planet.myplanet.lite.dashboard.exportOccurrence
import org.ole.planet.myplanet.lite.dashboard.TeamMeetup
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.MaterialDatePicker
import org.ole.planet.myplanet.lite.dashboard.DashboardTeamSelectionPreferences
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.WeekFields

internal data class TeamCalendarEvent(
    val id: String,
    val date: LocalDate,
    val title: String,
    val timeLabel: String = "",
    val description: String = "",
    val meetupLink: String = "",
    val sourceCalendar: String = "",
    val location: String = "",
    val planetMeetup: TeamMeetup? = null,
    val occurrenceStart: LocalDate? = null,
)

class TeamCalendarFragment : Fragment(R.layout.fragment_team_calendar) {
    internal var selectedDate: LocalDate = LocalDate.now()
        private set
    internal var visibleMonth: YearMonth = YearMonth.from(selectedDate)
        private set
    private var events: List<TeamCalendarEvent> = emptyList()
    private var googleEvents: List<TeamCalendarEvent> = emptyList()
    private lateinit var googleConnection: GoogleCalendarConnection
    private val googlePermission = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted -> if (view != null && ::googleConnection.isInitialized) googleConnection.permissionResult(granted) }
    private var currentTeamId: String? = null
    private var meetups: List<TeamMeetup>? = null
    private var loadJob: Job? = null
    private var loadedTeamId: String? = null
    private var loadError: Int? = null
    private var isLoadingEvents = false
    private val repository = TeamCalendarRepository()
    private val markwon by lazy { Markwon.builder(requireContext()).build() }
    private val locale get() = resources.configuration.locales[0]
    private val dateFormat get() = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.getString(STATE_DATE)?.let { selectedDate = LocalDate.parse(it) }
        savedInstanceState?.getString(STATE_MONTH)?.let { visibleMonth = YearMonth.parse(it) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.calendarPreviousMonth).setOnClickListener { moveMonth(-1) }
        view.findViewById<View>(R.id.calendarNextMonth).setOnClickListener { moveMonth(1) }
        view.findViewById<View>(R.id.calendarToday).setOnClickListener { selectDate(LocalDate.now()) }
        view.findViewById<View>(R.id.calendarMonthTitle).setOnClickListener { showDatePicker() }
        view.findViewById<View>(R.id.calendarRefresh).setOnClickListener { loadEvents(force = true); googleConnection.refresh() }
        view.findViewById<View>(R.id.calendarCreate).setOnClickListener {
            val teamId = currentTeamId ?: return@setOnClickListener
            if (childFragmentManager.findFragmentByTag("create_team_event") == null) {
                CreateTeamEventDialogFragment.newInstance(teamId, selectedDate)
                    .show(childFragmentManager, "create_team_event")
            }
        }
        view.findViewById<View>(R.id.calendarCreate).enableDrag()
        childFragmentManager.setFragmentResultListener(CreateTeamEventDialogFragment.RESULT_KEY, viewLifecycleOwner) { _, result ->
            if (result.getString("teamId") == currentTeamId) {
                result.getString("startDate")?.let { selectDate(LocalDate.parse(it)) }
                loadEvents(force = true)
            }
        }
        googleConnection = GoogleCalendarConnection(this, view,
            requestPermission = { googlePermission.launch(android.Manifest.permission.READ_CALENDAR) },
            onEvents = { googleEvents = it; renderCalendar() },
        )
        refreshTeam()
        renderCalendar()
    }

    override fun onResume() {
        super.onResume()
        refreshTeam()
        renderCalendar()
    }

    internal fun refreshTeam() {
        val teamId = DashboardTeamSelectionPreferences.getSelectedTeamId(requireContext())
        if (teamId != currentTeamId) {
            loadJob?.cancel()
            meetups = null
            events = emptyList()
            loadedTeamId = null
            loadError = null
            isLoadingEvents = false
            currentTeamId = teamId
        }
        view?.findViewById<TextView>(R.id.calendarTeamName)?.text =
            DashboardTeamSelectionPreferences.getSelectedTeamName(requireContext())
                ?: getString(R.string.dashboard_teams_select_team_hint)
        if (view != null) {
            renderCalendar()
            loadEvents()
        }
    }

    private fun loadEvents(force: Boolean = false) {
        val teamId = currentTeamId ?: return
        if (!force && (loadedTeamId == teamId || loadJob?.isActive == true)) return
        val context = requireContext().applicationContext
        val base = DashboardServerPreferences.getServerBaseUrl(context)
        val credentials = ProfileCredentialsStore.getStoredCredentials(context)
        if (base.isNullOrBlank() || credentials == null) {
            loadError = R.string.calendar_load_error
            renderCalendar()
            return
        }
        loadJob?.cancel()
        events = emptyList()
        meetups = null
        isLoadingEvents = true
        loadError = null
        renderCalendar()
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            val result = try {
                val cookie = withContext(Dispatchers.IO) {
                    AuthDependencies.provideAuthService(context, base).getStoredToken()
                }
                repository.fetch(base, credentials, cookie, teamId)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Result.failure(error)
            }
            if (teamId != currentTeamId) return@launch
            isLoadingEvents = false
            result.onSuccess {
                meetups = it
                loadedTeamId = teamId
            }.onFailure {
                loadError = if (it is CalendarAccessDeniedException) R.string.calendar_access_denied else R.string.calendar_load_error
            }
            renderCalendar()
        }
    }

    override fun onDestroyView() {
        loadJob?.cancel()
        loadJob = null
        isLoadingEvents = false
        googleEvents = emptyList()
        super.onDestroyView()
    }

    internal fun showEvents(teamId: String, items: List<TeamCalendarEvent>) {
        if (teamId != currentTeamId) return
        loadJob?.cancel()
        isLoadingEvents = false
        loadError = null
        meetups = null
        events = items
        if (view != null) renderCalendar()
    }

    internal fun selectDate(date: LocalDate) {
        selectedDate = date
        visibleMonth = YearMonth.from(date)
        renderCalendar()
    }

    internal fun moveMonth(amount: Long) {
        visibleMonth = visibleMonth.plusMonths(amount)
        selectedDate = visibleMonth.atDay(selectedDate.dayOfMonth.coerceAtMost(visibleMonth.lengthOfMonth()))
        renderCalendar()
    }

    private fun renderCalendar() {
        val root = view ?: return
        val firstDay = WeekFields.of(locale).firstDayOfWeek
        val visibleDays = calendarMonthDays(visibleMonth, firstDay)
        if (::googleConnection.isInitialized) googleConnection.showRange(currentTeamId, visibleDays.first(), visibleDays.last())
        meetups?.let { documents ->
            events = documents.flatMap { it.occurrences(visibleDays.first(), visibleDays.last()) }
                .distinctBy { it.id to it.date }
        }
        val displayedEvents = events + googleEvents
        root.findViewById<View>(R.id.calendarLoading).isVisible = isLoadingEvents
        root.findViewById<TextView>(R.id.calendarStatus).apply {
            isVisible = loadError != null
            loadError?.let { setText(it) }
        }
        root.findViewById<View>(R.id.calendarCreate).isVisible = currentTeamId != null
        root.findViewById<View>(R.id.calendarCreate).isEnabled = !isLoadingEvents && loadError != R.string.calendar_access_denied
        root.findViewById<View>(R.id.calendarRefresh).isVisible = currentTeamId != null
        root.findViewById<MaterialButton>(R.id.calendarMonthTitle).text =
            visibleMonth.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
        val weekdays = root.findViewById<LinearLayout>(R.id.calendarWeekdays)
        weekdays.removeAllViews()
        for (offset in 0L until 7L) {
            val day = firstDay.plus(offset)
            weekdays.addView(TextView(requireContext()).apply {
                text = day.getDisplayName(java.time.format.TextStyle.SHORT, locale)
                gravity = Gravity.CENTER
                setTextColor(themeColor(android.R.attr.textColorSecondary))
                layoutParams = LinearLayout.LayoutParams(0, dp(32), 1f)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            })
        }
        val grid = root.findViewById<GridLayout>(R.id.calendarDays)
        grid.removeAllViews()
        val today = LocalDate.now()
        visibleDays.forEachIndexed { index, date ->
            val selected = date == selectedDate
            val eventCount = displayedEvents.count { it.date == date }
            val primary = themeColor(androidx.appcompat.R.attr.colorPrimary)
            val textColor = if (selected) Color.WHITE else if (date == today) primary
                else themeColor(android.R.attr.textColorPrimary)
            grid.addView(TextView(requireContext()).apply {
                text = date.dayOfMonth.toString() + if (eventCount > 0) "\n•" else ""
                textSize = 16f
                gravity = Gravity.CENTER
                isSelected = selected
                isClickable = true
                isFocusable = true
                alpha = if (YearMonth.from(date) == visibleMonth || selected) 1f else 0.4f
                setTextColor(textColor)
                if (date == today || selected) setTypeface(typeface, Typeface.BOLD)
                contentDescription = date.format(dateFormat) +
                    if (eventCount > 0) ", " + getString(R.string.calendar_event_count, eventCount) else ""
                background = GradientDrawable().apply {
                    cornerRadius = dp(24).toFloat()
                    setColor(if (selected) primary else Color.TRANSPARENT)
                    if (date == today && !selected) setStroke(dp(1), primary)
                }
                layoutParams = GridLayout.LayoutParams(
                    GridLayout.spec(index / 7), GridLayout.spec(index % 7, 1f),
                ).apply {
                    width = 0
                    height = dp(48)
                    setMargins(dp(2), dp(2), dp(2), dp(2))
                }
                setOnClickListener { selectDate(date) }
            })
        }
        renderAgenda(root)
    }

    private fun renderAgenda(root: View) {
        root.findViewById<TextView>(R.id.calendarSelectedDate).text = selectedDate.format(dateFormat)
        val container = root.findViewById<LinearLayout>(R.id.calendarEvents)
        container.removeAllViews()
        val dayEvents = (events + googleEvents).filter { it.date == selectedDate }.sortedBy { it.timeLabel }
        root.findViewById<View>(R.id.calendarEmpty).isVisible = dayEvents.isEmpty() && !isLoadingEvents && loadError == null
        dayEvents.forEach { event ->
            container.addView(LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(12), dp(16), dp(12))
                background = GradientDrawable().apply {
                    cornerRadius = dp(12).toFloat()
                    setColor(themeColor(androidx.appcompat.R.attr.colorPrimary).let {
                        Color.argb(18, Color.red(it), Color.green(it), Color.blue(it))
                    })
                }
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
                addView(TextView(context).apply {
                    text = event.title
                    textSize = 16f
                    setTypeface(typeface, Typeface.BOLD)
                })
                addView(TextView(context).apply {
                    text = event.timeLabel.ifBlank { getString(R.string.calendar_all_day) }
                })
                if (event.description.isNotBlank() || event.location.isNotBlank()) addView(TextView(context).apply {
                    if (event.sourceCalendar.isBlank()) markwon.setMarkdown(this, event.description)
                    else {
                        text = googleCalendarDescription(event.description, event.location)
                        movementMethod = android.text.method.LinkMovementMethod.getInstance()
                    }
                })
                if (event.sourceCalendar.isNotBlank()) addView(TextView(context).apply {
                    text = "Google Calendar · " + event.sourceCalendar
                    textSize = 12f
                })
                if (event.meetupLink.isNotBlank()) addView(TextView(context).apply {
                    val url = event.meetupLink.trim()
                    text = android.text.SpannableString(url).apply {
                        setSpan(android.text.style.URLSpan(url), 0, length,
                            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    movementMethod = android.text.method.LinkMovementMethod.getInstance()
                })
                val username = ProfileCredentialsStore.getStoredCredentials(context)?.username
                if (!username.isNullOrBlank() && event.planetMeetup?.createdBy == username) addView(MaterialButton(context).apply {
                    setText(R.string.calendar_edit_event)
                    setOnClickListener {
                        val team = currentTeamId ?: return@setOnClickListener
                        if (childFragmentManager.findFragmentByTag("create_team_event") == null) {
                            CreateTeamEventDialogFragment.editInstance(team, event.id)
                                .show(childFragmentManager, "create_team_event")
                        }
                    }
                })
                if (event.planetMeetup != null && event.occurrenceStart != null) addView(MaterialButton(context).apply {
                    setText(R.string.calendar_google_add_event)
                    setIconResource(R.drawable.ic_google_24)
                    iconTint = null
                    setOnClickListener { exportToGoogle(event) }
                })
            })
        }
    }

    private fun exportToGoogle(event: TeamCalendarEvent) {
        val meetup = event.planetMeetup ?: return
        val occurrence = event.occurrenceStart ?: return
        try {
            val export = meetup.exportOccurrence(occurrence, markwon.toMarkdown(meetup.description).toString())
            try {
                startActivity(export.appIntent())
            } catch (_: android.content.ActivityNotFoundException) {
                startActivity(export.webIntent())
            }
        } catch (_: Exception) {
            android.widget.Toast.makeText(requireContext(), R.string.calendar_google_export_error,
                android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun showDatePicker() {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.calendar_choose_date)
            .setSelection(selectedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
            .build()
        picker.addOnPositiveButtonClickListener { timestamp ->
            selectDate(Instant.ofEpochMilli(timestamp).atZone(ZoneOffset.UTC).toLocalDate())
        }
        if (parentFragmentManager.findFragmentByTag(DATE_PICKER_TAG) == null) {
            picker.show(parentFragmentManager, DATE_PICKER_TAG)
        }
    }

    private fun themeColor(attribute: Int): Int {
        val value = TypedValue()
        requireContext().theme.resolveAttribute(attribute, value, true)
        return if (value.resourceId != 0) androidx.core.content.ContextCompat.getColor(requireContext(), value.resourceId) else value.data
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_DATE, selectedDate.toString())
        outState.putString(STATE_MONTH, visibleMonth.toString())
    }

    private companion object {
        const val STATE_DATE = "calendar_date"
        const val STATE_MONTH = "calendar_month"
        const val DATE_PICKER_TAG = "team_calendar_date_picker"
    }
}
