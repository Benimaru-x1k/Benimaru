package com.benimaru.official

import android.widget.LinearLayout
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.unity3d.mediation.LevelPlay
import com.unity3d.mediation.LevelPlayAdError
import com.unity3d.mediation.LevelPlayAdInfo
import com.unity3d.mediation.LevelPlayAdSize
import com.unity3d.mediation.LevelPlayConfiguration
import com.unity3d.mediation.LevelPlayInitError
import com.unity3d.mediation.LevelPlayInitListener
import com.unity3d.mediation.LevelPlayInitRequest
import com.unity3d.mediation.banner.LevelPlayBannerAdView
import com.unity3d.mediation.banner.LevelPlayBannerAdViewListener
import com.unity3d.mediation.interstitial.LevelPlayInterstitialAd
import com.unity3d.mediation.interstitial.LevelPlayInterstitialAdListener
import com.unity3d.mediation.rewarded.LevelPlayReward
import com.unity3d.mediation.rewarded.LevelPlayRewardedAd
import com.unity3d.mediation.rewarded.LevelPlayRewardedAdListener
import es.dmoral.toasty.Toasty
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val REWARDED_WAIT_MS = 15000
private const val REWARDED_RELOAD_CHECK_MS = 250
private const val REWARD_CALLBACK_GRACE_MS = 1500L

private fun backoffMs(attempt: Int): Long = minOf(3000L shl minOf(attempt, 4), 30000L)

private fun MainActivity.isOnline(): Boolean = try {
    val cm = getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
    val caps = cm.getNetworkCapabilities(cm.activeNetwork)
    caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
} catch (e: Exception) {
    true
}

internal fun MainActivity.initializeLevelPlay() {
    if (isPremium()) return

    if (levelPlayInitialized) {
        createAdUnits()
        setupBannerAd()
        return
    }
    if (levelPlayInitializing) return
    levelPlayInitializing = true

    val request = LevelPlayInitRequest.Builder(AppConfig.LEVELPLAY_APP_KEY).build()
    LevelPlay.init(this, request, object : LevelPlayInitListener {
        override fun onInitSuccess(configuration: LevelPlayConfiguration) {
            levelPlayInitializing = false
            levelPlayInitialized = true
            levelPlayInitRetry = 0
            if (isFinishing || isDestroyed || isPremium()) return
            createAdUnits()
            setupBannerAd()
        }

        override fun onInitFailed(error: LevelPlayInitError) {
            levelPlayInitializing = false
            val wait = backoffMs(levelPlayInitRetry++)
            lifecycleScope.launch {
                delay(wait)
                if (!isFinishing && !isDestroyed && !levelPlayInitialized) initializeLevelPlay()
            }
        }
    })
}

private fun MainActivity.createAdUnits() {
    if (interstitialAd == null) {
        interstitialAd = LevelPlayInterstitialAd(AppConfig.AD_UNIT_INTERSTITIAL).also {
            it.setListener(object : LevelPlayInterstitialAdListener {
                override fun onAdLoaded(adInfo: LevelPlayAdInfo) {
                    interstitialLoading = false
                    interstitialRetry = 0
                }
                override fun onAdLoadFailed(error: LevelPlayAdError) {
                    interstitialLoading = false
                    val wait = backoffMs(interstitialRetry++)
                    lifecycleScope.launch {
                        delay(wait)
                        if (!isFinishing && !isDestroyed) loadInterstitialAd()
                    }
                }
                override fun onAdDisplayed(adInfo: LevelPlayAdInfo) {}
                override fun onAdDisplayFailed(error: LevelPlayAdError, adInfo: LevelPlayAdInfo) {
                    lastInterstitialAt = 0L
                    finishInterstitial()
                }
                override fun onAdClicked(adInfo: LevelPlayAdInfo) {}
                override fun onAdClosed(adInfo: LevelPlayAdInfo) {
                    finishInterstitial()
                }
                override fun onAdInfoChanged(adInfo: LevelPlayAdInfo) {}
            })
        }
        loadInterstitialAd()
    }

    if (rewardedAd == null) {
        rewardedAd = LevelPlayRewardedAd(AppConfig.AD_UNIT_REWARDED).also {
            it.setListener(object : LevelPlayRewardedAdListener {
                override fun onAdLoaded(adInfo: LevelPlayAdInfo) {
                    rewardedLoading = false
                    rewardedReady = true
                    rewardedRetry = 0
                }
                override fun onAdLoadFailed(error: LevelPlayAdError) {
                    rewardedLoading = false
                    rewardedReady = false
                    val wait = backoffMs(rewardedRetry++)
                    lifecycleScope.launch {
                        delay(wait)
                        if (!isFinishing && !isDestroyed) loadRewardedAd()
                    }
                }
                override fun onAdDisplayed(adInfo: LevelPlayAdInfo) {}
                override fun onAdDisplayFailed(error: LevelPlayAdError, adInfo: LevelPlayAdInfo) {
                    rewardedReady = false
                    pendingReward = null
                    Toasty.error(this@createAdUnits, getString(R.string.toast_ad_failed), Toast.LENGTH_SHORT, true).show()
                    loadRewardedAd()
                }
                override fun onAdRewarded(reward: LevelPlayReward, adInfo: LevelPlayAdInfo) {
                    rewardGranted = true
                    val callback = pendingReward
                    pendingReward = null
                    if (callback != null) {
                        Toasty.success(this@createAdUnits, getString(R.string.toast_unlocked), Toast.LENGTH_SHORT, true).show()
                        callback()
                    }
                }
                override fun onAdClicked(adInfo: LevelPlayAdInfo) {}
                override fun onAdClosed(adInfo: LevelPlayAdInfo) {
                    rewardedReady = false
                    loadRewardedAd()
                    lifecycleScope.launch {
                        delay(REWARD_CALLBACK_GRACE_MS)
                        if (!rewardGranted && pendingReward != null) {
                            pendingReward = null
                            Toasty.warning(this@createAdUnits, getString(R.string.toast_ad_skipped), Toast.LENGTH_LONG, true).show()
                        }
                    }
                }
                override fun onAdInfoChanged(adInfo: LevelPlayAdInfo) {}
            })
        }
        loadRewardedAd()
    }
}

