package org.ole.planet.myplanet.lite.util

import android.content.Context
import org.ole.planet.myplanet.lite.R
import kotlin.math.max

object TimeUtils {

    private const val MINUTE_MILLIS = 60_000L
    private const val HOUR_MILLIS = 60 * MINUTE_MILLIS
    private const val DAY_MILLIS = 24 * HOUR_MILLIS
    private const val MONTH_MILLIS = 30 * DAY_MILLIS
    private const val YEAR_MILLIS = 12 * MONTH_MILLIS

    fun formatRelativeTime(context: Context, timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diffMillis = max(0L, now - timestamp)
        val minutes = diffMillis / MINUTE_MILLIS
        val hours = diffMillis / HOUR_MILLIS
        val days = diffMillis / DAY_MILLIS
        val months = diffMillis / MONTH_MILLIS
        val years = diffMillis / YEAR_MILLIS
        return when {
            years >= 1 -> if (years == 1L) {
                context.getString(R.string.dashboard_relative_time_year)
            } else {
                context.getString(R.string.dashboard_relative_time_years, years)
            }
            months >= 1 -> if (months == 1L) {
                context.getString(R.string.dashboard_relative_time_month)
            } else {
                context.getString(R.string.dashboard_relative_time_months, months)
            }
            days >= 1 -> if (days == 1L) {
                context.getString(R.string.dashboard_relative_time_day)
            } else {
                context.getString(R.string.dashboard_relative_time_days, days)
            }
            hours >= 1 -> if (hours == 1L) {
                context.getString(R.string.dashboard_relative_time_hour)
            } else {
                context.getString(R.string.dashboard_relative_time_hours, hours)
            }
            minutes >= 1 -> if (minutes == 1L) {
                context.getString(R.string.dashboard_relative_time_minute)
            } else {
                context.getString(R.string.dashboard_relative_time_minutes, minutes)
            }
            else -> context.getString(R.string.dashboard_relative_time_seconds)
        }
    }

    fun buildMetadata(username: String?, relativeTime: String): String {
        return if (!username.isNullOrBlank()) {
            "@${username.trim()} • $relativeTime"
        } else {
            relativeTime
        }
    }
}
