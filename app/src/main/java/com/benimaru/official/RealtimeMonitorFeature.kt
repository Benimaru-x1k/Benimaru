package com.benimaru.official

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.graphics.Typeface
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import es.dmoral.toasty.Toasty
import kotlin.math.abs

private val MONITOR_ROW_DEFS = listOf(
    R.string.monitor_row_cpu to 1,
    R.string.monitor_row_ram to 2,
    R.string.monitor_row_sto to 4,
    R.string.monitor_row_bat to 8,
    R.string.monitor_row_disp to 16,
    R.string.monitor_row_net to 32
)

private val MONITOR_SCALES = floatArrayOf(0.75f, 1f, 1.4f)

private fun monitorSectionTitle(ctx: Context, res: Int): TextView = TextView(ctx).apply {
    text = ctx.getString(res)
    textSize = 13f
    setTypeface(typeface, Typeface.BOLD)
    setTextColor(Ui.onSurfaceVariant(ctx))
    setPadding(ctx.dp(4), ctx.dp(18), ctx.dp(4), 0)
}

internal fun MainActivity.toggleRealtimeMonitor() {
    if (!Settings.canDrawOverlays(this)) {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        startActivity(intent)
        Toasty.info(this, getString(R.string.toast_allow_overlay), Toast.LENGTH_LONG, true).show()
        return
    }

    val intent = Intent(this, RealtimeMonitorService::class.java)
    val prefs = getSharedPreferences(Prefs.MAIN, Context.MODE_PRIVATE)

    if (isMonitorEnabled) {
        stopService(intent)
        isMonitorEnabled = false
        prefs.edit().putBoolean(Prefs.KEY_MONITOR_ENABLED, false).apply()
        findViewById<TextView>(R.id.tvStatusMonitor).text = getString(R.string.status_disabled)
        Toasty.success(this, getString(R.string.toast_monitor_off), Toast.LENGTH_SHORT, true).show()
    } else {
        overlayGraceUntil = android.os.SystemClock.elapsedRealtime() + 2500
        startService(intent)
        isMonitorEnabled = true
        prefs.edit().putBoolean(Prefs.KEY_MONITOR_ENABLED, true).apply()
        findViewById<TextView>(R.id.tvStatusMonitor).text = getString(R.string.status_enabled)
        Toasty.success(this, getString(R.string.monitor_toast_ready), Toast.LENGTH_LONG, true).show()
        showInterstitialAd()
    }
}

internal fun MainActivity.showMonitorSetupDialog() {
    if (!Settings.canDrawOverlays(this)) {
        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        Toasty.info(this, getString(R.string.toast_allow_overlay), Toast.LENGTH_LONG, true).show()
        return
    }

    val prefs = getSharedPreferences(Prefs.MAIN, Context.MODE_PRIVATE)
    var mask = prefs.getInt(Prefs.KEY_MONITOR_ROWS, RealtimeMonitorService.ROWS_ALL)
        .takeIf { it != 0 } ?: RealtimeMonitorService.ROWS_ALL

    val savedScale = prefs.getFloat(Prefs.KEY_MONITOR_SCALE, 1f)
    val closestSize = MONITOR_SCALES.indices.minByOrNull { abs(MONITOR_SCALES[it] - savedScale) } ?: 1
    var pickedSize: Int? = null

    val sizeLabels = listOf(R.string.monitor_size_small, R.string.monitor_size_medium, R.string.monitor_size_large)

    val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    val rowsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    val sizeBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

    fun renderRows() {
        rowsBox.removeAllViews()
        MONITOR_ROW_DEFS.forEach { (res, bit) ->
            rowsBox.addView(optionRow(this, getString(res), selected = (mask and bit) != 0) {
                val newMask = mask xor bit
                if (newMask != 0) {
                    mask = newMask
                } else {
                    Toasty.info(this, getString(R.string.monitor_keep_one), Toast.LENGTH_SHORT, true).show()
                }
                renderRows()
            })
        }
    }
    fun renderSizes() {
        sizeBox.removeAllViews()
        sizeLabels.forEachIndexed { i, res ->
            sizeBox.addView(optionRow(this, getString(res), selected = i == (pickedSize ?: closestSize)) {
                pickedSize = i
                renderSizes()
            })
        }
    }
    renderRows()
    renderSizes()

    content.addView(infoCard(this, getString(R.string.monitor_steps), Ui.accent(this)))
    content.addView(monitorSectionTitle(this, R.string.monitor_show))
    content.addView(rowsBox)
    content.addView(monitorSectionTitle(this, R.string.monitor_section_size))
    content.addView(sizeBox)

    Sheet(this)
        .icon(R.drawable.ic_monitor)
        .title(getString(R.string.monitor_sheet_title))
        .message(getString(R.string.dlg_monitor_msg))
        .content(content)
        .button(getString(R.string.btn_cancel), SheetStyle.TEXT)
        .button(getString(R.string.btn_show_monitor), SheetStyle.PRIMARY) {
            val edit = prefs.edit().putInt(Prefs.KEY_MONITOR_ROWS, mask)
            pickedSize?.let { edit.putFloat(Prefs.KEY_MONITOR_SCALE, MONITOR_SCALES[it]) }
            edit.apply()
            if (!isMonitorEnabled) toggleRealtimeMonitor()
        }
        .show()
}

