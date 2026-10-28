package com.benimaru.official

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.text.format.Formatter
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import es.dmoral.toasty.Toasty
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

private val TEX_SAFE_PKG = Regex("^[A-Za-z0-9._]+$")
private const val TEX_MIN_BYTES = 32768L
private const val TEX_MAX_BYTES = 24L * 1024 * 1024
private const val TEX_MAX_PIXELS = 16_000_000L
private const val TEX_TMP_SUFFIX = ".bnr_tmp"

private class TexFile(val path: String, val size: Long)

private enum class TexMode(val scale: Float) { RECOMPRESS(1f), SCALE_75(0.75f), SCALE_50(0.5f) }

private class TexResult(var changed: Int = 0, var saved: Long = 0L, var failed: Int = 0, var cancelled: Boolean = false)

private fun texDataRoot(pkg: String) = "/sdcard/Android/data/$pkg"

private fun MainActivity.texBackupRoot(): File =
    File(getExternalFilesDir(null) ?: filesDir, "texture_backup")

private fun MainActivity.hasTextureBackup(pkg: String): Boolean =
    File(texBackupRoot(), pkg).let { it.isDirectory && it.walkTopDown().any { f -> f.isFile } }

private fun MainActivity.anyTextureBackup(): Boolean =
    texBackupRoot().let { it.isDirectory && it.walkTopDown().any { f -> f.isFile } }

private fun texReadRemote(path: String): ByteArray? = try {
    val p = Shizuku.newProcess(arrayOf("sh", "-c", "cat '$path'"), null, null)
    val bytes = p.inputStream.readBytes()
    if (p.waitFor() == 0) bytes else null
} catch (e: Exception) {
    null
}

private fun texWriteRemote(path: String, bytes: ByteArray): Boolean = try {
    val tmp = path + TEX_TMP_SUFFIX
    val p = Shizuku.newProcess(arrayOf("sh", "-c", "cat > '$tmp' && mv -f '$tmp' '$path'"), null, null)
    p.outputStream.use { it.write(bytes) }
    p.waitFor() == 0
} catch (e: Exception) {
    false
}

@Suppress("DEPRECATION")
private fun webpFormat(): Bitmap.CompressFormat =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY
    else Bitmap.CompressFormat.WEBP

private fun pathIsSafe(path: String): Boolean =
    !path.contains('\'') && !path.contains('\n') && !path.contains("..")

private fun MainActivity.scanTextures(pkg: String): List<TexFile> {
    val root = texDataRoot(pkg)
    val cmd = "[ -d '$root' ] && find '$root' -type f -size +${TEX_MIN_BYTES}c " +
        "\\( -iname '*.png' -o -iname '*.jpg' -o -iname '*.jpeg' -o -iname '*.webp' \\) " +
        "-exec stat -c '%s|%n' {} + 2>/dev/null"
    val r = runShell(cmd)
    val prefix = "$root/"
    return r.out.lineSequence()
        .mapNotNull { line ->
            val bar = line.indexOf('|')
            if (bar <= 0) return@mapNotNull null
            val size = line.substring(0, bar).trim().toLongOrNull() ?: return@mapNotNull null
            val path = line.substring(bar + 1).trim()
            if (!path.startsWith(prefix) || !pathIsSafe(path) || path.endsWith(TEX_TMP_SUFFIX)) return@mapNotNull null
            if (size > TEX_MAX_BYTES) return@mapNotNull null
            TexFile(path, size)
        }
        .toList()
}

