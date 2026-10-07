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
import android.view.View
import android.view.WindowManager
import android.widget.ProgressBar
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
    private lateinit var pbRam: ProgressBar

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
        pbRam = overlayView.findViewById(R.id.pbOverlayRam)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = 50
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        windowManager.addView(overlayView, params)
        startMonitoring()
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

        val ramPercent = if (totalRamMb > 0) ((usedRamMb * 100) / totalRamMb).toInt() else 0
        val cpuTempC = cpuTemp.toIntOrNull()
        val tempColor = { t: Int? ->
            when {
                t == null -> "#E5E7EB"
                t >= 45 -> "#F87171"
                t >= 38 -> "#FBBF24"
                else -> "#4ADE80"
            }
        }
        val ramColor = when {
            ramPercent >= 85 -> "#F87171"
            ramPercent >= 70 -> "#FBBF24"
            else -> "#E5E7EB"
        }
        val batColor = when {
            level in 0..15 -> "#F87171"
            level in 16..30 -> "#FBBF24"
            else -> "#4ADE80"
        }
        val white = "#E5E7EB"

        withContext(Dispatchers.Main) {
            tvCpuTemp.text = formatHtml(span(cpuTemp, "°C", tempColor(cpuTempC)))
            tvCpuFreq.text = formatHtml(span(cpuFreq, "MHz", white))

            tvRamUsed.text = formatHtml(span(usedRamMb.toString(), "MB", ramColor))
            tvRamTotal.text = formatHtml(span(totalRamMb.toString(), "MB", white))
            pbRam.progress = ramPercent

            tvBatTemp.text = formatHtml(span(batTemp.toString(), "°C", tempColor(batTemp)))
            tvBatLevel.text = formatHtml(span(level.toString(), "%", batColor))


            tvDispFps.text = formatHtml(span(refreshRate.toString(), "Hz", "#F9A8D4"))
        }
    }

    private fun span(value: String, unit: String, color: String): String =
        "<font color='$color'>$value</font> <small><font color='#9CA3AF'>$unit</font></small>"

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