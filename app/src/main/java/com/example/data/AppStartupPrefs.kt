package com.example.data

import android.content.Context

/**
 * تخزين حالة بدء التطبيق واكتمال الترحيب/الإعداد لمنع تكرار شاشة البداية (Instant Cold Start).
 */
object AppStartupPrefs {
    private const val PREFS = "zad_startup_prefs"
    private const val KEY_ONBOARDING_COMPLETED = "isOnboardingCompleted"

    fun isOnboardingCompleted(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ONBOARDING_COMPLETED, false)
    }

    fun setOnboardingCompleted(context: Context, completed: Boolean = true) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ONBOARDING_COMPLETED, completed)
            .apply()
    }
}
