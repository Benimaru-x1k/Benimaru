package com.benimaru.official

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import es.dmoral.toasty.Toasty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class AppEntry(val pkg: String, val label: String, val isSystem: Boolean, val frozen: Boolean)

internal fun MainActivity.savedFrozen(): MutableSet<String> =
    (getSharedPreferences(Prefs.MAIN, Context.MODE_PRIVATE)
        .getStringSet(Prefs.KEY_FROZEN_APPS, emptySet()) ?: emptySet()).toMutableSet()

internal fun MainActivity.saveFrozen(set: Set<String>) {
    getSharedPreferences(Prefs.MAIN, Context.MODE_PRIVATE)
        .edit().putStringSet(Prefs.KEY_FROZEN_APPS, HashSet(set)).apply()
}

internal fun MainActivity.refreshFreezeStatus() {
    val tv = findViewById<TextView?>(R.id.tvStatusFreeze) ?: return
    val saved = savedFrozen()
    val stillFrozen = saved.filter { p ->
        try { !packageManager.getApplicationInfo(p, 0).enabled } catch (e: Exception) { true }
    }
    if (stillFrozen.size != saved.size) saveFrozen(stillFrozen.toSet())
    tv.text = if (stillFrozen.isEmpty()) getString(R.string.status_ready)
    else resources.getQuantityString(R.plurals.status_frozen_apps, stillFrozen.size, stillFrozen.size)
}

internal fun MainActivity.freezeBlocklist(): Set<String> {
    val pm = packageManager
    val set = mutableSetOf(
        packageName, AppConfig.SHIZUKU_PACKAGE, "android", "com.android.systemui",
        "com.android.settings", "com.android.phone", "com.android.server.telecom",
        "com.android.shell", "com.android.packageinstaller", "com.google.android.packageinstaller",
        "com.android.permissioncontroller", "com.google.android.permissioncontroller",
        "com.google.android.gms", "com.google.android.gsf", "com.google.android.webview",
        "com.android.webview", "com.google.android.trichromelibrary", "com.android.bluetooth",
        "com.android.nfc", "com.android.emergency", "com.android.networkstack",
        "com.android.cellbroadcastreceiver", "com.android.inputmethod.latin"
    )
    try {
        pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_ALL
        ).forEach { set.add(it.activityInfo.packageName) }
    } catch (_: Exception) {}
    try {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
            .enabledInputMethodList.forEach { set.add(it.packageName) }
    } catch (_: Exception) {}
    try { android.provider.Telephony.Sms.getDefaultSmsPackage(this)?.let { set.add(it) } } catch (_: Exception) {}
    try {
        (getSystemService(Context.TELECOM_SERVICE) as android.telecom.TelecomManager)
            .defaultDialerPackage?.let { set.add(it) }
    } catch (_: Exception) {}
    return set
}

internal fun MainActivity.isBlockedPackage(pkg: String, block: Set<String>): Boolean =
    pkg in block || pkg.startsWith("com.android.providers.") || pkg.startsWith("com.android.networkstack")

internal fun MainActivity.scanInstalledApps(): List<AppEntry> {
    val pm = packageManager
    val block = freezeBlocklist()
    val saved = savedFrozen()
    val result = mutableListOf<AppEntry>()
    val seen = mutableSetOf<String>()
    @Suppress("DEPRECATION")
    val installed = pm.getInstalledApplications(0)
    for (ai in installed) {
        val pkg = ai.packageName
        if (isBlockedPackage(pkg, block)) continue
        val frozen = pkg in saved && !ai.enabled
        if (!frozen && pm.getLaunchIntentForPackage(pkg) == null) continue
        val label = try { pm.getApplicationLabel(ai).toString() } catch (e: Exception) { pkg }
        result += AppEntry(pkg, label, (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0, frozen)
        seen += pkg
    }
    for (p in saved) if (p !in seen && !isBlockedPackage(p, block)) {
        result += AppEntry(p, p, false, true)
    }
    return result.sortedBy { it.label.lowercase() }
}

internal fun MainActivity.showFreezeAppsSheet() {
    if (!hasShizukuPermission()) return
    val scanning = ProgressSheet(this)
        .icon(R.drawable.ic_freeze)
        .title(getString(R.string.dlg_loading_apps))
        .message(getString(R.string.dlg_loading_apps_msg))
    scanning.show(indeterminate = true)
    scanning.setIndeterminate(getString(R.string.txt_moment))

    lifecycleScope.launch(Dispatchers.IO) {
        val apps = try { scanInstalledApps() } catch (e: Exception) { emptyList() }
        withContext(Dispatchers.Main) {
            scanning.dismiss()
            if (apps.isEmpty()) {
                Toasty.info(this@showFreezeAppsSheet, getString(R.string.toast_no_apps), Toast.LENGTH_LONG, true).show()
            } else {
                presentFreezeList(apps)
            }
        }
    }
}

private fun MainActivity.createFreezeTabs(
    userCount: Int, systemCount: Int, frozenCount: Int
): Pair<com.google.android.material.button.MaterialButtonToggleGroup, List<Int>> {
    val tabs = com.google.android.material.button.MaterialButtonToggleGroup(this).apply {
        isSingleSelection = true
        isSelectionRequired = true
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(4) }
    }
    val tabLabels = listOf(
        getString(R.string.tab_apps, userCount), getString(R.string.tab_system, systemCount), getString(R.string.tab_frozen, frozenCount)
    )
    val tabIds = tabLabels.map { t ->
        val b = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            id = View.generateViewId()
            text = t
            isAllCaps = false
            textSize = 12.5f
            insetTop = 0; insetBottom = 0
            setPadding(dp(6), 0, dp(6), 0)
            layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f)
        }
        tabs.addView(b)
        b.id
    }
    return tabs to tabIds
}

