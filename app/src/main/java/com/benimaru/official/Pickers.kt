package com.benimaru.official

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.google.android.material.button.MaterialButton


class SwatchView(context: Context, private val fillColor: Int) : View(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fillColor }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    var isPicked: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val diameter = minOf(width, height, context.dp(34)).toFloat()
        val ringWidth = (if (isPicked) context.dp(3) else context.dp(1)).toFloat()

        val radius = diameter / 2f - ringWidth / 2f
        val cx = width / 2f
        val cy = height / 2f

        canvas.drawCircle(cx, cy, radius, fill)
        ring.strokeWidth = ringWidth
        ring.color = if (isPicked) Ui.accent(context) else Ui.outline(context)
        canvas.drawCircle(cx, cy, radius, ring)
    }
}


internal fun colorSwatchRow(
    ctx: Context,
    colors: List<Int>,
    names: List<String>,
    initialIndex: Int,
    onPick: (Int) -> Unit
): LinearLayout {
    val row = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        clipChildren = false
        clipToPadding = false
    }
    val swatches = mutableListOf<SwatchView>()
    colors.forEachIndexed { i, color ->
        val swatch = SwatchView(ctx, color).apply {
            contentDescription = names[i]
            isPicked = i == initialIndex
            layoutParams = LinearLayout.LayoutParams(0, ctx.dp(44), 1f)
            setOnClickListener {
                swatches.forEachIndexed { j, s -> s.isPicked = j == i }
                onPick(i)
            }
        }
        swatches += swatch
        row.addView(swatch)
    }
    return row
}


internal fun presetRow(ctx: Context, labels: List<String>, onClick: (Int) -> Unit): LinearLayout {
    val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
    labels.forEachIndexed { i, label ->
        row.addView(
            MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = label
                isAllCaps = false
                textSize = 14f
                maxLines = 1
                isSingleLine = true
                cornerRadius = ctx.dp(14)
                insetTop = 0
                insetBottom = 0
                minWidth = 0
                minimumWidth = 0
                setPadding(ctx.dp(2), 0, ctx.dp(2), 0)
                layoutParams = LinearLayout.LayoutParams(0, ctx.dp(44), 1f).apply {
                    if (i > 0) marginStart = ctx.dp(8)
                }
                setOnClickListener { onClick(i) }
            }
        )
    }
    row.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    return row
}
