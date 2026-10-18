package com.benimaru.official

import android.widget.LinearLayout
import android.widget.Toast
import com.unity3d.ads.IUnityAdsInitializationListener
import com.unity3d.ads.IUnityAdsLoadListener
import com.unity3d.ads.IUnityAdsShowListener
import com.unity3d.ads.UnityAds
import com.unity3d.services.banners.BannerView
import com.unity3d.services.banners.UnityBannerSize
import es.dmoral.toasty.Toasty

internal fun MainActivity.initializeUnityAds() {
    if (isPremium()) return

    UnityAds.initialize(this, AppConfig.UNITY_GAME_ID, AppConfig.UNITY_TEST_MODE, object : IUnityAdsInitializationListener {
        override fun onInitializationComplete() {
            loadInterstitialAd()
            loadRewardedAd()
            setupBannerAd()
        }
        override fun onInitializationFailed(error: UnityAds.UnityAdsInitializationError?, message: String?) {}
    })
}

internal fun MainActivity.loadInterstitialAd() {
    if (isPremium()) return
    UnityAds.load(AppConfig.AD_UNIT_INTERSTITIAL, object : IUnityAdsLoadListener {
        override fun onUnityAdsAdLoaded(placementId: String) {}
        override fun onUnityAdsFailedToLoad(placementId: String, error: UnityAds.UnityAdsLoadError, message: String) {}
    })
}

internal fun MainActivity.loadRewardedAd() {
    if (isPremium()) return
    UnityAds.load(AppConfig.AD_UNIT_REWARDED, object : IUnityAdsLoadListener {
        override fun onUnityAdsAdLoaded(placementId: String) {}
        override fun onUnityAdsFailedToLoad(placementId: String, error: UnityAds.UnityAdsLoadError, message: String) {}
    })
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
    var done = false
    fun finish() {
        if (done) return
        done = true
        loadInterstitialAd()
        onFinished()
    }
    lastInterstitialAt = android.os.SystemClock.elapsedRealtime()
    UnityAds.show(this, AppConfig.AD_UNIT_INTERSTITIAL, object : IUnityAdsShowListener {
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

internal fun MainActivity.showRewardedAd(onSuccess: () -> Unit) {
    if (isPremium()) {
        onSuccess()
        return
    }

    Toasty.info(this, getString(R.string.toast_loading_ad), Toast.LENGTH_SHORT, true).show()

    UnityAds.show(this, AppConfig.AD_UNIT_REWARDED, object : IUnityAdsShowListener {
        override fun onUnityAdsShowFailure(placementId: String, error: UnityAds.UnityAdsShowError, message: String) {
            Toasty.error(this@showRewardedAd, getString(R.string.toast_ad_failed), Toast.LENGTH_SHORT, true).show()
            loadRewardedAd()
        }
        override fun onUnityAdsShowStart(placementId: String) {}
        override fun onUnityAdsShowClick(placementId: String) {}
        override fun onUnityAdsShowComplete(placementId: String, state: UnityAds.UnityAdsShowCompletionState) {
            if (state == UnityAds.UnityAdsShowCompletionState.COMPLETED) {
                Toasty.success(this@showRewardedAd, getString(R.string.toast_unlocked), Toast.LENGTH_SHORT, true).show()
                onSuccess()
            } else {
                Toasty.warning(this@showRewardedAd, getString(R.string.toast_ad_skipped), Toast.LENGTH_LONG, true).show()
            }
            loadRewardedAd()
        }
    })
}

internal fun MainActivity.setupBannerAd() {
    val bannerContainer = findViewById<LinearLayout>(R.id.bannerAdContainer)

    if (isPremium()) {
        bannerContainer.removeAllViews()
        return
    }

    val bannerView = BannerView(this, AppConfig.AD_UNIT_BANNER, UnityBannerSize(320, 50))
    bannerContainer.addView(bannerView)
    bannerView.load()
}
