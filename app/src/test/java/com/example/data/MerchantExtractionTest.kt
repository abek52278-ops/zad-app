package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

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

/**
 * التصنيف بالتاجر أولاً.
 *
 * `classify` بيقرا **الرسالة كلها**، وده بيخلّيه يتلخبط من نص إعلاني وأسماء بنوك
 * وسطور عروض جوه نفس الإشعار. التاجر حقيقة أضيق وأصدق عن العملية نفسها، فلما يكون
 * معروف بيحسم الفئة قبل ما الكلمات المفتاحية تتكلم.
 */
@RunWith(RobolectricTestRunner::class)
class MerchantFirstClassificationTest {

    // ── التضارب: كلمة من فئة تانية جوه نص العملية ──────────────────────────

    @Test
    fun merchantBeatsAConflictingKeywordInTheSameText() {
        // "صيدلية" بتوقع في الرعاية الصحية لو الكلمات المفتاحية اتكلمت الأول
        val text = "شراء من هنقرستيشن بمبلغ 78 ريال - بجوار صيدلية النهدي"
        assertEquals("المطاعم", SaBankParser.classify(text, TxType.PURCHASE, "هنقرستيشن"))
    }

    @Test
    fun promotionalNoiseDoesNotHijackTheCategory() {
        val text = "شراء من بنده. عرض خاص على المطاعم والكافيهات هذا الأسبوع"
        assertEquals("البقالة", SaBankParser.classify(text, TxType.PURCHASE, "بنده"))
    }

    @Test
    fun rideHailingIsNotFoodEvenWhenTheTextMentionsIt() {
        val text = "خصم لدى كريم - رحلة إلى مطعم الشرق"
        assertEquals("المواصلات", SaBankParser.classify(text, TxType.PURCHASE, "كريم"))
    }

    // ── الرجوع للتحليل النصي ───────────────────────────────────────────────

    @Test
    fun unknownMerchantFallsBackToTextAnalysis() {
        val text = "شراء من مطعم الشرق بمبلغ 60 ريال"
        assertEquals("المطاعم", SaBankParser.classify(text, TxType.PURCHASE, "مطعم الشرق"))
    }

    @Test
    fun nullMerchantBehavesExactlyAsBefore() {
        val text = "شراء من سوبرماركت بمبلغ 60 ريال"
        assertEquals(SaBankParser.classify(text, TxType.PURCHASE), SaBankParser.classify(text, TxType.PURCHASE, null))
    }

    /**
     * التجارة الإلكترونية مقصود إنها بترجع للتحليل النصي: مفيش فئة "تسوق" معتمدة،
     * وشراء من نون ممكن يكون أي حاجة.
     */
    @Test
    fun ecommerceIsLeftToTheText() {
        assertEquals("أخرى", SaBankParser.classify("شراء من نون بمبلغ 250 ريال", TxType.PURCHASE, "نون"))
    }

    // ── النوع بيفضل أقوى من التاجر ─────────────────────────────────────────

    /**
     * الحد اللي وقفت عنده "التاجر أولاً" عن قصد: النوع بيقول دخل ولا مصروف، والتاجر
     * بيقول نوع الإنفاق بس. راتب من شركة اسمها في القاموس لازم يفضل راتب.
     */
    @Test
    fun transactionTypeStillOutranksTheMerchantForIncome() {
        val text = "تم إيداع راتب بمبلغ 8,500 ريال من بنده"
        assertEquals("الراتب", SaBankParser.classify(text, TxType.SALARY, "بنده"))
    }

    @Test
    fun billPaymentStaysABill() {
        assertEquals("الفواتير", SaBankParser.classify("سداد فاتورة لدى كارفور", TxType.BILL_PAYMENT, "كارفور"))
    }

    // ── تناسق القاموسين ────────────────────────────────────────────────────

    /**
     * كل تاجر في جدول الفئات لازم يكون اسم معتمد بيرجّعه extractMerchant فعلاً.
     * مفتاح مكتوب غلط بيبقى مدخل ميت مابيتنفّذش أبداً ومحدش بياخد باله.
     */
    @Test
    fun everyCategorisedMerchantIsReachableFromExtraction() {
        val samples = mapOf(
            "هنقرستيشن" to "لدى hungerstation", "جاهز" to "لدى jahez",
            "مرسول" to "لدى mrsool", "طلبات" to "لدى talabat",
            "بنده" to "لدى panda", "كارفور" to "لدى carrefour",
            "لولو" to "لدى lulu", "العثيم" to "لدى othaim",
            "أوبر" to "لدى uber", "كريم" to "لدى careem",
            "تابي" to "لدى tabby", "تمارا" to "لدى tamara",
            "نتفليكس" to "لدى netflix", "سبوتيفاي" to "لدى spotify",
        )
        samples.forEach { (canonical, text) ->
            assertEquals("الاستخراج لازم يرجّع الاسم المعتمد لـ $canonical",
                canonical, SaBankParser.extractMerchant(text))
        }
    }

    /**
     * الفئة المتخصصة لازم تفرض نفسها عبر المسار الكامل مش بس في classify.
     *
     * النص فيه "صيدلية" كتضارب مقصود، بس **من غير** أي كلمة إعلانية: "عروض" مثلاً
     * كلمة promo وبترمي الإشعار كله كضجيج قبل ما يوصل للاستخراج أصلاً، فالتضارب
     * لازم يتبني من كلمة فئة حقيقية مش من نص عرض.
     */
    @Test
    fun endToEndKeepsTheMerchantCategory() {
        val text = "مصرف الراجحي: تم خصم بمبلغ 78.00 ريال لدى hungerstation بجوار صيدلية النهدي"
        val parsed = SaBankParser.detectAndParse("com.alrajhi.bank", "شراء", text)
        assertEquals("هنقرستيشن", parsed?.merchantName)
        assertEquals("المطاعم", parsed?.category)
    }
}