private fun MainActivity.shrinkTextures(
    pkg: String,
    files: List<TexFile>,
    mode: TexMode,
    isCancelled: () -> Boolean,
    onProgress: (done: Int, total: Int) -> Unit
): TexResult {
    val result = TexResult()
    val backupDir = File(texBackupRoot(), pkg)
    val prefix = texDataRoot(pkg) + "/"

    files.forEachIndexed { index, f ->
        if (isCancelled()) {
            result.cancelled = true
            return result
        }
        onProgress(index, files.size)

        val rel = f.path.removePrefix(prefix)
        val ext = rel.substringAfterLast('.', "").lowercase()
        val orig = texReadRemote(f.path)
        if (orig == null) {
            result.failed++
            return@forEachIndexed
        }

        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(orig, 0, orig.size, bounds)
            val w = bounds.outWidth
            val h = bounds.outHeight
            if (w <= 0 || h <= 0 || w.toLong() * h > TEX_MAX_PIXELS) return@forEachIndexed
            if (mode != TexMode.RECOMPRESS && (w < 64 || h < 64)) return@forEachIndexed

            var bmp = BitmapFactory.decodeByteArray(orig, 0, orig.size) ?: return@forEachIndexed
            if (mode != TexMode.RECOMPRESS) {
                val nw = (w * mode.scale).roundToInt().coerceAtLeast(1)
                val nh = (h * mode.scale).roundToInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(bmp, nw, nh, true)
                if (scaled !== bmp) bmp.recycle()
                bmp = scaled
            }

            val out = ByteArrayOutputStream(orig.size)
            val encoded = when (ext) {
                "png" -> bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                "jpg", "jpeg" -> bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
                else -> bmp.compress(webpFormat(), 85, out)
            }
            bmp.recycle()
            if (!encoded) return@forEachIndexed

            val newBytes = out.toByteArray()
            val worthIt = if (mode == TexMode.RECOMPRESS) newBytes.size < orig.size * 0.95 else newBytes.size < orig.size
            if (!worthIt) return@forEachIndexed

            val backup = File(backupDir, rel)
            if (!backup.exists()) {
                backup.parentFile?.mkdirs()
                backup.writeBytes(orig)
            }

            if (texWriteRemote(f.path, newBytes)) {
                result.changed++
                result.saved += (orig.size - newBytes.size).toLong()
            } else {
                result.failed++
            }
        } catch (e: OutOfMemoryError) {
            result.failed++
        } catch (e: Exception) {
            result.failed++
        }
    }
    onProgress(files.size, files.size)
    return result
}

private fun MainActivity.restoreTextures(pkg: String?): Pair<Int, Int> {
    var restored = 0
    var failed = 0
    val root = texBackupRoot()
    val dirs = if (pkg != null) listOf(File(root, pkg)) else root.listFiles()?.toList().orEmpty()
    for (dir in dirs) {
        if (!dir.isDirectory || !TEX_SAFE_PKG.matches(dir.name)) continue
        val prefix = texDataRoot(dir.name) + "/"
        var dirFailed = 0
        dir.walkTopDown().filter { it.isFile }.toList().forEach { f ->
            val rel = f.relativeTo(dir).invariantSeparatorsPath
            val dest = prefix + rel
            if (!pathIsSafe(dest)) {
                dirFailed++
                return@forEach
            }
            val ok = try { texWriteRemote(dest, f.readBytes()) } catch (e: Exception) { false }
            if (ok) {
                f.delete()
                restored++
            } else {
                dirFailed++
            }
        }
        failed += dirFailed
        if (dirFailed == 0) dir.deleteRecursively()
    }
    return restored to failed
}

private fun MainActivity.setTextureStatus(changed: Int?) {
    findViewById<TextView>(R.id.tvStatusTexture)?.text =
        if (changed != null && changed > 0) getString(R.string.tex_status_done, changed)
        else getString(R.string.status_ready)
}

internal fun MainActivity.showTextureSheet() {
    if (!hasShizukuPermission()) return
    showGamePicker(getString(R.string.title_texture), getString(R.string.tex_pick_msg)) { app ->
        val pkg = app.activityInfo.packageName
        if (!TEX_SAFE_PKG.matches(pkg)) return@showGamePicker
        scanAndPresent(pkg, app.loadLabel(packageManager).toString())
    }
}

private fun MainActivity.scanAndPresent(pkg: String, label: String) {
    val scanning = ProgressSheet(this)
        .icon(R.drawable.ic_resolution)
        .title(getString(R.string.tex_scanning))
        .message(label)
    scanning.show(indeterminate = true)
    scanning.setIndeterminate(getString(R.string.txt_working))

    lifecycleScope.launch(Dispatchers.IO) {
        val files = try { scanTextures(pkg) } catch (e: Exception) { emptyList() }
        withContext(Dispatchers.Main) {
            scanning.dismiss()
            if (!isFinishing && !isDestroyed) renderTextureSheet(pkg, label, files)
        }
    }
}

private fun MainActivity.renderTextureSheet(pkg: String, label: String, files: List<TexFile>) {
    var mode = TexMode.RECOMPRESS
    val sheet = Sheet(this)
    val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    val hasBackup = hasTextureBackup(pkg)

    val totalBytes = files.sumOf { it.size }
    box.addView(
        infoCard(
            this,
            if (files.isEmpty()) getString(R.string.tex_scan_none)
            else getString(R.string.tex_summary, files.size, Formatter.formatFileSize(this, totalBytes))
        )
    )
    if (hasBackup) {
        box.addView(android.view.View(this), LinearLayout.LayoutParams(1, dp(10)))
        box.addView(infoCard(this, getString(R.string.tex_backup_exists)))
    }

    val modes = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    fun renderModes() {
        modes.removeAllViews()
        listOf(
            Triple(TexMode.RECOMPRESS, R.string.tex_mode_recompress, R.string.tex_mode_recompress_sub),
            Triple(TexMode.SCALE_75, R.string.tex_mode_75, R.string.tex_mode_75_sub),
            Triple(TexMode.SCALE_50, R.string.tex_mode_50, R.string.tex_mode_50_sub)
        ).forEach { (m, title, sub) ->
            modes.addView(optionRow(this, getString(title), getString(sub), selected = mode == m) {
                mode = m
                renderModes()
            })
        }
        fontify(modes)
    }
    if (files.isNotEmpty()) {
        renderModes()
        box.addView(modes, LinearLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        ))
    }

    sheet.icon(R.drawable.ic_resolution)
        .title(label)
        .message(getString(R.string.tex_msg))
        .content(box)

    if (files.isNotEmpty()) {
        sheet.button(getString(R.string.tex_btn_start), SheetStyle.PRIMARY) {
            runShrink(pkg, files, mode)
        }
    }
    if (hasBackup) {
        sheet.button(getString(R.string.tex_btn_restore), SheetStyle.TONAL) { runRestore(pkg) }
    }
    sheet.button(getString(R.string.btn_close), SheetStyle.TEXT).show()
}

