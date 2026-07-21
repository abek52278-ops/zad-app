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
    val useEuropeanNumberFormat: Boolean = false
) {
    SAUDI_ARABIA("السعودية", "ar-SA", "SAR", "ر.س"),
    EGYPT("مصر", "ar-EG", "EGP", "ج.م"),
    TURKEY("تركيا", "tr-TR", "TRY", "₺", useEuropeanNumberFormat = true);

    fun toLocale(): Locale {
        val parts = localeTag.split("-")
        return Locale(parts[0], parts.getOrElse(1) { "" })
    }
}

/** تخزين اختيار البلد محلياً على الجهاز — نفس نمط SharedPreferences المستخدم في SessionHelper/AlertPrefs */
object MarketPrefs {
    private const val PREFS = "zad_market_prefs"
    private const val KEY_MARKET = "selected_market"

    fun getMarket(context: Context): Market {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MARKET, null)
        return stored?.let { name -> Market.entries.find { it.name == name } } ?: Market.SAUDI_ARABIA
    }

    fun setMarket(context: Context, market: Market) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MARKET, market.name)
            .apply()
    }

    /** أول مرة يفتح فيها التطبيق — لسه محددش بلد */
    fun hasSelectedMarket(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(KEY_MARKET)
    }
}
