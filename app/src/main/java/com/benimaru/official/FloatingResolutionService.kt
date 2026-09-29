package com.benimaru.official

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import kotlin.math.abs

class FloatingResolutionService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        // 1. Read the user's manual theme preference
        val prefs = getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)
        val isDarkModeSaved = prefs.getBoolean("isDarkMode", false)

        // 2. Clone the system configuration and force it into the chosen mode
        val config = android.content.res.Configuration(resources.configuration)
        val nightModeFlag = if (isDarkModeSaved) {
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        } else {
            android.content.res.Configuration.UI_MODE_NIGHT_NO
        }
        config.uiMode = nightModeFlag or (config.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK.inv())

        // 3. Apply the forced configuration, THEN wrap it in the Material Theme
        val localizedContext = createConfigurationContext(config)
        val themeContext = android.view.ContextThemeWrapper(localizedContext, R.style.Theme_Benimaru)

        floatingView = LayoutInflater.from(themeContext).inflate(R.layout.layout_floating_resolution, null)

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        layoutParams.gravity = Gravity.TOP or Gravity.START
        layoutParams.x = 0
        layoutParams.y = 200

        windowManager.addView(floatingView, layoutParams)
        setupInteractions(layoutParams)
    }

    private fun setupInteractions(params: WindowManager.LayoutParams) {
        val floatingIcon = floatingView.findViewById<ImageView>(R.id.floatingIcon)
        val menuContainer = floatingView.findViewById<View>(R.id.menuContainer)

        val etWidth = floatingView.findViewById<EditText>(R.id.etFloatWidth)
        val etHeight = floatingView.findViewById<EditText>(R.id.etFloatHeight)

        val btnApply = floatingView.findViewById<Button>(R.id.btnFloatApply)
        val btnReset = floatingView.findViewById<Button>(R.id.btnFloatReset)
        val btnMinimize = floatingView.findViewById<Button>(R.id.btnFloatMinimize)
        val btnKill = floatingView.findViewById<Button>(R.id.btnFloatKill)

        val metrics = resources.displayMetrics
        val currentWidth = metrics.widthPixels
        val currentHeight = metrics.heightPixels
        val currentDpi = metrics.densityDpi

        // Variables to calculate drag movement
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        // We explicitly declare the object to prevent Kotlin type mismatch errors
        val dragTouchListener = object : View.OnTouchListener {
            override fun onTouch(view: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDragging = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val moveX = event.rawX - initialTouchX
                        val moveY = event.rawY - initialTouchY

                        if (abs(moveX) > 10 || abs(moveY) > 10) {
                            isDragging = true
                            params.x = initialX + moveX.toInt()
                            params.y = initialY + moveY.toInt()
                            windowManager.updateViewLayout(floatingView, params)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!isDragging) {
                            if (view.id == R.id.floatingIcon) {
                                floatingIcon.visibility = View.GONE
                                menuContainer.visibility = View.VISIBLE

                                // Remove NOT_FOCUSABLE so we can type, but ADD NOT_TOUCH_MODAL so we can click outside
                                params.flags = (params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()) or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL

                                windowManager.updateViewLayout(floatingView, params)
                            }
                        }
                        return true
                    }
                    else -> return false
                }
            }
        }

        // Apply the drag listener to the Icon AND the entire Menu background
        floatingIcon.setOnTouchListener(dragTouchListener)
        menuContainer.setOnTouchListener(dragTouchListener)

        // --- Button Click Listeners ---

        btnMinimize.setOnClickListener {
            menuContainer.visibility = View.GONE
            floatingIcon.visibility = View.VISIBLE

            // Put NOT_FOCUSABLE back on to prevent the invisible menu from blocking the keyboard
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE

            windowManager.updateViewLayout(floatingView, params)
        }

        btnKill.setOnClickListener {
            stopSelf()
        }

        btnApply.setOnClickListener {
            val wStr = etWidth.text.toString()
            val hStr = etHeight.text.toString()
            if (wStr.isNotEmpty() && hStr.isNotEmpty()) {
                val input1 = wStr.toInt()
                val input2 = hStr.toInt()
                val newWidth = minOf(input1, input2)
                val newHeight = maxOf(input1, input2)

                val widthRatio = newWidth.toFloat() / currentWidth.toFloat()
                val heightRatio = newHeight.toFloat() / currentHeight.toFloat()
                val scalingRatio = minOf(widthRatio, heightRatio)

                val newDpi = (scalingRatio * currentDpi).toInt()
                val cmd = "wm size ${newWidth}x${newHeight} && wm density $newDpi"
                executeCommand(cmd, "Resolution Applied")
            }
        }

        btnReset.setOnClickListener {
            executeCommand("wm size reset && wm density reset", "Resolution Reset")
        }
    }

    private fun executeCommand(command: String, successMsg: String) {
        serviceScope.launch {
            try {
                val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
                if (process.waitFor() == 0) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@FloatingResolutionService, successMsg, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@FloatingResolutionService, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::floatingView.isInitialized) {
            windowManager.removeView(floatingView)
        }
    }
}