package com.benimaru.official

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import es.dmoral.toasty.Toasty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun MainActivity.setupClickListeners() {
    val prefs = getSharedPreferences(Prefs.MAIN, Context.MODE_PRIVATE)

    setupTweakCardListeners(prefs)
    setupPremiumCardListeners(prefs)
    setupOverlayResetListeners()
    setupTweakResetListeners(prefs)
    setupBottomActionListeners(prefs)
}

private fun MainActivity.en(res: Int): String = AppTranslator.english(this, res)

private class TweakSetting(val ns: String, val name: String, val ours: String, val fallback: String?) {
    val prefKey get() = "${Prefs.PREV_SETTING_PREFIX}${ns}_$name"
}

private val TOUCH_SETTINGS = listOf(
    TweakSetting("system", "pointer_speed", "7", null),
    TweakSetting("secure", "long_press_timeout", "250", "400")
)

private val ANIM_SETTINGS = listOf(
    TweakSetting("global", "window_animation_scale", "0.5", "1"),
    TweakSetting("global", "transition_animation_scale", "0.5", "1"),
    TweakSetting("global", "animator_duration_scale", "0.5", "1")
)

private val SAFE_SETTING_VALUE = Regex("^-?[0-9]+(\\.[0-9]+)?$")

private fun MainActivity.snapshotSettings(prefs: SharedPreferences, settings: List<TweakSetting>) {
    val ed = prefs.edit()
    for (s in settings) {
        val current = runAdbCommandWithResult("settings get ${s.ns} ${s.name}").trim()
        if (current.isNotEmpty() && current != s.ours) ed.putString(s.prefKey, current)
    }
    ed.apply()
}

private fun restoreSettingsCmd(prefs: SharedPreferences, settings: List<TweakSetting>): String =
    settings.joinToString("; ") { s ->
        val prev = prefs.getString(s.prefKey, null)
        when {
            prev != null && SAFE_SETTING_VALUE.matches(prev) -> "settings put ${s.ns} ${s.name} $prev"
            prev == "null" -> "settings delete ${s.ns} ${s.name}"
            s.fallback == null -> "settings delete ${s.ns} ${s.name}"
            else -> "settings put ${s.ns} ${s.name} ${s.fallback}"
        }
    }

private fun clearSavedSettings(prefs: SharedPreferences, settings: List<TweakSetting>) {
    val ed = prefs.edit()
    settings.forEach { ed.remove(it.prefKey) }
    ed.apply()
}

private fun MainActivity.snapshotThen(prefs: SharedPreferences, settings: List<TweakSetting>, action: () -> Unit) {
    lifecycleScope.launch {
        withContext(Dispatchers.IO) { snapshotSettings(prefs, settings) }
        action()
    }
}

private fun MainActivity.runTweakOnce(
    statusId: Int,
    doneMarker: () -> String,
    alreadyToastRes: Int,
    command: String,
    successMessageRes: Int,
    onSuccess: () -> Unit = { fetchSystemStatuses() }
) {
    val status = AppTranslator.toEnglish(findViewById<TextView>(statusId).text.toString())
    if (status.contains(doneMarker())) {
        Toasty.info(this, getString(alreadyToastRes), Toast.LENGTH_SHORT, true).show()
        return
    }
    runAdbCommand(command, getString(successMessageRes), showAd = true) { onSuccess() }
}

private fun MainActivity.bindCommandReset(buttonId: Int, command: String, messageRes: Int) {
    findViewById<TextView>(buttonId).setOnClickListener {
        runAdbCommand(command, getString(messageRes)) { fetchSystemStatuses() }
    }
}

private fun MainActivity.bindStatusReset(buttonId: Int, statusId: Int) {
    findViewById<TextView>(buttonId).setOnClickListener {
        findViewById<TextView>(statusId).text = getString(R.string.status_ready)
        Toasty.success(this, getString(R.string.toast_status_reset), Toast.LENGTH_SHORT, true).show()
    }
}


