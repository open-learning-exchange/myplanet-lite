package org.ole.planet.myplanet.lite

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.graphics.withRotation

internal class EnterpriseTaskStickerLayout
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : FrameLayout(context, attrs) {
        var bodyColor: Int = 0
            set(value) {
                field = value
                invalidate()
            }
        var foldColor: Int = 0
            set(value) {
                field = value
                invalidate()
            }
        var foldVisible: Boolean = false
            set(value) {
                field = value
                invalidate()
            }
        var foldOnLeft: Boolean = true
            set(value) {
                field = value
                invalidate()
            }
        var tornBottom: Boolean = false
            set(value) {
                field = value
                invalidate()
            }
        var tapeVisible: Boolean = false
            set(value) {
                field = value
                invalidate()
            }
        var tapeOnLeft: Boolean = true
            set(value) {
                field = value
                invalidate()
            }
        var attachment: StickerAttachment = StickerAttachment.NONE
            set(value) {
                field = value
                invalidate()
            }

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val clearPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            }
        private val path = Path()
        private val foldSize = 34f * resources.displayMetrics.density
        private val bodyTop = 32f * resources.displayMetrics.density
        private val bodySideInset = 12f * resources.displayMetrics.density
        private val tapePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ContextCompat.getColor(context, R.color.enterprise_sticker_tape)
            }
        private val attachmentPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val pinDrawable = AppCompatResources.getDrawable(context, R.drawable.ic_enterprise_pin)

        init {
            setWillNotDraw(false)
        }

        override fun onDraw(canvas: Canvas) {
            val checkpoint = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
            paint.color = bodyColor
            canvas.drawRect(bodySideInset, bodyTop, width - bodySideInset, height.toFloat(), paint)
            if (foldVisible) {
                drawTransparentCorner(canvas)
            }
            if (tornBottom) drawTornBottom(canvas)
            if (foldVisible) drawFold(canvas)
            canvas.restoreToCount(checkpoint)
            super.onDraw(canvas)
        }

        override fun dispatchDraw(canvas: Canvas) {
            super.dispatchDraw(canvas)
            when {
                tapeVisible -> drawTape(canvas)
                attachment == StickerAttachment.PIN -> drawPin(canvas)
                attachment == StickerAttachment.CLIP -> drawClip(canvas)
            }
        }

        private fun drawTransparentCorner(canvas: Canvas) {
            path.reset()
            if (foldOnLeft) {
                path.moveTo(bodySideInset, height - foldSize)
                path.lineTo(bodySideInset, height.toFloat())
                path.lineTo(bodySideInset + foldSize, height.toFloat())
            } else {
                path.moveTo(width - bodySideInset, height - foldSize)
                path.lineTo(width - bodySideInset, height.toFloat())
                path.lineTo(width - bodySideInset - foldSize, height.toFloat())
            }
            path.close()
            canvas.drawPath(path, clearPaint)
        }

        private fun drawFold(canvas: Canvas) {
            paint.color = foldColor
            path.reset()
            if (foldOnLeft) {
                path.moveTo(bodySideInset, height - foldSize)
                path.lineTo(bodySideInset + foldSize, height - foldSize)
                path.lineTo(bodySideInset + foldSize, height.toFloat())
            } else {
                path.moveTo(width - bodySideInset, height - foldSize)
                path.lineTo(width - bodySideInset - foldSize, height - foldSize)
                path.lineTo(width - bodySideInset - foldSize, height.toFloat())
            }
            path.close()
            canvas.drawPath(path, paint)
        }

        private fun drawTornBottom(canvas: Canvas) {
            val density = resources.displayMetrics.density
            val segment = 12f * density
            val depths = floatArrayOf(3f, 8f, 5f, 10f, 4f, 7f, 2f, 9f)
            path.reset()
            path.moveTo(bodySideInset, height.toFloat())
            var x = bodySideInset
            var index = 0
            val right = width - bodySideInset
            while (x <= right) {
                val depth = depths[index % depths.size] * density
                path.lineTo(x.coerceAtMost(right), height - depth)
                x += segment
                index++
            }
            path.lineTo(right, height.toFloat())
            path.close()
            canvas.drawPath(path, clearPaint)
        }

        private fun drawTape(canvas: Canvas) {
            val density = resources.displayMetrics.density
            val tapeWidth = 76f * density
            val tapeHeight = 22f * density
            val centerX = if (tapeOnLeft) bodySideInset + 8f * density else width - bodySideInset - 8f * density
            val centerY = bodyTop
            canvas.withRotation(if (tapeOnLeft) -32f else 32f, centerX, centerY) {
                val left = centerX - tapeWidth / 2f
                val top = centerY - tapeHeight / 2f
                path.reset()
                path.moveTo(left + 3f * density, top)
                path.lineTo(left + tapeWidth - 2f * density, top + 2f * density)
                path.lineTo(left + tapeWidth, top + tapeHeight - 3f * density)
                path.lineTo(left + tapeWidth - 4f * density, top + tapeHeight)
                path.lineTo(left + 2f * density, top + tapeHeight - 2f * density)
                path.lineTo(left, top + 3f * density)
                path.close()
                drawPath(path, tapePaint)
            }
        }

        private fun drawPin(canvas: Canvas) {
            val density = resources.displayMetrics.density
            val pinWidth = 36f * density
            val pinHeight = 46f * density
            val left = ((width - pinWidth) / 2f).toInt()
            val top = (bodyTop - 22f * density).toInt()
            pinDrawable?.setBounds(left, top, (left + pinWidth).toInt(), (top + pinHeight).toInt())
            pinDrawable?.draw(canvas)
        }

        private fun drawClip(canvas: Canvas) {
            val density = resources.displayMetrics.density
            val centerX = width / 2f
            val top = 26f * density
            attachmentPaint.style = Paint.Style.STROKE
            attachmentPaint.strokeWidth = 2.2f * density
            attachmentPaint.strokeCap = Paint.Cap.ROUND
            attachmentPaint.strokeJoin = Paint.Join.ROUND
            attachmentPaint.color = ContextCompat.getColor(context, R.color.enterprise_clip_main)
            path.reset()
            path.moveTo(centerX - 5f * density, top + 5f * density)
            path.cubicTo(
                centerX - 6f * density,
                top,
                centerX + 5f * density,
                top - 1f * density,
                centerX + 6f * density,
                top + 6f * density,
            )
            path.lineTo(centerX + 9f * density, top + 31f * density)
            path.cubicTo(
                centerX + 10f * density,
                top + 42f * density,
                centerX - 5f * density,
                top + 45f * density,
                centerX - 7f * density,
                top + 34f * density,
            )
            path.lineTo(centerX - 9f * density, top + 15f * density)
            canvas.drawPath(path, attachmentPaint)
            attachmentPaint.color = ContextCompat.getColor(context, R.color.enterprise_clip_highlight)
            attachmentPaint.strokeWidth = 0.8f * density
            canvas.drawPath(path, attachmentPaint)
            attachmentPaint.style = Paint.Style.FILL
        }
    }
