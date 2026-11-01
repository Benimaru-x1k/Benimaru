package com.benimaru.official

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.graphics.Typeface
import android.text.InputType
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import es.dmoral.toasty.Toasty

private class SpeedPreset(val titleRes: Int, val descRes: Int, val min: Float, val max: Float)

private val SPEED_PRESETS = listOf(
    SpeedPreset(R.string.ac_speed_vfast, R.string.ac_speed_vfast_desc, 0.25f, 0.40f),
    SpeedPreset(R.string.ac_speed_fast, R.string.ac_speed_fast_desc, 0.5f, 0.8f),
    SpeedPreset(R.string.ac_speed_normal, R.string.ac_speed_normal_desc, 1f, 2f),
    SpeedPreset(R.string.ac_speed_slow, R.string.ac_speed_slow_desc, 5f, 0f)
)
private val SPEED_CUSTOM = SPEED_PRESETS.size

private fun sectionTitle(ctx: Context, text: String): TextView = TextView(ctx).apply {
    this.text = text
    textSize = 13f
    setTypeface(typeface, Typeface.BOLD)
    setTextColor(Ui.onSurfaceVariant(ctx))
    setPadding(ctx.dp(4), ctx.dp(18), ctx.dp(4), 0)
}

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
    val modes = listOf(
        getString(R.string.mode_click) to getString(R.string.mode_click_desc),
        getString(R.string.mode_swipe) to getString(R.string.mode_swipe_desc),
        getString(R.string.mode_both) to getString(R.string.mode_both_desc)
    )

    val savedMin = prefs.getFloat(AutoClickService.KEY_INTERVAL, 5f)
    val savedMax = prefs.getFloat(AutoClickService.KEY_INTERVAL_MAX, 0f)
    var speed = SPEED_PRESETS.indexOfFirst { it.min == savedMin && it.max == savedMax }.let { if (it < 0) SPEED_CUSTOM else it }

    val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

    val (intervalLayout, intervalEdit) = styledField(this, getString(R.string.hint_interval), InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
    intervalEdit.setText(savedMin.toString())
    val (maxLayout, maxEdit) = styledField(this, getString(R.string.hint_interval_max), InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
    savedMax.takeIf { it > 0f }?.let { maxEdit.setText(it.toString()) }
    val customBox = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(intervalLayout)
        addView(maxLayout)
    }

    val (jitterLayout, jitterEdit) = styledField(this, getString(R.string.hint_jitter), InputType.TYPE_CLASS_NUMBER)
    jitterEdit.setText(prefs.getInt(AutoClickService.KEY_JITTER, AutoClickService.DEFAULT_JITTER_PX).toString())
    val (holdLayout, holdEdit) = styledField(this, getString(R.string.hint_hold), InputType.TYPE_CLASS_NUMBER)
    holdEdit.setText(prefs.getInt(AutoClickService.KEY_HOLD, AutoClickService.DEFAULT_HOLD_MS).toString())
    val (lenLayout, lenEdit) = styledField(this, getString(R.string.hint_swipe_len), InputType.TYPE_CLASS_NUMBER)
    lenEdit.setText(prefs.getInt(AutoClickService.KEY_SWIPE_LEN, 300).toString())
    val (xLayout, xEdit) = styledField(this, getString(R.string.hint_x), InputType.TYPE_CLASS_NUMBER)
    val (yLayout, yEdit) = styledField(this, getString(R.string.hint_y), InputType.TYPE_CLASS_NUMBER)
    prefs.getInt(AutoClickService.KEY_X, -1).takeIf { it >= 0 }?.let { xEdit.setText(it.toString()) }
    prefs.getInt(AutoClickService.KEY_Y, -1).takeIf { it >= 0 }?.let { yEdit.setText(it.toString()) }
    val advancedBox = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        visibility = View.GONE
        addView(infoCard(this@showAutoClickDialog, getString(R.string.ac_advanced_info)).apply {
            (layoutParams as? LinearLayout.LayoutParams ?: LinearLayout.LayoutParams(-1, -2)).also { it.topMargin = dp(10); layoutParams = it }
        })
        listOf(jitterLayout, holdLayout, lenLayout, xLayout, yLayout).forEach { addView(it) }
    }
    val advancedToggle = TextView(this).apply {
        textSize = 14f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(Ui.accent(this@showAutoClickDialog))
        setPadding(dp(4), dp(18), dp(4), dp(6))
        text = getString(R.string.ac_advanced_show)
        setOnClickListener {
            val show = advancedBox.visibility != View.VISIBLE
            advancedBox.visibility = if (show) View.VISIBLE else View.GONE
            text = getString(if (show) R.string.ac_advanced_hide else R.string.ac_advanced_show)
        }
    }

    val modeBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    val speedBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    fun renderModes() {
        modeBox.removeAllViews()
        modes.forEachIndexed { i, (t, sub) ->
            modeBox.addView(optionRow(this, t, sub, selected = i == mode) { mode = i; renderModes() })
        }
    }
    fun renderSpeeds() {
        speedBox.removeAllViews()
        SPEED_PRESETS.forEachIndexed { i, sp ->
            speedBox.addView(optionRow(this, getString(sp.titleRes), getString(sp.descRes), selected = i == speed) { speed = i; renderSpeeds() })
        }
        speedBox.addView(optionRow(this, getString(R.string.ac_speed_custom), getString(R.string.ac_speed_custom_desc), selected = speed == SPEED_CUSTOM) { speed = SPEED_CUSTOM; renderSpeeds() })
        customBox.visibility = if (speed == SPEED_CUSTOM) View.VISIBLE else View.GONE
    }
    renderModes()
    renderSpeeds()

    content.addView(infoCard(this, getString(R.string.ac_steps), Ui.accent(this)))
    content.addView(sectionTitle(this, getString(R.string.ac_section_do)))
    content.addView(modeBox)
    content.addView(sectionTitle(this, getString(R.string.ac_section_speed)))
    content.addView(speedBox)
    content.addView(customBox)
    content.addView(advancedToggle)
    content.addView(advancedBox)
    content.addView(infoCard(this, getString(R.string.info_autoclick)).apply {
        (layoutParams as? LinearLayout.LayoutParams ?: LinearLayout.LayoutParams(-1, -2)).also { it.topMargin = dp(14); layoutParams = it }
    })

    Sheet(this)
        .icon(R.drawable.ic_gamepad)
        .title(getString(R.string.dlg_autoclick_title))
        .message(getString(R.string.dlg_autoclick_msg))
        .content(content)
        .button(getString(R.string.btn_cancel), SheetStyle.TEXT)
        .button(getString(R.string.btn_show_button), SheetStyle.PRIMARY) {
            val interval: Float
            val maxInterval: Float
            if (speed == SPEED_CUSTOM) {
                interval = (intervalEdit.text?.toString()?.toFloatOrNull() ?: 1f).coerceIn(0.05f, 3600f)
                val maxRaw = maxEdit.text?.toString()?.toFloatOrNull() ?: 0f
                maxInterval = if (maxRaw > interval) maxRaw.coerceAtMost(3600f) else 0f // blank / not larger = fixed time
            } else {
                interval = SPEED_PRESETS[speed].min
                maxInterval = SPEED_PRESETS[speed].max
            }
            val jitter = (jitterEdit.text?.toString()?.toIntOrNull() ?: AutoClickService.DEFAULT_JITTER_PX).coerceIn(0, 50)
            val hold = (holdEdit.text?.toString()?.toIntOrNull() ?: AutoClickService.DEFAULT_HOLD_MS).coerceIn(0, 500)
            prefs.edit()
                .putInt(AutoClickService.KEY_MODE, mode)
                .putFloat(AutoClickService.KEY_INTERVAL, interval)
                .putFloat(AutoClickService.KEY_INTERVAL_MAX, maxInterval)
                .putInt(AutoClickService.KEY_JITTER, jitter)
                .putInt(AutoClickService.KEY_HOLD, hold)
                .putInt(AutoClickService.KEY_X, xEdit.text?.toString()?.toIntOrNull() ?: -1)
                .putInt(AutoClickService.KEY_Y, yEdit.text?.toString()?.toIntOrNull() ?: -1)
                .putInt(AutoClickService.KEY_SWIPE_LEN, lenEdit.text?.toString()?.toIntOrNull() ?: 300)
                .putBoolean(AutoClickService.KEY_ENABLED, true)
                .apply()
            overlayGraceUntil = android.os.SystemClock.elapsedRealtime() + 2500
            startService(Intent(this, AutoClickService::class.java))
            isAutoClickEnabled = true
            findViewById<TextView>(R.id.tvStatusAutoClick).text = getString(R.string.status_autoclick, AutoClickService.modeLabel(this, mode), if (maxInterval > interval) "$interval–$maxInterval" else interval.toString())
            Toasty.success(this, getString(R.string.ac_toast_ready), Toast.LENGTH_LONG, true).show()
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
