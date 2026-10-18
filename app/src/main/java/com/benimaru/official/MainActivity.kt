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
import androidx.core.content.ContextCompat
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
    private var isAutoClickEnabled = false

    private val unityGameId = "5781189"
    private val adUnitInterstitial = "Interstitial_Android"
    private val adUnitBanner = "Banner_Android"
    private val adUnitRewarded = "Rewarded_Android"
    private val testMode = false

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == SHIZUKU_REQUEST_CODE) {
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                Toasty.success(this, getString(R.string.toast_shizuku_granted), Toast.LENGTH_SHORT, true).show()
                updateShizukuBannerUI(true)
                fetchSystemStatuses()
            } else {
                updateShizukuBannerUI(false)
                Toasty.error(this, getString(R.string.toast_shizuku_denied), Toast.LENGTH_LONG, true).show()
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
        recheckPremiumIfNeeded(force = true)

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
                Toasty.success(this, getString(R.string.toast_you_premium), Toast.LENGTH_SHORT, true).show()
            }
        } else {
            val btnPremium = findViewById<Button>(R.id.btnRemoveAds)
            btnPremium.text = getString(R.string.btn_remove_ads)
            btnPremium.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#26FFC107"))
            val typedValue = android.util.TypedValue()
            theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
            btnPremium.setTextColor(ContextCompat.getColor(this, typedValue.resourceId))
            (btnPremium as? MaterialButton)?.iconTint = ColorStateList.valueOf(Color.parseColor("#E0A800"))

            btnPremium.setOnClickListener {
                showRemoveAdsDialog()
            }
        }
    }

    private enum class PremiumStatus { VALID, INVALID, UNREACHABLE }

    private var lastPremiumRecheckAt = 0L
    private var isRecheckingPremium = false
    private val premiumRecheckIntervalMs = 60 * 1000L
    private val premiumOfflineGraceMs = 7L * 24L * 60L * 60L * 1000L

    private fun recheckPremiumIfNeeded(force: Boolean = false) {
        if (!isPremium() || isRecheckingPremium) return

        val now = android.os.SystemClock.elapsedRealtime()
        if (!force && lastPremiumRecheckAt != 0L && now - lastPremiumRecheckAt < premiumRecheckIntervalMs) return
        lastPremiumRecheckAt = now
        isRecheckingPremium = true

        val prefs = getSharedPreferences("BenimaruPrefs", Context.MODE_PRIVATE)
        val username = prefs.getString("PremiumUser", "") ?: ""
        val storedHash = prefs.getString("PremiumHash", "") ?: ""
        val deviceId = Secure.getString(contentResolver, Secure.ANDROID_ID) ?: ""

        lifecycleScope.launch(Dispatchers.IO) {
            val status = checkPremiumStatus(username, storedHash, deviceId)
            withContext(Dispatchers.Main) {
                isRecheckingPremium = false
                val wallNow = System.currentTimeMillis()
                when (status) {
                    PremiumStatus.VALID -> {
                        prefs.edit().putLong("PremiumLastVerified", wallNow).apply()
                    }
                    PremiumStatus.INVALID -> revokePremium()
                    PremiumStatus.UNREACHABLE -> {
                        val lastVerified = prefs.getLong("PremiumLastVerified", 0L)
                        if (lastVerified == 0L) {
                            prefs.edit().putLong("PremiumLastVerified", wallNow).apply()
                        } else if (wallNow - lastVerified > premiumOfflineGraceMs) {
                            revokePremium()
                        }
                    }
                }
            }
        }
    }

    private fun checkPremiumStatus(username: String, storedHash: String, deviceId: String): PremiumStatus {
        if (username.isBlank()) return PremiumStatus.INVALID

        var conn: HttpURLConnection? = null
        try {
            conn = URL("https://raw.githubusercontent.com/Benimaru-x1k/Benimaru/main/users.json?t=${System.currentTimeMillis()}")
                .openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.useCaches = false

            if (conn.responseCode != 200) return PremiumStatus.UNREACHABLE

            val jsonObject = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val usersArray = jsonObject.getJSONArray("users")

            for (i in 0 until usersArray.length()) {
                val userObj = usersArray.getJSONObject(i)
                if (!userObj.optString("username").equals(username.trim(), ignoreCase = true)) continue

                if (userObj.has("passwordHash") && storedHash.isNotEmpty()) {
                    val remoteHash = userObj.getString("passwordHash").trim().lowercase()
                    if (!MessageDigest.isEqual(storedHash.toByteArray(), remoteHash.toByteArray())) {
                        return PremiumStatus.INVALID
                    }
                }

                val devices = userObj.optJSONArray("devices") ?: return PremiumStatus.INVALID
                if (devices.length() > 2) return PremiumStatus.INVALID
                for (j in 0 until devices.length()) {
                    if (devices.getString(j) == deviceId) return PremiumStatus.VALID
                }
                return PremiumStatus.INVALID
            }
            return PremiumStatus.INVALID
        } catch (e: Exception) {
            return PremiumStatus.UNREACHABLE
        } finally {
            conn?.disconnect()
        }
    }

    private fun revokePremium() {
        getSharedPreferences("BenimaruPrefs", Context.MODE_PRIVATE).edit()
            .remove("AdsRemoved")
            .remove("PremiumUser")
            .remove("PremiumHash")
            .remove("PremiumLastVerified")
            .apply()
        updatePremiumButtonUI()
        initializeUnityAds()
        Toasty.warning(this, getString(R.string.toast_premium_inactive), Toast.LENGTH_LONG, true).show()
    }

    private fun handlePremiumFeature(action: () -> Unit) {
        if (isPremium()) {
            action()
            return
        }

        Sheet(this)
            .icon(R.drawable.ic_crown, Color.parseColor("#E0A800"))
            .title(getString(R.string.dlg_premium_feature))
            .message(getString(R.string.dlg_premium_feature_msg))
            .button(getString(R.string.btn_watch_ad), SheetStyle.PRIMARY) { showRewardedAd { action() } }
            .button(getString(R.string.btn_get_premium), SheetStyle.TONAL) { showRemoveAdsDialog() }
            .button(getString(R.string.btn_not_now), SheetStyle.TEXT)
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

        Toasty.info(this, getString(R.string.toast_loading_ad), Toast.LENGTH_SHORT, true).show()

        UnityAds.show(this, adUnitRewarded, object : IUnityAdsShowListener {
            override fun onUnityAdsShowFailure(placementId: String, error: UnityAds.UnityAdsShowError, message: String) {
                Toasty.error(this@MainActivity, getString(R.string.toast_ad_failed), Toast.LENGTH_SHORT, true).show()
                loadRewardedAd()
            }
            override fun onUnityAdsShowStart(placementId: String) {}
            override fun onUnityAdsShowClick(placementId: String) {}
            override fun onUnityAdsShowComplete(placementId: String, state: UnityAds.UnityAdsShowCompletionState) {
                if (state == UnityAds.UnityAdsShowCompletionState.COMPLETED) {
                    Toasty.success(this@MainActivity, getString(R.string.toast_unlocked), Toast.LENGTH_SHORT, true).show()
                    onSuccess()
                } else {
                    Toasty.warning(this@MainActivity, getString(R.string.toast_ad_skipped), Toast.LENGTH_LONG, true).show()
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
        recheckPremiumIfNeeded()
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
                        Toasty.error(this@MainActivity, getString(R.string.toast_unofficial), Toast.LENGTH_LONG, true).show()
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

    private fun downloadAppUpdate(downloadUrl: String) {
        downloadAndInstall(getString(R.string.dl_updating), downloadUrl, "BenimaruTool-update.apk", finishAfterInstall = false)
    }

    private fun formatMb(bytes: Long): String = String.format("%.1f MB", bytes / (1024.0 * 1024.0))


    private fun downloadAndInstall(title: String, url: String, fileName: String, finishAfterInstall: Boolean) {
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
                                        progress.update(pct, getString(R.string.dl_progress, pct, formatMb(downloaded), formatMb(total)))
                                    }
                                }
                            } else {
                                val mbNow = downloaded / (1024 * 1024)
                                if (mbNow != lastMb) {
                                    lastMb = mbNow
                                    withContext(Dispatchers.Main) {
                                        progress.setIndeterminate(getString(R.string.dl_downloaded, formatMb(downloaded)))
                                    }
                                }
                            }
                        }
                        output.flush()
                    }
                }

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
                    Toasty.error(this@MainActivity, getString(R.string.toast_dl_failed, e.message ?: ""), Toast.LENGTH_LONG, true).show()
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
                Toasty.warning(this, getString(R.string.toast_shizuku_not_running_launch), Toast.LENGTH_LONG, true).show()
                launchShizukuApp()
            }
        } else {
            updateShizukuBannerUI(false)
            showShizukuRequiredDialog()
        }
    }

    private fun hasShizukuPermission(): Boolean {
        if (!Shizuku.pingBinder()) {
            Toasty.error(this, getString(R.string.toast_shizuku_not_running), Toast.LENGTH_SHORT, true).show()
            return false
        }
        return if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            true
        } else {
            if (Shizuku.shouldShowRequestPermissionRationale()) {
                Toasty.info(this, getString(R.string.toast_open_shizuku), Toast.LENGTH_LONG, true).show()
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
            Toasty.error(this, getString(R.string.toast_shizuku_launch_fail), Toast.LENGTH_SHORT, true).show()
            finishAffinity()
        }
    }

    private fun fetchSystemStatuses() {
        if (!hasShizukuPermission()) return

        lifecycleScope.launch(Dispatchers.IO) {
            val prefs = getSharedPreferences("BenimaruPrefs", Context.MODE_PRIVATE)
            val isFixedPerf = prefs.getBoolean("FixedPerf", false)
            val fixedPerfStatus = if (isFixedPerf)
                getString(if (prefs.getBoolean("FixedPerfAggressive", false)) R.string.status_enabled_aggr else R.string.status_enabled)
            else getString(R.string.status_default)

            val isThermalDisabled = prefs.getBoolean("ThermalDisabled", false)
            val thermalStatus = if (isThermalDisabled) getString(R.string.status_throttling_disabled) else getString(R.string.status_default)

            val lastGameMode = prefs.getString("LastGameMode", "")
            val gameModeStatus = if (lastGameMode.isNullOrEmpty()) getString(R.string.status_default) else getString(R.string.status_active, lastGameMode.orEmpty())

            val minRefresh = runAdbCommandWithResult("settings get system min_refresh_rate")
            val maxRefresh = runAdbCommandWithResult("settings get system peak_refresh_rate")

            val refreshStatus = if (minRefresh.isNotEmpty() && minRefresh != "null" && maxRefresh.isNotEmpty() && maxRefresh != "null") {
                getString(R.string.status_value, "$minRefresh - $maxRefresh Hz")
            } else {
                getString(R.string.status_default)
            }

            val wmSize = runAdbCommandWithResult("wm size")
            val resStatus = if (wmSize.contains("Override size:")) {
                getString(R.string.status_value, wmSize.substringAfter("Override size:").trim())
            } else if (wmSize.contains("Physical size:")) {
                getString(R.string.status_value, wmSize.substringAfter("Physical size:").trim())
            } else {
                getString(R.string.status_default)
            }

            val networkOpt = runAdbCommandWithResult("settings get global tether_dun_required")
            val netStatus = if (networkOpt == "0") getString(R.string.status_optimized) else getString(R.string.status_default)

            val touchOpt = runAdbCommandWithResult("settings get system pointer_speed")
            val touchStatus = if (touchOpt == "7") getString(R.string.status_improved) else getString(R.string.status_default)

            val dnsMode = runAdbCommandWithResult("settings get global private_dns_mode")
            val dnsSpecifier = runAdbCommandWithResult("settings get global private_dns_specifier")

            val dnsStatus = if (dnsMode.trim() == "hostname" && dnsSpecifier.isNotEmpty() && dnsSpecifier != "null") {
                getString(R.string.status_value, dnsSpecifier.trim())
            } else {
                getString(R.string.status_default)
            }

            val animScale = runAdbCommandWithResult("settings get global window_animation_scale")
            val animStatus = if (animScale == "0.5") getString(R.string.status_anim_fast) else getString(R.string.status_default)

            val blursOpt = runAdbCommandWithResult("settings get global disable_window_blurs")
            val blursStatus = if (blursOpt == "1") getString(R.string.status_blurs_off) else getString(R.string.status_default)

            val headsUpOpt = runAdbCommandWithResult("settings get global heads_up_notifications_enabled")
            val headsUpStatus = if (headsUpOpt == "0") getString(R.string.status_dnd_blocked) else getString(R.string.status_default)

            val devOpt = runAdbCommandWithResult("settings get global development_settings_enabled")
            val devOptStatus = if (devOpt == "1") getString(R.string.status_devopt_on) else getString(R.string.status_devopt_off)

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
                    findViewById<TextView>(R.id.tvStatusCrosshair).text = getString(R.string.status_crosshair, xl(style.orEmpty()), xl(color.orEmpty()), xl(size.orEmpty()))
                } else {
                    findViewById<TextView>(R.id.tvStatusCrosshair).text = getString(R.string.status_disabled)
                }


                val isMonitorOn = prefs.getBoolean("MonitorEnabled", false)
                isMonitorEnabled = isMonitorOn
                if (isMonitorOn) {
                    findViewById<TextView>(R.id.tvStatusMonitor)?.text = getString(R.string.status_enabled)
                } else {
                    findViewById<TextView>(R.id.tvStatusMonitor)?.text = getString(R.string.status_disabled)
                }

                val isAutoOn = prefs.getBoolean(AutoClickService.KEY_ENABLED, false)
                isAutoClickEnabled = isAutoOn
                findViewById<TextView>(R.id.tvStatusAutoClick)?.text = if (isAutoOn)
                    getString(R.string.status_autoclick, AutoClickService.modeLabel(this@MainActivity, prefs.getInt(AutoClickService.KEY_MODE, 0)), prefs.getFloat(AutoClickService.KEY_INTERVAL, 5f).toString())
                else getString(R.string.status_disabled)
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
            if (prefs.getBoolean("FixedPerfAggressive", false)) {
                Toasty.info(this, getString(R.string.toast_already_aggr), Toast.LENGTH_SHORT, true).show()
                return@setOnClickListener
            }
            showFixedPerformanceSheet(prefs)
        }

        findViewById<CardView>(R.id.cardOptimizeSystem).setOnClickListener {
            val status = findViewById<TextView>(R.id.tvStatusOptimize).text.toString()
            if (status.contains(getString(R.string.w_recently))) {
                Toasty.info(this, getString(R.string.toast_already_optimized), Toast.LENGTH_SHORT, true).show()
                return@setOnClickListener
            }
            runAdbCommand("cmd activity kill-all", getString(R.string.msg_sys_optimized), showAd = true) {
                findViewById<TextView>(R.id.tvStatusOptimize).text = getString(R.string.status_recently)
            }
        }

        findViewById<CardView>(R.id.cardImproveNetwork).setOnClickListener {
            val status = findViewById<TextView>(R.id.tvStatusNetwork).text.toString()
            if (status.contains(getString(R.string.w_optimized))) {
                Toasty.info(this, getString(R.string.toast_already_optimized), Toast.LENGTH_SHORT, true).show()
                return@setOnClickListener
            }
            runAdbCommand("settings put global tether_dun_required 0", getString(R.string.msg_net_optimized), showAd = true) {
                fetchSystemStatuses()
            }
        }

        findViewById<CardView>(R.id.cardImproveTouch).setOnClickListener {
            val status = findViewById<TextView>(R.id.tvStatusTouch).text.toString()
            if (status.contains(getString(R.string.w_improved))) {
                Toasty.info(this, getString(R.string.toast_already_improved), Toast.LENGTH_SHORT, true).show()
                return@setOnClickListener
            }
            runAdbCommand("settings put system pointer_speed 7 && settings put secure long_press_timeout 250", getString(R.string.msg_touch_improved), showAd = true) {
                fetchSystemStatuses()
            }
        }

        findViewById<CardView>(R.id.cardFastAnimations).setOnClickListener {
            val status = findViewById<TextView>(R.id.tvStatusAnimations).text.toString()
            if (status.contains("0.5x")) {
                Toasty.info(this, getString(R.string.toast_already_sped), Toast.LENGTH_SHORT, true).show()
                return@setOnClickListener
            }
            runAdbCommand("settings put global window_animation_scale 0.5 && settings put global transition_animation_scale 0.5 && settings put global animator_duration_scale 0.5", getString(R.string.msg_anim_fast), showAd = true) {
                fetchSystemStatuses()
            }
        }

        findViewById<CardView>(R.id.cardDisableBlurs).setOnClickListener {
            val status = findViewById<TextView>(R.id.tvStatusBlurs).text.toString()
            if (status.contains(getString(R.string.w_disabled))) {
                Toasty.info(this, getString(R.string.toast_already_disabled), Toast.LENGTH_SHORT, true).show()
                return@setOnClickListener
            }
            runAdbCommand("settings put global disable_window_blurs 1", getString(R.string.msg_blurs_off), showAd = true) {
                fetchSystemStatuses()
            }
        }

        findViewById<CardView>(R.id.cardGamingDnd).setOnClickListener {
            val status = findViewById<TextView>(R.id.tvStatusDnd).text.toString()
            if (status.contains(getString(R.string.w_blocked))) {
                Toasty.info(this, getString(R.string.toast_already_blocked), Toast.LENGTH_SHORT, true).show()
                return@setOnClickListener
            }
            runAdbCommand("settings put global heads_up_notifications_enabled 0", getString(R.string.msg_dnd_on), showAd = true) {
                fetchSystemStatuses()
            }
        }

        findViewById<CardView>(R.id.cardDisableDevOptions).setOnClickListener {
            val status = findViewById<TextView>(R.id.tvStatusDevOptions).text.toString()
            if (status.contains(getString(R.string.w_disabled))) {
                Toasty.info(this, getString(R.string.toast_already_disabled), Toast.LENGTH_SHORT, true).show()
                return@setOnClickListener
            }
            runAdbCommand("settings put global development_settings_enabled 0", getString(R.string.msg_devopt_off), showAd = true) {
                fetchSystemStatuses()
            }
        }

        findViewById<CardView>(R.id.cardFstrim).setOnClickListener {
            handlePremiumFeature {
                val status = findViewById<TextView>(R.id.tvStatusFstrim).text.toString()
                if (status.contains(getString(R.string.w_recently))) {
                    Toasty.info(this, getString(R.string.toast_storage_already), Toast.LENGTH_SHORT, true).show()
                    return@handlePremiumFeature
                }


                runAdbCommand("sm fstrim", getString(R.string.msg_fstrim_done), showAd = true) {
                    findViewById<TextView>(R.id.tvStatusFstrim).text = getString(R.string.status_recently)
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
            findViewById<TextView>(R.id.tvStatusRamBooster).text = getString(R.string.status_ready)
            Toasty.success(this, getString(R.string.toast_status_reset), Toast.LENGTH_SHORT, true).show()
        }
        findViewById<TextView>(R.id.btnResetCache).setOnClickListener {
            findViewById<TextView>(R.id.tvStatusCache).text = getString(R.string.status_ready)
            Toasty.success(this, getString(R.string.toast_status_reset), Toast.LENGTH_SHORT, true).show()
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

        findViewById<CardView>(R.id.cardAutoClick).setOnClickListener {
            handlePremiumFeature { showAutoClickDialog() }
        }

        findViewById<TextView>(R.id.btnResetAutoClick).setOnClickListener {
            if (isAutoClickEnabled) stopAutoClickService()
        }

        findViewById<CardView>(R.id.cardThermalThrottling).setOnClickListener {
            handlePremiumFeature {
                Sheet(this)
                    .icon(R.drawable.ic_warning, Color.parseColor("#E53935"))
                    .title(getString(R.string.dlg_thermal_title))
                    .message(getString(R.string.dlg_thermal_msg))
                    .button(getString(R.string.btn_cancel), SheetStyle.TONAL)
                    .button(getString(R.string.btn_enable), SheetStyle.DANGER) {
                        runAdbCommand("cmd thermalservice override-status 0", getString(R.string.msg_thermal_off)) {
                            prefs.edit().putBoolean("ThermalDisabled", true).apply()
                            fetchSystemStatuses()
                        }
                    }
                    .show()
            }
        }

        findViewById<CardView>(R.id.cardGameMode).setOnClickListener {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                Toasty.error(this, getString(R.string.toast_needs_a12), Toast.LENGTH_LONG, true).show()
                return@setOnClickListener
            }
            handlePremiumFeature { showGameModeSelectorDialog() }
        }

        findViewById<TextView>(R.id.btnResetFixedPerf).setOnClickListener {
            runAdbCommand(fixedPerfResetCmd(prefs), getString(R.string.fp_reset_done)) {
                prefs.edit().putBoolean("FixedPerf", false).putBoolean("FixedPerfAggressive", false).apply()
                findViewById<TextView>(R.id.tvStatusFixedPerf).text = getString(R.string.status_default)
            }
        }
        findViewById<TextView>(R.id.btnResetThermal).setOnClickListener {
            runAdbCommand("cmd thermalservice reset", getString(R.string.msg_thermal_restored)) {
                prefs.edit().putBoolean("ThermalDisabled", false).apply()
                fetchSystemStatuses()
            }
        }
        findViewById<TextView>(R.id.btnResetGameMode).setOnClickListener {
            val lastGame = prefs.getString("LastGameMode", "")
            if (!lastGame.isNullOrEmpty()) {
                runAdbCommand("cmd game mode standard $lastGame", getString(R.string.msg_gm_reset)) {
                    prefs.edit().putString("LastGameMode", "").apply()
                    fetchSystemStatuses()
                }
            } else {
                Toasty.info(this, getString(R.string.toast_no_gm), Toast.LENGTH_SHORT, true).show()
            }
        }
        findViewById<TextView>(R.id.btnResetOptimize).setOnClickListener {
            findViewById<TextView>(R.id.tvStatusOptimize).text = getString(R.string.status_ready)
            Toasty.success(this, getString(R.string.toast_status_reset), Toast.LENGTH_SHORT, true).show()
        }
        findViewById<TextView>(R.id.btnResetRefresh).setOnClickListener {
            runAdbCommand("settings delete system min_refresh_rate && settings delete system peak_refresh_rate", getString(R.string.msg_refresh_reset)) { fetchSystemStatuses() }
        }
        findViewById<TextView>(R.id.btnResetNetwork).setOnClickListener {
            runAdbCommand("settings delete global tether_dun_required", getString(R.string.msg_net_reset)) { fetchSystemStatuses() }
        }
        findViewById<TextView>(R.id.btnResetTouch).setOnClickListener {
            runAdbCommand("settings delete system pointer_speed && settings put secure long_press_timeout 400", getString(R.string.msg_touch_reset)) { fetchSystemStatuses() }
        }
        findViewById<TextView>(R.id.btnResetResolution).setOnClickListener {
            runAdbCommand("wm size reset && wm density reset", getString(R.string.msg_res_reset)) { fetchSystemStatuses() }
        }
        findViewById<TextView>(R.id.btnResetDns).setOnClickListener {
            runAdbCommand("settings put global private_dns_mode default && settings delete global private_dns_specifier", getString(R.string.msg_dns_reset)) { fetchSystemStatuses() }
        }
        findViewById<TextView>(R.id.btnResetCrosshair).setOnClickListener {
            if (isCrosshairEnabled) stopCrosshairService()
        }


        findViewById<TextView>(R.id.btnResetMonitor).setOnClickListener {
            if (isMonitorEnabled) toggleRealtimeMonitor()
        }

        findViewById<TextView>(R.id.btnResetAnimations).setOnClickListener {
            runAdbCommand("settings put global window_animation_scale 1 && settings put global transition_animation_scale 1 && settings put global animator_duration_scale 1", getString(R.string.msg_anim_reset)) { fetchSystemStatuses() }
        }
        findViewById<TextView>(R.id.btnResetBlurs).setOnClickListener {
            runAdbCommand("settings put global disable_window_blurs 0", getString(R.string.msg_blurs_reset)) { fetchSystemStatuses() }
        }
        findViewById<TextView>(R.id.btnResetDnd).setOnClickListener {
            runAdbCommand("settings put global heads_up_notifications_enabled 1", getString(R.string.msg_dnd_reset)) { fetchSystemStatuses() }
        }

        findViewById<TextView>(R.id.btnResetDevOptions).setOnClickListener {
            runAdbCommand("settings put global development_settings_enabled 1", getString(R.string.msg_devopt_on)) { fetchSystemStatuses() }
        }

        findViewById<TextView>(R.id.btnResetFstrim).setOnClickListener {
            findViewById<TextView>(R.id.tvStatusFstrim).text = getString(R.string.status_ready)
            Toasty.success(this, getString(R.string.toast_status_reset), Toast.LENGTH_SHORT, true).show()
        }

        findViewById<Button>(R.id.btnLaunchGame).setOnClickListener {
            showGameLauncherDialog()
        }

        findViewById<Button>(R.id.btnRemoveAds).setOnClickListener {
            if (isPremium()) {
                Toasty.success(this, getString(R.string.toast_you_premium), Toast.LENGTH_SHORT, true).show()
            } else {
                showRemoveAdsDialog()
            }
        }

        findViewById<Button>(R.id.btnResetAll).setOnClickListener {
            Sheet(this)
                .icon(R.drawable.ic_reset, Color.parseColor("#E53935"))
                .title(getString(R.string.dlg_reset_all_title))
                .message(getString(R.string.dlg_reset_all_msg))
                .button(getString(R.string.btn_cancel), SheetStyle.TONAL)
                .button(getString(R.string.btn_reset_all), SheetStyle.DANGER) { resetAllFunctions(prefs) }
                .show()
        }
    }

    private fun resetAllFunctions(prefs: android.content.SharedPreferences) {
        run {
            val lastGame = prefs.getString("LastGameMode", "")
            val gameModeReset = if (lastGame.isNullOrEmpty()) "" else "cmd game mode standard $lastGame && "
            val aggressiveReset = if (prefs.getBoolean("FixedPerfAggressive", false))
                "{ ${fixedPerfAggressiveRestore(prefs)}; true; } && " else ""

            val resetCmd = """
                cmd power set-fixed-performance-mode-enabled false
                cmd thermalservice reset
                $gameModeReset$aggressiveReset
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

            runAdbCommand(resetCmd, getString(R.string.msg_all_reset)) {
                prefs.edit()
                    .putBoolean("FixedPerf", false)
                    .putBoolean("FixedPerfAggressive", false)
                    .putBoolean("ThermalDisabled", false)
                    .putString("LastGameMode", "")
                    .apply()


                if (isMonitorEnabled) {
                    stopService(Intent(this@MainActivity, RealtimeMonitorService::class.java))
                    isMonitorEnabled = false
                    prefs.edit().putBoolean("MonitorEnabled", false).apply()
                    findViewById<TextView>(R.id.tvStatusMonitor)?.text = getString(R.string.status_disabled)
                }

                if (isCrosshairEnabled) stopCrosshairService()
                if (isAutoClickEnabled) stopAutoClickService()

                findViewById<TextView>(R.id.tvStatusFixedPerf).text = getString(R.string.status_default)
                findViewById<TextView>(R.id.tvStatusOptimize).text = getString(R.string.status_ready)

                findViewById<TextView>(R.id.tvStatusFstrim).text = getString(R.string.status_ready)
                findViewById<TextView>(R.id.tvStatusRamBooster).text = getString(R.string.status_ready)
                findViewById<TextView>(R.id.tvStatusCache).text = getString(R.string.status_ready)

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
            .title(getString(R.string.dlg_scan_mem))
            .message(getString(R.string.dlg_scan_mem_msg))
        scanning.show(indeterminate = true)
        scanning.setIndeterminate(getString(R.string.dlg_scan_mem_sub))

        lifecycleScope.launch(Dispatchers.IO) {
            val apps = try { scanMemoryHogs() } catch (e: Exception) { emptyList() }
            withContext(Dispatchers.Main) {
                scanning.dismiss()
                if (apps.isEmpty()) {
                    Toasty.info(this@MainActivity, getString(R.string.toast_no_heavy), Toast.LENGTH_LONG, true).show()
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
            summary.text = if (selected.isEmpty()) getString(R.string.txt_nothing_selected)
            else getString(R.string.ram_summary, selected.size, mb, availRamMb())
            sheet.buttonViews.getOrNull(1)?.let {
                it.isEnabled = selected.isNotEmpty()
                it.text = if (selected.isEmpty()) getString(R.string.btn_boost) else getString(R.string.btn_boost_n, selected.size)
            }
        }

        fun render() {
            list.removeAllViews()
            apps.forEach { a ->
                list.addView(optionRow(
                    this, a.label, getString(R.string.ram_in_use, a.mb),
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
            .title(getString(R.string.dlg_ram_title))
            .message(getString(R.string.dlg_ram_msg))
            .content(summary)
            .content(list)
            .button(getString(R.string.btn_cancel), SheetStyle.TONAL)
            .button(getString(R.string.btn_boost), SheetStyle.PRIMARY) { boostApps(selected.toList()) }
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
        runAdbCommand(cmd, resources.getQuantityString(R.plurals.stopped_apps, valid.size, valid.size), showAd = true) {
            lifecycleScope.launch {
                delay(1500)
                val freed = (availRamMb() - before).coerceAtLeast(0)
                findViewById<TextView>(R.id.tvStatusRamBooster).text =
                    if (freed > 0) getString(R.string.status_freed_mb, freed) else resources.getQuantityString(R.plurals.status_stopped_apps, valid.size, valid.size)
                updateDeviceInfo()
            }
        }
    }

    private fun confirmClearCaches() {
        Sheet(this)
            .icon(R.drawable.ic_reset)
            .title(getString(R.string.dlg_clear_title))
            .message(getString(R.string.dlg_clear_msg))
            .button(getString(R.string.btn_cancel), SheetStyle.TONAL)
            .button(getString(R.string.btn_clear_caches), SheetStyle.PRIMARY) { clearAllCaches() }
            .show()
    }

    private fun clearAllCaches() {
        if (!hasShizukuPermission()) return

        val progress = ProgressSheet(this)
            .icon(R.drawable.ic_reset)
            .title(getString(R.string.dlg_clearing))
            .message(getString(R.string.dlg_clearing_msg))
        progress.show(indeterminate = true)
        progress.setIndeterminate(getString(R.string.txt_working))

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
                        if (freed > 0) getString(R.string.status_freed, formatSize(freed)) else getString(R.string.status_already_clean)
                    Toasty.success(
                        this@MainActivity,
                        if (freed > 0) getString(R.string.toast_freed_cache, formatSize(freed)) else getString(R.string.toast_cache_clean),
                        Toast.LENGTH_LONG, true
                    ).show()
                    updateDeviceInfo()
                    showInterstitialAd()
                } else {
                    Toasty.error(this@MainActivity, getString(R.string.toast_cache_blocked), Toast.LENGTH_LONG, true).show()
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
        tv.text = if (stillFrozen.isEmpty()) getString(R.string.status_ready)
        else resources.getQuantityString(R.plurals.status_frozen_apps, stillFrozen.size, stillFrozen.size)
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
            .title(getString(R.string.dlg_loading_apps))
            .message(getString(R.string.dlg_loading_apps_msg))
        scanning.show(indeterminate = true)
        scanning.setIndeterminate(getString(R.string.txt_moment))

        lifecycleScope.launch(Dispatchers.IO) {
            val apps = try { scanInstalledApps() } catch (e: Exception) { emptyList() }
            withContext(Dispatchers.Main) {
                scanning.dismiss()
                if (apps.isEmpty()) {
                    Toasty.info(this@MainActivity, getString(R.string.toast_no_apps), Toast.LENGTH_LONG, true).show()
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
            getString(R.string.tab_apps, userApps.size), getString(R.string.tab_system, systemApps.size), getString(R.string.tab_frozen, frozenApps.size)
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

        val (searchLayout, searchEdit) = styledField(this, getString(R.string.hint_search_apps), android.text.InputType.TYPE_CLASS_TEXT, topMarginDp = 10)
        val warning = infoCard(
            this,
            getString(R.string.txt_sys_warning),
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
                selected.isEmpty() -> if (tab == 2) getString(R.string.btn_restore) else getString(R.string.btn_freeze)
                tab == 2 -> getString(R.string.btn_restore_n, selected.size)
                else -> getString(R.string.btn_freeze_n, selected.size)
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
                    query.isNotBlank() -> getString(R.string.txt_no_match)
                    tab == 2 -> getString(R.string.txt_nothing_frozen)
                    else -> getString(R.string.txt_no_apps_list)
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
                    if (a.isSystem) getString(R.string.sys_app_pkg, a.pkg) else a.pkg,
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
                    text = getString(R.string.showing_n, shown.size, rows.size)
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
                    .title(getString(R.string.dlg_freeze_sys_title))
                    .message(
                        resources.getQuantityString(R.plurals.sys_selected, sys.size, sys.size) +
                            sys.take(8).joinToString("\n") { "• ${it.label}" } +
                            (if (sys.size > 8) getString(R.string.more_n, sys.size - 8) else "") +
                            getString(R.string.txt_freeze_sys_tail)
                    )
                    .button(getString(R.string.btn_cancel), SheetStyle.TONAL)
                    .button(getString(R.string.btn_freeze_anyway), SheetStyle.DANGER) {
                        sheet.dismiss()
                        applyFreezeChange(picked, freeze = true)
                    }
                    .show()
            }
        }

        sheet.icon(R.drawable.ic_freeze)
            .title(getString(R.string.dlg_freeze_apps))
            .message(getString(R.string.dlg_freeze_apps_msg))
            .content(tabs)
            .content(searchLayout)
            .content(warning)
            .content(list)
            .button(getString(R.string.btn_close), SheetStyle.TONAL)
            .button(getString(R.string.btn_freeze), SheetStyle.PRIMARY, dismiss = false) { doChange() }
            .button(getString(R.string.btn_restore_all_frozen), SheetStyle.TEXT, dismiss = false) {
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
            .title(if (freeze) getString(R.string.dlg_freezing) else getString(R.string.dlg_restoring))
            .message(getString(R.string.txt_please_wait))
        progress.show()
        val userId = android.os.Process.myUid() / 100000
        val before = availRamMb()

        lifecycleScope.launch(Dispatchers.IO) {
            val saved = savedFrozen()
            var ok = 0
            val failed = mutableListOf<String>()
            valid.forEachIndexed { i, p ->
                withContext(Dispatchers.Main) {
                    progress.update((i * 100) / valid.size, getString(R.string.progress_n_of, i + 1, valid.size))
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
                        resources.getQuantityString(R.plurals.froze_apps, ok, ok) + if (freed > 0) " · " + getString(R.string.freed_suffix, freed) else ""
                    } else resources.getQuantityString(R.plurals.restored_apps, ok, ok)
                    Toasty.success(this@MainActivity, msg, Toast.LENGTH_LONG, true).show()
                }
                if (failed.isNotEmpty()) {
                    Toasty.warning(
                        this@MainActivity,
                        resources.getQuantityString(R.plurals.failed_apps, failed.size, failed.size),
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
            Toasty.info(this, getString(R.string.toast_no_frozen), Toast.LENGTH_SHORT, true).show()
            return
        }
        if (!hasShizukuPermission()) return
        applyFreezeChange(saved, freeze = false)
    }

    private fun showAutoClickDialog() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            Toasty.info(this, getString(R.string.toast_allow_overlay), Toast.LENGTH_LONG, true).show()
            return
        }
        if (!hasShizukuPermission()) {
            Toasty.error(this, getString(R.string.toast_start_shizuku), Toast.LENGTH_LONG, true).show()
            return
        }

        val prefs = getSharedPreferences("BenimaruPrefs", Context.MODE_PRIVATE)
        var mode = prefs.getInt(AutoClickService.KEY_MODE, AutoClickService.MODE_TAP)
        val modes = listOf(getString(R.string.mode_click) to getString(R.string.mode_click_desc), getString(R.string.mode_swipe) to getString(R.string.mode_swipe_desc), getString(R.string.mode_both) to getString(R.string.mode_both_desc))

        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val rows = mutableListOf<LinearLayout>()
        fun renderModes() {
            rows.forEach { content.removeView(it) }
            rows.clear()
            modes.forEachIndexed { i, (t, sub) ->
                val row = optionRow(this, t, sub, selected = i == mode) { mode = i; renderModes() }
                content.addView(row, i)
                rows += row
            }
        }
        renderModes()

        val (intervalLayout, intervalEdit) = styledField(this, getString(R.string.hint_interval), InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        intervalEdit.setText(prefs.getFloat(AutoClickService.KEY_INTERVAL, 5f).toString())
        val (xLayout, xEdit) = styledField(this, getString(R.string.hint_x), InputType.TYPE_CLASS_NUMBER)
        val (yLayout, yEdit) = styledField(this, getString(R.string.hint_y), InputType.TYPE_CLASS_NUMBER)
        val (lenLayout, lenEdit) = styledField(this, getString(R.string.hint_swipe_len), InputType.TYPE_CLASS_NUMBER)
        prefs.getInt(AutoClickService.KEY_X, -1).takeIf { it >= 0 }?.let { xEdit.setText(it.toString()) }
        prefs.getInt(AutoClickService.KEY_Y, -1).takeIf { it >= 0 }?.let { yEdit.setText(it.toString()) }
        lenEdit.setText(prefs.getInt(AutoClickService.KEY_SWIPE_LEN, 300).toString())
        listOf(intervalLayout, xLayout, yLayout, lenLayout).forEach { content.addView(it) }
        content.addView(infoCard(this, getString(R.string.info_autoclick)))

        Sheet(this)
            .icon(R.drawable.ic_gamepad)
            .title(getString(R.string.dlg_autoclick_title))
            .message(getString(R.string.dlg_autoclick_msg))
            .content(content)
            .button(getString(R.string.btn_cancel), SheetStyle.TEXT)
            .button(getString(R.string.btn_show_button), SheetStyle.PRIMARY) {
                val interval = (intervalEdit.text?.toString()?.toFloatOrNull() ?: 5f).coerceIn(0.05f, 3600f)
                prefs.edit()
                    .putInt(AutoClickService.KEY_MODE, mode)
                    .putFloat(AutoClickService.KEY_INTERVAL, interval)
                    .putInt(AutoClickService.KEY_X, xEdit.text?.toString()?.toIntOrNull() ?: -1)
                    .putInt(AutoClickService.KEY_Y, yEdit.text?.toString()?.toIntOrNull() ?: -1)
                    .putInt(AutoClickService.KEY_SWIPE_LEN, lenEdit.text?.toString()?.toIntOrNull() ?: 300)
                    .putBoolean(AutoClickService.KEY_ENABLED, true)
                    .apply()
                startService(Intent(this, AutoClickService::class.java))
                isAutoClickEnabled = true
                findViewById<TextView>(R.id.tvStatusAutoClick).text = getString(R.string.status_autoclick, AutoClickService.modeLabel(this, mode), interval.toString())
                Toasty.success(this, getString(R.string.toast_float_ready), Toast.LENGTH_SHORT, true).show()
            }
            .show()
    }

    private fun stopAutoClickService() {
        stopService(Intent(this, AutoClickService::class.java))
        isAutoClickEnabled = false
        getSharedPreferences("BenimaruPrefs", Context.MODE_PRIVATE).edit()
            .putBoolean(AutoClickService.KEY_ENABLED, false).apply()
        findViewById<TextView>(R.id.tvStatusAutoClick).text = getString(R.string.status_disabled)
    }

    private fun toggleRealtimeMonitor() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
            Toasty.info(this, getString(R.string.toast_allow_overlay), Toast.LENGTH_LONG, true).show()
            return
        }

        val intent = Intent(this, RealtimeMonitorService::class.java)
        val prefs = getSharedPreferences("BenimaruPrefs", Context.MODE_PRIVATE)

        if (isMonitorEnabled) {
            stopService(intent)
            isMonitorEnabled = false
            prefs.edit().putBoolean("MonitorEnabled", false).apply()
            findViewById<TextView>(R.id.tvStatusMonitor).text = getString(R.string.status_disabled)
            Toasty.success(this, getString(R.string.toast_monitor_off), Toast.LENGTH_SHORT, true).show()
        } else {
            startService(intent)
            isMonitorEnabled = true
            prefs.edit().putBoolean("MonitorEnabled", true).apply()
            findViewById<TextView>(R.id.tvStatusMonitor).text = getString(R.string.status_enabled)
            Toasty.success(this, getString(R.string.toast_monitor_on), Toast.LENGTH_SHORT, true).show()
            showInterstitialAd()
        }
    }

    private fun showResolutionModeDialog() {
        val sheet = Sheet(this)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        list.addView(optionRow(
            this, getString(R.string.res_direct), getString(R.string.res_direct_desc),
            icon = Ui.drawable(this, R.drawable.ic_tune)
        ) {
            sheet.dismiss()
            showResolutionDialog()
        })
        list.addView(optionRow(
            this, getString(R.string.res_floating), getString(R.string.res_floating_desc),
            icon = Ui.drawable(this, R.drawable.ic_play)
        ) {
            sheet.dismiss()
            launchFloatingResolution()
        })
        sheet.icon(R.drawable.ic_tune)
            .title(getString(R.string.dlg_change_res))
            .message(getString(R.string.dlg_change_res_msg))
            .content(list)
            .button(getString(R.string.btn_cancel), SheetStyle.TEXT)
            .show()
    }

    private fun launchFloatingResolution() {
        if (!android.provider.Settings.canDrawOverlays(this)) {
            val intent = Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
            Toasty.info(this, getString(R.string.toast_allow_overlay), Toast.LENGTH_LONG, true).show()
        } else {
            startService(Intent(this, FloatingResolutionService::class.java))
            Toasty.success(this, getString(R.string.toast_float_enabled), Toast.LENGTH_SHORT, true).show()
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
            text = getString(R.string.btn_login)
            isAllCaps = false
            id = View.generateViewId()
            layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f)
        }
        val buyTab = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = getString(R.string.btn_get_premium)
            isAllCaps = false
            id = View.generateViewId()
            layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f)
        }
        toggle.addView(loginTab)
        toggle.addView(buyTab)

        val benefits = infoCard(
            this,
            getString(R.string.premium_benefits),
            gold
        )

        val (userLayout, userEdit) = styledField(this, getString(R.string.hint_username), InputType.TYPE_CLASS_TEXT)
        val (passLayout, passEdit) = styledField(
            this, getString(R.string.hint_password),
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
            this, getString(R.string.hint_device_id), deviceId,
            icon = Ui.drawable(this, R.drawable.ic_lock)
        ) {
            val cb = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cb.setPrimaryClip(android.content.ClipData.newPlainText(getString(R.string.hint_device_id), deviceId))
            Toasty.success(this, getString(R.string.toast_device_id_copied), Toast.LENGTH_SHORT, true).show()
        }

        val note = infoCard(
            this,
            getString(R.string.txt_password_note)
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
            .message(getString(R.string.txt_login_hint))
            .content(toggle)
            .content(form)


        fun readFields(forPurchase: Boolean): Pair<String, String>? {
            val user = userEdit.text.toString().trim()
            val pass = passEdit.text.toString()
            if (user.isEmpty() || pass.isEmpty()) {
                showError(getString(R.string.err_enter_user_pass))
                return null
            }
            if (forPurchase) {
                if (!Regex("^[A-Za-z0-9_.-]{3,32}\$").matches(user)) {
                    showError(getString(R.string.err_username_format))
                    return null
                }
                if (pass.length < 6) {
                    showError(getString(R.string.err_password_short))
                    return null
                }
            }
            showError(null)
            return user to pass
        }


        sheet.button(getString(R.string.btn_login), SheetStyle.PRIMARY, dismiss = false) { btn ->
            val (user, pass) = readFields(false) ?: return@button
            btn.isEnabled = false
            btn.text = getString(R.string.txt_verifying)
            verifyPremiumAccount(user, pass) { ok, message ->
                btn.isEnabled = true
                btn.text = getString(R.string.btn_login)
                if (ok) sheet.dismiss() else showError(message)
            }
        }

        sheet.button(getString(R.string.btn_buy_telegram), SheetStyle.PRIMARY, dismiss = false) { btn ->
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
                Toasty.info(this@MainActivity, getString(R.string.toast_account_copied), Toast.LENGTH_LONG, true).show()
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/benimarux1k")))
            }
        }
        sheet.button(getString(R.string.btn_close), SheetStyle.TEXT)
        sheet.show()

        fun applyMode() {
            sheet.messageView?.text = if (buyMode)
                getString(R.string.txt_buy_hint)
            else
                getString(R.string.txt_login_hint)
            benefits.visibility = if (buyMode) View.VISIBLE else View.GONE
            sheet.buttonViews[0].visibility = if (buyMode) View.GONE else View.VISIBLE
            sheet.buttonViews[1].visibility = if (buyMode) View.VISIBLE else View.GONE
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
            var verifiedHash = ""
            var reason = getString(R.string.err_invalid_login)
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
                            reason = getString(R.string.err_device_limit)
                        } else {
                            var deviceFound = false
                            if (devices != null) {
                                for (j in 0 until devices.length()) {
                                    if (devices.getString(j) == deviceId) { deviceFound = true; break }
                                }
                            }
                            if (deviceFound) {
                                ok = true
                                verifiedHash = hash
                            }
                            else reason = getString(R.string.err_not_authorized, deviceId)
                        }
                        break
                    }
                } else {
                    reason = getString(R.string.err_server, conn.responseCode)
                }
                conn.disconnect()
            } catch (e: Exception) {
                reason = getString(R.string.err_network)
            }

            withContext(Dispatchers.Main) {
                if (ok) {
                    getSharedPreferences("BenimaruPrefs", Context.MODE_PRIVATE).edit()
                        .putBoolean("AdsRemoved", true)
                        .putString("PremiumUser", username.trim())
                        .putString("PremiumHash", verifiedHash)
                        .putLong("PremiumLastVerified", System.currentTimeMillis())
                        .apply()
                    findViewById<LinearLayout>(R.id.bannerAdContainer).removeAllViews()
                    updatePremiumButtonUI()
                    Toasty.success(this@MainActivity, getString(R.string.toast_premium_activated), Toast.LENGTH_LONG, true).show()
                }
                onDone(ok, reason)
            }
        }
    }

    private val fpAggressiveOn =
        "settings put global low_power 0; " +
        "cmd power set-adaptive-power-saver-enabled false; " +
        "settings put global adaptive_battery_management_enabled 0; " +
        "dumpsys deviceidle disable all"

    private fun fixedPerfAggressiveRestore(prefs: android.content.SharedPreferences): String {
        val prev = prefs.getString("FPPrevAdaptiveBattery", null)
        val battery = if (prev == "0" || prev == "1")
            "settings put global adaptive_battery_management_enabled $prev"
        else "settings delete global adaptive_battery_management_enabled"
        return "cmd power set-adaptive-power-saver-enabled true; $battery; dumpsys deviceidle enable all"
    }

    private fun fixedPerfResetCmd(prefs: android.content.SharedPreferences): String {
        val base = "cmd power set-fixed-performance-mode-enabled false"
        if (!prefs.getBoolean("FixedPerfAggressive", false)) return base
        return fixedPerfAggressiveRestore(prefs) + "; " + base
    }

    private fun showFixedPerformanceSheet(prefs: android.content.SharedPreferences) {
        val standardOn = prefs.getBoolean("FixedPerf", false)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val sheet = Sheet(this)
            .icon(R.drawable.ic_game)
            .title(getString(R.string.fp_title))
            .message(getString(R.string.fp_message))
        if (!standardOn) {
            list.addView(optionRow(this, getString(R.string.fp_standard), getString(R.string.fp_standard_desc)) {
                sheet.dismiss()
                applyFixedPerformance(prefs, aggressive = false)
            })
        }
        list.addView(optionRow(this, getString(R.string.fp_aggressive), getString(R.string.fp_aggressive_desc)) {
            sheet.dismiss()
            applyFixedPerformance(prefs, aggressive = true)
        })
        sheet.content(list).button(getString(R.string.btn_cancel), SheetStyle.TEXT).show()
    }

    private fun applyFixedPerformance(prefs: android.content.SharedPreferences, aggressive: Boolean) {
        if (!hasShizukuPermission()) return
        lifecycleScope.launch(Dispatchers.IO) {
            if (aggressive && !prefs.getBoolean("FixedPerfAggressive", false)) {
                val prev = runAdbCommandWithResult("settings get global adaptive_battery_management_enabled").trim()
                prefs.edit().putString("FPPrevAdaptiveBattery", prev).apply()
            }
            val main = "cmd power set-fixed-performance-mode-enabled true"
            val cmd = if (aggressive) "$fpAggressiveOn; $main" else main
            withContext(Dispatchers.Main) {
                runAdbCommand(
                    cmd,
                    getString(if (aggressive) R.string.fp_done_aggressive else R.string.fp_done_standard),
                    showAd = true
                ) {
                    prefs.edit().putBoolean("FixedPerf", true).putBoolean("FixedPerfAggressive", aggressive).apply()
                    findViewById<TextView>(R.id.tvStatusFixedPerf).text =
                        getString(if (aggressive) R.string.status_enabled_aggr else R.string.status_enabled)
                }
            }
        }
    }


    private fun xl(v: String): String {
        val id = when (v) {
            "Cross" -> R.string.xl_cross
            "Dot" -> R.string.xl_dot
            "Circle" -> R.string.xl_circle
            "Cross with Circle" -> R.string.xl_cross_circle
            "Square" -> R.string.xl_square
            "Target" -> R.string.xl_target
            "Gap Cross" -> R.string.xl_gap_cross
            "Diamond" -> R.string.xl_diamond
            "Triangle" -> R.string.xl_triangle
            "White" -> R.string.xl_white
            "Black" -> R.string.xl_black
            "Red" -> R.string.xl_red
            "Green" -> R.string.xl_green
            "Blue" -> R.string.xl_blue
            "Yellow" -> R.string.xl_yellow
            "Cyan" -> R.string.xl_cyan
            "Magenta" -> R.string.xl_magenta
            "Tiny" -> R.string.xl_tiny
            "Small" -> R.string.xl_small
            "Medium" -> R.string.xl_medium
            "Large" -> R.string.xl_large
            "Extra Large" -> R.string.xl_xlarge
            else -> return v
        }
        return getString(id)
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
                        Toasty.error(this@MainActivity, getString(R.string.toast_cmd_failed, exitCode), Toast.LENGTH_SHORT, true).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toasty.error(this@MainActivity, getString(R.string.toast_error, e.message ?: ""), Toast.LENGTH_SHORT, true).show()
                }
            }
        }
    }

    private fun showDnsDialog() {
        data class Dns(val name: String, val desc: String, val host: String)
        val providers = listOf(
            Dns("Control D", getString(R.string.dns_blocks_ads), "p2.freedns.controld.com"),
            Dns("Cloudflare", getString(R.string.dns_fastest), "one.one.one.one"),
            Dns("Quad9", getString(R.string.dns_malicious), "dns.quad9.net"),
            Dns("NextDNS", getString(R.string.dns_personalized), "dns.nextdns.io"),
            Dns("Google", getString(R.string.dns_stable), "dns.google")
        )
        val current = findViewById<TextView>(R.id.tvStatusDns).text.toString()

        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val sheet = Sheet(this)
            .icon(R.drawable.ic_lock)
            .title(getString(R.string.dlg_private_dns))
            .message(getString(R.string.dlg_private_dns_msg))

        providers.forEach { p ->
            list.addView(optionRow(this, p.name, "${p.desc}  ·  ${p.host}", selected = current.contains(p.host)) {
                sheet.dismiss()
                val cmd = "settings put global private_dns_mode hostname && settings put global private_dns_specifier ${p.host}"
                runAdbCommand(cmd, getString(R.string.toast_dns_set, p.name), showAd = true) { fetchSystemStatuses() }
            })
        }
        list.addView(optionRow(this, getString(R.string.w_default), getString(R.string.dns_off_desc), selected = current.contains(getString(R.string.w_default))) {
            sheet.dismiss()
            val cmd = "settings put global private_dns_mode default && settings delete global private_dns_specifier"
            runAdbCommand(cmd, getString(R.string.msg_dns_default), showAd = false) { fetchSystemStatuses() }
        })

        sheet.content(list).button(getString(R.string.btn_cancel), SheetStyle.TEXT).show()
    }

    private fun showRefreshRateDialog() {
        val (minLayout, minInput) = styledField(this, getString(R.string.hint_min_hz), InputType.TYPE_CLASS_NUMBER, topMarginDp = 0)
        val (maxLayout, maxInput) = styledField(this, getString(R.string.hint_max_hz), InputType.TYPE_CLASS_NUMBER, topMarginDp = 0)

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
            .title(getString(R.string.dlg_refresh))
            .message(getString(R.string.dlg_refresh_msg))
            .content(presets)
            .content(fields)
            .button(getString(R.string.btn_cancel), SheetStyle.TONAL)
            .button(getString(R.string.btn_apply), SheetStyle.PRIMARY, dismiss = false) {
                val min = minInput.text.toString().trim().toIntOrNull()
                val max = maxInput.text.toString().trim().toIntOrNull()
                when {
                    min == null || max == null -> Toasty.warning(this, getString(R.string.toast_valid_numbers), Toast.LENGTH_SHORT, true).show()
                    min !in 30..240 || max !in 30..240 -> Toasty.warning(this, getString(R.string.toast_hz_range), Toast.LENGTH_SHORT, true).show()
                    min > max -> Toasty.warning(this, getString(R.string.toast_min_gt_max), Toast.LENGTH_SHORT, true).show()
                    else -> {
                        sheet.dismiss()
                        val cmd = "settings put system min_refresh_rate $min && settings put system peak_refresh_rate $max"
                        runAdbCommand(cmd, getString(R.string.toast_hz_set, min, max), showAd = true) { fetchSystemStatuses() }
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
            text = getString(R.string.sw_aspect)
            isChecked = false
        }
        val autoDpiSwitch = MaterialSwitch(this).apply {
            text = getString(R.string.sw_dpi)
            isChecked = true
        }
        val (widthLayout, widthInput) = styledField(this, getString(R.string.hint_width_now, currentWidth.toInt()), InputType.TYPE_CLASS_NUMBER, topMarginDp = 0)
        val (heightLayout, heightInput) = styledField(this, getString(R.string.hint_height_now, currentHeight.toInt()), InputType.TYPE_CLASS_NUMBER, topMarginDp = 0)

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
            ).apply { bottomMargin = dp(4) })
            addView(autoDpiSwitch, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) })
            addView(fields)
            addView(infoCard(this@MainActivity, getString(R.string.info_dpi)),
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = dp(14) })
        }

        val sheet = Sheet(this)
        sheet.icon(R.drawable.ic_tune)
            .title(getString(R.string.dlg_change_res))
            .content(content)
            .button(getString(R.string.btn_cancel), SheetStyle.TONAL)
            .button(getString(R.string.btn_preview), SheetStyle.PRIMARY, dismiss = false) {
                val newWidth = widthInput.text.toString().trim().toIntOrNull()
                val newHeight = heightInput.text.toString().trim().toIntOrNull()
                if (newWidth == null || newHeight == null) {
                    Toasty.warning(this, getString(R.string.toast_enter_wh), Toast.LENGTH_SHORT, true).show()
                    return@button
                }
                if (newWidth < 300 || newHeight < 300) {
                    Toasty.warning(this, getString(R.string.toast_res_small), Toast.LENGTH_SHORT, true).show()
                    return@button
                }
                if (!hasShizukuPermission()) return@button
                sheet.dismiss()

                val cmd = if (autoDpiSwitch.isChecked) {
                    val scalingRatio = minOf(newWidth / currentWidth, newHeight / currentHeight)
                    val newDpi = (scalingRatio * currentDpi).toInt()
                    "wm size ${newWidth}x${newHeight} && wm density $newDpi"
                } else {
                    "wm size ${newWidth}x${newHeight}"
                }

                lifecycleScope.launch(Dispatchers.IO) {
                    val process = Shizuku.newProcess(arrayOf("sh", "-c", cmd), null, null)
                    if (process.waitFor() == 0) {
                        withContext(Dispatchers.Main) { showResolutionPreviewCountdown() }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toasty.error(this@MainActivity, getString(R.string.toast_res_fail), Toast.LENGTH_SHORT, true).show()
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
            .title(getString(R.string.dlg_keep_res))
            .message(getString(R.string.dlg_keep_res_msg))
            .content(bar)
            .cancelable(false)
            .button(getString(R.string.btn_revert), SheetStyle.TONAL) {
                decided = true
                timerJob?.cancel()
                runAdbCommand("wm size reset && wm density reset", getString(R.string.msg_res_reverted)) { fetchSystemStatuses() }
            }
            .button(getString(R.string.btn_keep), SheetStyle.PRIMARY) {
                decided = true
                timerJob?.cancel()
                Toasty.success(this, getString(R.string.toast_res_saved), Toast.LENGTH_SHORT, true).show()
                fetchSystemStatuses()
                showInterstitialAd()
            }
            .show()

        timerJob = lifecycleScope.launch(Dispatchers.Main) {
            for (step in 60 downTo 0) {
                bar.progress = step * 100
                sheet.messageView?.text = getString(R.string.revert_n, (step + 9) / 10)
                delay(100)
            }
            if (!decided) {
                decided = true
                sheet.dismiss()
                runAdbCommand("wm size reset && wm density reset", getString(R.string.msg_res_auto_reverted)) { fetchSystemStatuses() }
            }
        }
    }

    private fun showCrosshairConfigDialog() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
            Toasty.info(this, getString(R.string.toast_allow_overlay_cross), Toast.LENGTH_LONG, true).show()
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
                text = xl(name)
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


        val swatchRow = colorSwatchRow(this, colorValues, colors.map { xl(it) }, colorIdx) { i ->
            colorIdx = i
            refresh()
        }


        val sizeLabel = sectionLabel(getString(R.string.size_label, xl(sizes[sizeIdx])))
        val slider = com.google.android.material.slider.Slider(this).apply {
            valueFrom = 0f
            valueTo = (sizes.size - 1).toFloat()
            stepSize = 1f
            value = sizeIdx.toFloat()
            setLabelFormatter { v -> xl(sizes[v.toInt().coerceIn(sizes.indices)]) }
            addOnChangeListener { _, v, fromUser ->
                if (fromUser) {
                    sizeIdx = v.toInt()
                    sizeLabel.text = getString(R.string.size_label, xl(sizes[sizeIdx]))
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
            addView(sectionLabel(getString(R.string.lbl_shape)))
            addView(hScroll)
            addView(sectionLabel(getString(R.string.lbl_color)))
            addView(swatchRow)
            addView(sizeLabel)
            addView(slider)
        }
        preview.set(styles[styleIdx], colors[colorIdx], sizes[sizeIdx])

        val sheet = Sheet(this)
        sheet.icon(R.drawable.ic_tune)
            .title(getString(R.string.dlg_crosshair))
            .message(getString(R.string.dlg_crosshair_msg))
            .content(content)
            .button(getString(R.string.btn_cancel), SheetStyle.TONAL)
            .button(if (wasEnabled) getString(R.string.btn_save) else getString(R.string.btn_start), SheetStyle.PRIMARY) {
                saved = true
                prefs.edit()
                    .putInt("CrosshairStyleIdx", styleIdx)
                    .putInt("CrosshairColorIdx", colorIdx)
                    .putInt("CrosshairSizeIdx", sizeIdx)
                    .apply()
                startCrosshairService(styles[styleIdx], colors[colorIdx], sizes[sizeIdx])
            }
        if (wasEnabled) {
            sheet.button(getString(R.string.btn_turn_off), SheetStyle.DANGER) {
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
            putExtra(getString(R.string.lbl_color), color)
            putExtra("SIZE", size)
        })
    }

    private fun startCrosshairService(style: String, color: String, size: String) {
        val intent = Intent(this, CrosshairService::class.java).apply {
            putExtra("STYLE", style)
            putExtra(getString(R.string.lbl_color), color)
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

        findViewById<TextView>(R.id.tvStatusCrosshair).text = getString(R.string.status_crosshair, xl(style.orEmpty()), xl(color.orEmpty()), xl(size.orEmpty()))
        Toasty.success(this, getString(R.string.toast_cross_updated), Toast.LENGTH_SHORT, true).show()
        showInterstitialAd()
    }

    private fun stopCrosshairService() {
        val intent = Intent(this, CrosshairService::class.java)
        stopService(intent)

        isCrosshairEnabled = false

        getSharedPreferences("BenimaruPrefs", Context.MODE_PRIVATE).edit()
            .putBoolean("CrosshairEnabled", false)
            .apply()

        findViewById<TextView>(R.id.tvStatusCrosshair).text = getString(R.string.status_disabled)
        Toasty.success(this, getString(R.string.toast_cross_off), Toast.LENGTH_SHORT, true).show()
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

    private fun showGameModeSelectorDialog() {
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
                            Toasty.success(this@MainActivity, getString(R.string.toast_gm_enabled, appName), Toast.LENGTH_SHORT, true).show()
                            getSharedPreferences("BenimaruPrefs", Context.MODE_PRIVATE).edit().putString("LastGameMode", pkgName).apply()
                            fetchSystemStatuses()
                            showInterstitialAd()
                        } else {
                            Toasty.warning(this@MainActivity, getString(R.string.toast_gm_blocked), Toast.LENGTH_LONG, true).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toasty.error(this@MainActivity, getString(R.string.toast_error, e.message ?: ""), Toast.LENGTH_SHORT, true).show()
                    }
                }
            }
        }
    }

    private fun showGameLauncherDialog() {
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
                                Toasty.success(this@MainActivity, getString(R.string.toast_optimized, appName), Toast.LENGTH_SHORT, true).show()
                                showInterstitialAd { launchGame() }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                progress.dismiss()
                                Toasty.error(this@MainActivity, getString(R.string.toast_opt_failed, e.message ?: ""), Toast.LENGTH_LONG, true).show()
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

    private fun showShizukuRequiredDialog() {
        if (isFinishing || isDestroyed) return
        Sheet(this)
            .icon(R.drawable.ic_download)
            .title(getString(R.string.dlg_shizuku_required))
            .message(getString(R.string.dlg_shizuku_required_msg))
            .cancelable(false)
            .button(getString(R.string.btn_exit), SheetStyle.TONAL) { finishAffinity() }
            .button(getString(R.string.btn_download), SheetStyle.PRIMARY) { downloadShizukuApk() }
            .show()
    }

    private fun downloadShizukuApk() {
        downloadAndInstall(
            getString(R.string.dl_shizuku),
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
        val gamingCards = listOf(R.id.cardImproveTouch, R.id.cardGamingDnd, R.id.cardCrosshair, R.id.cardRealtimeMonitor, R.id.cardAutoClick, R.id.cardGameMode)

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
            R.id.tvStatusRamBooster, R.id.tvStatusCache, R.id.tvStatusFreeze, R.id.tvStatusAutoClick
        )
        val states = HashMap<Int, TweakState>()
        val green = Color.parseColor("#2E9E5B")
        val amber = Color.parseColor("#E09B00")

        fun stateOf(id: Int, text: String): TweakState {
            val t = text.removePrefix("Status:").trim()
            return when {
                t.isEmpty() || t.startsWith(getString(R.string.w_default)) || t.startsWith(getString(R.string.w_ready)) ||
                    t.startsWith(getString(R.string.w_unknown)) || t.startsWith(getString(R.string.w_checking)) -> TweakState.NEUTRAL
                id == R.id.tvStatusDevOptions -> if (t.startsWith(getString(R.string.w_enabled))) TweakState.WARN else TweakState.ACTIVE
                id == R.id.tvStatusThermal -> TweakState.WARN
                id == R.id.tvStatusBlurs -> if (t.startsWith(getString(R.string.w_disabled))) TweakState.ACTIVE else TweakState.NEUTRAL
                t.startsWith(getString(R.string.w_disabled)) -> TweakState.NEUTRAL
                else -> TweakState.ACTIVE
            }
        }

        fun refresh() {
            val active = states.values.count { it != TweakState.NEUTRAL }
            findViewById<TextView>(R.id.tvActiveSummary)?.text = getString(R.string.summary_active, active, statusIds.size)
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
        tvShizuku.text = if (isConnected) "Shizuku" else getString(R.string.shizuku_offline)
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
                Toasty.success(this, getString(R.string.toast_shizuku_ok), Toast.LENGTH_SHORT, true).show()
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