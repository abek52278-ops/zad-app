package com.example.data

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * MainActivity مش AppCompatActivity، فـ AppCompatDelegate.setApplicationLocales() ما بيعملش
 * recreate تلقائي على أجهزة أقدم من API 33 — محتاجين نلاقي الـ Activity ونعمل recreate()
 * يدوي بعد أي تبديل سوق عشان النصوص المعتمدة على اللغة تتحدث فعلياً.
 */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * البلد اللي بيحدد اللغة/اللهجة والعملة وتنسيق الأرقام لكل واجهات التطبيق.
 *
 * مرحلة ٢ (docs/agent/PLAN_2026_08_06_rebuild.md) — توسّعت من ٣ أسواق (السعودية/مصر/
 * تركيا) لتغطية الشرق الأوسط وشمال أفريقيا كله + تركيا. كل سوق عربي جديد بياخد
 * dialectInstruction خاص بيه (مش نسخة عامة "عربي") — نفس مبدأ الـ ٣ الأصليين، الحقن
 * شغال في ZadAiRepository.kt:827. flagEmoji مستخدم في شاشة اختيار البلد الموحّدة
 * (MarketPickerGrid) في الـ onboarding والبروفايل مع بعض.
 */
enum class Market(
    val displayNameAr: String,
    val countryCode: String,
    val localeTag: String,
    val currencyCode: String,
    val currencySymbol: String,
    val flagEmoji: String,
    /** فاصل الآلاف والعشري بيختلف في تركيا (1.234,56) عن باقي الأسواق (1,234.56) */
    val useEuropeanNumberFormat: Boolean = false,
    /** توجيه لهجة/لغة يُحقن في system prompt الذكاء الاصطناعي — يحدد إزاي زاد يتكلم مع عميل البلد ده */
    val dialectInstruction: String = ""
) {
    SAUDI_ARABIA(
        "السعودية", "SA", "ar-SA", "SAR", "ر.س", "🇸🇦",
        dialectInstruction = "تتحدث باللهجة السعودية/الخليجية الطبيعية في المحادثة اليومية (مثل: \"أبشر\"، \"يعطيك العافية\"، \"وش رايك\"، \"كذا\") — مش فصحى رسمية."
    ),
    EGYPT(
        "مصر", "EG", "ar-EG", "EGP", "ج.م", "🇪🇬",
        dialectInstruction = "تتحدث باللهجة المصرية العامية الطبيعية في المحادثة اليومية (مثل: \"إزيك\"، \"تمام\"، \"خلاص\"، \"يلا\"، \"معلش\") — مش فصحى رسمية."
    ),
    UAE(
        "الإمارات", "AE", "ar-AE", "AED", "د.إ", "🇦🇪",
        dialectInstruction = "تتحدث باللهجة الإماراتية الخليجية الطبيعية في المحادثة اليومية (مثل: \"يعطيك العافية\"، \"شحالك\"، \"زين\"، \"خلاص\") — مش فصحى رسمية."
    ),
    KUWAIT(
        "الكويت", "KW", "ar-KW", "KWD", "د.ك", "🇰🇼",
        dialectInstruction = "تتحدث باللهجة الكويتية الخليجية الطبيعية في المحادثة اليومية (مثل: \"شلونك\"، \"زين\"، \"عادي\"، \"شكو ماكو\") — مش فصحى رسمية."
    ),
    QATAR(
        "قطر", "QA", "ar-QA", "QAR", "ر.ق", "🇶🇦",
        dialectInstruction = "تتحدث باللهجة القطرية الخليجية الطبيعية في المحادثة اليومية (مثل: \"شخبارك\"، \"زين\"، \"يا هلا\") — مش فصحى رسمية."
    ),
    BAHRAIN(
        "البحرين", "BH", "ar-BH", "BHD", "د.ب", "🇧🇭",
        dialectInstruction = "تتحدث باللهجة البحرينية الخليجية الطبيعية في المحادثة اليومية (مثل: \"شلونك\"، \"زين\"، \"خوش\") — مش فصحى رسمية."
    ),
    OMAN(
        "عُمان", "OM", "ar-OM", "OMR", "ر.ع", "🇴🇲",
        dialectInstruction = "تتحدث باللهجة العُمانية الخليجية الطبيعية في المحادثة اليومية (مثل: \"كيفك\"، \"زين\"، \"طيب\") — مش فصحى رسمية."
    ),
    JORDAN(
        "الأردن", "JO", "ar-JO", "JOD", "د.أ", "🇯🇴",
        dialectInstruction = "تتحدث باللهجة الأردنية الشامية الطبيعية في المحادثة اليومية (مثل: \"كيفك\"، \"تمام\"، \"يلا\"، \"ولك\") — مش فصحى رسمية."
    ),
    LEBANON(
        "لبنان", "LB", "ar-LB", "LBP", "ل.ل", "🇱🇧",
        dialectInstruction = "تتحدث باللهجة اللبنانية الشامية الطبيعية في المحادثة اليومية (مثل: \"كيفك\"، \"تمام\"، \"يلا\"، \"شو\") — مش فصحى رسمية."
    ),
    IRAQ(
        "العراق", "IQ", "ar-IQ", "IQD", "د.ع", "🇮🇶",
        dialectInstruction = "تتحدث باللهجة العراقية الطبيعية في المحادثة اليومية (مثل: \"شلونك\"، \"زين\"، \"هواي\"، \"شنو\") — مش فصحى رسمية."
    ),
    SYRIA(
        "سوريا", "SY", "ar-SY", "SYP", "ل.س", "🇸🇾",
        dialectInstruction = "تتحدث باللهجة السورية الشامية الطبيعية في المحادثة اليومية (مثل: \"كيفك\"، \"تمام\"، \"يلا\"، \"شو\") — مش فصحى رسمية."
    ),
    YEMEN(
        "اليمن", "YE", "ar-YE", "YER", "ر.ي", "🇾🇪",
        dialectInstruction = "تتحدث باللهجة اليمنية الطبيعية في المحادثة اليومية (مثل: \"كيفك\"، \"طيب\"، \"زين\"، \"يا زول\") — مش فصحى رسمية."
    ),
    PALESTINE(
        "فلسطين", "PS", "ar-PS", "ILS", "₪", "🇵🇸",
        dialectInstruction = "تتحدث باللهجة الفلسطينية الشامية الطبيعية في المحادثة اليومية (مثل: \"كيفك\"، \"تمام\"، \"يلا\"، \"شو\") — مش فصحى رسمية."
    ),
    LIBYA(
        "ليبيا", "LY", "ar-LY", "LYD", "د.ل", "🇱🇾",
        dialectInstruction = "تتحدث باللهجة الليبية المغاربية الطبيعية في المحادثة اليومية (مثل: \"كيفك\"، \"زين\"، \"باهي\") — مش فصحى رسمية."
    ),
    SUDAN(
        "السودان", "SD", "ar-SD", "SDG", "ج.س", "🇸🇩",
        dialectInstruction = "تتحدث باللهجة السودانية الطبيعية في المحادثة اليومية (مثل: \"كيفك\"، \"تمام\"، \"زول\"، \"ياخي\") — مش فصحى رسمية."
    ),
    MOROCCO(
        "المغرب", "MA", "ar-MA", "MAD", "د.م", "🇲🇦",
        dialectInstruction = "تتحدث بالدارجة المغربية الطبيعية في المحادثة اليومية (مثل: \"كيفاش داير\"، \"بخير\"، \"واخا\"، \"زوين\") — مش فصحى رسمية."
    ),
    TUNISIA(
        "تونس", "TN", "ar-TN", "TND", "د.ت", "🇹🇳",
        dialectInstruction = "تتحدث بالدارجة التونسية الطبيعية في المحادثة اليومية (مثل: \"شنية أحوالك\"، \"باهي\"، \"يعطيك الصحة\") — مش فصحى رسمية."
    ),
    ALGERIA(
        "الجزائر", "DZ", "ar-DZ", "DZD", "د.ج", "🇩🇿",
        dialectInstruction = "تتحدث بالدارجة الجزائرية الطبيعية في المحادثة اليومية (مثل: \"كيفاش\"، \"لاباس\"، \"واعر\") — مش فصحى رسمية."
    ),
    TURKEY(
        "تركيا", "TR", "tr-TR", "TRY", "₺", "🇹🇷", useEuropeanNumberFormat = true,
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
     * يتحدّث تلقائياً مع أي getMarket()/setMarket(). Compose state (مش @Volatile var عادي)
     * عشان الشاشات اللي بتعرض العملة (CurrencyFormatter) تتحدث فوراً لما المستخدم يبدّل
     * السوق، من غير ما تحتاج تنتظر recomposition لسبب تاني.
     */
    var currentMarket: Market by androidx.compose.runtime.mutableStateOf(Market.SAUDI_ARABIA)
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

    /** اختبار فقط — يغيّر currentMarket من غير SharedPreferences أو applyLocale */
    internal fun setCurrentMarketForTest(market: Market) {
        currentMarket = market
    }

    /** أول مرة يفتح فيها التطبيق — لسه محددش بلد */
    fun hasSelectedMarket(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(KEY_MARKET)
    }

    /**
     * لازم تتنادى مرة عند بدء التطبيق (بعد getMarket) — من غيرها اختيار البلد
     * بيتخزن بس، ومجلد strings.xml اللي بيتحمل فعلياً بيفضل تابع للغة نظام الجهاز
     * مش لاختيار المستخدم جوه التطبيق.
     *
     * دي بتضبط لغة *التطبيق* لأي حاجة تتفتح بعد كده، بس **مش كفاية لوحدها** للـ Activity
     * اللي شغالة — ده شغل [wrapWithStoredLocale] في attachBaseContext. انظر تعليقها.
     */
    fun applyStoredLocale(context: Context) {
        applyLocale(getMarket(context))
    }

    private fun applyLocale(market: Market) {
        val locales = androidx.core.os.LocaleListCompat.forLanguageTags(market.localeTag)
        androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(locales)
        // Locale.setDefault كمان: الفورماترز (java.time، NumberFormat) مابتقراش من
        // الـ Configuration، بتقرا من الـ default الساكن. من غير السطر ده كنت تلاقي
        // واجهة تركي بتواريخ عربية.
        java.util.Locale.setDefault(java.util.Locale.forLanguageTag(market.localeTag))
    }

    /**
     * بيلف الـ Context بلغة السوق المخزّنة. **لازم** تتنادى من `attachBaseContext`، مش
     * من `onCreate`.
     *
     * ده كان سبب "بختار التركي والتطبيق مايتحولش" — ومكانش نقص ترجمة: `values-tr`
     * مترجمة بالكامل (1011 نص، زي الافتراضي بالظبط). المشكلة إن `MainActivity` هي
     * `ComponentActivity` مش `AppCompatActivity`، يعني مفيش `AppCompatDelegate` بيلف
     * `attachBaseContext` عشان يطبّق اللغة اللي `setApplicationLocales` سجّلها. وفوق كده
     * `applyStoredLocale` كانت بتتنادى جوه `onCreate` — بعد ما الـ Resources بتاعة
     * الـ Activity اتحلّت خلاص. فحتى `recreate()` كان بيرجّع نفس اللغة، لأن الـ Activity
     * الجديدة بتتبني من إعدادات النظام برضه.
     *
     * الحل إن التطبيق يطبّق اللغة بنفسه بدل ما يستنى AppCompat — سطر واحد في
     * attachBaseContext بيخلي كل `stringResource` في الشجرة يقرا من المجلد الصح.
     */
    fun wrapWithStoredLocale(context: Context): Context {
        val market = getMarket(context)
        val locale = java.util.Locale.forLanguageTag(market.localeTag)
        java.util.Locale.setDefault(locale)
        val config = android.content.res.Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }
}
