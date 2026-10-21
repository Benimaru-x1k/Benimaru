package com.benimaru.official

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import es.dmoral.toasty.Toasty
import java.io.Closeable
import java.io.File
import java.io.FileNotFoundException
import java.util.Locale
import java.util.zip.ZipException
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

private const val COPY_BUFFER = 256 * 1024

private val C_APK_GREEN = Color.parseColor("#2E9E5B")
private val C_APK_FAIL = Color.parseColor("#E53935")


private val OBB_ENTRY = Regex("^Android/obb/[A-Za-z0-9._]+/[A-Za-z0-9._-]+\\.obb$")


private val ABI_TOKEN = Regex("(?:^|[^a-z0-9])(arm64_v8a|armeabi_v7a|armeabi|x86_64|x86|mips64|mips)(?:$|[^a-z0-9])")

private class PickedBundle(
    val name: String,
    val uri: Uri,
    val apks: List<String>,
    val apkBytes: Long,
    val obbs: List<String>,
    val obbBytes: Long
)

private sealed class BundleRead {
    class Ok(val bundle: PickedBundle) : BundleRead()
    object Unreadable : BundleRead()
    object NotZip : BundleRead()
    object NoApk : BundleRead()
}

private class UserCancelled : Exception()

private class OpenedZip(
    val zip: ZipFile,
    private val pfd: ParcelFileDescriptor?,
    private val temp: File?
) : Closeable {
    override fun close() {
        runCatching { zip.close() }
        runCatching { pfd?.close() }
        runCatching { temp?.delete() }
    }
}



private fun MainActivity.openZip(uri: Uri): OpenedZip? {
    try {
        val pfd = contentResolver.openFileDescriptor(uri, "r")
        if (pfd != null) {
            try {
                return OpenedZip(ZipFile("/proc/self/fd/${pfd.fd}"), pfd, null)
            } catch (e: ZipException) {
                runCatching { pfd.close() }
                throw e
            } catch (e: Exception) {
                runCatching { pfd.close() }
            }
        }
    } catch (e: ZipException) {
        throw e
    } catch (e: Exception) {
    }

    val tmp = File(cacheDir, "bundle_${System.currentTimeMillis()}.zip")
    return try {
        val input = contentResolver.openInputStream(uri) ?: return null
        input.use { src -> tmp.outputStream().use { dst -> src.copyTo(dst, COPY_BUFFER) } }
        OpenedZip(ZipFile(tmp), null, tmp)
    } catch (e: ZipException) {
        tmp.delete()
        throw e
    } catch (e: Exception) {
        tmp.delete()
        null
    }
}

private fun supportedAbis(): Set<String> =
    Build.SUPPORTED_ABIS.map { it.replace('-', '_').lowercase(Locale.ROOT) }.toSet()

private fun abiAllowed(entryName: String): Boolean {
    val base = entryName.substringAfterLast('/').substringBeforeLast('.')
        .lowercase(Locale.ROOT).replace('-', '_')
    val abi = ABI_TOKEN.find(base)?.groupValues?.get(1) ?: return true
    return abi in supportedAbis()
}


private fun selectApks(names: List<String>): List<String> {
    val apks = names.filter { it.endsWith(".apk", ignoreCase = true) }

    val bundletool = apks.filter {
        it.startsWith("splits/", ignoreCase = true) || it.startsWith("asset-slices/", ignoreCase = true)
    }
    if (bundletool.isNotEmpty()) return bundletool.filter { abiAllowed(it) }

    apks.firstOrNull { it.equals("universal.apk", ignoreCase = true) }?.let { return listOf(it) }

    val standalones = apks.filter { it.startsWith("standalones/", ignoreCase = true) }
    if (standalones.isNotEmpty()) {
        for (abi in Build.SUPPORTED_ABIS.map { it.replace('-', '_').lowercase(Locale.ROOT) }) {
            standalones.firstOrNull { it.lowercase(Locale.ROOT).replace('-', '_').contains(abi) }
                ?.let { return listOf(it) }
        }
        return listOf(standalones.first())
    }

    return apks.filter { !it.startsWith("instant/", ignoreCase = true) && abiAllowed(it) }
}

