package com.benimaru.official

import android.content.Context
import android.content.SharedPreferences
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class SystemStatuses(
    val fixedPerf: String,
    val thermal: String,
    val gameMode: String,
    val refresh: String,
    val resolution: String,
    val network: String,
    val touch: String,
    val dns: String,
    val animations: String,
    val blurs: String,
    val dnd: String,
    val devOptions: String
)

internal fun MainActivity.fetchSystemStatuses() {
    if (!hasShizukuPermission()) return

    lifecycleScope.launch(Dispatchers.IO) {
        val prefs = getSharedPreferences(Prefs.MAIN, Context.MODE_PRIVATE)
        val statuses = readSystemStatuses(prefs)

        withContext(Dispatchers.Main) {
            renderSystemStatuses(statuses)
            renderOverlayStatuses(prefs)
        }
    }
}

private fun MainActivity.readSystemStatuses(prefs: SharedPreferences): SystemStatuses {
    val isFixedPerf = prefs.getBoolean(Prefs.KEY_FIXED_PERF, false)
    val fixedPerfStatus = if (isFixedPerf)
        getString(if (prefs.getBoolean(Prefs.KEY_FIXED_PERF_AGGRESSIVE, false)) R.string.status_enabled_aggr else R.string.status_enabled)
    else getString(R.string.status_default)

    val isThermalDisabled = prefs.getBoolean(Prefs.KEY_THERMAL_DISABLED, false)
    val thermalStatus = if (isThermalDisabled) getString(R.string.status_throttling_disabled) else getString(R.string.status_default)

    val lastGameMode = prefs.getString(Prefs.KEY_LAST_GAME_MODE, "")
    val gameModeStatus = if (lastGameMode.isNullOrEmpty()) getString(R.string.status_default) else getString(R.string.status_active, lastGameMode.orEmpty())

    val minRefresh = runAdbCommandWithResult("settings get system min_refresh_rate")
    val maxRefresh = runAdbCommandWithResult("settings get system peak_refresh_rate")

    val refreshStatus = if (minRefresh.isNotEmpty() && minRefresh != "null" && maxRefresh.isNotEmpty() && maxRefresh != "null") {
        getString(R.string.status_value, "$minRefresh - $maxRefresh Hz")
    } else {
        getString(R.string.status_default)
    }

    val wmSize = runAdbCommandWithResult("wm size")
    val resStatus = if (wmSize.contains("Override size:")) {
        getString(R.string.status_value, wmSize.substringAfter("Override size:").trim())
    } else if (wmSize.contains("Physical size:")) {
        getString(R.string.status_value, wmSize.substringAfter("Physical size:").trim())
    } else {
        getString(R.string.status_default)
    }

    val networkOpt = runAdbCommandWithResult("settings get global tether_dun_required")
    val netStatus = if (networkOpt == "0") getString(R.string.status_optimized) else getString(R.string.status_default)

    val touchOpt = runAdbCommandWithResult("settings get system pointer_speed")
    val touchStatus = if (touchOpt == "7") getString(R.string.status_improved) else getString(R.string.status_default)

    val dnsMode = runAdbCommandWithResult("settings get global private_dns_mode")
    val dnsSpecifier = runAdbCommandWithResult("settings get global private_dns_specifier")

    val dnsStatus = if (dnsMode.trim() == "hostname" && dnsSpecifier.isNotEmpty() && dnsSpecifier != "null") {
        getString(R.string.status_value, dnsSpecifier.trim())
    } else {
        getString(R.string.status_default)
    }

    val animScale = runAdbCommandWithResult("settings get global window_animation_scale")
    val animStatus = if (animScale == "0.5") getString(R.string.status_anim_fast) else getString(R.string.status_default)

    val blursOpt = runAdbCommandWithResult("settings get global disable_window_blurs")
    val blursStatus = if (blursOpt == "1") getString(R.string.status_blurs_off) else getString(R.string.status_default)

    val headsUpOpt = runAdbCommandWithResult("settings get global heads_up_notifications_enabled")
    val headsUpStatus = if (headsUpOpt == "0") getString(R.string.status_dnd_blocked) else getString(R.string.status_default)

    val devOpt = runAdbCommandWithResult("settings get global development_settings_enabled")
    val devOptStatus = if (devOpt == "1") getString(R.string.status_devopt_on) else getString(R.string.status_devopt_off)

    return SystemStatuses(
        fixedPerf = fixedPerfStatus,
        thermal = thermalStatus,
        gameMode = gameModeStatus,
        refresh = refreshStatus,
        resolution = resStatus,
        network = netStatus,
        touch = touchStatus,
        dns = dnsStatus,
        animations = animStatus,
        blurs = blursStatus,
        dnd = headsUpStatus,
        devOptions = devOptStatus
    )
}

private fun MainActivity.renderSystemStatuses(s: SystemStatuses) {
    findViewById<TextView>(R.id.tvStatusFixedPerf).text = s.fixedPerf
    findViewById<TextView>(R.id.tvStatusThermal).text = s.thermal
    findViewById<TextView>(R.id.tvStatusGameMode).text = s.gameMode
    findViewById<TextView>(R.id.tvStatusRefresh).text = s.refresh
    findViewById<TextView>(R.id.tvStatusResolution).text = s.resolution
    findViewById<TextView>(R.id.tvStatusNetwork).text = s.network
    findViewById<TextView>(R.id.tvStatusTouch).text = s.touch
    findViewById<TextView>(R.id.tvStatusDns).text = s.dns
    findViewById<TextView>(R.id.tvStatusAnimations).text = s.animations
    findViewById<TextView>(R.id.tvStatusBlurs).text = s.blurs
    findViewById<TextView>(R.id.tvStatusDnd).text = s.dnd
    findViewById<TextView>(R.id.tvStatusDevOptions).text = s.devOptions
}

private fun MainActivity.renderOverlayStatuses(prefs: SharedPreferences) {
    val isCrosshairOn = prefs.getBoolean(Prefs.KEY_CROSSHAIR_ENABLED, false)
    isCrosshairEnabled = isCrosshairOn
    if (isCrosshairOn) {
        val style = prefs.getString(Prefs.KEY_CROSSHAIR_STYLE, "Cross")
        val color = prefs.getString(Prefs.KEY_CROSSHAIR_COLOR, "Red")
        val size = prefs.getString(Prefs.KEY_CROSSHAIR_SIZE, "Medium")
        findViewById<TextView>(R.id.tvStatusCrosshair).text = getString(R.string.status_crosshair, xl(style.orEmpty()), xl(color.orEmpty()), xl(size.orEmpty()))
    } else {
        findViewById<TextView>(R.id.tvStatusCrosshair).text = getString(R.string.status_disabled)
    }

    val isMonitorOn = prefs.getBoolean(Prefs.KEY_MONITOR_ENABLED, false)
    isMonitorEnabled = isMonitorOn
    if (isMonitorOn) {
        findViewById<TextView>(R.id.tvStatusMonitor)?.text = getString(R.string.status_enabled)
    } else {
        findViewById<TextView>(R.id.tvStatusMonitor)?.text = getString(R.string.status_disabled)
    }

    val isAutoOn = prefs.getBoolean(AutoClickService.KEY_ENABLED, false)
    isAutoClickEnabled = isAutoOn
    findViewById<TextView>(R.id.tvStatusAutoClick)?.text = if (isAutoOn)
        getString(R.string.status_autoclick, AutoClickService.modeLabel(this, prefs.getInt(AutoClickService.KEY_MODE, 0)), prefs.getFloat(AutoClickService.KEY_INTERVAL, 5f).toString())
    else getString(R.string.status_disabled)
}
