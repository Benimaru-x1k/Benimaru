package com.benimaru.official

import android.text.InputType
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import es.dmoral.toasty.Toasty

internal fun MainActivity.showRefreshRateDialog() {
    val (minLayout, minInput) = styledField(this, getString(R.string.hint_min_hz), InputType.TYPE_CLASS_NUMBER, topMarginDp = 0)
    val (maxLayout, maxInput) = styledField(this, getString(R.string.hint_max_hz), InputType.TYPE_CLASS_NUMBER, topMarginDp = 0)

    val fields = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        minLayout.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        maxLayout.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            .apply { marginStart = dp(10) }
        addView(minLayout)
        addView(maxLayout)
    }


    val presetRates = listOf(60, 90, 120, 144)
    val presets = presetRow(this, presetRates.map { "$it Hz" }) { i ->
        minInput.setText(presetRates[i].toString())
        maxInput.setText(presetRates[i].toString())
    }

    val sheet = Sheet(this)
    sheet.icon(R.drawable.ic_tune)
        .title(getString(R.string.dlg_refresh))
        .message(getString(R.string.dlg_refresh_msg))
        .content(presets)
        .content(fields)
        .button(getString(R.string.btn_cancel), SheetStyle.TONAL)
        .button(getString(R.string.btn_apply), SheetStyle.PRIMARY, dismiss = false) {
            val min = minInput.text.toString().trim().toIntOrNull()
            val max = maxInput.text.toString().trim().toIntOrNull()
            when {
                min == null || max == null -> Toasty.warning(this, getString(R.string.toast_valid_numbers), Toast.LENGTH_SHORT, true).show()
                min !in 30..240 || max !in 30..240 -> Toasty.warning(this, getString(R.string.toast_hz_range), Toast.LENGTH_SHORT, true).show()
                min > max -> Toasty.warning(this, getString(R.string.toast_min_gt_max), Toast.LENGTH_SHORT, true).show()
                else -> {
                    sheet.dismiss()
                    val cmd = "settings put system min_refresh_rate $min && settings put system peak_refresh_rate $max"
                    runAdbCommand(cmd, getString(R.string.toast_hz_set, min, max), showAd = true) { fetchSystemStatuses() }
                }
            }
        }
        .show()
}
