package org.ole.planet.myplanet.lite

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class CourseGridSpacingDecoration(private val spanCount: Int) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val position = parent.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION || position == 0) {
            outRect.set(0, 0, 0, 0)
            return
        }

        val column = (position - 1) % spanCount
        val spacing = view.resources.getDimensionPixelSize(R.dimen.dashboard_card_spacing)
        val halfSpacing = spacing / 2

        outRect.top = spacing
        outRect.left = if (column == 0) 0 else halfSpacing
        outRect.right = if (column == spanCount - 1) 0 else halfSpacing
        outRect.bottom = 0
    }
}