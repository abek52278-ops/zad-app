package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * عقد بين [Market] و[CurrencyExchange].
 *
 * `CurrencyExchange.rateToUsd` بيرجّع 1.0 لأي عملة مش في الجدول، و`convert` بتبني على
 * ده فبترجّع **نفس المبلغ** — يعني تبديل عملة على سوق مالوش سعر بيخلّي ١٠٠٠ ريال تبقى
 * ١٠٠٠ جنيه بالظبط، وهو الباج اللي `convertLimitsForMarketChange` أصلاً اتعملت عشان
 * تمنعه. الفشل ده صامت تماماً: مفيش استثناء، ومفيش لوج، والرقم غلط بس.
 *
 * كل الـ١٩ عملة المدعومة عندها سعر النهارده، فالمسار ده مش بيتفتح دلوقتي. الاختبار ده
 * موجود عشان يفضل كده: إضافة سوق جديد من غير سعر تبقى **بيلد فاشل** بدل ما تبقى رقم
 * فلوس غلط عند العميل مايظهرش في أي لوج.
 */
class CurrencyExchangeCoverageTest {

    @Test
    fun `every supported market currency has an exchange rate`() {
        val missing = Market.entries
            .map { it.currencyCode }
            .distinct()
            .filter { code ->
                // لو مفيش سعر، التحويل من دولار بيرجّع نفس الرقم — دي البصمة الوحيدة
                // اللي نقدر نشوفها من بره من غير ما نفتح الخريطة الخاصة.
                CurrencyExchange.convert(1.0, "USD", code) == 1.0 && code != "USD"
            }

        assertTrue(
            "أسواق مدعومة من غير سعر صرف: $missing — ضيفهم في CurrencyExchange.usdRate، " +
                "وإلا تبديل العملة هيسيب أرقام البادجت زي ما هي من غير تحويل.",
            missing.isEmpty()
        )
    }

    @Test
    fun `converting between two known currencies actually changes the amount`() {
        // SAR→EGP لازم يعدّي رقم مختلف تماماً، مش نفس الرقم.
        val converted = CurrencyExchange.convert(1000.0, "SAR", "EGP")
        assertTrue("SAR→EGP رجّع نفس الرقم — يعني التحويل مابيشتغلش", converted != 1000.0)
        assertTrue("SAR→EGP المفروض يطلع رقم أكبر بكتير", converted > 1000.0)
    }

    @Test
    fun `same currency is a no-op`() {
        assertEquals(1234.5, CurrencyExchange.convert(1234.5, "SAR", "SAR"), 0.0001)
    }

    @Test
    fun `round trip returns close to the original`() {
        val there = CurrencyExchange.convert(500.0, "SAR", "TRY")
        val back = CurrencyExchange.convert(there, "TRY", "SAR")
        assertEquals(500.0, back, 0.01)
    }
}
