package com.example.data

import android.content.Context

/**
 * تنبيه ربط تليجرام أول ما العميل يدخل الرئيسية.
 *
 * البوت هو القناة اللي زاد بيكلم العميل منها فعلاً: تأكيد الحركة البنكية قبل ما تتحسب،
 * نتايج الماسح الاستباقي (ملخص البيت، توقّع الصرف، تذكير الفواتير)، وتنبيه النواقص
 * اللحظي. عميل مش رابط تليجرام مابيوصلوش أي حاجة من دول برّه التطبيق — وكارت الربط
 * الموجود مستخبي ورا "عرض المزيد" في الرئيسية، فمحدش كان بيلاقيه.
 *
 * مرتين بالكتير، بينهم ٣ أيام: مرة أول دخول، ومرة تذكير لو قال "مش دلوقتي". بعد كده
 * الكارت العادي كفاية — تنبيه بيتكرر كل فتح بيتقفل من غير ما يتقرا.
 */
object TelegramLinkPrompt {
    const val MAX_SHOWS = 2
    const val RESHOW_AFTER_MS = 3 * 24 * 60 * 60 * 1000L

    private const val PREFS = "zad_prefs"
    private const val KEY_TIMES_SHOWN = "telegram_link_prompt_times_shown"
    private const val KEY_LAST_SHOWN_AT = "telegram_link_prompt_last_shown_at"

    /**
     * محلي بالكامل عشان يتسأل قبل أي نداء شبكة: الرئيسية أكتر شاشة بتتفتح، ونداء
     * `isTelegramLinked` في كل فتح هيبقى تكلفة على العميل اللي خلص من التنبيه ده.
     */
    fun shouldShow(timesShown: Int, lastShownAtMs: Long?, nowMs: Long): Boolean {
        if (timesShown >= MAX_SHOWS) return false
        if (timesShown == 0 || lastShownAtMs == null) return true
        return nowMs - lastShownAtMs >= RESHOW_AFTER_MS
    }

    fun isDue(context: Context, nowMs: Long = System.currentTimeMillis()): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = prefs.getLong(KEY_LAST_SHOWN_AT, -1L).takeIf { it >= 0 }
        return shouldShow(prefs.getInt(KEY_TIMES_SHOWN, 0), last, nowMs)
    }

    fun recordShown(context: Context, nowMs: Long = System.currentTimeMillis()) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_TIMES_SHOWN, prefs.getInt(KEY_TIMES_SHOWN, 0) + 1)
            .putLong(KEY_LAST_SHOWN_AT, nowMs)
            .apply()
    }

    /** العميل مربوط فعلاً — مفيش داعي نسأل السيرفر تاني ولا نعرض التنبيه. */
    fun recordLinked(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_TIMES_SHOWN, MAX_SHOWS).apply()
    }
}
