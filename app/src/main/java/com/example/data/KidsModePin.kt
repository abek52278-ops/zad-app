package com.example.data

import android.content.Context
import java.security.MessageDigest

/**
 * قفل PIN محلي (على الجهاز بس) للخروج من وضع الأطفال — مش حاجز أمان سيرفر،
 * مجرد احتكاك يمنع طفل من الرجوع للشاشات المالية على نفس الجهاز. مبيتزامنش
 * بين الأجهزة عن قصد؛ الأب/الأم بيحطوه مرة واحدة أول ما يستخدموا الميزة.
 */
object KidsModePin {

    private const val PREFS = "zad_kids_mode_pin"
    private const val KEY_HASH = "pin_hash"
    private const val KEY_MANUAL_ACTIVE = "manual_kids_mode_active"

    /**
     * حالة تبديل "وضع الأطفال اليدوي" (لما الأب/الأم يديوا الجهاز لطفل مؤقتاً) —
     * محفوظة محلياً عشان لو التطبيق اتقفل في نص الاستخدام متفتحش كامل تاني من غير PIN.
     */
    fun isManualModeActive(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_MANUAL_ACTIVE, false)

    fun setManualModeActive(context: Context, active: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_MANUAL_ACTIVE, active)
            .apply()
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    fun hasPinSet(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(KEY_HASH)

    fun setPin(context: Context, pin: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_HASH, sha256(pin))
            .apply()
    }

    fun verifyPin(context: Context, pin: String): Boolean {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_HASH, null)
            ?: return false
        return stored == sha256(pin)
    }

    fun clearPin(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_HASH).apply()
    }
}
