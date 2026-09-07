package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * عقد بين [Market] و[CurrencyExchange].
 *
 * الاختبار ده موجود عشان إضافة سوق جديد من غير سعر تبقى **بيلد فاشل** بدل ما تبقى رقم
 * فلوس غلط عند العميل مايظهرش في أي لوج.
 *
 * **الكشف اتغيّر مع تغيير التوقيع.** قبل كده `convert` كانت بترجّع نفس المبلغ لعملة
 * مش معروفة، فالبصمة الوحيدة من بره كانت `convert(1.0, "USD", code) == 1.0`. دلوقتي
 * بترجّع `null`، يعني الشرط القديم عمره ما هيتحقق تاني — الحارس كان هيفضل أخضر وهو
 * مش بيفحص حاجة. الفحص بقى على `rateToUsd(code) == null` مباشرةً.
 */
class CurrencyExchangeCoverageTest {

    @Test
    fun `every supported market currency has an exchange rate`() {
        val missing = Market.entries
            .map { it.currencyCode }
            .distinct()
            .filter { code -> CurrencyExchange.rateToUsd(code) == null }

        assertTrue(
            "أسواق مدعومة من غير سعر صرف: $missing — ضيفهم في CurrencyExchange.usdRate، " +
                "وإلا تبديل العملة هيسيب أرقام البادجت زي ما هي من غير تحويل.",
            missing.isEmpty()
        )
    }

    @Test
    fun `converting between two known currencies actually changes the amount`() {
        // SAR→EGP لازم يعدّي رقم مختلف تماماً، مش نفس الرقم.
        // `!!` مقصودة: عملة مدعومة لازم يبقى ليها سعر، وnull هنا فشل حقيقي مش حالة.
        val converted = CurrencyExchange.convert(1000.0, "SAR", "EGP")!!
        assertTrue("SAR→EGP رجّع نفس الرقم — يعني التحويل مابيشتغلش", converted != 1000.0)
        assertTrue("SAR→EGP المفروض يطلع رقم أكبر بكتير", converted > 1000.0)
    }

    @Test
    fun `same currency is a no-op`() {
        assertEquals(1234.5, CurrencyExchange.convert(1234.5, "SAR", "SAR")!!, 0.0001)
    }

    @Test
    fun `round trip returns close to the original`() {
        val there = CurrencyExchange.convert(500.0, "SAR", "TRY")!!
        val back = CurrencyExchange.convert(there, "TRY", "SAR")!!
        assertEquals(500.0, back, 0.01)
    }

}
