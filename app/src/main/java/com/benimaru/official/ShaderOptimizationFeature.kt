package com.benimaru.official

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import es.dmoral.toasty.Toasty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val ANGLE_PKGS = "angle_gl_driver_selection_pkgs"
private const val ANGLE_VALUES = "angle_gl_driver_selection_values"

private const val DRIVER_DEFAULT = "default"
private const val DRIVER_ANGLE = "angle"
private const val DRIVER_NATIVE = "native"

private val SHADER_SAFE_PKG = Regex("^[A-Za-z0-9._]+$")
private val C_SH_AMBER = Color.parseColor("#E09B00")

private class ShaderInfo(val driver: String, val driverSupported: Boolean, val angleInstalled: Boolean)

private fun MainActivity.readDriverMap(): LinkedHashMap<String, String> {
    fun read(key: String): List<String> {
        val r = runShell("settings get global $key")
        val raw = r.out.trim()
        if (r.code != 0 || raw.isEmpty() || raw == "null") return emptyList()
        return raw.split(',').map { it.trim() }
    }
    val pkgs = read(ANGLE_PKGS)
    val values = read(ANGLE_VALUES)
    val map = LinkedHashMap<String, String>()
    pkgs.forEachIndexed { i, p ->
        if (SHADER_SAFE_PKG.matches(p)) map[p] = values.getOrNull(i)?.ifEmpty { DRIVER_DEFAULT } ?: DRIVER_DEFAULT
    }
    return map
}

private fun MainActivity.writeDriverMap(map: Map<String, String>): Boolean {
    if (map.isEmpty()) {
        val a = runShell("settings delete global $ANGLE_PKGS")
        val b = runShell("settings delete global $ANGLE_VALUES")
        return a.code == 0 && b.code == 0 && readDriverMap().isEmpty()
    }
    val pkgs = map.keys.joinToString(",")
    val values = map.values.joinToString(",")
    val a = runShell("settings put global $ANGLE_PKGS '$pkgs'")
    val b = runShell("settings put global $ANGLE_VALUES '$values'")
    return a.code == 0 && b.code == 0 && readDriverMap() == map
}

private fun MainActivity.isAngleInstalled(): Boolean =
    runShell("pm list packages angle").out.contains("angle", ignoreCase = true)

private fun MainActivity.setShaderStatus(active: Boolean) {
    findViewById<TextView>(R.id.tvStatusShader)?.text =
        getString(if (active) R.string.shader_status_active else R.string.status_ready)
}

private fun MainActivity.trackedDriverPkgs(): MutableSet<String> =
    getSharedPreferences(Prefs.MAIN, Context.MODE_PRIVATE)
        .getStringSet(Prefs.KEY_SHADER_PKGS, emptySet()).orEmpty().toMutableSet()

private fun MainActivity.saveTrackedDriverPkgs(pkgs: Set<String>) {
    getSharedPreferences(Prefs.MAIN, Context.MODE_PRIVATE).edit()
        .putStringSet(Prefs.KEY_SHADER_PKGS, pkgs)
        .apply()
}

internal fun MainActivity.showShaderOptimizationSheet() {
    if (!hasShizukuPermission()) return
    showGamePicker(getString(R.string.title_shader), getString(R.string.shader_pick_msg)) { app ->
        presentShaderSheet(app.activityInfo.packageName, app.loadLabel(packageManager).toString())
    }
}

private fun MainActivity.presentShaderSheet(pkg: String, label: String) {
    if (!SHADER_SAFE_PKG.matches(pkg)) return
    lifecycleScope.launch {
        val info = withContext(Dispatchers.IO) {
            val supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
            ShaderInfo(
                driver = if (supported) readDriverMap()[pkg] ?: DRIVER_DEFAULT else DRIVER_DEFAULT,
                driverSupported = supported,
                angleInstalled = supported && isAngleInstalled()
            )
        }
        if (isFinishing || isDestroyed) return@launch
        renderShaderSheet(pkg, label, info)
    }
}