private fun MainActivity.setupTweakCardListeners(prefs: SharedPreferences) {
    findViewById<CardView>(R.id.cardApkInstall).setOnClickListener { showApkInstallSheet() }
    findViewById<CardView>(R.id.cardFlagSecure).setOnClickListener { showFlagSecureSheet() }

    findViewById<CardView>(R.id.cardFixedPerformance).setOnClickListener {
        if (prefs.getBoolean(Prefs.KEY_FIXED_PERF_AGGRESSIVE, false)) {
            Toasty.info(this, getString(R.string.toast_already_aggr), Toast.LENGTH_SHORT, true).show()
            return@setOnClickListener
        }
        showFixedPerformanceSheet(prefs)
    }

    findViewById<CardView>(R.id.cardOptimizeSystem).setOnClickListener {
        runTweakOnce(
            R.id.tvStatusOptimize, { en(R.string.w_recently) }, R.string.toast_already_optimized,
            "cmd activity kill-all", R.string.msg_sys_optimized
        ) {
            findViewById<TextView>(R.id.tvStatusOptimize).text = getString(R.string.status_recently)
        }
    }

    findViewById<CardView>(R.id.cardImproveNetwork).setOnClickListener {
        runTweakOnce(
            R.id.tvStatusNetwork, { en(R.string.w_optimized) }, R.string.toast_already_optimized,
            "settings put global tether_dun_required 0", R.string.msg_net_optimized
        )
    }

    findViewById<CardView>(R.id.cardImproveTouch).setOnClickListener {
        snapshotThen(prefs, TOUCH_SETTINGS) {
            runTweakOnce(
                R.id.tvStatusTouch, { en(R.string.w_improved) }, R.string.toast_already_improved,
                "settings put system pointer_speed 7 && settings put secure long_press_timeout 250", R.string.msg_touch_improved
            )
        }
    }

    findViewById<CardView>(R.id.cardFastAnimations).setOnClickListener {
        snapshotThen(prefs, ANIM_SETTINGS) {
            runTweakOnce(
                R.id.tvStatusAnimations, { "0.5x" }, R.string.toast_already_sped,
                "settings put global window_animation_scale 0.5 && settings put global transition_animation_scale 0.5 && settings put global animator_duration_scale 0.5",
                R.string.msg_anim_fast
            )
        }
    }

    findViewById<CardView>(R.id.cardDisableBlurs).setOnClickListener {
        runTweakOnce(
            R.id.tvStatusBlurs, { en(R.string.w_disabled) }, R.string.toast_already_disabled,
            "settings put global disable_window_blurs 1", R.string.msg_blurs_off
        )
    }

    findViewById<CardView>(R.id.cardGamingDnd).setOnClickListener {
        runTweakOnce(
            R.id.tvStatusDnd, { en(R.string.w_blocked) }, R.string.toast_already_blocked,
            "settings put global heads_up_notifications_enabled 0", R.string.msg_dnd_on
        )
    }

    findViewById<CardView>(R.id.cardDisableDevOptions).setOnClickListener {
        runTweakOnce(
            R.id.tvStatusDevOptions, { en(R.string.w_disabled) }, R.string.toast_already_disabled,
            "settings put global development_settings_enabled 0", R.string.msg_devopt_off
        )
    }

    findViewById<CardView>(R.id.cardFixGyro).setOnClickListener { fixGyroDelay() }

    findViewById<CardView>(R.id.cardAdjustRefreshRate).setOnClickListener { showRefreshRateDialog() }

    findViewById<CardView>(R.id.cardGameMode).setOnClickListener {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            Toasty.error(this, getString(R.string.toast_needs_a12), Toast.LENGTH_LONG, true).show()
            return@setOnClickListener
        }
        handlePremiumFeature { showGameModeSelectorDialog() }
    }
}

