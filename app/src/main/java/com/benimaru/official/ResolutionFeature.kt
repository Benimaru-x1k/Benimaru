package com.benimaru.official

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import es.dmoral.toasty.Toasty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

internal fun MainActivity.showResolutionModeDialog() {
    val sheet = Sheet(this)
    val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    list.addView(optionRow(
        this, getString(R.string.res_direct), getString(R.string.res_direct_desc),
        icon = Ui.drawable(this, R.drawable.ic_tune)
    ) {
        sheet.dismiss()
        showResolutionDialog()
    })
    list.addView(optionRow(
        this, getString(R.string.res_floating), getString(R.string.res_floating_desc),
        icon = Ui.drawable(this, R.drawable.ic_play)
    ) {
        sheet.dismiss()
        launchFloatingResolution()
    })
    sheet.icon(R.drawable.ic_tune)
        .title(getString(R.string.dlg_change_res))
        .message(getString(R.string.dlg_change_res_msg))
        .content(list)
        .button(getString(R.string.btn_cancel), SheetStyle.TEXT)
        .show()
}

internal fun MainActivity.launchFloatingResolution() {
    if (!android.provider.Settings.canDrawOverlays(this)) {
        val intent = Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        startActivity(intent)
        Toasty.info(this, getString(R.string.toast_allow_overlay), Toast.LENGTH_LONG, true).show()
    } else {
        startService(Intent(this, FloatingResolutionService::class.java))
        Toasty.success(this, getString(R.string.toast_float_enabled), Toast.LENGTH_SHORT, true).show()
    }
}

internal fun MainActivity.showResolutionDialog() {
    val metrics = resources.displayMetrics
    val currentDpi = metrics.densityDpi
    val currentWidth = metrics.widthPixels.toFloat()
    val currentHeight = metrics.heightPixels.toFloat()
    val aspectRatio = currentHeight / currentWidth

    val autoMatchSwitch = MaterialSwitch(this).apply {
        text = getString(R.string.sw_aspect)
        isChecked = false
    }
    val autoDpiSwitch = MaterialSwitch(this).apply {
        text = getString(R.string.sw_dpi)
        isChecked = true
    }
    val (widthLayout, widthInput) = styledField(this, getString(R.string.hint_width_now, currentWidth.toInt()), InputType.TYPE_CLASS_NUMBER, topMarginDp = 0)
    val (heightLayout, heightInput) = styledField(this, getString(R.string.hint_height_now, currentHeight.toInt()), InputType.TYPE_CLASS_NUMBER, topMarginDp = 0)

    val fields = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        widthLayout.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        heightLayout.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            .apply { marginStart = dp(10) }
        addView(widthLayout)
        addView(heightLayout)
    }

    var isAutoCalculating = false
    fun link(source: TextInputEditText, target: TextInputEditText, calc: (Int) -> Int) {
        source.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!autoMatchSwitch.isChecked || isAutoCalculating) return
                isAutoCalculating = true
                val v = s.toString().toIntOrNull()
                target.setText(if (v != null) calc(v).toString() else "")
                isAutoCalculating = false
            }
        })
    }
    link(widthInput, heightInput) { (it * aspectRatio).toInt() }
    link(heightInput, widthInput) { (it / aspectRatio).toInt() }

    autoMatchSwitch.setOnCheckedChangeListener { _, isChecked ->
        if (!isChecked || isAutoCalculating) return@setOnCheckedChangeListener
        val w = widthInput.text.toString().toIntOrNull()
        val h = heightInput.text.toString().toIntOrNull()
        isAutoCalculating = true
        if (w != null) heightInput.setText((w * aspectRatio).toInt().toString())
        else if (h != null) widthInput.setText((h / aspectRatio).toInt().toString())
        isAutoCalculating = false
    }

    val content = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(autoMatchSwitch, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(4) })
        addView(autoDpiSwitch, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(12) })
        addView(fields)
        addView(infoCard(this@showResolutionDialog, getString(R.string.info_dpi)),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(14) })
    }

    val sheet = Sheet(this)
    sheet.icon(R.drawable.ic_tune)
        .title(getString(R.string.dlg_change_res))
        .content(content)
        .button(getString(R.string.btn_cancel), SheetStyle.TONAL)
        .button(getString(R.string.btn_preview), SheetStyle.PRIMARY, dismiss = false) {
            val newWidth = widthInput.text.toString().trim().toIntOrNull()
            val newHeight = heightInput.text.toString().trim().toIntOrNull()
            if (newWidth == null || newHeight == null) {
                Toasty.warning(this, getString(R.string.toast_enter_wh), Toast.LENGTH_SHORT, true).show()
                return@button
            }
            if (newWidth < 300 || newHeight < 300) {
                Toasty.warning(this, getString(R.string.toast_res_small), Toast.LENGTH_SHORT, true).show()
                return@button
            }
            if (!hasShizukuPermission()) return@button
            sheet.dismiss()

            val cmd = resolutionCommand(
                newWidth, newHeight, autoDpiSwitch.isChecked, currentWidth, currentHeight, currentDpi
            )
            applyResolutionCommand(cmd)
        }
        .show()
}

