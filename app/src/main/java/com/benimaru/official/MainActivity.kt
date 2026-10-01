package com.benimaru.official

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.cardview.widget.CardView
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.materialswitch.MaterialSwitch
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.IUnityAdsLoadListener
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.UnityAds
import com.unity3d.services.banners.BannerView
import com.unity3d.services.banners.UnityBannerSize
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
import android.provider.Settings.Secure
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.button.MaterialButton

class MainActivity : AppCompatActivity() {

    private val SHIZUKU_REQUEST_CODE = 100
    private var isCrosshairEnabled = false

    // Unity Ads IDs
    private val unityGameId = "5781189"
    private val adUnitInterstitial = "Interstitial_Android"
    private val adUnitBanner = "Banner_Android"
    private val adUnitRewarded = "Rewarded_Android" // Added Rewarded Ad Unit
    private val testMode = false

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
        val prefs = getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        val currentNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !currentNightMode
            isAppearanceLightNavigationBars = !currentNightMode
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val btnThemeToggle = findViewById<ImageView>(R.id.btnThemeToggle)

        if (currentNightMode) {
            btnThemeToggle.setImageResource(R.drawable.ic_light_mode)
        } else {
            btnThemeToggle.setImageResource(R.drawable.ic_dark_mode)
        }

        btnThemeToggle.setOnClickListener {
            if (currentNightMode) {
                prefs.edit().putBoolean("isDarkMode", false).apply()
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            } else {
                prefs.edit().putBoolean("isDarkMode", true).apply()
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }
        }

        updatePremiumButtonUI() // Check and update button color on startup
        initializeUnityAds()

        verifyAppSignature()
        checkForUpdates()
        updateDeviceInfo()

