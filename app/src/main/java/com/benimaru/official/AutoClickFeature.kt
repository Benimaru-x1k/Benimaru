package com.benimaru.official

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.InputType
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import es.dmoral.toasty.Toasty

internal fun MainActivity.showAutoClickDialog() {
    if (!Settings.canDrawOverlays(this)) {
        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        Toasty.info(this, getString(R.string.toast_allow_overlay), Toast.LENGTH_LONG, true).show()
        return
    }
    if (!hasShizukuPermission()) {
        Toasty.error(this, getString(R.string.toast_start_shizuku), Toast.LENGTH_LONG, true).show()
        return
    }

    val prefs = getSharedPreferences(Prefs.MAIN, Context.MODE_PRIVATE)
    var mode = prefs.getInt(AutoClickService.KEY_MODE, AutoClickService.MODE_TAP)
    val modes = listOf(getString(R.string.mode_click) to getString(R.string.mode_click_desc), getString(R.string.mode_swipe) to getString(R.string.mode_swipe_desc), getString(R.string.mode_both) to getString(R.string.mode_both_desc))

    val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    val rows = mutableListOf<LinearLayout>()
    fun renderModes() {
        rows.forEach { content.removeView(it) }
        rows.clear()
        modes.forEachIndexed { i, (t, sub) ->
            val row = optionRow(this, t, sub, selected = i == mode) { mode = i; renderModes() }
            content.addView(row, i)
            rows += row
        }
    }
    renderModes()

    val (intervalLayout, intervalEdit) = styledField(this, getString(R.string.hint_interval), InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
    intervalEdit.setText(prefs.getFloat(AutoClickService.KEY_INTERVAL, 5f).toString())
    val (xLayout, xEdit) = styledField(this, getString(R.string.hint_x), InputType.TYPE_CLASS_NUMBER)
    val (yLayout, yEdit) = styledField(this, getString(R.string.hint_y), InputType.TYPE_CLASS_NUMBER)
    val (lenLayout, lenEdit) = styledField(this, getString(R.string.hint_swipe_len), InputType.TYPE_CLASS_NUMBER)
    prefs.getInt(AutoClickService.KEY_X, -1).takeIf { it >= 0 }?.let { xEdit.setText(it.toString()) }
    prefs.getInt(AutoClickService.KEY_Y, -1).takeIf { it >= 0 }?.let { yEdit.setText(it.toString()) }
    lenEdit.setText(prefs.getInt(AutoClickService.KEY_SWIPE_LEN, 300).toString())
    listOf(intervalLayout, xLayout, yLayout, lenLayout).forEach { content.addView(it) }
    content.addView(infoCard(this, getString(R.string.info_autoclick)))

    Sheet(this)
        .icon(R.drawable.ic_gamepad)
        .title(getString(R.string.dlg_autoclick_title))
        .message(getString(R.string.dlg_autoclick_msg))
        .content(content)
        .button(getString(R.string.btn_cancel), SheetStyle.TEXT)
        .button(getString(R.string.btn_show_button), SheetStyle.PRIMARY) {
            val interval = (intervalEdit.text?.toString()?.toFloatOrNull() ?: 5f).coerceIn(0.05f, 3600f)
            prefs.edit()
                .putInt(AutoClickService.KEY_MODE, mode)
                .putFloat(AutoClickService.KEY_INTERVAL, interval)
                .putInt(AutoClickService.KEY_X, xEdit.text?.toString()?.toIntOrNull() ?: -1)
                .putInt(AutoClickService.KEY_Y, yEdit.text?.toString()?.toIntOrNull() ?: -1)
                .putInt(AutoClickService.KEY_SWIPE_LEN, lenEdit.text?.toString()?.toIntOrNull() ?: 300)
                .putBoolean(AutoClickService.KEY_ENABLED, true)
                .apply()
            startService(Intent(this, AutoClickService::class.java))
            isAutoClickEnabled = true
            findViewById<TextView>(R.id.tvStatusAutoClick).text = getString(R.string.status_autoclick, AutoClickService.modeLabel(this, mode), interval.toString())
            Toasty.success(this, getString(R.string.toast_float_ready), Toast.LENGTH_SHORT, true).show()
        }
        .show()
}

internal fun MainActivity.stopAutoClickService() {
    stopService(Intent(this, AutoClickService::class.java))
    isAutoClickEnabled = false
    getSharedPreferences(Prefs.MAIN, Context.MODE_PRIVATE).edit()
        .putBoolean(AutoClickService.KEY_ENABLED, false).apply()
    findViewById<TextView>(R.id.tvStatusAutoClick).text = getString(R.string.status_disabled)
}
