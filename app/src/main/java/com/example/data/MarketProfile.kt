package com.example.data

import android.content.Context
import java.util.Locale

/**
 * البلد اللي بيحدد اللغة/اللهجة والعملة وتنسيق الأرقام لكل واجهات التطبيق.
 * السعودية هي السوق الأساسي والافتراضي — مصر وتركيا إضافة، مش استبدال.
 */
enum class Market(
    val displayNameAr: String,
    val localeTag: String,
    val currencyCode: String,
    val currencySymbol: String,
    /** فاصل الآلاف والعشري بيختلف في تركيا (1.234,56) عن السعودية/مصر (1,234.56) */
    val useEuropeanNumberFormat: Boolean = false,
    /** توجيه لهجة/لغة يُحقن في system prompt الذكاء الاصطناعي — يحدد إزاي زاد يتكلم مع عميل البلد ده */
    val dialectInstruction: String = ""
) {
    SAUDI_ARABIA(
        "السعودية", "ar-SA", "SAR", "ر.س",
        dialectInstruction = "تتحدث باللهجة السعودية/الخليجية الطبيعية في المحادثة اليومية (مثل: \"أبشر\"، \"يعطيك العافية\"، \"وش رايك\"، \"كذا\") — مش فصحى رسمية."
    ),
    EGYPT(
        "مصر", "ar-EG", "EGP", "ج.م",
        dialectInstruction = "تتحدث باللهجة المصرية العامية الطبيعية في المحادثة اليومية (مثل: \"إزيك\"، \"تمام\"، \"خلاص\"، \"يلا\"، \"معلش\") — مش فصحى رسمية."
    ),
    TURKEY(
        "تركيا", "tr-TR", "TRY", "₺", useEuropeanNumberFormat = true,
        dialectInstruction = "Respond entirely in natural, conversational Turkish (samimi bir Türkçe ile) — not Arabic, regardless of what language the underlying data labels are in."
    );

    fun toLocale(): Locale {
        val parts = localeTag.split("-")
        return Locale(parts[0], parts.getOrElse(1) { "" })
    }
}

/** تخزين اختيار البلد محلياً على الجهاز — نفس نمط SharedPreferences المستخدم في SessionHelper/AlertPrefs */
object MarketPrefs {
    private const val PREFS = "zad_market_prefs"
    private const val KEY_MARKET = "selected_market"

    /**
     * كاش في الذاكرة — يسمح لطبقات زي ZadAiRepository (object بلا Context) إنها تقرأ
     * البلد الحالي عشان تحقن توجيه اللهجة في أي AI call من غير ما تحمل Context في كل دالة.
     * يتحدّث تلقائياً مع أي getMarket()/setMarket().
     */
    @Volatile
    var currentMarket: Market = Market.SAUDI_ARABIA
        private set

    fun getMarket(context: Context): Market {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MARKET, null)
        val market = stored?.let { name -> Market.entries.find { it.name == name } } ?: Market.SAUDI_ARABIA
        currentMarket = market
        return market
    }

    fun setMarket(context: Context, market: Market) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MARKET, market.name)
            .apply()
        currentMarket = market
        applyLocale(market)
    }

    /** أول مرة يفتح فيها التطبيق — لسه محددش بلد */
    fun hasSelectedMarket(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(KEY_MARKET)
    }

    /**
     * لازم تتنادى مرة عند بدء التطبيق (بعد getMarket) — من غيرها اختيار البلد
     * بيتخزن بس، ومجلد strings.xml اللي بيتحمل فعلياً بيفضل تابع للغة نظام الجهاز
     * مش لاختيار المستخدم جوه التطبيق.
     */
    fun applyStoredLocale(context: Context) {
        applyLocale(getMarket(context))
    }

    private fun applyLocale(market: Market) {
        val locales = androidx.core.os.LocaleListCompat.forLanguageTags(market.localeTag)
        androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(locales)
    }
}
