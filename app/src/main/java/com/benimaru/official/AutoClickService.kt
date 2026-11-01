package com.benimaru.official

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.*
import rikka.shizuku.Shizuku


class AutoClickService : Service() {

    override fun getResources(): android.content.res.Resources = AppTranslator.wrap(super.getResources())

    companion object {
        const val PREFS = Prefs.MAIN
        const val KEY_MODE = "AutoMode"
        const val KEY_INTERVAL = "AutoIntervalSec"
        const val KEY_X = "AutoX"
        const val KEY_Y = "AutoY"
        const val KEY_SWIPE_LEN = "AutoSwipeLenPx"
        const val KEY_ENABLED = "AutoClickEnabled"
        const val KEY_INTERVAL_MAX = "AutoIntervalMaxSec"
        const val KEY_JITTER = "AutoJitterPx"
        const val KEY_HOLD = "AutoHoldMs"

        const val DEFAULT_JITTER_PX = 4
        const val DEFAULT_HOLD_MS = 40

        const val MODE_TAP = 0
        const val MODE_SWIPE = 1
        const val MODE_BOTH = 2

        @Volatile var running = false

        fun intervalLabel(prefs: SharedPreferences): String {
            val min = prefs.getFloat(KEY_INTERVAL, 5f)
            val max = prefs.getFloat(KEY_INTERVAL_MAX, 0f)
            return if (max > min) "$min–$max" else min.toString()
        }

        fun modeLabel(ctx: android.content.Context, mode: Int): String = ctx.getString(
            when (mode) {
                MODE_SWIPE -> R.string.mode_swipe
                MODE_BOTH -> R.string.mode_both
                else -> R.string.mode_click
            }
        )
    }

