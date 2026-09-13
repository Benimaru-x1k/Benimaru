package com.benimaru.official

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import es.dmoral.toasty.Toasty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private val SHIZUKU_REQUEST_CODE = 100

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == SHIZUKU_REQUEST_CODE) {
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                Toasty.success(this, "Shizuku permission granted! You can now apply settings.", Toast.LENGTH_SHORT, true).show()
                fetchSystemStatuses()
            } else {
                Toasty.error(this, "Shizuku permission denied. The app cannot function without it.", Toast.LENGTH_LONG, true).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        Shizuku.addRequestPermissionResultListener(permissionListener)

        checkShizukuStatus()
        setupClickListeners()
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(permissionListener)
    }

    // --- Shizuku Setup & Logic ---

    private fun checkShizukuStatus() {
        if (isShizukuInstalled()) {
            if (Shizuku.pingBinder()) {
                if (hasShizukuPermission()) {
                    fetchSystemStatuses()
                }
            } else {
                Toasty.warning(this, "Shizuku is installed but not running. Launching app...", Toast.LENGTH_LONG, true).show()
                launchShizukuApp()
            }
        } else {
            showShizukuRequiredDialog()
        }
    }

    private fun hasShizukuPermission(): Boolean {
        if (!Shizuku.pingBinder()) {
            Toasty.error(this, "Shizuku is not running!", Toast.LENGTH_SHORT, true).show()
            return false
        }
        return if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            true
        } else {
            if (Shizuku.shouldShowRequestPermissionRationale()) {
                Toasty.info(this, "Please open Shizuku and grant permission to this app.", Toast.LENGTH_LONG, true).show()
            } else {
                Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
            }
            false
        }
    }

    private fun isShizukuInstalled(): Boolean {
        return try {
            packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun launchShizukuApp() {
        val intent = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
        if (intent != null) {
            startActivity(intent)
            finishAffinity()
        } else {
            Toasty.error(this, "Unable to launch Shizuku manager", Toast.LENGTH_SHORT, true).show()
            finishAffinity()
        }
    }

    // --- System Status Fetching ---

    private fun fetchSystemStatuses() {
        if (!hasShizukuPermission()) return

        lifecycleScope.launch(Dispatchers.IO) {
            val minRefresh = runAdbCommandWithResult("settings get system min_refresh_rate")
            val maxRefresh = runAdbCommandWithResult("settings get system peak_refresh_rate")
            val refreshStatus = if (minRefresh.isNotEmpty() && minRefresh != "null" && maxRefresh.isNotEmpty() && maxRefresh != "null") {
                "Status: $minRefresh - $maxRefresh Hz"
            } else {
                "Status: Default"
            }

            val wmSize = runAdbCommandWithResult("wm size")
            val resStatus = if (wmSize.contains("Override size:")) {
                "Status: " + wmSize.substringAfter("Override size:").trim()
            } else if (wmSize.contains("Physical size:")) {
                "Status: " + wmSize.substringAfter("Physical size:").trim()
            } else {
                "Status: Default"
            }

            val networkOpt = runAdbCommandWithResult("settings get global tether_dun_required")
            val netStatus = if (networkOpt == "0") "Status: Optimized" else "Status: Default"

            val touchOpt = runAdbCommandWithResult("settings get system pointer_speed")
            val touchStatus = if (touchOpt == "7") "Status: Improved" else "Status: Default"

            withContext(Dispatchers.Main) {
                findViewById<TextView>(R.id.tvStatusRefresh).text = refreshStatus
                findViewById<TextView>(R.id.tvStatusResolution).text = resStatus
                findViewById<TextView>(R.id.tvStatusNetwork).text = netStatus
                findViewById<TextView>(R.id.tvStatusTouch).text = touchStatus
            }
        }
    }

    private fun runAdbCommandWithResult(command: String): String {
        return try {
            val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = java.lang.StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            process.waitFor()
            output.toString().trim()
        } catch (e: Exception) {
            ""
        }
    }

    // --- Click Listeners & Commands ---

    private fun setupClickListeners() {
        findViewById<CardView>(R.id.cardFixedPerformance).setOnClickListener {
            runAdbCommand("cmd power set-fixed-performance-mode-enabled true", "Fixed Performance Enabled") {
                findViewById<TextView>(R.id.tvStatusFixedPerf).text = "Status: Enabled"
            }
        }

        findViewById<CardView>(R.id.cardOptimizeSystem).setOnClickListener {
            runAdbCommand("cmd activity kill-all", "System Optimized (Background processes cleared)") {
                findViewById<TextView>(R.id.tvStatusOptimize).text = "Status: Recently Optimized"
            }
        }

        findViewById<CardView>(R.id.cardImproveNetwork).setOnClickListener {
            runAdbCommand("settings put global tether_dun_required 0", "Network Optimized") {
                fetchSystemStatuses()
            }
        }

        findViewById<CardView>(R.id.cardImproveTouch).setOnClickListener {
            runAdbCommand("settings put system pointer_speed 7 && settings put secure long_press_timeout 250", "Touch Latency Improved") {
                fetchSystemStatuses()
            }
        }

        findViewById<CardView>(R.id.cardAdjustRefreshRate).setOnClickListener {
            showRefreshRateDialog()
        }

        findViewById<CardView>(R.id.cardChangeResolution).setOnClickListener {
            showResolutionDialog()
        }

        findViewById<Button>(R.id.btnResetAll).setOnClickListener {
            val resetCmd = """
                cmd power set-fixed-performance-mode-enabled false
                settings delete system min_refresh_rate
                settings delete system peak_refresh_rate
                wm size reset
                wm density reset
                settings delete system pointer_speed
                settings put secure long_press_timeout 400
            """.trimIndent().replace("\n", " && ")

            runAdbCommand(resetCmd, "All adjustments reset to default") {
                findViewById<TextView>(R.id.tvStatusFixedPerf).text = "Status: Default"
                findViewById<TextView>(R.id.tvStatusOptimize).text = "Status: Ready"
                fetchSystemStatuses()
            }
        }
    }

    private fun runAdbCommand(command: String, successMessage: String, onSuccess: () -> Unit = {}) {
        if (!hasShizukuPermission()) return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
                val exitCode = process.waitFor()
                withContext(Dispatchers.Main) {
                    if (exitCode == 0) {
                        Toasty.success(this@MainActivity, successMessage, Toast.LENGTH_SHORT, true).show()
                        onSuccess()
                    } else {
                        Toasty.error(this@MainActivity, "Command failed (Exit code: $exitCode)", Toast.LENGTH_SHORT, true).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toasty.error(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT, true).show()
                }
            }
        }
    }

    // --- Custom Dialogs ---

    private fun showRefreshRateDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 32, 64, 32)
        }

        val minInput = EditText(this).apply {
            hint = "Min Refresh Rate (e.g., 60)"
            inputType = InputType.TYPE_CLASS_NUMBER
        }

        val maxInput = EditText(this).apply {
            hint = "Max Refresh Rate (e.g., 120)"
            inputType = InputType.TYPE_CLASS_NUMBER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 16 }
        }

        layout.addView(minInput)
        layout.addView(maxInput)

        MaterialAlertDialogBuilder(this)
            .setTitle("Adjust Refresh Rate")
            .setView(layout)
            .setPositiveButton("Apply") { _, _ ->
                val min = minInput.text.toString().trim()
                val max = maxInput.text.toString().trim()
                if (min.isNotEmpty() && max.isNotEmpty()) {
                    val cmd = "settings put system min_refresh_rate $min && settings put system peak_refresh_rate $max"
                    runAdbCommand(cmd, "Refresh rate set to $min - $max Hz") {
                        fetchSystemStatuses()
                    }
                } else {
                    Toasty.warning(this, "Please enter valid numbers", Toast.LENGTH_SHORT, true).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showResolutionDialog() {
        val metrics = resources.displayMetrics
        val currentWidth = metrics.widthPixels
        val currentHeight = metrics.heightPixels
        val currentDpi = metrics.densityDpi

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 32, 64, 32)
        }

        val widthInput = EditText(this).apply {
            hint = "Width (Current: $currentWidth)"
            inputType = InputType.TYPE_CLASS_NUMBER
        }

        val heightInput = EditText(this).apply {
            hint = "Height (Current: $currentHeight)"
            inputType = InputType.TYPE_CLASS_NUMBER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 16 }
        }

        layout.addView(widthInput)
        layout.addView(heightInput)

        MaterialAlertDialogBuilder(this)
            .setTitle("Change Resolution")
            .setMessage("DPI will be calculated automatically.")
            .setView(layout)
            .setPositiveButton("Preview") { _, _ ->
                val wStr = widthInput.text.toString().trim()
                val hStr = heightInput.text.toString().trim()

                if (wStr.isNotEmpty() && hStr.isNotEmpty()) {
                    if (!hasShizukuPermission()) return@setPositiveButton

                    val newWidth = wStr.toInt()
                    val newHeight = hStr.toInt()
                    val newDpi = ((newWidth.toFloat() / currentWidth.toFloat()) * currentDpi).toInt()

                    val cmd = "wm size ${newWidth}x${newHeight} && wm density $newDpi"

                    lifecycleScope.launch(Dispatchers.IO) {
                        val process = Shizuku.newProcess(arrayOf("sh", "-c", cmd), null, null)
                        if (process.waitFor() == 0) {
                            withContext(Dispatchers.Main) {
                                showResolutionPreviewCountdown()
                            }
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showResolutionPreviewCountdown() {
        var timerJob: Job? = null

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Confirm Resolution")
            .setMessage("Reverting to default in 6 seconds...")
            .setCancelable(false)
            .setPositiveButton("Save") { _, _ ->
                timerJob?.cancel()
                Toasty.success(this, "Resolution saved", Toast.LENGTH_SHORT, true).show()
                fetchSystemStatuses()
            }
            .setNegativeButton("Revert") { _, _ ->
                timerJob?.cancel()
                runAdbCommand("wm size reset && wm density reset", "Resolution Reverted") {
                    fetchSystemStatuses()
                }
            }
            .create()

        dialog.show()

        timerJob = lifecycleScope.launch(Dispatchers.Main) {
            for (i in 5 downTo 1) {
                delay(1000)
                dialog.setMessage("Reverting to default in $i seconds...")
            }
            delay(1000)

            if (dialog.isShowing) {
                dialog.dismiss()
                runAdbCommand("wm size reset && wm density reset", "Auto-reverted due to timeout") {
                    fetchSystemStatuses()
                }
            }
        }
    }

    // --- Shizuku Download Logic Below ---

    private fun showShizukuRequiredDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Shizuku Required")
            .setMessage("Shizuku is required to use this app. Would you like to download it now?")
            .setCancelable(false)
            .setPositiveButton("Download") { _, _ ->
                downloadShizukuApk()
            }
            .setNegativeButton("Dismiss") { _, _ ->
                finishAffinity()
            }
            .show()
    }

    private fun downloadShizukuApk() {
        val downloadUrl = "https://github.com/RikkaApps/Shizuku/releases/download/v13.6.0/shizuku-v13.6.0.r1086.2650830c-release.apk"
        val fileName = "shizuku-v13.6.0.apk"

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 32, 64, 32)
        }

        val tvProgress = TextView(this).apply {
            text = "0%"
            textSize = 16f
        }

        val progressIndicator = LinearProgressIndicator(this).apply {
            isIndeterminate = false
            max = 100
            progress = 0
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 16 }
        }

        layout.addView(tvProgress)
        layout.addView(progressIndicator)

        val progressDialog = MaterialAlertDialogBuilder(this)
            .setTitle("Downloading Shizuku")
            .setView(layout)
            .setCancelable(false)
            .show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = URL(downloadUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.connect()

                val fileLength = connection.contentLength
                val input = connection.inputStream

                val outputFile = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
                val output = FileOutputStream(outputFile)

                val data = ByteArray(4096)
                var total: Long = 0
                var count: Int

                while (input.read(data).also { count = it } != -1) {
                    total += count
                    val progress = ((total * 100) / fileLength).toInt()

                    withContext(Dispatchers.Main) {
                        progressIndicator.progress = progress
                        tvProgress.text = "$progress%"
                    }
                    output.write(data, 0, count)
                }

                output.flush()
                output.close()
                input.close()

                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    installApk(outputFile)
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressDialog.dismiss()
                    Toasty.error(this@MainActivity, "Download failed: ${e.message}", Toast.LENGTH_SHORT, true).show()
                    finishAffinity()
                }
            }
        }
    }

    private fun installApk(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.provider",
                file
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            startActivity(intent)
            finishAffinity()
        } catch (e: Exception) {
            Toasty.error(this, "Failed to parse APK: ${e.message}", Toast.LENGTH_LONG, true).show()
            finishAffinity()
        }
    }
}