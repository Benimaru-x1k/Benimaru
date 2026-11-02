package com.benimaru.official

object AppConfig {
    // Unity LevelPlay
    const val LEVELPLAY_APP_KEY = "290725abd"
    const val AD_UNIT_BANNER = "24h3lp1sosejgjs1"
    const val AD_UNIT_INTERSTITIAL = "0jmpm61xv9sz5j5v"
    const val AD_UNIT_REWARDED = "2nm6ti9h61c4ou52"

    const val INTERSTITIAL_COOLDOWN_MS = 3 * 60 * 1000L
    const val INTERSTITIAL_ACTIONS_BEFORE_FIRST = 3

    const val PREMIUM_RECHECK_INTERVAL_MS = 60 * 1000L
    const val PREMIUM_OFFLINE_GRACE_MS = 7L * 24L * 60L * 60L * 1000L

    const val MIN_DPI = 72
    const val MAX_DPI = 1000

    const val REPO_RAW_BASE = "https://raw.githubusercontent.com/Benimaru-x1k/Benimaru/main"
    const val USERS_URL = "$REPO_RAW_BASE/users.json"
    const val SIGNATURE_URL = "$REPO_RAW_BASE/signature.txt"
    const val VERSION_URL = "$REPO_RAW_BASE/version.json"
    const val TELEGRAM_URL = "https://t.me/benimarux1k"
    const val USDT_TRC20_ADDRESS = "TGuax7NAhETmEgp1rbXFnPkZzS2nxxzSr9"
    const val PAYMENT_EMAIL = "benimarux1kqq@gmail.com"
    const val SHIZUKU_APK_URL =
        "https://github.com/RikkaApps/Shizuku/releases/download/v13.6.0/shizuku-v13.6.0.r1086.2650830c-release.apk"

    const val EXPECTED_SIGNATURE_SHA256 = "3ba04fa59e97759b9d2cd2b85e4598d1efa87caf714a70e46f1d8c8f5bb7658b"

    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
}
