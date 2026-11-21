package com.benimaru.official

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val LIVE_STATS_INTERVAL_MS = 2500L

internal fun MainActivity.startLiveStats() {
    if (liveStatsJob?.isActive == true) return
    liveStatsJob = lifecycleScope.launch {
        while (isActive) {
            updateDeviceInfo()
            delay(LIVE_STATS_INTERVAL_MS)
        }
    }
}

internal fun MainActivity.stopLiveStats() {
    liveStatsJob?.cancel()
    liveStatsJob = null
}

private fun MainActivity.readBatteryTempC(): Float? {
    val intent: Intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
    val tenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
    return if (tenths == Int.MIN_VALUE) null else tenths / 10f
}

internal fun MainActivity.updateDeviceInfo() {
    try {
        val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".replace("\n", "").trim()
        val androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        val apiLevel = "API ${Build.VERSION.SDK_INT}"

        val actManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)
        val totalRamGb = memInfo.totalMem.toDouble() / (1024 * 1024 * 1024)
        val availRamGb = memInfo.availMem.toDouble() / (1024 * 1024 * 1024)
        val usedRamGb = totalRamGb - availRamGb

        val stat = StatFs(Environment.getDataDirectory().path)
        val totalStorageGb = stat.totalBytes.toDouble() / (1024 * 1024 * 1024)
        val availStorageGb = stat.availableBytes.toDouble() / (1024 * 1024 * 1024)
        val usedStorageGb = totalStorageGb - availStorageGb

        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batLevel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        val metrics = resources.displayMetrics
        val currentRes = "${metrics.widthPixels}x${metrics.heightPixels}"

        findViewById<TextView>(R.id.tvDeviceModel).text = deviceName.uppercase()
        findViewById<TextView>(R.id.tvAndroidVersion).text = androidVersion
        findViewById<TextView>(R.id.tvRamUsage).text = String.format("%.1f/%.1f GB", usedRamGb, totalRamGb)
        findViewById<TextView>(R.id.tvStorageUsage).text = String.format("%.1f/%.1f GB", usedStorageGb, totalStorageGb)
        findViewById<TextView>(R.id.tvBattery).text = "$batLevel%"
        findViewById<TextView>(R.id.tvApiLevel).text = apiLevel
        findViewById<TextView>(R.id.tvResolutionDisplay).text = currentRes
        findViewById<TextView>(R.id.tvFreeStorageDisplay).text = String.format("%.1f GB", availStorageGb)

        findViewById<RingGaugeView>(R.id.pbRam)
            .setProgress(((usedRamGb / totalRamGb) * 100).toInt().coerceIn(0, 100), true)
        findViewById<RingGaugeView>(R.id.pbStorage)
            .setProgress(((usedStorageGb / totalStorageGb) * 100).toInt().coerceIn(0, 100), true)
        findViewById<RingGaugeView>(R.id.pbBattery).apply {
            invertColors = true
            setProgress(batLevel.coerceIn(0, 100), true)
        }

        val tvTemp = findViewById<TextView>(R.id.tvBatteryTemp)
        val tempC = readBatteryTempC()
        if (tempC == null) {
            tvTemp.text = "--°C"
            tvTemp.setTextColor(ContextCompat.getColor(this, R.color.onGradientMuted))
        } else {
            tvTemp.text = String.format("%.1f°C", tempC)
            tvTemp.setTextColor(
                ContextCompat.getColor(
                    this,
                    when {
                        tempC >= 45f -> R.color.gaugeBad
                        tempC >= 40f -> R.color.gaugeWarn
                        else -> R.color.onGradientMuted
                    }
                )
            )
        }
    } catch (e: Exception) {
        android.util.Log.e("BenimaruTool", "Error in updateDeviceInfo", e)
    }
}

internal fun MainActivity.availRamMb(): Long {
    val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val info = ActivityManager.MemoryInfo()
    am.getMemoryInfo(info)
    return info.availMem / (1024 * 1024)
}

internal fun MainActivity.freeStorageBytes(): Long = StatFs(Environment.getDataDirectory().path).availableBytes

internal fun MainActivity.formatSize(bytes: Long): String =
    if (bytes >= 1024L * 1024 * 1024) String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024))
    else String.format("%.0f MB", bytes / (1024.0 * 1024))