private fun MainActivity.createSystemAppsWarning(): TextView = infoCard(
    this,
    getString(R.string.txt_sys_warning),
    tint = 0xFFFFA000.toInt()
).apply {
    layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(10) }
    visibility = View.GONE
}


private fun MainActivity.confirmFreezeSystemApps(sys: List<AppEntry>, onConfirm: () -> Unit) {
    Sheet(this)
        .icon(R.drawable.ic_reset)
        .title(getString(R.string.dlg_freeze_sys_title))
        .message(
            resources.getQuantityString(R.plurals.sys_selected, sys.size, sys.size) +
                sys.take(8).joinToString("\n") { "• ${it.label}" } +
                (if (sys.size > 8) getString(R.string.more_n, sys.size - 8) else "") +
                getString(R.string.txt_freeze_sys_tail)
        )
        .button(getString(R.string.btn_cancel), SheetStyle.TONAL)
        .button(getString(R.string.btn_freeze_anyway), SheetStyle.DANGER) { onConfirm() }
        .show()
}

internal fun MainActivity.presentFreezeList(apps: List<AppEntry>) {
    val pm = packageManager
    val userApps = apps.filter { !it.isSystem && !it.frozen }
    val systemApps = apps.filter { it.isSystem && !it.frozen }
    val frozenApps = apps.filter { it.frozen }

    var tab = 0
    var query = ""
    val selected = mutableSetOf<String>()
    val iconCache = HashMap<String, android.graphics.drawable.Drawable?>()

    val (tabs, tabIds) = createFreezeTabs(userApps.size, systemApps.size, frozenApps.size)

    val (searchLayout, searchEdit) = styledField(this, getString(R.string.hint_search_apps), android.text.InputType.TYPE_CLASS_TEXT, topMarginDp = 10)
    val warning = createSystemAppsWarning()
    val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    val sheet = Sheet(this)

    fun current(): List<AppEntry> {
        val base = when (tab) { 0 -> userApps; 1 -> systemApps; else -> frozenApps }
        return if (query.isBlank()) base
        else base.filter { it.label.contains(query, true) || it.pkg.contains(query, true) }
    }

    fun updateButtons() {
        val primary = sheet.buttonViews.getOrNull(1) ?: return
        primary.isEnabled = selected.isNotEmpty()
        primary.text = when {
            selected.isEmpty() -> if (tab == 2) getString(R.string.btn_restore) else getString(R.string.btn_freeze)
            tab == 2 -> getString(R.string.btn_restore_n, selected.size)
            else -> getString(R.string.btn_freeze_n, selected.size)
        }
        sheet.buttonViews.getOrNull(2)?.visibility =
            if (frozenApps.isNotEmpty()) View.VISIBLE else View.GONE
    }

    fun render() {
        list.removeAllViews()
        val rows = current()
        val shown = rows.take(60)
        if (shown.isEmpty()) {
            list.addView(infoCard(this, when {
                query.isNotBlank() -> getString(R.string.txt_no_match)
                tab == 2 -> getString(R.string.txt_nothing_frozen)
                else -> getString(R.string.txt_no_apps_list)
            }).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(10) }
            })
        }
        shown.forEach { a ->
            val icon = iconCache.getOrPut(a.pkg) {
                try { pm.getApplicationIcon(a.pkg) } catch (e: Exception) { null }
            }
            list.addView(optionRow(
                this, a.label,
                if (a.isSystem) getString(R.string.sys_app_pkg, a.pkg) else a.pkg,
                icon = icon?.constantState?.newDrawable()?.mutate() ?: icon,
                tintIcon = false, selected = a.pkg in selected
            ) {
                if (!selected.remove(a.pkg)) selected.add(a.pkg)
                render()
                updateButtons()
            })
        }
        if (rows.size > shown.size) {
            list.addView(TextView(this).apply {
                text = getString(R.string.showing_n, shown.size, rows.size)
                textSize = 12f
                setTextColor(Ui.onSurfaceVariant(this@presentFreezeList))
                setPadding(dp(4), dp(10), dp(4), 0)
            })
        }
        fontify(list)
    }

    tabs.addOnButtonCheckedListener { _, checkedId, isChecked ->
        if (!isChecked) return@addOnButtonCheckedListener
        tab = tabIds.indexOf(checkedId).coerceAtLeast(0)
        selected.clear()
        warning.visibility = if (tab == 1) View.VISIBLE else View.GONE
        render()
        updateButtons()
    }
    searchEdit.addTextChangedListener(object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
        override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        override fun afterTextChanged(s: android.text.Editable?) {
            query = s?.toString()?.trim() ?: ""
            render()
        }
    })
    tabs.check(tabIds[0])

    fun doChange() {
        val picked = selected.toList()
        if (picked.isEmpty()) return
        if (tab == 2) {
            sheet.dismiss()
            applyFreezeChange(picked, freeze = false)
            return
        }
        val sys = apps.filter { it.pkg in selected && it.isSystem }
        if (sys.isEmpty()) {
            sheet.dismiss()
            applyFreezeChange(picked, freeze = true)
        } else {
            confirmFreezeSystemApps(sys) {
                sheet.dismiss()
                applyFreezeChange(picked, freeze = true)
            }
        }
    }

    sheet.icon(R.drawable.ic_freeze)
        .title(getString(R.string.dlg_freeze_apps))
        .message(getString(R.string.dlg_freeze_apps_msg))
        .content(tabs)
        .content(searchLayout)
        .content(warning)
        .content(list)
        .button(getString(R.string.btn_close), SheetStyle.TONAL)
        .button(getString(R.string.btn_freeze), SheetStyle.PRIMARY, dismiss = false) { doChange() }
        .button(getString(R.string.btn_restore_all_frozen), SheetStyle.TEXT, dismiss = false) {
            sheet.dismiss()
            restoreAllFrozen()
        }
        .show()
    updateButtons()
}

