package com.benimaru.official

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
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
import org.json.JSONObject
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

class MainActivity : AppCompatActivity() {

    private val SHIZUKU_REQUEST_CODE = 100
    private var isCrosshairEnabled = false

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

        verifyAppSignature()
        checkForUpdates()

        Shizuku.addRequestPermissionResultListener(permissionListener)
        checkShizukuStatus()
        setupClickListeners()
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(permissionListener)
    }

    // --- Security & Signature Checker ---

    private fun verifyAppSignature() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val currentSignature = getCurrentAppSignature()
                var expectedSignature = "3ba04fa59e97759b9d2cd2b85e4598d1efa87caf714a70e46f1d8c8f5bb7658b"

                try {
                    val url = URL("https://raw.githubusercontent.com/Benimaru-x1k/Benimaru/main/signature.txt")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 3000
                    conn.readTimeout = 3000

                    if (conn.responseCode == 200) {
                        val fetchedSig = conn.inputStream.bufferedReader().readText().trim()
                        if (fetchedSig.isNotEmpty()) {
                            expectedSignature = fetchedSig
                        }
                    }
                } catch (e: Exception) {}

                if (!currentSignature.equals(expectedSignature, ignoreCase = true)) {
                    withContext(Dispatchers.Main) {
                        Toasty.error(this@MainActivity, "Unofficial APK detected! Closing app.", Toast.LENGTH_LONG, true).show()
                        delay(2000)
                        finishAffinity()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { finishAffinity() }
            }
        }
    }

    private fun getCurrentAppSignature(): String {
        val signatures = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            val packageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            packageInfo.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            val packageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            @Suppress("DEPRECATION")
            packageInfo.signatures
        }

        if (signatures.isNullOrEmpty()) return ""
        val md = MessageDigest.getInstance("SHA-256")
        md.update(signatures[0].toByteArray())
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    // --- Auto Updater Logic ---
    private fun checkForUpdates() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = URL("https://raw.githubusercontent.com/Benimaru-x1k/Benimaru/main/version.json")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000

                if (conn.responseCode == 200) {
                    val jsonString = conn.inputStream.bufferedReader().readText()
                    val jsonObject = JSONObject(jsonString)

                    val latestVersionCode = jsonObject.getInt("versionCode")
                    val latestVersionName = jsonObject.getString("versionName")
                    val apkUrl = jsonObject.getString("apkUrl")
                    val releaseNotes = jsonObject.getString("releaseNotes")

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
                            showUpdateDialog(latestVersionName, releaseNotes, apkUrl)
                        }
                    }
                }
            } catch (e: Exception) {
                // Fails silently if offline or URL is inaccessible
            }
        }
    }

    private fun showUpdateDialog(versionName: String, releaseNotes: String, apkUrl: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Update Available (v$versionName)")
            .setMessage("A new version of Benimaru Tool is available.\n\nWhat's new:\n$releaseNotes")
            .setCancelable(false)
            .setPositiveButton("Update Now") { _, _ -> downloadAppUpdate(apkUrl) }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun downloadAppUpdate(downloadUrl: String) {
        val fileName = "BenimaruTool-update.apk"
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
            .setTitle("Downloading Update")
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
                    Toasty.error(this@MainActivity, "Update failed: ${e.message}", Toast.LENGTH_SHORT, true).show()
                }
            }
        }
    }

    // --- Shizuku Setup & Logic ---

    private fun checkShizukuStatus() {
        if (isShizukuInstalled()) {
            if (Shizuku.pingBinder()) {
                if (hasShizukuPermission()) fetchSystemStatuses()
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

            val dnsMode = runAdbCommandWithResult("settings get global private_dns_mode")
            val dnsSpecifier = runAdbCommandWithResult("settings get global private_dns_specifier")
            val dnsStatus = if (dnsMode.trim() == "hostname" && dnsSpecifier.isNotEmpty() && dnsSpecifier != "null") {
                "Status: ${dnsSpecifier.trim()}"
            } else {
                "Status: Default"
            }

            withContext(Dispatchers.Main) {
                findViewById<TextView>(R.id.tvStatusRefresh).text = refreshStatus
                findViewById<TextView>(R.id.tvStatusResolution).text = resStatus
                findViewById<TextView>(R.id.tvStatusNetwork).text = netStatus
                findViewById<TextView>(R.id.tvStatusTouch).text = touchStatus
                findViewById<TextView>(R.id.tvStatusDns).text = dnsStatus
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

        findViewById<CardView>(R.id.cardCustomDns).setOnClickListener {
            showDnsDialog()
        }

        // Crosshair Configuration
        findViewById<CardView>(R.id.cardCrosshair).setOnClickListener {
            showCrosshairConfigDialog()
        }

        findViewById<Button>(R.id.btnLaunchGame).setOnClickListener {
            showGameLauncherDialog()
        }

        // Reset Everything
        findViewById<Button>(R.id.btnResetAll).setOnClickListener {
            val resetCmd = """
                cmd power set-fixed-performance-mode-enabled false
                settings delete system min_refresh_rate
                settings delete system peak_refresh_rate
                wm size reset
                wm density reset
                settings delete system pointer_speed
                settings put secure long_press_timeout 400
                settings put global private_dns_mode default
                settings delete global private_dns_specifier
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

    private fun showDnsDialog() {
        val dnsOptions = arrayOf(
            "Control D (Blocks ads/trackers)",
            "Cloudflare (Fastest, no logs)",
            "Quad9 (Blocks malicious links)",
            "NextDNS (Personalized blocking)",
            "Google (Stable and reliable)",
            "Default (Off / Automatic)"
        )

        val dnsHostnames = arrayOf(
            "p2.freedns.controld.com",
            "one.one.one.one",
            "dns.quad9.net",
            "dns.nextdns.io",
            "dns.google",
            ""
        )

        MaterialAlertDialogBuilder(this)
            .setTitle("Select Custom DNS")
            .setItems(dnsOptions) { _, which ->
                val selectedHostname = dnsHostnames[which]
                if (selectedHostname.isEmpty()) {
                    val cmd = "settings put global private_dns_mode default && settings delete global private_dns_specifier"
                    runAdbCommand(cmd, "DNS reset to Default") { fetchSystemStatuses() }
                } else {
                    val cmd = "settings put global private_dns_mode hostname && settings put global private_dns_specifier $selectedHostname"
                    runAdbCommand(cmd, "DNS set to $selectedHostname") { fetchSystemStatuses() }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

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

    // --- Crosshair & Launch Game logic Below ---

    private fun showCrosshairConfigDialog() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
            Toasty.info(this, "Please allow 'Display over other apps' to use the crosshair", Toast.LENGTH_LONG, true).show()
            return
        }

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 32, 64, 32)
        }

        // Shape Spinner
        val styleLabel = TextView(this).apply { text = "Shape"; setPadding(0, 0, 0, 8) }
        val styleSpinner = Spinner(this)
        val styles = arrayOf("Cross", "Dot", "Cross with Circle")
        styleSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, styles)

        // Color Spinner
        val colorLabel = TextView(this).apply { text = "Color"; setPadding(0, 32, 0, 8) }
        val colorSpinner = Spinner(this)
        val colors = arrayOf("White", "Black", "Blue", "Red", "Green")
        colorSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, colors)
        colorSpinner.setSelection(3) // Default to Red

        // Size Spinner
        val sizeLabel = TextView(this).apply { text = "Size"; setPadding(0, 32, 0, 8) }
        val sizeSpinner = Spinner(this)
        val sizes = arrayOf("Small", "Medium", "Large")
        sizeSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, sizes)
        sizeSpinner.setSelection(1) // Default to Medium

        layout.addView(styleLabel)
        layout.addView(styleSpinner)
        layout.addView(colorLabel)
        layout.addView(colorSpinner)
        layout.addView(sizeLabel)
        layout.addView(sizeSpinner)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Customize Crosshair")
            .setView(layout)
            .setPositiveButton(if (isCrosshairEnabled) "Apply" else "Start") { _, _ ->
                val selectedStyle = styles[styleSpinner.selectedItemPosition]
                val selectedColor = colors[colorSpinner.selectedItemPosition]
                val selectedSize = sizes[sizeSpinner.selectedItemPosition]
                startCrosshairService(selectedStyle, selectedColor, selectedSize)
            }
            .setNegativeButton("Cancel", null)

        // Show a "Turn Off" button if the crosshair is currently running
        if (isCrosshairEnabled) {
            dialog.setNeutralButton("Turn Off") { _, _ ->
                stopCrosshairService()
            }
        }

        dialog.show()
    }

    private fun startCrosshairService(style: String, color: String, size: String) {
        val intent = Intent(this, CrosshairService::class.java).apply {
            putExtra("STYLE", style)
            putExtra("COLOR", color)
            putExtra("SIZE", size)
        }
        startService(intent)
        isCrosshairEnabled = true
        findViewById<TextView>(R.id.tvStatusCrosshair).text = "Status: $style ($color, $size)"
        Toasty.success(this, "Crosshair Updated", Toast.LENGTH_SHORT, true).show()
    }

    private fun stopCrosshairService() {
        val intent = Intent(this, CrosshairService::class.java)
        stopService(intent)
        isCrosshairEnabled = false
        findViewById<TextView>(R.id.tvStatusCrosshair).text = "Status: Disabled"
        Toasty.success(this, "Crosshair disabled", Toast.LENGTH_SHORT, true).show()
    }

    private fun showGameLauncherDialog() {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply { addCategory(Intent.CATEGORY_LAUNCHER) }
        val allApps = pm.queryIntentActivities(intent, 0)

        val gameApps = allApps.filter {
            val appInfo = it.activityInfo.applicationInfo
            val isGameFlag = (appInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0
            val isGameCategory = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                appInfo.category == ApplicationInfo.CATEGORY_GAME
            } else false
            isGameFlag || isGameCategory
        }

        if (gameApps.isEmpty()) {
            Toasty.info(this, "No games found on your device", Toast.LENGTH_SHORT, true).show()
            return
        }

        val adapter = object : ArrayAdapter<ResolveInfo>(this, android.R.layout.select_dialog_item, android.R.id.text1, gameApps) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                val app = gameApps[position]
                view.text = app.loadLabel(pm)

                val icon = app.loadIcon(pm)
                icon.setBounds(0, 0, 96, 96)
                view.setCompoundDrawables(icon, null, null, null)
                view.compoundDrawablePadding = 24

                return view
            }
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Launch a Game")
            .setAdapter(adapter) { _, which ->
                val selectedApp = gameApps[which]
                val launchIntent = pm.getLaunchIntentForPackage(selectedApp.activityInfo.packageName)
                if (launchIntent != null) {
                    startActivity(launchIntent)
                    Toasty.success(this, "Launching ${selectedApp.loadLabel(pm)}", Toast.LENGTH_SHORT, true).show()
                } else {
                    Toasty.error(this, "Failed to launch game", Toast.LENGTH_SHORT, true).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // --- Shizuku Download Logic Below ---

    private fun showShizukuRequiredDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Shizuku Required")
            .setMessage("Shizuku is required to use this app. Would you like to download it now?")
            .setCancelable(false)
            .setPositiveButton("Download") { _, _ -> downloadShizukuApk() }
            .setNegativeButton("Dismiss") { _, _ -> finishAffinity() }
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