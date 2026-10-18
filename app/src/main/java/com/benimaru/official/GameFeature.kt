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

internal fun MainActivity.findGames(): List<ResolveInfo> {
    val pm = packageManager
    val intent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
    return pm.queryIntentActivities(intent, 0)
        .filter {
            val appInfo = it.activityInfo.applicationInfo
            val isGameFlag = (appInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0
            val isGameCategory = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appInfo.category == ApplicationInfo.CATEGORY_GAME
            } else false
            isGameFlag || isGameCategory
        }
        .sortedBy { it.loadLabel(pm).toString().lowercase() }
}

internal fun MainActivity.showGamePicker(title: String, message: String, onPick: (ResolveInfo) -> Unit) {
    val games = findGames()
    if (games.isEmpty()) {
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
        .button(getString(R.string.btn_close), SheetStyle.TEXT)
        .show()
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
                        getSharedPreferences(Prefs.MAIN, Context.MODE_PRIVATE).edit().putString(Prefs.KEY_LAST_GAME_MODE, pkgName).apply()
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
                        val process = Shizuku.newProcess(arrayOf("sh", "-c", "cmd package compile -m speed -f $pkgName"), null, null)
                        process.waitFor()
                        withContext(Dispatchers.Main) {
                            progress.dismiss()
                            Toasty.success(this@showGameLauncherDialog, getString(R.string.toast_optimized, appName), Toast.LENGTH_SHORT, true).show()
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
