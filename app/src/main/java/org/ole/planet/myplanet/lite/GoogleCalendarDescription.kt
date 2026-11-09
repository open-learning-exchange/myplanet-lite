package org.ole.planet.myplanet.lite

import android.text.SpannableStringBuilder
import androidx.core.text.HtmlCompat

private val calendarHtmlTag = Regex("</?(?:a|b|strong|i|em|u|s|strike|p|div|br|ul|ol|li|span|font|h[1-6]|blockquote|img|table|tr|td)\\b[^>]*>", RegexOption.IGNORE_CASE)

internal fun googleCalendarDescription(description: String, location: String): CharSequence {
    val body = if (calendarHtmlTag.containsMatchIn(description)) {
        HtmlCompat.fromHtml(description.replace("\n", "<br>"), HtmlCompat.FROM_HTML_MODE_COMPACT)
    } else description
    return SpannableStringBuilder(body).apply {
        if (location.isNotBlank()) {
            if (isNotEmpty()) append("\n")
            append(location)
        }
    }
}