private fun MainActivity.setupPremiumCardListeners(prefs: SharedPreferences) {
    findViewById<CardView>(R.id.cardFstrim).setOnClickListener {
        handlePremiumFeature {
            runTweakOnce(
                R.id.tvStatusFstrim, { en(R.string.w_recently) }, R.string.toast_storage_already,
                "sm fstrim", R.string.msg_fstrim_done
            ) {
                findViewById<TextView>(R.id.tvStatusFstrim).text = getString(R.string.status_recently)
            }
        }
    }

    findViewById<CardView>(R.id.cardFreezeApps).setOnClickListener {
        handlePremiumFeature { showFreezeAppsSheet() }
    }
    findViewById<CardView>(R.id.cardShellExecute).setOnClickListener {
        handlePremiumFeature { showShellExecuteSheet() }
    }
    findViewById<CardView>(R.id.cardShaderOptimization).setOnClickListener {
        handlePremiumFeature { showShaderOptimizationSheet() }
    }
    findViewById<CardView>(R.id.cardTextureCompression).setOnClickListener {
        handlePremiumFeature { showTextureSheet() }
    }
    findViewById<CardView>(R.id.cardRamBooster).setOnClickListener {
        handlePremiumFeature { showRamBoosterSheet() }
    }
    findViewById<CardView>(R.id.cardClearCache).setOnClickListener {
        handlePremiumFeature { confirmClearCaches() }
    }
    findViewById<CardView>(R.id.cardChangeResolution).setOnClickListener {
        handlePremiumFeature { showResolutionModeDialog() }
    }
    findViewById<CardView>(R.id.cardCustomDns).setOnClickListener {
        handlePremiumFeature { showDnsDialog() }
    }
    findViewById<CardView>(R.id.cardCrosshair).setOnClickListener {
        handlePremiumFeature { showCrosshairConfigDialog() }
    }
    findViewById<CardView>(R.id.cardRealtimeMonitor).setOnClickListener {
        handlePremiumFeature { toggleRealtimeMonitor() }
    }
    findViewById<CardView>(R.id.cardAutoClick).setOnClickListener {
        handlePremiumFeature { showAutoClickDialog() }
    }

    findViewById<CardView>(R.id.cardThermalThrottling).setOnClickListener {
        handlePremiumFeature {
            Sheet(this)
                .icon(R.drawable.ic_warning, Color.parseColor("#E53935"))
                .title(getString(R.string.dlg_thermal_title))
                .message(getString(R.string.dlg_thermal_msg))
                .button(getString(R.string.btn_cancel), SheetStyle.TONAL)
                .button(getString(R.string.btn_enable), SheetStyle.DANGER) {
                    runAdbCommand("cmd thermalservice override-status 0", getString(R.string.msg_thermal_off)) {
                        prefs.edit().putBoolean(Prefs.KEY_THERMAL_DISABLED, true).apply()
                        fetchSystemStatuses()
                    }
                }
                .show()
        }
    }
}

private fun MainActivity.setupOverlayResetListeners() {
    findViewById<TextView>(R.id.btnResetAutoClick).setOnClickListener {
        if (isAutoClickEnabled) stopAutoClickService()
    }
    findViewById<TextView>(R.id.btnResetCrosshair).setOnClickListener {
        if (isCrosshairEnabled) stopCrosshairService()
    }
    findViewById<TextView>(R.id.btnResetMonitor).setOnClickListener {
        if (isMonitorEnabled) toggleRealtimeMonitor()
    }
}

