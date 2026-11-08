package com.benimaru.official

import android.graphics.Color
import android.os.Build
import android.widget.LinearLayout
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import es.dmoral.toasty.Toasty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val C_FF_GREEN = Color.parseColor("#2E9E5B")
private val C_FF_AMBER = Color.parseColor("#E09B00")

// Global settings that make Android allow free-floating, resizable windows.
//  - enable_freeform_support / freeform_window_management : the freeform windowing mode itself
//  - force_resizable_activities                           : lets every app be resized
//  - enable_non_resizable_multi_window                    : (Android 14+) multi-window for non-resizable apps
internal const val FREEFORM_ENABLE_CMD =
    "settings put global enable_freeform_support 1; " +
    "settings put global freeform_window_management 1; " +
    "settings put global force_resizable_activities 1; " +
    "settings put global enable_non_resizable_multi_window 1"

internal const val FREEFORM_RESET_CMD =
    "settings delete global enable_freeform_support; " +
    "settings delete global freeform_window_management; " +
    "settings delete global force_resizable_activities; " +
    "settings delete global enable_non_resizable_multi_window"

private class FreeformState(val enabled: Boolean)

private fun MainActivity.readFreeformState(): FreeformState {
    val freeform = runShell("settings get global enable_freeform_support").out.trim() == "1"
    val resizable = runShell("settings get global force_resizable_activities").out.trim() == "1"
    return FreeformState(freeform && resizable)
}

internal fun MainActivity.showFreeformSheet() {
    if (!hasShizukuPermission()) return
    lifecycleScope.launch {
        val state = withContext(Dispatchers.IO) { readFreeformState() }
        if (isFinishing || isDestroyed) return@launch
        renderFreeformSheet(state)
    }
}

private fun MainActivity.renderFreeformSheet(state: FreeformState) {
    val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

    box.addView(
        infoCard(
            this,
            getString(if (state.enabled) R.string.ff_status_on else R.string.ff_status_off),
            if (state.enabled) C_FF_GREEN else null
        )
    )
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
        box.addView(android.view.View(this), LinearLayout.LayoutParams(1, dp(10)))
        box.addView(infoCard(this, getString(R.string.ff_warn_old_android), C_FF_AMBER))
    } else {
        box.addView(android.view.View(this), LinearLayout.LayoutParams(1, dp(10)))
        box.addView(infoCard(this, getString(R.string.ff_hint), null))
    }

    Sheet(this)
        .icon(R.drawable.ic_freeform)
        .title(getString(R.string.title_freeform))
        .message(getString(R.string.ff_msg))
        .content(box)
        .button(getString(R.string.btn_close), SheetStyle.TONAL)
        .button(
            getString(if (state.enabled) R.string.ff_btn_off else R.string.ff_btn_on),
            SheetStyle.PRIMARY
        ) { setFreeform(!state.enabled) }
        .show()
}

private fun MainActivity.setFreeform(enable: Boolean) {
    if (!hasShizukuPermission()) return
    lifecycleScope.launch(Dispatchers.IO) {
        val result = runShell(if (enable) FREEFORM_ENABLE_CMD else FREEFORM_RESET_CMD)
        val nowOn = readFreeformState().enabled
        val applied = result.code == 0 && nowOn == enable

        withContext(Dispatchers.Main) {
            when {
                !applied -> Toasty.error(this@setFreeform, getString(R.string.ff_toast_failed), Toast.LENGTH_LONG, true).show()
                enable -> Toasty.success(this@setFreeform, getString(R.string.ff_toast_on), Toast.LENGTH_LONG, true).show()
                else -> Toasty.success(this@setFreeform, getString(R.string.ff_toast_off), Toast.LENGTH_SHORT, true).show()
            }
        }
    }
}
