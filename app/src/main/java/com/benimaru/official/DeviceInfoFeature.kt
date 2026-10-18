package com.benimaru.official

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.widget.TextView
import com.google.android.material.progressindicator.LinearProgressIndicator

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

        findViewById<com.google.android.material.progressindicator.LinearProgressIndicator>(R.id.pbRam)
            .setProgressCompat(((usedRamGb / totalRamGb) * 100).toInt().coerceIn(0, 100), true)
        findViewById<com.google.android.material.progressindicator.LinearProgressIndicator>(R.id.pbStorage)
            .setProgressCompat(((usedStorageGb / totalStorageGb) * 100).toInt().coerceIn(0, 100), true)
        findViewById<com.google.android.material.progressindicator.LinearProgressIndicator>(R.id.pbBattery)
            .setProgressCompat(batLevel.coerceIn(0, 100), true)
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