internal fun MainActivity.applyFreezeChange(pkgs: List<String>, freeze: Boolean) {
    val safeName = Regex("^[A-Za-z0-9._]+$")
    val valid = pkgs.filter { safeName.matches(it) }
    if (valid.isEmpty()) return

    val progress = ProgressSheet(this)
        .icon(R.drawable.ic_freeze)
        .title(if (freeze) getString(R.string.dlg_freezing) else getString(R.string.dlg_restoring))
        .message(getString(R.string.txt_please_wait))
    progress.show()
    val userId = android.os.Process.myUid() / 100000
    val before = availRamMb()

    lifecycleScope.launch(Dispatchers.IO) {
        val saved = savedFrozen()
        var ok = 0
        val failed = mutableListOf<String>()
        valid.forEachIndexed { i, p ->
            withContext(Dispatchers.Main) {
                progress.update((i * 100) / valid.size, getString(R.string.progress_n_of, i + 1, valid.size))
            }
            val cmd = if (freeze) "pm disable-user --user $userId $p" else "pm enable --user $userId $p"
            val r = runShell(cmd)
            val low = r.out.lowercase()
            if (r.code == 0 && !low.contains("exception") && !low.contains("error") && !low.contains("failed")) {
                ok++
                if (freeze) saved.add(p) else saved.remove(p)
            } else failed += p
        }
        saveFrozen(saved)
        if (freeze) delay(800)
        withContext(Dispatchers.Main) {
            progress.dismiss()
            refreshFreezeStatus()
            updateDeviceInfo()
            if (ok > 0) {
                val freed = (availRamMb() - before).coerceAtLeast(0)
                val msg = if (freeze) {
                    resources.getQuantityString(R.plurals.froze_apps, ok, ok) + if (freed > 0) " · " + getString(R.string.freed_suffix, freed) else ""
                } else resources.getQuantityString(R.plurals.restored_apps, ok, ok)
                Toasty.success(this@applyFreezeChange, msg, Toast.LENGTH_LONG, true).show()
            }
            if (failed.isNotEmpty()) {
                Toasty.warning(
                    this@applyFreezeChange,
                    resources.getQuantityString(R.plurals.failed_apps, failed.size, failed.size),
                    Toast.LENGTH_LONG, true
                ).show()
            }
            if (freeze && ok > 0) showInterstitialAd()
        }
    }
}

internal fun MainActivity.restoreAllFrozen() {
    val saved = savedFrozen().toList()
    if (saved.isEmpty()) {
        Toasty.info(this, getString(R.string.toast_no_frozen), Toast.LENGTH_SHORT, true).show()
        return
    }
    if (!hasShizukuPermission()) return
    applyFreezeChange(saved, freeze = false)
}