internal fun MainActivity.loadInterstitialAd() {
    if (isPremium() || interstitialLoading) return
    val ad = interstitialAd ?: return
    if (ad.isAdReady()) return
    interstitialLoading = true
    ad.loadAd()
}

internal fun MainActivity.loadRewardedAd() {
    if (hasPremiumFeatures() || rewardedLoading) return
    val ad = rewardedAd ?: return
    if (ad.isAdReady()) return
    rewardedLoading = true
    ad.loadAd()
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
    val ad = interstitialAd
    if (isFinishing || isDestroyed || !shouldShowInterstitial()) {
        onFinished()
        return
    }
    if (ad == null || !ad.isAdReady()) {
        loadInterstitialAd()
        onFinished()
        return
    }
    pendingInterstitialFinish = onFinished
    lastInterstitialAt = android.os.SystemClock.elapsedRealtime()
    ad.showAd(this)
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
            if (!levelPlayInitialized) initializeLevelPlay()

            var waited = 0
            var slowToastShown = false
            while (rewardedAd?.isAdReady() != true && waited < REWARDED_WAIT_MS) {
                if (levelPlayInitialized) {
                    if (rewardedAd == null) initializeLevelPlay()
                    loadRewardedAd()
                }
                if (waited == 8000) rewardedLoading = false
                if (!slowToastShown && waited >= 6000) {
                    slowToastShown = true
                    loadingSheet.setIndeterminate(getString(R.string.ad_loading_step_slow))
                }
                delay(REWARDED_RELOAD_CHECK_MS.toLong())
                waited += REWARDED_RELOAD_CHECK_MS
            }

            val ad = rewardedAd
            loadingSheet.dismiss()
            if (isFinishing || isDestroyed) return@launch
            if (ad == null || !ad.isAdReady()) {
                Toasty.error(this@showRewardedAd, getString(R.string.toast_ad_failed), Toast.LENGTH_SHORT, true).show()
                return@launch
            }

            rewardGranted = false
            pendingReward = onSuccess
            rewardedReady = false
            ad.showAd(this@showRewardedAd)
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

    if (isPremium() || !levelPlayInitialized) return

    val config = LevelPlayBannerAdView.Config.Builder()
        .setAdSize(LevelPlayAdSize.BANNER)
        .build()
    val banner = LevelPlayBannerAdView(this, AppConfig.AD_UNIT_BANNER, config)
    banner.setBannerListener(object : LevelPlayBannerAdViewListener {
        override fun onAdLoaded(adInfo: LevelPlayAdInfo) {}
        override fun onAdLoadFailed(error: LevelPlayAdError) {}
        override fun onAdDisplayed(adInfo: LevelPlayAdInfo) {}
        override fun onAdDisplayFailed(adInfo: LevelPlayAdInfo, error: LevelPlayAdError) {}
        override fun onAdClicked(adInfo: LevelPlayAdInfo) {}
        override fun onAdExpanded(adInfo: LevelPlayAdInfo) {}
        override fun onAdCollapsed(adInfo: LevelPlayAdInfo) {}
        override fun onAdLeftApplication(adInfo: LevelPlayAdInfo) {}
    })
    bannerView = banner
    bannerContainer.addView(banner)
    banner.loadAd()
}
