package com.benimaru.official

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.res.ResourcesCompat
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import es.dmoral.toasty.Toasty

private val CROSSHAIR_STYLES = listOf(
    "Cross", "Dot", "Circle", "Cross with Circle", "Square", "Target",
    "Gap Cross", "Diamond", "Triangle"
)
private val CROSSHAIR_COLORS = listOf("White", "Black", "Red", "Green", "Blue", "Yellow", "Cyan", "Magenta")
private val CROSSHAIR_COLOR_VALUES = listOf(
    Color.WHITE, Color.BLACK, Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.CYAN, Color.MAGENTA
)
private val CROSSHAIR_SIZES = listOf("Tiny", "Small", "Medium", "Large", "Extra Large")

private fun MainActivity.crosshairSectionLabel(text: String) = TextView(this).apply {
    this.text = text
    textSize = 12f
    letterSpacing = 0.08f
    setTypeface(typeface, android.graphics.Typeface.BOLD)
    setTextColor(Ui.onSurfaceVariant(this@crosshairSectionLabel))
    setPadding(dp(4), dp(18), 0, dp(8))
}

private fun MainActivity.createCrosshairStyleChips(
    styles: List<String>, selectedIdx: Int, onSelected: (Int) -> Unit
): ChipGroup {
    val chipGroup = ChipGroup(this).apply {
        isSingleSelection = true
        isSelectionRequired = true
        chipSpacingHorizontal = dp(8)
    }
    val chipIds = styles.map { View.generateViewId() }
    styles.forEachIndexed { i, name ->
        chipGroup.addView(Chip(this).apply {
            id = chipIds[i]
            text = xl(name)
            isCheckable = true
            isChecked = i == selectedIdx
            isCheckedIconVisible = false
            ResourcesCompat.getFont(this@createCrosshairStyleChips, R.font.lexendregular)?.let { typeface = it }
        })
    }
    chipGroup.setOnCheckedChangeListener { _, checkedId ->
        val idx = chipIds.indexOf(checkedId)
        if (idx >= 0) onSelected(idx)
    }
    return chipGroup
}

private fun MainActivity.createCrosshairSizeSlider(
    sizes: List<String>, sizeIdx: Int, onChange: (Int) -> Unit
) = com.google.android.material.slider.Slider(this).apply {
    valueFrom = 0f
    valueTo = (sizes.size - 1).toFloat()
    stepSize = 1f
    value = sizeIdx.toFloat()
    setLabelFormatter { v -> xl(sizes[v.toInt().coerceIn(sizes.indices)]) }
    addOnChangeListener { _, v, fromUser ->
        if (fromUser) onChange(v.toInt())
    }
}

