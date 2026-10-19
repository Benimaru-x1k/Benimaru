package com.benimaru.official

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import es.dmoral.toasty.Toasty
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val GYRO_MEASURE_MS = 1000L

private const val GYRO_HEALTHY_MIN_HZ = 100

internal fun MainActivity.fixGyroDelay() {
    if (!hasShizukuPermission()) return

    val progress = ProgressSheet(this)
        .icon(R.drawable.ic_gamepad)
        .title(getString(R.string.dlg_gyro_working))
        .message(getString(R.string.dlg_gyro_working_msg))
    progress.show(indeterminate = true)

    lifecycleScope.launch {
        val before = measureGyroRateHz()
        if (before == null) {
            progress.dismiss()
            Toasty.error(this@fixGyroDelay, getString(R.string.gyro_not_found), Toast.LENGTH_LONG, true).show()
            return@launch
        }

        withContext(Dispatchers.IO) {
            runShell("dumpsys sensorservice enable")
            runShell("settings put global low_power 0")
        }

        val after = measureGyroRateHz() ?: before
        progress.dismiss()
        findViewById<TextView>(R.id.tvStatusGyro).text = getString(R.string.status_gyro, after)
        showGyroResult(before, after)
    }
}

private fun MainActivity.showGyroResult(before: Int, after: Int) {
    val message = if (after >= GYRO_HEALTHY_MIN_HZ) {
        getString(R.string.gyro_result_good, after, before)
    } else {
        getString(R.string.gyro_result_low, after, before)
    }
    Sheet(this)
        .icon(R.drawable.ic_gamepad)
        .title(getString(R.string.dlg_gyro_result))
        .message(message)
        .button(getString(R.string.btn_close), SheetStyle.PRIMARY)
        .onDismiss { showInterstitialAd() }
        .show()
}

private suspend fun MainActivity.measureGyroRateHz(): Int? {
    val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
    val gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) ?: return null

    val events = AtomicInteger(0)
    val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            events.incrementAndGet()
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    if (!sensorManager.registerListener(listener, gyro, SensorManager.SENSOR_DELAY_FASTEST)) return null
    try {
        delay(GYRO_MEASURE_MS)
    } finally {
        sensorManager.unregisterListener(listener)
    }
    return (events.get() * 1000L / GYRO_MEASURE_MS).toInt()
}
