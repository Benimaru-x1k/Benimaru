package com.benimaru.official

import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.provider.Settings.Secure
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import es.dmoral.toasty.Toasty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class MemApp(
    val pkg: String,
    val label: CharSequence,
    val icon: android.graphics.drawable.Drawable?,
    val mb: Int
)

internal fun MainActivity.scanMemoryHogs(): List<MemApp> {
    val pm = packageManager
    val output = runAdbCommandWithResult("dumpsys meminfo")

    val homePkg = pm.resolveActivity(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY
    )?.activityInfo?.packageName
    val imePkg = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.substringBefore('/')

    val protectedPkgs = setOfNotNull(
        packageName, AppConfig.SHIZUKU_PACKAGE, "android", "com.android.systemui",
        "com.android.phone", homePkg, imePkg
    )

    val line = Regex("""^\s*([\d,]+)K: (\S+)""")
    val perPackage = LinkedHashMap<String, Int>()
    var inSection = false
    for (raw in output.lineSequence()) {
        if (raw.startsWith("Total PSS by process")) { inSection = true; continue }
        if (!inSection) continue
        if (raw.isBlank()) break
        val m = line.find(raw) ?: continue
        val kb = m.groupValues[1].replace(",", "").toLongOrNull() ?: continue
        val pkg = m.groupValues[2].substringBefore(':')
        perPackage[pkg] = (perPackage[pkg] ?: 0) + (kb / 1024).toInt()
    }

    val safeName = Regex("^[A-Za-z0-9._]+$")
    return perPackage.entries
        .asSequence()
        .filter { it.key !in protectedPkgs && it.value >= 20 && safeName.matches(it.key) }
        .sortedByDescending { it.value }
        .mapNotNull { (pkg, mb) ->
            try {
                if (pm.getLaunchIntentForPackage(pkg) == null) return@mapNotNull null
                val ai = pm.getApplicationInfo(pkg, 0)
                MemApp(pkg, pm.getApplicationLabel(ai), pm.getApplicationIcon(ai), mb)
            } catch (e: Exception) {
                null
            }
        }
        .take(12)
        .toList()
}

internal fun MainActivity.showRamBoosterSheet() {
    if (!hasShizukuPermission()) return

    val scanning = ProgressSheet(this)
        .icon(R.drawable.ic_tune)
        .title(getString(R.string.dlg_scan_mem))
        .message(getString(R.string.dlg_scan_mem_msg))
    scanning.show(indeterminate = true)
    scanning.setIndeterminate(getString(R.string.dlg_scan_mem_sub))

    lifecycleScope.launch(Dispatchers.IO) {
        val apps = try { scanMemoryHogs() } catch (e: Exception) { emptyList() }
        withContext(Dispatchers.Main) {
            scanning.dismiss()
            if (apps.isEmpty()) {
                Toasty.info(this@showRamBoosterSheet, getString(R.string.toast_no_heavy), Toast.LENGTH_LONG, true).show()
            } else {
                presentRamList(apps)
            }
        }
    }
}

internal fun MainActivity.presentRamList(apps: List<MemApp>) {
    val selected = apps.map { it.pkg }.toMutableSet()
    val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    val summary = infoCard(this, "")
    val sheet = Sheet(this)

    fun updateSummary() {
        val mb = apps.filter { it.pkg in selected }.sumOf { it.mb }
        summary.text = if (selected.isEmpty()) getString(R.string.txt_nothing_selected)
        else getString(R.string.ram_summary, selected.size, mb, availRamMb())
        sheet.buttonViews.getOrNull(1)?.let {
            it.isEnabled = selected.isNotEmpty()
            it.text = if (selected.isEmpty()) getString(R.string.btn_boost) else getString(R.string.btn_boost_n, selected.size)
        }
    }

    fun render() {
        list.removeAllViews()
        apps.forEach { a ->
            list.addView(optionRow(
                this, a.label, getString(R.string.ram_in_use, a.mb),
                icon = a.icon?.constantState?.newDrawable()?.mutate() ?: a.icon,
                tintIcon = false, selected = a.pkg in selected
            ) {
                if (!selected.remove(a.pkg)) selected.add(a.pkg)
                render()
                updateSummary()
            })
        }
        fontify(list)
    }
    render()

    sheet.icon(R.drawable.ic_tune)
        .title(getString(R.string.dlg_ram_title))
        .message(getString(R.string.dlg_ram_msg))
        .content(summary)
        .content(list)
        .button(getString(R.string.btn_cancel), SheetStyle.TONAL)
        .button(getString(R.string.btn_boost), SheetStyle.PRIMARY) { boostApps(selected.toList()) }
        .show()
    updateSummary()
}

internal fun MainActivity.boostApps(pkgs: List<String>) {
    if (pkgs.isEmpty()) return
    val safeName = Regex("^[A-Za-z0-9._]+$")
    val valid = pkgs.filter { safeName.matches(it) }
    if (valid.isEmpty()) return

    val before = availRamMb()
    val cmd = valid.joinToString("; ") { "am force-stop $it" }
    runAdbCommand(cmd, resources.getQuantityString(R.plurals.stopped_apps, valid.size, valid.size), showAd = true) {
        lifecycleScope.launch {
            delay(1500)
            val freed = (availRamMb() - before).coerceAtLeast(0)
            findViewById<TextView>(R.id.tvStatusRamBooster).text =
                if (freed > 0) getString(R.string.status_freed_mb, freed) else resources.getQuantityString(R.plurals.status_stopped_apps, valid.size, valid.size)
            updateDeviceInfo()
        }
    }
}
