package com.benimaru.official

import android.content.pm.PackageManager
import android.graphics.Color
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.DrawableCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import es.dmoral.toasty.Toasty
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

internal fun MainActivity.checkShizukuStatus() {
    if (isShizukuInstalled()) {
        if (Shizuku.pingBinder()) {
            if (hasShizukuPermission()) {
                updateShizukuBannerUI(true)
                fetchSystemStatuses()
            } else {
                updateShizukuBannerUI(false)
            }
        } else {
            updateShizukuBannerUI(false)
            Toasty.warning(this, getString(R.string.toast_shizuku_not_running_launch), Toast.LENGTH_LONG, true).show()
            launchShizukuApp()
        }
    } else {
        updateShizukuBannerUI(false)
        showShizukuRequiredDialog()
    }
}

internal fun MainActivity.hasShizukuPermission(): Boolean {
    if (!Shizuku.pingBinder()) {
        Toasty.error(this, getString(R.string.toast_shizuku_not_running), Toast.LENGTH_SHORT, true).show()
        return false
    }
    return if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
        true
    } else {
        if (Shizuku.shouldShowRequestPermissionRationale()) {
            Toasty.info(this, getString(R.string.toast_open_shizuku), Toast.LENGTH_LONG, true).show()
        } else {
            Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
        }
        false
    }
}

internal fun MainActivity.isShizukuInstalled(): Boolean {
    return try {
        packageManager.getPackageInfo(AppConfig.SHIZUKU_PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}

internal fun MainActivity.launchShizukuApp() {
    val intent = packageManager.getLaunchIntentForPackage(AppConfig.SHIZUKU_PACKAGE)
    if (intent != null) {
        startActivity(intent)
        finishAffinity()
    } else {
        Toasty.error(this, getString(R.string.toast_shizuku_launch_fail), Toast.LENGTH_SHORT, true).show()
        finishAffinity()
    }
}

internal fun MainActivity.runAdbCommandWithResult(command: String): String {
    return try {
        val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        val output = java.lang.StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            output.append(line).append("\n")
        }
        process.waitFor()
        output.toString().trim()
    } catch (e: Exception) {
        ""
    }
}

internal data class ShellResult(val code: Int, val out: String)

internal fun MainActivity.runShell(cmd: String): ShellResult = try {
    val p = Shizuku.newProcess(arrayOf("sh", "-c", cmd), null, null)
    val out = p.inputStream.bufferedReader().readText()
    val err = p.errorStream.bufferedReader().readText()
    ShellResult(p.waitFor(), (out + "\n" + err).trim())
} catch (e: Exception) {
    ShellResult(-1, e.message ?: "error")
}

internal fun MainActivity.runAdbCommand(command: String, successMessage: String, showAd: Boolean = false, onSuccess: () -> Unit = {}) {
    if (!hasShizukuPermission()) return

    lifecycleScope.launch(Dispatchers.IO) {
        try {
            val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
            val exitCode = process.waitFor()
            withContext(Dispatchers.Main) {
                if (exitCode == 0) {
                    Toasty.success(this@runAdbCommand, successMessage, Toast.LENGTH_SHORT, true).show()
                    onSuccess()
                    if (showAd) showInterstitialAd()
                } else {
                    Toasty.error(this@runAdbCommand, getString(R.string.toast_cmd_failed, exitCode), Toast.LENGTH_SHORT, true).show()
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toasty.error(this@runAdbCommand, getString(R.string.toast_error, e.message ?: ""), Toast.LENGTH_SHORT, true).show()
            }
        }
    }
}

internal fun MainActivity.showShizukuRequiredDialog() {
    if (isFinishing || isDestroyed) return
    Sheet(this)
        .icon(R.drawable.ic_download)
        .title(getString(R.string.dlg_shizuku_required))
        .message(getString(R.string.dlg_shizuku_required_msg))
        .cancelable(false)
        .button(getString(R.string.btn_exit), SheetStyle.TONAL) { finishAffinity() }
        .button(getString(R.string.btn_download), SheetStyle.PRIMARY) { downloadShizukuApk() }
        .show()
}

internal fun MainActivity.downloadShizukuApk() {
    downloadAndInstall(
        getString(R.string.dl_shizuku),
        AppConfig.SHIZUKU_APK_URL,
        "shizuku-v13.6.0.apk",
        finishAfterInstall = true
    )
}

internal fun MainActivity.updateShizukuBannerUI(isConnected: Boolean) {
    val cardShizuku = findViewById<MaterialCardView>(R.id.cardShizukuStatus)
    val tvShizuku = findViewById<TextView>(R.id.tvShizukuStatus)

    val color = Color.parseColor(if (isConnected) "#4ADE80" else "#FF6B6B")
    tvShizuku.text = if (isConnected) "Shizuku" else getString(R.string.shizuku_offline)
    tvShizuku.setTextColor(Color.WHITE)
    cardShizuku.setCardBackgroundColor(Color.parseColor(if (isConnected) "#334ADE80" else "#33FF6B6B"))
    cardShizuku.strokeColor = Color.parseColor(if (isConnected) "#664ADE80" else "#66FF6B6B")

    AppCompatResources.getDrawable(this, R.drawable.ic_status_dot)?.let { d ->
        val dot = DrawableCompat.wrap(d.mutate())
        DrawableCompat.setTint(dot, color)
        tvShizuku.setCompoundDrawablesRelativeWithIntrinsicBounds(dot, null, null, null)
    }

    cardShizuku.setOnClickListener {
        if (isConnected) {
            Toasty.success(this, getString(R.string.toast_shizuku_ok), Toast.LENGTH_SHORT, true).show()
        } else {
            checkShizukuStatus()
        }
    }
}