private fun MainActivity.runShrink(pkg: String, files: List<TexFile>, mode: TexMode) {
    val root = texBackupRoot().apply { mkdirs() }
    val needed = files.sumOf { it.size }
    if (root.usableSpace < needed + needed / 10 + 50L * 1024 * 1024) {
        Toasty.error(this, getString(R.string.tex_toast_no_space), Toast.LENGTH_LONG, true).show()
        return
    }

    val cancelled = java.util.concurrent.atomic.AtomicBoolean(false)
    val progress = ProgressSheet(this)
        .icon(R.drawable.ic_resolution)
        .title(getString(R.string.tex_progress_title))
        .message(getString(R.string.tex_progress_msg))
        .cancelButton(getString(R.string.tex_cancel)) { cancelled.set(true) }
    progress.show()
    progress.update(0, getString(R.string.tex_progress, 0, files.size))

    lifecycleScope.launch(Dispatchers.IO) {
        var lastPct = -1
        val result = shrinkTextures(pkg, files, mode, { cancelled.get() }) { done, total ->
            val pct = if (total == 0) 100 else done * 100 / total
            if (pct != lastPct) {
                lastPct = pct
                runOnUiThread { progress.update(pct, getString(R.string.tex_progress, done, total)) }
            }
        }
        withContext(Dispatchers.Main) {
            progress.dismiss()
            setTextureStatus(if (result.changed > 0) result.changed else null)
            when {
                result.changed > 0 -> Toasty.success(
                    this@runShrink,
                    getString(R.string.tex_toast_done, result.changed, Formatter.formatFileSize(this@runShrink, result.saved)),
                    Toast.LENGTH_LONG, true
                ).show()
                result.failed > 0 -> Toasty.error(this@runShrink, getString(R.string.tex_toast_failed), Toast.LENGTH_LONG, true).show()
                else -> Toasty.info(this@runShrink, getString(R.string.tex_toast_none), Toast.LENGTH_LONG, true).show()
            }
        }
    }
}

private fun MainActivity.runRestore(pkg: String?) {
    if (!hasShizukuPermission()) return
    val progress = ProgressSheet(this)
        .icon(R.drawable.ic_resolution)
        .title(getString(R.string.tex_restoring))
    progress.show(indeterminate = true)
    progress.setIndeterminate(getString(R.string.txt_working))

    lifecycleScope.launch(Dispatchers.IO) {
        val (restored, failed) = restoreTextures(pkg)
        withContext(Dispatchers.Main) {
            progress.dismiss()
            when {
                failed > 0 -> Toasty.error(this@runRestore, getString(R.string.tex_toast_restore_failed), Toast.LENGTH_LONG, true).show()
                restored > 0 -> {
                    if (!anyTextureBackup()) setTextureStatus(null)
                    Toasty.success(this@runRestore, getString(R.string.tex_toast_restored), Toast.LENGTH_LONG, true).show()
                }
                else -> Toasty.info(this@runRestore, getString(R.string.tex_toast_no_backup), Toast.LENGTH_SHORT, true).show()
            }
        }
    }
}

internal fun MainActivity.confirmRestoreAllTextures() {
    if (!anyTextureBackup()) {
        setTextureStatus(null)
        Toasty.info(this, getString(R.string.tex_toast_no_backup), Toast.LENGTH_SHORT, true).show()
        return
    }
    Sheet(this)
        .icon(R.drawable.ic_reset)
        .title(getString(R.string.tex_restore_title))
        .message(getString(R.string.tex_restore_all_msg))
        .button(getString(R.string.btn_cancel), SheetStyle.TONAL)
        .button(getString(R.string.tex_btn_restore), SheetStyle.PRIMARY) { runRestore(null) }
        .show()
}
