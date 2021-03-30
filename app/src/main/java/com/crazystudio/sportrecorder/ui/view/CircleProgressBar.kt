package com.crazystudio.sportrecorder.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.crazystudio.sportrecorder.R
import com.crazystudio.sportrecorder.util.dpToPx
import kotlin.math.cos
import kotlin.math.sin

class CircleProgressBar(context: Context, attrs: AttributeSet) : View(context, attrs) {
    var progress = 50.0

    private val widthPx = context.dpToPx(20f)

    private val rect by lazy {
        RectF(
            widthPx,
            widthPx,
            width.toFloat() - widthPx,
            width.toFloat() - widthPx
        )
    }
    var shader: Shader? = null

    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = widthPx
    }

    private val progressStartPointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.dark_green)
    }

    private val progressEndPointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.light_green)
    }

    private val progressTipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL_AND_STROKE
        color = ContextCompat.getColor(context, R.color.grey_1)
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
        if (shader == null) {
            shader = SweepGradient(
                width / 2f,
                width / 2f,
                intArrayOf(
                    ContextCompat.getColor(context, R.color.dark_green),
                    ContextCompat.getColor(context, R.color.light_green)
                ),
                floatArrayOf(0f, 1f)
            ).apply {
                setLocalMatrix(Matrix().apply {
                    setRotate(-90f, width / 2f, width / 2f)
                })
            }

            progressPaint.shader = shader
            progressEndPointPaint.shader = shader
        }

        if (progress > 99) {
            canvas.drawCircle(width / 2f, width / 2f, width / 2 - widthPx, progressPaint)
        } else {
            val angle = Math.toDegrees(Math.PI * 2) * progress / 100
            canvas.drawCircle(width / 2f, width / 2f, width / 2 - widthPx, backgroundPaint)
            canvas.drawArc(rect, -90f, angle.toFloat(), false, progressPaint)

            canvas.drawCircle(
                (width / 2f + (width-widthPx*2) / 2f * cos(Math.toRadians(angle-90.0))).toFloat(),
                (width / 2f + (width-widthPx*2) / 2f * sin(Math.toRadians(angle-90.0))).toFloat(),
                widthPx / 2f,
                progressEndPointPaint)

            canvas.drawCircle(
                (width / 2f),
                (width / 2f + (width-widthPx*2) / -2f),
                widthPx / 2f,
                progressStartPointPaint)

            canvas.drawCircle(
                (width / 2f + (width - widthPx * 2) / 2f * cos(Math.toRadians(angle - 90.0))).toFloat(),
                (width / 2f + (width - widthPx * 2) / 2f * sin(Math.toRadians(angle - 90.0))).toFloat(),
                widthPx / 3.25f,
                progressTipPaint)
        }


    }
}