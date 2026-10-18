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
import es.dmoral.toasty.Toasty



internal fun MainActivity.setupClickListeners() {
    val prefs = getSharedPreferences(Prefs.MAIN, Context.MODE_PRIVATE)

    setupTweakCardListeners(prefs)
    setupPremiumCardListeners(prefs)
    setupOverlayResetListeners()
    setupTweakResetListeners(prefs)
    setupBottomActionListeners(prefs)
}


private fun MainActivity.runTweakOnce(
    statusId: Int,
    doneMarker: () -> String,
    alreadyToastRes: Int,
    command: String,
    successMessageRes: Int,
    onSuccess: () -> Unit = { fetchSystemStatuses() }
) {
    val status = findViewById<TextView>(statusId).text.toString()
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
    findViewById<CardView>(R.id.cardFixedPerformance).setOnClickListener {
        if (prefs.getBoolean(Prefs.KEY_FIXED_PERF_AGGRESSIVE, false)) {
            Toasty.info(this, getString(R.string.toast_already_aggr), Toast.LENGTH_SHORT, true).show()
            return@setOnClickListener
        }
        showFixedPerformanceSheet(prefs)
    }

    findViewById<CardView>(R.id.cardOptimizeSystem).setOnClickListener {
        runTweakOnce(
            R.id.tvStatusOptimize, { getString(R.string.w_recently) }, R.string.toast_already_optimized,
            "cmd activity kill-all", R.string.msg_sys_optimized
        ) {
            findViewById<TextView>(R.id.tvStatusOptimize).text = getString(R.string.status_recently)
        }
    }

    findViewById<CardView>(R.id.cardImproveNetwork).setOnClickListener {
        runTweakOnce(
            R.id.tvStatusNetwork, { getString(R.string.w_optimized) }, R.string.toast_already_optimized,
            "settings put global tether_dun_required 0", R.string.msg_net_optimized
        )
    }

    findViewById<CardView>(R.id.cardImproveTouch).setOnClickListener {
        runTweakOnce(
            R.id.tvStatusTouch, { getString(R.string.w_improved) }, R.string.toast_already_improved,
            "settings put system pointer_speed 7 && settings put secure long_press_timeout 250", R.string.msg_touch_improved
        )
    }

    findViewById<CardView>(R.id.cardFastAnimations).setOnClickListener {
        runTweakOnce(
            R.id.tvStatusAnimations, { "0.5x" }, R.string.toast_already_sped,
            "settings put global window_animation_scale 0.5 && settings put global transition_animation_scale 0.5 && settings put global animator_duration_scale 0.5",
            R.string.msg_anim_fast
        )
    }

    findViewById<CardView>(R.id.cardDisableBlurs).setOnClickListener {
        runTweakOnce(
            R.id.tvStatusBlurs, { getString(R.string.w_disabled) }, R.string.toast_already_disabled,
            "settings put global disable_window_blurs 1", R.string.msg_blurs_off
        )
    }

    findViewById<CardView>(R.id.cardGamingDnd).setOnClickListener {
        runTweakOnce(
            R.id.tvStatusDnd, { getString(R.string.w_blocked) }, R.string.toast_already_blocked,
            "settings put global heads_up_notifications_enabled 0", R.string.msg_dnd_on
        )
    }

    findViewById<CardView>(R.id.cardDisableDevOptions).setOnClickListener {
        runTweakOnce(
            R.id.tvStatusDevOptions, { getString(R.string.w_disabled) }, R.string.toast_already_disabled,
            "settings put global development_settings_enabled 0", R.string.msg_devopt_off
        )
    }

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
                R.id.tvStatusFstrim, { getString(R.string.w_recently) }, R.string.toast_storage_already,
                "sm fstrim", R.string.msg_fstrim_done
            ) {
                findViewById<TextView>(R.id.tvStatusFstrim).text = getString(R.string.status_recently)
            }
        }
    }

    findViewById<CardView>(R.id.cardFreezeApps).setOnClickListener {
        handlePremiumFeature { showFreezeAppsSheet() }
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
        val lastGame = prefs.getString(Prefs.KEY_LAST_GAME_MODE, "")
        if (!lastGame.isNullOrEmpty()) {
            runAdbCommand("cmd game mode standard $lastGame", getString(R.string.msg_gm_reset)) {
                prefs.edit().putString(Prefs.KEY_LAST_GAME_MODE, "").apply()
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

    bindCommandReset(
        R.id.btnResetRefresh,
        "settings delete system min_refresh_rate && settings delete system peak_refresh_rate",
        R.string.msg_refresh_reset
    )
    bindCommandReset(R.id.btnResetNetwork, "settings delete global tether_dun_required", R.string.msg_net_reset)
    bindCommandReset(
        R.id.btnResetTouch,
        "settings delete system pointer_speed && settings put secure long_press_timeout 400",
        R.string.msg_touch_reset
    )
    bindCommandReset(R.id.btnResetResolution, "wm size reset && wm density reset", R.string.msg_res_reset)
    bindCommandReset(
        R.id.btnResetDns,
        "settings put global private_dns_mode default && settings delete global private_dns_specifier",
        R.string.msg_dns_reset
    )
    bindCommandReset(
        R.id.btnResetAnimations,
        "settings put global window_animation_scale 1 && settings put global transition_animation_scale 1 && settings put global animator_duration_scale 1",
        R.string.msg_anim_reset
    )
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

internal fun MainActivity.resetAllFunctions(prefs: android.content.SharedPreferences) {
    run {
        val lastGame = prefs.getString(Prefs.KEY_LAST_GAME_MODE, "")
        val gameModeReset = if (lastGame.isNullOrEmpty()) "" else "cmd game mode standard $lastGame && "
        val aggressiveReset = if (prefs.getBoolean(Prefs.KEY_FIXED_PERF_AGGRESSIVE, false))
            "{ ${fixedPerfAggressiveRestore(prefs)}; true; } && " else ""

        val resetCmd = """
            cmd power set-fixed-performance-mode-enabled false
            cmd thermalservice reset
            $gameModeReset$aggressiveReset
            settings delete system min_refresh_rate
            settings delete system peak_refresh_rate
            wm size reset
            wm density reset
            settings delete system pointer_speed
            settings put secure long_press_timeout 400
            settings put global private_dns_mode default
            settings delete global private_dns_specifier
            settings put global window_animation_scale 1
            settings put global transition_animation_scale 1
            settings put global animator_duration_scale 1
            settings put global disable_window_blurs 0
            settings put global heads_up_notifications_enabled 1
            settings put global development_settings_enabled 0
        """.trimIndent().replace("\n", " && ").replace("&&  &&", "&&")

        runAdbCommand(resetCmd, getString(R.string.msg_all_reset)) {
            prefs.edit()
                .putBoolean(Prefs.KEY_FIXED_PERF, false)
                .putBoolean(Prefs.KEY_FIXED_PERF_AGGRESSIVE, false)
                .putBoolean(Prefs.KEY_THERMAL_DISABLED, false)
                .putString(Prefs.KEY_LAST_GAME_MODE, "")
                .apply()


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

            fetchSystemStatuses()
        }
    }
}
