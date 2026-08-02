package com.example.workers

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.ZadCentralBrain
import com.example.data.ZadNotifier
import io.github.jan.supabase.auth.auth
import com.example.data.buildZadFamilyState
import com.example.data.local.ZadDatabase
import kotlinx.coroutines.flow.first

class PeriodicAnalysisWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.d("ZadWorker", "PeriodicAnalysisWorker started!")
        try {
            val dao = ZadDatabase.getDatabase(applicationContext).zadDao()
            // ZadFamilyState covers inventory+pharmacy (its documented single-source-of-truth
            // scope) — subscriptions/behaviorPatterns aren't part of its schema (see
            // FamilyState.kt), so those two still come from the DAO directly.
            val familyState = buildZadFamilyState(dao, applicationContext)
            val inventory = familyState.inventory.items
            val pharmacyItems = familyState.pharmacy.activeMeds
            val transactions = dao.getAllTransactions().first()
            val subscriptions = dao.getAllSubscriptions().first()
            val behaviorPatterns = dao.getBehaviorPatterns()

            // ====== RUN ENHANCED BRAIN ======
            // Read the budget from SharedPreferences (set by user in BudgetScreen)
            val prefs = applicationContext.getSharedPreferences("zad_prefs", Context.MODE_PRIVATE)
            // 0 = مش متسجل، مش 3500 مخترع. BudgetMath بترجع 0 على أي سقف <= 0، فالتحليل
            // بيطلع من غير أحكام ميزانية بدل ما يطلع بأحكام مبنية على رقم متأليف.
            val budget = prefs.getFloat("cached_budget", 0f).toDouble()

            val brainResult = ZadCentralBrain.fullAnalysis(
                context = applicationContext,
                inventory = inventory,
                transactions = transactions,
                subscriptions = subscriptions,
                shoppingList = emptyList(),
                behaviorPatterns = behaviorPatterns,
                budget = budget,
                pharmacyItems = pharmacyItems
            )

            // Send smart notifications
            brainResult.smartNotifications.forEach { notif ->
                showNotification(
                    title = notif.title,
                    message = notif.body,
                    priority = when (notif.priority) {
                        "HIGH" -> NotificationCompat.PRIORITY_HIGH
                        "LOW" -> NotificationCompat.PRIORITY_LOW
                        else -> NotificationCompat.PRIORITY_DEFAULT
                    },
                    speak = notif.priority == "HIGH"
                )
            }

            // مسار العقل الذكي الحقيقي: رؤى zad_insights (مكتوبة سيرفر-سايد من بيانات حقيقية)
            // بتتحول هنا لإشعارات نظام + صوت. الـ worker ده بيشغّلها دورياً عشان أي رؤية
            // pending تنزل إشعار حتى لو التطبيق مش مفتوح. ماكنش متصل قبل كده — الـ sync
            // كان ميت ميتاً (مفيش ولا مكالمة له في الكود كله).
            try {
                val userId = com.example.data.SupabaseRepo.client.auth.currentUserOrNull()?.id
                if (userId != null) {
                    com.zad.agent.ZadAlertRouter.sync(applicationContext, userId)
                }
            } catch (e: Exception) {
                Log.e("ZadWorker", "ZadAlertRouter.sync() failed: ${e.message}")
            }

            if (brainResult.autoActions.isNotEmpty()) {
                Log.d("ZadWorker", "${brainResult.autoActions.size} auto-actions generated")
            }

            Log.d("ZadWorker", "Analysis complete — ${brainResult.smartNotifications.size} smart notifications sent")
            return Result.success()
        } catch (e: Exception) {
            Log.e("ZadWorker", "Analysis Failed", e)
            return Result.failure()
        }
    }

    private fun showNotification(title: String, message: String, priority: Int = NotificationCompat.PRIORITY_DEFAULT, speak: Boolean = false) {
        ZadNotifier.send(applicationContext, title, message, priority, speak)
        Log.d("ZadWorker", "Notification sent: $title — $message")
    }
}
