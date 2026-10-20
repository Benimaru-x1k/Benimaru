package com.benimaru.official

import android.app.Application
import android.content.Context
import android.content.res.Resources
import androidx.appcompat.app.AppCompatDelegate

class MainApplication : Application() {
    override fun getResources(): Resources = AppTranslator.wrap(super.getResources())

    override fun onCreate() {
        super.onCreate()
        AppTranslator.init(this)

        val prefs = getSharedPreferences(Prefs.THEME, Context.MODE_PRIVATE)
        val isDarkModeSaved = prefs.getBoolean(Prefs.KEY_DARK_MODE, false)

        if (isDarkModeSaved) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }
}
