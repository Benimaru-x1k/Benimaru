package com.benimaru.official

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import kotlin.math.min
import kotlin.math.roundToInt


class RingGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val strokePx = 6f * density

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = strokePx
        color = ContextCompat.getColor(context, R.color.gaugeTrack)
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = strokePx
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.white)
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val colorGood = ContextCompat.getColor(context, R.color.gaugeGood)
    private val colorWarn = ContextCompat.getColor(context, R.color.gaugeWarn)
    private val colorBad = ContextCompat.getColor(context, R.color.gaugeBad)

    private val oval = RectF()
    private var shown = 0f
    private var target = -1
    private var animator: ValueAnimator? = null

    var invertColors: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    fun setProgress(percent: Int, animate: Boolean = true) {
        val clamped = percent.coerceIn(0, 100)
        if (clamped == target) return
        target = clamped
        animator?.cancel()
        if (!animate || !isAttachedToWindow) {
            shown = clamped.toFloat()
            invalidate()
            return
        }
        animator = ValueAnimator.ofFloat(shown, clamped.toFloat()).apply {
            duration = 450L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                shown = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun colorFor(level: Float): Int =
        if (invertColors) {
            when {
                level > 40f -> colorGood
                level > 20f -> colorWarn
                else -> colorBad
            }
        } else {
            when {
                level < 60f -> colorGood
                level < 85f -> colorWarn
                else -> colorBad
            }
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desired = (64f * density).roundToInt()
        setMeasuredDimension(
            resolveSize(desired, widthMeasureSpec),
            resolveSize(desired, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val diameter = min(w, h) - strokePx
        if (diameter <= 0f) return

        val left = (w - diameter) / 2f
        val top = (h - diameter) / 2f
        oval.set(left, top, left + diameter, top + diameter)

        canvas.drawArc(oval, START_ANGLE, SWEEP_ANGLE, false, trackPaint)

        val sweep = SWEEP_ANGLE * (shown / 100f)
        if (sweep > 0f) {
            arcPaint.color = colorFor(shown)
            canvas.drawArc(oval, START_ANGLE, sweep, false, arcPaint)
        }

        textPaint.textSize = diameter * 0.28f
        val baseline = h / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText("${shown.roundToInt()}%", w / 2f, baseline, textPaint)
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    private companion object {
        const val START_ANGLE = 135f
        const val SWEEP_ANGLE = 270f
    }
}