        Shizuku.addRequestPermissionResultListener(permissionListener)
        checkShizukuStatus()
        setupClickListeners()
    }

    // --- Premium Logic ---
    private fun isPremium(): Boolean {
        return getSharedPreferences("BenimaruPrefs", Context.MODE_PRIVATE).getBoolean("AdsRemoved", false)
    }

    private fun updatePremiumButtonUI() {
        if (isPremium()) {
            val btnPremium = findViewById<Button>(R.id.btnRemoveAds)
            btnPremium.text = "✔ Premium"
            btnPremium.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FFD700")) // Gold
            btnPremium.setTextColor(Color.parseColor("#000000")) // Black text for contrast

            // Re-assign click listener to tell them they are already premium
            btnPremium.setOnClickListener {
                Toasty.success(this, "You are a Premium user!", Toast.LENGTH_SHORT, true).show()
            }
        }
    }

    // Helper method to enforce Premium or Rewarded Ad check
    private fun handlePremiumFeature(action: () -> Unit) {
        if (isPremium()) {
            action()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Premium Feature Locked")
            .setMessage("This feature requires Premium access.\n\nYou can temporarily unlock it right now by watching a short video ad, or permanently unlock all features by purchasing a Premium Login.")
            .setPositiveButton("Watch Ad") { _, _ ->
                showRewardedAd { action() }
            }
            .setNeutralButton("Buy Premium") { _, _ ->
                showRemoveAdsDialog()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // --- Unity Ads Implementation ---
    private fun initializeUnityAds() {
        if (isPremium()) return // Don't init if removed

        UnityAds.initialize(this, unityGameId, testMode, object : IUnityAdsInitializationListener {
            override fun onInitializationComplete() {
                loadInterstitialAd()
                loadRewardedAd()
                setupBannerAd()
            }
            override fun onInitializationFailed(error: UnityAds.UnityAdsInitializationError?, message: String?) {}
        })
    }

    private fun loadInterstitialAd() {
        if (isPremium()) return
        UnityAds.load(adUnitInterstitial, object : IUnityAdsLoadListener {
            override fun onUnityAdsAdLoaded(placementId: String) {}
            override fun onUnityAdsFailedToLoad(placementId: String, error: UnityAds.UnityAdsLoadError, message: String) {}
        })
    }

    private fun loadRewardedAd() {
        if (isPremium()) return
        UnityAds.load(adUnitRewarded, object : IUnityAdsLoadListener {
            override fun onUnityAdsAdLoaded(placementId: String) {}
            override fun onUnityAdsFailedToLoad(placementId: String, error: UnityAds.UnityAdsLoadError, message: String) {}
        })
    }

    private fun showInterstitialAd() {
        if (isPremium()) return
        UnityAds.show(this, adUnitInterstitial, object : IUnityAdsShowListener {
            override fun onUnityAdsShowFailure(placementId: String, error: UnityAds.UnityAdsShowError, message: String) {
                loadInterstitialAd()
            }
            override fun onUnityAdsShowStart(placementId: String) {}
            override fun onUnityAdsShowClick(placementId: String) {}
            override fun onUnityAdsShowComplete(placementId: String, state: UnityAds.UnityAdsShowCompletionState) {
                loadInterstitialAd()
            }
        })
    }

    private fun showRewardedAd(onSuccess: () -> Unit) {
        if (isPremium()) {
            onSuccess()
            return
        }

        Toasty.info(this, "Loading Ad...", Toast.LENGTH_SHORT, true).show()

        UnityAds.show(this, adUnitRewarded, object : IUnityAdsShowListener {
            override fun onUnityAdsShowFailure(placementId: String, error: UnityAds.UnityAdsShowError, message: String) {
                Toasty.error(this@MainActivity, "Ad failed to load. Please try again later.", Toast.LENGTH_SHORT, true).show()
                loadRewardedAd()
            }
            override fun onUnityAdsShowStart(placementId: String) {}
            override fun onUnityAdsShowClick(placementId: String) {}
            override fun onUnityAdsShowComplete(placementId: String, state: UnityAds.UnityAdsShowCompletionState) {
                if (state == UnityAds.UnityAdsShowCompletionState.COMPLETED) {
                    Toasty.success(this@MainActivity, "Unlocked successfully!", Toast.LENGTH_SHORT, true).show()
                    onSuccess()
                } else {
                    Toasty.warning(this@MainActivity, "Ad skipped. Feature remains locked.", Toast.LENGTH_LONG, true).show()
                }
                loadRewardedAd()
            }
        })
    }

    private fun setupBannerAd() {
        val bannerContainer = findViewById<LinearLayout>(R.id.bannerAdContainer)

        if (isPremium()) {
            bannerContainer.removeAllViews() // Ensure it's clear
            return
        }

        val bannerView = BannerView(this, adUnitBanner, UnityBannerSize(320, 50))
        bannerContainer.addView(bannerView)
        bannerView.load()
    }
    // --------------------------------

    override fun onResume() {
        super.onResume()
        updateDeviceInfo()
        updatePremiumButtonUI()
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(permissionListener)
    }

    // --- Device Info Dashboard ---
    private fun updateDeviceInfo() {
        try {
            val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
            val androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
            val apiLevel = "API ${Build.VERSION.SDK_INT}"

            val actManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            actManager.getMemoryInfo(memInfo)
            val totalRamGb = memInfo.totalMem.toDouble() / (1024 * 1024 * 1024)
            val availRamGb = memInfo.availMem.toDouble() / (1024 * 1024 * 1024)
            val usedRamGb = totalRamGb - availRamGb

            val stat = StatFs(Environment.getDataDirectory().path)
            val totalStorageGb = stat.totalBytes.toDouble() / (1024 * 1024 * 1024)
            val availStorageGb = stat.availableBytes.toDouble() / (1024 * 1024 * 1024)
            val usedStorageGb = totalStorageGb - availStorageGb

            val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val batLevel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

            val metrics = resources.displayMetrics
            val currentRes = "${metrics.widthPixels}x${metrics.heightPixels}"

            findViewById<TextView>(R.id.tvDeviceModel).text = deviceName.uppercase()
            findViewById<TextView>(R.id.tvAndroidVersion).text = androidVersion
            findViewById<TextView>(R.id.tvRamUsage).text = String.format("%.1f/%.1f GB", usedRamGb, totalRamGb)
            findViewById<TextView>(R.id.tvStorageUsage).text = String.format("%.1f/%.1f GB", usedStorageGb, totalStorageGb)
            findViewById<TextView>(R.id.tvBattery).text = "$batLevel%"
            findViewById<TextView>(R.id.tvApiLevel).text = apiLevel
            findViewById<TextView>(R.id.tvResolutionDisplay).text = currentRes
            findViewById<TextView>(R.id.tvFreeStorageDisplay).text = String.format("%.1f GB", availStorageGb)
        } catch (e: Exception) {
            android.util.Log.e("BenimaruTool", "Error in updateDeviceInfo", e)
        }
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
                android.util.Log.e("BenimaruTool", "Failed to check for updates", e)
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
            val prefs = getSharedPreferences("BenimaruPrefs", Context.MODE_PRIVATE)
            val isFixedPerf = prefs.getBoolean("FixedPerf", false)
            val fixedPerfStatus = if (isFixedPerf) "Status: Enabled" else "Status: Default"

            val isThermalDisabled = prefs.getBoolean("ThermalDisabled", false)
            val thermalStatus = if (isThermalDisabled) "Status: Throttling Disabled" else "Status: Default"

            val lastGameMode = prefs.getString("LastGameMode", "")
            val gameModeStatus = if (lastGameMode.isNullOrEmpty()) "Status: Default" else "Status: Active ($lastGameMode)"

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

            val animScale = runAdbCommandWithResult("settings get global window_animation_scale")
            val animStatus = if (animScale == "0.5") "Status: 0.5x (Fast)" else "Status: Default"

            val blursOpt = runAdbCommandWithResult("settings get global disable_window_blurs")
            val blursStatus = if (blursOpt == "1") "Status: Disabled (Optimized)" else "Status: Default"

            val headsUpOpt = runAdbCommandWithResult("settings get global heads_up_notifications_enabled")
            val headsUpStatus = if (headsUpOpt == "0") "Status: Blocked (Focus Mode)" else "Status: Default"

            withContext(Dispatchers.Main) {
                findViewById<TextView>(R.id.tvStatusFixedPerf).text = fixedPerfStatus
                findViewById<TextView>(R.id.tvStatusThermal).text = thermalStatus
                findViewById<TextView>(R.id.tvStatusGameMode).text = gameModeStatus
                findViewById<TextView>(R.id.tvStatusRefresh).text = refreshStatus
                findViewById<TextView>(R.id.tvStatusResolution).text = resStatus
                findViewById<TextView>(R.id.tvStatusNetwork).text = netStatus
                findViewById<TextView>(R.id.tvStatusTouch).text = touchStatus
                findViewById<TextView>(R.id.tvStatusDns).text = dnsStatus
                findViewById<TextView>(R.id.tvStatusAnimations).text = animStatus
                findViewById<TextView>(R.id.tvStatusBlurs).text = blursStatus
                findViewById<TextView>(R.id.tvStatusDnd).text = headsUpStatus

                // Restore Crosshair State
                val isCrosshairOn = prefs.getBoolean("CrosshairEnabled", false)
                isCrosshairEnabled = isCrosshairOn // Sync the global variable
                if (isCrosshairOn) {
                    val style = prefs.getString("CrosshairStyle", "Cross")
                    val color = prefs.getString("CrosshairColor", "Red")
                    val size = prefs.getString("CrosshairSize", "Medium")
                    findViewById<TextView>(R.id.tvStatusCrosshair).text = "Status: $style ($color, $size)"
                } else {
                    findViewById<TextView>(R.id.tvStatusCrosshair).text = "Status: Disabled"
                }
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
        val prefs = getSharedPreferences("BenimaruPrefs", Context.MODE_PRIVATE)

        // --- Free Features ---
        findViewById<CardView>(R.id.cardFixedPerformance).setOnClickListener {
            val status = findViewById<TextView>(R.id.tvStatusFixedPerf).text.toString()
            if (status.contains("Enabled")) {
                Toasty.info(this, "Already enabled!", Toast.LENGTH_SHORT, true).show()
                return@setOnClickListener
            }
            runAdbCommand("cmd power set-fixed-performance-mode-enabled true", "Fixed Performance Enabled", showAd = true) {
                prefs.edit().putBoolean("FixedPerf", true).apply()
                findViewById<TextView>(R.id.tvStatusFixedPerf).text = "Status: Enabled"
            }
        }

        findViewById<CardView>(R.id.cardOptimizeSystem).setOnClickListener {
            val status = findViewById<TextView>(R.id.tvStatusOptimize).text.toString()
            if (status.contains("Recently")) {
                Toasty.info(this, "Already optimized!", Toast.LENGTH_SHORT, true).show()
                return@setOnClickListener
            }
            runAdbCommand("cmd activity kill-all", "System Optimized (Background processes cleared)", showAd = true) {
                findViewById<TextView>(R.id.tvStatusOptimize).text = "Status: Recently Optimized"
            }
        }

        findViewById<CardView>(R.id.cardImproveNetwork).setOnClickListener {
            val status = findViewById<TextView>(R.id.tvStatusNetwork).text.toString()
            if (status.contains("Optimized")) {
                Toasty.info(this, "Already optimized!", Toast.LENGTH_SHORT, true).show()
                return@setOnClickListener
            }
            runAdbCommand("settings put global tether_dun_required 0", "Network Optimized", showAd = true) {
                fetchSystemStatuses()
            }
        }

        findViewById<CardView>(R.id.cardImproveTouch).setOnClickListener {
            val status = findViewById<TextView>(R.id.tvStatusTouch).text.toString()
            if (status.contains("Improved")) {
                Toasty.info(this, "Already improved!", Toast.LENGTH_SHORT, true).show()
                return@setOnClickListener
            }
            runAdbCommand("settings put system pointer_speed 7 && settings put secure long_press_timeout 250", "Touch Latency Improved", showAd = true) {
                fetchSystemStatuses()
            }
        }

        findViewById<CardView>(R.id.cardFastAnimations).setOnClickListener {
            val status = findViewById<TextView>(R.id.tvStatusAnimations).text.toString()
            if (status.contains("0.5x")) {
                Toasty.info(this, "Already sped up!", Toast.LENGTH_SHORT, true).show()
                return@setOnClickListener
            }
            runAdbCommand("settings put global window_animation_scale 0.5 && settings put global transition_animation_scale 0.5 && settings put global animator_duration_scale 0.5", "Animations sped up to 0.5x", showAd = true) {
                fetchSystemStatuses()
            }
        }

        findViewById<CardView>(R.id.cardDisableBlurs).setOnClickListener {
            val status = findViewById<TextView>(R.id.tvStatusBlurs).text.toString()
            if (status.contains("Disabled")) {
                Toasty.info(this, "Already disabled!", Toast.LENGTH_SHORT, true).show()
                return@setOnClickListener
            }
            runAdbCommand("settings put global disable_window_blurs 1", "Window blurs disabled", showAd = true) {
                fetchSystemStatuses()
            }
        }

        findViewById<CardView>(R.id.cardGamingDnd).setOnClickListener {
            val status = findViewById<TextView>(R.id.tvStatusDnd).text.toString()
            if (status.contains("Blocked")) {
                Toasty.info(this, "Already blocked!", Toast.LENGTH_SHORT, true).show()
                return@setOnClickListener
            }
            runAdbCommand("settings put global heads_up_notifications_enabled 0", "Heads-up notifications blocked (Focus Mode)", showAd = true) {
                fetchSystemStatuses()
            }
        }

        findViewById<CardView>(R.id.cardAdjustRefreshRate).setOnClickListener { showRefreshRateDialog() }

        // --- Premium Features with Rewarded Ad Alternative ---
        findViewById<CardView>(R.id.cardChangeResolution).setOnClickListener {
            handlePremiumFeature { showResolutionModeDialog() }
        }

        findViewById<CardView>(R.id.cardCustomDns).setOnClickListener {
            handlePremiumFeature { showDnsDialog() }
        }

        findViewById<CardView>(R.id.cardCrosshair).setOnClickListener {
            handlePremiumFeature { showCrosshairConfigDialog() }
        }

        findViewById<CardView>(R.id.cardThermalThrottling).setOnClickListener {
            handlePremiumFeature {
                MaterialAlertDialogBuilder(this)
                    .setTitle("Warning: High Temperatures")
                    .setMessage("Disabling Thermal Throttling tricks the device into thinking it is cool, forcing maximum performance. Your device WILL get hot. Use with caution.\n\nProceed?")
                    .setPositiveButton("Enable") { _, _ ->
                        runAdbCommand("cmd thermalservice override-status 0", "Thermal Throttling Disabled") {
                            prefs.edit().putBoolean("ThermalDisabled", true).apply()
                            fetchSystemStatuses()
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }

        findViewById<CardView>(R.id.cardGameMode).setOnClickListener {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) { // S is Android 12
                Toasty.error(this, "This feature requires Android 12 or newer.", Toast.LENGTH_LONG, true).show()
                return@setOnClickListener
            }

            handlePremiumFeature { showGameModeSelectorDialog() }
        }

        // --- Individual Reset Button Clicks ---
        findViewById<TextView>(R.id.btnResetFixedPerf).setOnClickListener {
            runAdbCommand("cmd power set-fixed-performance-mode-enabled false", "Fixed Performance Reset") {
                prefs.edit().putBoolean("FixedPerf", false).apply()
                findViewById<TextView>(R.id.tvStatusFixedPerf).text = "Status: Default"
            }
        }
        findViewById<TextView>(R.id.btnResetThermal).setOnClickListener {
            runAdbCommand("cmd thermalservice reset", "Thermal limits restored") {
                prefs.edit().putBoolean("ThermalDisabled", false).apply()
                fetchSystemStatuses()
            }
        }
        findViewById<TextView>(R.id.btnResetGameMode).setOnClickListener {
            val lastGame = prefs.getString("LastGameMode", "")
            if (!lastGame.isNullOrEmpty()) {
                runAdbCommand("cmd game mode standard $lastGame", "Game Mode Reset") {
                    prefs.edit().putString("LastGameMode", "").apply()
                    fetchSystemStatuses()
                }
            } else {
                Toasty.info(this, "No active Game Mode found", Toast.LENGTH_SHORT, true).show()
            }
        }
        findViewById<TextView>(R.id.btnResetOptimize).setOnClickListener {
            findViewById<TextView>(R.id.tvStatusOptimize).text = "Status: Ready"
            Toasty.success(this, "Status reset", Toast.LENGTH_SHORT, true).show()
        }
        findViewById<TextView>(R.id.btnResetRefresh).setOnClickListener {
            runAdbCommand("settings delete system min_refresh_rate && settings delete system peak_refresh_rate", "Refresh Rate Reset") { fetchSystemStatuses() }
        }
        findViewById<TextView>(R.id.btnResetNetwork).setOnClickListener {
            runAdbCommand("settings delete global tether_dun_required", "Network Reset") { fetchSystemStatuses() }
        }
        findViewById<TextView>(R.id.btnResetTouch).setOnClickListener {
            runAdbCommand("settings delete system pointer_speed && settings put secure long_press_timeout 400", "Touch Settings Reset") { fetchSystemStatuses() }
        }
        findViewById<TextView>(R.id.btnResetResolution).setOnClickListener {
            runAdbCommand("wm size reset && wm density reset", "Resolution Reset") { fetchSystemStatuses() }
        }
        findViewById<TextView>(R.id.btnResetDns).setOnClickListener {
            runAdbCommand("settings put global private_dns_mode default && settings delete global private_dns_specifier", "DNS Reset") { fetchSystemStatuses() }
        }
        findViewById<TextView>(R.id.btnResetCrosshair).setOnClickListener {
            if (isCrosshairEnabled) stopCrosshairService()
        }
        findViewById<TextView>(R.id.btnResetAnimations).setOnClickListener {
            runAdbCommand("settings put global window_animation_scale 1 && settings put global transition_animation_scale 1 && settings put global animator_duration_scale 1", "Animations Reset") { fetchSystemStatuses() }
        }
        findViewById<TextView>(R.id.btnResetBlurs).setOnClickListener {
            runAdbCommand("settings put global disable_window_blurs 0", "Window Blurs Reset") { fetchSystemStatuses() }
        }
        findViewById<TextView>(R.id.btnResetDnd).setOnClickListener {
            runAdbCommand("settings put global heads_up_notifications_enabled 1", "Gaming Focus Mode Reset") { fetchSystemStatuses() }
        }

        // --- Global Buttons ---
        findViewById<Button>(R.id.btnLaunchGame).setOnClickListener {
            showGameLauncherDialog()
        }

        findViewById<Button>(R.id.btnRemoveAds).setOnClickListener {
            if (isPremium()) {
                Toasty.success(this, "You are a Premium user!", Toast.LENGTH_SHORT, true).show()
            } else {
                showRemoveAdsDialog()
            }
        }

        findViewById<Button>(R.id.btnResetAll).setOnClickListener {
            val lastGame = prefs.getString("LastGameMode", "")
            val gameModeReset = if (lastGame.isNullOrEmpty()) "" else "cmd game mode standard $lastGame && "

            val resetCmd = """
                cmd power set-fixed-performance-mode-enabled false
                cmd thermalservice reset
                $gameModeReset
                settings delete system min_refresh_rate
                settings delete system peak_refresh_rate
                wm size reset
                wm density reset
                settings delete system pointer_speed
                settings put secure long_press_timeout 400
                settings put global private_dns_mode default
                settings delete global private_dns_specifier
                settings put global window_animation_scale 1
                settings put global transition_animation_scale 1
                settings put global animator_duration_scale 1
                settings put global disable_window_blurs 0
                settings put global heads_up_notifications_enabled 1
            """.trimIndent().replace("\n", " && ").replace("&&  &&", "&&")

            runAdbCommand(resetCmd, "All functions reset to default") {
                prefs.edit()
                    .putBoolean("FixedPerf", false)
                    .putBoolean("ThermalDisabled", false)
                    .putString("LastGameMode", "")
                    .apply()
                findViewById<TextView>(R.id.tvStatusFixedPerf).text = "Status: Default"
                findViewById<TextView>(R.id.tvStatusOptimize).text = "Status: Ready"
                fetchSystemStatuses()
            }
        }
    }

    private fun showResolutionModeDialog() {
        val options = arrayOf("Direct Change (In-App)", "Floating Menu (Overlay)")

        MaterialAlertDialogBuilder(this)
            .setTitle("Change Resolution")
            .setItems(options) { _, which ->
                if (which == 0) {
                    showResolutionDialog()
                } else if (which == 1) {
                    launchFloatingResolution()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun launchFloatingResolution() {
        if (!android.provider.Settings.canDrawOverlays(this)) {
            val intent = Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
            Toasty.info(this, "Please allow 'Display over other apps'", Toast.LENGTH_LONG, true).show()
        } else {
            startService(Intent(this, FloatingResolutionService::class.java))
            Toasty.success(this, "Floating menu enabled", Toast.LENGTH_SHORT, true).show()
        }
    }

    private fun showRemoveAdsDialog() {
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)

        val scrollContainer = android.widget.ScrollView(this)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 48, 64, 32)
        }

        // Material 3 Outlined Username Input Box
        val usernameLayout = TextInputLayout(this).apply {
            hint = "Username"
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            setBoxCornerRadii(24f, 24f, 24f, 24f) // Smooth rounded corners
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val usernameInput = TextInputEditText(usernameLayout.context).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            maxLines = 1
        }
        usernameLayout.addView(usernameInput)

        // Material 3 Outlined Password Input Box with Toggle Eye
        val passwordLayout = TextInputLayout(this).apply {
            hint = "Password"
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            setBoxCornerRadii(24f, 24f, 24f, 24f)
            endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE // Adds the eye icon
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 32 }
        }
        val passwordInput = TextInputEditText(passwordLayout.context).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            maxLines = 1
        }
        passwordLayout.addView(passwordInput)

        // Device ID Display
        val tvDeviceId = TextView(this).apply {
            text = "Your Device ID: $deviceId"
            textSize = 13f
            setTextColor(Color.GRAY)
            gravity = android.view.Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 48
                bottomMargin = 16
            }
        }

        // Material 3 Outlined Style Button
        val copyIdButton = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Copy Device ID"
            cornerRadius = 50 // Pill shape
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8 }
            setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Device ID", deviceId)
                clipboard.setPrimaryClip(clip)
                Toasty.success(this@MainActivity, "Device ID Copied!", Toast.LENGTH_SHORT, true).show()
            }
        }

        // Material 3 Filled Style Button
        val buyPremiumButton = MaterialButton(this).apply {
            text = "Buy Premium via PayPal"
            cornerRadius = 50 // Pill shape
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 8 }

            setOnClickListener {
                val user = usernameInput.text.toString().trim()
                val pass = passwordInput.text.toString().trim()

                if (user.isEmpty() || pass.isEmpty()) {
                    Toasty.warning(this@MainActivity, "Please enter your desired Username and Password first!", Toast.LENGTH_LONG, true).show()
                    return@setOnClickListener
                }

                val encodedUser = Uri.encode(user)
                val encodedPass = Uri.encode(pass)
                val encodedDevice = Uri.encode(deviceId)

                val paypalEmail = "vestalkimqq02@gmail.com"
                val price = "5.00"

                val paymentUrl = "https://www.paypal.com/cgi-bin/webscr" +
                        "?cmd=_xclick" +
                        "&business=$paypalEmail" +
                        "&item_name=Benimaru+Premium+Unlock" +
                        "&amount=$price" +
                        "&currency_code=USD" +
                        "&on0=Username&os0=$encodedUser" +
                        "&on1=Device+ID&os1=$encodedDevice" +
                        "&on2=Password&os2=$encodedPass" +
                        "&custom=$encodedUser|$encodedPass|$encodedDevice"

                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(paymentUrl))
                startActivity(intent)
            }
        }

        layout.addView(usernameLayout)
        layout.addView(passwordLayout)
        layout.addView(tvDeviceId)
        layout.addView(copyIdButton)
        layout.addView(buyPremiumButton)

        scrollContainer.addView(layout)

        MaterialAlertDialogBuilder(this)
            .setTitle("Premium Login")
            .setMessage("Login to permanently unlock all Premium Cards and remove ads.")
            .setView(scrollContainer)
            .setPositiveButton("Login") { _, _ ->
                val user = usernameInput.text.toString().trim()
                val pass = passwordInput.text.toString().trim()
                if (user.isNotEmpty() && pass.isNotEmpty()) {
                    verifyPremiumAccount(user, pass)
                } else {
                    Toasty.warning(this, "Please fill in all fields", Toast.LENGTH_SHORT, true).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun verifyPremiumAccount(username: String, pass: String) {
        val deviceId = Secure.getString(contentResolver, Secure.ANDROID_ID)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Verifying...")
            .setMessage("Checking account on server...")
            .setCancelable(false)
            .show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val url = URL("https://raw.githubusercontent.com/Benimaru-x1k/Benimaru/main/users.json")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000

                if (conn.responseCode == 200) {
                    val jsonString = conn.inputStream.bufferedReader().readText()
                    val jsonObject = JSONObject(jsonString)
                    val usersArray = jsonObject.getJSONArray("users")

                    var isValid = false
                    var reason = "Invalid username or password."

                    for (i in 0 until usersArray.length()) {
                        val userObj = usersArray.getJSONObject(i)
                        if (userObj.getString("username") == username && userObj.getString("password") == pass) {
                            val devices = userObj.getJSONArray("devices")
                            if (devices.length() > 2) {
                                reason = "Account limits exceeded (Max 2 devices allowed)."
                            } else {
                                var deviceFound = false
                                for (j in 0 until devices.length()) {
                                    if (devices.getString(j) == deviceId) {
                                        deviceFound = true
                                        break
                                    }
                                }
                                if (deviceFound) {
                                    isValid = true
                                } else {
                                    reason = "Device not authorized. Send this ID to admin: $deviceId"
                                }
                            }
                            break
                        }
                    }

                    withContext(Dispatchers.Main) {
                        dialog.dismiss()
                        if (isValid) {
                            val prefs = getSharedPreferences("BenimaruPrefs", Context.MODE_PRIVATE)
                            prefs.edit().putBoolean("AdsRemoved", true).apply()
                            findViewById<LinearLayout>(R.id.bannerAdContainer).removeAllViews()
                            updatePremiumButtonUI()
                            Toasty.success(this@MainActivity, "Premium Activated! Ads Removed & Features Unlocked.", Toast.LENGTH_LONG, true).show()
                        } else {
                            Toasty.error(this@MainActivity, reason, Toast.LENGTH_LONG, true).show()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        dialog.dismiss()
                        Toasty.error(this@MainActivity, "Server Error: ${conn.responseCode}", Toast.LENGTH_SHORT, true).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    dialog.dismiss()
                    Toasty.error(this@MainActivity, "Network Error. Please check connection.", Toast.LENGTH_SHORT, true).show()
                }
            }
        }
    }

    private fun runAdbCommand(command: String, successMessage: String, showAd: Boolean = false, onSuccess: () -> Unit = {}) {
        if (!hasShizukuPermission()) return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
                val exitCode = process.waitFor()
                withContext(Dispatchers.Main) {
                    if (exitCode == 0) {
                        Toasty.success(this@MainActivity, successMessage, Toast.LENGTH_SHORT, true).show()
                        onSuccess()
                        if (showAd) showInterstitialAd()
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
                    runAdbCommand(cmd, "DNS reset to Default", showAd = false) { fetchSystemStatuses() }
                } else {
                    val cmd = "settings put global private_dns_mode hostname && settings put global private_dns_specifier $selectedHostname"
                    runAdbCommand(cmd, "DNS set to $selectedHostname", showAd = true) { fetchSystemStatuses() }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRefreshRateDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 48, 64, 32)
        }

        val minLayout = TextInputLayout(this).apply {
            hint = "Min Refresh Rate (e.g., 60)"
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            setBoxCornerRadii(24f, 24f, 24f, 24f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val minInput = TextInputEditText(minLayout.context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            maxLines = 1
        }
        minLayout.addView(minInput)

        val maxLayout = TextInputLayout(this).apply {
            hint = "Max Refresh Rate (e.g., 120)"
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            setBoxCornerRadii(24f, 24f, 24f, 24f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 32 }
        }
        val maxInput = TextInputEditText(maxLayout.context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            maxLines = 1
        }
        maxLayout.addView(maxInput)

        layout.addView(minLayout)
        layout.addView(maxLayout)

        MaterialAlertDialogBuilder(this)
            .setTitle("Adjust Refresh Rate")
            .setView(layout)
            .setPositiveButton("Apply") { _, _ ->
                val min = minInput.text.toString().trim()
                val max = maxInput.text.toString().trim()
                if (min.isNotEmpty() && max.isNotEmpty()) {
                    val cmd = "settings put system min_refresh_rate $min && settings put system peak_refresh_rate $max"
                    runAdbCommand(cmd, "Refresh rate set to $min - $max Hz", showAd = true) {
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

        val currentDpi = metrics.densityDpi
        val nativePortraitWidth = minOf(metrics.widthPixels, metrics.heightPixels).toFloat()
        val nativePortraitHeight = maxOf(metrics.widthPixels, metrics.heightPixels).toFloat()

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 48, 64, 32)
        }

        val autoMatchSwitch = MaterialSwitch(this).apply {
            text = "Auto-match aspect ratio"
            isChecked = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 32 }
        }
        layout.addView(autoMatchSwitch)

        val widthLayout = TextInputLayout(this).apply {
            hint = "Width (Current: ${nativePortraitWidth.toInt()})"
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            setBoxCornerRadii(24f, 24f, 24f, 24f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val widthInput = TextInputEditText(widthLayout.context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            maxLines = 1
        }
        widthLayout.addView(widthInput)

        val heightLayout = TextInputLayout(this).apply {
            hint = "Height (Current: ${nativePortraitHeight.toInt()})"
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            setBoxCornerRadii(24f, 24f, 24f, 24f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 32 }
        }
        val heightInput = TextInputEditText(heightLayout.context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            maxLines = 1
        }
        heightLayout.addView(heightInput)

        layout.addView(widthLayout)
        layout.addView(heightLayout)

        var isAutoCalculating = false

        val widthWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (autoMatchSwitch.isChecked && !isAutoCalculating) {
                    val wStr = s.toString()
                    isAutoCalculating = true
                    if (wStr.isNotEmpty()) {
                        val w = wStr.toIntOrNull()
                        if (w != null) {
                            val calculatedHeight = (w * (nativePortraitHeight / nativePortraitWidth)).toInt()
                            heightInput.setText(calculatedHeight.toString())
                        }
                    } else {
                        heightInput.setText("")
                    }
                    isAutoCalculating = false
                }
            }
        }

        val heightWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (autoMatchSwitch.isChecked && !isAutoCalculating) {
                    val hStr = s.toString()
                    isAutoCalculating = true
                    if (hStr.isNotEmpty()) {
                        val h = hStr.toIntOrNull()
                        if (h != null) {
                            val calculatedWidth = (h * (nativePortraitWidth / nativePortraitHeight)).toInt()
                            widthInput.setText(calculatedWidth.toString())
                        }
                    } else {
                        widthInput.setText("")
                    }
                    isAutoCalculating = false
                }
            }
        }

        widthInput.addTextChangedListener(widthWatcher)
        heightInput.addTextChangedListener(heightWatcher)

        autoMatchSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !isAutoCalculating) {
                if (heightInput.text?.isNotEmpty() == true) {
                    val h = heightInput.text.toString().toIntOrNull()
                    if (h != null) {
                        isAutoCalculating = true
                        val w = (h * (nativePortraitWidth / nativePortraitHeight)).toInt()
                        widthInput.setText(w.toString())
                        isAutoCalculating = false
                    }
                } else if (widthInput.text?.isNotEmpty() == true) {
                    val w = widthInput.text.toString().toIntOrNull()
                    if (w != null) {
                        isAutoCalculating = true
                        val h = (w * (nativePortraitHeight / nativePortraitWidth)).toInt()
                        heightInput.setText(h.toString())
                        isAutoCalculating = false
                    }
                }
            }
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Change Resolution")
            .setMessage("DPI will be calculated automatically.")
            .setView(layout)
            .setPositiveButton("Preview") { _, _ ->
                val wStr = widthInput.text.toString().trim()
                val hStr = heightInput.text.toString().trim()

                if (wStr.isNotEmpty() && hStr.isNotEmpty()) {
                    if (!hasShizukuPermission()) return@setPositiveButton

                    val input1 = wStr.toInt()
                    val input2 = hStr.toInt()

                    val newWidth = minOf(input1, input2)
                    val newHeight = maxOf(input1, input2)

                    val widthRatio = newWidth.toFloat() / nativePortraitWidth
                    val heightRatio = newHeight.toFloat() / nativePortraitHeight
                    val scalingRatio = minOf(widthRatio, heightRatio)

                    val newDpi = (scalingRatio * currentDpi).toInt()

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
                showInterstitialAd() // Triggers Ad ONLY on Save
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

    private fun showCrosshairConfigDialog() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
            Toasty.info(this, "Please allow 'Display over other apps' to use the crosshair", Toast.LENGTH_LONG, true).show()
            return
        }

        val prefs = getSharedPreferences("BenimaruPrefs", Context.MODE_PRIVATE)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 32, 64, 32)
        }

        val styles = arrayOf("Cross", "Dot", "Circle", "Cross with Circle", "Square", "Target")
        val colors = arrayOf("White", "Black", "Red", "Green", "Blue", "Yellow", "Cyan", "Magenta")
        val sizes = arrayOf("Tiny", "Small", "Medium", "Large", "Extra Large")

        fun updateLive(sIdx: Int, cIdx: Int, szIdx: Int) {
            if (isCrosshairEnabled) {
                startCrosshairService(styles[sIdx], colors[cIdx], sizes[szIdx])
            }
        }

        var currentStyleIdx = prefs.getInt("CrosshairStyleIdx", 0)
        var currentColorIdx = prefs.getInt("CrosshairColorIdx", 2)
        var currentSizeIdx = prefs.getInt("CrosshairSizeIdx", 2)

        fun createSlider(title: String, options: Array<String>, defaultIdx: Int, onProgress: (Int) -> Unit): android.widget.SeekBar {
            val label = TextView(this).apply {
                text = "$title: ${options[defaultIdx]}"
                setPadding(0, 24, 0, 8)
                textSize = 16f
                setTextColor(Color.parseColor("#808080"))
            }
            val seekBar = android.widget.SeekBar(this).apply {
                max = options.size - 1
                progress = defaultIdx
                setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                        label.text = "$title: ${options[progress]}"
                        if (fromUser) {
                            onProgress(progress)
                            updateLive(currentStyleIdx, currentColorIdx, currentSizeIdx)
                        }
                    }
                    override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
                })
            }
            layout.addView(label)
            layout.addView(seekBar)
            return seekBar
        }

        createSlider("Shape", styles, currentStyleIdx) { currentStyleIdx = it }
        createSlider("Color", colors, currentColorIdx) { currentColorIdx = it }
        createSlider("Size", sizes, currentSizeIdx) { currentSizeIdx = it }

        val isEnabled = prefs.getBoolean("CrosshairEnabled", false)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Customize Crosshair")
            .setView(layout)
            .setPositiveButton(if (isEnabled) "Save" else "Start") { _, _ ->
                prefs.edit()
                    .putInt("CrosshairStyleIdx", currentStyleIdx)
                    .putInt("CrosshairColorIdx", currentColorIdx)
                    .putInt("CrosshairSizeIdx", currentSizeIdx)
                    .apply()

                startCrosshairService(styles[currentStyleIdx], colors[currentColorIdx], sizes[currentSizeIdx])
            }
            .setNegativeButton("Cancel", null)

        if (isEnabled) {
            dialog.setNeutralButton("Turn Off") { _, _ -> stopCrosshairService() }
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

        getSharedPreferences("BenimaruPrefs", Context.MODE_PRIVATE).edit()
            .putBoolean("CrosshairEnabled", true)
            .putString("CrosshairStyle", style)
            .putString("CrosshairColor", color)
            .putString("CrosshairSize", size)
            .apply()

        findViewById<TextView>(R.id.tvStatusCrosshair).text = "Status: $style ($color, $size)"
        Toasty.success(this, "Crosshair Updated", Toast.LENGTH_SHORT, true).show()
        showInterstitialAd()
    }

    private fun stopCrosshairService() {
        val intent = Intent(this, CrosshairService::class.java)
        stopService(intent)

        isCrosshairEnabled = false

        getSharedPreferences("BenimaruPrefs", Context.MODE_PRIVATE).edit()
            .putBoolean("CrosshairEnabled", false)
            .apply()

        findViewById<TextView>(R.id.tvStatusCrosshair).text = "Status: Disabled"
        Toasty.success(this, "Crosshair disabled", Toast.LENGTH_SHORT, true).show()
    }

    private fun showGameModeSelectorDialog() {
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
            .setTitle("Select a game to optimize")
            .setAdapter(adapter) { _, which ->
                val selectedApp = gameApps[which]
                val pkgName = selectedApp.activityInfo.packageName
                val appName = selectedApp.loadLabel(pm).toString()

                runAdbCommand("cmd game mode performance $pkgName", "Performance Mode enabled for $appName", showAd = true) {
                    val prefs = getSharedPreferences("BenimaruPrefs", Context.MODE_PRIVATE)
                    prefs.edit().putString("LastGameMode", pkgName).apply()
                    fetchSystemStatuses()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
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
                val pkgName = selectedApp.activityInfo.packageName
                val appName = selectedApp.loadLabel(pm).toString()

                MaterialAlertDialogBuilder(this@MainActivity)
                    .setTitle(appName)
                    .setMessage("Would you like to pre-compile the game code to prevent in-game stutters, or launch immediately?\n\n(Optimization takes 10-30 seconds)")
                    .setPositiveButton("Launch") { _, _ ->
                        val launchIntent = pm.getLaunchIntentForPackage(pkgName)
                        if (launchIntent != null) {
                            startActivity(launchIntent)
                            Toasty.success(this@MainActivity, "Launching $appName", Toast.LENGTH_SHORT, true).show()
                        } else {
                            Toasty.error(this@MainActivity, "Failed to launch game", Toast.LENGTH_SHORT, true).show()
                        }
                    }
                    .setNeutralButton("Optimize") { _, _ ->
                        Toasty.info(this@MainActivity, "Optimizing $appName. Please wait...", Toast.LENGTH_LONG, true).show()
                        runAdbCommand("cmd package compile -m speed -f $pkgName", "$appName optimized successfully!", showAd = true)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
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