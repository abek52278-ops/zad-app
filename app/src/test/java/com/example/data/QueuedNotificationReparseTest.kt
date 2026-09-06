package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * الطابور لازم يستفيد من تحسينات الپارسر بأثر رجعي.
 *
 * مسار الإعادة كان بينادي الـAI على طول ويبعت client_classification="ambiguous"
 * و confidence=0.0 **ثابتين في الكود** — يعني بيقول للسيرفر "مش عارف" في كل محاولة
 * مهما كان الپارسر بقى يعرف. النتيجة: صف حقيقي في الإنتاج عمره ٢١ يوم فضل غامق
 * والپارسر الحالي بيقراه بثقة كاملة.
 *
 * النص هنا هو **نص الصف العالق نفسه** من zad_notification_ingest_events، عشان
 * الحماية تبقى على الحالة اللي حصلت فعلًا مش على حالة متخيّلة.
 */
@RunWith(RobolectricTestRunner::class)
class QueuedNotificationReparseTest {

    private val stuckRowTitle = "BDC"
    private val stuckRowText =
        "تم قيد معاملة مشتريات ب 245.24 EGP من AMAZON Ca علي بطاقة رقم  9766 " +
            "في 16/08 16:41 ورصيدكم الحالي 10263.00"

    /**
     * الصف ده اتصفّ غامض. الپارسر الحالي بيحسمه — ولولا الإصلاح ده كانت الإعادة
     * هتفضل تبعت "ambiguous" للأبد رغم كده.
     */
    @Test
    fun theRealStuckNotificationNowParsesAsCompleted() {
        val result = SaBankParser.classifyNotification(
            "com.google.android.apps.messaging", stuckRowTitle, stuckRowText
        )
        assertEquals(NotificationClassification.COMPLETED_TRANSACTION, result.classification)
        assertNotNull(result.transaction)
    }

    /** المبلغ الصح مش الرصيد — 245.24 مش 10263.00. */
    @Test
    fun theAmountIsThePurchaseNotTheBalance() {
        val tx = SaBankParser.detectAndParse(
            "com.google.android.apps.messaging", stuckRowTitle, stuckRowText
        )
        assertEquals(245.24, tx!!.amount, 0.001)
        assertEquals("EGP", tx.currency)
        assertTrue(tx.isExpense)
    }

    /** التاجر من قاموس النهارده — "AMAZON Ca" اسم مقصوص وبرضه بيتعرف. */
    @Test
    fun theMerchantComesFromTheDictionary() {
        val tx = SaBankParser.detectAndParse(
            "com.google.android.apps.messaging", stuckRowTitle, stuckRowText
        )
        assertEquals("أمازون", tx?.merchantName)
    }

    /**
     * الثقة لازم تعدّي بوابة الكتابة عند السيرفر (≥ 0.9)، وإلا الإصلاح بيوصّل
     * التصنيف الصح ومع ذلك مايتسجلش.
     */
    @Test
    fun confidenceClearsTheServerWriteGate() {
        val tx = SaBankParser.detectAndParse(
            "com.google.android.apps.messaging", stuckRowTitle, stuckRowText
        )
        assertTrue("الثقة ${tx?.confidence} لازم تكون ≥ 0.9", (tx?.confidence ?: 0f) >= 0.9f)
    }

    /**
     * والاتجاه التاني لسه شغال: إشعار الپارسر لسه مش قادر عليه بيفضل يروح للـAI
     * ومسار التأكيد، مش بيتحوّل لـ"completed" بالغلط.
     */
    @Test
    fun aStillUnreadableNotificationStaysOnTheConfirmationPath() {
        val result = SaBankParser.classifyNotification(
            "com.unknown.app", "تنبيه", "تمت عملية على حسابك"
        )
        assertTrue(
            "المفروض ماتكونش COMPLETED",
            result.classification != NotificationClassification.COMPLETED_TRANSACTION
        )
    }
}
