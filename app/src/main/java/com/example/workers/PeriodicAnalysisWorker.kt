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
import java.time.Instant

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

            // نفس الرقم اللي زاد-برين شايفه في محادثته (behavior_profile.avg_weekly_spending)
            // — من غيره "توقع إنفاق مرتفع" اللي بيوصل كإشعار من هنا ممكن يختلف عن "متوسط
            // الإنفاق الأسبوعي" اللي المستخدم شايفه في شاشة ذكاء زاد لنفس السؤال بالظبط.
            // فشل الجلب (أوفلاين) مايكسرش التحليل — null يرجّع fullAnalysis للتوقع المحلي.
            val behaviorProfile = try {
                com.example.data.SupabaseRepo.getBehaviorProfile()
            } catch (e: Exception) {
                Log.w("ZadWorker", "getBehaviorProfile() failed, falling back to local forecast: ${e.message}")
                null
            }

            val brainResult = ZadCentralBrain.fullAnalysis(
                context = applicationContext,
                inventory = inventory,
                transactions = transactions,
                subscriptions = subscriptions,
                shoppingList = emptyList(),
                behaviorPatterns = behaviorPatterns,
                budget = budget,
                pharmacyItems = pharmacyItems,
                behaviorProfile = behaviorProfile
            )

            // ═══ درع المصاريف — وقاية قبل ما تحصل، مش محاسبة بعدها ═══
            // لو معدل صرف النهاردة عدى ٨٠٪ من المعدل الآمن → تنبيه فوري عالي الأولوية.
            // ده الفرق بين زاد يقولك "صرفت كتير" بعد ما خسرت، وبينه يوقفك قبلها.
            try {
                // نفس معادلة SpendingPower: (الميزانية − المصروف حتى اليوم) / الأيام المتبقية
                val today = java.time.LocalDate.now()
                val todayKey = today.toString()
                val monthStart = today.withDayOfMonth(1)
                val daysElapsed = today.dayOfMonth.toLong()
                val daysLeftInMonth = (today.lengthOfMonth() - today.dayOfMonth + 1).coerceAtLeast(1)
                val spentThisMonth = transactions.asSequence()
                    .filter { it.isExpense && it.createdAt?.startsWith(monthStart.toString()) == true }
                    .sumOf { it.amount }
                val remaining = budget - spentThisMonth
                val safe = if (remaining > 0) remaining / daysLeftInMonth else null
                if (safe != null && safe > 0) {
                    val todayKey = java.time.LocalDate.now().toString()
                    val todaySpent = transactions.asSequence()
                        .filter { it.isExpense && it.createdAt?.startsWith(todayKey) == true }
                        .sumOf { it.amount }
                    val ratio = if (safe > 0) todaySpent / safe else 0.0
                    val shieldKey = "shield_warned_$todayKey"
                    val alreadyWarned = prefs.getBoolean(shieldKey, false)
                    // تنبيه واحد بس في اليوم عند ٨٠٪ — إزعاج أقل، قيمة أكتر
                    if (ratio >= 0.8 && !alreadyWarned) {
                        val remaining = (safe - todaySpent).coerceAtLeast(0.0)
                        showNotification(
                            title = "🛡️ درع المصاريف",
                            message = "وصلت ${"%.0f".format(ratio * 100)}٪ من حدك اليومي — باقي ${com.example.data.CurrencyFormatter.format(applicationContext, remaining)} لليوم. أي صرف كمان هيأثر على آخر الشهر.",
                            priority = NotificationCompat.PRIORITY_HIGH,
                            speak = false // صامت — التنبيه البصري كفاية، الصوت opt-in
                        )
                        prefs.edit().putBoolean(shieldKey, true).apply()
                        Log.d("ZadWorker", "🛡️ Spend shield triggered: %.0f%% of daily safe".format(ratio * 100))
                    }
                }
            } catch (e: Exception) {
                Log.e("ZadWorker", "spend shield failed: ${e.message}")
            }

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

            checkMissedDoses(dao)

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

    /**
     * جرعة اتطلق منبهها (PharmacyReminderReceiver عمل insertDoseLog) وعدّت ٩٠ دقيقة من
     * غير ما حد يعلّمها "تم أخذها" (takenAt لسه null) = فاتت. مفيش عمود "missed" في
     * zad_dose_log نفسه (كان محتاج migration) — بدل كده نفس نمط PharmacyReminderScheduler's
     * SharedPreferences-tracked keys: مجموعة IDs اتنبّهنا عليها قبل كده، عشان الـ worker
     * ده كل ٦ ساعات ميبعتش نفس رسالة "فاتت الجرعة" أكتر من مرة لكل جرعة.
     */
    private suspend fun checkMissedDoses(dao: com.example.data.local.ZadDao) {
        val graceMs = 90 * 60 * 1000L
        val nowMs = Instant.now().toEpochMilli()
        val prefs = applicationContext.getSharedPreferences("zad_prefs", Context.MODE_PRIVATE)
        val alertedKey = "missed_dose_alerted_ids"
        val alerted = prefs.getStringSet(alertedKey, emptySet())?.toMutableSet() ?: mutableSetOf()
        var changed = false

        dao.getAllDoseLogs().first().forEach { log ->
            if (log.takenAt != null || log.id in alerted) return@forEach
            val scheduledMs = try { Instant.parse(log.scheduledAt).toEpochMilli() } catch (e: Exception) { return@forEach }
            if (nowMs - scheduledMs < graceMs) return@forEach
            com.example.data.ZadCentralBrain.sendFamilyAlert("⚠️ فاتت جرعة ${log.itemName} (${log.scheduledAt.take(16)})")
            alerted.add(log.id)
            changed = true
        }

        if (changed) prefs.edit().putStringSet(alertedKey, alerted).apply()
    }
}
