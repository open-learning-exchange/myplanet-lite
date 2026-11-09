package org.ole.planet.myplanet.lite

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

internal data class CalendarHourBand(val first: Int, val last: Int, val occupied: Boolean)

/** Empty intermediate hours share one compact band; leading and trailing hours are omitted. */
internal fun calendarHourBands(occupied: Set<Int>): List<CalendarHourBand> {
    val hours = occupied.filter { it in 0..23 }.sorted()
    return buildList {
        hours.forEachIndexed { index, hour ->
            if (index > 0 && hour > hours[index - 1] + 1) {
                add(CalendarHourBand(hours[index - 1] + 1, hour - 1, false))
            }
            add(CalendarHourBand(hour, hour, true))
        }
    }
}

/** Compact agenda: all-day reminders stay above the time axis. */
internal object CalendarDayTimeline {
    fun render(container: LinearLayout, date: LocalDate, events: List<TeamCalendarEvent>, card: (TeamCalendarEvent) -> View) {
        val context = container.context
        fun dp(n: Int) = (n * context.resources.displayMetrics.density).toInt()
        events.filter { it.timeLabel.isBlank() }.forEach { container.addView(card(it)) }
        val timed = events.filter { it.timeLabel.isNotBlank() }.groupBy {
            if (it.occurrenceStart?.isBefore(date) == true) 0
            else it.timeLabel.substringBefore(':').trim().toIntOrNull()?.coerceIn(0, 23) ?: 0
        }
        val format = DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT)
            .withLocale(context.resources.configuration.locales[0])
        for (band in calendarHourBands(timed.keys)) {
            val hour = band.first
            val minimum = dp(if (band.occupied) 72 else 40)
            val row = FrameLayout(context).apply {
                minimumHeight = minimum
                layoutParams = LinearLayout.LayoutParams(-1, -2)
            }
            val contents = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                minimumHeight = minimum
                addView(TextView(context).apply {
                    text = LocalTime.of(hour, 0).format(format) +
                        if (band.last > hour) "\n…\n" + LocalTime.of(band.last, 0).format(format) else ""
                    val textColor = android.util.TypedValue()
                    context.theme.resolveAttribute(android.R.attr.textColorSecondary, textColor, true)
                    if (textColor.resourceId != 0) setTextColor(androidx.core.content.ContextCompat.getColorStateList(context, textColor.resourceId))
                    else setTextColor(textColor.data)
                    textSize = 11f; gravity = Gravity.TOP or Gravity.END
                    setPadding(0, dp(8), dp(8), 0)
                }, LinearLayout.LayoutParams(dp(64), -2))
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, dp(4), 0, dp(4))
                    timed[hour].orEmpty().forEach { addView(card(it)) }
                }, LinearLayout.LayoutParams(0, -2, 1f))
            }
            row.addView(contents, FrameLayout.LayoutParams(-1, -2))
            row.addView(object : View(context) {
                private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    val colorValue = android.util.TypedValue()
                    context.theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, colorValue, true)
                    color = if (colorValue.resourceId != 0) androidx.core.content.ContextCompat.getColor(context, colorValue.resourceId) else colorValue.data
                    strokeWidth = dp(1).toFloat()
                    pathEffect = DashPathEffect(floatArrayOf(dp(4).toFloat(), dp(4).toFloat()), 0f)
                }
                private val tick = object : Runnable {
                    override fun run() { invalidate(); postDelayed(this, 30_000) }
                }
                override fun onAttachedToWindow() { super.onAttachedToWindow(); post(tick) }
                override fun onDetachedFromWindow() { removeCallbacks(tick); super.onDetachedFromWindow() }
                override fun onDraw(canvas: Canvas) {
                    super.onDraw(canvas)
                    paint.pathEffect = null
                    paint.alpha = 35
                    canvas.drawLine(dp(64).toFloat(), 1f, width.toFloat(), 1f, paint)
                    paint.alpha = 255
                    paint.pathEffect = DashPathEffect(floatArrayOf(dp(4).toFloat(), dp(4).toFloat()), 0f)
                    val now = LocalTime.now()
                    if (date != LocalDate.now() || now.hour !in band.first..band.last) return
                    val elapsedMinutes = (now.hour - band.first) * 60 + now.minute + now.second / 60f
                    val y = height * elapsedMinutes / ((band.last - band.first + 1) * 60f)
                    canvas.drawLine(dp(64).toFloat(), y, width.toFloat(), y, paint)
                }
            }, FrameLayout.LayoutParams(-1, -1))
            container.addView(row)
        }
    }
}