    private lateinit var windowManager: WindowManager
    private lateinit var root: LinearLayout
    private lateinit var btnToggle: TextView
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var marker: TargetView
    private lateinit var markerParams: WindowManager.LayoutParams
    private var markerAdded = false
    private var markerSize = 0
    private var markerOffX = 0
    private var markerOffY = 0

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)
    private var loop: Job? = null
    private var viewAdded = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        buildOverlay()
        buildMarker()
        running = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (markerAdded) placeMarkerFromPrefs()
        return START_STICKY
    }

    private fun buildOverlay() {
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density + 0.5f).toInt()

        btnToggle = TextView(this).apply {
            text = "▶"
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(14), dp(8), dp(10), dp(8))
        }
        val btnClose = TextView(this).apply {
            text = "✕"
            textSize = 16f
            setTextColor(Color.parseColor("#CCFFFFFF"))
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(8), dp(14), dp(8))
            setOnClickListener {
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, false).apply()
                stopSelf()
            }
        }

        root = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(28).toFloat()
                setColor(Color.parseColor("#E6202124"))
                setStroke(dp(1), Color.parseColor("#55FFFFFF"))
            }
            addView(btnToggle)
            addView(btnClose)
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(12)
            y = dp(120)
        }


        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var moved = false
        btnToggle.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startX = params.x; startY = params.y; moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - downX).toInt(); val dy = (e.rawY - downY).toInt()
                    if (Math.abs(dx) > dp(6) || Math.abs(dy) > dp(6)) moved = true
                    if (moved) {
                        params.x = startX + dx
                        params.y = startY + dy
                        windowManager.updateViewLayout(root, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) toggleRunning()
                    true
                }
                else -> false
            }
        }

        windowManager.addView(root, params)
        viewAdded = true
    }

    private class TargetView(ctx: Context, private val accent: Int) : View(ctx) {
        private val d = ctx.resources.displayMetrics.density
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(70, Color.red(accent), Color.green(accent), Color.blue(accent))
        }
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = accent
            strokeWidth = 3f * d
        }
        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.WHITE
            strokeWidth = 1.5f * d
        }
        private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

        override fun onDraw(canvas: Canvas) {
            val cx = width / 2f
            val cy = height / 2f
            val r = minOf(cx, cy) - ringPaint.strokeWidth
            canvas.drawCircle(cx, cy, r, fillPaint)
            canvas.drawCircle(cx, cy, r, ringPaint)
            canvas.drawLine(cx - r * 0.55f, cy, cx + r * 0.55f, cy, linePaint)
            canvas.drawLine(cx, cy - r * 0.55f, cx, cy + r * 0.55f, linePaint)
            canvas.drawCircle(cx, cy, 3f * d, dotPaint)
        }
    }

    private fun buildMarker() {
        markerSize = (56 * resources.displayMetrics.density + 0.5f).toInt()
        marker = TargetView(this, Ui.accent(this))
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        markerParams = WindowManager.LayoutParams(
            markerSize, markerSize, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val dm = realMetrics()
        val cx = prefs.getInt(KEY_X, -1).let { if (it < 0) dm.widthPixels / 2 else it.coerceIn(0, dm.widthPixels - 1) }
        val cy = prefs.getInt(KEY_Y, -1).let { if (it < 0) dm.heightPixels / 2 else it.coerceIn(0, dm.heightPixels - 1) }
        markerParams.x = cx - markerSize / 2
        markerParams.y = cy - markerSize / 2

        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0
        marker.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startX = markerParams.x; startY = markerParams.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val m = realMetrics()
                    markerParams.x = (startX + (e.rawX - downX).toInt()).coerceIn(-markerOffX - markerSize / 2, m.widthPixels - markerOffX - markerSize / 2)
                    markerParams.y = (startY + (e.rawY - downY).toInt()).coerceIn(-markerOffY - markerSize / 2, m.heightPixels - markerOffY - markerSize / 2)
                    windowManager.updateViewLayout(marker, markerParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    saveMarkerCenter()
                    true
                }
                else -> false
            }
        }

        windowManager.addView(marker, markerParams)
        markerAdded = true
        marker.post {
            val loc = IntArray(2)
            marker.getLocationOnScreen(loc)
            markerOffX = loc[0] - markerParams.x
            markerOffY = loc[1] - markerParams.y
            if (markerOffX != 0 || markerOffY != 0) {
                markerParams.x -= markerOffX
                markerParams.y -= markerOffY
                windowManager.updateViewLayout(marker, markerParams)
            }
            saveMarkerCenter()
        }
    }

    private fun placeMarkerFromPrefs() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val dm = realMetrics()
        val cx = prefs.getInt(KEY_X, -1).let { if (it < 0) dm.widthPixels / 2 else it.coerceIn(0, dm.widthPixels - 1) }
        val cy = prefs.getInt(KEY_Y, -1).let { if (it < 0) dm.heightPixels / 2 else it.coerceIn(0, dm.heightPixels - 1) }
        markerParams.x = cx - markerSize / 2 - markerOffX
        markerParams.y = cy - markerSize / 2 - markerOffY
        try { windowManager.updateViewLayout(marker, markerParams) } catch (_: Exception) {}
    }

    private fun saveMarkerCenter() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putInt(KEY_X, markerParams.x + markerOffX + markerSize / 2)
            .putInt(KEY_Y, markerParams.y + markerOffY + markerSize / 2)
            .apply()
    }

    private fun setMarkerActive(active: Boolean) {
        if (!markerAdded) return
        markerParams.flags = if (active)
            markerParams.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        else
            markerParams.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        marker.alpha = if (active) 0.35f else 1f
        try { windowManager.updateViewLayout(marker, markerParams) } catch (_: Exception) {}
    }

    private fun toggleRunning() {
        if (loop?.isActive == true) stopLoop() else startLoop()
    }

    private class DaemonLink(private val proc: Process, private val out: java.io.OutputStream) {
        @Volatile var alive = true

        fun send(line: String): Boolean {
            if (!alive) return false
            return try {
                out.write((line + "\n").toByteArray())
                out.flush()
                true
            } catch (_: Throwable) {
                alive = false
                false
            }
        }

        fun close() {
            alive = false
            try { out.write("Q\n".toByteArray()); out.flush() } catch (_: Throwable) {}
            try { out.close() } catch (_: Throwable) {}
            try { proc.destroy() } catch (_: Throwable) {}
        }
    }

    private suspend fun startDaemon(): DaemonLink? = withContext(Dispatchers.IO) {
        var proc: Process? = null
        try {
            val apk = applicationInfo.sourceDir
            val cmd = "CLASSPATH='$apk' exec app_process /system/bin --nice-name=benimaru_touch " +
                "com.benimaru.official.TouchDaemon 2>/dev/null"
            val p = Shizuku.newProcess(arrayOf("sh", "-c", cmd), null, null)
            proc = p
            val ready = java.util.concurrent.atomic.AtomicBoolean(false)
            val dog = launch { delay(6000); if (!ready.get()) { try { p.destroy() } catch (_: Throwable) {} } }
            var ok = false
            try {
                val reader = p.inputStream.bufferedReader()
                var tries = 0
                while (tries++ < 5) {
                    val line = reader.readLine() ?: break
                    if (line.trim() == "READY") { ok = true; break }
                    if (line.trim() == "FAIL") break
                }
            } catch (_: Throwable) {
            }
            ready.set(true)
            dog.cancel()
            if (ok) DaemonLink(p, p.outputStream) else {
                try { p.destroy() } catch (_: Throwable) {}
                null
            }
        } catch (_: Throwable) {
            try { proc?.destroy() } catch (_: Throwable) {}
            null
        }
    }

    private fun startLoop() {
        val ready = try {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (e: Throwable) { false }
        if (!ready) {
            Toast.makeText(this, getString(R.string.svc_shizuku_missing), Toast.LENGTH_LONG).show()
            return
        }

        btnToggle.text = "■"
        setMarkerActive(true)
        loop = scope.launch(Dispatchers.IO) {
            val daemon = startDaemon()
            try {
                if (daemon == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@AutoClickService, getString(R.string.svc_touch_fallback), Toast.LENGTH_LONG).show()
                    }
                }
                val rnd = java.util.Random()
                var swipeTurn = false
                var nextAt = SystemClock.elapsedRealtime()
                while (isActive) {
                    val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
                    val mode = prefs.getInt(KEY_MODE, MODE_TAP)
                    val minMs = (prefs.getFloat(KEY_INTERVAL, 5f).coerceIn(0.05f, 3600f) * 1000).toLong()
                    val maxMs = (prefs.getFloat(KEY_INTERVAL_MAX, 0f).coerceIn(0f, 3600f) * 1000).toLong()
                    val jitter = prefs.getInt(KEY_JITTER, DEFAULT_JITTER_PX).coerceIn(0, 50)
                    val hold = prefs.getInt(KEY_HOLD, DEFAULT_HOLD_MS).coerceIn(0, 500).toLong().coerceAtMost(minMs / 2)
                    val dm = realMetrics()
                    val w = dm.widthPixels
                    val h = dm.heightPixels
                    val x = prefs.getInt(KEY_X, -1).let { if (it < 0) w / 2 else it.coerceIn(0, w - 1) }
                    val y = prefs.getInt(KEY_Y, -1).let { if (it < 0) h / 2 else it.coerceIn(0, h - 1) }
                    val len = prefs.getInt(KEY_SWIPE_LEN, 300).coerceIn(50, minOf(w, h) - 1)

                    val doSwipe = when (mode) {
                        MODE_SWIPE -> true
                        MODE_BOTH -> swipeTurn
                        else -> false
                    }
                    swipeTurn = !swipeTurn

                    if (doSwipe) {
                        val dir = if (swipeTurn) 1 else -1
                        val x2 = (x + dir * len).coerceIn(0, w - 1)
                        runShell("input swipe $x $y $x2 $y 300")
                    } else {
                        val tx = if (jitter > 0) (x + rnd.nextInt(jitter * 2 + 1) - jitter).coerceIn(0, w - 1) else x
                        val ty = if (jitter > 0) (y + rnd.nextInt(jitter * 2 + 1) - jitter).coerceIn(0, h - 1) else y
                        val sent = daemon?.send("T $tx $ty $hold") == true
                        if (!sent) {
                            runShell(if (hold > 0) "input swipe $tx $ty $tx $ty $hold" else "input tap $tx $ty")
                        }
                    }

                    val interval = if (maxMs > minMs) minMs + (rnd.nextDouble() * (maxMs - minMs)).toLong() else minMs
                    nextAt += interval
                    val now = SystemClock.elapsedRealtime()
                    if (now - nextAt > 1000) nextAt = now
                    delay((nextAt - now).coerceAtLeast(0))
                }
            } finally {
                daemon?.close()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun realMetrics(): android.util.DisplayMetrics {
        val dm = android.util.DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(dm)
        return dm
    }

    private fun stopLoop() {
        loop?.cancel()
        loop = null
        if (::btnToggle.isInitialized) btnToggle.text = "▶"
        setMarkerActive(false)
    }

    private fun runShell(cmd: String) {
        try {
            val p = Shizuku.newProcess(arrayOf("sh", "-c", cmd), null, null)
            p.waitFor()
            p.destroy()
        } catch (_: Throwable) {
        }
    }

    override fun onDestroy() {
        running = false
        stopLoop()
        job.cancel()
        if (markerAdded) {
            try { windowManager.removeView(marker) } catch (_: Exception) {}
            markerAdded = false
        }
        if (viewAdded) {
            try { windowManager.removeView(root) } catch (_: Exception) {}
            viewAdded = false
        }
        super.onDestroy()
    }
}