private fun MainActivity.readBundle(uri: Uri): BundleRead {
    return try {
        var name = "bundle.apks"
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0)?.takeIf { it.isNotBlank() }?.let { name = it }
        }
        val opened = openZip(uri) ?: return BundleRead.Unreadable
        opened.use { oz ->
            val entries = oz.zip.entries().toList().filter { !it.isDirectory }
            val apkNames = selectApks(entries.map { it.name })
            if (apkNames.isEmpty()) return BundleRead.NoApk

            val byName = entries.associateBy { it.name }
            val apkBytes = apkNames.sumOf { (byName[it]?.size ?: 0L).coerceAtLeast(0L) }
            val obbNames = entries.map { it.name }.filter { OBB_ENTRY.matches(it) }
            val obbBytes = obbNames.sumOf { (byName[it]?.size ?: 0L).coerceAtLeast(0L) }
            BundleRead.Ok(PickedBundle(name, uri, apkNames, apkBytes, obbNames, obbBytes))
        }
    } catch (e: ZipException) {
        BundleRead.NotZip
    } catch (e: Exception) {
        BundleRead.Unreadable
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024L * 1024L -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    bytes < 1024L * 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    else -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
}

private fun shizukuReady(): Boolean = try {
    Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
} catch (e: Throwable) {
    false
}


internal fun MainActivity.showApkInstallSheet() {
    var bundle: PickedBundle? = null
    var sheetRef: Sheet? = null
    val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

    var pick: () -> Unit = {}
    pick = {
        onScriptPicked = { uri ->
            if (uri != null) {
                lifecycleScope.launch {
                    val result = withContext(Dispatchers.IO) { readBundle(uri) }
                    when (result) {
                        is BundleRead.Ok -> {
                            bundle = result.bundle
                            sheetRef?.buttonViews?.firstOrNull()?.isEnabled = true
                            renderBundlePicker(box, result.bundle, pick)
                        }
                        BundleRead.Unreadable -> apkToastErr(R.string.apk_err_read)
                        BundleRead.NotZip -> apkToastErr(R.string.apk_err_zip)
                        BundleRead.NoApk -> apkToastErr(R.string.apk_err_noapk)
                    }
                }
            }
        }
        scriptPicker.launch(arrayOf("*/*"))
    }
    renderBundlePicker(box, null, pick)

    sheetRef = Sheet(this)
        .icon(R.drawable.ic_download)
        .title(getString(R.string.title_apk_install))
        .message(getString(R.string.apk_msg))
        .content(box)
        .button(getString(R.string.apk_btn_install), SheetStyle.PRIMARY) {
            bundle?.let { beginApkInstall(it) }
        }
        .button(getString(R.string.btn_close), SheetStyle.TONAL)
        .show()
    sheetRef?.buttonViews?.firstOrNull()?.isEnabled = false
}

private fun MainActivity.apkToastErr(res: Int) {
    Toasty.error(this, getString(res), Toast.LENGTH_SHORT, true).show()
}

private fun MainActivity.renderBundlePicker(box: LinearLayout, bundle: PickedBundle?, pick: () -> Unit) {
    val ctx: Context = this
    box.removeAllViews()

    fun styleRow(row: LinearLayout) {
        (row.layoutParams as? LinearLayout.LayoutParams)?.topMargin = 0
        row.background = Ui.ripple(
            ctx, ColorUtils.setAlphaComponent(C_APK_GREEN, 0x14), ctx.dp(16),
            ColorUtils.setAlphaComponent(C_APK_GREEN, 0x99), ctx.dp(1)
        )
    }

    if (bundle == null) {
        box.addView(
            optionRow(
                ctx,
                getString(R.string.apk_pick_title),
                getString(R.string.apk_pick_sub),
                Ui.drawable(ctx, R.drawable.ic_download)
            ) { pick() }.also { styleRow(it) }
        )
        return
    }

    box.addView(
        optionRow(
            ctx,
            bundle.name,
            getString(R.string.apk_meta, formatSize(bundle.apkBytes), bundle.apks.size),
            Ui.drawable(ctx, R.drawable.ic_download)
        ) { pick() }.also { row ->
            styleRow(row)
            row.addView(TextView(ctx).apply {
                text = getString(R.string.apk_change)
                textSize = 12f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Ui.accent(ctx))
            })
        }
    )

    if (bundle.obbs.isNotEmpty()) {
        box.addView(
            infoCard(ctx, getString(R.string.apk_obb_note, bundle.obbs.size), tint = Color.parseColor("#E09B00"))
                .apply { setPadding(ctx.dp(14), ctx.dp(12), ctx.dp(14), ctx.dp(12)) }
        )
        (box.getChildAt(box.childCount - 1).layoutParams as? LinearLayout.LayoutParams)?.topMargin = ctx.dp(12)
    }
}

