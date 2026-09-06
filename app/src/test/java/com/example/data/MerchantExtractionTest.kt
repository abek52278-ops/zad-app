package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * استخراج التاجر — كان أضعف حلقة في خط الإشعارات وبصفر تغطية.
 *
 * التاجر مش حقل تجميلي: هو اللي بيغذّي عنوان المعاملة و`merchantName`، وعليه بيتبني
 * تحليل الإنفاق على نفس المتجر. لما كان بيرجع "بنده الرياض" مرة و"بنده جدة" مرة،
 * كانوا بيتحسبوا تاجرين مختلفين.
 *
 * النصوص هنا بأشكال الإشعارات الحقيقية: السطري بنقطتين اللي بنوك السعودية بتستخدمه،
 * والسطر الواحد، واللاتيني من شبكات نقاط البيع.
 */
class MerchantExtractionTest {

    // ── القاموس: بيدي الاسم المعتمد مهما كان شكل الكتابة ──────────────────

    @Test
    fun knownMerchantWinsRegardlessOfSpelling() {
        assertEquals("نون", SaBankParser.extractMerchant("شراء من NOON.COM بمبلغ 250 ريال"))
        assertEquals("نون", SaBankParser.extractMerchant("عملية شراء لدى نون"))
        assertEquals("أمازون", SaBankParser.extractMerchant("purchase at AMAZON.SA SAR 120"))
        assertEquals("أمازون", SaBankParser.extractMerchant("شراء من امازون"))
    }

    @Test
    fun deliveryAppsAreRecognised() {
        assertEquals("هنقرستيشن", SaBankParser.extractMerchant("لدى:HUNGERSTATION"))
        assertEquals("هنقرستيشن", SaBankParser.extractMerchant("شراء من هنقر ستيشن بمبلغ 78 ريال"))
        assertEquals("جاهز", SaBankParser.extractMerchant("مدفوعات JAHEZ"))
        assertEquals("مرسول", SaBankParser.extractMerchant("خصم لدى مرسول"))
    }

    @Test
    fun buyNowPayLaterProvidersAreRecognised() {
        assertEquals("تابي", SaBankParser.extractMerchant("تم خصم القسط الأول من tabby"))
        assertEquals("تمارا", SaBankParser.extractMerchant("Tamara: قسط بمبلغ 125 ريال"))
        assertEquals("مدفوع", SaBankParser.extractMerchant("madfu installment"))
    }

    @Test
    fun retailChainsAreRecognised() {
        assertEquals("كارفور", SaBankParser.extractMerchant("شراء من CARREFOUR"))
        assertEquals("بنده", SaBankParser.extractMerchant("مصرف الراجحي: تم خصم بمبلغ 125.50 ريال من حسابك في متجر بنده"))
        assertEquals("العثيم", SaBankParser.extractMerchant("لدى: أسواق العثيم"))
    }

    // ── التداخل: تاجر + مدينة + رقم نقطة بيع ──────────────────────────────

    /**
     * الشكل السطري بنقطتين. النمط القديم كان بيطلب مسافة بعد الكلمة الدالة، فالنقطتين
     * كانت بتمنع المطابقة أصلاً وميرجعش تاجر خالص.
     */
    @Test
    fun colonSeparatedFormIsParsed() {
        val sa = "شراء\nبطاقة:1234\nمبلغ:SAR 89.00\nلدى:مطعم الطازج\nفي:الرياض"
        assertEquals("مطعم الطازج", SaBankParser.extractMerchant(sa))
    }

    /** المدينة اللازقة في آخر الاسم بتتقص — من غير كده نفس المتجر بيتفتّت لتجار. */
    @Test
    fun trailingCityIsStripped() {
        assertEquals("مطعم الشرق", SaBankParser.extractMerchant("لدى: مطعم الشرق الرياض"))
        assertEquals("BOOKSTORE", SaBankParser.extractMerchant("purchase at BOOKSTORE JEDDAH"))
    }

    /** رقم الطرفية/نقطة البيع مش جزء من اسم التاجر. */
    @Test
    fun posTerminalNumberIsStripped() {
        assertEquals("مخبز السنابل", SaBankParser.extractMerchant("لدى: مخبز السنابل 4471"))
        assertEquals("GENERIC STORE", SaBankParser.extractMerchant("at GENERIC STORE POS 88213"))
    }

    /** الاسم والمدينة ورقم النقطة مع بعض في سطر واحد. */
    @Test
    fun merchantCityAndTerminalTogether() {
        assertEquals("صيدلية النهدي", SaBankParser.extractMerchant("لدى: صيدلية النهدي جدة 20551"))
    }

    // ── اللواحق البنكية ────────────────────────────────────────────────────

    @Test
    fun inlineBankTailIsStripped() {
        assertEquals("مطعم البيت", SaBankParser.extractMerchant("شراء من مطعم البيت بمبلغ 45 ريال"))
        assertEquals("محل الورد", SaBankParser.extractMerchant("لدى محل الورد الرصيد المتاح 900 ريال"))
    }

    // ── الرفض ──────────────────────────────────────────────────────────────

    @Test
    fun returnsNullWhenThereIsNoMerchant() {
        assertNull(SaBankParser.extractMerchant("رمز التحقق الخاص بك هو 4521 لا تشاركه مع أحد"))
        assertNull(SaBankParser.extractMerchant("خصم 30 ريال من حسابك"))
    }

    /** اسم كله أرقام مش تاجر. */
    @Test
    fun numericOnlyNameIsRejected() {
        assertNull(SaBankParser.cleanMerchant("4471"))
        assertNull(SaBankParser.cleanMerchant("  8821 "))
    }

    /**
     * "extra" المجردة مقصود إنها مش في القاموس — كلمة إنجليزية شائعة في نص العروض،
     * وإضافتها كانت هتخلي أي إشعار فيه "extra cashback" يتنسب لمتجر إكسترا.
     */
    @Test
    fun commonEnglishWordsDoNotFalselyMatchAStore() {
        assertNull(SaBankParser.extractMerchant("get extra cashback on your next purchase"))
    }

    /** حدود الكلمة بتمنع مطابقة جزئية جوه اسم أطول. */
    @Test
    fun aliasDoesNotMatchInsideALongerWord() {
        assertEquals("سبوتيفاي", SaBankParser.extractMerchant("اشتراك سبوتيفاي الشهري"))
    }
}
