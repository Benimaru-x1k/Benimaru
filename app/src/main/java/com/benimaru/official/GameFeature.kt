package com.benimaru.official

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.os.Build
import android.widget.LinearLayout
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import es.dmoral.toasty.Toasty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

private val GAME_PKG_SAFE = Regex("^[A-Za-z0-9._]+$")

internal fun gameModePackages(prefs: android.content.SharedPreferences): Set<String> {
    val all = LinkedHashSet<String>()
    prefs.getStringSet(Prefs.KEY_GAME_MODE_PKGS, null)?.let { all.addAll(it) }
    prefs.getString(Prefs.KEY_LAST_GAME_MODE, "")?.takeIf { it.isNotEmpty() }?.let { all.add(it) }
    return all.filter { GAME_PKG_SAFE.matches(it) }.toSet()
}

internal fun gameModeResetCmd(prefs: android.content.SharedPreferences): String =
    gameModePackages(prefs).joinToString("; ") { "cmd game mode standard $it" }

internal fun clearGameModeState(prefs: android.content.SharedPreferences) {
    prefs.edit().remove(Prefs.KEY_GAME_MODE_PKGS).putString(Prefs.KEY_LAST_GAME_MODE, "").apply()
}

internal fun MainActivity.findGames(all: Boolean = false): List<ResolveInfo> {
    val pm = packageManager
    val intent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
    return pm.queryIntentActivities(intent, 0)
        .filter { info ->
            if (all) {
                info.activityInfo.packageName != packageName
            } else {
                val appInfo = info.activityInfo.applicationInfo
                val isGameFlag = (appInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0
                val isGameCategory = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appInfo.category == ApplicationInfo.CATEGORY_GAME
                } else false
                isGameFlag || isGameCategory
            }
        }
        .distinctBy { it.activityInfo.packageName }
        .sortedBy { it.loadLabel(pm).toString().lowercase() }
}

internal fun MainActivity.showGamePicker(title: String, message: String, showAll: Boolean = false, onPick: (ResolveInfo) -> Unit) {
    val games = findGames(showAll)
    if (games.isEmpty()) {
        if (!showAll) {
            showGamePicker(title, message, true, onPick)
            return
        }
        Toasty.info(this, getString(R.string.toast_no_games), Toast.LENGTH_SHORT, true).show()
        return
    }
    val pm = packageManager
    val sheet = Sheet(this)
    val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    games.forEach { app ->
        list.addView(optionRow(
            this, app.loadLabel(pm), app.activityInfo.packageName,
            icon = app.loadIcon(pm), tintIcon = false
        ) {
            sheet.dismiss()
            onPick(app)
        })
    }
    sheet.icon(R.drawable.ic_play)
        .title(title)
        .message(message)
        .content(list)
    if (!showAll) {
        sheet.button(getString(R.string.btn_show_all_apps), SheetStyle.TONAL) {
            showGamePicker(title, message, true, onPick)
        }
    }
    sheet.button(getString(R.string.btn_close), SheetStyle.TEXT).show()
}

internal fun MainActivity.showGameModeSelectorDialog() {
    showGamePicker(getString(R.string.dlg_native_gm), getString(R.string.dlg_native_gm_msg)) { selectedApp ->
        val pkgName = selectedApp.activityInfo.packageName
        val appName = selectedApp.loadLabel(packageManager).toString()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                var process = Shizuku.newProcess(arrayOf("sh", "-c", "cmd game mode performance $pkgName"), null, null)
                var exitCode = process.waitFor()

                if (exitCode != 0) {
                    process = Shizuku.newProcess(arrayOf("sh", "-c", "cmd game mode 2 $pkgName"), null, null)
                    exitCode = process.waitFor()
                }

                withContext(Dispatchers.Main) {
                    if (exitCode == 0) {
                        Toasty.success(this@showGameModeSelectorDialog, getString(R.string.toast_gm_enabled, appName), Toast.LENGTH_SHORT, true).show()
                        val gmPrefs = getSharedPreferences(Prefs.MAIN, Context.MODE_PRIVATE)
                        val gmSet = (gmPrefs.getStringSet(Prefs.KEY_GAME_MODE_PKGS, null) ?: emptySet()).toMutableSet()
                        gmSet.add(pkgName)
                        gmPrefs.edit()
                            .putStringSet(Prefs.KEY_GAME_MODE_PKGS, gmSet)
                            .putString(Prefs.KEY_LAST_GAME_MODE, pkgName)
                            .apply()
                        fetchSystemStatuses()
                        showInterstitialAd()
                    } else {
                        Toasty.warning(this@showGameModeSelectorDialog, getString(R.string.toast_gm_blocked), Toast.LENGTH_LONG, true).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toasty.error(this@showGameModeSelectorDialog, getString(R.string.toast_error, e.message ?: ""), Toast.LENGTH_SHORT, true).show()
                }
            }
        }
    }
}

internal fun MainActivity.showGameLauncherDialog() {
    showGamePicker(getString(R.string.dlg_launch_game), getString(R.string.dlg_launch_game_msg)) { selectedApp ->
        val pm = packageManager
        val pkgName = selectedApp.activityInfo.packageName
        val appName = selectedApp.loadLabel(pm).toString()

        fun launchGame() {
            val launchIntent = pm.getLaunchIntentForPackage(pkgName)
            if (launchIntent != null) {
                startActivity(launchIntent)
            } else {
                Toasty.error(this, getString(R.string.toast_launch_fail), Toast.LENGTH_SHORT, true).show()
            }
        }

        Sheet(this)
            .icon(R.drawable.ic_play)
            .title(appName)
            .message(getString(R.string.dlg_precompile_msg))
            .button(getString(R.string.btn_optimize_launch), SheetStyle.PRIMARY) {
                val progress = ProgressSheet(this)
                    .icon(R.drawable.ic_tune)
                    .title(getString(R.string.dlg_optimizing, appName))
                    .message(getString(R.string.dlg_precompiling_msg))
                progress.show(indeterminate = true)
                progress.setIndeterminate(getString(R.string.txt_working))

                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val result = runShell("cmd package compile -m speed -f $pkgName")
                        val compiled = result.code == 0 && result.out.contains("Success", ignoreCase = true)
                        withContext(Dispatchers.Main) {
                            progress.dismiss()
                            if (compiled) {
                                Toasty.success(this@showGameLauncherDialog, getString(R.string.toast_optimized, appName), Toast.LENGTH_SHORT, true).show()
                            } else {
                                Toasty.warning(this@showGameLauncherDialog, getString(R.string.toast_opt_failed, result.out.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().take(80)), Toast.LENGTH_LONG, true).show()
                            }
                            showInterstitialAd { launchGame() }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            progress.dismiss()
                            Toasty.error(this@showGameLauncherDialog, getString(R.string.toast_opt_failed, e.message ?: ""), Toast.LENGTH_LONG, true).show()
                        }
                    }
                }
            }
            .button(getString(R.string.btn_launch_now), SheetStyle.TONAL) {
                launchGame()
                Toasty.success(this, getString(R.string.toast_launching, appName), Toast.LENGTH_SHORT, true).show()
            }
            .button(getString(R.string.btn_cancel), SheetStyle.TEXT)
            .show()
    }
}