private fun resolutionCommand(
    newWidth: Int, newHeight: Int, adjustDpi: Boolean,
    currentWidth: Float, currentHeight: Float, currentDpi: Int
): String = if (adjustDpi) {
    val scalingRatio = minOf(newWidth / currentWidth, newHeight / currentHeight)
    val newDpi = (scalingRatio * currentDpi).toInt()
    "wm size ${newWidth}x${newHeight} && wm density $newDpi"
} else {
    "wm size ${newWidth}x${newHeight}"
}

private fun MainActivity.applyResolutionCommand(cmd: String) {
    lifecycleScope.launch(Dispatchers.IO) {
        val process = Shizuku.newProcess(arrayOf("sh", "-c", cmd), null, null)
        if (process.waitFor() == 0) {
            withContext(Dispatchers.Main) { showResolutionPreviewCountdown() }
        } else {
            withContext(Dispatchers.Main) {
                Toasty.error(this@applyResolutionCommand, getString(R.string.toast_res_fail), Toast.LENGTH_SHORT, true).show()
            }
        }
    }
}

internal fun MainActivity.showResolutionPreviewCountdown() {
    var timerJob: Job? = null
    var decided = false

    val bar = LinearProgressIndicator(this).apply {
        max = 6000
        progress = 6000
        isIndeterminate = false
        trackThickness = dp(8)
        trackCornerRadius = dp(4)
        setIndicatorColor(Ui.accent(this@showResolutionPreviewCountdown))
    }

    val sheet = Sheet(this)
    sheet.icon(R.drawable.ic_warning, Color.parseColor("#E09B00"))
        .title(getString(R.string.dlg_keep_res))
        .message(getString(R.string.dlg_keep_res_msg))
        .content(bar)
        .cancelable(false)
        .button(getString(R.string.btn_revert), SheetStyle.TONAL) {
            decided = true
            timerJob?.cancel()
            runAdbCommand("wm size reset && wm density reset", getString(R.string.msg_res_reverted)) { fetchSystemStatuses() }
        }
        .button(getString(R.string.btn_keep), SheetStyle.PRIMARY) {
            decided = true
            timerJob?.cancel()
            Toasty.success(this, getString(R.string.toast_res_saved), Toast.LENGTH_SHORT, true).show()
            fetchSystemStatuses()
            showInterstitialAd()
        }
        .show()

    timerJob = lifecycleScope.launch(Dispatchers.Main) {
        for (step in 60 downTo 0) {
            bar.progress = step * 100
            sheet.messageView?.text = getString(R.string.revert_n, (step + 9) / 10)
            delay(100)
        }
        if (!decided) {
            decided = true
            sheet.dismiss()
            runAdbCommand("wm size reset && wm density reset", getString(R.string.msg_res_auto_reverted)) { fetchSystemStatuses() }
        }
    }
}
