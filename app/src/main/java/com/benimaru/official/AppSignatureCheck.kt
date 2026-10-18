package com.benimaru.official

import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import es.dmoral.toasty.Toasty
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun MainActivity.verifyAppSignature() {
    lifecycleScope.launch(Dispatchers.IO) {
        try {
            val currentSignature = getCurrentAppSignature()
            var expectedSignature = AppConfig.EXPECTED_SIGNATURE_SHA256

            try {
                val url = URL(AppConfig.SIGNATURE_URL)
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
                    Toasty.error(this@verifyAppSignature, getString(R.string.toast_unofficial), Toast.LENGTH_LONG, true).show()
                    delay(2000)
                    finishAffinity()
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { finishAffinity() }
        }
    }
}

internal fun MainActivity.getCurrentAppSignature(): String {
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
