package org.ole.planet.myplanet.lite.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class AudioWaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var waveformData: FloatArray = floatArrayOf()
    private var progress: Float = 0f
    private var selectionStart: Float = 0f
    private var selectionEnd: Float = 1f

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E0E0E0") // Light grey for background
        strokeWidth = 3f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#90CAF9") // Light blue for selected range
        strokeWidth = 3f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E88E5") // Darker blue for progress
        strokeWidth = 3f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    fun setWaveform(data: FloatArray) {
        waveformData = data
        invalidate()
    }

    fun setProgress(p: Float) {
        progress = p.coerceIn(0f, 1f)
        invalidate()
    }

    fun setSelection(start: Float, end: Float) {
        selectionStart = start.coerceIn(0f, 1f)
        selectionEnd = end.coerceIn(selectionStart, 1f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (waveformData.isEmpty()) return

        val w = width.toFloat()
        val h = height.toFloat()
        val centerY = h / 2
        val step = w / waveformData.size

        for (i in waveformData.indices) {
            val x = i * step
            val amplitude = (waveformData[i] * (h / 2)).coerceAtLeast(2f)
            val currentPosRatio = i.toFloat() / waveformData.size

            val paint = when {
                currentPosRatio <= progress -> progressPaint
                currentPosRatio >= selectionStart && currentPosRatio <= selectionEnd -> selectedPaint
                else -> basePaint
            }
            canvas.drawLine(x, centerY - amplitude, x, centerY + amplitude, paint)
        }
    }
}
