package com.benimaru.official

import android.app.ActivityManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.StatFs
import android.os.SystemClock
import android.text.Html
import android.view.Display
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ProgressBar
import android.widget.TextView
import kotlinx.coroutines.*
import java.io.File
import java.io.RandomAccessFile

class RealtimeMonitorService : Service() {

    override fun getResources(): android.content.res.Resources = AppTranslator.wrap(super.getResources())

    companion object {
        @Volatile var running = false
    }

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
    private lateinit var tvStoUsed: TextView
    private lateinit var tvStoTotal: TextView
    private lateinit var pbStorage: ProgressBar
    private lateinit var tvNetDown: TextView
    private lateinit var tvNetUp: TextView

    private var lastRxBytes = 0L
    private var lastTxBytes = 0L
    private var lastNetSampleAt = 0L

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
        tvStoUsed = overlayView.findViewById(R.id.tvStoUsed)
        tvStoTotal = overlayView.findViewById(R.id.tvStoTotal)
        pbStorage = overlayView.findViewById(R.id.pbOverlayStorage)
        tvNetDown = overlayView.findViewById(R.id.tvNetDown)
        tvNetUp = overlayView.findViewById(R.id.tvNetUp)

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
        running = true
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
        val cpuTemp = readCpuTempC(fallback = batTemp.toString())

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

        val storage = StatFs(Environment.getDataDirectory().path)
        val gb = 1024.0 * 1024.0 * 1024.0
        val totalStorageGb = storage.totalBytes / gb
        val usedStorageGb = (storage.totalBytes - storage.availableBytes) / gb
        val storagePercent = if (storage.totalBytes > 0) (((storage.totalBytes - storage.availableBytes) * 100) / storage.totalBytes).toInt() else 0
        val storageColor = when {
            storagePercent >= 90 -> "#F87171"
            storagePercent >= 80 -> "#FBBF24"
            else -> "#E5E7EB"
        }

        val net = sampleNetworkSpeed()

        withContext(Dispatchers.Main) {
            tvCpuTemp.text = formatHtml(span(cpuTemp, "°C", tempColor(cpuTempC)))
            tvCpuFreq.text = formatHtml(span(cpuFreq, "MHz", white))

            tvRamUsed.text = formatHtml(span(usedRamMb.toString(), "MB", ramColor))
            tvRamTotal.text = formatHtml(span(totalRamMb.toString(), "MB", white))
            pbRam.progress = ramPercent

            tvBatTemp.text = formatHtml(span(batTemp.toString(), "°C", tempColor(batTemp)))
            tvBatLevel.text = formatHtml(span(level.toString(), "%", batColor))


            tvDispFps.text = formatHtml(span(refreshRate.toString(), "Hz", "#F9A8D4"))

            tvStoUsed.text = formatHtml(span(String.format("%.1f", usedStorageGb), "GB", storageColor))
            tvStoTotal.text = formatHtml(span(String.format("%.0f", totalStorageGb), "GB", white))
            pbStorage.progress = storagePercent

            tvNetDown.text = formatHtml(span("↓ " + net.first.first, net.first.second, "#4ADE80"))
            tvNetUp.text = formatHtml(span("↑ " + net.second.first, net.second.second, "#F9A8D4"))
        }
    }

    private fun sampleNetworkSpeed(): Pair<Pair<String, String>, Pair<String, String>> {
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        if (rx < 0 || tx < 0) return ("--" to "KB/s") to ("--" to "KB/s")

        val now = SystemClock.elapsedRealtime()
        val elapsed = now - lastNetSampleAt
        val down: Long
        val up: Long
        if (lastNetSampleAt == 0L || elapsed <= 0L) {
            down = 0L
            up = 0L
        } else {
            down = ((rx - lastRxBytes) * 1000L / elapsed).coerceAtLeast(0L)
            up = ((tx - lastTxBytes) * 1000L / elapsed).coerceAtLeast(0L)
        }
        lastRxBytes = rx
        lastTxBytes = tx
        lastNetSampleAt = now
        return formatSpeed(down) to formatSpeed(up)
    }

    private fun formatSpeed(bytesPerSec: Long): Pair<String, String> =
        if (bytesPerSec >= 1024L * 1024L) String.format("%.1f", bytesPerSec / (1024.0 * 1024.0)) to "MB/s"
        else (bytesPerSec / 1024L).toString() to "KB/s"

    private fun span(value: String, unit: String, color: String): String =
        "<font color='$color'>$value</font> <small><font color='#9CA3AF'>$unit</font></small>"

    private fun getSysFileValue(path: String, divisor: Long = 1, fallback: String = "--"): String {
        return try {
            RandomAccessFile(path, "r").use { it.readLine().trim().toLong() / divisor }.toString()
        } catch (e: Exception) {
            fallback
        }
    }

    private var cpuTempPath: String? = null
    private var cpuTempPathResolved = false

    private fun resolveCpuTempPath(): String {
        for (i in 0..24) {
            val zone = "/sys/class/thermal/thermal_zone$i"
            val type = try {
                File("$zone/type").readText().trim().lowercase()
            } catch (e: Exception) {
                break
            }
            if (type.contains("cpu") || type.contains("soc") || type.contains("cluster")) return "$zone/temp"
        }
        return "/sys/class/thermal/thermal_zone0/temp"
    }

    private fun readCpuTempC(fallback: String): String {
        if (!cpuTempPathResolved) {
            cpuTempPath = resolveCpuTempPath()
            cpuTempPathResolved = true
        }
        val raw = getSysFileValue(cpuTempPath ?: return fallback, divisor = 1, fallback = "").toLongOrNull() ?: return fallback
        val celsius = if (raw >= 1000) raw / 1000 else raw
        return if (celsius in 1..150) celsius.toString() else fallback
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
        serviceJob.cancel()
        if (::overlayView.isInitialized) {
            try { windowManager.removeView(overlayView) } catch (_: Exception) {}
        }
    }
}