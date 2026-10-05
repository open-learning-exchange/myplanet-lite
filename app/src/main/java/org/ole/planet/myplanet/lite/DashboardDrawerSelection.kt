package org.ole.planet.myplanet.lite

import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import androidx.annotation.IdRes
import com.google.android.material.navigation.NavigationView

internal fun NavigationView.highlightDashboardDestination(@IdRes destinationId: Int) {
    for (index in 0 until menu.size()) {
        val item = menu.getItem(index)
        val title = SpannableString(item.title.toString())
        if (item.itemId == destinationId) {
            title.setSpan(StyleSpan(Typeface.BOLD), 0, title.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        item.title = title
    }
}
