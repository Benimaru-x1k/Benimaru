package com.benimaru.official

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path

class CrosshairPainter {
    private val paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val outline = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.argb(150, 0, 0, 0)
    }

    private var style = "Cross"
    private var sizePx = 40f

    fun update(newStyle: String, newColor: String, newSize: String) {
        style = newStyle
        paint.color = when (newColor) {
            "White" -> Color.WHITE
            "Black" -> Color.BLACK
            "Red" -> Color.RED
            "Green" -> Color.GREEN
            "Blue" -> Color.BLUE
            "Yellow" -> Color.YELLOW
            "Cyan" -> Color.CYAN
            "Magenta" -> Color.MAGENTA
            else -> Color.RED
        }

        outline.color = if (newColor == "Black") Color.argb(170, 255, 255, 255) else Color.argb(150, 0, 0, 0)
        sizePx = when (newSize) {
            "Tiny" -> 15f
            "Small" -> 25f
            "Medium" -> 40f
            "Large" -> 60f
            "Extra Large" -> 90f
            else -> 40f
        }
        paint.strokeWidth = sizePx / 8f
        outline.strokeWidth = paint.strokeWidth + 2.5f
    }

    fun draw(canvas: Canvas, cx: Float, cy: Float) {
        drawShape(canvas, cx, cy, outline)
        drawShape(canvas, cx, cy, paint)
    }

    private fun drawShape(canvas: Canvas, cx: Float, cy: Float, p: Paint) {
        val half = sizePx / 2f
        val dotRadius = paint.strokeWidth * 1.5f + (if (p === outline) 1.25f else 0f)
        val targetRadius = paint.strokeWidth + (if (p === outline) 1.25f else 0f)

        fun fill(radius: Float) {
            val old = p.style
            p.style = Paint.Style.FILL
            canvas.drawCircle(cx, cy, radius, p)
            p.style = old
        }

        when (style) {
            "Cross" -> {
                canvas.drawLine(cx - half, cy, cx + half, cy, p)
                canvas.drawLine(cx, cy - half, cx, cy + half, p)
            }
            "Dot" -> fill(dotRadius)
            "Circle" -> canvas.drawCircle(cx, cy, half, p)
            "Cross with Circle" -> {
                canvas.drawLine(cx - half, cy, cx + half, cy, p)
                canvas.drawLine(cx, cy - half, cx, cy + half, p)
                canvas.drawCircle(cx, cy, half, p)
            }
            "Square" -> canvas.drawRect(cx - half, cy - half, cx + half, cy + half, p)
            "Target" -> {
                canvas.drawCircle(cx, cy, half, p)
                fill(targetRadius)
            }
            "Gap Cross" -> {
                val gap = half * 0.35f
                canvas.drawLine(cx - half, cy, cx - gap, cy, p)
                canvas.drawLine(cx + gap, cy, cx + half, cy, p)
                canvas.drawLine(cx, cy - half, cx, cy - gap, p)
                canvas.drawLine(cx, cy + gap, cx, cy + half, p)
            }
            "Diamond" -> {
                val path = Path().apply {
                    moveTo(cx, cy - half); lineTo(cx + half, cy)
                    lineTo(cx, cy + half); lineTo(cx - half, cy); close()
                }
                canvas.drawPath(path, p)
            }
            "Triangle" -> {
                val path = Path().apply {
                    moveTo(cx, cy - half); lineTo(cx + half, cy + half)
                    lineTo(cx - half, cy + half); close()
                }
                canvas.drawPath(path, p)
            }
        }
    }
}
