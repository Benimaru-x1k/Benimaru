package com.benimaru.official

import android.util.Log
import android.widget.LinearLayout
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.IUnityAdsLoadListener
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.UnityAds
import com.unity3d.ads.UnityAdsShowOptions
import com.unity3d.services.banners.BannerErrorInfo
import com.unity3d.services.banners.BannerView
import com.unity3d.services.banners.UnityBannerSize
import es.dmoral.toasty.Toasty
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "UnityAds"
private const val REWARDED_WAIT_MS = 15000
private const val REWARDED_RELOAD_CHECK_MS = 250

private fun backoffMs(attempt: Int): Long = minOf(3000L shl minOf(attempt, 4), 30000L)

private fun MainActivity.isOnline(): Boolean = try {
    val cm = getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
    val caps = cm.getNetworkCapabilities(cm.activeNetwork)
    caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
} catch (e: Exception) {
    true
}

internal fun MainActivity.initializeAds() {
    if (isPremium()) return

    if (adsInitialized) {
        loadInterstitialAd()
        loadRewardedAd()
        setupBannerAd()
        return
    }
    if (adsInitializing) return
    adsInitializing = true

    UnityAds.initialize(applicationContext, AppConfig.UNITY_GAME_ID, AppConfig.UNITY_TEST_MODE,
        object : IUnityAdsInitializationListener {
            override fun onInitializationComplete() {
                adsInitializing = false
                adsInitialized = true
                adsInitRetry = 0
                if (isFinishing || isDestroyed || isPremium()) return
                loadInterstitialAd()
                loadRewardedAd()
                setupBannerAd()
            }

            override fun onInitializationFailed(error: UnityAds.UnityAdsInitializationError?, message: String?) {
                Log.w(TAG, "Init failed: $error $message")
                adsInitializing = false
                val wait = backoffMs(adsInitRetry++)
                lifecycleScope.launch {
                    delay(wait)
                    if (!isFinishing && !isDestroyed && !adsInitialized) initializeAds()
                }
            }
        })
}

internal fun MainActivity.loadInterstitialAd() {
    if (isPremium() || !adsInitialized || interstitialLoading || interstitialReady) return
    interstitialLoading = true
    UnityAds.load(AppConfig.AD_UNIT_INTERSTITIAL, object : IUnityAdsLoadListener {
        override fun onUnityAdsAdLoaded(placementId: String?) {
            interstitialLoading = false
            interstitialReady = true
            interstitialRetry = 0
        }

        override fun onUnityAdsFailedToLoad(placementId: String?, error: UnityAds.UnityAdsLoadError?, message: String?) {
            Log.w(TAG, "Interstitial load failed: $error $message")
            interstitialLoading = false
            interstitialReady = false
            val wait = backoffMs(interstitialRetry++)
            lifecycleScope.launch {
                delay(wait)
                if (!isFinishing && !isDestroyed) loadInterstitialAd()
            }
        }
    })
}

internal fun MainActivity.loadRewardedAd() {
    if (hasPremiumFeatures() || !adsInitialized || rewardedLoading || rewardedReady) return
    rewardedLoading = true
    UnityAds.load(AppConfig.AD_UNIT_REWARDED, object : IUnityAdsLoadListener {
        override fun onUnityAdsAdLoaded(placementId: String?) {
            rewardedLoading = false
            rewardedReady = true
            rewardedRetry = 0
        }

        override fun onUnityAdsFailedToLoad(placementId: String?, error: UnityAds.UnityAdsLoadError?, message: String?) {
            Log.w(TAG, "Rewarded load failed: $error $message")
            rewardedLoading = false
            rewardedReady = false
            val wait = backoffMs(rewardedRetry++)
            lifecycleScope.launch {
                delay(wait)
                if (!isFinishing && !isDestroyed) loadRewardedAd()
            }
        }
    })
}

private fun MainActivity.finishInterstitial() {
    val callback = pendingInterstitialFinish
    pendingInterstitialFinish = null
    loadInterstitialAd()
    callback?.invoke()
}

internal fun MainActivity.shouldShowInterstitial(): Boolean {
    if (isPremium()) return false
    interstitialEligibleActions++
    if (interstitialEligibleActions < AppConfig.INTERSTITIAL_ACTIONS_BEFORE_FIRST) return false
    val now = android.os.SystemClock.elapsedRealtime()
    return lastInterstitialAt == 0L || now - lastInterstitialAt >= AppConfig.INTERSTITIAL_COOLDOWN_MS
}

