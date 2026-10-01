package com.benimaru.official

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import com.google.android.material.materialswitch.MaterialSwitch
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

        // Connect the switch from your floating layout
        val switchAutoMatch = floatingView.findViewById<MaterialSwitch>(R.id.switchFloatAutoMatch)

        val btnApply = floatingView.findViewById<Button>(R.id.btnFloatApply)
        val btnReset = floatingView.findViewById<Button>(R.id.btnFloatReset)
        val btnMinimize = floatingView.findViewById<Button>(R.id.btnFloatMinimize)
        val btnKill = floatingView.findViewById<Button>(R.id.btnFloatKill)

        val metrics = resources.displayMetrics

        // Use portrait bounds to establish consistent native aspect ratio
        val currentDpi = metrics.densityDpi
        val nativePortraitWidth = minOf(metrics.widthPixels, metrics.heightPixels).toFloat()
        val nativePortraitHeight = maxOf(metrics.widthPixels, metrics.heightPixels).toFloat()

        // --- Auto Match Aspect Ratio Logic ---
        var isAutoCalculating = false

        val widthWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (switchAutoMatch?.isChecked == true && !isAutoCalculating) {
                    val wStr = s.toString()
                    isAutoCalculating = true
                    if (wStr.isNotEmpty()) {
                        val w = wStr.toIntOrNull()
                        if (w != null) {
                            val calculatedHeight = (w * (nativePortraitHeight / nativePortraitWidth)).toInt()
                            etHeight.setText(calculatedHeight.toString())
                        }
                    } else {
                        etHeight.setText("")
                    }
                    isAutoCalculating = false
                }
            }
        }

        val heightWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (switchAutoMatch?.isChecked == true && !isAutoCalculating) {
                    val hStr = s.toString()
                    isAutoCalculating = true
                    if (hStr.isNotEmpty()) {
                        val h = hStr.toIntOrNull()
                        if (h != null) {
                            val calculatedWidth = (h * (nativePortraitWidth / nativePortraitHeight)).toInt()
                            etWidth.setText(calculatedWidth.toString())
                        }
                    } else {
                        etWidth.setText("")
                    }
                    isAutoCalculating = false
                }
            }
        }

        etWidth.addTextChangedListener(widthWatcher)
        etHeight.addTextChangedListener(heightWatcher)

        switchAutoMatch?.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !isAutoCalculating) {
                if (etHeight.text?.isNotEmpty() == true) {
                    val h = etHeight.text.toString().toIntOrNull()
                    if (h != null) {
                        isAutoCalculating = true
                        val w = (h * (nativePortraitWidth / nativePortraitHeight)).toInt()
                        etWidth.setText(w.toString())
                        isAutoCalculating = false
                    }
                } else if (etWidth.text?.isNotEmpty() == true) {
                    val w = etWidth.text.toString().toIntOrNull()
                    if (w != null) {
                        isAutoCalculating = true
                        val h = (w * (nativePortraitHeight / nativePortraitWidth)).toInt()
                        etHeight.setText(h.toString())
                        isAutoCalculating = false
                    }
                }
            }
        }
        // ------------------------------------

        // Variables to calculate drag movement
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

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

        floatingIcon.setOnTouchListener(dragTouchListener)
        menuContainer.setOnTouchListener(dragTouchListener)

        // --- Button Click Listeners ---

        btnMinimize.setOnClickListener {
            menuContainer.visibility = View.GONE
            floatingIcon.visibility = View.VISIBLE

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

                val widthRatio = newWidth.toFloat() / nativePortraitWidth
                val heightRatio = newHeight.toFloat() / nativePortraitHeight
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