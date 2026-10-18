package com.benimaru.official

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import es.dmoral.toasty.Toasty
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal fun MainActivity.checkForUpdates() {
    lifecycleScope.launch(Dispatchers.IO) {
        try {
            val url = URL(AppConfig.VERSION_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000

            if (conn.responseCode == 200) {
                val jsonString = conn.inputStream.bufferedReader().readText()
                val jsonObject = JSONObject(jsonString)

                val latestVersionCode = jsonObject.getInt("versionCode")
                val latestVersionName = jsonObject.getString("versionName")
                val apkUrl = jsonObject.getString("apkUrl")
                val releaseNotes = jsonObject.optString("releaseNotes", "")
                val mandatory = jsonObject.optBoolean("mandatory", false)

                val currentVersionCode = try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        packageManager.getPackageInfo(packageName, 0).longVersionCode.toInt()
                    } else {
                        @Suppress("DEPRECATION")
                        packageManager.getPackageInfo(packageName, 0).versionCode
                    }
                } catch (e: Exception) {
                    1
                }

                if (latestVersionCode > currentVersionCode) {
                    withContext(Dispatchers.Main) {
                        showUpdateDialog(latestVersionName, releaseNotes, apkUrl, mandatory)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("BenimaruTool", "Failed to check for updates", e)
        }
    }
}

internal fun MainActivity.showUpdateDialog(versionName: String, releaseNotes: String, apkUrl: String, mandatory: Boolean = false) {
    if (isFinishing || isDestroyed) return
    val notes = releaseNotes.trim().ifEmpty { getString(R.string.notes_default) }

    val sheet = Sheet(this)
        .icon(R.drawable.ic_download)
        .title(getString(R.string.dlg_update_available))
        .message(getString(R.string.update_ready, versionName))
        .content(infoCard(this, getString(R.string.update_whats_new, notes)))
        .cancelable(!mandatory)
    if (!mandatory) sheet.button(getString(R.string.btn_later), SheetStyle.TONAL)
    sheet.button(getString(R.string.btn_update_now), SheetStyle.PRIMARY) { downloadAppUpdate(apkUrl) }
    sheet.show()
}

internal fun MainActivity.downloadAppUpdate(downloadUrl: String) {
    downloadAndInstall(getString(R.string.dl_updating), downloadUrl, "BenimaruTool-update.apk", finishAfterInstall = false)
}

internal fun MainActivity.formatMb(bytes: Long): String = String.format("%.1f MB", bytes / (1024.0 * 1024.0))


private suspend fun CoroutineScope.streamToPartFile(
    activity: MainActivity, c: HttpURLConnection, part: File, progress: ProgressSheet
): Pair<Long, Long> {
    val total = c.getHeaderField("Content-Length")?.toLongOrNull() ?: c.contentLength.toLong()
    var downloaded = 0L
    var lastPercent = -1
    var lastMb = -1L

    c.inputStream.use { input ->
        FileOutputStream(part).use { output ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                ensureActive()
                val n = input.read(buffer)
                if (n == -1) break
                output.write(buffer, 0, n)
                downloaded += n

                if (total > 0) {
                    val pct = ((downloaded * 100) / total).toInt()
                    if (pct != lastPercent) {
                        lastPercent = pct
                        withContext(Dispatchers.Main) {
                            progress.update(pct, activity.getString(R.string.dl_progress, pct, activity.formatMb(downloaded), activity.formatMb(total)))
                        }
                    }
                } else {
                    val mbNow = downloaded / (1024 * 1024)
                    if (mbNow != lastMb) {
                        lastMb = mbNow
                        withContext(Dispatchers.Main) {
                            progress.setIndeterminate(activity.getString(R.string.dl_downloaded, activity.formatMb(downloaded)))
                        }
                    }
                }
            }
            output.flush()
        }
    }
    return downloaded to total
}

internal fun MainActivity.downloadAndInstall(title: String, url: String, fileName: String, finishAfterInstall: Boolean) {
    val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
    if (dir == null) {
        Toasty.error(this, getString(R.string.toast_storage_na), Toast.LENGTH_SHORT, true).show()
        if (finishAfterInstall) showShizukuRequiredDialog()
        return
    }

    var job: Job? = null
    val progress = ProgressSheet(this)
        .icon(R.drawable.ic_download)
        .title(title)
        .message(getString(R.string.dl_keep_open))
        .cancelButton(getString(R.string.btn_cancel)) { job?.cancel() }
    progress.show(indeterminate = true)

    job = lifecycleScope.launch(Dispatchers.IO) {
        val target = File(dir, fileName)
        val part = File(dir, "$fileName.part")
        var conn: HttpURLConnection? = null
        try {
            val c = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 20000
                instanceFollowRedirects = true
            }
            conn = c
            c.connect()
            if (c.responseCode !in 200..299) {
                throw java.io.IOException(getString(R.string.err_http, c.responseCode))
            }


            val (downloaded, total) = streamToPartFile(this@downloadAndInstall, c, part, progress)

            if (total > 0 && downloaded != total) throw java.io.IOException(getString(R.string.err_incomplete))
            if (target.exists()) target.delete()
            if (!part.renameTo(target)) throw java.io.IOException(getString(R.string.err_save_file))

            withContext(Dispatchers.Main) {
                progress.dismiss()
                installApk(target, finishAfterInstall)
            }
        } catch (e: CancellationException) {
            part.delete()
            withContext(NonCancellable + Dispatchers.Main) {
                progress.dismiss()
                if (finishAfterInstall) showShizukuRequiredDialog()
            }
            throw e
        } catch (e: Exception) {
            part.delete()
            withContext(Dispatchers.Main) {
                progress.dismiss()
                Toasty.error(this@downloadAndInstall, getString(R.string.toast_dl_failed, e.message ?: ""), Toast.LENGTH_LONG, true).show()
                if (finishAfterInstall) showShizukuRequiredDialog()
            }
        } finally {
            conn?.disconnect()
        }
    }
}

internal fun MainActivity.installApk(file: File, finishAfter: Boolean = true) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
        pendingApk = file
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

    try {
        val uri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        startActivity(intent)
        if (finishAfter) finishAffinity()
    } catch (e: Exception) {
        Toasty.error(this, getString(R.string.toast_installer_failed, e.message ?: ""), Toast.LENGTH_LONG, true).show()
    }
}
