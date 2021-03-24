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
    var progress = 20

    private val widthPx = context.dpToPx(20f)

    val rect by lazy {
        RectF(
            widthPx,
            widthPx,
            width.toFloat() - widthPx,
            width.toFloat() - widthPx
        )
    }

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

        canvas.drawCircle(width / 2f, width / 2f, width / 2 - widthPx, backgroundPaint)

        val angle = 360f * progress / 100
        canvas.drawArc(rect, -90f, angle, false, progressPaint)
    }
}