package org.ole.planet.myplanet.lite

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

internal class EnterpriseTaskActionsTapeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.enterprise_actions_tape)
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.enterprise_actions_tape_shadow)
    }
    private val path = Path()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.density
        val top = 2f * density
        val bottom = height - 2f * density
        buildTapePath(top + 3f * density, bottom + 2f * density)
        canvas.drawPath(path, shadowPaint)
        buildTapePath(top, bottom)
        canvas.drawPath(path, paint)
    }

    private fun buildTapePath(top: Float, bottom: Float) {
        val density = resources.displayMetrics.density
        path.reset()
        path.moveTo(7f * density, top)
        path.lineTo(2f * density, top + 5f * density)
        path.lineTo(6f * density, top + 10f * density)
        path.lineTo(1f * density, top + 16f * density)
        path.lineTo(5f * density, top + 23f * density)
        path.lineTo(2f * density, top + 30f * density)
        path.lineTo(7f * density, bottom)
        path.lineTo(width - 7f * density, bottom)
        path.lineTo(width - 2f * density, bottom - 6f * density)
        path.lineTo(width - 6f * density, bottom - 13f * density)
        path.lineTo(width - 1f * density, bottom - 20f * density)
        path.lineTo(width - 5f * density, bottom - 28f * density)
        path.lineTo(width - 2f * density, top + 7f * density)
        path.lineTo(width - 7f * density, top)
        path.close()
    }
}
