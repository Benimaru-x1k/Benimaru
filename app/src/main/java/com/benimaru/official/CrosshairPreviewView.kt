package com.benimaru.official

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.GradientDrawable
import android.view.View


class CrosshairPreviewView(context: Context) : View(context) {
    private val painter = CrosshairPainter()

    init {
        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xFF1F2937.toInt(), 0xFF0B0F14.toInt())
        ).apply { cornerRadius = context.dp(18).toFloat() }
    }

    fun set(style: String, color: String, size: String) {
        painter.update(style, color, size)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        painter.draw(canvas, width / 2f, height / 2f)
    }
}
