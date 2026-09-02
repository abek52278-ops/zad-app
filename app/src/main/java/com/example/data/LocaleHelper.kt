package com.example.data

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * تبديل لغة الواجهة — مستقل عن MarketPrefs (البلد بيحدد لهجة الذكاء الاصطناعي والعملة،
 * مش لغة الواجهة). كان الكود ده مكرر inline جوه OnboardingScreen بس، وLoginScreen كان
 * عنده زرار "AR/EN" شكلي (isArabic محلي) مش موصول بأي حاجة فعلية.
 *
 * اللغات هنا لازم تفضل مطابقة لـ res/xml/locales_config.xml ولمجلدات values-* اللي
 * موجودة فعلاً. إعلان لغة مالهاش مجلد معناه واجهة نصها عربي، وإخفاء لغة ليها مجلد
 * معناه ترجمة اتكتبت وماحدش يقدر يوصلها — وده اللي كان حاصل للتركي: values-tr مترجم
 * بالكامل (١٠١١ نص) والشاشة الوحيدة اللي فيها اختيار لغة كانت بتعرض عربي/إنجليزي بس.
 */
object LocaleHelper {

    /** كود اللغة، الاسم المعروض بلغته هو نفسها. الترتيب هو ترتيب العرض. */
    val supported: List<Pair<String, String>> = listOf(
        "ar" to "العربية",
        "en" to "English",
        "tr" to "Türkçe",
    )

    /**
     * اللغة المختارة حالياً. فاضية معناها "زي النظام"، والافتراضي وقتها عربي لأن
     * values/ نفسه عربي.
     */
    fun currentLanguage(): String {
        val tag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        if (tag.isBlank()) return "ar"
        return supported.firstOrNull { tag.startsWith(it.first) }?.first ?: "ar"
    }

    /** لاتجاه الواجهة (RTL) بس — مش لاختيار اللغة، دي بقت [currentLanguage]. */
    fun isArabic(): Boolean = currentLanguage() == "ar"

    /**
     * التنقل بين اللغات بالترتيب. الشاشات اللي فيها زرار واحد (تسجيل الدخول، الترحيب)
     * بتستخدمها؛ كانت بتقلب بين اتنين بس فماكانش فيه طريق للتركي من هناك خالص.
     */
    fun toggleLanguage(context: android.content.Context) {
        val index = supported.indexOfFirst { it.first == currentLanguage() }
        setLanguage(context, supported[(index + 1) % supported.size].first)
    }

    /**
     * اختيار العميل الصريح للغة. بيسجّل علامة دائمة كمان، مش بس بيكتب في
     * AppCompatDelegate.
     *
     * السبب: `MarketProfile.applyLocale(market)` بيكتب في **نفس** المخزن ده لما السوق
     * يتغير. فبعد ما يتكتب فيه أي حاجة، `applyStoredLocale` كان بيشوف قيمة موجودة
     * ويستنتج "العميل اختار لغة بنفسه" ويسيبها زي ما هي للأبد — حتى لو اللي كتبها هو
     * التطبيق نفسه من السوق الافتراضي (السعودية). النتيجة: حساب سوقه مصر بيفضل يقرا
     * من `values-ar-rSA` ("هلا يا فلان" بدل "إزيك يا فلان")، والعملة بتبقى صح لأنها
     * بتتقري من MarketPrefs مباشرة — فتطلع واجهة سعودية بأرقام مصرية.
     *
     * العلامة دي هي الفرق بين "العميل قال عايز إنجليزي" و"التطبيق طبّق لغة السوق".
     */
    fun setLanguage(context: android.content.Context, languageTag: String) {
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_USER_CHOSE, true).apply()
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag))
    }

    /** هل العميل اختار اللغة بنفسه؟ لو لأ، لغة السوق هي المرجع وبتتطبّق مع كل تغيير سوق. */
    fun userChoseLanguage(context: android.content.Context): Boolean =
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .getBoolean(KEY_USER_CHOSE, false)

    private const val PREFS = "zad_locale_prefs"
    private const val KEY_USER_CHOSE = "user_chose_language"
}
