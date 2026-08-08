package com.example.data

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * تبديل لغة الواجهة (AR/EN) — مستقل عن MarketPrefs (البلد بيحدد لهجة الذكاء الاصطناعي
 * والعملة، مش اتجاه الواجهة). كان الكود ده مكرر Inline جوه OnboardingScreen بس،
 * وLoginScreen كان عنده زرار "AR/EN" شكلي (isArabic محلي) مش موصول بأي حاجة فعلية.
 *
 * ملحوظة: مفيش values-en/strings.xml في المشروع لسه (values/ الافتراضي عربي)، فالتبديل
 * هنا بيأثر على اتجاه الواجهة (RTL/LTR) فوراً، مش على ترجمة النصوص نفسها.
 */
object LocaleHelper {
    fun isArabic(): Boolean {
        val tag = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        return tag.isBlank() || tag.startsWith("ar")
    }

    fun toggleLanguage() {
        setLanguage(if (isArabic()) "en" else "ar")
    }

    fun setLanguage(languageTag: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(languageTag))
    }
}
