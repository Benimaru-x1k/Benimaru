package com.benimaru.official

import android.content.Context
import android.os.SystemClock
import android.widget.Toast
import es.dmoral.toasty.Toasty

private const val RETRY_BACKOFF_MS = 6L * 60 * 60 * 1000


internal fun MainActivity.setupLanguageIfNeeded() {
    if (!AppTranslator.needsSetup(this)) return

    val prefs = getSharedPreferences(Prefs.MAIN, Context.MODE_PRIVATE)
    val now = System.currentTimeMillis()
    if (now < prefs.getLong(Prefs.KEY_LANG_RETRY_AFTER, 0L)) return

    val progress = ProgressSheet(this)
        .icon(R.drawable.ic_language)
        .title(getString(R.string.lang_setup_title))
        .message(getString(R.string.lang_setup_msg, AppTranslator.languageDisplayName()))
        .cancelButton(getString(R.string.btn_skip)) {
            AppTranslator.cancelSetup()
            prefs.edit().putLong(Prefs.KEY_LANG_RETRY_AFTER, System.currentTimeMillis() + RETRY_BACKOFF_MS / 6).apply()
        }
        .show(indeterminate = true)
    progress.setIndeterminate(getString(R.string.lang_phase_download))

    val startedAt = SystemClock.elapsedRealtime()
    AppTranslator.startSetup(
        this,
        onPhase = { pct ->
            if (pct < 0) progress.setIndeterminate(getString(R.string.lang_phase_download))
            else progress.update(pct, getString(R.string.lang_phase_translate, pct))
        },
        onDone = { ok ->
            if (!isFinishing && !isDestroyed) progress.dismiss()
            if (ok) {
                prefs.edit().remove(Prefs.KEY_LANG_RETRY_AFTER).apply()
                if (!isFinishing && !isDestroyed) recreate()
            } else if (SystemClock.elapsedRealtime() - startedAt > 400 && !isFinishing && !isDestroyed) {
                if (System.currentTimeMillis() >= prefs.getLong(Prefs.KEY_LANG_RETRY_AFTER, 0L)) {
                    prefs.edit().putLong(Prefs.KEY_LANG_RETRY_AFTER, System.currentTimeMillis() + RETRY_BACKOFF_MS).apply()
                    Toasty.warning(this, getString(R.string.toast_lang_failed), Toast.LENGTH_LONG, true).show()
                }
            }
        }
    )
}
