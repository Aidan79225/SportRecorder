package com.crazystudio.sportrecorder.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.crazystudio.sportrecorder.R
import com.crazystudio.sportrecorder.util.dpToPx
import com.crazystudio.sportrecorder.util.spToPx

class VerticalProgressBar(context: Context, attrs: AttributeSet) : View(context, attrs) {
    private val widthPx = context.dpToPx(20f)
    var progress = 0.0f
        set(value) {
            field = value
            invalidate()
        }

    var shader: Shader? = null

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = widthPx
    }

    private val progressPointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = widthPx
        color = ContextCompat.getColor(context, R.color.bg_black2)
    }

    private val backgroundPointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.bg_black2)
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        textSize = context.dpToPx(12f)
        color = ContextCompat.getColor(context, R.color.white)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (shader == null) {
            shader = LinearGradient(

                width/2f, 0f, width/2f, height.toFloat(),
                intArrayOf(
                    ContextCompat.getColor(context, R.color.light_green),
                    ContextCompat.getColor(context, R.color.dark_green)
                ),
                floatArrayOf(0f, 1f),
                Shader.TileMode.REPEAT
            )

            progressPaint.shader = shader
            progressPointPaint.shader = shader
        }

        if (progress > 0.0) {
            canvas.drawLine(width/2f, widthPx/2, width/2f, height.toFloat()-widthPx/2, backgroundPaint)
            canvas.drawCircle(width/2f, height.toFloat()-widthPx/2, widthPx/2f, backgroundPointPaint)
            canvas.drawCircle(width/2f, widthPx/2, widthPx/2f, backgroundPointPaint)
            canvas.drawCircle(width/2f, height.toFloat()-widthPx/2, widthPx/2f, progressPointPaint)
            canvas.drawLine(width/2f, height.toFloat()-widthPx/2, width/2f, height.toFloat()-((height.toFloat()-widthPx/2)*progress), progressPaint)
            canvas.drawCircle(width/2f, height.toFloat()-((height.toFloat()-widthPx/2)*progress), widthPx/2f, progressPointPaint)
        } else {
            canvas.drawLine(width/2f, widthPx/2, width/2f, height.toFloat()-widthPx/2, backgroundPaint)
            canvas.drawCircle(width/2f, height.toFloat()-widthPx/2, widthPx/2f, backgroundPointPaint)
            canvas.drawCircle(width/2f, widthPx/2, widthPx/2f, backgroundPointPaint)
        }
    }

}