private fun MainActivity.setupTweakResetListeners(prefs: SharedPreferences) {
    findViewById<TextView>(R.id.btnResetFreeze).setOnClickListener { restoreAllFrozen() }
    findViewById<TextView>(R.id.btnResetShader).setOnClickListener { resetShaderOptimizations() }
    findViewById<TextView>(R.id.btnResetTexture).setOnClickListener { confirmRestoreAllTextures() }

    findViewById<TextView>(R.id.btnResetFixedPerf).setOnClickListener {
        runAdbCommand(fixedPerfResetCmd(prefs), getString(R.string.fp_reset_done)) {
            prefs.edit().putBoolean(Prefs.KEY_FIXED_PERF, false).putBoolean(Prefs.KEY_FIXED_PERF_AGGRESSIVE, false).apply()
            findViewById<TextView>(R.id.tvStatusFixedPerf).text = getString(R.string.status_default)
        }
    }
    findViewById<TextView>(R.id.btnResetThermal).setOnClickListener {
        runAdbCommand("cmd thermalservice reset", getString(R.string.msg_thermal_restored)) {
            prefs.edit().putBoolean(Prefs.KEY_THERMAL_DISABLED, false).apply()
            fetchSystemStatuses()
        }
    }
    findViewById<TextView>(R.id.btnResetGameMode).setOnClickListener {
        val resetCmd = gameModeResetCmd(prefs)
        if (resetCmd.isNotEmpty()) {
            runAdbCommand("$resetCmd; true", getString(R.string.msg_gm_reset)) {
                clearGameModeState(prefs)
                fetchSystemStatuses()
            }
        } else {
            Toasty.info(this, getString(R.string.toast_no_gm), Toast.LENGTH_SHORT, true).show()
        }
    }

    bindStatusReset(R.id.btnResetOptimize, R.id.tvStatusOptimize)
    bindStatusReset(R.id.btnResetRamBooster, R.id.tvStatusRamBooster)
    bindStatusReset(R.id.btnResetCache, R.id.tvStatusCache)
    bindStatusReset(R.id.btnResetFstrim, R.id.tvStatusFstrim)
    bindStatusReset(R.id.btnResetGyro, R.id.tvStatusGyro)
    bindStatusReset(R.id.btnResetShell, R.id.tvStatusShell)

    bindCommandReset(
        R.id.btnResetRefresh,
        "settings delete system min_refresh_rate && settings delete system peak_refresh_rate",
        R.string.msg_refresh_reset
    )
    bindCommandReset(R.id.btnResetNetwork, "settings delete global tether_dun_required", R.string.msg_net_reset)
    findViewById<TextView>(R.id.btnResetTouch).setOnClickListener {
        runAdbCommand(restoreSettingsCmd(prefs, TOUCH_SETTINGS), getString(R.string.msg_touch_reset)) {
            clearSavedSettings(prefs, TOUCH_SETTINGS)
            fetchSystemStatuses()
        }
    }
    bindCommandReset(R.id.btnResetResolution, "wm size reset && wm density reset", R.string.msg_res_reset)
    bindCommandReset(
        R.id.btnResetDns,
        "settings put global private_dns_mode default && settings delete global private_dns_specifier",
        R.string.msg_dns_reset
    )
    findViewById<TextView>(R.id.btnResetAnimations).setOnClickListener {
        runAdbCommand(restoreSettingsCmd(prefs, ANIM_SETTINGS), getString(R.string.msg_anim_reset)) {
            clearSavedSettings(prefs, ANIM_SETTINGS)
            fetchSystemStatuses()
        }
    }
    bindCommandReset(R.id.btnResetBlurs, "settings put global disable_window_blurs 0", R.string.msg_blurs_reset)
    bindCommandReset(R.id.btnResetDnd, "settings put global heads_up_notifications_enabled 1", R.string.msg_dnd_reset)
    bindCommandReset(R.id.btnResetDevOptions, "settings put global development_settings_enabled 1", R.string.msg_devopt_on)
}


