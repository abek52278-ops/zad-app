package com.example.data

import android.content.Context
import android.telephony.TelephonyManager
import java.util.Locale

/**
 * مرحلة ١ (docs/agent/PLAN_2026_08_06_rebuild.md) — كشف السفر: هل بلد الشبكة الحالي
 * مختلف عن السوق المختار في التطبيق؟ `networkCountryIso` أدق من `simCountryIso` وقت
 * التجوال — بيرجع بلد الشبكة الفعلية اللي الجهاز متصل بيها دلوقتي، مش بلد شريحة SIM
 * الأصلية. بيرجع فاضي على أجهزة واي فاي بس أو أثناء فقدان الشبكة، فبنرجع لـ
 * `Locale.getDefault()` كبديل، وبعدين null لو الاتنين فشلوا.
 *
 * النتيجة اقتراح بس — بانر "تحويل؟" قابل للتجاهل، أبداً مش تبديل صامت لعملة/سوق المستخدم.
 */
object TravelDetector {

    /** كود بلد ISO-3166 حرفين (SA/EG/TR...) بالحروف الكبيرة، أو null لو مش معروف */
    fun detectCurrentCountryCode(context: Context): String? {
        val networkIso = try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            tm?.networkCountryIso?.takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            null
        }
        val code = networkIso ?: Locale.getDefault().country.takeIf { it.isNotBlank() }
        return code?.uppercase(Locale.US)
    }

    /**
     * السوق المطابق لكود البلد المكتشف، لو زاد بيدعمه فعلاً (Market.countryCode) — null
     * لو البلد المكتشف مش من الأسواق المدعومة لسه (قبل توسيع مرحلة ٢) أو مطابق للسوق
     * الحالي أصلاً (مفيش داعي لاقتراح تحويل لنفس السوق).
     */
    fun suggestedMarketFor(context: Context): Market? {
        val detected = detectCurrentCountryCode(context) ?: return null
        val current = MarketPrefs.getMarket(context)
        if (detected == current.countryCode) return null
        return Market.entries.firstOrNull { it.countryCode == detected }
    }
}