internal fun MainActivity.showInterstitialAd(onFinished: () -> Unit = {}) {
    if (isFinishing || isDestroyed || !shouldShowInterstitial()) {
        onFinished()
        return
    }
    if (!interstitialReady) {
        loadInterstitialAd()
        onFinished()
        return
    }
    pendingInterstitialFinish = onFinished
    lastInterstitialAt = android.os.SystemClock.elapsedRealtime()
    interstitialReady = false
    UnityAds.show(this, AppConfig.AD_UNIT_INTERSTITIAL, UnityAdsShowOptions(), object : IUnityAdsShowListener {
        override fun onUnityAdsShowFailure(placementId: String?, error: UnityAds.UnityAdsShowError?, message: String?) {
            Log.w(TAG, "Interstitial show failed: $error $message")
            lastInterstitialAt = 0L
            finishInterstitial()
        }

        override fun onUnityAdsShowStart(placementId: String?) {}
        override fun onUnityAdsShowClick(placementId: String?) {}
        override fun onUnityAdsShowComplete(placementId: String?, state: UnityAds.UnityAdsShowCompletionState?) {
            finishInterstitial()
        }
    })
}

internal fun MainActivity.showRewardedAd(onSuccess: () -> Unit) {
    if (hasPremiumFeatures()) {
        onSuccess()
        return
    }

    if (isFinishing || isDestroyed) return
    if (rewardedWaiting) return

    if (!isOnline()) {
        Toasty.error(this, getString(R.string.toast_ad_no_internet), Toast.LENGTH_LONG, true).show()
        return
    }

    rewardedWaiting = true

    var job: kotlinx.coroutines.Job? = null
    val loadingSheet = ProgressSheet(this)
        .icon(R.drawable.ic_play)
        .title(getString(R.string.ad_loading_title))
        .message(getString(R.string.ad_loading_msg))
        .cancelButton(getString(R.string.btn_cancel)) { job?.cancel() }
    loadingSheet.show(indeterminate = true)
    loadingSheet.setIndeterminate(getString(R.string.ad_loading_step_prepare))

    job = lifecycleScope.launch {
        try {
            if (!adsInitialized) initializeAds()

            var waited = 0
            var slowToastShown = false
            while (!rewardedReady && waited < REWARDED_WAIT_MS) {
                if (adsInitialized) loadRewardedAd()
                if (!slowToastShown && waited >= 6000) {
                    slowToastShown = true
                    loadingSheet.setIndeterminate(getString(R.string.ad_loading_step_slow))
                }
                delay(REWARDED_RELOAD_CHECK_MS.toLong())
                waited += REWARDED_RELOAD_CHECK_MS
            }

            loadingSheet.dismiss()
            if (isFinishing || isDestroyed) return@launch
            if (!rewardedReady) {
                Toasty.error(this@showRewardedAd, getString(R.string.toast_ad_failed), Toast.LENGTH_SHORT, true).show()
                return@launch
            }

            rewardGranted = false
            pendingReward = onSuccess
            rewardedReady = false
            UnityAds.show(this@showRewardedAd, AppConfig.AD_UNIT_REWARDED, UnityAdsShowOptions(), object : IUnityAdsShowListener {
                override fun onUnityAdsShowFailure(placementId: String?, error: UnityAds.UnityAdsShowError?, message: String?) {
                    Log.w(TAG, "Rewarded show failed: $error $message")
                    pendingReward = null
                    Toasty.error(this@showRewardedAd, getString(R.string.toast_ad_failed), Toast.LENGTH_SHORT, true).show()
                    loadRewardedAd()
                }

                override fun onUnityAdsShowStart(placementId: String?) {}
                override fun onUnityAdsShowClick(placementId: String?) {}
                override fun onUnityAdsShowComplete(placementId: String?, state: UnityAds.UnityAdsShowCompletionState?) {
                    val callback = pendingReward
                    pendingReward = null
                    loadRewardedAd()
                    if (state == UnityAds.UnityAdsShowCompletionState.COMPLETED) {
                        rewardGranted = true
                        if (callback != null) {
                            Toasty.success(this@showRewardedAd, getString(R.string.toast_unlocked), Toast.LENGTH_SHORT, true).show()
                            callback()
                        }
                    } else if (callback != null) {
                        Toasty.warning(this@showRewardedAd, getString(R.string.toast_ad_skipped), Toast.LENGTH_LONG, true).show()
                    }
                }
            })
        } finally {
            loadingSheet.dismiss()
            rewardedWaiting = false
        }
    }
}

internal fun MainActivity.setupBannerAd() {
    val bannerContainer = findViewById<LinearLayout>(R.id.bannerAdContainer)

    bannerContainer.removeAllViews()
    bannerView?.destroy()
    bannerView = null

    if (isPremium() || !adsInitialized) return

    val banner = BannerView(this, AppConfig.AD_UNIT_BANNER, UnityBannerSize(320, 50))
    banner.listener = object : BannerView.Listener() {
        override fun onBannerFailedToLoad(bannerAdView: BannerView?, errorInfo: BannerErrorInfo?) {
            Log.w(TAG, "Banner load failed: ${errorInfo?.errorMessage}")
        }
    }
    bannerView = banner
    bannerContainer.addView(banner)
    banner.load()
}
