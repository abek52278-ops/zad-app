package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * بوابة إرسال الإشعار لـ zad-brain.
 *
 * قبلها كان كل إشعار مش COMPLETED بيتبعت. الأثر المقاس على الإنتاج: إشعار من تطبيق
 * الصور فيه رقم شبه مبلغ وصل السيرفر، والسيرفر مقدرش يستخرج منه مبلغ هو كمان، فكتب
 * سؤال للعميل عنوانه "معاملة بنكية محتاجة تأكيد" بمبلغ "غير واضح". اتنين من ست
 * عمليات استيعاب حقيقية كانوا الحالة دي بالظبط.
 *
 * المصفوفة كاملة هنا: تصنيف × سبب رفض × متتبَّع/غير متتبَّع.
 */
class BrainIngestGateTest {

    private fun gate(
        c: NotificationClassification,
        reason: SaBankParser.RejectReason? = null,
        tracked: Boolean = false,
    ) = SaBankParser.shouldSendToBrain(c, reason, tracked)

    // ── الغامض: دايمًا يتبعت، هو سبب وجود المسار أصلًا ────────────────────

    @Test
    fun ambiguousAlwaysGoesToTheBrain() {
        assertTrue(gate(NotificationClassification.AMBIGUOUS, tracked = true))
        assertTrue(gate(NotificationClassification.AMBIGUOUS, tracked = false))
    }

    // ── الفاشل/المعلّق: حدث مالي حصل فعلًا ────────────────────────────────

    @Test
    fun failedOrPendingStillGoes() {
        assertTrue(gate(NotificationClassification.FAILED_OR_PENDING_TRANSACTION, tracked = true))
        assertTrue(gate(NotificationClassification.FAILED_OR_PENDING_TRANSACTION, tracked = false))
        assertTrue(
            gate(NotificationClassification.FAILED_OR_PENDING_TRANSACTION, SaBankParser.RejectReason.DECLINED, true)
        )
        assertTrue(
            gate(NotificationClassification.FAILED_OR_PENDING_TRANSACTION, SaBankParser.RejectReason.PENDING, false)
        )
    }

    // ── القاعدة 1: سبب رفض معروف = دليل إيجابي إنها مش معاملة ─────────────

    @Test
    fun knownNoiseNeverGoesEvenFromABank() {
        listOf(
            SaBankParser.RejectReason.OTP,
            SaBankParser.RejectReason.PROMO,
            SaBankParser.RejectReason.EXPIRED,
        ).forEach { reason ->
            assertFalse("$reason من حزمة متتبَّعة", gate(NotificationClassification.INFORMATIONAL_ONLY, reason, true))
            assertFalse("$reason من حزمة غير متتبَّعة", gate(NotificationClassification.INFORMATIONAL_ONLY, reason, false))
        }
    }

    // ── القاعدة 2: بلا مبلغ ومن حزمة مش مالية ─────────────────────────────

    /** حالة تطبيق الصور بالظبط — دي اللي كانت بتحرق الكوتة وتزعج العميل. */
    @Test
    fun untrackedAppWithNoAmountIsDropped() {
        assertFalse(gate(NotificationClassification.INFORMATIONAL_ONLY, null, tracked = false))
    }

    /**
     * حد الأمان: نفس التصنيف بالظبط، بس من حزمة بنكية — بيعدّي.
     * صيغة بنك غريبة مالهاش سبب رفض تستاهل ذكاء السيرفر.
     */
    @Test
    fun trackedBankWithNoAmountStillGetsServerIntelligence() {
        assertTrue(gate(NotificationClassification.INFORMATIONAL_ONLY, null, tracked = true))
    }

    // ── المكتملة: ليها نداء منفصل ──────────────────────────────────────────

    @Test
    fun completedIsNotThisGatesBusiness() {
        assertFalse(gate(NotificationClassification.COMPLETED_TRANSACTION, tracked = true))
        assertFalse(gate(NotificationClassification.COMPLETED_TRANSACTION, tracked = false))
    }

    // ── المصفوفة كاملة، مسرودة صراحة ───────────────────────────────────────

