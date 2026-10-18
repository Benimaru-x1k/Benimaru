package com.benimaru.official

import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import es.dmoral.toasty.Toasty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

internal fun MainActivity.confirmClearCaches() {
    Sheet(this)
        .icon(R.drawable.ic_reset)
        .title(getString(R.string.dlg_clear_title))
        .message(getString(R.string.dlg_clear_msg))
        .button(getString(R.string.btn_cancel), SheetStyle.TONAL)
        .button(getString(R.string.btn_clear_caches), SheetStyle.PRIMARY) { clearAllCaches() }
        .show()
}

internal fun MainActivity.clearAllCaches() {
    if (!hasShizukuPermission()) return

    val progress = ProgressSheet(this)
        .icon(R.drawable.ic_reset)
        .title(getString(R.string.dlg_clearing))
        .message(getString(R.string.dlg_clearing_msg))
    progress.show(indeterminate = true)
    progress.setIndeterminate(getString(R.string.txt_working))

    lifecycleScope.launch(Dispatchers.IO) {
        val before = freeStorageBytes()
        var ok = false
        try {

            val process = Shizuku.newProcess(arrayOf("sh", "-c", "pm trim-caches 999G"), null, null)
            ok = process.waitFor() == 0
        } catch (e: Exception) {
            ok = false
        }
        delay(1000)
        val freed = (freeStorageBytes() - before).coerceAtLeast(0)

        withContext(Dispatchers.Main) {
            progress.dismiss()
            if (ok) {
                findViewById<TextView>(R.id.tvStatusCache).text =
                    if (freed > 0) getString(R.string.status_freed, formatSize(freed)) else getString(R.string.status_already_clean)
                Toasty.success(
                    this@clearAllCaches,
                    if (freed > 0) getString(R.string.toast_freed_cache, formatSize(freed)) else getString(R.string.toast_cache_clean),
                    Toast.LENGTH_LONG, true
                ).show()
                updateDeviceInfo()
                showInterstitialAd()
            } else {
                Toasty.error(this@clearAllCaches, getString(R.string.toast_cache_blocked), Toast.LENGTH_LONG, true).show()
            }
        }
    }
}
