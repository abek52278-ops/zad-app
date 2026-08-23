package com.example.data

import android.content.Context
import android.util.Log

/**
 * سجل الأعطال المحلي — بديل خفيف لـ Crashlytics بدون Firebase:
 * - كل كراش بيتسجل في SharedPreferences (آخر 20 عطل) مع الـ stacktrace
 * - العميل يقدر يصدر السجل من الإعدادات (يبعته للمطور)
 * - مفيش بيانات بتخرج من الجهاز من غير ما العميل يبعتها بنفسه — خصوصية زاد
 *
 * ليه مش Crashlytics؟ التطبيق بيبيع "خصوصيتك أولوية" — إرسال تقارير تلقائي
 * لخدمة Google بيعاكس الوعد ده. التصدير اليدوي أنضف.
 */
object ZadCrashLog {
    private const val PREFS = "zad_crash_log"
    private const val KEY_COUNT = "count"
    private const val KEY_PREFIX = "crash_"
    private const val MAX_ENTRIES = 20

    fun record(context: Context, throwable: Throwable) {
        try {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val count = prefs.getInt(KEY_COUNT, 0)
            val entry = buildString {
                append(java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                    .format(java.util.Date()))
                append("\n")
                append(throwable.javaClass.name).append(": ").append(throwable.message ?: "")
                append("\n")
                append(throwable.stackTraceToString().take(3000))
            }
            prefs.edit()
                .putString(KEY_PREFIX + (count % MAX_ENTRIES), entry)
                .putInt(KEY_COUNT, count + 1)
                .apply()
            Log.e("ZadCrashLog", "crash recorded (#${count + 1})")
        } catch (_: Exception) { /* ما نكسرش الكراش هاندلر */ }
    }

    /** كل السجل كسطر واحد للتصدير/المشاركة */
    fun exportAll(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val count = prefs.getInt(KEY_COUNT, 0)
        if (count == 0) return "لا توجد أعطال مسجلة ✅"
        val sb = StringBuilder("=== سجل أعطال زاد ===\n\n")
        for (i in 0 until minOf(count, MAX_ENTRIES)) {
            prefs.getString(KEY_PREFIX + i, null)?.let { sb.append(it).append("\n\n———\n\n") }
        }
        return sb.toString()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
