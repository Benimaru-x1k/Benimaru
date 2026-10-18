package com.benimaru.official

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
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

    companion object {
        const val PREFS = Prefs.MAIN
        const val KEY_MODE = "AutoMode"
        const val KEY_INTERVAL = "AutoIntervalSec"
        const val KEY_X = "AutoX"
        const val KEY_Y = "AutoY"
        const val KEY_SWIPE_LEN = "AutoSwipeLenPx"
        const val KEY_ENABLED = "AutoClickEnabled"

        const val MODE_TAP = 0
        const val MODE_SWIPE = 1
        const val MODE_BOTH = 2

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

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)
    private var loop: Job? = null
    private var viewAdded = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        buildOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

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

    private fun toggleRunning() {
        if (loop?.isActive == true) stopLoop() else startLoop()
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
        loop = scope.launch(Dispatchers.IO) {
            var swipeTurn = false
            while (isActive) {
                val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
                val mode = prefs.getInt(KEY_MODE, MODE_TAP)
                val intervalMs = (prefs.getFloat(KEY_INTERVAL, 5f).coerceIn(0.05f, 3600f) * 1000).toLong()
                val dm = resources.displayMetrics
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

                val cmd = if (doSwipe) {

                    val dir = if (swipeTurn) 1 else -1
                    val x2 = (x + dir * len).coerceIn(0, w - 1)
                    "input swipe $x $y $x2 $y 300"
                } else {
                    "input tap $x $y"
                }
                val t0 = System.currentTimeMillis()
                runShell(cmd)

                delay((intervalMs - (System.currentTimeMillis() - t0)).coerceAtLeast(0))
            }
        }
    }

    private fun stopLoop() {
        loop?.cancel()
        loop = null
        if (::btnToggle.isInitialized) btnToggle.text = "▶"
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
        stopLoop()
        job.cancel()
        if (viewAdded) {
            try { windowManager.removeView(root) } catch (_: Exception) {}
            viewAdded = false
        }
        super.onDestroy()
    }
}
