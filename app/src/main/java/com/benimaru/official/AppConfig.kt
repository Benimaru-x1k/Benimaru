package com.benimaru.official

object AppConfig {
    const val UNITY_GAME_ID = "5781189"
    const val AD_UNIT_INTERSTITIAL = "Interstitial_Android"
    const val AD_UNIT_BANNER = "Banner_Android"
    const val AD_UNIT_REWARDED = "Rewarded_Android"
    const val UNITY_TEST_MODE = false

    const val INTERSTITIAL_COOLDOWN_MS = 3 * 60 * 1000L
    const val INTERSTITIAL_ACTIONS_BEFORE_FIRST = 3

    const val PREMIUM_RECHECK_INTERVAL_MS = 60 * 1000L
    const val PREMIUM_OFFLINE_GRACE_MS = 7L * 24L * 60L * 60L * 1000L

    const val REPO_RAW_BASE = "https://raw.githubusercontent.com/Benimaru-x1k/Benimaru/main"
    const val USERS_URL = "$REPO_RAW_BASE/users.json"
    const val SIGNATURE_URL = "$REPO_RAW_BASE/signature.txt"
    const val VERSION_URL = "$REPO_RAW_BASE/version.json"
    const val TELEGRAM_URL = "https://t.me/benimarux1k"
    const val SHIZUKU_APK_URL =
        "https://github.com/RikkaApps/Shizuku/releases/download/v13.6.0/shizuku-v13.6.0.r1086.2650830c-release.apk"

    const val EXPECTED_SIGNATURE_SHA256 = "3ba04fa59e97759b9d2cd2b85e4598d1efa87caf714a70e46f1d8c8f5bb7658b"

    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
}