private fun MainActivity.showApkFailure(message: String?) {
    Sheet(this)
        .icon(R.drawable.ic_warning, C_APK_FAIL)
        .title(getString(R.string.apk_fail_title))
        .message(message?.takeIf { it.isNotBlank() } ?: getString(R.string.apk_fail_unknown))
        .button(getString(R.string.btn_close), SheetStyle.TONAL)
        .show()
}


private fun MainActivity.beginApkInstall(b: PickedBundle) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
        Sheet(this)
            .icon(R.drawable.ic_lock)
            .title(getString(R.string.dlg_allow_installs))
            .message(getString(R.string.dlg_allow_installs_msg))
            .button(getString(R.string.btn_not_now), SheetStyle.TONAL)
            .button(getString(R.string.btn_open_settings), SheetStyle.PRIMARY) {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))
                )
            }
            .show()
        return
    }

    val act: MainActivity = this
    var cancelled = false
    var sessionId = -1
    var receiver: BroadcastReceiver? = null

    fun unregister() {
        val r = receiver ?: return
        receiver = null
        runCatching { act.unregisterReceiver(r) }
    }

    val progress = ProgressSheet(this)
        .icon(R.drawable.ic_download)
        .title(getString(R.string.title_apk_install))
        .message(b.name)
        .cancelButton(getString(R.string.btn_cancel)) {
            cancelled = true
            val id = sessionId
            if (id >= 0) runCatching { act.packageManager.packageInstaller.abandonSession(id) }
        }
        .show()
    progress.setIndeterminate(getString(R.string.apk_phase_read))

    val action = "$packageName.APK_INSTALL_RESULT.${System.currentTimeMillis()}"

    receiver = object : BroadcastReceiver() {
        @Suppress("DEPRECATION")
        override fun onReceive(c: Context, intent: Intent) {
            val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
            val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
            when (status) {
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                    progress.setIndeterminate(act.getString(R.string.apk_confirm))
                    val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                    if (confirm != null) {
                        confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { act.startActivity(confirm) }
                    }
                }
                PackageInstaller.STATUS_SUCCESS -> {
                    unregister()
                    act.onApkInstalled(b, progress)
                }
                PackageInstaller.STATUS_FAILURE_ABORTED -> {
                    unregister()
                    progress.dismiss()
                    Toasty.info(act, act.getString(R.string.apk_cancelled), Toast.LENGTH_SHORT, true).show()
                }
                else -> {
                    unregister()
                    progress.dismiss()
                    act.showApkFailure(msg)
                }
            }
        }
    }
    ContextCompat.registerReceiver(this, receiver!!, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)

    lifecycle.addObserver(object : DefaultLifecycleObserver {
        override fun onDestroy(owner: LifecycleOwner) {
            unregister()
        }
    })

    lifecycleScope.launch(Dispatchers.IO) {
        val installer = packageManager.packageInstaller
        try {
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            sessionId = installer.createSession(params)
            if (cancelled) throw UserCancelled()

            installer.openSession(sessionId).use { session ->
                val opened = openZip(b.uri) ?: throw FileNotFoundException(b.name)
                opened.use { oz ->
                    var done = 0L
                    var lastPct = -1
                    val buf = ByteArray(COPY_BUFFER)
                    b.apks.forEachIndexed { index, entryName ->
                        val entry = oz.zip.getEntry(entryName) ?: throw FileNotFoundException(entryName)
                        oz.zip.getInputStream(entry).use { input ->
                            session.openWrite("split_$index.apk", 0, if (entry.size >= 0) entry.size else -1L).use { out ->
                                while (true) {
                                    if (cancelled) throw UserCancelled()
                                    val n = input.read(buf)
                                    if (n < 0) break
                                    out.write(buf, 0, n)
                                    done += n
                                    if (b.apkBytes > 0) {
                                        val pct = (done * 100 / b.apkBytes).toInt().coerceIn(0, 100)
                                        if (pct != lastPct) {
                                            lastPct = pct
                                            runOnUiThread { progress.update(pct, getString(R.string.apk_phase_copy, pct)) }
                                        }
                                    }
                                }
                                session.fsync(out)
                            }
                        }
                    }
                }
                if (cancelled) throw UserCancelled()

                val intent = Intent(action).setPackage(packageName)
                var flags = PendingIntent.FLAG_UPDATE_CURRENT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags = flags or PendingIntent.FLAG_MUTABLE
                val pi = PendingIntent.getBroadcast(this@beginApkInstall, sessionId, intent, flags)
                session.commit(pi.intentSender)
            }
            runOnUiThread { progress.setIndeterminate(getString(R.string.apk_confirm)) }
        } catch (e: Exception) {
            runCatching { if (sessionId >= 0) installer.abandonSession(sessionId) }
            runOnUiThread {
                unregister()
                progress.dismiss()
                if (e !is UserCancelled) showApkFailure(e.message)
            }
        }
    }
}


