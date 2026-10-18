package com.benimaru.official

import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val fpAggressiveOn =
    "settings put global low_power 0; " +
    "cmd power set-adaptive-power-saver-enabled false; " +
    "settings put global adaptive_battery_management_enabled 0; " +
    "dumpsys deviceidle disable all"

internal fun MainActivity.fixedPerfAggressiveRestore(prefs: android.content.SharedPreferences): String {
    val prev = prefs.getString(Prefs.KEY_FP_PREV_ADAPTIVE_BATTERY, null)
    val battery = if (prev == "0" || prev == "1")
        "settings put global adaptive_battery_management_enabled $prev"
    else "settings delete global adaptive_battery_management_enabled"
    return "cmd power set-adaptive-power-saver-enabled true; $battery; dumpsys deviceidle enable all"
}

internal fun MainActivity.fixedPerfResetCmd(prefs: android.content.SharedPreferences): String {
    val base = "cmd power set-fixed-performance-mode-enabled false"
    if (!prefs.getBoolean(Prefs.KEY_FIXED_PERF_AGGRESSIVE, false)) return base
    return fixedPerfAggressiveRestore(prefs) + "; " + base
}

internal fun MainActivity.showFixedPerformanceSheet(prefs: android.content.SharedPreferences) {
    val standardOn = prefs.getBoolean(Prefs.KEY_FIXED_PERF, false)
    val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    val sheet = Sheet(this)
        .icon(R.drawable.ic_game)
        .title(getString(R.string.fp_title))
        .message(getString(R.string.fp_message))
    if (!standardOn) {
        list.addView(optionRow(this, getString(R.string.fp_standard), getString(R.string.fp_standard_desc)) {
            sheet.dismiss()
            applyFixedPerformance(prefs, aggressive = false)
        })
    }
    list.addView(optionRow(this, getString(R.string.fp_aggressive), getString(R.string.fp_aggressive_desc)) {
        sheet.dismiss()
        applyFixedPerformance(prefs, aggressive = true)
    })
    sheet.content(list).button(getString(R.string.btn_cancel), SheetStyle.TEXT).show()
}

internal fun MainActivity.applyFixedPerformance(prefs: android.content.SharedPreferences, aggressive: Boolean) {
    if (!hasShizukuPermission()) return
    lifecycleScope.launch(Dispatchers.IO) {
        if (aggressive && !prefs.getBoolean(Prefs.KEY_FIXED_PERF_AGGRESSIVE, false)) {
            val prev = runAdbCommandWithResult("settings get global adaptive_battery_management_enabled").trim()
            prefs.edit().putString(Prefs.KEY_FP_PREV_ADAPTIVE_BATTERY, prev).apply()
        }
        val main = "cmd power set-fixed-performance-mode-enabled true"
        val cmd = if (aggressive) "$fpAggressiveOn; $main" else main
        withContext(Dispatchers.Main) {
            runAdbCommand(
                cmd,
                getString(if (aggressive) R.string.fp_done_aggressive else R.string.fp_done_standard),
                showAd = true
            ) {
                prefs.edit().putBoolean(Prefs.KEY_FIXED_PERF, true).putBoolean(Prefs.KEY_FIXED_PERF_AGGRESSIVE, aggressive).apply()
                findViewById<TextView>(R.id.tvStatusFixedPerf).text =
                    getString(if (aggressive) R.string.status_enabled_aggr else R.string.status_enabled)
            }
        }
    }
}