internal fun MainActivity.showMonitorSheet() {
    val prefs = getSharedPreferences(Prefs.MAIN, Context.MODE_PRIVATE)
    var mask = prefs.getInt(Prefs.KEY_MONITOR_ROWS, RealtimeMonitorService.ROWS_ALL)
    var sheetRef: Sheet? = null

    fun send(action: String, scale: Float? = null) {
        val i = Intent(this, RealtimeMonitorService::class.java).setAction(action)
        if (scale != null) i.putExtra(RealtimeMonitorService.EXTRA_SCALE, scale)
        startService(i)
    }

    val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

    content.addView(infoCard(this, getString(R.string.monitor_running_info), Ui.accent(this)))

    content.addView(
        optionRow(
            this,
            getString(R.string.monitor_move),
            getString(R.string.monitor_move_desc),
            icon = AppCompatResources.getDrawable(this, R.drawable.ic_monitor)
        ) {
            sheetRef?.dismiss()
            send(RealtimeMonitorService.ACTION_EDIT)
        }
    )

    content.addView(monitorSectionTitle(this, R.string.monitor_size))
    content.addView(
        presetRow(this, listOf(getString(R.string.monitor_size_small), getString(R.string.monitor_size_medium), getString(R.string.monitor_size_large))) { i ->
            send(RealtimeMonitorService.ACTION_SIZE, MONITOR_SCALES[i])
        }.apply { (layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(8) }
    )

    content.addView(monitorSectionTitle(this, R.string.monitor_show))
    val rowsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    fun renderRows() {
        rowsBox.removeAllViews()
        MONITOR_ROW_DEFS.forEach { (res, bit) ->
            rowsBox.addView(optionRow(this, getString(res), selected = (mask and bit) != 0) {
                val newMask = mask xor bit
                if (newMask != 0) {
                    mask = newMask
                    prefs.edit().putInt(Prefs.KEY_MONITOR_ROWS, mask).apply()
                    send(RealtimeMonitorService.ACTION_ROWS)
                } else {
                    Toasty.info(this, getString(R.string.monitor_keep_one), Toast.LENGTH_SHORT, true).show()
                }
                renderRows()
            })
        }
    }
    renderRows()
    content.addView(rowsBox)

    content.addView(
        optionRow(this, getString(R.string.monitor_reset_pos)) { send(RealtimeMonitorService.ACTION_RESET) }
            .apply { (layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(18) }
    )

    sheetRef = Sheet(this)
        .icon(R.drawable.ic_monitor)
        .title(getString(R.string.monitor_sheet_title))
        .message(getString(R.string.monitor_sheet_msg))
        .content(content)
        .button(getString(R.string.monitor_turn_off), SheetStyle.DANGER) { toggleRealtimeMonitor() }
        .button(getString(R.string.btn_close), SheetStyle.TONAL)
        .show()
}
