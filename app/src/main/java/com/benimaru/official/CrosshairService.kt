package com.benimaru.official

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
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

        // Programmatically build a simple red plus (+) crosshair
        crosshairView = FrameLayout(this).apply {
            val verticalLine = View(context).apply { setBackgroundColor(Color.RED) }
            val horizontalLine = View(context).apply { setBackgroundColor(Color.RED) }

            addView(verticalLine, FrameLayout.LayoutParams(4, 40, Gravity.CENTER))
            addView(horizontalLine, FrameLayout.LayoutParams(40, 4, Gravity.CENTER))
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
        if (::crosshairView.isInitialized) {
            windowManager.removeView(crosshairView)
        }
    }
}