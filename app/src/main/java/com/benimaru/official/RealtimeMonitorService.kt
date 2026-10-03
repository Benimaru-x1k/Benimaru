package com.benimaru.official

import android.app.ActivityManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.text.Html
import android.view.Display
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import kotlinx.coroutines.*
import java.io.RandomAccessFile

class RealtimeMonitorService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var params: WindowManager.LayoutParams
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private lateinit var activityManager: ActivityManager


    private lateinit var tvCpuTemp: TextView
    private lateinit var tvCpuFreq: TextView
    private lateinit var tvRamUsed: TextView
    private lateinit var tvRamTotal: TextView
    private lateinit var tvBatTemp: TextView
    private lateinit var tvBatLevel: TextView
    private lateinit var tvDispFps: TextView

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager


        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_monitor, null)


        tvCpuTemp = overlayView.findViewById(R.id.tvCpuTemp)
        tvCpuFreq = overlayView.findViewById(R.id.tvCpuFreq)
        tvRamUsed = overlayView.findViewById(R.id.tvRamUsed)
        tvRamTotal = overlayView.findViewById(R.id.tvRamTotal)
        tvBatTemp = overlayView.findViewById(R.id.tvBatTemp)
        tvBatLevel = overlayView.findViewById(R.id.tvBatLevel)
        tvDispFps = overlayView.findViewById(R.id.tvDispFps)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 200
        }

        setupDraggable(overlayView)
        windowManager.addView(overlayView, params)
        startMonitoring()
    }

    private fun setupDraggable(view: View) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(view, params)
                    true
                }
                else -> false
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun formatHtml(html: String): CharSequence {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
        } else {
            Html.fromHtml(html)
        }
    }

    private fun startMonitoring() {
        serviceScope.launch {
            while (isActive) {
                updateHardwareStats()
                delay(1000)
            }
        }
    }

    private suspend fun updateHardwareStats() = withContext(Dispatchers.IO) {

        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val usedRamMb = (memInfo.totalMem - memInfo.availMem) / (1024 * 1024)
        val totalRamMb = memInfo.totalMem / (1024 * 1024)


        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = registerReceiver(null, intentFilter)
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val batTemp = (batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10


        val cpuFreq = getSysFileValue("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq", divisor = 1000, fallback = "--")
        val cpuTemp = getSysFileValue("/sys/class/thermal/thermal_zone0/temp", divisor = 1000, fallback = batTemp.toString())


        val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        @Suppress("DEPRECATION")
        val refreshRate = displayManager.getDisplay(Display.DEFAULT_DISPLAY)?.refreshRate?.toInt() ?: 60


        withContext(Dispatchers.Main) {

            tvCpuTemp.text = formatHtml("<font color='#FFAA00'>$cpuTemp</font> <small><font color='#FFAA00'>°C</font></small>")
            tvCpuFreq.text = formatHtml("<font color='#FFAA00'>$cpuFreq</font> <small><font color='#FFAA00'>MHz</font></small>")

            tvRamUsed.text = formatHtml("<font color='#FFAA00'>$usedRamMb</font> <small><font color='#FFAA00'>MB</font></small>")
            tvRamTotal.text = formatHtml("<font color='#FFAA00'>$totalRamMb</font> <small><font color='#FFAA00'>MB</font></small>")

            tvBatTemp.text = formatHtml("<font color='#FFAA00'>$batTemp</font> <small><font color='#FFAA00'>°C</font></small>")
            tvBatLevel.text = formatHtml("<font color='#FFAA00'>$level</font> <small><font color='#FFAA00'>%</font></small>")

            tvDispFps.text = formatHtml("<font color='#FFFFFF'>$refreshRate</font> <small><font color='#FFFFFF'>FPS</font></small>")
        }
    }

    private fun getSysFileValue(path: String, divisor: Long = 1, fallback: String = "--"): String {
        return try {
            val reader = RandomAccessFile(path, "r")
            val value = reader.readLine().toLong() / divisor
            reader.close()
            value.toString()
        } catch (e: Exception) {
            fallback
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
        if (::overlayView.isInitialized) {
            windowManager.removeView(overlayView)
        }
    }
}