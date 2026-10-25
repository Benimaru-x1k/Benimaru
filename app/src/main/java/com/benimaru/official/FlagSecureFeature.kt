package com.benimaru.official

import android.graphics.Color
import android.widget.LinearLayout
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import es.dmoral.toasty.Toasty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val SECURE_SETTING = "disable_secure_windows"
private val C_FS_GREEN = Color.parseColor("#2E9E5B")
private val C_FS_AMBER = Color.parseColor("#E09B00")

private class FlagSecureState(val enabled: Boolean, val supported: Boolean)

private fun MainActivity.readFlagSecureState(): FlagSecureState {
    val enabled = runShell("settings get secure $SECURE_SETTING").out.trim() == "1"
    val supported = runShell("getprop ro.debuggable").out.trim() == "1"
    return FlagSecureState(enabled, supported)
}

internal fun MainActivity.showFlagSecureSheet() {
    if (!hasShizukuPermission()) return
    lifecycleScope.launch {
        val state = withContext(Dispatchers.IO) { readFlagSecureState() }
        if (isFinishing || isDestroyed) return@launch
        renderFlagSecureSheet(state)
    }
}

private fun MainActivity.renderFlagSecureSheet(state: FlagSecureState) {
    val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

    box.addView(
        infoCard(
            this,
            getString(if (state.enabled) R.string.fs_status_on else R.string.fs_status_off),
            if (state.enabled) C_FS_GREEN else null
        )
    )
    if (!state.supported) {
        box.addView(android.view.View(this), LinearLayout.LayoutParams(1, dp(10)))
        box.addView(infoCard(this, getString(R.string.fs_warn_unsupported), C_FS_AMBER))
    }

    Sheet(this)
        .icon(R.drawable.ic_lock)
        .title(getString(R.string.title_flag_secure))
        .message(getString(R.string.fs_msg))
        .content(box)
        .button(getString(R.string.btn_close), SheetStyle.TONAL)
        .button(
            getString(if (state.enabled) R.string.fs_btn_off else R.string.fs_btn_on),
            SheetStyle.PRIMARY
        ) { setFlagSecure(!state.enabled, state.supported) }
        .show()
}

private fun MainActivity.setFlagSecure(enable: Boolean, supported: Boolean) {
    if (!hasShizukuPermission()) return
    lifecycleScope.launch(Dispatchers.IO) {
        val cmd = if (enable) "settings put secure $SECURE_SETTING 1" else "settings delete secure $SECURE_SETTING"
        val result = runShell(cmd)
        val nowOn = runShell("settings get secure $SECURE_SETTING").out.trim() == "1"
        val applied = result.code == 0 && nowOn == enable

        withContext(Dispatchers.Main) {
            when {
                !applied -> Toasty.error(this@setFlagSecure, getString(R.string.fs_toast_failed), Toast.LENGTH_LONG, true).show()
                !enable -> Toasty.success(this@setFlagSecure, getString(R.string.fs_toast_off), Toast.LENGTH_SHORT, true).show()
                supported -> Toasty.success(this@setFlagSecure, getString(R.string.fs_toast_on), Toast.LENGTH_LONG, true).show()
                else -> Toasty.warning(this@setFlagSecure, getString(R.string.fs_toast_on_unsupported), Toast.LENGTH_LONG, true).show()
            }
        }
    }
}
