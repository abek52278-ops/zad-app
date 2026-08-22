package com.example.data

import android.content.Context

/**
 * إعداد "Hey Zad" — المستخدم يتحكم في الاستماع الدائم من الإعدادات.
 * مفعّل افتراضيًا؛ الإيقاف بيوقف الخدمة تمامًا (صفر بطارية).
 */
object WakePrefs {
    private const val PREFS = "zad_wake_prefs"
    private const val KEY_ENABLED = "wake_enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
    }
}
