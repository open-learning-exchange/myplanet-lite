package org.ole.planet.myplanet.lite.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

class RecordingWaveformView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 4f
        alpha = 100
    }
    private val amplitudes = mutableListOf<Float>()
    private val maxSamples = 50

    fun addAmplitude(amplitude: Float) {
        amplitudes.add(amplitude)
        if (amplitudes.size > maxSamples) {
            amplitudes.removeAt(0)
        }
        invalidate()
    }

    fun clear() {
        amplitudes.clear()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (amplitudes.isEmpty()) return
        val w = width.toFloat()
        val h = height.toFloat()
        val centerY = h / 2
        val step = w / maxSamples
        val startX = w - (amplitudes.size * step)

        for (i in 0 until amplitudes.size - 1) {
            val x1 = startX + i * step
            val y1 = centerY - (amplitudes[i] * h / 2)
            val x2 = startX + (i + 1) * step
            val y2 = centerY - (amplitudes[i + 1] * h / 2)
            canvas.drawLine(x1, y1, x2, y2, paint)
            canvas.drawLine(x1, centerY + (centerY - y1), x2, centerY + (centerY - y2), paint)
        }
    }
}