private fun MainActivity.renderShaderSheet(pkg: String, label: String, info: ShaderInfo) {
    val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    val sheet = Sheet(this)

    if (info.driverSupported) {
        val name = when (info.driver) {
            DRIVER_ANGLE -> getString(R.string.shader_driver_angle)
            DRIVER_NATIVE -> getString(R.string.shader_driver_native)
            else -> getString(R.string.shader_driver_default)
        }
        box.addView(infoCard(this, getString(R.string.shader_driver_current, name)))
        if (!info.angleInstalled) {
            box.addView(android.view.View(this), LinearLayout.LayoutParams(1, dp(10)))
            box.addView(infoCard(this, getString(R.string.shader_warn_no_angle), C_SH_AMBER))
        }
    } else {
        box.addView(infoCard(this, getString(R.string.shader_warn_old), C_SH_AMBER))
    }

    box.addView(optionRow(
        this, getString(R.string.shader_row_compile), getString(R.string.shader_row_compile_sub),
        icon = Ui.drawable(this, R.drawable.ic_tune)
    ) {
        sheet.dismiss()
        precompileGame(pkg, label)
    })

    if (info.driverSupported) {
        val rows = listOf(
            Triple(DRIVER_ANGLE, R.string.shader_row_angle, R.string.shader_row_angle_sub),
            Triple(DRIVER_NATIVE, R.string.shader_row_native, R.string.shader_row_native_sub),
            Triple(DRIVER_DEFAULT, R.string.shader_row_default, R.string.shader_row_default_sub)
        )
        rows.forEach { (value, title, sub) ->
            box.addView(optionRow(
                this, getString(title), getString(sub),
                icon = Ui.drawable(this, R.drawable.ic_game),
                selected = info.driver == value
            ) {
                sheet.dismiss()
                if (info.driver != value) setGameDriver(pkg, label, value)
            })
        }
    }
    fontify(box)

    sheet.icon(R.drawable.ic_tune)
        .title(label)
        .message(getString(R.string.shader_game_msg))
        .content(box)
        .button(getString(R.string.btn_close), SheetStyle.TEXT)
        .show()
}

private fun MainActivity.precompileGame(pkg: String, label: String) {
    if (!hasShizukuPermission()) return
    val progress = ProgressSheet(this)
        .icon(R.drawable.ic_tune)
        .title(getString(R.string.shader_compiling, label))
        .message(getString(R.string.shader_compiling_sub))
    progress.show(indeterminate = true)
    progress.setIndeterminate(getString(R.string.txt_working))

    lifecycleScope.launch(Dispatchers.IO) {
        val r = runShell("cmd package compile -m speed -f $pkg")
        val ok = r.code == 0 && r.out.contains("Success", ignoreCase = true)
        withContext(Dispatchers.Main) {
            progress.dismiss()
            if (ok) {
                setShaderStatus(true)
                Toasty.success(this@precompileGame, getString(R.string.shader_toast_compiled, label), Toast.LENGTH_SHORT, true).show()
            } else {
                Toasty.error(this@precompileGame, getString(R.string.shader_toast_compile_failed, label), Toast.LENGTH_LONG, true).show()
            }
        }
    }
}

private fun MainActivity.setGameDriver(pkg: String, label: String, value: String) {
    if (!hasShizukuPermission()) return
    lifecycleScope.launch(Dispatchers.IO) {
        val map = readDriverMap()
        if (value == DRIVER_DEFAULT) map.remove(pkg) else map[pkg] = value
        val ok = writeDriverMap(map)
        withContext(Dispatchers.Main) {
            if (!ok) {
                Toasty.error(this@setGameDriver, getString(R.string.shader_toast_driver_failed), Toast.LENGTH_LONG, true).show()
                return@withContext
            }
            val tracked = trackedDriverPkgs()
            if (value == DRIVER_DEFAULT) tracked.remove(pkg) else tracked.add(pkg)
            saveTrackedDriverPkgs(tracked)
            setShaderStatus(tracked.isNotEmpty())
            Toasty.success(this@setGameDriver, getString(R.string.shader_toast_driver_set, label), Toast.LENGTH_LONG, true).show()
            if (!isFinishing && !isDestroyed) presentShaderSheet(pkg, label)
        }
    }
}

internal fun MainActivity.resetShaderOptimizations(silent: Boolean = false) {
    val tracked = trackedDriverPkgs()
    if (tracked.isEmpty()) {
        setShaderStatus(false)
        if (!silent) Toasty.info(this, getString(R.string.shader_toast_nothing), Toast.LENGTH_SHORT, true).show()
        return
    }
    if (!hasShizukuPermission()) return
    lifecycleScope.launch(Dispatchers.IO) {
        val map = readDriverMap()
        tracked.forEach { map.remove(it) }
        val ok = writeDriverMap(map)
        withContext(Dispatchers.Main) {
            if (ok) {
                saveTrackedDriverPkgs(emptySet())
                setShaderStatus(false)
                if (!silent) Toasty.success(this@resetShaderOptimizations, getString(R.string.shader_toast_reset), Toast.LENGTH_SHORT, true).show()
            } else if (!silent) {
                Toasty.error(this@resetShaderOptimizations, getString(R.string.shader_toast_driver_failed), Toast.LENGTH_LONG, true).show()
            }
        }
    }
}
