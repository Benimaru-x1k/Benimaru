package com.benimaru.official

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.View
import android.view.WindowManager

class CrosshairService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var crosshairView: CrosshairView

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        crosshairView = CrosshairView(this)

        val layoutFlag: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.TRANSLUCENT
        )


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }


        windowManager.addView(crosshairView, params)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            val style = it.getStringExtra("STYLE") ?: "Cross"
            val color = it.getStringExtra("COLOR") ?: "Red"
            val size = it.getStringExtra("SIZE") ?: "Medium"

            crosshairView.updateCrosshair(style, color, size)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::crosshairView.isInitialized) {
            windowManager.removeView(crosshairView)
        }
    }


    private inner class CrosshairView(context: Context) : View(context) {
        private var paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }

        private var style = "Cross"
        private var sizePx = 30f

        fun updateCrosshair(newStyle: String, newColor: String, newSize: String) {
            this.style = newStyle


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


            sizePx = when (newSize) {
                "Tiny" -> 15f
                "Small" -> 25f
                "Medium" -> 40f
                "Large" -> 60f
                "Extra Large" -> 90f
                else -> 40f
            }

            paint.strokeWidth = sizePx / 8f


            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val halfSize = sizePx / 2f

            when (style) {
                "Cross" -> {
                    canvas.drawLine(cx - halfSize, cy, cx + halfSize, cy, paint)
                    canvas.drawLine(cx, cy - halfSize, cx, cy + halfSize, paint)
                }
                "Dot" -> {
                    paint.style = Paint.Style.FILL
                    canvas.drawCircle(cx, cy, paint.strokeWidth * 1.5f, paint)
                    paint.style = Paint.Style.STROKE
                }
                "Circle" -> {
                    canvas.drawCircle(cx, cy, halfSize, paint)
                }
                "Cross with Circle" -> {
                    canvas.drawLine(cx - halfSize, cy, cx + halfSize, cy, paint)
                    canvas.drawLine(cx, cy - halfSize, cx, cy + halfSize, paint)
                    canvas.drawCircle(cx, cy, halfSize, paint)
                }
                "Square" -> {
                    canvas.drawRect(cx - halfSize, cy - halfSize, cx + halfSize, cy + halfSize, paint)
                }
                "Target" -> {
                    canvas.drawCircle(cx, cy, halfSize, paint)
                    paint.style = Paint.Style.FILL
                    canvas.drawCircle(cx, cy, paint.strokeWidth, paint)
                    paint.style = Paint.Style.STROKE
                }
            }
        }
    }
}