internal fun MainActivity.showCrosshairConfigDialog() {
    if (!Settings.canDrawOverlays(this)) {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        startActivity(intent)
        Toasty.info(this, getString(R.string.toast_allow_overlay_cross), Toast.LENGTH_LONG, true).show()
        return
    }

    val prefs = getSharedPreferences(Prefs.MAIN, Context.MODE_PRIVATE)
    val styles = CROSSHAIR_STYLES
    val colors = CROSSHAIR_COLORS
    val colorValues = CROSSHAIR_COLOR_VALUES
    val sizes = CROSSHAIR_SIZES

    var styleIdx = prefs.getInt(Prefs.KEY_CROSSHAIR_STYLE_IDX, 0).coerceIn(styles.indices)
    var colorIdx = prefs.getInt(Prefs.KEY_CROSSHAIR_COLOR_IDX, 2).coerceIn(colors.indices)
    var sizeIdx = prefs.getInt(Prefs.KEY_CROSSHAIR_SIZE_IDX, 2).coerceIn(sizes.indices)
    val savedStyle = styleIdx
    val savedColor = colorIdx
    val savedSize = sizeIdx
    val wasEnabled = prefs.getBoolean(Prefs.KEY_CROSSHAIR_ENABLED, false)
    var saved = false

    val preview = CrosshairPreviewView(this).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(120))
    }

    fun refresh() {
        preview.set(styles[styleIdx], colors[colorIdx], sizes[sizeIdx])

        if (isCrosshairEnabled) pushCrosshair(styles[styleIdx], colors[colorIdx], sizes[sizeIdx])
    }

    val chipGroup = createCrosshairStyleChips(styles, styleIdx) { idx ->
        styleIdx = idx
        refresh()
    }


    val swatchRow = colorSwatchRow(this, colorValues, colors.map { xl(it) }, colorIdx) { i ->
        colorIdx = i
        refresh()
    }


    val sizeLabel = crosshairSectionLabel(getString(R.string.size_label, xl(sizes[sizeIdx])))
    val slider = createCrosshairSizeSlider(sizes, sizeIdx) { idx ->
        sizeIdx = idx
        sizeLabel.text = getString(R.string.size_label, xl(sizes[sizeIdx]))
        refresh()
    }

    val hScroll = HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        addView(chipGroup)
    }

    val content = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        addView(preview)
        addView(crosshairSectionLabel(getString(R.string.lbl_shape)))
        addView(hScroll)
        addView(crosshairSectionLabel(getString(R.string.lbl_color)))
        addView(swatchRow)
        addView(sizeLabel)
        addView(slider)
    }
    preview.set(styles[styleIdx], colors[colorIdx], sizes[sizeIdx])

    val sheet = Sheet(this)
    sheet.icon(R.drawable.ic_tune)
        .title(getString(R.string.dlg_crosshair))
        .message(getString(R.string.dlg_crosshair_msg))
        .content(content)
        .button(getString(R.string.btn_cancel), SheetStyle.TONAL)
        .button(if (wasEnabled) getString(R.string.btn_save) else getString(R.string.btn_start), SheetStyle.PRIMARY) {
            saved = true
            prefs.edit()
                .putInt(Prefs.KEY_CROSSHAIR_STYLE_IDX, styleIdx)
                .putInt(Prefs.KEY_CROSSHAIR_COLOR_IDX, colorIdx)
                .putInt(Prefs.KEY_CROSSHAIR_SIZE_IDX, sizeIdx)
                .apply()
            startCrosshairService(styles[styleIdx], colors[colorIdx], sizes[sizeIdx])
        }
    if (wasEnabled) {
        sheet.button(getString(R.string.btn_turn_off), SheetStyle.DANGER) {
            saved = true
            stopCrosshairService()
        }
    }
    sheet.onDismiss {

        if (!saved && isCrosshairEnabled) {
            pushCrosshair(styles[savedStyle], colors[savedColor], sizes[savedSize])
        }
    }
    sheet.show()
}

internal fun MainActivity.pushCrosshair(style: String, color: String, size: String) {
    startService(Intent(this, CrosshairService::class.java).apply {
        putExtra("STYLE", style)
        putExtra(getString(R.string.lbl_color), color)
        putExtra("SIZE", size)
    })
}

internal fun MainActivity.startCrosshairService(style: String, color: String, size: String) {
    val intent = Intent(this, CrosshairService::class.java).apply {
        putExtra("STYLE", style)
        putExtra(getString(R.string.lbl_color), color)
        putExtra("SIZE", size)
    }
    startService(intent)

    isCrosshairEnabled = true

    getSharedPreferences(Prefs.MAIN, Context.MODE_PRIVATE).edit()
        .putBoolean(Prefs.KEY_CROSSHAIR_ENABLED, true)
        .putString(Prefs.KEY_CROSSHAIR_STYLE, style)
        .putString(Prefs.KEY_CROSSHAIR_COLOR, color)
        .putString(Prefs.KEY_CROSSHAIR_SIZE, size)
        .apply()

    findViewById<TextView>(R.id.tvStatusCrosshair).text = getString(R.string.status_crosshair, xl(style.orEmpty()), xl(color.orEmpty()), xl(size.orEmpty()))
    Toasty.success(this, getString(R.string.toast_cross_updated), Toast.LENGTH_SHORT, true).show()
    showInterstitialAd()
}

internal fun MainActivity.stopCrosshairService() {
    val intent = Intent(this, CrosshairService::class.java)
    stopService(intent)

    isCrosshairEnabled = false

    getSharedPreferences(Prefs.MAIN, Context.MODE_PRIVATE).edit()
        .putBoolean(Prefs.KEY_CROSSHAIR_ENABLED, false)
        .apply()

    findViewById<TextView>(R.id.tvStatusCrosshair).text = getString(R.string.status_disabled)
    Toasty.success(this, getString(R.string.toast_cross_off), Toast.LENGTH_SHORT, true).show()
}
