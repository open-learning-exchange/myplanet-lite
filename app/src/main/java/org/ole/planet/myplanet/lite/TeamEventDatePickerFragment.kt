package org.ole.planet.myplanet.lite

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.WeekFields

class TeamEventDatePickerFragment : DialogFragment() {
    internal var start: LocalDate = LocalDate.now()
        private set
    internal var end: LocalDate? = null
        private set
    private var awaitingEnd = false
    private var month = YearMonth.now()
    private lateinit var previous: MaterialButton
    private lateinit var heading: MaterialButton
    private lateinit var summary: TextView
    private lateinit var grid: GridLayout
    private val futureOnly get() = requireArguments().getBoolean("futureOnly", true)
    private val locale get() = resources.configuration.locales[0]

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val state = savedInstanceState ?: requireArguments()
        start = LocalDate.parse(state.getString("startDate"))
        if (futureOnly) start = maxOf(LocalDate.now(), start)
        end = state.getString("endDate")?.let(LocalDate::parse)?.takeIf { it >= start }
        awaitingEnd = state.getBoolean("awaitingEnd")
        month = state.getString("month")?.let(YearMonth::parse) ?: YearMonth.from(start)
        val body = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        body.addView(TextView(requireContext()).apply { setText(R.string.calendar_select_date_range) })
        body.addView(LinearLayout(requireContext()).apply {
            gravity = Gravity.CENTER_VERTICAL
            previous = MaterialButton(context).apply {
                text = "‹"; contentDescription = getString(R.string.calendar_previous_month)
                layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
                minWidth = 0; setPadding(0, 0, 0, 0)
                setOnClickListener { month = month.minusMonths(1); render() }
            }
            addView(previous)
            heading = MaterialButton(context).apply {
                isClickable = false
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            }
            addView(heading)
            addView(MaterialButton(context).apply {
                text = "›"; contentDescription = getString(R.string.calendar_next_month)
                layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
                minWidth = 0; setPadding(0, 0, 0, 0)
                setOnClickListener { month = month.plusMonths(1); render() }
            })
        })
        body.addView(LinearLayout(requireContext()).apply {
            val first = WeekFields.of(locale).firstDayOfWeek
            for (offset in 0L..6L) addView(TextView(context).apply {
                text = first.plus(offset).getDisplayName(TextStyle.NARROW, locale)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, dp(32), 1f)
            })
        })
        grid = GridLayout(requireContext()).apply { columnCount = 7; rowCount = 6 }
        body.addView(grid)
        summary = TextView(requireContext()).apply { setPadding(0, dp(12), 0, 0) }
        body.addView(summary)
        render()
        return MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.calendar_choose_date)
            .setView(ScrollView(requireContext()).apply { addView(body) }).setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                render()
                parentFragmentManager.setFragmentResult(RESULT_KEY, Bundle().apply {
                    putString("startDate", start.toString()); putString("endDate", end?.toString())
                })
            }.create()
    }

    internal fun select(date: LocalDate) {
        if (futureOnly && date < LocalDate.now()) return
        if (!awaitingEnd) {
            start = date
            end = null
            awaitingEnd = true
        } else {
            end = maxOf(start, date)
            start = minOf(start, date)
            awaitingEnd = false
        }
        render()
    }

    private fun render() {
        val today = LocalDate.now()
        if (futureOnly && start < today) { start = today; end = end?.takeIf { it >= start } }
        if (futureOnly) month = maxOf(month, YearMonth.from(today))
        previous.isEnabled = !futureOnly || month > YearMonth.from(today)
        heading.text = month.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
        val format = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
        summary.text = start.format(format) + (end?.let { " – " + it.format(format) } ?: "")
        val value = TypedValue()
        requireContext().theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, value, true)
        val primary = if (value.resourceId != 0) ContextCompat.getColor(requireContext(), value.resourceId) else value.data
        grid.removeAllViews()
        calendarMonthDays(month, WeekFields.of(locale).firstDayOfWeek).forEachIndexed { index, date ->
            val selected = date >= start && date <= (end ?: start)
            grid.addView(TextView(requireContext()).apply {
                tag = date.toString()
                text = date.dayOfMonth.toString(); textSize = 16f
                gravity = Gravity.CENTER
                isSelected = selected; isClickable = true; isFocusable = true
                isEnabled = !futureOnly || date >= today
                contentDescription = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))
                if (selected) setTextColor(Color.WHITE)
                alpha = if (futureOnly && date < today) 0.2f else if (YearMonth.from(date) == month || selected) 1f else 0.4f
                background = GradientDrawable().apply {
                    cornerRadius = dp(20).toFloat()
                    setColor(if (selected) primary else Color.TRANSPARENT)
                }
                layoutParams = GridLayout.LayoutParams(GridLayout.spec(index / 7), GridLayout.spec(index % 7, 1f)).apply {
                    width = 0; height = dp(48)
                    setMargins(dp(1), dp(1), dp(1), dp(1))
                }
                setOnClickListener { select(date) }
            })
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("startDate", start.toString()); outState.putString("endDate", end?.toString())
        outState.putString("month", month.toString()); outState.putBoolean("awaitingEnd", awaitingEnd)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val RESULT_KEY = "team_event_dates"
        fun newInstance(start: LocalDate, end: LocalDate?, futureOnly: Boolean = true) = TeamEventDatePickerFragment().apply {
            arguments = Bundle().apply {
                putString("startDate", start.toString()); putString("endDate", end?.toString())
                putBoolean("futureOnly", futureOnly)
            }
        }
    }
}
