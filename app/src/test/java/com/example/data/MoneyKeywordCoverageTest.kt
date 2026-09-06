package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * بوابة "الإشعار ده بيتكلم عن فلوس؟" — أول نص فلتر `isFinancialNotification`.
 *
 * البوابة دي حارسة القناة البنكية الأساسية: رسايل البنك بتوصل من تطبيق المراسلة
 * وهو **مش** في trackedPackages (شوف docs/agent/BANK_NOTIFICATION_CHANNEL.md)، يعني
 * الرسالة بتعدّي إما من هنا وإما من الالتقاط الشامل المحدود بسقف يومي.
 *
 * النصوص هنا بصيغ بنوك حقيقية لأسواق التطبيق. المعلّمة (حقيقي) نصوص إنتاج أو نصوص
 * موجودة في تستات الريبو؛ الباقي مصاغ على أنماط شائعة.
 */
class MoneyKeywordCoverageTest {

    // ── نصوص حقيقية ────────────────────────────────────────────────────────

    @Test
    fun realProductionAndFixtureTextsAllPass() {
        val real = listOf(
            "تم قيد معاملة مشتريات ب 245.24 EGP من AMAZON Ca علي بطاقة رقم 9766 ورصيدكم الحالي 10263.00",
            "مصرف الراجحي: تم خصم بمبلغ 125.50 ريال من حسابك في متجر بنده. الرصيد المتاح: 3,450.00 ريال",
            "مصرف الإنماء: تم إيداع راتب بمبلغ 8,500.00 ريال في حسابك",
            "Your card ending 1234 SAR 75.00 was charged",
            "kartınızdan 150,00 TL tutarında harcama yapıldı",
        )
        real.forEach { assertTrue("سقط: $it", SaBankParser.mentionsMoney(it)) }
    }

    // ── صيغ الأسواق المستهدفة ──────────────────────────────────────────────

    @Test
    fun gulfAndEgyptianPhrasingsPass() {
        val samples = listOf(
            "عملية سحب نقدي 500 ر.س من صراف آلي",
            "شراء نقاط البيع 89.00 ر.س لدى تميمي",
            "عملية مدى بقيمة 45.00 ر.س",
            "دفعة أبل باي 120.00 ر.س",
            "Tabby: قسطك الأول 125.00 ر.س",
            "تمارا: تم تحصيل 200.00 ر.س",
            "تم استقطاع عمولة 15.00 ر.س",
            "Refund of SAR 60.00 has been processed",
            "تم عكس عملية بقيمة 75.00 ر.س",
            "وصلتك حوالة 1,000.00 ر.س",
            "تم ايداع 5,000.00 ج.م بحسابك",
            "تم خصم 350.00 ج.م من بطاقتك",
            "InstaPay: تم تحويل 500.00 ج.م",
            "فودافون كاش: تم استلام 250.00 ج.م",
            "AED 320.00 debited from your account",
            "تم سحب 45.000 د.ك من حسابك",
            "QAR 180.00 purchase at Lulu",
            "stc pay: تم الدفع 60.00 ر.س",
            "سداد فاتورة الكهرباء 430.00 ر.س",
            "POS purchase SAR 210.00",
            "You spent SAR 95.00 at Jarir",
        )
        samples.forEach { assertTrue("سقط: $it", SaBankParser.mentionsMoney(it)) }
    }

    // ── الفجوة اللي اتقفلت: عملات عالمية ──────────────────────────────────

    /**
     * الأربعة دول كانوا **بيسقطوا بالكامل** قبل 2026-09-06: القايمة كان فيها ١٩ عملة
     * إقليمية وصفر عملة عالمية، فمشتريات الإنترنت بالدولار ماكانتش توصل الپارسر أصلاً.
     */
    @Test
    fun globalCurrenciesNowPassTheGate() {
        listOf(
            "\$45.00 - Netflix",
            "USD 120.00 - Amazon.com",
            "EUR 89.00 Booking.com",
            "GBP 30.00",
            "تم استقطاع 45 دولار",
        ).forEach { assertTrue("سقط: $it", SaBankParser.mentionsMoney(it)) }
    }

    /**
     * والمهم إن البوابة التانية بتشتغل كمان: إضافة الكلمة لوحدها ماكانتش تنفع لأن
     * CUR في الپارسر كان فيه نفس الثغرة، فالمبلغ ماكانش يتقرا والإشعار يسقط بعد خطوة.
     */
    @Test
    fun globalCurrencyAmountsAreActuallyExtractable() {
        assertEquals(45.0, SaBankParser.extractAmount("\$45.00 - Netflix")!!, 0.001)
        assertEquals(120.0, SaBankParser.extractAmount("USD 120.00 - Amazon.com")!!, 0.001)
        assertEquals(89.0, SaBankParser.extractAmount("EUR 89.00 Booking.com")!!, 0.001)
        assertEquals(45.0, SaBankParser.extractAmount("تم استقطاع 45 دولار")!!, 0.001)
    }

    @Test
    fun globalCurrencyCodesAreReported() {
        assertEquals("USD", SaBankParser.extractCurrency("USD 120.00 - Amazon.com"))
        assertEquals("USD", SaBankParser.extractCurrency("\$45.00 - Netflix"))
        assertEquals("EUR", SaBankParser.extractCurrency("EUR 89.00 Booking.com"))
        assertEquals("GBP", SaBankParser.extractCurrency("GBP 30.00"))
    }

    // ── اللي المفروض يفضل ساقط ────────────────────────────────────────────

    /**
     * إشعار رصيد مش معاملة. سقوطه **مطلوب**، ولو عدّى يبقى التوسيع بوّظ حاجة.
     */
    @Test
    fun aBalanceOnlyNotificationIsStillRejected() {
        assertFalse(SaBankParser.mentionsMoney("Available: 3,450.00"))
    }

    /**
     * "pos" و"card" مقصود إنهم مش في القايمة: المطابقة `contains` بلا حدود كلمات،
     * فـ"pos" بتطابق "post"/"possible" — نفس فخ "extra" في قاموس التجار.
     */
    @Test
    fun shortTokensThatWouldMatchOrdinaryWordsAreAbsent() {
        assertFalse(SaBankParser.moneyKeywords.contains("pos"))
        assertFalse(SaBankParser.moneyKeywords.contains("card"))
        assertFalse(SaBankParser.mentionsMoney("Your post was published"))
        assertFalse(SaBankParser.mentionsMoney("It is possible to continue"))
    }
}
