package com.example.data

import android.content.Context
import androidx.core.app.NotificationManagerCompat

/**
 * حالة قراءة رسايل البنك الحقيقية — مفيش toggle بيكذب. الشاشة اللي بتعرض الحالة دي
 * (BankReadingStatusScreen) بتقرا نفس المصدرين اللي أندرويد نفسه بيرجعهم، مش قيمة محفوظة
 * ممكن تفضل true حتى لو المستخدم سحب الصلاحية من إعدادات النظام.
 */
object BankReadingStatus {
    private const val PREFS = "zad_bank_reading_status"
    private const val KEY_LAST_PARSED_AT = "last_parsed_at"

    fun recordParsed(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(KEY_LAST_PARSED_AT, System.currentTimeMillis()).apply()
    }

    fun lastParsedAt(context: Context): Long? {
        val v = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_LAST_PARSED_AT, -1L)
        return if (v > 0) v else null
    }

    fun isNotificationListenerEnabled(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
}
