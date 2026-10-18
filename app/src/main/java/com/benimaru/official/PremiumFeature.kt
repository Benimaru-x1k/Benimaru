package com.benimaru.official

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.provider.Settings
import android.provider.Settings.Secure
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import es.dmoral.toasty.Toasty
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal fun MainActivity.isPremium(): Boolean {
    return getSharedPreferences(Prefs.MAIN, Context.MODE_PRIVATE).getBoolean(Prefs.KEY_ADS_REMOVED, false)
}

internal fun MainActivity.updatePremiumButtonUI() {
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

internal fun MainActivity.recheckPremiumIfNeeded(force: Boolean = false) {
    if (!isPremium() || isRecheckingPremium) return

    val now = android.os.SystemClock.elapsedRealtime()
    if (!force && lastPremiumRecheckAt != 0L && now - lastPremiumRecheckAt < AppConfig.PREMIUM_RECHECK_INTERVAL_MS) return
    lastPremiumRecheckAt = now
    isRecheckingPremium = true

    val prefs = getSharedPreferences(Prefs.MAIN, Context.MODE_PRIVATE)
    val username = prefs.getString(Prefs.KEY_PREMIUM_USER, "") ?: ""
    val storedHash = prefs.getString(Prefs.KEY_PREMIUM_HASH, "") ?: ""
    val deviceId = Secure.getString(contentResolver, Secure.ANDROID_ID) ?: ""

    lifecycleScope.launch(Dispatchers.IO) {
        val status = PremiumRepository.checkStatus(username, storedHash, deviceId)
        withContext(Dispatchers.Main) {
            isRecheckingPremium = false
            val wallNow = System.currentTimeMillis()
            when (status) {
                PremiumStatus.VALID -> {
                    prefs.edit().putLong(Prefs.KEY_PREMIUM_LAST_VERIFIED, wallNow).apply()
                }
                PremiumStatus.INVALID -> revokePremium()
                PremiumStatus.UNREACHABLE -> {
                    val lastVerified = prefs.getLong(Prefs.KEY_PREMIUM_LAST_VERIFIED, 0L)
                    if (lastVerified == 0L) {
                        prefs.edit().putLong(Prefs.KEY_PREMIUM_LAST_VERIFIED, wallNow).apply()
                    } else if (wallNow - lastVerified > AppConfig.PREMIUM_OFFLINE_GRACE_MS) {
                        revokePremium()
                    }
                }
            }
        }
    }
}

internal fun MainActivity.revokePremium() {
    getSharedPreferences(Prefs.MAIN, Context.MODE_PRIVATE).edit()
        .remove(Prefs.KEY_ADS_REMOVED)
        .remove(Prefs.KEY_PREMIUM_USER)
        .remove(Prefs.KEY_PREMIUM_HASH)
        .remove(Prefs.KEY_PREMIUM_LAST_VERIFIED)
        .apply()
    updatePremiumButtonUI()
    initializeUnityAds()
    Toasty.warning(this, getString(R.string.toast_premium_inactive), Toast.LENGTH_LONG, true).show()
}

internal fun MainActivity.handlePremiumFeature(action: () -> Unit) {
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

private class PremiumModeTabs(
    val toggle: com.google.android.material.button.MaterialButtonToggleGroup,
    val loginTab: MaterialButton,
    val buyTab: MaterialButton
)

private fun MainActivity.createPremiumModeTabs(): PremiumModeTabs {
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
    return PremiumModeTabs(toggle, loginTab, buyTab)
}

private fun MainActivity.premiumFieldError(user: String, pass: String, forPurchase: Boolean): String? {
    if (user.isEmpty() || pass.isEmpty()) return getString(R.string.err_enter_user_pass)
    if (forPurchase) {
        if (!Regex("^[A-Za-z0-9_.-]{3,32}\$").matches(user)) return getString(R.string.err_username_format)
        if (pass.length < 6) return getString(R.string.err_password_short)
    }
    return null
}

private fun MainActivity.copyAccountAndOpenTelegram(user: String, pass: String, deviceId: String, btn: android.widget.Button) {
    lifecycleScope.launch {
        val hash = withContext(Dispatchers.Default) { PremiumRepository.hashPassword(user, pass) }
        btn.isEnabled = true
        val cb = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cb.setPrimaryClip(
            android.content.ClipData.newPlainText(
                "Premium account",
                "Username: $user\nDevice ID: $deviceId\nPassword hash: $hash"
            )
        )
        Toasty.info(this@copyAccountAndOpenTelegram, getString(R.string.toast_account_copied), Toast.LENGTH_LONG, true).show()
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AppConfig.TELEGRAM_URL)))
    }
}

internal fun MainActivity.showRemoveAdsDialog() {
    val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
    val gold = Color.parseColor("#E0A800")
    var buyMode = false


    val tabs = createPremiumModeTabs()
    val toggle = tabs.toggle
    val loginTab = tabs.loginTab
    val buyTab = tabs.buyTab

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
        val error = premiumFieldError(user, pass, forPurchase)
        if (error != null) {
            showError(error)
            return null
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
        copyAccountAndOpenTelegram(user, pass, deviceId, btn)
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

internal fun MainActivity.verifyPremiumAccount(username: String, pass: String, onDone: (Boolean, String) -> Unit) {
    val deviceId = Secure.getString(contentResolver, Secure.ANDROID_ID) ?: ""

    lifecycleScope.launch(Dispatchers.IO) {
        var ok = false
        var verifiedHash = ""
        var reason = getString(R.string.err_invalid_login)
        try {
            val hash = PremiumRepository.hashPassword(username, pass)
            val conn = URL(AppConfig.USERS_URL)
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
                getSharedPreferences(Prefs.MAIN, Context.MODE_PRIVATE).edit()
                    .putBoolean(Prefs.KEY_ADS_REMOVED, true)
                    .putString(Prefs.KEY_PREMIUM_USER, username.trim())
                    .putString(Prefs.KEY_PREMIUM_HASH, verifiedHash)
                    .putLong(Prefs.KEY_PREMIUM_LAST_VERIFIED, System.currentTimeMillis())
                    .apply()
                findViewById<LinearLayout>(R.id.bannerAdContainer).removeAllViews()
                updatePremiumButtonUI()
                Toasty.success(this@verifyPremiumAccount, getString(R.string.toast_premium_activated), Toast.LENGTH_LONG, true).show()
            }
            onDone(ok, reason)
        }
    }
}
