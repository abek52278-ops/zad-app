package com.example.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.SupabaseRepo
import io.github.jan.supabase.auth.auth

/**
 * AwarenessWorker — حلقة الوعي كل ساعة (نمط cron-jobs في Hermes).
 *
 * المشكلة: العقل (zad-brain) كان بيتنادى مرة واحدة يومياً من MorningSummaryWorker،
 * وZadAlertRouter.sync كان بيشتغل بس لما المستخدم يفتح التطبيق. يعني زاد "بايع"
 * بينفتحين التطبيق — الفاتورة اللي بتستحق النهاردة، الدوا اللي هيخلص، الصنف اللي
 * هيخلص قبل الراتب — مكانش بيشوفهم غير لما العميل يفتح بنفسه.
 *
 * الحل: worker كل ساعة:
 *  1) يصحّي العقل بـ trigger="event" (نفس مسار رؤى zad_insights الموجود).
 *  2) يزامن الرؤى الـ pending → إشعارات نظام + صوت عبر ZadAlertRouter.sync.
 *
 * ملاحظات تصميم:
 * - مش بينفّذ أي فعل مالي بنفسه — الأدوات بتاعة العقل عندها validators وتأكيد،
 *   الـ worker ده بس "بيفتح عين" كل ساعة.
 * - dedupe: zad-brain نفسه بيرفض run daily مكرر خلال 12 ساعة، والرؤى ليها
 *   dedupe_key على مستوى الجدول — فمفيش دوبليكيشن إشعارات.
 * - المستخدم مش متسجل → worker بيقفل بهدوء (بريء).
 */
class AwarenessWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val userId = try {
            SupabaseRepo.client.auth.currentUserOrNull()?.id
        } catch (e: Exception) {
            Log.w(TAG, "auth check failed (offline?): ${e.message}")
            null
        }
        if (userId == null) {
            // مش متسجل — مفيش وعي محتاج. retry مش مطلوب: الجدولة دورية أصلاً.
            return Result.success()
        }

        // 1) نداء العقل — نفس مسار MorningSummaryWorker المجرّب (callEdgeFunction
        //    مع backoff داخلي). trigger="event" مش محكوم بحد الـ 12 ساعة زي daily،
        //    والعقل نفسه بيتقرر يطلع رؤية أو لأ حسب الـ snapshot.
        try {
            SupabaseRepo.callEdgeFunction(
                "zad-brain",
                mapOf("user_id" to userId, "trigger" to "event")
            )
            Log.d(TAG, "brain awareness tick triggered")
        } catch (e: Exception) {
            Log.w(TAG, "zad-brain tick failed (will retry next hour): ${e.message}")
            // ماتفشّلش الساعة كلها لو النت واقع — المزامنة لوحدها لسه قيمة.
        }

        // 2) مزامنة الرؤى → إشعارات/صوت. sync بتاع ZadAlertRouter idempotent بـ dedupe_key.
        try {
            com.zad.agent.ZadAlertRouter.sync(applicationContext, userId)
            Log.d(TAG, "insights synced → notifications/voice")
        } catch (e: Exception) {
            Log.e(TAG, "ZadAlertRouter.sync() failed: ${e.message}")
            return Result.retry()
        }

        return Result.success()
    }

    companion object {
        private const val TAG = "ZadAwarenessWorker"
        const val UNIQUE_NAME = "ZadAwarenessWorker"
    }
}