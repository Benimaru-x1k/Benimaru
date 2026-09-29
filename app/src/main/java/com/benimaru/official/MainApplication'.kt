package com.benimaru.official

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

class MainApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // We check and apply the saved theme before ANY UI is created
        val prefs = getSharedPreferences("ThemePrefs", Context.MODE_PRIVATE)
        val isDarkModeSaved = prefs.getBoolean("isDarkMode", false)

        if (isDarkModeSaved) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }
}