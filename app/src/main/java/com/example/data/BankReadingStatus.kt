package com.example.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

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

    fun isSmsPermissionGranted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED

    /** لو مش مستثنى، دوز/App Standby ممكن يأخر أو يوقف UnifiedBankListener لما التطبيق في الخلفية */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }
}