    /**
     * كل تركيبة مذكورة بنتيجتها. لو حد غيّر البوابة، التست ده بيقول **أنهي** خانة
     * اتحركت مش بس إن حاجة وقعت.
     */
    @Test
    fun theWholeMatrixIsPinned() {
        val reasons = listOf(null, SaBankParser.RejectReason.OTP, SaBankParser.RejectReason.PROMO)
        val expected = mapOf(
            // تصنيف to (متتبَّع, بلا سبب) to (متتبَّع, بسبب) to (غير متتبَّع, بلا سبب) to (غير متتبَّع, بسبب)
            NotificationClassification.COMPLETED_TRANSACTION to listOf(false, false, false, false),
            NotificationClassification.AMBIGUOUS to listOf(true, true, true, true),
            NotificationClassification.FAILED_OR_PENDING_TRANSACTION to listOf(true, true, true, true),
            NotificationClassification.INFORMATIONAL_ONLY to listOf(true, false, false, false),
        )
        expected.forEach { (c, exp) ->
            assertEquals("$c متتبَّع بلا سبب", exp[0], gate(c, null, true))
            assertEquals("$c متتبَّع بسبب", exp[1], gate(c, reasons[1], true))
            assertEquals("$c غير متتبَّع بلا سبب", exp[2], gate(c, null, false))
            assertEquals("$c غير متتبَّع بسبب", exp[3], gate(c, reasons[2], false))
        }
    }
}

/**
 * إغلاق الحلقة: من نص إشعار حقيقي، خلال التصنيف الحقيقي، للبوابة.
 *
 * التستات فوق بتفحص البوابة كدالة. دي بتفحص إن التصنيف الحقيقي بيوصّل للخانة الصح
 * في المصفوفة — لأن بوابة سليمة بتتغذّى تصنيف غلط لسه بتبعت.
 *
 * Robolectric لأن classifyNotification بيمر على detectAndParse اللي بينادي android.util.Log.
 */
@RunWith(RobolectricTestRunner::class)
class BrainIngestGateEndToEndTest {

    private fun wouldSend(pkg: String, title: String, text: String, tracked: Boolean): Boolean {
        val result = SaBankParser.classifyNotification(pkg, title, text)
        return SaBankParser.shouldSendToBrain(result.classification, result.rejectionReason, tracked)
    }

    /** الحالة اللي اتشافت في الإنتاج مرتين: تطبيق صور فيه رقم شبه مبلغ. */
    @Test
    fun photosNotificationWithANumberIsNoLongerSent() {
        assertFalse(
            wouldSend(
                "com.google.android.apps.photos",
                "ذكريات",
                "لديك 1,250 صورة جديدة من هذا الأسبوع",
                tracked = false,
            )
        )
    }

    @Test
    fun otpFromARealBankIsNoLongerSent() {
        assertFalse(
            wouldSend(
                "com.alrajhi.bank",
                "مصرف الراجحي",
                "رمز التحقق الخاص بك هو 4521 لا تشاركه مع أحد",
                tracked = true,
            )
        )
    }

    @Test
    fun promotionalBankMessageIsNoLongerSent() {
        assertFalse(
            wouldSend(
                "com.alrajhi.bank",
                "مصرف الراجحي",
                "عرض خاص! احصل على كاش باك يصل الى 20% عند الشراء الآن",
                tracked = true,
            )
        )
    }

    /** المسار القيّم لسه مفتوح: مبلغ واضح بثقة أقل من عتبة الكتابة = غامض = يتبعت. */
    @Test
    fun aRealAmbiguousBankMessageStillReachesTheBrain() {
        assertTrue(
            wouldSend(
                "com.unknown.wallet",
                "تنبيه",
                "تمت عملية بمبلغ 125.50 ريال",
                tracked = false,
            )
        )
    }

    /** والمعاملة الفاشلة برضه — دي حدث مالي حصل. */
    @Test
    fun declinedTransactionStillReachesTheBrain() {
        assertTrue(
            wouldSend(
                "com.alrajhi.bank",
                "مصرف الراجحي",
                "عملية الشراء بمبلغ 200 ريال لم تتم بسبب رصيد غير كاف",
                tracked = true,
            )
        )
    }
}
