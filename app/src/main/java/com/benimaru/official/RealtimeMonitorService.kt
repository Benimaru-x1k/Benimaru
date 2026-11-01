package com.benimaru.official

import android.app.ActivityManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
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
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
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
        private const val MIN_SCALE = 0.6f
        private const val MAX_SCALE = 2.0f
        private const val EDIT_TIMEOUT_MS = 120_000L

        const val ACTION_EDIT = "com.benimaru.official.MONITOR_EDIT"
        const val ACTION_SIZE = "com.benimaru.official.MONITOR_SIZE"
        const val ACTION_ROWS = "com.benimaru.official.MONITOR_ROWS"
        const val ACTION_RESET = "com.benimaru.official.MONITOR_RESET"
        const val EXTRA_SCALE = "scale"
        const val ROWS_ALL = 63
    }

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var container: FrameLayout
    private lateinit var handleView: View
    private lateinit var doneChip: TextView
    private var editMode = false
    private val autoLock = Runnable {
        if (editMode) {
            setEditMode(false)
            Toast.makeText(this, getString(R.string.monitor_locked_auto), Toast.LENGTH_SHORT).show()
        }
    }
    private var scale = 1f
    private var hasSavedPosition = false
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

    private fun dp(v: Int) = (v * resources.displayMetrics.density + 0.5f).toInt()

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

        val prefs = getSharedPreferences(Prefs.MAIN, MODE_PRIVATE)

        val handle = TextView(this).apply {
            text = "◢"
            textSize = 15f
            setTextColor(Color.parseColor("#B3FFFFFF"))
            gravity = Gravity.END or Gravity.BOTTOM
            setPadding(dp(8), dp(8), dp(10), dp(4))
        }
        handle.visibility = View.GONE
        handleView = handle
        doneChip = TextView(this).apply {
            text = getString(R.string.monitor_done)
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.BLACK)
            setPadding(dp(14), dp(6), dp(14), dp(6))
            background = Ui.rounded(Ui.accent(this@RealtimeMonitorService), dp(16))
            visibility = View.GONE
            setOnClickListener { setEditMode(false) }
        }
        container = FrameLayout(this).apply {
            addView(overlayView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(handle, FrameLayout.LayoutParams(dp(40), dp(36), Gravity.END or Gravity.BOTTOM))
            addView(doneChip, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END))
        }
        collectBases(overlayView)

        scale = prefs.getFloat(Prefs.KEY_MONITOR_SCALE, 1f).coerceIn(MIN_SCALE, MAX_SCALE)
        applyScale(scale)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE

        val savedX = prefs.getInt(Prefs.KEY_MONITOR_X, -1)
        val savedY = prefs.getInt(Prefs.KEY_MONITOR_Y, -1)
        hasSavedPosition = savedX >= 0 && savedY >= 0

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (hasSavedPosition) savedX else 0
            y = if (hasSavedPosition) savedY else 50
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        setupTouch(handle)

        windowManager.addView(container, params)
        if (!hasSavedPosition) container.post { centerAtTop() } else container.post { clampToScreen() }
        applyRowVisibility()
        startMonitoring()
        running = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (::container.isInitialized) {
            when (intent?.action) {
                ACTION_EDIT -> setEditMode(true)
                ACTION_SIZE -> {
                    setScale(intent?.getFloatExtra(EXTRA_SCALE, 1f) ?: 1f)
                    container.post { savePlacement() }
                }
                ACTION_ROWS -> applyRowVisibility()
                ACTION_RESET -> resetPlacement()
            }
        }
        return START_STICKY
    }

    private fun setEditMode(on: Boolean) {
        if (editMode == on) return
        editMode = on
        val pad = dp(36)
        (overlayView.layoutParams as FrameLayout.LayoutParams).topMargin = if (on) pad else 0
        overlayView.requestLayout()
        params.y = if (on) (params.y - pad).coerceAtLeast(0) else params.y + pad
        handleView.visibility = if (on) View.VISIBLE else View.GONE
        doneChip.visibility = if (on) View.VISIBLE else View.GONE
        params.flags = if (on) params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv() or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        else params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL.inv()
        try { windowManager.updateViewLayout(container, params) } catch (_: Exception) {}
        container.removeCallbacks(autoLock)
        if (on) {
            container.postDelayed(autoLock, EDIT_TIMEOUT_MS)
            Toast.makeText(this, getString(R.string.toast_monitor_hint), Toast.LENGTH_LONG).show()
        } else {
            savePlacement()
            container.post { clampToScreen() }
        }
    }

    private fun applyRowVisibility() {
        val mask = getSharedPreferences(Prefs.MAIN, MODE_PRIVATE).getInt(Prefs.KEY_MONITOR_ROWS, ROWS_ALL)
        fun vis(bit: Int, vararg ids: Int) {
            ids.forEach { overlayView.findViewById<View>(it)?.visibility = if ((mask and bit) != 0) View.VISIBLE else View.GONE }
        }
        vis(1, R.id.rowCpu)
        vis(2, R.id.rowRam, R.id.rowRamBar)
        vis(4, R.id.rowSto, R.id.rowStoBar)
        vis(8, R.id.rowBat)
        vis(16, R.id.rowDisp)
        vis(32, R.id.rowNet)
        container.post { clampToScreen() }
    }


    private class Base(val textPx: Float, val pl: Int, val pt: Int, val pr: Int, val pb: Int, val h: Int)
    private val bases = LinkedHashMap<View, Base>()

    private fun collectBases(v: View) {
        bases[v] = Base(
            (v as? TextView)?.textSize ?: 0f,
            v.paddingLeft, v.paddingTop, v.paddingRight, v.paddingBottom,
            v.layoutParams?.height ?: 0
        )
        if (v is ViewGroup) for (i in 0 until v.childCount) collectBases(v.getChildAt(i))
    }

    private fun applyScale(sc: Float) {
        for ((v, b) in bases) {
            if (v is TextView && b.textPx > 0f) v.setTextSize(TypedValue.COMPLEX_UNIT_PX, b.textPx * sc)
            v.setPadding((b.pl * sc).toInt(), (b.pt * sc).toInt(), (b.pr * sc).toInt(), (b.pb * sc).toInt())
            if (v is ProgressBar && b.h > 0) {
                val lp = v.layoutParams
                lp.height = (b.h * sc).toInt().coerceAtLeast(2)
                v.layoutParams = lp
            }
        }
    }

    private fun setScale(newScale: Float) {
        scale = newScale.coerceIn(MIN_SCALE, MAX_SCALE)
        applyScale(scale)
        container.post { clampToScreen() }
    }

    @Suppress("DEPRECATION")
    private fun realMetrics(): DisplayMetrics {
        val dm = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(dm)
        return dm
    }

    private fun clampToScreen() {
        val dm = realMetrics()
        params.x = params.x.coerceIn(0, (dm.widthPixels - container.width).coerceAtLeast(0))
        params.y = params.y.coerceIn(0, (dm.heightPixels - container.height).coerceAtLeast(0))
        try { windowManager.updateViewLayout(container, params) } catch (_: Exception) {}
    }

    private fun centerAtTop() {
        val dm = realMetrics()
        params.x = ((dm.widthPixels - container.width) / 2).coerceAtLeast(0)
        params.y = 50
        try { windowManager.updateViewLayout(container, params) } catch (_: Exception) {}
    }

    private fun savePlacement() {
        getSharedPreferences(Prefs.MAIN, MODE_PRIVATE).edit()
            .putInt(Prefs.KEY_MONITOR_X, params.x)
            .putInt(Prefs.KEY_MONITOR_Y, params.y + if (editMode) dp(36) else 0)
            .putFloat(Prefs.KEY_MONITOR_SCALE, scale)
            .apply()
        hasSavedPosition = true
    }

    private fun resetPlacement() {
        getSharedPreferences(Prefs.MAIN, MODE_PRIVATE).edit()
            .remove(Prefs.KEY_MONITOR_X).remove(Prefs.KEY_MONITOR_Y).remove(Prefs.KEY_MONITOR_SCALE)
            .apply()
        hasSavedPosition = false
        scale = 1f
        applyScale(scale)
        container.post { centerAtTop() }
    }

    private fun setupTouch(handle: View) {
        val density = resources.displayMetrics.density

        val scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                setScale(scale * detector.scaleFactor)
                return true
            }
        })
        val gestures = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true
            override fun onDoubleTap(e: MotionEvent): Boolean {
                resetPlacement()
                return true
            }
        })

        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var ignoreDrag = false
        container.setOnTouchListener { _, e ->
            if (editMode) { container.removeCallbacks(autoLock); container.postDelayed(autoLock, EDIT_TIMEOUT_MS) }
            gestures.onTouchEvent(e)
            scaleDetector.onTouchEvent(e)
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startX = params.x; startY = params.y; ignoreDrag = false
                }
                MotionEvent.ACTION_POINTER_DOWN -> ignoreDrag = true
                MotionEvent.ACTION_MOVE -> if (!ignoreDrag && !scaleDetector.isInProgress && e.pointerCount == 1) {
                    val dm = realMetrics()
                    params.x = (startX + (e.rawX - downX).toInt()).coerceIn(0, (dm.widthPixels - container.width).coerceAtLeast(0))
                    params.y = (startY + (e.rawY - downY).toInt()).coerceIn(0, (dm.heightPixels - container.height).coerceAtLeast(0))
                    try { windowManager.updateViewLayout(container, params) } catch (_: Exception) {}
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> savePlacement()
            }
            true
        }

        var hDownX = 0f; var hDownY = 0f; var hStartScale = 1f
        handle.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { hDownX = e.rawX; hDownY = e.rawY; hStartScale = scale }
                MotionEvent.ACTION_MOVE -> {
                    val delta = ((e.rawX - hDownX) + (e.rawY - hDownY)) / (160f * density)
                    setScale(hStartScale + delta)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> savePlacement()
            }
            true
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
        if (::container.isInitialized) {
            try { windowManager.removeView(container) } catch (_: Exception) {}
        }
    }
}