private fun MainActivity.setupBottomActionListeners(prefs: SharedPreferences) {
    findViewById<Button>(R.id.btnLaunchGame).setOnClickListener {
        showGameLauncherDialog()
    }

    findViewById<Button>(R.id.btnRemoveAds).setOnClickListener {
        if (isPremium()) {
            Toasty.success(this, getString(R.string.toast_you_premium), Toast.LENGTH_SHORT, true).show()
        } else {
            showRemoveAdsDialog()
        }
    }

    findViewById<Button>(R.id.btnResetAll).setOnClickListener {
        Sheet(this)
            .icon(R.drawable.ic_reset, Color.parseColor("#E53935"))
            .title(getString(R.string.dlg_reset_all_title))
            .message(getString(R.string.dlg_reset_all_msg))
            .button(getString(R.string.btn_cancel), SheetStyle.TONAL)
            .button(getString(R.string.btn_reset_all), SheetStyle.DANGER) { resetAllFunctions(prefs) }
            .show()
    }
}

internal fun MainActivity.resetAllFunctions(prefs: android.content.SharedPreferences, onDone: (() -> Unit)? = null) {
    run {
        val steps = ArrayList<String>()
        steps += "cmd power set-fixed-performance-mode-enabled false"
        steps += "cmd thermalservice reset"
        gameModeResetCmd(prefs).takeIf { it.isNotEmpty() }?.let { steps += it }
        if (prefs.getBoolean(Prefs.KEY_FIXED_PERF_AGGRESSIVE, false)) {
            steps += "{ ${fixedPerfAggressiveRestore(prefs)}; true; }"
        }
        steps += "settings delete system min_refresh_rate"
        steps += "settings delete system peak_refresh_rate"
        steps += "wm size reset"
        steps += "wm density reset"
        steps += restoreSettingsCmd(prefs, TOUCH_SETTINGS)
        steps += "settings put global private_dns_mode default"
        steps += "settings delete global private_dns_specifier"
        steps += restoreSettingsCmd(prefs, ANIM_SETTINGS)
        steps += "settings put global disable_window_blurs 0"
        steps += "settings put global heads_up_notifications_enabled 1"
        steps += "settings delete global tether_dun_required"
        steps += "settings delete secure disable_secure_windows"
        steps += "settings put global development_settings_enabled 1"
        val resetCmd = steps.joinToString("; ")

        runAdbCommand(resetCmd, getString(R.string.msg_all_reset)) {
            prefs.edit()
                .putBoolean(Prefs.KEY_FIXED_PERF, false)
                .putBoolean(Prefs.KEY_FIXED_PERF_AGGRESSIVE, false)
                .putBoolean(Prefs.KEY_THERMAL_DISABLED, false)
                .apply()
            clearGameModeState(prefs)
            clearSavedSettings(prefs, TOUCH_SETTINGS)
            clearSavedSettings(prefs, ANIM_SETTINGS)


            if (isMonitorEnabled) {
                stopService(Intent(this@resetAllFunctions, RealtimeMonitorService::class.java))
                isMonitorEnabled = false
                prefs.edit().putBoolean(Prefs.KEY_MONITOR_ENABLED, false).apply()
                findViewById<TextView>(R.id.tvStatusMonitor)?.text = getString(R.string.status_disabled)
            }

            if (isCrosshairEnabled) stopCrosshairService()
            if (isAutoClickEnabled) stopAutoClickService()

            findViewById<TextView>(R.id.tvStatusFixedPerf).text = getString(R.string.status_default)
            findViewById<TextView>(R.id.tvStatusOptimize).text = getString(R.string.status_ready)

            findViewById<TextView>(R.id.tvStatusFstrim).text = getString(R.string.status_ready)
            findViewById<TextView>(R.id.tvStatusRamBooster).text = getString(R.string.status_ready)
            findViewById<TextView>(R.id.tvStatusCache).text = getString(R.string.status_ready)
            findViewById<TextView>(R.id.tvStatusGyro).text = getString(R.string.status_ready)
            findViewById<TextView>(R.id.tvStatusShell)?.text = getString(R.string.status_ready)

            resetShaderOptimizations(silent = true)
            fetchSystemStatuses()
            onDone?.invoke()
        }
    }
}