private fun MainActivity.onApkInstalled(b: PickedBundle, progress: ProgressSheet) {
    if (b.obbs.isEmpty()) {
        progress.dismiss()
        Toasty.success(this, getString(R.string.apk_ok), Toast.LENGTH_LONG, true).show()
        return
    }
    if (!shizukuReady()) {
        progress.dismiss()
        Toasty.warning(this, getString(R.string.apk_obb_skipped), Toast.LENGTH_LONG, true).show()
        return
    }

    progress.update(0, getString(R.string.apk_obb_copy, 0))
    lifecycleScope.launch(Dispatchers.IO) {
        var ok = true
        try {
            val opened = openZip(b.uri)
            if (opened == null) {
                ok = false
            } else {
                opened.use { oz ->
                    var done = 0L
                    var lastPct = -1
                    val buf = ByteArray(COPY_BUFFER)
                    for (name in b.obbs) {
                        val entry = oz.zip.getEntry(name) ?: continue
                        val dest = "/sdcard/$name"
                        val dir = dest.substringBeforeLast('/')
                        val p = Shizuku.newProcess(
                            arrayOf("sh", "-c", "mkdir -p '$dir' && cat > '$dest'"), null, null
                        )
                        oz.zip.getInputStream(entry).use { input ->
                            p.outputStream.use { out ->
                                while (true) {
                                    val n = input.read(buf)
                                    if (n < 0) break
                                    out.write(buf, 0, n)
                                    done += n
                                    if (b.obbBytes > 0) {
                                        val pct = (done * 100 / b.obbBytes).toInt().coerceIn(0, 100)
                                        if (pct != lastPct) {
                                            lastPct = pct
                                            runOnUiThread { progress.update(pct, getString(R.string.apk_obb_copy, pct)) }
                                        }
                                    }
                                }
                            }
                        }
                        if (p.waitFor() != 0) ok = false
                    }
                }
            }
        } catch (e: Exception) {
            ok = false
        }
        withContext(Dispatchers.Main) {
            progress.dismiss()
            if (ok) Toasty.success(this@onApkInstalled, getString(R.string.apk_obb_ok), Toast.LENGTH_LONG, true).show()
            else Toasty.error(this@onApkInstalled, getString(R.string.apk_obb_fail), Toast.LENGTH_LONG, true).show()
        }
    }
}
