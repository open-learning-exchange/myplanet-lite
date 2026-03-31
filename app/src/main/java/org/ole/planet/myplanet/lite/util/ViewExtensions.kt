package org.ole.planet.myplanet.lite.util

import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup

private const val CLICK_DRAG_TOLERANCE = 10

fun View.enableDrag() {
    var downRawX = 0f
    var downRawY = 0f
    var dX = 0f
    var dY = 0f

    setOnTouchListener { view, event ->
        val parentView = view.parent as? ViewGroup ?: return@setOnTouchListener false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                dX = view.x - event.rawX
                dY = view.y - event.rawY
                parentView.requestDisallowInterceptTouchEvent(true)
                true
            }

            MotionEvent.ACTION_MOVE -> {
                var newX = event.rawX + dX
                var newY = event.rawY + dY
                val maxX = (parentView.width - view.width).toFloat()
                val maxY = (parentView.height - view.height).toFloat()
                newX = newX.coerceIn(0f, maxX)
                newY = newY.coerceIn(0f, maxY)
                view.x = newX
                view.y = newY
                true
            }

            MotionEvent.ACTION_UP -> {
                val upDX = event.rawX - downRawX
                val upDY = event.rawY - downRawY
                parentView.requestDisallowInterceptTouchEvent(false)
                if (kotlin.math.abs(upDX) < CLICK_DRAG_TOLERANCE && kotlin.math.abs(upDY) < CLICK_DRAG_TOLERANCE) {
                    view.performClick()
                }
                true
            }

            MotionEvent.ACTION_CANCEL -> {
                parentView.requestDisallowInterceptTouchEvent(false)
                true
            }

            else -> false
        }
    }
}
