package com.example.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * سقف محاولات التسليم.
 *
 * `attempts` كان بيتزوّد وما بيتقراش في أي سطر في المشروع — فمدخل ما بيوصلش السيرفر
 * يفضل يتعاد كل ٣ ساعات + كل رجوع شبكة + كل مزامنة، للأبد.
 *
 * تسمية مهمة: ده **مش** "السيرفر رفضه". أي رد دلالي من السيرفر بيمسح المدخل فوراً
 * (logged / ignored / ambiguous / needs_classification / awaiting_confirmation).
 * اللي بيوصل للسقف هو اللي **ما وصلش السيرفر أصلاً**: أوفلاين، أو مستخدم مسجّل خروج
 * (currentUserOrNull بترجع null)، أو حالة راجعة مش معروفة للعميل. الاتنين الأخيرين
 * الشبكة فيهم سليمة، وعشان كده الكارت المحلي وردّه بيشتغلوا فعلاً.
 */
class OutboxAttemptCapTest {

    @Test
    fun freshAndMidFlightOpsKeepRetrying() {
        assertFalse(SyncOutbox.isExhausted(0))
        assertFalse(SyncOutbox.isExhausted(1))
        assertFalse(SyncOutbox.isExhausted(SyncOutbox.MAX_DELIVERY_ATTEMPTS - 1))
    }

    @Test
    fun theCapStopsTheRetryLoop() {
        assertTrue(SyncOutbox.isExhausted(SyncOutbox.MAX_DELIVERY_ATTEMPTS))
    }

    /** لو عدّاد اتخطّى السقف لأي سبب، لازم يفضل متوقف مش يرجع يعيد. */
    @Test
    fun countsAboveTheCapStayExhausted() {
        assertTrue(SyncOutbox.isExhausted(SyncOutbox.MAX_DELIVERY_ATTEMPTS + 1))
        assertTrue(SyncOutbox.isExhausted(9_999))
    }

    /**
     * ١٢ محاولة على مدار الـ٣ ساعات بتاعة TransactionSyncWorker = ~٣٦ ساعة كحد أدنى،
     * وأكتر مع محفزات الشبكة. الرقم مش عشوائي، والتست بيثبّت المنطق ده عشان أي تغيير
     * فيه يبقى قرار مكتوب مش تعديل عابر.
     */
    @Test
    fun theCapCoversAtLeastAFullDayAndAHalfOfWorkerRuns() {
        val workerIntervalHours = 3
        assertTrue(SyncOutbox.MAX_DELIVERY_ATTEMPTS * workerIntervalHours >= 36)
    }
}
