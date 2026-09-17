package com.benimaru.official

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout

class CrosshairService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var crosshairView: FrameLayout

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val style = intent?.getStringExtra("STYLE") ?: "Cross"
        val colorName = intent?.getStringExtra("COLOR") ?: "Red"
        val sizeStr = intent?.getStringExtra("SIZE") ?: "Medium"

        val color = when (colorName) {
            "White" -> Color.WHITE
            "Black" -> Color.BLACK
            "Blue" -> Color.BLUE
            "Green" -> Color.GREEN
            else -> Color.RED
        }

        val scale = when (sizeStr) {
            "Small" -> 0.6f
            "Large" -> 1.5f
            else -> 1.0f // Medium
        }

        drawCrosshair(style, color, scale)
        return START_STICKY
    }

    private fun drawCrosshair(style: String, color: Int, scale: Float) {
        // Remove existing crosshair if we are just updating the style
        if (::crosshairView.isInitialized && crosshairView.isAttachedToWindow) {
            windowManager.removeView(crosshairView)
        }

        crosshairView = FrameLayout(this)

        val dotSize = (16 * scale).toInt()
        val lineThick = (4 * scale).toInt().coerceAtLeast(1)
        val lineLength = (40 * scale).toInt()
        val circleSize = (64 * scale).toInt()
        val strokeThick = (4 * scale).toInt().coerceAtLeast(1)

        when (style) {
            "Dot" -> {
                val dot = View(this).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(color)
                    }
                }
                crosshairView.addView(dot, FrameLayout.LayoutParams(dotSize, dotSize, Gravity.CENTER))
            }
            "Cross" -> {
                val vLine = View(this).apply { setBackgroundColor(color) }
                val hLine = View(this).apply { setBackgroundColor(color) }
                crosshairView.addView(vLine, FrameLayout.LayoutParams(lineThick, lineLength, Gravity.CENTER))
                crosshairView.addView(hLine, FrameLayout.LayoutParams(lineLength, lineThick, Gravity.CENTER))
            }
            "Cross with Circle" -> {
                val circle = View(this).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setStroke(strokeThick, color)
                        setColor(Color.TRANSPARENT)
                    }
                }
                val vLine = View(this).apply { setBackgroundColor(color) }
                val hLine = View(this).apply { setBackgroundColor(color) }

                crosshairView.addView(circle, FrameLayout.LayoutParams(circleSize, circleSize, Gravity.CENTER))
                crosshairView.addView(vLine, FrameLayout.LayoutParams(lineThick, lineLength, Gravity.CENTER))
                crosshairView.addView(hLine, FrameLayout.LayoutParams(lineLength, lineThick, Gravity.CENTER))
            }
        }

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER

        windowManager.addView(crosshairView, params)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::crosshairView.isInitialized && crosshairView.isAttachedToWindow) {
            windowManager.removeView(crosshairView)
        }
    }
}