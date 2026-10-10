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
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
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
import com.google.android.material.progressindicator.CircularProgressIndicator
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
import com.google.android.material.chip.ChipGroup
import com.google.android.material.card.MaterialCardView
import androidx.core.widget.NestedScrollView
import androidx.core.content.res.ResourcesCompat
import com.google.android.material.chip.Chip
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import android.widget.HorizontalScrollView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.DrawableCompat


class MainActivity : AppCompatActivity() {

    private val SHIZUKU_REQUEST_CODE = 100
    private var pendingApk: File? = null
    private var isCrosshairEnabled = false
    private var isMonitorEnabled = false

    private val unityGameId = "5781189"
    private val adUnitInterstitial = "Interstitial_Android"
    private val adUnitBanner = "Banner_Android"
    private val adUnitRewarded = "Rewarded_Android"
    private val testMode = false

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == SHIZUKU_REQUEST_CODE) {
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                Toasty.success(this, "Shizuku permission granted! You can now apply settings.", Toast.LENGTH_SHORT, true).show()
                updateShizukuBannerUI(true)
                fetchSystemStatuses()
            } else {
                updateShizukuBannerUI(false)
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

        updatePremiumButtonUI()
        initializeUnityAds()

        verifyAppSignature()
        checkForUpdates()
        updateDeviceInfo()

        Shizuku.addRequestPermissionResultListener(permissionListener)
        checkShizukuStatus()
        setupClickListeners()
        setupFilterChips()
        setupBottomBarScrollBehavior()
        setupStatusStyling()
        refreshFreezeStatus()
    }


    private fun isPremium(): Boolean {
        return getSharedPreferences("BenimaruPrefs", Context.MODE_PRIVATE).getBoolean("AdsRemoved", false)
    }

    private fun updatePremiumButtonUI() {
        if (isPremium()) {
            val btnPremium = findViewById<Button>(R.id.btnRemoveAds)
            btnPremium.text = " Premium"
            btnPremium.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FFD700"))
            btnPremium.setTextColor(Color.parseColor("#000000"))
            (btnPremium as? MaterialButton)?.iconTint = ColorStateList.valueOf(Color.BLACK)

            btnPremium.setOnClickListener {
                Toasty.success(this, "You are a Premium user!", Toast.LENGTH_SHORT, true).show()
            }
        }
    }

    private fun handlePremiumFeature(action: () -> Unit) {
        if (isPremium()) {
            action()
            return
        }

        Sheet(this)
            .icon(R.drawable.ic_crown, Color.parseColor("#E0A800"))
            .title("Premium feature")
            .message("Unlock it once by watching a short video, or get Premium to unlock everything permanently and remove ads.")
            .button("Watch ad to unlock", SheetStyle.PRIMARY) { showRewardedAd { action() } }
            .button("Get Premium", SheetStyle.TONAL) { showRemoveAdsDialog() }
            .button("Not now", SheetStyle.TEXT)
            .show()
    }

    private fun initializeUnityAds() {
        if (isPremium()) return

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

    private var lastInterstitialAt = 0L
    private var interstitialEligibleActions = 0
    private val interstitialCooldownMs = 3 * 60 * 1000L
    private val interstitialActionsBeforeFirst = 3

    private fun shouldShowInterstitial(): Boolean {
        if (isPremium()) return false
        interstitialEligibleActions++
        if (interstitialEligibleActions < interstitialActionsBeforeFirst) return false
        val now = android.os.SystemClock.elapsedRealtime()
        return lastInterstitialAt == 0L || now - lastInterstitialAt >= interstitialCooldownMs
    }

    /**
     * Shows an interstitial if the frequency cap allows it. [onFinished] ALWAYS runs exactly once,
     * after the ad closes, fails, is skipped by the cap, or user is premium, so callers can safely
     * chain follow-up work (e.g. launching a game) without it racing the ad.
     */
    private fun showInterstitialAd(onFinished: () -> Unit = {}) {
        if (isFinishing || isDestroyed || !shouldShowInterstitial()) {
            onFinished()
            return
        }
        var done = false
        fun finish() {
            if (done) return
            done = true
            loadInterstitialAd()
            onFinished()
        }
        lastInterstitialAt = android.os.SystemClock.elapsedRealtime()
        UnityAds.show(this, adUnitInterstitial, object : IUnityAdsShowListener {
            override fun onUnityAdsShowFailure(placementId: String, error: UnityAds.UnityAdsShowError, message: String) {
                lastInterstitialAt = 0L
                finish()
            }
            override fun onUnityAdsShowStart(placementId: String) {}
            override fun onUnityAdsShowClick(placementId: String) {}
            override fun onUnityAdsShowComplete(placementId: String, state: UnityAds.UnityAdsShowCompletionState) {
                finish()
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
            bannerContainer.removeAllViews()
            return
        }

        val bannerView = BannerView(this, adUnitBanner, UnityBannerSize(320, 50))
        bannerContainer.addView(bannerView)
        bannerView.load()
    }

    override fun onResume() {
        super.onResume()
        updateDeviceInfo()
        updatePremiumButtonUI()
        refreshFreezeStatus()

        val apk = pendingApk
        if (apk != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && packageManager.canRequestPackageInstalls()) {
            pendingApk = null
            installApk(apk, false)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeRequestPermissionResultListener(permissionListener)
    }

    private fun updateDeviceInfo() {
        try {
            val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".replace("\n", "").trim()
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

            findViewById<com.google.android.material.progressindicator.LinearProgressIndicator>(R.id.pbRam)
                .setProgressCompat(((usedRamGb / totalRamGb) * 100).toInt().coerceIn(0, 100), true)
            findViewById<com.google.android.material.progressindicator.LinearProgressIndicator>(R.id.pbStorage)
                .setProgressCompat(((usedStorageGb / totalStorageGb) * 100).toInt().coerceIn(0, 100), true)
            findViewById<com.google.android.material.progressindicator.LinearProgressIndicator>(R.id.pbBattery)
                .setProgressCompat(batLevel.coerceIn(0, 100), true)
        } catch (e: Exception) {
            android.util.Log.e("BenimaruTool", "Error in updateDeviceInfo", e)
        }
    }

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

    private fun showUpdateDialog(versionName: String, releaseNotes: String, apkUrl: String, mandatory: Boolean = false) {
        if (isFinishing || isDestroyed) return
        val notes = releaseNotes.trim().ifEmpty { "Bug fixes and improvements." }

        val sheet = Sheet(this)
            .icon(R.drawable.ic_download)
            .title("Update available")
            .message("Version $versionName is ready to install.")
            .content(infoCard(this, "What's new\n$notes"))
            .cancelable(!mandatory)
        if (!mandatory) sheet.button("Later", SheetStyle.TONAL)
        sheet.button("Update now", SheetStyle.PRIMARY) { downloadAppUpdate(apkUrl) }
        sheet.show()
    }

    private fun downloadAppUpdate(downloadUrl: String) {
        downloadAndInstall("Downloading update", downloadUrl, "BenimaruTool-update.apk", finishAfterInstall = false)
    }

    private fun formatMb(bytes: Long): String = String.format("%.1f MB", bytes / (1024.0 * 1024.0))


    private fun downloadAndInstall(title: String, url: String, fileName: String, finishAfterInstall: Boolean) {
        val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (dir == null) {
            Toasty.error(this, "Storage is not available", Toast.LENGTH_SHORT, true).show()
            if (finishAfterInstall) showShizukuRequiredDialog()
            return
        }

        var job: Job? = null
        val progress = ProgressSheet(this)
            .icon(R.drawable.ic_download)
            .title(title)
            .message("Keep the app open until the download finishes.")
            .cancelButton("Cancel") { job?.cancel() }
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
                    throw java.io.IOException("Server returned HTTP ${c.responseCode}")
                }


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
                                        progress.update(pct, "$pct%  ·  ${formatMb(downloaded)} of ${formatMb(total)}")
                                    }
                                }
                            } else {
                                val mbNow = downloaded / (1024 * 1024)
                                if (mbNow != lastMb) {
                                    lastMb = mbNow
                                    withContext(Dispatchers.Main) {
                                        progress.setIndeterminate("${formatMb(downloaded)} downloaded")
                                    }
                                }
                            }
                        }
                        output.flush()
                    }
                }

                if (total > 0 && downloaded != total) throw java.io.IOException("Download was incomplete")
                if (target.exists()) target.delete()
                if (!part.renameTo(target)) throw java.io.IOException("Could not save the file")

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
                    Toasty.error(this@MainActivity, "Download failed: ${e.message}", Toast.LENGTH_LONG, true).show()
                    if (finishAfterInstall) showShizukuRequiredDialog()
                }
            } finally {
                conn?.disconnect()
            }
        }
    }

    private fun checkShizukuStatus() {
        if (isShizukuInstalled()) {
            if (Shizuku.pingBinder()) {
                if (hasShizukuPermission()) {
                    updateShizukuBannerUI(true)
                    fetchSystemStatuses()
                } else {
                    updateShizukuBannerUI(false)
                }
            } else {
                updateShizukuBannerUI(false)
                Toasty.warning(this, "Shizuku is installed but not running. Launching app...", Toast.LENGTH_LONG, true).show()
                launchShizukuApp()
            }
        } else {
            updateShizukuBannerUI(false)
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

            val devOpt = runAdbCommandWithResult("settings get global development_settings_enabled")
            val devOptStatus = if (devOpt == "1") "Status: Enabled (Unsafe for Banks)" else "Status: Disabled (Safe)"

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
                findViewById<TextView>(R.id.tvStatusDevOptions).text = devOptStatus

                val isCrosshairOn = prefs.getBoolean("CrosshairEnabled", false)
                isCrosshairEnabled = isCrosshairOn
                if (isCrosshairOn) {
                    val style = prefs.getString("CrosshairStyle", "Cross")
                    val color = prefs.getString("CrosshairColor", "Red")
                    val size = prefs.getString("CrosshairSize", "Medium")
                    findViewById<TextView>(R.id.tvStatusCrosshair).text = "Status: $style ($color, $size)"
                } else {
                    findViewById<TextView>(R.id.tvStatusCrosshair).text = "Status: Disabled"
                }


                val isMonitorOn = prefs.getBoolean("MonitorEnabled", false)
                isMonitorEnabled = isMonitorOn
                if (isMonitorOn) {
                    findViewById<TextView>(R.id.tvStatusMonitor)?.text = "Status: Enabled"
                } else {
                    findViewById<TextView>(R.id.tvStatusMonitor)?.text = "Status: Disabled"
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


    private fun setupClickListeners() {
        val prefs = getSharedPreferences("BenimaruPrefs", Context.MODE_PRIVATE)

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

        findViewById<CardView>(R.id.cardDisableDevOptions).setOnClickListener {
            val status = findViewById<TextView>(R.id.tvStatusDevOptions).text.toString()
            if (status.contains("Disabled")) {
                Toasty.info(this, "Already disabled!", Toast.LENGTH_SHORT, true).show()
                return@setOnClickListener
            }
            runAdbCommand("settings put global development_settings_enabled 0", "Developer Options Disabled", showAd = true) {
                fetchSystemStatuses()
            }
        }

        findViewById<CardView>(R.id.cardFstrim).setOnClickListener {
            handlePremiumFeature {
                val status = findViewById<TextView>(R.id.tvStatusFstrim).text.toString()
                if (status.contains("Recently")) {
                    Toasty.info(this, "Storage already optimized recently!", Toast.LENGTH_SHORT, true).show()
                    return@handlePremiumFeature
                }


                runAdbCommand("sm fstrim", "Storage Optimized (Fstrim complete)", showAd = true) {
                    findViewById<TextView>(R.id.tvStatusFstrim).text = "Status: Recently Optimized"
                }
            }
        }


        findViewById<CardView>(R.id.cardFreezeApps).setOnClickListener {
            handlePremiumFeature { showFreezeAppsSheet() }
        }
        findViewById<TextView>(R.id.btnResetFreeze).setOnClickListener { restoreAllFrozen() }
        findViewById<CardView>(R.id.cardRamBooster).setOnClickListener {
            handlePremiumFeature { showRamBoosterSheet() }
        }
        findViewById<CardView>(R.id.cardClearCache).setOnClickListener {
            handlePremiumFeature { confirmClearCaches() }
        }
        findViewById<TextView>(R.id.btnResetRamBooster).setOnClickListener {
            findViewById<TextView>(R.id.tvStatusRamBooster).text = "Status: Ready"
            Toasty.success(this, "Status reset", Toast.LENGTH_SHORT, true).show()
        }
        findViewById<TextView>(R.id.btnResetCache).setOnClickListener {
            findViewById<TextView>(R.id.tvStatusCache).text = "Status: Ready"
            Toasty.success(this, "Status reset", Toast.LENGTH_SHORT, true).show()
        }

        findViewById<CardView>(R.id.cardAdjustRefreshRate).setOnClickListener { showRefreshRateDialog() }

        findViewById<CardView>(R.id.cardChangeResolution).setOnClickListener {
            handlePremiumFeature { showResolutionModeDialog() }
        }

        findViewById<CardView>(R.id.cardCustomDns).setOnClickListener {
            handlePremiumFeature { showDnsDialog() }
        }

        findViewById<CardView>(R.id.cardCrosshair).setOnClickListener {
            handlePremiumFeature { showCrosshairConfigDialog() }
        }


        findViewById<CardView>(R.id.cardRealtimeMonitor).setOnClickListener {
            handlePremiumFeature { toggleRealtimeMonitor() }
        }

        findViewById<CardView>(R.id.cardThermalThrottling).setOnClickListener {
            handlePremiumFeature {
                Sheet(this)
                    .icon(R.drawable.ic_warning, Color.parseColor("#E53935"))
                    .title("Disable thermal throttling?")
                    .message("This tricks the device into thinking it is cool, forcing maximum performance. Your device WILL get hot. Use with caution.")
                    .button("Cancel", SheetStyle.TONAL)
                    .button("Enable", SheetStyle.DANGER) {
                        runAdbCommand("cmd thermalservice override-status 0", "Thermal Throttling Disabled") {
                            prefs.edit().putBoolean("ThermalDisabled", true).apply()
                            fetchSystemStatuses()
                        }
                    }
                    .show()
            }
        }

        findViewById<CardView>(R.id.cardGameMode).setOnClickListener {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                Toasty.error(this, "This feature requires Android 12 or newer.", Toast.LENGTH_LONG, true).show()
                return@setOnClickListener
            }
            handlePremiumFeature { showGameModeSelectorDialog() }
        }

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


        findViewById<TextView>(R.id.btnResetMonitor).setOnClickListener {
            if (isMonitorEnabled) toggleRealtimeMonitor()
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

        findViewById<TextView>(R.id.btnResetDevOptions).setOnClickListener {
            runAdbCommand("settings put global development_settings_enabled 1", "Developer Options Enabled") { fetchSystemStatuses() }
        }

        findViewById<TextView>(R.id.btnResetFstrim).setOnClickListener {
            findViewById<TextView>(R.id.tvStatusFstrim).text = "Status: Ready"
            Toasty.success(this, "Status reset", Toast.LENGTH_SHORT, true).show()
        }

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
            Sheet(this)
                .icon(R.drawable.ic_reset, Color.parseColor("#E53935"))
                .title("Reset all functions?")
                .message("This restores every tweak to its default (refresh rate, resolution, DNS, animations, thermal limits, game mode and more) and stops the crosshair and monitor overlays.")
                .button("Cancel", SheetStyle.TONAL)
                .button("Reset all", SheetStyle.DANGER) { resetAllFunctions(prefs) }
                .show()
        }
    }

    private fun resetAllFunctions(prefs: android.content.SharedPreferences) {
        run {
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
                settings put global development_settings_enabled 0
            """.trimIndent().replace("\n", " && ").replace("&&  &&", "&&")

            runAdbCommand(resetCmd, "All functions reset to default") {
                prefs.edit()
                    .putBoolean("FixedPerf", false)
                    .putBoolean("ThermalDisabled", false)
                    .putString("LastGameMode", "")
                    .apply()


                if (isMonitorEnabled) {
                    stopService(Intent(this@MainActivity, RealtimeMonitorService::class.java))
                    isMonitorEnabled = false
                    prefs.edit().putBoolean("MonitorEnabled", false).apply()
                    findViewById<TextView>(R.id.tvStatusMonitor)?.text = "Status: Disabled"
                }

                if (isCrosshairEnabled) stopCrosshairService()

                findViewById<TextView>(R.id.tvStatusFixedPerf).text = "Status: Default"
                findViewById<TextView>(R.id.tvStatusOptimize).text = "Status: Ready"

                findViewById<TextView>(R.id.tvStatusFstrim).text = "Status: Ready"
                findViewById<TextView>(R.id.tvStatusRamBooster).text = "Status: Ready"
                findViewById<TextView>(R.id.tvStatusCache).text = "Status: Ready"

                fetchSystemStatuses()
            }
        }
    }



    private class MemApp(
        val pkg: String,
        val label: CharSequence,
        val icon: android.graphics.drawable.Drawable?,
        val mb: Int
    )

    private fun availRamMb(): Long {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.availMem / (1024 * 1024)
    }

    private fun freeStorageBytes(): Long = StatFs(Environment.getDataDirectory().path).availableBytes

    private fun formatSize(bytes: Long): String =
        if (bytes >= 1024L * 1024 * 1024) String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024))
        else String.format("%.0f MB", bytes / (1024.0 * 1024))


    private fun fontify(view: View) {
        ResourcesCompat.getFont(this, R.font.lexendregular)?.let { applyAppFont(view, it) }
    }


    private fun scanMemoryHogs(): List<MemApp> {
        val pm = packageManager
        val output = runAdbCommandWithResult("dumpsys meminfo")

        val homePkg = pm.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY
        )?.activityInfo?.packageName
        val imePkg = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.substringBefore('/')

        val protectedPkgs = setOfNotNull(
            packageName, "moe.shizuku.privileged.api", "android", "com.android.systemui",
            "com.android.phone", homePkg, imePkg
        )

        val line = Regex("""^\s*([\d,]+)K: (\S+)""")
        val perPackage = LinkedHashMap<String, Int>()
        var inSection = false
        for (raw in output.lineSequence()) {
            if (raw.startsWith("Total PSS by process")) { inSection = true; continue }
            if (!inSection) continue
            if (raw.isBlank()) break
            val m = line.find(raw) ?: continue
            val kb = m.groupValues[1].replace(",", "").toLongOrNull() ?: continue
            val pkg = m.groupValues[2].substringBefore(':')
            perPackage[pkg] = (perPackage[pkg] ?: 0) + (kb / 1024).toInt()
        }

        val safeName = Regex("^[A-Za-z0-9._]+$")
        return perPackage.entries
            .asSequence()
            .filter { it.key !in protectedPkgs && it.value >= 20 && safeName.matches(it.key) }
            .sortedByDescending { it.value }
            .mapNotNull { (pkg, mb) ->
                try {
                    if (pm.getLaunchIntentForPackage(pkg) == null) return@mapNotNull null
                    val ai = pm.getApplicationInfo(pkg, 0)
                    MemApp(pkg, pm.getApplicationLabel(ai), pm.getApplicationIcon(ai), mb)
                } catch (e: Exception) {
                    null
                }
            }
            .take(12)
            .toList()
    }

    private fun showRamBoosterSheet() {
        if (!hasShizukuPermission()) return

        val scanning = ProgressSheet(this)
            .icon(R.drawable.ic_tune)
            .title("Scanning memory")
            .message("Checking which apps use the most RAM…")
        scanning.show(indeterminate = true)
        scanning.setIndeterminate("This takes a few seconds")

        lifecycleScope.launch(Dispatchers.IO) {
            val apps = try { scanMemoryHogs() } catch (e: Exception) { emptyList() }
            withContext(Dispatchers.Main) {
                scanning.dismiss()
                if (apps.isEmpty()) {
                    Toasty.info(this@MainActivity, "No heavy background apps found", Toast.LENGTH_LONG, true).show()
                } else {
                    presentRamList(apps)
                }
            }
        }
    }

    private fun presentRamList(apps: List<MemApp>) {
        val selected = apps.map { it.pkg }.toMutableSet()
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val summary = infoCard(this, "")
        val sheet = Sheet(this)

        fun updateSummary() {
            val mb = apps.filter { it.pkg in selected }.sumOf { it.mb }
            summary.text = if (selected.isEmpty()) "Nothing selected."
            else "${selected.size} selected  ·  up to about $mb MB in use. Free RAM now: ${availRamMb()} MB."
            sheet.buttonViews.getOrNull(1)?.let {
                it.isEnabled = selected.isNotEmpty()
                it.text = if (selected.isEmpty()) "Boost" else "Boost (${selected.size})"
            }
        }

        fun render() {
            list.removeAllViews()
            apps.forEach { a ->
                list.addView(optionRow(
                    this, a.label, "${a.mb} MB in use",
                    icon = a.icon?.constantState?.newDrawable()?.mutate() ?: a.icon,
                    tintIcon = false, selected = a.pkg in selected
                ) {
                    if (!selected.remove(a.pkg)) selected.add(a.pkg)
                    render()
                    updateSummary()
                })
            }
            fontify(list)
        }
        render()

        sheet.icon(R.drawable.ic_tune)
            .title("RAM booster")
            .message("Apps using the most memory right now. Tap to select or deselect. Stopped apps can start again on their own, so this is a temporary boost.")
            .content(summary)
            .content(list)
            .button("Cancel", SheetStyle.TONAL)
            .button("Boost", SheetStyle.PRIMARY) { boostApps(selected.toList()) }
            .show()
        updateSummary()
    }

    private fun boostApps(pkgs: List<String>) {
        if (pkgs.isEmpty()) return
        val safeName = Regex("^[A-Za-z0-9._]+$")
        val valid = pkgs.filter { safeName.matches(it) }
        if (valid.isEmpty()) return

        val before = availRamMb()
        val cmd = valid.joinToString("; ") { "am force-stop $it" }
        runAdbCommand(cmd, "Stopped ${valid.size} apps", showAd = true) {
            lifecycleScope.launch {
                delay(1500)
                val freed = (availRamMb() - before).coerceAtLeast(0)
                findViewById<TextView>(R.id.tvStatusRamBooster).text =
                    if (freed > 0) "Status: Freed about $freed MB" else "Status: Stopped ${valid.size} apps"
                updateDeviceInfo()
            }
        }
    }

    private fun confirmClearCaches() {
        Sheet(this)
            .icon(R.drawable.ic_reset)
            .title("Clear all app caches?")
            .message("This removes temporary files from every app to free up storage. Your accounts, settings and saved data are not touched. Apps may open a little slower the first time afterward.")
            .button("Cancel", SheetStyle.TONAL)
            .button("Clear caches", SheetStyle.PRIMARY) { clearAllCaches() }
            .show()
    }

    private fun clearAllCaches() {
        if (!hasShizukuPermission()) return

        val progress = ProgressSheet(this)
            .icon(R.drawable.ic_reset)
            .title("Clearing caches")
            .message("This can take up to a minute. Please keep the app open.")
        progress.show(indeterminate = true)
        progress.setIndeterminate("Working…")

        lifecycleScope.launch(Dispatchers.IO) {
            val before = freeStorageBytes()
            var ok = false
            try {

                val process = Shizuku.newProcess(arrayOf("sh", "-c", "pm trim-caches 999G"), null, null)
                ok = process.waitFor() == 0
            } catch (e: Exception) {
                ok = false
            }
            delay(1000)
            val freed = (freeStorageBytes() - before).coerceAtLeast(0)

            withContext(Dispatchers.Main) {
                progress.dismiss()
                if (ok) {
                    findViewById<TextView>(R.id.tvStatusCache).text =
                        if (freed > 0) "Status: Freed ${formatSize(freed)}" else "Status: Already clean"
                    Toasty.success(
                        this@MainActivity,
                        if (freed > 0) "Freed ${formatSize(freed)} of cache" else "Caches were already clean",
                        Toast.LENGTH_LONG, true
                    ).show()
                    updateDeviceInfo()
                    showInterstitialAd()
                } else {
                    Toasty.error(this@MainActivity, "Your device blocked the cache cleaner", Toast.LENGTH_LONG, true).show()
                }
            }
        }
    }



    private class AppEntry(val pkg: String, val label: String, val isSystem: Boolean, val frozen: Boolean)

    private fun savedFrozen(): MutableSet<String> =
        (getSharedPreferences("BenimaruPrefs", Context.MODE_PRIVATE)
            .getStringSet("FrozenApps", emptySet()) ?: emptySet()).toMutableSet()

    private fun saveFrozen(set: Set<String>) {
        getSharedPreferences("BenimaruPrefs", Context.MODE_PRIVATE)
            .edit().putStringSet("FrozenApps", HashSet(set)).apply()
    }

    private fun refreshFreezeStatus() {
        val tv = findViewById<TextView?>(R.id.tvStatusFreeze) ?: return
        val saved = savedFrozen()
        val stillFrozen = saved.filter { p ->
            try { !packageManager.getApplicationInfo(p, 0).enabled } catch (e: Exception) { true }
        }
        if (stillFrozen.size != saved.size) saveFrozen(stillFrozen.toSet())
        tv.text = if (stillFrozen.isEmpty()) "Status: Ready"
        else "Status: ${stillFrozen.size} app${if (stillFrozen.size == 1) "" else "s"} frozen"
    }

    private fun freezeBlocklist(): Set<String> {
        val pm = packageManager
        val set = mutableSetOf(
            packageName, "moe.shizuku.privileged.api", "android", "com.android.systemui",
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

    private fun isBlockedPackage(pkg: String, block: Set<String>): Boolean =
        pkg in block || pkg.startsWith("com.android.providers.") || pkg.startsWith("com.android.networkstack")

    private fun scanInstalledApps(): List<AppEntry> {
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

    private fun showFreezeAppsSheet() {
        if (!hasShizukuPermission()) return
        val scanning = ProgressSheet(this)
            .icon(R.drawable.ic_freeze)
            .title("Loading apps")
            .message("Reading your installed apps…")
        scanning.show(indeterminate = true)
        scanning.setIndeterminate("Just a moment")

        lifecycleScope.launch(Dispatchers.IO) {
            val apps = try { scanInstalledApps() } catch (e: Exception) { emptyList() }
            withContext(Dispatchers.Main) {
                scanning.dismiss()
                if (apps.isEmpty()) {
                    Toasty.info(this@MainActivity, "No apps found", Toast.LENGTH_LONG, true).show()
                } else {
                    presentFreezeList(apps)
                }
            }
        }
    }

    private fun presentFreezeList(apps: List<AppEntry>) {
        val pm = packageManager
        val userApps = apps.filter { !it.isSystem && !it.frozen }
        val systemApps = apps.filter { it.isSystem && !it.frozen }
        val frozenApps = apps.filter { it.frozen }

        var tab = 0
        var query = ""
        val selected = mutableSetOf<String>()
        val iconCache = HashMap<String, android.graphics.drawable.Drawable?>()

        val tabs = com.google.android.material.button.MaterialButtonToggleGroup(this).apply {
            isSingleSelection = true
            isSelectionRequired = true
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4) }
        }
        val tabLabels = listOf(
            "Apps (${userApps.size})", "System (${systemApps.size})", "Frozen (${frozenApps.size})"
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

        val (searchLayout, searchEdit) = styledField(this, "Search apps", android.text.InputType.TYPE_CLASS_TEXT, topMarginDp = 10)
        val warning = infoCard(
            this,
            "System apps are part of Android. Freezing the wrong one can break calls, notifications, or the home screen. Only freeze system apps you recognise. You can always restore them from the Frozen tab.",
            tint = 0xFFFFA000.toInt()
        ).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
            visibility = View.GONE
        }
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
                selected.isEmpty() -> if (tab == 2) "Restore" else "Freeze"
                tab == 2 -> "Restore (${selected.size})"
                else -> "Freeze (${selected.size})"
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
                    query.isNotBlank() -> "No apps match your search."
                    tab == 2 -> "Nothing is frozen right now."
                    else -> "No apps in this list."
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
                    if (a.isSystem) "System app · ${a.pkg}" else a.pkg,
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
                    text = "Showing ${shown.size} of ${rows.size}. Use search to find more."
                    textSize = 12f
                    setTextColor(Ui.onSurfaceVariant(this@MainActivity))
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
                Sheet(this)
                    .icon(R.drawable.ic_reset)
                    .title("Freeze system apps?")
                    .message(
                        "You selected ${sys.size} system app${if (sys.size == 1) "" else "s"}:\n\n" +
                            sys.take(8).joinToString("\n") { "• ${it.label}" } +
                            (if (sys.size > 8) "\n• and ${sys.size - 8} more" else "") +
                            "\n\nFreezing the wrong system app can break features. You can undo this from the Frozen tab or with Restore all."
                    )
                    .button("Cancel", SheetStyle.TONAL)
                    .button("Freeze anyway", SheetStyle.DANGER) {
                        sheet.dismiss()
                        applyFreezeChange(picked, freeze = true)
                    }
                    .show()
            }
        }

        sheet.icon(R.drawable.ic_freeze)
            .title("Freeze apps")
            .message("Frozen apps are switched off completely: no RAM, no background work, no notifications. Restore them any time.")
            .content(tabs)
            .content(searchLayout)
            .content(warning)
            .content(list)
            .button("Close", SheetStyle.TONAL)
            .button("Freeze", SheetStyle.PRIMARY, dismiss = false) { doChange() }
            .button("Restore all frozen apps", SheetStyle.TEXT, dismiss = false) {
                sheet.dismiss()
                restoreAllFrozen()
            }
            .show()
        updateButtons()
    }

    private data class ShellResult(val code: Int, val out: String)

    private fun runShell(cmd: String): ShellResult = try {
        val p = Shizuku.newProcess(arrayOf("sh", "-c", cmd), null, null)
        val out = p.inputStream.bufferedReader().readText()
        val err = p.errorStream.bufferedReader().readText()
        ShellResult(p.waitFor(), (out + "\n" + err).trim())
    } catch (e: Exception) {
        ShellResult(-1, e.message ?: "error")
    }

    private fun applyFreezeChange(pkgs: List<String>, freeze: Boolean) {
        val safeName = Regex("^[A-Za-z0-9._]+$")
        val valid = pkgs.filter { safeName.matches(it) }
        if (valid.isEmpty()) return

        val progress = ProgressSheet(this)
            .icon(R.drawable.ic_freeze)
            .title(if (freeze) "Freezing apps" else "Restoring apps")
            .message("Please wait…")
        progress.show()
        val userId = android.os.Process.myUid() / 100000
        val before = availRamMb()

        lifecycleScope.launch(Dispatchers.IO) {
            val saved = savedFrozen()
            var ok = 0
            val failed = mutableListOf<String>()
            valid.forEachIndexed { i, p ->
                withContext(Dispatchers.Main) {
                    progress.update((i * 100) / valid.size, "${i + 1} of ${valid.size}")
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
                        "Froze $ok app${if (ok == 1) "" else "s"}" + if (freed > 0) " · about $freed MB freed" else ""
                    } else "Restored $ok app${if (ok == 1) "" else "s"}"
                    Toasty.success(this@MainActivity, msg, Toast.LENGTH_LONG, true).show()
                }
                if (failed.isNotEmpty()) {
                    Toasty.warning(
                        this@MainActivity,
                        "${failed.size} app${if (failed.size == 1) "" else "s"} could not be changed. Android blocks some protected apps.",
                        Toast.LENGTH_LONG, true
                    ).show()
                }
                if (freeze && ok > 0) showInterstitialAd()
            }
        }
    }

    private fun restoreAllFrozen() {
        val saved = savedFrozen().toList()
        if (saved.isEmpty()) {
            Toasty.info(this, "No frozen apps to restore", Toast.LENGTH_SHORT, true).show()
            return
        }
        if (!hasShizukuPermission()) return
        applyFreezeChange(saved, freeze = false)
    }

    private fun toggleRealtimeMonitor() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
            Toasty.info(this, "Please allow 'Display over other apps'", Toast.LENGTH_LONG, true).show()
            return
        }

        val intent = Intent(this, RealtimeMonitorService::class.java)
        val prefs = getSharedPreferences("BenimaruPrefs", Context.MODE_PRIVATE)

        if (isMonitorEnabled) {
            stopService(intent)
            isMonitorEnabled = false
            prefs.edit().putBoolean("MonitorEnabled", false).apply()
            findViewById<TextView>(R.id.tvStatusMonitor).text = "Status: Disabled"
            Toasty.success(this, "Monitor Disabled", Toast.LENGTH_SHORT, true).show()
        } else {
            startService(intent)
            isMonitorEnabled = true
            prefs.edit().putBoolean("MonitorEnabled", true).apply()
            findViewById<TextView>(R.id.tvStatusMonitor).text = "Status: Enabled"
            Toasty.success(this, "Monitor Enabled", Toast.LENGTH_SHORT, true).show()
            showInterstitialAd()
        }
    }

    private fun showResolutionModeDialog() {
        val sheet = Sheet(this)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        list.addView(optionRow(
            this, "Direct change", "Set the resolution right here in the app",
            icon = Ui.drawable(this, R.drawable.ic_tune)
        ) {
            sheet.dismiss()
            showResolutionDialog()
        })
        list.addView(optionRow(
            this, "Floating menu", "A draggable panel you can use on top of any game",
            icon = Ui.drawable(this, R.drawable.ic_play)
        ) {
            sheet.dismiss()
            launchFloatingResolution()
        })
        sheet.icon(R.drawable.ic_tune)
            .title("Change resolution")
            .message("Lower resolutions can boost frame rates and battery life.")
            .content(list)
            .button("Cancel", SheetStyle.TEXT)
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
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
        val gold = Color.parseColor("#E0A800")
        var buyMode = false


        val toggle = com.google.android.material.button.MaterialButtonToggleGroup(this).apply {
            isSingleSelection = true
            isSelectionRequired = true
        }
        val loginTab = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Log in"
            isAllCaps = false
            id = View.generateViewId()
            layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f)
        }
        val buyTab = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Get Premium"
            isAllCaps = false
            id = View.generateViewId()
            layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f)
        }
        toggle.addView(loginTab)
        toggle.addView(buyTab)

        val benefits = infoCard(
            this,
            "✓  No ads, ever\n✓  Every premium tweak unlocked\n✓  Works on up to 2 devices\n✓  One-time $3 payment, lifetime access",
            gold
        )

        val (userLayout, userEdit) = styledField(this, "Username", InputType.TYPE_CLASS_TEXT)
        val (passLayout, passEdit) = styledField(
            this, "Password",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            password = true
        )

        val errorView = TextView(this).apply {
            visibility = View.GONE
            textSize = 13f
            setTextColor(Color.parseColor("#E53935"))
            setPadding(dp(4), dp(8), dp(4), 0)
        }
        fun showError(msg: String?) {
            errorView.text = msg ?: ""
            errorView.visibility = if (msg.isNullOrEmpty()) View.GONE else View.VISIBLE
        }

        val deviceRow = optionRow(
            this, "Device ID", deviceId,
            icon = Ui.drawable(this, R.drawable.ic_lock)
        ) {
            val cb = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cb.setPrimaryClip(android.content.ClipData.newPlainText("Device ID", deviceId))
            Toasty.success(this, "Device ID copied", Toast.LENGTH_SHORT, true).show()
        }

        val note = infoCard(
            this,
            "Your password is never sent or stored as text. It is turned into a one-way hash on this device before it is used."
        )

        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(benefits)
            addView(userLayout)
            addView(passLayout)
            addView(errorView)
            addView(deviceRow)
            addView(note, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(12) })
        }
        (benefits.layoutParams as? LinearLayout.LayoutParams)?.bottomMargin = dp(4)

        val sheet = Sheet(this)
        sheet.icon(R.drawable.ic_crown, gold)
            .title("Premium")
            .message("Log in with the account you created when you purchased.")
            .content(toggle)
            .content(form)


        fun readFields(forPurchase: Boolean): Pair<String, String>? {
            val user = userEdit.text.toString().trim()
            val pass = passEdit.text.toString()
            if (user.isEmpty() || pass.isEmpty()) {
                showError("Please enter a username and password.")
                return null
            }
            if (forPurchase) {
                if (!Regex("^[A-Za-z0-9_.-]{3,32}\$").matches(user)) {
                    showError("Username must be 3-32 letters, numbers, dots, dashes or underscores.")
                    return null
                }
                if (pass.length < 6) {
                    showError("Use a password with at least 6 characters.")
                    return null
                }
            }
            showError(null)
            return user to pass
        }


        sheet.button("Log in", SheetStyle.PRIMARY, dismiss = false) { btn ->
            val (user, pass) = readFields(false) ?: return@button
            btn.isEnabled = false
            btn.text = "Verifying…"
            verifyPremiumAccount(user, pass) { ok, message ->
                btn.isEnabled = true
                btn.text = "Log in"
                if (ok) sheet.dismiss() else showError(message)
            }
        }

        sheet.button("Pay \$3 with PayPal", SheetStyle.PRIMARY, dismiss = false) { btn ->
            val (user, pass) = readFields(true) ?: return@button
            btn.isEnabled = false
            lifecycleScope.launch {
                val hash = withContext(Dispatchers.Default) { hashPassword(user, pass) }
                btn.isEnabled = true
                val paypalEmail = "vestalkimqq02@gmail.com"
                val price = "3.00"
                val paymentUrl = "https://www.paypal.com/cgi-bin/webscr" +
                        "?cmd=_xclick" +
                        "&business=$paypalEmail" +
                        "&item_name=Benimaru+Premium+Unlock" +
                        "&amount=$price" +
                        "&currency_code=USD" +
                        "&on0=Username&os0=${Uri.encode(user)}" +
                        "&on1=Device+ID&os1=${Uri.encode(deviceId)}" +
                        "&on2=Password+hash&os2=${Uri.encode(hash)}" +
                        "&custom=${Uri.encode("$user|$hash|$deviceId")}"
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(paymentUrl)))
            }
        }

        sheet.button("Buy directly via Telegram", SheetStyle.TONAL, dismiss = false) { btn ->
            val (user, pass) = readFields(true) ?: return@button
            btn.isEnabled = false
            lifecycleScope.launch {
                val hash = withContext(Dispatchers.Default) { hashPassword(user, pass) }
                btn.isEnabled = true
                val cb = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cb.setPrimaryClip(
                    android.content.ClipData.newPlainText(
                        "Premium account",
                        "Username: $user\nDevice ID: $deviceId\nPassword hash: $hash"
                    )
                )
                Toasty.info(this@MainActivity, "Account details copied. Paste them in the chat.", Toast.LENGTH_LONG, true).show()
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/benimarux1k")))
            }
        }
        sheet.button("Close", SheetStyle.TEXT)
        sheet.show()

        fun applyMode() {
            sheet.messageView?.text = if (buyMode)
                "Choose a username and password. After you pay, your account is activated and you can log in."
            else
                "Log in with the account you created when you purchased."
            benefits.visibility = if (buyMode) View.VISIBLE else View.GONE
            sheet.buttonViews[0].visibility = if (buyMode) View.GONE else View.VISIBLE
            sheet.buttonViews[1].visibility = if (buyMode) View.VISIBLE else View.GONE
            sheet.buttonViews[2].visibility = if (buyMode) View.VISIBLE else View.GONE
            showError(null)
        }
        toggle.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                buyMode = checkedId == buyTab.id
                applyMode()
            }
        }
        toggle.check(loginTab.id)
        applyMode()
    }


    private fun hashPassword(username: String, password: String): String {
        val salt = "benimaru:${username.trim().lowercase()}".toByteArray(Charsets.UTF_8)
        val key = pbkdf2HmacSha256(password.toByteArray(Charsets.UTF_8), salt, 100_000, 32)
        return key.joinToString("") { "%02x".format(it) }
    }

    private fun pbkdf2HmacSha256(password: ByteArray, salt: ByteArray, iterations: Int, dkLen: Int): ByteArray {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(javax.crypto.spec.SecretKeySpec(password, "HmacSHA256"))
        val result = ByteArray(dkLen)
        var offset = 0
        var block = 1
        while (offset < dkLen) {
            mac.update(salt)
            mac.update(byteArrayOf((block ushr 24).toByte(), (block ushr 16).toByte(), (block ushr 8).toByte(), block.toByte()))
            var u = mac.doFinal()
            val t = u.copyOf()
            for (i in 1 until iterations) {
                u = mac.doFinal(u)
                for (j in t.indices) t[j] = (t[j].toInt() xor u[j].toInt()).toByte()
            }
            val len = minOf(t.size, dkLen - offset)
            System.arraycopy(t, 0, result, offset, len)
            offset += len
            block++
        }
        return result
    }


    private fun verifyPremiumAccount(username: String, pass: String, onDone: (Boolean, String) -> Unit) {
        val deviceId = Secure.getString(contentResolver, Secure.ANDROID_ID) ?: ""

        lifecycleScope.launch(Dispatchers.IO) {
            var ok = false
            var reason = "Invalid username or password."
            try {
                val hash = hashPassword(username, pass)
                val conn = URL("https://raw.githubusercontent.com/Benimaru-x1k/Benimaru/main/users.json")
                    .openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.useCaches = false

                if (conn.responseCode == 200) {
                    val jsonObject = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    val usersArray = jsonObject.getJSONArray("users")

                    for (i in 0 until usersArray.length()) {
                        val userObj = usersArray.getJSONObject(i)
                        if (!userObj.optString("username").equals(username.trim(), ignoreCase = true)) continue

                        val passwordMatches = if (userObj.has("passwordHash")) {
                            MessageDigest.isEqual(
                                hash.toByteArray(),
                                userObj.getString("passwordHash").trim().lowercase().toByteArray()
                            )
                        } else {
                            userObj.optString("password") == pass
                        }
                        if (!passwordMatches) break

                        val devices = userObj.optJSONArray("devices")
                        if (devices != null && devices.length() > 2) {
                            reason = "Account limit exceeded (max 2 devices allowed)."
                        } else {
                            var deviceFound = false
                            if (devices != null) {
                                for (j in 0 until devices.length()) {
                                    if (devices.getString(j) == deviceId) { deviceFound = true; break }
                                }
                            }
                            if (deviceFound) ok = true
                            else reason = "This device isn't authorized yet. Send this ID to the admin: $deviceId"
                        }
                        break
                    }
                } else {
                    reason = "Server error (${conn.responseCode}). Please try again."
                }
                conn.disconnect()
            } catch (e: Exception) {
                reason = "Network error. Please check your connection."
            }

            withContext(Dispatchers.Main) {
                if (ok) {
                    getSharedPreferences("BenimaruPrefs", Context.MODE_PRIVATE).edit()
                        .putBoolean("AdsRemoved", true)
                        .putString("PremiumUser", username.trim())
                        .apply()
                    findViewById<LinearLayout>(R.id.bannerAdContainer).removeAllViews()
                    updatePremiumButtonUI()
                    Toasty.success(this@MainActivity, "Premium activated. Ads removed and features unlocked.", Toast.LENGTH_LONG, true).show()
                }
                onDone(ok, reason)
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
        data class Dns(val name: String, val desc: String, val host: String)
        val providers = listOf(
            Dns("Control D", "Blocks ads and trackers", "p2.freedns.controld.com"),
            Dns("Cloudflare", "Fastest, no logs", "one.one.one.one"),
            Dns("Quad9", "Blocks malicious links", "dns.quad9.net"),
            Dns("NextDNS", "Personalized blocking", "dns.nextdns.io"),
            Dns("Google", "Stable and reliable", "dns.google")
        )
        val current = findViewById<TextView>(R.id.tvStatusDns).text.toString()

        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val sheet = Sheet(this)
            .icon(R.drawable.ic_lock)
            .title("Private DNS")
            .message("Choose a provider for ad blocking, tracker protection and lower latency.")

        providers.forEach { p ->
            list.addView(optionRow(this, p.name, "${p.desc}  ·  ${p.host}", selected = current.contains(p.host)) {
                sheet.dismiss()
                val cmd = "settings put global private_dns_mode hostname && settings put global private_dns_specifier ${p.host}"
                runAdbCommand(cmd, "DNS set to ${p.name}", showAd = true) { fetchSystemStatuses() }
            })
        }
        list.addView(optionRow(this, "Default", "Turn private DNS off (automatic)", selected = current.contains("Default")) {
            sheet.dismiss()
            val cmd = "settings put global private_dns_mode default && settings delete global private_dns_specifier"
            runAdbCommand(cmd, "DNS reset to default", showAd = false) { fetchSystemStatuses() }
        })

        sheet.content(list).button("Cancel", SheetStyle.TEXT).show()
    }

    private fun showRefreshRateDialog() {
        val (minLayout, minInput) = styledField(this, "Minimum (Hz)", InputType.TYPE_CLASS_NUMBER, topMarginDp = 0)
        val (maxLayout, maxInput) = styledField(this, "Maximum (Hz)", InputType.TYPE_CLASS_NUMBER, topMarginDp = 0)

        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            minLayout.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            maxLayout.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginStart = dp(10) }
            addView(minLayout)
            addView(maxLayout)
        }


        val presetRates = listOf(60, 90, 120, 144)
        val presets = presetRow(this, presetRates.map { "$it Hz" }) { i ->
            minInput.setText(presetRates[i].toString())
            maxInput.setText(presetRates[i].toString())
        }

        val sheet = Sheet(this)
        sheet.icon(R.drawable.ic_tune)
            .title("Refresh rate")
            .message("Pick a preset or set your own range. Not every phone supports every rate.")
            .content(presets)
            .content(fields)
            .button("Cancel", SheetStyle.TONAL)
            .button("Apply", SheetStyle.PRIMARY, dismiss = false) {
                val min = minInput.text.toString().trim().toIntOrNull()
                val max = maxInput.text.toString().trim().toIntOrNull()
                when {
                    min == null || max == null -> Toasty.warning(this, "Please enter valid numbers", Toast.LENGTH_SHORT, true).show()
                    min !in 30..240 || max !in 30..240 -> Toasty.warning(this, "Use a value between 30 and 240 Hz", Toast.LENGTH_SHORT, true).show()
                    min > max -> Toasty.warning(this, "Minimum can't be higher than maximum", Toast.LENGTH_SHORT, true).show()
                    else -> {
                        sheet.dismiss()
                        val cmd = "settings put system min_refresh_rate $min && settings put system peak_refresh_rate $max"
                        runAdbCommand(cmd, "Refresh rate set to $min - $max Hz", showAd = true) { fetchSystemStatuses() }
                    }
                }
            }
            .show()
    }

    private fun showResolutionDialog() {
        val metrics = resources.displayMetrics
        val currentDpi = metrics.densityDpi
        val currentWidth = metrics.widthPixels.toFloat()
        val currentHeight = metrics.heightPixels.toFloat()
        val aspectRatio = currentHeight / currentWidth

        val autoMatchSwitch = MaterialSwitch(this).apply {
            text = "Auto-match aspect ratio"
            isChecked = false
        }
        val (widthLayout, widthInput) = styledField(this, "Width (now ${currentWidth.toInt()})", InputType.TYPE_CLASS_NUMBER, topMarginDp = 0)
        val (heightLayout, heightInput) = styledField(this, "Height (now ${currentHeight.toInt()})", InputType.TYPE_CLASS_NUMBER, topMarginDp = 0)

        val fields = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            widthLayout.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            heightLayout.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .apply { marginStart = dp(10) }
            addView(widthLayout)
            addView(heightLayout)
        }

        var isAutoCalculating = false
        fun link(source: TextInputEditText, target: TextInputEditText, calc: (Int) -> Int) {
            source.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (!autoMatchSwitch.isChecked || isAutoCalculating) return
                    isAutoCalculating = true
                    val v = s.toString().toIntOrNull()
                    target.setText(if (v != null) calc(v).toString() else "")
                    isAutoCalculating = false
                }
            })
        }
        link(widthInput, heightInput) { (it * aspectRatio).toInt() }
        link(heightInput, widthInput) { (it / aspectRatio).toInt() }

        autoMatchSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (!isChecked || isAutoCalculating) return@setOnCheckedChangeListener
            val w = widthInput.text.toString().toIntOrNull()
            val h = heightInput.text.toString().toIntOrNull()
            isAutoCalculating = true
            if (w != null) heightInput.setText((w * aspectRatio).toInt().toString())
            else if (h != null) widthInput.setText((h / aspectRatio).toInt().toString())
            isAutoCalculating = false
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(autoMatchSwitch, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) })
            addView(fields)
            addView(infoCard(this@MainActivity, "DPI is calculated automatically. You'll get 6 seconds to confirm, otherwise it reverts."),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = dp(14) })
        }

        val sheet = Sheet(this)
        sheet.icon(R.drawable.ic_tune)
            .title("Change resolution")
            .content(content)
            .button("Cancel", SheetStyle.TONAL)
            .button("Preview", SheetStyle.PRIMARY, dismiss = false) {
                val newWidth = widthInput.text.toString().trim().toIntOrNull()
                val newHeight = heightInput.text.toString().trim().toIntOrNull()
                if (newWidth == null || newHeight == null) {
                    Toasty.warning(this, "Please enter width and height", Toast.LENGTH_SHORT, true).show()
                    return@button
                }
                if (newWidth < 300 || newHeight < 300) {
                    Toasty.warning(this, "That resolution is too small", Toast.LENGTH_SHORT, true).show()
                    return@button
                }
                if (!hasShizukuPermission()) return@button
                sheet.dismiss()

                val scalingRatio = minOf(newWidth / currentWidth, newHeight / currentHeight)
                val newDpi = (scalingRatio * currentDpi).toInt()
                val cmd = "wm size ${newWidth}x${newHeight} && wm density $newDpi"

                lifecycleScope.launch(Dispatchers.IO) {
                    val process = Shizuku.newProcess(arrayOf("sh", "-c", cmd), null, null)
                    if (process.waitFor() == 0) {
                        withContext(Dispatchers.Main) { showResolutionPreviewCountdown() }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toasty.error(this@MainActivity, "Couldn't apply that resolution", Toast.LENGTH_SHORT, true).show()
                        }
                    }
                }
            }
            .show()
    }

    private fun showResolutionPreviewCountdown() {
        var timerJob: Job? = null
        var decided = false

        val bar = LinearProgressIndicator(this).apply {
            max = 6000
            progress = 6000
            isIndeterminate = false
            trackThickness = dp(8)
            trackCornerRadius = dp(4)
            setIndicatorColor(Ui.accent(this@MainActivity))
        }

        val sheet = Sheet(this)
        sheet.icon(R.drawable.ic_warning, Color.parseColor("#E09B00"))
            .title("Keep this resolution?")
            .message("Reverting in 6 seconds…")
            .content(bar)
            .cancelable(false)
            .button("Revert", SheetStyle.TONAL) {
                decided = true
                timerJob?.cancel()
                runAdbCommand("wm size reset && wm density reset", "Resolution reverted") { fetchSystemStatuses() }
            }
            .button("Keep", SheetStyle.PRIMARY) {
                decided = true
                timerJob?.cancel()
                Toasty.success(this, "Resolution saved", Toast.LENGTH_SHORT, true).show()
                fetchSystemStatuses()
                showInterstitialAd()
            }
            .show()

        timerJob = lifecycleScope.launch(Dispatchers.Main) {
            for (step in 60 downTo 0) {
                bar.progress = step * 100
                sheet.messageView?.text = "Reverting in ${(step + 9) / 10} seconds…"
                delay(100)
            }
            if (!decided) {
                decided = true
                sheet.dismiss()
                runAdbCommand("wm size reset && wm density reset", "Auto-reverted: no confirmation") { fetchSystemStatuses() }
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
        val styles = listOf(
            "Cross", "Dot", "Circle", "Cross with Circle", "Square", "Target",
            "Gap Cross", "Diamond", "Triangle"
        )
        val colors = listOf("White", "Black", "Red", "Green", "Blue", "Yellow", "Cyan", "Magenta")
        val colorValues = listOf(
            Color.WHITE, Color.BLACK, Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.CYAN, Color.MAGENTA
        )
        val sizes = listOf("Tiny", "Small", "Medium", "Large", "Extra Large")

        var styleIdx = prefs.getInt("CrosshairStyleIdx", 0).coerceIn(styles.indices)
        var colorIdx = prefs.getInt("CrosshairColorIdx", 2).coerceIn(colors.indices)
        var sizeIdx = prefs.getInt("CrosshairSizeIdx", 2).coerceIn(sizes.indices)
        val savedStyle = styleIdx
        val savedColor = colorIdx
        val savedSize = sizeIdx
        val wasEnabled = prefs.getBoolean("CrosshairEnabled", false)
        var saved = false

        val preview = CrosshairPreviewView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(120))
        }

        fun refresh() {
            preview.set(styles[styleIdx], colors[colorIdx], sizes[sizeIdx])

            if (isCrosshairEnabled) pushCrosshair(styles[styleIdx], colors[colorIdx], sizes[sizeIdx])
        }

        fun sectionLabel(text: String) = TextView(this).apply {
            this.text = text
            textSize = 12f
            letterSpacing = 0.08f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(Ui.onSurfaceVariant(this@MainActivity))
            setPadding(dp(4), dp(18), 0, dp(8))
        }


        val chipGroup = ChipGroup(this).apply {
            isSingleSelection = true
            isSelectionRequired = true
            chipSpacingHorizontal = dp(8)
        }
        val chipIds = styles.map { View.generateViewId() }
        styles.forEachIndexed { i, name ->
            chipGroup.addView(Chip(this).apply {
                id = chipIds[i]
                text = name
                isCheckable = true
                isChecked = i == styleIdx
                isCheckedIconVisible = false
                ResourcesCompat.getFont(this@MainActivity, R.font.lexendregular)?.let { typeface = it }
            })
        }
        chipGroup.setOnCheckedChangeListener { _, checkedId ->
            val idx = chipIds.indexOf(checkedId)
            if (idx >= 0) { styleIdx = idx; refresh() }
        }


        val swatchRow = colorSwatchRow(this, colorValues, colors, colorIdx) { i ->
            colorIdx = i
            refresh()
        }


        val sizeLabel = sectionLabel("SIZE  ·  ${sizes[sizeIdx]}")
        val slider = com.google.android.material.slider.Slider(this).apply {
            valueFrom = 0f
            valueTo = (sizes.size - 1).toFloat()
            stepSize = 1f
            value = sizeIdx.toFloat()
            setLabelFormatter { v -> sizes[v.toInt().coerceIn(sizes.indices)] }
            addOnChangeListener { _, v, fromUser ->
                if (fromUser) {
                    sizeIdx = v.toInt()
                    sizeLabel.text = "SIZE  ·  ${sizes[sizeIdx]}"
                    refresh()
                }
            }
        }

        val hScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(chipGroup)
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(preview)
            addView(sectionLabel("SHAPE"))
            addView(hScroll)
            addView(sectionLabel("COLOR"))
            addView(swatchRow)
            addView(sizeLabel)
            addView(slider)
        }
        preview.set(styles[styleIdx], colors[colorIdx], sizes[sizeIdx])

        val sheet = Sheet(this)
        sheet.icon(R.drawable.ic_tune)
            .title("Crosshair")
            .message("Changes show live on your screen once the crosshair is running.")
            .content(content)
            .button("Cancel", SheetStyle.TONAL)
            .button(if (wasEnabled) "Save" else "Start", SheetStyle.PRIMARY) {
                saved = true
                prefs.edit()
                    .putInt("CrosshairStyleIdx", styleIdx)
                    .putInt("CrosshairColorIdx", colorIdx)
                    .putInt("CrosshairSizeIdx", sizeIdx)
                    .apply()
                startCrosshairService(styles[styleIdx], colors[colorIdx], sizes[sizeIdx])
            }
        if (wasEnabled) {
            sheet.button("Turn off", SheetStyle.DANGER) {
                saved = true
                stopCrosshairService()
            }
        }
        sheet.onDismiss {

            if (!saved && isCrosshairEnabled) {
                pushCrosshair(styles[savedStyle], colors[savedColor], sizes[savedSize])
            }
        }
        sheet.show()
    }


    private fun pushCrosshair(style: String, color: String, size: String) {
        startService(Intent(this, CrosshairService::class.java).apply {
            putExtra("STYLE", style)
            putExtra("COLOR", color)
            putExtra("SIZE", size)
        })
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

    private fun findGames(): List<ResolveInfo> {
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

    private fun showGamePicker(title: String, message: String, onPick: (ResolveInfo) -> Unit) {
        val games = findGames()
        if (games.isEmpty()) {
            Toasty.info(this, "No games found on your device", Toast.LENGTH_SHORT, true).show()
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
            .button("Close", SheetStyle.TEXT)
            .show()
    }

    private fun showGameModeSelectorDialog() {
        showGamePicker("Native Game Mode", "Pick a game to run in Performance mode.") { selectedApp ->
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
                            Toasty.success(this@MainActivity, "Performance Mode enabled for $appName", Toast.LENGTH_SHORT, true).show()
                            getSharedPreferences("BenimaruPrefs", Context.MODE_PRIVATE).edit().putString("LastGameMode", pkgName).apply()
                            fetchSystemStatuses()
                            showInterstitialAd()
                        } else {
                            Toasty.warning(this@MainActivity, "Your device's custom OS blocks Native Game Mode. Please use the 'Optimize System' or 'Launch Game' buttons instead.", Toast.LENGTH_LONG, true).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toasty.error(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT, true).show()
                    }
                }
            }
        }
    }

    private fun showGameLauncherDialog() {
        showGamePicker("Launch a game", "Choose a game to start.") { selectedApp ->
            val pm = packageManager
            val pkgName = selectedApp.activityInfo.packageName
            val appName = selectedApp.loadLabel(pm).toString()

            fun launchGame() {
                val launchIntent = pm.getLaunchIntentForPackage(pkgName)
                if (launchIntent != null) {
                    startActivity(launchIntent)
                } else {
                    Toasty.error(this, "Failed to launch game", Toast.LENGTH_SHORT, true).show()
                }
            }

            Sheet(this)
                .icon(R.drawable.ic_play)
                .title(appName)
                .message("Pre-compiling the game's code can reduce in-game stutter. It takes 10-30 seconds.")
                .button("Optimize & launch", SheetStyle.PRIMARY) {
                    val progress = ProgressSheet(this)
                        .icon(R.drawable.ic_tune)
                        .title("Optimizing $appName")
                        .message("Pre-compiling game code. Please keep the app open.")
                    progress.show(indeterminate = true)
                    progress.setIndeterminate("Working…")

                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            val process = Shizuku.newProcess(arrayOf("sh", "-c", "cmd package compile -m speed -f $pkgName"), null, null)
                            process.waitFor()
                            withContext(Dispatchers.Main) {
                                progress.dismiss()
                                Toasty.success(this@MainActivity, "$appName optimized", Toast.LENGTH_SHORT, true).show()
                                showInterstitialAd { launchGame() }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                progress.dismiss()
                                Toasty.error(this@MainActivity, "Optimization failed: ${e.message}", Toast.LENGTH_LONG, true).show()
                            }
                        }
                    }
                }
                .button("Launch now", SheetStyle.TONAL) {
                    launchGame()
                    Toasty.success(this, "Launching $appName", Toast.LENGTH_SHORT, true).show()
                }
                .button("Cancel", SheetStyle.TEXT)
                .show()
        }
    }

    private fun showShizukuRequiredDialog() {
        if (isFinishing || isDestroyed) return
        Sheet(this)
            .icon(R.drawable.ic_download)
            .title("Shizuku required")
            .message("Benimaru Tool uses Shizuku to apply system tweaks without root. Download it now to continue.")
            .cancelable(false)
            .button("Exit", SheetStyle.TONAL) { finishAffinity() }
            .button("Download", SheetStyle.PRIMARY) { downloadShizukuApk() }
            .show()
    }

    private fun downloadShizukuApk() {
        downloadAndInstall(
            "Downloading Shizuku",
            "https://github.com/RikkaApps/Shizuku/releases/download/v13.6.0/shizuku-v13.6.0.r1086.2650830c-release.apk",
            "shizuku-v13.6.0.apk",
            finishAfterInstall = true
        )
    }

    private fun installApk(file: File, finishAfter: Boolean = true) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            pendingApk = file
            Sheet(this)
                .icon(R.drawable.ic_lock)
                .title("Allow installs")
                .message("To install this file, allow Benimaru Tool to install apps. Come back here afterwards and the installer will open.")
                .button("Not now", SheetStyle.TONAL)
                .button("Open settings", SheetStyle.PRIMARY) {
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
            Toasty.error(this, "Failed to open installer: ${e.message}", Toast.LENGTH_LONG, true).show()
        }
    }

    private fun setupFilterChips() {
        val chipGroup = findViewById<ChipGroup>(R.id.chipGroupCategories)

        val customTypeface = ResourcesCompat.getFont(this, R.font.lexendregular)
        val chipIds = listOf(R.id.chipAll, R.id.chipPerformance, R.id.chipDisplay, R.id.chipNetwork, R.id.chipGaming)
        chipIds.forEach { id ->
            findViewById<Chip>(id)?.typeface = customTypeface
        }

        val performanceCards = listOf(R.id.cardFixedPerformance, R.id.cardOptimizeSystem, R.id.cardThermalThrottling, R.id.cardFstrim, R.id.cardDisableDevOptions, R.id.cardRamBooster, R.id.cardClearCache, R.id.cardFreezeApps)
        val displayCards = listOf(R.id.cardAdjustRefreshRate, R.id.cardFastAnimations, R.id.cardDisableBlurs, R.id.cardChangeResolution)
        val networkCards = listOf(R.id.cardImproveNetwork, R.id.cardCustomDns)
        val gamingCards = listOf(R.id.cardImproveTouch, R.id.cardGamingDnd, R.id.cardCrosshair, R.id.cardRealtimeMonitor, R.id.cardGameMode)

        val allCards = performanceCards + displayCards + networkCards + gamingCards

        chipGroup.setOnCheckedChangeListener { _, checkedId ->
            allCards.forEach { findViewById<View>(it)?.visibility = View.GONE }

            val visibleIds = when (checkedId) {
                R.id.chipPerformance -> performanceCards
                R.id.chipDisplay -> displayCards
                R.id.chipNetwork -> networkCards
                R.id.chipGaming -> gamingCards
                else -> allCards
            }

            visibleIds.forEach { id ->
                findViewById<View>(id)?.visibility = View.VISIBLE
            }
        }
    }

    private enum class TweakState { ACTIVE, NEUTRAL, WARN }


    private fun setupStatusStyling() {
        val statusIds = listOf(
            R.id.tvStatusFixedPerf, R.id.tvStatusOptimize, R.id.tvStatusRefresh, R.id.tvStatusNetwork,
            R.id.tvStatusTouch, R.id.tvStatusAnimations, R.id.tvStatusBlurs, R.id.tvStatusDnd,
            R.id.tvStatusDevOptions, R.id.tvStatusResolution, R.id.tvStatusDns, R.id.tvStatusCrosshair,
            R.id.tvStatusMonitor, R.id.tvStatusGameMode, R.id.tvStatusThermal, R.id.tvStatusFstrim,
            R.id.tvStatusRamBooster, R.id.tvStatusCache, R.id.tvStatusFreeze
        )
        val states = HashMap<Int, TweakState>()
        val green = Color.parseColor("#2E9E5B")
        val amber = Color.parseColor("#E09B00")

        fun stateOf(id: Int, text: String): TweakState {
            val t = text.removePrefix("Status:").trim()
            return when {
                t.isEmpty() || t.startsWith("Default") || t.startsWith("Ready") ||
                    t.startsWith("Unknown") || t.startsWith("Checking") -> TweakState.NEUTRAL
                id == R.id.tvStatusDevOptions -> if (t.startsWith("Enabled")) TweakState.WARN else TweakState.ACTIVE
                id == R.id.tvStatusThermal -> TweakState.WARN
                id == R.id.tvStatusBlurs -> if (t.startsWith("Disabled")) TweakState.ACTIVE else TweakState.NEUTRAL
                t.startsWith("Disabled") -> TweakState.NEUTRAL
                else -> TweakState.ACTIVE
            }
        }

        fun refresh() {
            val active = states.values.count { it != TweakState.NEUTRAL }
            findViewById<TextView>(R.id.tvActiveSummary)?.text = "$active of ${statusIds.size} active"
        }

        fun apply(tv: TextView, id: Int) {
            val st = stateOf(id, tv.text.toString())
            states[id] = st
            val color = when (st) {
                TweakState.ACTIVE -> green
                TweakState.WARN -> amber
                TweakState.NEUTRAL -> Color.parseColor("#8A8F98")
            }
            tv.setTextColor(color)
            AppCompatResources.getDrawable(this, R.drawable.ic_status_dot)?.let { d ->
                val dot = DrawableCompat.wrap(d.mutate())
                DrawableCompat.setTint(dot, color)
                tv.setCompoundDrawablesRelativeWithIntrinsicBounds(dot, null, null, null)
            }
            refresh()
        }

        statusIds.forEach { id ->
            val tv = findViewById<TextView>(id) ?: return@forEach
            apply(tv, id)
            tv.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { apply(tv, id) }
            })
        }
    }

    private fun updateShizukuBannerUI(isConnected: Boolean) {
        val cardShizuku = findViewById<MaterialCardView>(R.id.cardShizukuStatus)
        val tvShizuku = findViewById<TextView>(R.id.tvShizukuStatus)

        val color = Color.parseColor(if (isConnected) "#4ADE80" else "#FF6B6B")
        tvShizuku.text = if (isConnected) "Shizuku" else "Offline"
        tvShizuku.setTextColor(Color.WHITE)
        cardShizuku.setCardBackgroundColor(Color.parseColor(if (isConnected) "#334ADE80" else "#33FF6B6B"))
        cardShizuku.strokeColor = Color.parseColor(if (isConnected) "#664ADE80" else "#66FF6B6B")

        AppCompatResources.getDrawable(this, R.drawable.ic_status_dot)?.let { d ->
            val dot = DrawableCompat.wrap(d.mutate())
            DrawableCompat.setTint(dot, color)
            tvShizuku.setCompoundDrawablesRelativeWithIntrinsicBounds(dot, null, null, null)
        }

        cardShizuku.setOnClickListener {
            if (isConnected) {
                Toasty.success(this, "Shizuku is connected and ready", Toast.LENGTH_SHORT, true).show()
            } else {
                checkShizukuStatus()
            }
        }
    }

    private fun setupBottomBarScrollBehavior() {
        val scrollView = findViewById<NestedScrollView>(R.id.mainScrollView)
        val bottomBar = findViewById<View>(R.id.floatingBottomBar)
        var isBottomBarVisible = true

        scrollView.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            if (scrollY > oldScrollY && isBottomBarVisible) {
                isBottomBarVisible = false
                val margin = (bottomBar.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin
                bottomBar.animate()
                    .translationY(bottomBar.height.toFloat() + margin)
                    .setDuration(250)
                    .start()
            } else if (scrollY < oldScrollY && !isBottomBarVisible) {
                isBottomBarVisible = true
                bottomBar.animate()
                    .translationY(0f)
                    .setDuration(250)
                    .start()
            }
        }
    }
}