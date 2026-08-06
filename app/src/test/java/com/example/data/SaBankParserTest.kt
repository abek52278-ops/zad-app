package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * اختبارات وحدة نقية لمحرك [SaBankParser] — بدون Robolectric shadow لأي Android API
 * غير android.util.Log (المُستخدم داخل detectAndParse للتسجيل فقط).
 * عينات الرسائل مأخوذة من صيغ إشعارات بنوك سعودية حقيقية (الراجحي، الأهلي، الإنماء، الرياض، ساب، STC Pay).
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SaBankParserTest {

    // ─── extractAmount ─────────────────────────────────────────────

    @Test
    fun `extractAmount picks labeled amount over balance`() {
        val text = "مصرف الراجحي: تم خصم بمبلغ 125.50 ريال من حسابك في متجر بنده. الرصيد المتاح: 3,450.00 ريال"
        assertEquals(125.50, SaBankParser.extractAmount(text)!!, 0.001)
    }

    @Test
    fun `extractAmount excludes balance-context number`() {
        val text = "خصم 30 ريال من حسابك. الرصيد المتاح: 970 ريال"
        assertEquals(30.0, SaBankParser.extractAmount(text)!!, 0.001)
    }

    @Test
    fun `extractAmount normalizes arabic-indic digits`() {
        val text = "خصم ١٢٥.٥٠ ريال من بطاقتك"
        assertEquals(125.50, SaBankParser.extractAmount(text)!!, 0.001)
    }

    @Test
    fun `extractAmount handles thousands separator`() {
        val text = "مصرف الإنماء: تم إيداع راتب بمبلغ 8,500.00 ريال في حسابك. الرصيد الحالي: 12,300.00 ريال"
        assertEquals(8500.00, SaBankParser.extractAmount(text)!!, 0.001)
    }

    @Test
    fun `extractAmount returns null when no amount present`() {
        val text = "رمز التحقق الخاص بك هو 4521 لا تشاركه مع أحد"
        // مفيش مبلغ بعملة صريح — الرقم ده كود OTP مش مبلغ
        assertNull(SaBankParser.extractAmount(text))
    }

    // ─── extractCurrency — مرحلة ١ (docs/agent/PLAN_2026_08_06_rebuild.md) ─────────

    @Test
    fun `extractCurrency reads SAR from a Saudi riyal message`() {
        val text = "مصرف الراجحي: تم خصم بمبلغ 125.50 ريال من حسابك في متجر بنده"
        assertEquals("SAR", SaBankParser.extractCurrency(text))
    }

    @Test
    fun `extractCurrency reads EGP from an Egyptian pound message`() {
        val text = "تم خصم مبلغ 300 ج.م من حسابك لدى بنك مصر"
        assertEquals("EGP", SaBankParser.extractCurrency(text))
    }

    @Test
    fun `extractCurrency reads TRY from a Turkish lira message`() {
        val text = "kartınızdan 150,00 TL tutarında harcama yapıldı"
        assertEquals("TRY", SaBankParser.extractCurrency(text))
    }

    @Test
    fun `extractCurrency returns null when no currency token is present`() {
        val text = "خصم 30 من حسابك اليوم"
        assertNull(SaBankParser.extractCurrency(text))
    }

    // ─── detectAndParse: noise filtering ───────────────────────────

    @Test
    fun `detectAndParse ignores OTP message`() {
        val result = SaBankParser.detectAndParse(
            "alrajhi", "الراجحي",
            "رمز التحقق الخاص بك هو 4521 لا تشاركه مع أحد"
        )
        assertNull(result)
    }

    @Test
    fun `detectAndParse ignores declined transaction`() {
        val result = SaBankParser.detectAndParse(
            "alrajhi", "الراجحي",
            "عملية الشراء بمبلغ 200 ريال لم تتم بسبب رصيد غير كاف"
        )
        assertNull(result)
    }

    @Test
    fun `detectAndParse ignores promotional offer`() {
        val result = SaBankParser.detectAndParse(
            "alrajhi", "الراجحي",
            "عرض خاص! احصل على كاش باك يصل الى 20% عند الشراء الآن"
        )
        assertNull(result)
    }

    @Test
    fun `detectAndParse ignores password reset message`() {
        val result = SaBankParser.detectAndParse(
            "alrajhi", "الراجحي",
            "تم تغيير كلمة المرور الخاصة بحسابك. لو مكنتش انت تواصل معنا فوراً"
        )
        assertNull(result)
    }

    @Test
    fun `detectAndParse ignores expired card message`() {
        val result = SaBankParser.detectAndParse(
            "alrajhi", "الراجحي",
            "بطاقتك المستخدمة بمبلغ 150 ريال انتهت صلاحيتها، يرجى تحديث بياناتك"
        )
        assertNull(result)
    }

    @Test
    fun `rejectionReason classifies each noise category correctly`() {
        assertEquals(SaBankParser.RejectReason.OTP, SaBankParser.rejectionReason("رمز التحقق الخاص بك هو 4521"))
        assertEquals(SaBankParser.RejectReason.OTP, SaBankParser.rejectionReason("لا تشارك كلمة المرور مع أحد"))
        assertEquals(SaBankParser.RejectReason.DECLINED, SaBankParser.rejectionReason("العملية لم تتم بسبب رصيد غير كاف"))
        assertEquals(SaBankParser.RejectReason.EXPIRED, SaBankParser.rejectionReason("بطاقتك انتهت صلاحيتها"))
        assertEquals(SaBankParser.RejectReason.PROMO, SaBankParser.rejectionReason("عرض خاص! خصم يصل الى 20%"))
        assertNull(SaBankParser.rejectionReason("تم خصم 50 ريال من حسابك لدى بنده"))
    }

    @Test
    fun `detectAndParse returns null when no explicit tx type keyword`() {
        val result = SaBankParser.detectAndParse("unknown", "تنبيه", "بمبلغ 75.00 ريال")
        assertNull(result)
    }

    // ─── detectAndParse: real bank samples ──────────────────────────

    @Test
    fun `Al Rajhi purchase at grocery store`() {
        val result = SaBankParser.detectAndParse(
            "com.alrajhi.bank", "الراجحي",
            "تم خصم مبلغ 125.50 ريال من حسابك لدى بنده. الرصيد المتاح: 3,450.00 ريال"
        )!!
        assertEquals(125.50, result.amount, 0.001)
        assertTrue(result.isExpense)
        assertEquals("الراجحي", result.bankName)
        assertEquals(TxType.PURCHASE, result.txType)
        assertEquals("البقالة", result.category)
        assertEquals("SAR", result.currency)
    }

    @Test
    fun `SNB cash withdrawal from ATM`() {
        val result = SaBankParser.detectAndParse(
            "com.snb", "البنك الأهلي السعودي",
            "تم سحب مبلغ 500.00 ريال من صراف آلي. الرصيد بعد العملية: 1,200.00 ريال"
        )!!
        assertEquals(500.0, result.amount, 0.001)
        assertTrue(result.isExpense)
        assertEquals("الأهلي السعودي", result.bankName)
        assertEquals(TxType.WITHDRAWAL, result.txType)
    }

    @Test
    fun `Alinma salary deposit is income`() {
        val result = SaBankParser.detectAndParse(
            "com.alinma.bank", "مصرف الإنماء",
            "تم إيداع راتب بمبلغ 8,500.00 ريال في حسابك. الرصيد الحالي: 12,300.00 ريال"
        )!!
        assertEquals(8500.0, result.amount, 0.001)
        assertTrue(!result.isExpense)
        assertEquals("مصرف الإنماء", result.bankName)
        assertEquals(TxType.SALARY, result.txType)
        assertEquals("الراتب", result.category)
    }

    @Test
    fun `Riyad Bank purchase at restaurant categorized correctly`() {
        val result = SaBankParser.detectAndParse(
            "com.riyadbank", "بنك الرياض",
            "عملية شراء بقيمة 89.90 ريال لدى ستاربكس بواسطة مدى"
        )!!
        assertEquals(89.90, result.amount, 0.001)
        assertTrue(result.isExpense)
        assertEquals("بنك الرياض", result.bankName)
        assertEquals(TxType.PURCHASE, result.txType)
        assertEquals("المطاعم", result.category)
    }

    @Test
    fun `SABB outgoing transfer`() {
        val result = SaBankParser.detectAndParse(
            "com.sabb", "ساب",
            "تم تحويل مبلغ 1,000.00 ريال إلى حساب آخر. الرصيد المتاح: 5,000.00 ريال"
        )!!
        assertEquals(1000.0, result.amount, 0.001)
        assertTrue(result.isExpense)
        assertEquals("ساب", result.bankName)
        assertEquals(TxType.TRANSFER_OUT, result.txType)
    }

    @Test
    fun `STC Pay purchase categorized as transport`() {
        val result = SaBankParser.detectAndParse(
            "com.stcpay", "STC Pay",
            "تم دفع 45.00 ريال لدى أوبر"
        )!!
        assertEquals(45.0, result.amount, 0.001)
        assertTrue(result.isExpense)
        assertEquals("stc pay", result.bankName)
        assertEquals(TxType.PURCHASE, result.txType)
        assertEquals("المواصلات", result.category)
    }

    @Test
    fun `Tabby always classified as installment regardless of keyword`() {
        val result = SaBankParser.detectAndParse(
            "tabby", "تابي",
            "دفعة من 4 بمبلغ 62.25 ريال لدى نمشي"
        )!!
        assertEquals(TxType.INSTALLMENT, result.txType)
        assertEquals("الأقساط", result.category)
        assertEquals("تابي", result.bankName)
    }

    @Test
    fun `refund is income not expense`() {
        val result = SaBankParser.detectAndParse(
            "com.alrajhi.bank", "الراجحي",
            "تم استرداد مبلغ 99.00 ريال إلى حسابك من متجر نون"
        )!!
        assertEquals(99.0, result.amount, 0.001)
        assertTrue(!result.isExpense)
        assertEquals(TxType.REFUND, result.txType)
    }
}
