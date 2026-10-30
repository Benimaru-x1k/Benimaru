package com.benimaru.official

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.unity3d.services.banners.BannerView
import es.dmoral.toasty.Toasty
import java.io.File
import rikka.shizuku.Shizuku

internal const val PICKER_SHELL = "shell"
internal const val PICKER_APK = "apk"

class MainActivity : AppCompatActivity() {


    internal val SHIZUKU_REQUEST_CODE = 100
    internal var pendingApk: File? = null
    internal var shizukuConnected = false

    @Volatile internal var rewardedReady = false
    internal var bannerView: BannerView? = null

    internal var isCrosshairEnabled = false
    internal var isMonitorEnabled = false
    internal var isAutoClickEnabled = false
    internal var overlayGraceUntil = 0L

    internal var lastPremiumRecheckAt = 0L
    internal var lastSalesFlagCheckAt = 0L
    internal var isRecheckingPremium = false

    internal var onScriptPicked: ((Uri?) -> Unit)? = null
    internal var pickerKind: String? = null
    internal val scriptPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val cb = onScriptPicked
        val kind = pickerKind
        onScriptPicked = null
        pickerKind = null
        if (cb != null) {
            cb.invoke(uri)
        } else if (uri != null) {
            when (kind) {
                PICKER_SHELL -> showShellExecuteSheet(uri)
                PICKER_APK -> showApkInstallSheet(uri)
            }
        }
    }

    internal var lastInterstitialAt = 0L
    internal var interstitialEligibleActions = 0

    internal val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
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


    override fun getResources(): Resources = AppTranslator.wrap(super.getResources())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pickerKind = savedInstanceState?.getString("pickerKind")
        AppTranslator.init(applicationContext)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        AppTranslator.translateTree(findViewById(android.R.id.content))

        val isNightMode = setupWindowAppearance()
        setupThemeToggle(isNightMode)

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
        refreshOverlayStatuses()
        setupLanguageIfNeeded()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("pickerKind", pickerKind)
    }

    override fun onResume() {
        super.onResume()
        updateDeviceInfo()
        updatePremiumButtonUI()
        recheckPremiumIfNeeded()
        refreshFreezeStatus()
        refreshShizukuSilently()
        refreshOverlayStatuses()
        refreshPremiumSalesFlag()

        val apk = pendingApk
        if (apk != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && packageManager.canRequestPackageInstalls()) {
            pendingApk = null
            installApk(apk, false)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bannerView?.destroy()
        bannerView = null
        Shizuku.removeRequestPermissionResultListener(permissionListener)
    }


    private fun setupWindowAppearance(): Boolean {
        val isNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isNightMode
            isAppearanceLightNavigationBars = !isNightMode
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        return isNightMode
    }

    private fun setupThemeToggle(isNightMode: Boolean) {
        val prefs = getSharedPreferences(Prefs.THEME, Context.MODE_PRIVATE)
        val btnThemeToggle = findViewById<ImageView>(R.id.btnThemeToggle)

        if (isNightMode) {
            btnThemeToggle.setImageResource(R.drawable.ic_light_mode)
        } else {
            btnThemeToggle.setImageResource(R.drawable.ic_dark_mode)
        }

        btnThemeToggle.setOnClickListener {
            if (isNightMode) {
                prefs.edit().putBoolean(Prefs.KEY_DARK_MODE, false).apply()
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            } else {
                prefs.edit().putBoolean(Prefs.KEY_DARK_MODE, true).apply()
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }
        }
    }
}
