package com.crazystudio.sportrecorder.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.crazystudio.sportrecorder.R
import com.crazystudio.sportrecorder.util.dpToPx

class CircleProgressBar(context: Context, attrs: AttributeSet) : View(context, attrs) {
    var progress = 25

    private val widthPx = context.dpToPx(20f)

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = widthPx
        color = ContextCompat.getColor(context, R.color.text_green)
    }

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = widthPx
        color = ContextCompat.getColor(context, R.color.bg_black2)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, widthMeasureSpec)
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        if (canvas == null) {
            return
        }

        val width = canvas.width
        val height = canvas.height
        canvas.drawCircle(width / 2f, height / 2f, width / 2 - widthPx, backgroundPaint)

        val rect = RectF(
            widthPx,
            widthPx,
            width.toFloat() - widthPx,
            height.toFloat() - widthPx)
        val angle = 360f * progress / 100
        canvas.drawArc(rect, -90f, angle, false, progressPaint)
    }
}